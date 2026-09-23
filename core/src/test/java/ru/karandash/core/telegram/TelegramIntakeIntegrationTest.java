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
import org.springframework.http.HttpMethod;
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
import ru.karandash.contracts.ai.LunchAdvice;
import ru.karandash.contracts.ai.LunchOption;
import ru.karandash.contracts.ai.ModelUsage;
import ru.karandash.contracts.ai.RecognitionItem;
import ru.karandash.contracts.ai.RecognitionResult;
import ru.karandash.contracts.telegram.BotNotification;
import ru.karandash.contracts.telegram.ReplyButton;
import ru.karandash.contracts.telegram.ReplyPhoto;
import ru.karandash.contracts.telegram.TelegramInboundMessage;
import ru.karandash.contracts.telegram.TelegramReply;
import ru.karandash.contracts.telegram.TelegramUserName;
import ru.karandash.core.ai.ModelLunchAdvice;
import ru.karandash.core.ai.ModelRecognition;
import ru.karandash.core.ai.ModelUnavailableException;
import ru.karandash.core.ai.RecognitionModel;
import ru.karandash.core.canteen.CanteenClient;
import ru.karandash.core.canteen.CanteenDish;
import ru.karandash.core.canteen.CanteenMenu;
import ru.karandash.core.canteen.LunchMailing;
import ru.karandash.core.reminder.Reminder;
import ru.karandash.core.reminder.ReminderMailing;
import ru.karandash.core.reminder.ReminderService;
import ru.karandash.core.reminder.ReminderType;
import ru.karandash.core.outbox.OutboxService;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Optional;
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
        "karandash.access.admins=" + TelegramIntakeIntegrationTest.ADMIN,
        "karandash.ai.provider=agent",
        "karandash.ai.agent.base-url=http://agent.invalid:8095",
        "karandash.ai.agent.service-token=agent-token",
        "karandash.outbox.poll-delay-ms=200",
        // Проверки столовой не должны зависеть от того, в какой день недели их запустили.
        "karandash.canteen.weekends-off=false"
})
class TelegramIntakeIntegrationTest {

    static final String TOKEN = "test-core-token";
    /** Первый администратор приходит из конфигурации — иначе в боте некому выдать доступ. */
    static final long ADMIN = 700_000;
    private static final AtomicLong UPDATE_IDS = new AtomicLong(1_000);
    private static final RecognitionResult BUCKWHEAT = new RecognitionResult(List.of(new RecognitionItem(
            "Гречка с курицей", new BigDecimal("250"), new BigDecimal("320"), 380, 480,
            new BigDecimal("25"), new BigDecimal("32"), new BigDecimal("8"), new BigDecimal("12"),
            new BigDecimal("45"), new BigDecimal("60"), new BigDecimal("0.7"))), List.of());

    /** Кусок меню столовой: по нему проверяем подбор обеда. */
    private static final CanteenMenu MENU = new CanteenMenu(List.of(new CanteenDish(
            "Горячие блюда", "Гречка с курицей", new BigDecimal("120"), new BigDecimal("250"),
            new BigDecimal("109.5"), new BigDecimal("8"), new BigDecimal("2.5"), new BigDecimal("13.7"),
            "https://canteen/grechka.jpg")));

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

    @MockitoBean
    CanteenClient canteenClient;

    @Autowired
    LunchMailing lunchMailing;

    @Autowired
    ReminderService reminderService;

    @Autowired
    ReminderMailing reminderMailing;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        reset(recognitionModel, canteenClient);
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
    void newUserSeesBetaGateAndBindsToSingleAccount() {
        long telegramId = 700_001;

        TelegramReply start = send(TelegramInboundMessage.message(nextUpdateId(), telegramId, "/start"), null);
        TelegramReply food = send(TelegramInboundMessage.message(nextUpdateId(), telegramId, "борщ"), null);

        assertThat(start.duplicate()).isFalse();
        assertThat(start.messages()).singleElement().asString()
                .startsWith("Карандаш пока в закрытом бета-тесте")
                .contains(String.valueOf(telegramId));
        assertThat(start.buttons()).extracting(ReplyButton::text).containsExactly("Подать заявку");
        assertThat(food.messages()).singleElement().asString().startsWith("Карандаш пока в закрытом бета-тесте");
        verify(recognitionModel, org.mockito.Mockito.never()).recognizeTextWithUsage(anyString(), any());
        assertThat(jdbc.queryForObject(
                "select count(*) from telegram_identity where telegram_id = ?", Integer.class, telegramId)).isOne();
        assertThat(jdbc.queryForObject("""
                select a.access from account a join telegram_identity t on t.account_id = a.id
                where t.telegram_id = ?""", String.class, telegramId)).isEqualTo("PENDING");
    }

