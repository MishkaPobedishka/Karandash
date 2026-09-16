package ru.karandash.telegram.notifications;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import ru.karandash.telegram.api.TelegramApiClient;
import ru.karandash.telegram.core.CoreClient;
import ru.karandash.telegram.testing.FakeTelegramServer;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.client.ExpectedCount.never;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/**
 * Ядро просит имена, приёмщик спрашивает их у Telegram и возвращает обратно.
 */
class NameRequestListenerTest {

    private static final String CORE_URL = "http://core:8080/internal/telegram/names";

    private final ObjectMapper objectMapper = new ObjectMapper();
    private FakeTelegramServer telegram;
    private MockRestServiceServer core;
    private NameRequestListener listener;

    @BeforeEach
    void setUp() throws Exception {
        telegram = new FakeTelegramServer();
        RestClient.Builder coreBuilder = RestClient.builder().baseUrl("http://core:8080");
        core = MockRestServiceServer.bindTo(coreBuilder).build();
        listener = new NameRequestListener(
                new TelegramApiClient(RestClient.builder().baseUrl(telegram.baseUrl()).build(),
                        FakeTelegramServer.TOKEN, objectMapper),
                new CoreClient(coreBuilder.build()),
                objectMapper);
    }

    @AfterEach
    void tearDown() {
        telegram.close();
    }

    @Test
    void asksTelegramForNamesAndReturnsWhatItKnows() {
        telegram.chats.put(42L, "{\"id\":42,\"type\":\"private\",\"first_name\":\"Иван\",\"last_name\":\"Петров\","
                + "\"username\":\"vanya\"}");
        telegram.chats.put(43L, "{\"id\":43,\"type\":\"private\",\"first_name\":\"Мария\"}");
        // 44 боту неизвестен — Telegram ответит ошибкой, и это не должно ронять всю пачку.
        core.expect(requestTo(CORE_URL))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().string(allOf(
                        containsString("\"telegramId\":42"),
                        containsString("Иван Петров"),
                        containsString("vanya"),
                        containsString("Мария"),
                        not(containsString("\"telegramId\":44")))))
                .andRespond(withSuccess("{\"saved\":2}", MediaType.APPLICATION_JSON));

        listener.onEvent(event("telegram.resolve-names", "{\"telegramIds\":[42,43,44]}"));

        core.verify();
    }

    @Test
    void staysSilentWhenNobodyIsKnown() {
        core.expect(never(), requestTo(CORE_URL));

        listener.onEvent(event("telegram.resolve-names", "{\"telegramIds\":[77]}"));

        core.verify();
    }

    @Test
    void ignoresOtherEvents() {
        core.expect(never(), requestTo(CORE_URL));

        listener.onEvent(event("telegram.notification", "{\"chatId\":42,\"text\":\"привет\"}"));

        core.verify();
        assertThat(telegram.requestPaths).isEmpty();
    }

    private static Message event(String eventType, String body) {
        MessageProperties properties = new MessageProperties();
        properties.setReceivedRoutingKey(eventType);
        return new Message(body.getBytes(StandardCharsets.UTF_8), properties);
    }
}
