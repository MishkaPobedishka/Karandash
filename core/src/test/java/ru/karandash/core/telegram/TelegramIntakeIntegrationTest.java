package ru.karandash.core.telegram;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.RabbitMQContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import ru.karandash.contracts.ai.ModelUsage;
import ru.karandash.contracts.ai.RecognitionItem;
import ru.karandash.contracts.ai.RecognitionResult;
import ru.karandash.contracts.telegram.BotNotification;
import ru.karandash.contracts.telegram.TelegramInboundMessage;
import ru.karandash.contracts.telegram.TelegramReply;
import ru.karandash.core.ai.ModelRecognition;
import ru.karandash.core.ai.ModelUnavailableException;
import ru.karandash.core.ai.RecognitionModel;
import ru.karandash.core.outbox.OutboxService;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Ядро целиком на настоящих PostgreSQL и RabbitMQ: внутренний API приёмщика, привязка аккаунта,
 * отсев дублей, учёт вызовов модели и доставка уведомлений в очередь приёмщика.
 */
@Testcontainers(disabledWithoutDocker = true)
@DirtiesContext
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "karandash.internal-api.service-token=" + TelegramIntakeIntegrationTest.TOKEN,
        "karandash.ai.provider=agent",
        "karandash.ai.agent.base-url=http://agent.invalid:8095",
        "karandash.ai.agent.service-token=agent-token",
        "karandash.outbox.poll-delay-ms=200"
})
class TelegramIntakeIntegrationTest {

    static final String TOKEN = "test-core-token";
    private static final AtomicLong UPDATE_IDS = new AtomicLong(1_000);
    private static final RecognitionResult BUCKWHEAT = new RecognitionResult(List.of(new RecognitionItem(
            "Гречка с курицей", new BigDecimal("250"), new BigDecimal("320"), 380, 480,
            new BigDecimal("25"), new BigDecimal("32"), new BigDecimal("8"), new BigDecimal("12"),
            new BigDecimal("45"), new BigDecimal("60"), new BigDecimal("0.7"))), List.of());

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine");

    @Container
    @ServiceConnection
    static final RabbitMQContainer RABBITMQ = new RabbitMQContainer("rabbitmq:4-management-alpine");

    @Autowired
    TestRestTemplate rest;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    TelegramNotifications notifications;

    @Autowired
    OutboxService outboxService;

    @Autowired
    RabbitTemplate rabbitTemplate;

