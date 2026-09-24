package ru.karandash.telegram.polling;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.SmartLifecycle;
import ru.karandash.telegram.api.TelegramApiClient;
import ru.karandash.telegram.api.TelegramApiException;
import ru.karandash.telegram.api.Update;

import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * Long polling Telegram. Offset подтверждается следующим {@code getUpdates} только после обработки всей пачки:
 * если приёмщик упадёт посреди пачки, Telegram доставит её снова, а ядро отсечёт уже обработанные апдейты.
 * Работает ровно одна реплика — второй поллер получит от Telegram 409.
 */
public class UpdatePoller implements SmartLifecycle {

    private static final Logger log = LoggerFactory.getLogger(UpdatePoller.class);
    private static final Duration MAX_BACKOFF = Duration.ofSeconds(60);

    private final TelegramApiClient telegram;
    private final UpdateProcessor processor;
    private final PollingState state;
    private final Duration pollTimeout;
    private final boolean enabled;

    /** Что видно в кнопке «Меню» Telegram: порядок тот же, что и в главном экране бота. */
    private static final List<Map<String, String>> COMMANDS = List.of(
            Map.of("command", "menu", "description", "Меню бота"),
            Map.of("command", "lunch", "description", "Что взять на обед в столовой"),
            Map.of("command", "diary", "description", "Дневник за сегодня"),
            Map.of("command", "reminders", "description", "Напоминания и итоги дня"),
            Map.of("command", "profile", "description", "Телосложение, цель и норма калорий"),
            Map.of("command", "changelog", "description", "Что нового в боте"),
            Map.of("command", "help", "description", "Как пользоваться"));

    private volatile boolean running;
    private Thread thread;
    private long offset;

    public UpdatePoller(
            TelegramApiClient telegram,
            UpdateProcessor processor,
            PollingState state,
            Duration pollTimeout,
            boolean enabled
    ) {
        this.telegram = telegram;
        this.processor = processor;
        this.state = state;
        this.pollTimeout = pollTimeout;
        this.enabled = enabled;
    }

    @Override
    public synchronized void start() {
        if (!enabled) {
            log.warn("TELEGRAM_BOT_TOKEN не задан — приём апдейтов из Telegram выключен");
            return;
        }
        publishCommands();
        running = true;
        thread = Thread.ofPlatform().name("telegram-poller").start(this::loop);
    }

    /** Список команд обновляем при старте: Telegram помнит его сам, ошибка здесь приёму не мешает. */
    private void publishCommands() {
        try {
            telegram.setMyCommands(COMMANDS);
        } catch (RuntimeException exception) {
            log.warn("Список команд не обновлён: {}", exception.getMessage());
        }
    }

    @Override
    public synchronized void stop() {
        running = false;
        if (thread != null) {
            thread.interrupt();
            try {
                thread.join(pollTimeout.plusSeconds(5).toMillis());
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
            }
        }
    }

    @Override
    public boolean isRunning() {
        return running;
    }

    long offset() {
        return offset;
    }

    void pollOnce() throws InterruptedException {
        List<Update> updates = telegram.getUpdates(offset, pollTimeout);
        state.markPolled();
        if (updates.isEmpty()) {
            return;
        }
        processor.process(updates);
        offset = updates.stream().mapToLong(Update::updateId).max().orElseThrow() + 1;
    }

    private void loop() {
        Duration backoff = Duration.ofSeconds(1);
        while (running) {
            try {
                pollOnce();
                backoff = Duration.ofSeconds(1);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                return;
            } catch (TelegramApiException exception) {
                state.markFailed(exception);
                if (exception.isUnauthorized()) {
                    log.error("Telegram отклонил токен бота — проверьте TELEGRAM_BOT_TOKEN");
                } else if (exception.errorCode() != null && exception.errorCode() == 409) {
                    log.error("Telegram вернул 409: работает второй приёмщик или у бота включён webhook");
                } else {
                    log.warn("Сбой приёма апдейтов: {}", exception.getMessage());
                }
                Duration pause = exception.retryAfterSeconds() != null
                        ? Duration.ofSeconds(exception.retryAfterSeconds())
                        : backoff;
                backoff = backoff.multipliedBy(2).compareTo(MAX_BACKOFF) > 0 ? MAX_BACKOFF : backoff.multipliedBy(2);
                if (!sleep(pause)) {
                    return;
                }
            } catch (RuntimeException exception) {
                log.error("Сбой цикла приёма апдейтов: {}", exception.getClass().getSimpleName());
                if (!sleep(backoff)) {
                    return;
                }
            }
        }
    }

    private boolean sleep(Duration pause) {
        try {
            Thread.sleep(pause);
            return true;
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return false;
        }
    }
}