    @Test
    void firstAdminDecisionWinsAndBothSidesLearnWhoDecided() {
        long applicant = 700_020;
        long secondAdmin = 700_021;
        allow(secondAdmin);
        send(TelegramInboundMessage.message(nextUpdateId(), ADMIN, "/promote " + secondAdmin), null);
        TelegramReply gate = send(TelegramInboundMessage.message(nextUpdateId(), applicant, "Мария", "masha", "/start"),
                null);

        TelegramReply requested = send(TelegramInboundMessage.button(nextUpdateId(), applicant, "Мария", "masha",
                button(gate, 0)), null);
        TelegramReply decision = send(TelegramInboundMessage.button(nextUpdateId(), ADMIN, "Костя", null,
                "agrant:" + applicant), null);
        TelegramReply late = send(TelegramInboundMessage.button(nextUpdateId(), secondAdmin, "Второй", null,
                "adeny:" + applicant), null);

        assertThat(requested.messages()).singleElement().asString().startsWith("Заявка отправлена");
        assertThat(decision.messages()).singleElement().asString().isEqualTo("Доступ открыт: Мария (@masha).");
        assertThat(late.messages()).singleElement().asString()
                .as("решает тот, кто нажал первым")
                .isEqualTo("Заявку уже закрыл Костя.");
        assertThat(jdbc.queryForObject("""
                select a.access from account a join telegram_identity t on t.account_id = a.id
                where t.telegram_id = ?""", String.class, applicant)).isEqualTo("ALLOWED");
        // Пользователю ушло, кто открыл доступ; второму администратору — кто закрыл заявку.
        assertThat(notificationsFor(applicant)).anyMatch(text -> text.startsWith("Доступ открыл Костя."));
        assertThat(notificationsFor(secondAdmin)).anyMatch(text -> text.contains("одобрил Костя"));
    }

    @Test
    void adminPanelOpensSectionsAndGivesRightsByButtons() {
        long user = 700_024;
        allow(user);

        TelegramReply panel = send(TelegramInboundMessage.message(nextUpdateId(), ADMIN, "/admin"), null);
        TelegramReply users = send(TelegramInboundMessage.button(nextUpdateId(), ADMIN, "ausers:-"), null);
        TelegramReply card = send(TelegramInboundMessage.button(nextUpdateId(), ADMIN, "auser:" + user), null);
        TelegramReply promoted = send(TelegramInboundMessage.button(nextUpdateId(), ADMIN, "apromote:" + user), null);
        TelegramReply cardAfter = send(TelegramInboundMessage.button(nextUpdateId(), ADMIN, "auser:" + user), null);

        assertThat(panel.messages()).singleElement().asString().startsWith("Панель администратора.");
        assertThat(panel.buttons()).extracting(ReplyButton::data)
                .containsExactly("areqs:-", "ausers:-", "aclog:-");
        assertThat(users.replaceMessage()).as("список переключается в том же сообщении").isTrue();
        assertThat(users.buttons()).extracting(ReplyButton::data).contains("apanel:-");
        assertThat(card.messages()).singleElement().asString()
                .contains("Номер: " + user)
                .contains("Доступ: открыт")
                .contains("Роль: пользователь");
        assertThat(card.buttons()).extracting(ReplyButton::text)
                .containsExactly("✗ Забрать доступ", "★ Сделать администратором", "← Назад");
        assertThat(promoted.messages()).singleElement().asString().startsWith("Теперь администратор");
        // Администратору администраторство второй раз не предлагаем.
        assertThat(cardAfter.messages()).singleElement().asString().contains("Роль: администратор");
        assertThat(cardAfter.buttons()).extracting(ReplyButton::text)
                .containsExactly("✗ Забрать доступ", "← Назад");
        assertThat(jdbc.queryForObject("""
                select a.role from account a join telegram_identity t on t.account_id = a.id
                where t.telegram_id = ?""", String.class, user)).isEqualTo("ADMIN");
    }

    @Test
    void onlyAdminRunsAdminCommands() {
        long user = 700_022;
        allow(user);

        TelegramReply users = send(TelegramInboundMessage.message(nextUpdateId(), user, "/users"), null);
        TelegramReply grant = send(TelegramInboundMessage.message(nextUpdateId(), user, "/grant 700023"), null);
        TelegramReply button = send(TelegramInboundMessage.button(nextUpdateId(), user, "agrant:700023"), null);

        assertThat(users.messages()).singleElement().asString().isEqualTo("Эта команда только для администратора.");
        assertThat(grant.messages()).singleElement().asString().isEqualTo("Эта команда только для администратора.");
        assertThat(button.messages()).singleElement().asString().isEqualTo("Эта команда только для администратора.");
    }