    @MockitoBean
    RecognitionModel recognitionModel;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        reset(recognitionModel);
    }

    @Test
    void rejectsCallsWithoutServiceToken() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);
        ResponseEntity<String> response = rest.postForEntity("/internal/telegram/messages",
                new HttpEntity<>(parts(new TelegramInboundMessage(nextUpdateId(), 42, "/start"), null), headers),
                String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void startBindsTelegramUserToSingleAccount() {
        long telegramId = 700_001;

        TelegramReply greeting = send(new TelegramInboundMessage(nextUpdateId(), telegramId, "/start"), null);
        send(new TelegramInboundMessage(nextUpdateId(), telegramId, "/help"), null);

        assertThat(greeting.duplicate()).isFalse();
        assertThat(greeting.messages()).singleElement().asString().startsWith("Привет! Я Карандаш");
        assertThat(jdbc.queryForObject(
                "select count(*) from telegram_identity where telegram_id = ?", Integer.class, telegramId)).isOne();
        assertThat(jdbc.queryForObject("""
                select a.status from account a join telegram_identity t on t.account_id = a.id
                where t.telegram_id = ?""", String.class, telegramId)).isEqualTo("ACTIVE");
    }

    @Test
    void recognizesTextOnceAndRecordsUsage() {
        long telegramId = 700_002;
        when(recognitionModel.recognizeTextWithUsage(anyString())).thenReturn(new ModelRecognition(BUCKWHEAT,
                new ModelUsage("claude-cli", "claude-sonnet-5", 1200, 150, new BigDecimal("0.0123"))));
        TelegramInboundMessage message = new TelegramInboundMessage(nextUpdateId(), telegramId, "гречка с курицей");

        TelegramReply reply = send(message, null);
        TelegramReply repeated = send(message, null);

        assertThat(reply.messages()).singleElement().asString()
                .contains("• Гречка с курицей — 250–320 г, 380–480 ккал")
                .contains("Б 25–32 г · Ж 8–12 г · У 45–60 г · уверенность 70%");
        assertThat(repeated.duplicate()).isTrue();
        assertThat(repeated.messages()).isEmpty();
        verify(recognitionModel, times(1)).recognizeTextWithUsage("гречка с курицей");

        Map<String, Object> usage = jdbc.queryForMap("""
                select u.kind, u.provider, u.model, u.tokens_in, u.tokens_out, u.cost, u.latency_ms
                from usage_record u join telegram_identity t on t.account_id = u.account_id
                where t.telegram_id = ?""", telegramId);
        assertThat(usage).containsEntry("kind", "RECOGNITION_TEXT")
                .containsEntry("provider", "claude-cli")
                .containsEntry("model", "claude-sonnet-5")
                .containsEntry("tokens_in", 1200)
                .containsEntry("tokens_out", 150);
        assertThat((BigDecimal) usage.get("cost")).isEqualByComparingTo("0.0123");
    }

    @Test
    void recognizesPhotoAndAsksQuestions() {
        byte[] photo = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, 1, 2, 3};
        when(recognitionModel.recognizePhotoWithUsage(any(), any())).thenReturn(new ModelRecognition(
                new RecognitionResult(List.of(), List.of("Что это за суп?")), ModelUsage.unknown()));

        TelegramReply reply = send(new TelegramInboundMessage(nextUpdateId(), 700_003, null), photo);

        assertThat(reply.messages()).containsExactly("Чтобы оценить, уточните:\n1. Что это за суп?");
        verify(recognitionModel).recognizePhotoWithUsage(eq(photo), eq("image/jpeg"));
        assertThat(jdbc.queryForObject("""
                select u.provider from usage_record u join telegram_identity t on t.account_id = u.account_id
                where t.telegram_id = ?""", String.class, 700_003L)).isEqualTo("agent");
    }

    @Test
    void modelFailureGivesFriendlyAnswerAndIsRecorded() {
        when(recognitionModel.recognizeTextWithUsage(anyString()))
                .thenThrow(new ModelUnavailableException("Агент-адаптер ответил 504"));

        TelegramReply reply = send(new TelegramInboundMessage(nextUpdateId(), 700_004, "борщ"), null);

        assertThat(reply.messages()).singleElement().asString().contains("не получается распознать");
        assertThat(jdbc.queryForObject("""
                select u.kind from usage_record u join telegram_identity t on t.account_id = u.account_id
                where t.telegram_id = ?""", String.class, 700_004L)).isEqualTo("RECOGNITION_TEXT_FAILED");
    }

    @Test
    void notificationsReachBotQueueAsJsonAndOtherEventsDoNot() throws Exception {
        drainBotQueue();
        outboxService.append("account", UUID.randomUUID(), "meal.recognized", Map.of("kcal", 400));
        notifications.notify(UUID.randomUUID(), 700_005, "Вы приближаетесь к верхней границе");

        Message message = rabbitTemplate.receive("q.bot.events", 15_000);

        assertThat(message).isNotNull();
        assertThat(message.getMessageProperties().getReceivedRoutingKey()).isEqualTo(BotNotification.EVENT_TYPE);
        assertThat(message.getMessageProperties().getContentType()).isEqualTo(MediaType.APPLICATION_JSON_VALUE);
        JsonNode body = objectMapper.readTree(new String(message.getBody(), StandardCharsets.UTF_8));
        assertThat(body.path("chatId").asLong()).isEqualTo(700_005);
        assertThat(body.path("text").asText()).isEqualTo("Вы приближаетесь к верхней границе");
        assertThat(rabbitTemplate.receive("q.bot.events", 2_000)).as("чужие события не попадают к приёмщику").isNull();
    }

    private void drainBotQueue() {
        while (rabbitTemplate.receive("q.bot.events", 500) != null) {
            // очищаем очередь от сообщений других тестов
        }
    }

    private TelegramReply send(TelegramInboundMessage message, byte[] photo) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(TOKEN);
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);
        ResponseEntity<TelegramReply> response = rest.postForEntity("/internal/telegram/messages",
                new HttpEntity<>(parts(message, photo), headers), TelegramReply.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        return response.getBody();
    }

    private static MultiValueMap<String, Object> parts(TelegramInboundMessage message, byte[] photo) {
        MultiValueMap<String, Object> parts = new LinkedMultiValueMap<>();
        HttpHeaders jsonPart = new HttpHeaders();
        jsonPart.setContentType(MediaType.APPLICATION_JSON);
        parts.add("message", new HttpEntity<>(message, jsonPart));
        if (photo != null) {
            HttpHeaders photoPart = new HttpHeaders();
            photoPart.setContentType(MediaType.IMAGE_JPEG);
            parts.add("photo", new HttpEntity<>(new ByteArrayResource(photo) {
                @Override
                public String getFilename() {
                    return "photo.jpg";
                }
            }, photoPart));
        }
        return parts;
    }

    private static long nextUpdateId() {
        return UPDATE_IDS.incrementAndGet();
    }
}
