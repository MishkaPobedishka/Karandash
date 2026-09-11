package ru.karandash.agent.cli;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Помнит, что провайдер модели отклонил учётные данные CLI. Отметка живёт ограниченное время,
 * чтобы разовый сбой провайдера не выключал агент навсегда.
 */
public class CliAuthState {

    private static final Duration REJECTION_TTL = Duration.ofMinutes(5);

    private final Clock clock;
    private final AtomicReference<Instant> rejectedAt = new AtomicReference<>();

    public CliAuthState() {
        this(Clock.systemUTC());
    }

    CliAuthState(Clock clock) {
        this.clock = clock;
    }

    public void markRejected() {
        rejectedAt.set(clock.instant());
    }

    public void markAccepted() {
        rejectedAt.set(null);
    }

    public boolean isRejected() {
        Instant at = rejectedAt.get();
        return at != null && at.plus(REJECTION_TTL).isAfter(clock.instant());
    }
}