    @Test
    void recognizesTextOnceAndRecordsUsage() {
        long telegramId = 700_002;
        allow(telegramId);
        when(recognitionModel.recognizeTextWithUsage(anyString(), any())).thenReturn(new ModelRecognition(BUCKWHEAT,
                new ModelUsage("claude-cli", "claude-sonnet-5", 1200, 150, new BigDecimal("0.0123"))));
        TelegramInboundMessage message = TelegramInboundMessage.message(nextUpdateId(), telegramId, "гречка с курицей");

        TelegramReply reply = send(message, null);
        TelegramReply repeated = send(message, null);

        assertThat(reply.messages()).singleElement().asString()
                .contains("• Гречка с курицей — 250–320 г, 380–480 ккал")
                .contains("Б 25–32 г · Ж 8–12 г · У 45–60 г · уверенность 70%");
        assertThat(repeated.duplicate()).isTrue();
        assertThat(repeated.messages()).isEmpty();
        verify(recognitionModel, times(1)).recognizeTextWithUsage(eq("гречка с курицей"), any());

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
        allow(700_003);
        when(recognitionModel.recognizePhotoWithUsage(any(), any(), any())).thenReturn(new ModelRecognition(
                new RecognitionResult(List.of(), List.of("Что это за суп?")), ModelUsage.unknown()));

        TelegramReply reply = send(TelegramInboundMessage.message(nextUpdateId(), 700_003, null), photo);

        assertThat(reply.messages()).singleElement().asString()
                .startsWith("Чтобы оценить, уточните:\n1. Что это за суп?")
                .endsWith("Ответьте сообщением — или нажмите «Оцени как есть», и я прикину по типичному варианту.");
        verify(recognitionModel).recognizePhotoWithUsage(eq(photo), eq("image/jpeg"), any());
        assertThat(jdbc.queryForObject("""
                select u.provider from usage_record u join telegram_identity t on t.account_id = u.account_id
                where t.telegram_id = ?""", String.class, 700_003L)).isEqualTo("agent");
    }

    @Test
    void modelFailureGivesFriendlyAnswerAndIsRecorded() {
        allow(700_004);
        when(recognitionModel.recognizeTextWithUsage(anyString(), any()))
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
        allow(telegramId);
        when(recognitionModel.recognizeTextWithUsage(anyString(), any()))
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
        allow(telegramId);
        when(recognitionModel.recognizeTextWithUsage(anyString(), any()))
                .thenReturn(new ModelRecognition(DUMPLING_QUESTION, ModelUsage.unknown()))
                .thenReturn(new ModelRecognition(DUMPLING, ModelUsage.unknown()));

        TelegramReply asked = send(TelegramInboundMessage.message(nextUpdateId(), telegramId, "пельмень"), null);
        TelegramReply revised = send(
                TelegramInboundMessage.message(nextUpdateId(), telegramId, "не знаю, запиши как есть"), null);

        assertThat(asked.buttons()).extracting(ReplyButton::text)
                .containsExactly("Оцени как есть", "✗ Не записывать");
        ArgumentCaptor<String> requests = ArgumentCaptor.forClass(String.class);
        verify(recognitionModel, times(2)).recognizeTextWithUsage(requests.capture(), any());
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
        allow(telegramId);
        when(recognitionModel.recognizeTextWithUsage(anyString(), any()))
                .thenReturn(new ModelRecognition(DUMPLING_QUESTION, ModelUsage.unknown()))
                .thenReturn(new ModelRecognition(DUMPLING, ModelUsage.unknown()));

        TelegramReply asked = send(TelegramInboundMessage.message(nextUpdateId(), telegramId, "пельмень"), null);
        TelegramReply revised = send(
                TelegramInboundMessage.button(nextUpdateId(), telegramId, button(asked, 0)), null);

        ArgumentCaptor<String> requests = ArgumentCaptor.forClass(String.class);
        verify(recognitionModel, times(2)).recognizeTextWithUsage(requests.capture(), any());
        assertThat(requests.getAllValues().getLast()).contains("Уточнить не могу, оцени по типичному варианту.");
        assertThat(revised.messages()).singleElement().asString().contains("Пельмень с мясом");
    }

    @Test
    void dropButtonForgetsEstimateAndNextMessageStartsOver() {
        long telegramId = 700_013;
        allow(telegramId);
        when(recognitionModel.recognizeTextWithUsage(anyString(), any()))
                .thenReturn(new ModelRecognition(BUCKWHEAT, ModelUsage.unknown()));

        TelegramReply estimate = send(
                TelegramInboundMessage.message(nextUpdateId(), telegramId, "гречка с курицей"), null);
        TelegramReply dropped = send(
                TelegramInboundMessage.button(nextUpdateId(), telegramId, button(estimate, 1)), null);
        send(TelegramInboundMessage.message(nextUpdateId(), telegramId, "борщ"), null);

        assertThat(dropped.messages()).singleElement().asString().startsWith("Не записал.");
        assertThat(meals(telegramId)).isZero();
        // После отказа следующее сообщение — новая еда, а не уточнение к закрытой оценке.
        verify(recognitionModel).recognizeTextWithUsage(eq("борщ"), any());
    }

