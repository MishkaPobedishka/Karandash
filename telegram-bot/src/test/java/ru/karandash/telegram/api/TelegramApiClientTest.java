package ru.karandash.telegram.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;
import ru.karandash.telegram.testing.FakeTelegramServer;

import java.net.ServerSocket;
import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TelegramApiClientTest {

    private FakeTelegramServer telegram;
    private TelegramApiClient client;

    @BeforeEach
    void setUp() throws Exception {
        telegram = new FakeTelegramServer();
        client = client(telegram.baseUrl());
    }

    @AfterEach
    void tearDown() {
        telegram.close();
    }

    @Test
    void readsUpdatesAndSendsOffset() {
        telegram.enqueueUpdates("""
                [{"update_id":10,"message":{"message_id":1,"from":{"id":42,"is_bot":false},
                "chat":{"id":42,"type":"private"},"text":"суп","entities":[]}}]""");

        List<Update> updates = client.getUpdates(7, Duration.ofSeconds(1));

        assertThat(telegram.offsets).containsExactly(7L);
        assertThat(updates).singleElement().satisfies(update -> {
            assertThat(update.updateId()).isEqualTo(10);
            assertThat(update.message().chat().isPrivate()).isTrue();
            assertThat(update.message().text()).isEqualTo("суп");
        });
    }

    @Test
    void apiErrorsCarryCodeButNeverToken() {
        telegram.failMethod("getUpdates", 409, "Conflict: terminated by other getUpdates request");

        assertThatThrownBy(() -> client.getUpdates(0, Duration.ofSeconds(1)))
                .isInstanceOf(TelegramApiException.class)
                .satisfies(exception -> {
                    assertThat(((TelegramApiException) exception).errorCode()).isEqualTo(409);
                    assertThat(exception.getMessage()).contains("Conflict").doesNotContain(FakeTelegramServer.TOKEN);
                });
    }

    @Test
    void networkErrorsDoNotLeakTokenFromUrl() throws Exception {
        int closedPort;
        try (ServerSocket socket = new ServerSocket(0)) {
            closedPort = socket.getLocalPort();
        }
        TelegramApiClient unreachable = client("http://127.0.0.1:" + closedPort);

        assertThatThrownBy(() -> unreachable.sendMessage(1, "текст"))
                .isInstanceOf(TelegramApiException.class)
                .satisfies(exception -> {
                    assertThat(exception.getMessage()).doesNotContain(FakeTelegramServer.TOKEN).doesNotContain("bot");
                    assertThat(exception.getCause()).isNull();
                });
    }

    @Test
    void downloadStopsAtSizeLimit() {
        telegram.addFile("f1", "photos/file_1.jpg", new byte[2048]);

        assertThat(client.downloadFile("photos/file_1.jpg", 4096)).hasSize(2048);
        assertThatThrownBy(() -> client.downloadFile("photos/file_1.jpg", 1024))
                .isInstanceOf(TelegramApiClient.FileTooLargeException.class);
        assertThatThrownBy(() -> client.downloadFile("../../etc/passwd", 1024))
                .isInstanceOf(TelegramApiException.class);
    }

    @Test
    void truncatesLongMessages() {
        client.sendMessage(42, "а".repeat(5000));

        assertThat(telegram.sentMessages).singleElement()
                .satisfies(message -> assertThat(message.path("text").asText()).hasSize(TelegramApiClient.MESSAGE_LIMIT));
    }

    private static TelegramApiClient client(String baseUrl) {
        return new TelegramApiClient(RestClient.builder().baseUrl(baseUrl).build(), FakeTelegramServer.TOKEN,
                new ObjectMapper());
    }
}
