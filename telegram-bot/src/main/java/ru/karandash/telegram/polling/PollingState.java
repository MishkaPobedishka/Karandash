package ru.karandash.telegram.polling;

import ru.karandash.telegram.api.TelegramApiException;

import java.time.Clock;
import java.time.Instant;

/**
 * Последний успешный опрос Telegram и последняя ошибка — для health.
 */
public class PollingState {

    private final Clock clock;
    private volatile Instant lastPolledAt;
    private volatile TelegramApiException lastFailure;

    public PollingState() {
        this(Clock.systemUTC());
    }

    PollingState(Clock clock) {
        this.clock = clock;
    }

    void markPolled() {
        lastPolledAt = clock.instant();
        lastFailure = null;
    }

    void markFailed(TelegramApiException failure) {
        lastFailure = failure;
    }

    public Instant lastPolledAt() {
        return lastPolledAt;
    }

    public TelegramApiException lastFailure() {
        return lastFailure;
    }

    public Instant now() {
        return clock.instant();
    }
}