    @Test
    void diaryIsSeparatePerUser() {
        long first = 700_014;
        long second = 700_015;
        allow(first);
        allow(second);
        when(recognitionModel.recognizeTextWithUsage(anyString(), any()))
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
        allow(700_016);
        TelegramReply reply = send(TelegramInboundMessage.message(nextUpdateId(), 700_016, "/diary"), null);

        assertThat(reply.messages()).singleElement().asString().startsWith("Сегодня в дневнике пусто");
    }

    @Test
    void nameFromTelegramSurvivesEventsWithoutIt() {
        long telegramId = 700_023;

        send(TelegramInboundMessage.message(nextUpdateId(), telegramId, "Иван Петров", "vanya", "/start"), null);
        // Нажатие кнопки Telegram присылает без имени — известное имя от этого теряться не должно.
        send(TelegramInboundMessage.button(nextUpdateId(), telegramId, "areq:-"), null);
        TelegramReply requests = send(TelegramInboundMessage.button(nextUpdateId(), ADMIN, "areqs:-"), null);

        assertThat(requests.messages()).singleElement().asString().contains("Иван Петров (@vanya) — 700023");
        assertThat(requests.buttons()).extracting(ReplyButton::text).contains("✓ Иван Петров (@vanya)");
        assertThat(jdbc.queryForObject("select display_name from telegram_identity where telegram_id = ?",
                String.class, telegramId)).isEqualTo("Иван Петров");
    }

    @Test
    void lunchCommandSuggestsFromTodayMenuAndMailingCanBeTurnedOff() {
        long telegramId = 700_040;
        allow(telegramId);
        when(canteenClient.today()).thenReturn(Optional.of(MENU));
        when(recognitionModel.adviseLunch(anyString())).thenReturn(Optional.of(new ModelLunchAdvice(
                new LunchAdvice(List.of(new LunchOption("Сытный", List.of("Гречка с курицей"), 380, 480, 120,
                        "Много белка"))), ModelUsage.unknown())));

        TelegramReply lunch = send(TelegramInboundMessage.message(nextUpdateId(), telegramId, "/lunch"), null);
        TelegramReply off = send(TelegramInboundMessage.button(nextUpdateId(), telegramId, "lmail:-"), null);
        lunchMailing.sendDailyAdvice();

        assertThat(lunch.messages()).singleElement().asString()
                .startsWith("Сегодня в столовой:")
                .contains("1. Сытный — 380–480 ккал, 120 ₽")
                .contains("• Гречка с курицей, 250 г")
                .contains("Много белка");
        assertThat(lunch.buttons()).extracting(ReplyButton::text)
                .containsExactly("Показать всё меню", "Не присылать по утрам");
        assertThat(lunch.photos()).as("фотографии предложенных блюд идут вместе с ответом")
                .extracting(ReplyPhoto::url).containsExactly("https://canteen/grechka.jpg");
        assertThat(lunch.photos().getFirst().caption()).isEqualTo("Гречка с курицей — 120 ₽, 250 г, 109,5 ккал");
        assertThat(off.messages()).singleElement().asString().startsWith("Больше не присылаю подбор обеда");
        assertThat(notificationsFor(telegramId))
                .as("отписался — утренняя рассылка его не трогает")
                .noneMatch(text -> text.startsWith("Что сегодня взять на обед"));
    }

    @Test
    void clarificationKeepsWhatWasAlreadyAnsweredAndStopsAskingAfterSecondRound() {
        long telegramId = 700_060;
        allow(telegramId);
        when(recognitionModel.recognizeTextWithUsage(anyString(), any())).thenReturn(new ModelRecognition(
                new RecognitionResult(List.of(), List.of("Что на маленькой тарелке?")), ModelUsage.unknown()));

        send(TelegramInboundMessage.message(nextUpdateId(), telegramId, "обед: салат и что-то ещё"), null);
        send(TelegramInboundMessage.message(nextUpdateId(), telegramId, "тефтелька в сливочном соусе"), null);
        send(TelegramInboundMessage.message(nextUpdateId(), telegramId, "сок яблочный"), null);

        ArgumentCaptor<String> requests = ArgumentCaptor.forClass(String.class);
        verify(recognitionModel, times(3)).recognizeTextWithUsage(requests.capture(), any());
        String second = requests.getAllValues().get(1);
        String third = requests.getAllValues().get(2);
        assertThat(second).contains("обед: салат и что-то ещё").contains("тефтелька в сливочном соусе");
        assertThat(third)
                .as("в третьем запросе видно всё, что уже выяснили")
                .contains("Вопрос модели: Что на маленькой тарелке?")
                .contains("Ответ человека: тефтелька в сливочном соусе")
                .contains("сок яблочный");
        assertThat(third).as("второй круг уточнений — последний").contains("последний круг уточнений");
        assertThat(third).contains("не проси их прислать");
    }

