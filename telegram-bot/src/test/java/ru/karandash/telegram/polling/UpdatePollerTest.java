package ru.karandash.telegram.polling;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;
import ru.karandash.telegram.api.TelegramApiClient;
import ru.karandash.telegram.api.Update;
import ru.karandash.telegram.testing.FakeTelegramServer;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.assertj.core.api.Assertions.assertThat;

class UpdatePollerTest {

    private FakeTelegramServer telegram;
    private ExecutorService executor;

    @BeforeEach
    void setUp() throws Exception {
        telegram = new FakeTelegramServer();
        executor = Executors.newFixedThreadPool(4);
    }

    @AfterEach
    void tearDown() {
        telegram.close();
        executor.shutdownNow();
    }

    @Test
    void confirmsOffsetOnlyAfterWholeBatchIsProcessed() throws Exception {
        List<Long> handled = new CopyOnWriteArrayList<>();
        MessageHandler recordingHandler = new MessageHandler(null, null, null, 0) {
            @Override
            public void handleSafely(Update update) {
                handled.add(update.updateId());
            }
        };
        TelegramApiClient client = new TelegramApiClient(RestClient.builder().baseUrl(telegram.baseUrl()).build(),
                FakeTelegramServer.TOKEN, new ObjectMapper());
        PollingState state = new PollingState();
        UpdatePoller poller = new UpdatePoller(client, new UpdateProcessor(recordingHandler, executor), state,
                Duration.ofSeconds(1), true);
        telegram.enqueueUpdates("""
                [{"update_id":10,"message":{"message_id":1,"chat":{"id":1,"type":"private"},"text":"a"}},
                 {"update_id":12,"message":{"message_id":2,"chat":{"id":2,"type":"private"},"text":"b"}},
                 {"update_id":11,"message":{"message_id":3,"chat":{"id":1,"type":"private"},"text":"c"}}]""");

        poller.pollOnce();
        poller.pollOnce();

        assertThat(handled).containsExactlyInAnyOrder(10L, 11L, 12L);
        assertThat(handled.indexOf(10L)).as("сообщения одного чата — по порядку").isLessThan(handled.indexOf(11L));
        assertThat(telegram.offsets).containsExactly(0L, 13L);
        assertThat(state.lastPolledAt()).isNotNull();
    }
}
