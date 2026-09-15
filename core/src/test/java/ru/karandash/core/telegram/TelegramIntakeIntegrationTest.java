package ru.karandash.core.telegram;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
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
import ru.karandash.contracts.telegram.ReplyButton;
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

    private static final RecognitionResult DUMPLING_QUESTION = new RecognitionResult(List.of(),
            List.of("Какая начинка и сколько весит один пельмень?"));
    private static final RecognitionResult DUMPLING = new RecognitionResult(List.of(new RecognitionItem(
            "Пельмень с мясом", new BigDecimal("12"), new BigDecimal("18"), 25, 40,
            new BigDecimal("1.5"), new BigDecimal("2.5"), new BigDecimal("1"), new BigDecimal("2"),
            new BigDecimal("3"), new BigDecimal("5"), new BigDecimal("0.4"))), List.of());

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
                new HttpEntity<>(parts(TelegramInboundMessage.message(nextUpdateId(), 42, "/start"), null), headers),
                String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void startBindsTelegramUserToSingleAccount() {
        long telegramId = 700_001;

        TelegramReply greeting = send(TelegramInboundMessage.message(nextUpdateId(), telegramId, "/start"), null);
        send(TelegramInboundMessage.message(nextUpdateId(), telegramId, "/help"), null);

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
        TelegramInboundMessage message = TelegramInboundMessage.message(nextUpdateId(), telegramId, "гречка с курицей");

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

        TelegramReply reply = send(TelegramInboundMessage.message(nextUpdateId(), 700_003, null), photo);

        assertThat(reply.messages()).singleElement().asString()
                .startsWith("Чтобы оценить, уточните:\n1. Что это за суп?")
                .endsWith("Ответьте сообщением — или нажмите «Оцени как есть», и я прикину по типичному варианту.");
        verify(recognitionModel).recognizePhotoWithUsage(eq(photo), eq("image/jpeg"));
        assertThat(jdbc.queryForObject("""
                select u.provider from usage_record u join telegram_identity t on t.account_id = u.account_id
                where t.telegram_id = ?""", String.class, 700_003L)).isEqualTo("agent");
    }

    @Test
    void modelFailureGivesFriendlyAnswerAndIsRecorded() {
        when(recognitionModel.recognizeTextWithUsage(anyString()))
                .thenThrow(new ModelUnavailableException("Агент-адаптер ответил 504"));

        TelegramReply reply = send(TelegramInboundMessage.message(nextUpdateId(), 700_004, "борщ"), null);

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


    @Test
    void estimateGoesToDiaryOnlyAfterButton() {
        long telegramId = 700_010;
        when(recognitionModel.recognizeTextWithUsage(anyString()))
                .thenReturn(new ModelRecognition(BUCKWHEAT, ModelUsage.unknown()));

        TelegramReply estimate = send(
                TelegramInboundMessage.message(nextUpdateId(), telegramId, "гречка с курицей"), null);

        assertThat(estimate.messages()).singleElement().asString()
                .endsWith("Записать в дневник? Если что-то не так — напишите, что именно, и я пересчитаю.");
        assertThat(estimate.buttons()).extracting(ReplyButton::text)
                .containsExactly("✓ Записать в дневник", "✗ Не записывать");
        assertThat(meals(telegramId)).as("до нажатия в дневнике пусто").isZero();

        TelegramReply saved = send(
                TelegramInboundMessage.button(nextUpdateId(), telegramId, button(estimate, 0)), null);

        assertThat(saved.messages()).singleElement().asString()
                .startsWith("Записал в дневник.")
                .contains("Гречка с курицей — 380–480 ккал")
                .contains("Всего за день: 380–480 ккал, записей: 1");
        assertThat(saved.buttons()).isEmpty();
        assertThat(meals(telegramId)).isOne();
        assertThat(jdbc.queryForObject("""
                select f.name from food_item f join meal m on m.id = f.meal_id
                join telegram_identity t on t.account_id = m.account_id where t.telegram_id = ?""",
                String.class, telegramId)).isEqualTo("Гречка с курицей");

        TelegramReply again = send(
                TelegramInboundMessage.button(nextUpdateId(), telegramId, button(estimate, 0)), null);

        assertThat(again.messages()).singleElement().asString().startsWith("Эта оценка уже закрыта");
        assertThat(meals(telegramId)).as("вторым нажатием запись не задваивается").isOne();
    }

    @Test
    void commentRecalculatesTheSameMeal() {
        long telegramId = 700_011;
        when(recognitionModel.recognizeTextWithUsage(anyString()))
                .thenReturn(new ModelRecognition(DUMPLING_QUESTION, ModelUsage.unknown()))
                .thenReturn(new ModelRecognition(DUMPLING, ModelUsage.unknown()));

        TelegramReply asked = send(TelegramInboundMessage.message(nextUpdateId(), telegramId, "пельмень"), null);
        TelegramReply revised = send(
                TelegramInboundMessage.message(nextUpdateId(), telegramId, "не знаю, запиши как есть"), null);

        assertThat(asked.buttons()).extracting(ReplyButton::text)
                .containsExactly("Оцени как есть", "✗ Не записывать");
        ArgumentCaptor<String> requests = ArgumentCaptor.forClass(String.class);
        verify(recognitionModel, times(2)).recognizeTextWithUsage(requests.capture());
        assertThat(requests.getAllValues().getLast())
                .as("модель получает и прошлую оценку, и ответ пользователя")
                .contains("пельмень")
                .contains("Какая начинка и сколько весит один пельмень?")
                .contains("не знаю, запиши как есть");
        assertThat(revised.messages()).singleElement().asString().contains("Пельмень с мясом");
        assertThat(revised.buttons()).extracting(ReplyButton::text)
                .containsExactly("✓ Записать в дневник", "✗ Не записывать");
        assertThat(jdbc.queryForList("""
                select u.kind from usage_record u join telegram_identity t on t.account_id = u.account_id
                where t.telegram_id = ? order by u.created_at""", String.class, telegramId))
                .containsExactlyInAnyOrder("RECOGNITION_TEXT", "RECOGNITION_REVISION");
    }

    @Test
    void asIsButtonAsksModelForTypicalEstimate() {
        long telegramId = 700_012;
        when(recognitionModel.recognizeTextWithUsage(anyString()))
                .thenReturn(new ModelRecognition(DUMPLING_QUESTION, ModelUsage.unknown()))
                .thenReturn(new ModelRecognition(DUMPLING, ModelUsage.unknown()));

        TelegramReply asked = send(TelegramInboundMessage.message(nextUpdateId(), telegramId, "пельмень"), null);
        TelegramReply revised = send(
                TelegramInboundMessage.button(nextUpdateId(), telegramId, button(asked, 0)), null);

        ArgumentCaptor<String> requests = ArgumentCaptor.forClass(String.class);
        verify(recognitionModel, times(2)).recognizeTextWithUsage(requests.capture());
        assertThat(requests.getAllValues().getLast()).contains("Уточнить не могу, оцени по типичному варианту.");
        assertThat(revised.messages()).singleElement().asString().contains("Пельмень с мясом");
    }

    @Test
    void dropButtonForgetsEstimateAndNextMessageStartsOver() {
        long telegramId = 700_013;
        when(recognitionModel.recognizeTextWithUsage(anyString()))
                .thenReturn(new ModelRecognition(BUCKWHEAT, ModelUsage.unknown()));

        TelegramReply estimate = send(
                TelegramInboundMessage.message(nextUpdateId(), telegramId, "гречка с курицей"), null);
        TelegramReply dropped = send(
                TelegramInboundMessage.button(nextUpdateId(), telegramId, button(estimate, 1)), null);
        send(TelegramInboundMessage.message(nextUpdateId(), telegramId, "борщ"), null);

        assertThat(dropped.messages()).singleElement().asString().startsWith("Не записал.");
        assertThat(meals(telegramId)).isZero();
        // После отказа следующее сообщение — новая еда, а не уточнение к закрытой оценке.
        verify(recognitionModel).recognizeTextWithUsage("борщ");
    }

    @Test
    void diaryIsSeparatePerUser() {
        long first = 700_014;
        long second = 700_015;
        when(recognitionModel.recognizeTextWithUsage(anyString()))
                .thenReturn(new ModelRecognition(BUCKWHEAT, ModelUsage.unknown()))
                .thenReturn(new ModelRecognition(DUMPLING, ModelUsage.unknown()));

        TelegramReply firstEstimate = send(TelegramInboundMessage.message(nextUpdateId(), first, "гречка"), null);
        send(TelegramInboundMessage.button(nextUpdateId(), first, button(firstEstimate, 0)), null);
        TelegramReply secondEstimate = send(TelegramInboundMessage.message(nextUpdateId(), second, "пельмени"), null);
        // Чужую кнопку нажать нельзя: черновик ищется вместе с аккаунтом.
        TelegramReply foreign = send(
                TelegramInboundMessage.button(nextUpdateId(), first, button(secondEstimate, 0)), null);
        send(TelegramInboundMessage.button(nextUpdateId(), second, button(secondEstimate, 0)), null);

        assertThat(foreign.messages()).singleElement().asString().startsWith("Эта оценка уже закрыта");
        assertThat(send(TelegramInboundMessage.message(nextUpdateId(), first, "/diary"), null).messages())
                .singleElement().asString()
                .contains("Гречка с курицей")
                .doesNotContain("Пельмень")
                .contains("записей: 1");
        assertThat(send(TelegramInboundMessage.message(nextUpdateId(), second, "/diary"), null).messages())
                .singleElement().asString()
                .contains("Пельмень с мясом")
                .doesNotContain("Гречка")
                .contains("записей: 1");
        assertThat(meals(first)).isOne();
        assertThat(meals(second)).isOne();
    }

    @Test
    void diaryIsEmptyForNewUser() {
        TelegramReply reply = send(TelegramInboundMessage.message(nextUpdateId(), 700_016, "/diary"), null);

        assertThat(reply.messages()).singleElement().asString().startsWith("Сегодня в дневнике пусто");
    }

    private int meals(long telegramId) {
        return jdbc.queryForObject("""
                select count(*) from meal m join telegram_identity t on t.account_id = m.account_id
                where t.telegram_id = ?""", Integer.class, telegramId);
    }

    private static String button(TelegramReply reply, int index) {
        return reply.buttons().get(index).data();
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