    @Test
    void questionAboutCanteenGoesToLunchWithTheWish() {
        long telegramId = 700_061;
        allow(telegramId);
        when(canteenClient.today()).thenReturn(Optional.of(MENU));
        when(recognitionModel.adviseLunch(anyString())).thenReturn(Optional.of(new ModelLunchAdvice(
                new LunchAdvice(List.of(new LunchOption("Лёгкий", List.of("Гречка с курицей"), 300, 380, 120, null))),
                ModelUsage.unknown())));

        TelegramReply reply = send(TelegramInboundMessage.message(nextUpdateId(), telegramId,
                "Че похавать в столовой на 600-700 ккал, чтобы было первое и салат"), null);

        ArgumentCaptor<String> prompt = ArgumentCaptor.forClass(String.class);
        verify(recognitionModel).adviseLunch(prompt.capture());
        verify(recognitionModel, org.mockito.Mockito.never()).recognizeTextWithUsage(anyString(), any());
        assertThat(prompt.getValue())
                .as("пожелание уходит модели вместе с меню")
                .contains("Меню столовой на сегодня")
                .contains("чтобы было первое и салат");
        assertThat(reply.messages()).singleElement().asString().startsWith("Сегодня в столовой:");
    }

    @Test
    void recognitionGetsTodayCanteenMenuAsAReference() {
        long telegramId = 700_063;
        allow(telegramId);
        when(canteenClient.today()).thenReturn(Optional.of(MENU));
        when(recognitionModel.recognizeTextWithUsage(anyString(), any())).thenReturn(new ModelRecognition(
                new RecognitionResult(List.of(), List.of("Сколько было гречки?")), ModelUsage.unknown()));

        send(TelegramInboundMessage.message(nextUpdateId(), telegramId, "гречка с курицей на обед"), null);

        ArgumentCaptor<String> context = ArgumentCaptor.forClass(String.class);
        verify(recognitionModel).recognizeTextWithUsage(eq("гречка с курицей на обед"), context.capture());
        assertThat(context.getValue())
                .as("модель видит сегодняшнее меню и берёт из него вес и калорийность")
                .contains("Сегодняшнее меню столовой")
                .contains("Гречка с курицей, 250 г, 109.5 ккал")
                .contains("бери её вес и пищевую ценность оттуда");
    }

    @Test
    void canteenTalkContinuesWithShortFollowUps() {
        long telegramId = 700_062;
        allow(telegramId);
        when(canteenClient.today()).thenReturn(Optional.of(MENU));
        when(recognitionModel.adviseLunch(anyString())).thenReturn(Optional.of(new ModelLunchAdvice(
                new LunchAdvice(List.of(new LunchOption("Лёгкий", List.of("Гречка с курицей"), 300, 380, 120, null))),
                ModelUsage.unknown())));

        when(recognitionModel.recognizeTextWithUsage(anyString(), any())).thenReturn(new ModelRecognition(
                new RecognitionResult(List.of(), List.of("Сколько было гречки?")), ModelUsage.unknown()));

        send(TelegramInboundMessage.message(nextUpdateId(), telegramId, "что мне поесть на 300 калорий"), null);
        TelegramReply lighter = send(TelegramInboundMessage.message(nextUpdateId(), telegramId, "а полегче?"), null);
        TelegramReply eaten = send(TelegramInboundMessage.message(nextUpdateId(), telegramId,
                "съел гречку с курицей"), null);

        ArgumentCaptor<String> prompts = ArgumentCaptor.forClass(String.class);
        verify(recognitionModel, times(2)).adviseLunch(prompts.capture());
        assertThat(prompts.getAllValues().get(0)).contains("что мне поесть на 300 калорий");
        assertThat(prompts.getAllValues().get(1))
                .as("уточнение приходит вместе с первой просьбой")
                .contains("что мне поесть на 300 калорий")
                .contains("а полегче?");
        assertThat(lighter.messages()).singleElement().asString().startsWith("Сегодня в столовой:");
        // «Съел…» закрывает разговор о столовой и уходит в дневник.
        verify(recognitionModel).recognizeTextWithUsage(anyString(), any());
        assertThat(eaten.messages()).singleElement().asString().doesNotStartWith("Сегодня в столовой:");
    }

