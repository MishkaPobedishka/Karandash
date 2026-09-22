package ru.karandash.telegram.polling;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import ru.karandash.telegram.api.PhotoSize;
import ru.karandash.telegram.api.TelegramApiClient;
import ru.karandash.telegram.api.Update;
import ru.karandash.telegram.core.CoreClient;
import ru.karandash.telegram.testing.FakeTelegramServer;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.client.ExpectedCount.never;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class MessageHandlerTest {

    private static final String CORE_URL = "http://core:8080/internal/telegram/messages";
    private static final String REPLY = "{\"duplicate\":false,\"messages\":[\"Похоже на: гречка\"]}";

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
    private FakeTelegramServer telegram;
    private MockRestServiceServer core;
    private MessageHandler handler;

    @BeforeEach
    void setUp() throws Exception {
        telegram = new FakeTelegramServer();
        RestClient.Builder coreBuilder = RestClient.builder()
                .baseUrl("http://core:8080")
                .defaultHeaders(headers -> headers.setBearerAuth("core-token"));
        core = MockRestServiceServer.bindTo(coreBuilder).build();
        handler = new MessageHandler(
                new TelegramApiClient(RestClient.builder().baseUrl(telegram.baseUrl()).build(),
                        FakeTelegramServer.TOKEN, objectMapper),
                new CoreClient(coreBuilder.build()),
                scheduler,
                1024 * 1024);
    }

    @AfterEach
    void tearDown() {
        telegram.close();
        scheduler.shutdownNow();
    }

    @Test
    void forwardsPrivateTextToCoreAndRepliesToUser() throws Exception {
        core.expect(requestTo(CORE_URL))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Authorization", "Bearer core-token"))
                .andExpect(content().string(allOf(
                        containsString("\"updateId\":10"),
                        containsString("\"telegramUserId\":42"),
                        containsString("гречка с курицей"))))
                .andRespond(withSuccess(REPLY, MediaType.APPLICATION_JSON));

        handler.handle(update(10, "private", "{\"text\":\"гречка с курицей\"}"));

        core.verify();
        assertThat(telegram.sentMessages).singleElement().satisfies(message -> {
            assertThat(message.path("chat_id").asLong()).isEqualTo(42);
            assertThat(message.path("text").asText()).isEqualTo("Похоже на: гречка");
        });
    }

    @Test
    void downloadsPhotoInMemoryAndSendsItWithCaption() throws Exception {
        telegram.addFile("big", "photos/big.jpg", "BIG-PHOTO".getBytes(StandardCharsets.UTF_8));
        telegram.addFile("medium", "photos/medium.jpg", "MEDIUM-PHOTO".getBytes(StandardCharsets.UTF_8));
        core.expect(requestTo(CORE_URL))
                .andExpect(content().string(allOf(
                        containsString("MEDIUM-PHOTO"),
                        not(containsString("BIG-PHOTO")),
                        containsString("обед"))))
                .andRespond(withSuccess(REPLY, MediaType.APPLICATION_JSON));

        handler.handle(update(11, "private", """
                {"caption":"обед","photo":[
                {"file_id":"small","width":90,"height":67,"file_size":1000},
                {"file_id":"medium","width":1280,"height":960,"file_size":12},
                {"file_id":"big","width":2560,"height":1920,"file_size":9}]}"""));

        core.verify();
        assertThat(telegram.sentMessages).hasSize(1);
    }

    @Test
    void ignoresGroupChatsAndBots() throws Exception {
        core.expect(never(), requestTo(CORE_URL));

        handler.handle(update(12, "group", "{\"text\":\"суп\"}"));
        handler.handle(update(13, "supergroup", "{\"text\":\"суп\"}"));

        core.verify();
        assertThat(telegram.sentMessages).isEmpty();
    }

    @Test
    void answersUnsupportedMessagesLocally() throws Exception {
        core.expect(never(), requestTo(CORE_URL));

        handler.handle(update(14, "private", "{\"sticker\":{\"file_id\":\"s\"}}"));

        core.verify();
        assertThat(telegram.sentMessages).singleElement()
                .satisfies(message -> assertThat(message.path("text").asText()).isEqualTo(BotTexts.UNSUPPORTED));
    }

    @Test
    void staysSilentOnDuplicate() throws Exception {
        core.expect(requestTo(CORE_URL))
                .andRespond(withSuccess("{\"duplicate\":true,\"messages\":[]}", MediaType.APPLICATION_JSON));

        handler.handle(update(15, "private", "{\"text\":\"суп\"}"));

        assertThat(telegram.sentMessages).isEmpty();
    }

    @Test
    void tellsUserWhenCoreIsUnavailable() throws Exception {
        core.expect(requestTo(CORE_URL)).andRespond(withStatus(HttpStatus.BAD_GATEWAY));

        handler.handleSafely(update(16, "private", "{\"text\":\"суп\"}"));

        assertThat(telegram.sentMessages).singleElement()
                .satisfies(message -> assertThat(message.path("text").asText()).isEqualTo(BotTexts.CORE_UNAVAILABLE));
    }

    @Test
    void choosesPhotoSizeWithinLimits() {
        List<PhotoSize> sizes = List.of(
                new PhotoSize("s", 90, 67, 1_000L),
                new PhotoSize("m", 1280, 960, 150_000L),
                new PhotoSize("l", 2560, 1920, 600_000L));

        assertThat(MessageHandler.choosePhotoSize(sizes, 1_000_000).fileId()).isEqualTo("m");
        assertThat(MessageHandler.choosePhotoSize(List.of(sizes.get(2)), 1_000_000).fileId()).isEqualTo("l");
        assertThat(MessageHandler.choosePhotoSize(List.of(sizes.get(2)), 100_000)).isNull();
    }

    @Test
    void putsCoreButtonsUnderTheLastMessage() throws Exception {
        core.expect(requestTo(CORE_URL)).andRespond(withSuccess("""
                {"duplicate":false,"messages":["Похоже на: гречка"],
                "buttons":[{"text":"✓ Записать в дневник","data":"save:1-2"},
                {"text":"✗ Не записывать","data":"drop:1-2"}]}""", MediaType.APPLICATION_JSON));

        handler.handle(update(20, "private", "{\"text\":\"гречка\"}"));

        assertThat(telegram.sentMessages).singleElement().satisfies(message -> {
            JsonNode keyboard = message.path("reply_markup").path("inline_keyboard");
            assertThat(keyboard).as("кнопки идут одна под другой").hasSize(2);
            assertThat(keyboard.get(0).get(0).path("text").asText()).isEqualTo("✓ Записать в дневник");
            assertThat(keyboard.get(0).get(0).path("callback_data").asText()).isEqualTo("save:1-2");
            assertThat(keyboard.get(1).get(0).path("callback_data").asText()).isEqualTo("drop:1-2");
        });
    }

    @Test
    void forwardsButtonPressAndTakesKeyboardAway() throws Exception {
        core.expect(requestTo(CORE_URL))
                .andExpect(content().string(allOf(
                        containsString("\"callbackData\":\"save:7f1c\""),
                        containsString("\"telegramUserId\":42"))))
                .andRespond(withSuccess("{\"duplicate\":false,\"messages\":[\"Записал в дневник.\"]}",
                        MediaType.APPLICATION_JSON));

        handler.handle(objectMapper.readValue("""
                {"update_id":21,"callback_query":{"id":"cb-1","from":{"id":42,"is_bot":false},
                "data":"save:7f1c","message":{"message_id":55,"chat":{"id":42,"type":"private"}}}}""", Update.class));

        core.verify();
        assertThat(telegram.answeredCallbacks).singleElement()
                .satisfies(call -> assertThat(call.path("callback_query_id").asText()).isEqualTo("cb-1"));
        assertThat(telegram.editedMarkups).singleElement().satisfies(edit -> {
            assertThat(edit.path("message_id").asLong()).isEqualTo(55);
            assertThat(edit.path("reply_markup").path("inline_keyboard")).isEmpty();
        });
        assertThat(telegram.sentMessages).singleElement()
                .satisfies(message -> assertThat(message.path("text").asText()).isEqualTo("Записал в дневник."));
    }

    @Test
    void menuScreenReplacesTheSameMessageInsteadOfSendingNewOne() throws Exception {
        core.expect(requestTo(CORE_URL)).andRespond(withSuccess("""
                {"duplicate":false,"messages":["Панель администратора. Заявок: 0, с доступом: 3."],
                "buttons":[{"text":"Заявки (0)","data":"areqs:-"}],"replaceMessage":true}""",
                MediaType.APPLICATION_JSON));

        handler.handle(objectMapper.readValue("""
                {"update_id":24,"callback_query":{"id":"cb-4","from":{"id":42,"is_bot":false},
                "data":"apanel:-","message":{"message_id":60,"chat":{"id":42,"type":"private"}}}}""", Update.class));

        assertThat(telegram.sentMessages).as("новых сообщений не появляется").isEmpty();
        assertThat(telegram.editedMessages).singleElement().satisfies(edit -> {
            assertThat(edit.path("message_id").asLong()).isEqualTo(60);
            assertThat(edit.path("text").asText()).startsWith("Панель администратора.");
            assertThat(edit.path("reply_markup").path("inline_keyboard").get(0).get(0).path("callback_data").asText())
                    .isEqualTo("areqs:-");
        });
    }

    @Test
    void fallsBackToNewMessageWhenTelegramRefusesToEdit() throws Exception {
        telegram.failMethod("editMessageText", 400, "Bad Request: message can't be edited");
        core.expect(requestTo(CORE_URL)).andRespond(withSuccess("""
                {"duplicate":false,"messages":["Панель администратора."],"buttons":[],"replaceMessage":true}""",
                MediaType.APPLICATION_JSON));

        handler.handle(objectMapper.readValue("""
                {"update_id":25,"callback_query":{"id":"cb-5","from":{"id":42,"is_bot":false},
                "data":"apanel:-","message":{"message_id":61,"chat":{"id":42,"type":"private"}}}}""", Update.class));

        assertThat(telegram.sentMessages).singleElement()
                .satisfies(sent -> assertThat(sent.path("text").asText()).isEqualTo("Панель администратора."));
    }

    @Test
    void answersButtonPressEvenWhenCoreIsDown() throws Exception {
        core.expect(requestTo(CORE_URL)).andRespond(withStatus(HttpStatus.BAD_GATEWAY));

        handler.handleSafely(objectMapper.readValue("""
                {"update_id":22,"callback_query":{"id":"cb-2","from":{"id":42,"is_bot":false},
                "data":"save:7f1c","message":{"message_id":56,"chat":{"id":42,"type":"private"}}}}""", Update.class));

        assertThat(telegram.answeredCallbacks).as("часы на кнопке гасим до похода в ядро").hasSize(1);
        assertThat(telegram.sentMessages).singleElement()
                .satisfies(message -> assertThat(message.path("text").asText()).isEqualTo(BotTexts.CORE_UNAVAILABLE));
    }

    @Test
    void ignoresButtonPressFromGroupChat() throws Exception {
        core.expect(never(), requestTo(CORE_URL));

        handler.handle(objectMapper.readValue("""
                {"update_id":23,"callback_query":{"id":"cb-3","from":{"id":42,"is_bot":false},
                "data":"save:7f1c","message":{"message_id":57,"chat":{"id":-100,"type":"supergroup"}}}}""",
                Update.class));

        core.verify();
        assertThat(telegram.answeredCallbacks).isEmpty();
        assertThat(telegram.sentMessages).isEmpty();
    }

    @Test
    void sendsDishPhotosAsAlbumBeforeTheTextWithButtons() throws Exception {
        core.expect(requestTo(CORE_URL)).andRespond(withSuccess("""
                {"duplicate":false,"messages":["Сегодня в столовой:"],
                "buttons":[{"text":"Показать всё меню","data":"lmenu:-"}],
                "photos":[{"url":"https://canteen/1.jpg","caption":"Борщ — 120 ₽"},
                {"url":"https://canteen/2.jpg","caption":"Котлета — 180 ₽"}]}""",
                MediaType.APPLICATION_JSON));

        handler.handle(objectMapper.readValue("""
                {"update_id":26,"callback_query":{"id":"cb-6","from":{"id":42,"is_bot":false},
                "data":"lshow:-","message":{"message_id":62,"chat":{"id":42,"type":"private"}}}}""", Update.class));

        assertThat(telegram.sentAlbums).singleElement().satisfies(album -> {
            assertThat(album.path("chat_id").asLong()).isEqualTo(42);
            assertThat(album.path("media")).hasSize(2);
            assertThat(album.path("media").get(0).path("type").asText()).isEqualTo("photo");
            assertThat(album.path("media").get(0).path("media").asText()).isEqualTo("https://canteen/1.jpg");
            assertThat(album.path("media").get(1).path("caption").asText()).isEqualTo("Котлета — 180 ₽");
        });
        assertThat(telegram.sentMessages).singleElement().satisfies(message -> {
            assertThat(message.path("text").asText()).isEqualTo("Сегодня в столовой:");
            assertThat(message.path("reply_markup").path("inline_keyboard").get(0).get(0).path("callback_data")
                    .asText()).isEqualTo("lmenu:-");
        });
    }

    @Test
    void sendsTextEvenWhenTelegramRefusesThePhotos() throws Exception {
        telegram.failMethod("sendMediaGroup", 400, "Bad Request: wrong file identifier");
        core.expect(requestTo(CORE_URL)).andRespond(withSuccess("""
                {"duplicate":false,"messages":["Сегодня в столовой:"],"buttons":[],
                "photos":[{"url":"https://canteen/1.jpg","caption":"Борщ"},
                {"url":"https://canteen/2.jpg","caption":"Котлета"}]}""",
                MediaType.APPLICATION_JSON));

        handler.handle(objectMapper.readValue("""
                {"update_id":27,"callback_query":{"id":"cb-7","from":{"id":42,"is_bot":false},
                "data":"lshow:-","message":{"message_id":63,"chat":{"id":42,"type":"private"}}}}""", Update.class));

        assertThat(telegram.sentMessages).singleElement()
                .satisfies(message -> assertThat(message.path("text").asText()).isEqualTo("Сегодня в столовой:"));
    }

    private Update update(long updateId, String chatType, String messageFields) throws Exception {
        String json = """
                {"update_id":%d,"message":{"message_id":1,"from":{"id":42,"is_bot":false},
                "chat":{"id":42,"type":"%s"},%s}}""".formatted(updateId, chatType, messageFields.substring(1,
                messageFields.length() - 1));
        return objectMapper.readValue(json, Update.class);
    }
}