    @Test
    void menuButtonShowsTheWholeCanteenMenu() {
        long telegramId = 700_042;
        allow(telegramId);
        when(canteenClient.today()).thenReturn(Optional.of(MENU));
        when(recognitionModel.adviseLunch(anyString())).thenReturn(Optional.of(new ModelLunchAdvice(
                new LunchAdvice(List.of(new LunchOption("Сытный", List.of("Гречка с курицей"), 380, 480, 120, null))),
                ModelUsage.unknown())));

        TelegramReply help = send(TelegramInboundMessage.message(nextUpdateId(), telegramId, "/help"), null);
        TelegramReply lunch = send(TelegramInboundMessage.button(nextUpdateId(), telegramId, "lshow:-"), null);
        TelegramReply menu = send(TelegramInboundMessage.button(nextUpdateId(), telegramId, "lmenu:-"), null);

        verify(recognitionModel, times(1)).adviseLunch(anyString());
        assertThat(lunch.photos()).as("подбор из памяти приходит с теми же блюдами").hasSize(1);

        // Столовая поменяла меню — держать прошлый подбор нельзя, считаем заново.
        when(canteenClient.today()).thenReturn(Optional.of(new CanteenMenu(List.of(new CanteenDish(
                "Горячие блюда", "Гречка с курицей", new BigDecimal("140"), new BigDecimal("250"),
                new BigDecimal("109.5"), new BigDecimal("8"), new BigDecimal("2.5"), new BigDecimal("13.7"),
                "https://canteen/grechka.jpg")))));
        send(TelegramInboundMessage.button(nextUpdateId(), telegramId, "lshow:-"), null);
        verify(recognitionModel, times(2)).adviseLunch(anyString());
        assertThat(help.buttons()).extracting(ReplyButton::text).contains("🍽 Меню столовой");
        assertThat(menu.messages()).hasSize(2);
        assertThat(menu.messages().getLast())
                .startsWith("Всё меню столовой на сегодня:")
                .contains("Горячие блюда:")
                .contains("• Гречка с курицей — 120 ₽, 250 г, 109,5 ккал");
        assertThat(menu.buttons()).as("меню уже открыто — кнопки на него больше нет")
                .extracting(ReplyButton::text).containsExactly("Не присылать по утрам");
        assertThat(menu.photos()).as("к списку всего меню фотографии не прикладываем").isEmpty();
    }

    @Test
    void remindersMenuSwitchesInPlaceAndKeepsUserChoices() {
        long telegramId = 700_050;
        allow(telegramId);

        TelegramReply menu = send(TelegramInboundMessage.message(nextUpdateId(), telegramId, "/reminders"), null);
        TelegramReply card = send(TelegramInboundMessage.button(nextUpdateId(), telegramId, "rmeal:b"), null);
        TelegramReply earlier = send(TelegramInboundMessage.button(nextUpdateId(), telegramId, "rtime:b:-60"), null);
        TelegramReply off = send(TelegramInboundMessage.button(nextUpdateId(), telegramId, "rtgl:b"), null);
        TelegramReply zones = send(TelegramInboundMessage.button(nextUpdateId(), telegramId, "rzone:-"), null);
        TelegramReply moscow = send(TelegramInboundMessage.button(nextUpdateId(), telegramId, "rzset:msk"), null);

        assertThat(menu.replaceMessage()).as("напоминания живут в одном сообщении").isTrue();
        assertThat(menu.messages()).singleElement().asString()
                .startsWith("⏰ Напоминания про еду")
                .contains("🍳 Завтрак — 08:30")
                .contains("🍲 Обед — 12:00")
                .contains("🍽 Ужин — 20:00")
                .contains("Часовой пояс: Тюмень");
        assertThat(menu.buttons()).extracting(ReplyButton::data)
                .containsExactly("rmeal:b", "rmeal:l", "rmeal:d", "rzone:-");
        assertThat(card.messages()).singleElement().asString().contains("Напомню в 08:30");
        assertThat(card.buttons()).extracting(ReplyButton::text)
                .containsExactly("−1 ч", "−10 мин", "+10 мин", "+1 ч", "Выключить", "← Назад");
        assertThat(card.buttons()).extracting(ReplyButton::row)
                .as("кнопки сдвига стоят в один ряд")
                .containsExactly(1, 1, 1, 1, 0, 0);
        assertThat(earlier.messages()).singleElement().asString().contains("Напомню в 07:30");
        assertThat(off.messages()).singleElement().asString().contains("Напоминание выключено");
        assertThat(zones.buttons()).extracting(ReplyButton::text)
                .anyMatch(text -> text.startsWith("✅ Тюмень"));
        assertThat(moscow.messages()).singleElement().asString().contains("Часовой пояс: Москва");
        assertThat(reminderService.settings(accountOf(telegramId)))
                .filteredOn(reminder -> reminder.type() == ReminderType.BREAKFAST)
                .singleElement()
                .satisfies(reminder -> {
                    assertThat(reminder.at()).isEqualTo(java.time.LocalTime.of(7, 30));
                    assertThat(reminder.enabled()).isFalse();
                });
    }

    @Test
    void reminderComesOnceWhenItsTimeAndNotAfterTheMealIsWritten() {
        long telegramId = 700_051;
        allow(telegramId);
        UUID accountId = accountOf(telegramId);
        // Ставим ужин на минуту назад по часовому поясу человека: напоминание должно уйти прямо сейчас.
        java.time.LocalTime now = java.time.ZonedDateTime.now(reminderService.zone(accountId)).toLocalTime();
        java.time.LocalTime target = now.minusMinutes(1);
        reminderService.shift(accountId, ReminderType.DINNER,
                (int) java.time.Duration.between(ReminderType.DINNER.defaultTime(), target).toMinutes());
        reminderService.toggle(accountId, ReminderType.BREAKFAST);
        reminderService.toggle(accountId, ReminderType.LUNCH);

        reminderMailing.sendDueReminders();
        reminderMailing.sendDueReminders();

        assertThat(notificationsFor(telegramId))
                .filteredOn(text -> text.contains("Пора записать ужин"))
                .as("за день напоминаем один раз")
                .hasSize(1);
    }

    @Test
    void morningMailingReachesSubscribedUsers() {
        long telegramId = 700_041;
        allow(telegramId);
        when(canteenClient.today()).thenReturn(Optional.of(MENU));
        when(recognitionModel.adviseLunch(anyString())).thenReturn(Optional.of(new ModelLunchAdvice(
                new LunchAdvice(List.of(new LunchOption("Лёгкий", List.of("Гречка с курицей"), 300, 380, 120, null))),
                ModelUsage.unknown())));

        lunchMailing.sendDailyAdvice();

        assertThat(notificationsFor(telegramId))
                .anyMatch(text -> text.startsWith("Что сегодня взять на обед в столовой:") && text.contains("Лёгкий"));
        assertThat(jdbc.queryForList("""
                select u.kind from usage_record u join telegram_identity t on t.account_id = u.account_id
                where t.telegram_id = ?""", String.class, telegramId)).contains("LUNCH_ADVICE");
    }

    @Test
    void asksReceiverForNamesOfUsersKnownOnlyByNumber() {
        long nameless = 700_025;
        allow(nameless);

        TelegramReply list = send(TelegramInboundMessage.button(nextUpdateId(), ADMIN, "ausers:-"), null);

        assertThat(list.replaceMessage()).as("экран меню переключается на месте").isTrue();
        assertThat(list.buttons()).extracting(ReplyButton::text).contains(String.valueOf(nameless));
        assertThat(jdbc.queryForList("""
                select payload ->> 'telegramIds' from outbox_event where event_type = 'telegram.resolve-names'
                order by created_at desc limit 1""", String.class))
                .singleElement().asString().contains(String.valueOf(nameless));

        // Приёмщик узнал имя в Telegram и вернул его — в списке появляется человек, а не номер.
        ResponseEntity<String> stored = rest.exchange("/internal/telegram/names", HttpMethod.POST,
                new HttpEntity<>(List.of(new TelegramUserName(nameless, "Пётр Сидоров", "petya")), jsonHeaders()),
                String.class);
        TelegramReply afterNames = send(TelegramInboundMessage.button(nextUpdateId(), ADMIN, "ausers:-"), null);

        assertThat(stored.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(afterNames.buttons()).extracting(ReplyButton::text).contains("Пётр Сидоров (@petya)");
    }

    @Test
    void profileWizardCountsDailyNormAndDiaryShowsWhatIsLeft() {
        long telegramId = 700_030;
        allow(telegramId);
        when(recognitionModel.recognizeTextWithUsage(anyString(), any()))
                .thenReturn(new ModelRecognition(BUCKWHEAT, ModelUsage.unknown()));

        TelegramReply start = send(TelegramInboundMessage.message(nextUpdateId(), telegramId, "/profile"), null);
        TelegramReply sex = send(TelegramInboundMessage.button(nextUpdateId(), telegramId, button(start, 0)), null);
        TelegramReply age = send(TelegramInboundMessage.button(nextUpdateId(), telegramId, button(sex, 0)), null);
        TelegramReply badAge = send(TelegramInboundMessage.message(nextUpdateId(), telegramId, "сто лет"), null);
        TelegramReply height = send(TelegramInboundMessage.message(nextUpdateId(), telegramId, "33"), null);
        TelegramReply weight = send(TelegramInboundMessage.message(nextUpdateId(), telegramId, "180"), null);
        TelegramReply activity = send(TelegramInboundMessage.message(nextUpdateId(), telegramId, "80,5"), null);
        TelegramReply goal = send(TelegramInboundMessage.button(nextUpdateId(), telegramId, "pact:MEDIUM"), null);
        TelegramReply summary = send(TelegramInboundMessage.button(nextUpdateId(), telegramId, "pgoal:LOSS"), null);

        assertThat(start.buttons()).extracting(ReplyButton::text).containsExactly("Посчитать норму");
        assertThat(sex.messages()).singleElement().asString().startsWith("Посчитаю вашу норму калорий");
        assertThat(age.messages()).singleElement().asString().startsWith("Сколько вам полных лет");
        assertThat(badAge.messages()).singleElement().asString().startsWith("Не понял ответ.");
        assertThat(height.messages()).singleElement().asString().startsWith("Какой у вас рост");
        assertThat(weight.messages()).singleElement().asString().startsWith("Какой сейчас вес");
        assertThat(activity.messages()).singleElement().asString().startsWith("Сколько двигаетесь");
        assertThat(goal.messages()).singleElement().asString().startsWith("К чему идём?");
        // Мужчина 33 года, 180 см, 80,5 кг: обмен 1795, с активностью 2782, минус 15% на похудении.
        assertThat(summary.messages()).singleElement().asString()
                .contains("Записал: Мужчина, 33 года, 180 см, 80,5 кг")
                .contains("Основной обмен — 1770 ккал, с активностью выходит 2744 ккал")
                .contains("Ваша норма: 2216–2449 ккал в день");
        assertThat(jdbc.queryForObject("""
                select g.type from goal g join telegram_identity t on t.account_id = g.account_id
                where t.telegram_id = ? and g.status = 'ACTIVE'""", String.class, telegramId)).isEqualTo("LOSS");

        TelegramReply estimate = send(TelegramInboundMessage.message(nextUpdateId(), telegramId, "гречка"), null);
        send(TelegramInboundMessage.button(nextUpdateId(), telegramId, button(estimate, 0)), null);
        TelegramReply diary = send(TelegramInboundMessage.message(nextUpdateId(), telegramId, "/diary"), null);

        assertThat(diary.messages()).singleElement().asString()
                .contains("Ваша норма: 2216–2449 ккал")
                .contains("осталось 1736–2069 ккал");
    }

    @Test
    void adminWritesChangelogAndSendsItToEveryoneWithAccess() {
        long reader = 700_031;
        allow(reader);

        TelegramReply draft = send(TelegramInboundMessage.message(nextUpdateId(), ADMIN,
                "/changelog Новые кнопки\nТеперь оценку можно записать в дневник."), null);
        TelegramReply beforeSending = send(TelegramInboundMessage.message(nextUpdateId(), reader, "/changelog"), null);
        TelegramReply sent = send(TelegramInboundMessage.button(nextUpdateId(), ADMIN, button(draft, 0)), null);
        TelegramReply afterSending = send(TelegramInboundMessage.message(nextUpdateId(), reader, "/changelog"), null);

        assertThat(draft.messages()).singleElement().asString()
                .startsWith("Что нового\n\nНовые кнопки\n\nТеперь оценку можно записать в дневник.")
                .endsWith("Черновик. Разослать всем, у кого есть доступ?");
        assertThat(beforeSending.messages()).singleElement().asString()
                .as("пока не разослали, пользователь записи не видит")
                .isEqualTo("Пока ничего не публиковали.");
        assertThat(sent.messages()).singleElement().asString().startsWith("Разослал. Получателей:");
        assertThat(afterSending.messages()).singleElement().asString().contains("Новые кнопки");
        assertThat(notificationsFor(reader)).anyMatch(text -> text.contains("Новые кнопки"));
    }

    /** Что ядро отправило человеку: события остаются в outbox и после публикации. */
    private UUID accountOf(long telegramId) {
        return jdbc.queryForObject("select account_id from telegram_identity where telegram_id = ?",
                UUID.class, telegramId);
    }

    private List<String> notificationsFor(long telegramId) {
        return jdbc.queryForList("""
                select payload ->> 'text' from outbox_event
                where event_type = 'telegram.notification' and payload ->> 'chatId' = ?
                order by created_at""", String.class, String.valueOf(telegramId));
    }

    /** Бот в бета-тесте: чтобы проверять еду, пользователю сначала открывают доступ. */
    private void allow(long telegramId) {
        send(TelegramInboundMessage.message(nextUpdateId(), telegramId, "/start"), null);
        TelegramReply granted = send(
                TelegramInboundMessage.message(nextUpdateId(), ADMIN, "/grant " + telegramId), null);
        assertThat(granted.messages()).singleElement().asString().startsWith("Доступ выдан");
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

    private static HttpHeaders jsonHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(TOKEN);
        headers.setContentType(MediaType.APPLICATION_JSON);
        return headers;
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
