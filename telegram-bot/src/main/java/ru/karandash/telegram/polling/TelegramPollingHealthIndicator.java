package ru.karandash.telegram.polling;

import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;
import ru.karandash.telegram.TelegramProperties;
import ru.karandash.telegram.api.TelegramApiException;

import java.time.Duration;
import java.time.Instant;

/**
 * Приём из Telegram жив: токен принят, и опрос проходил недавно.
 */
@Component
public class TelegramPollingHealthIndicator implements HealthIndicator {

    private final PollingState state;
    private final TelegramProperties properties;

    public TelegramPollingHealthIndicator(PollingState state, TelegramProperties properties) {
        this.state = state;
        this.properties = properties;
    }

    @Override
    public Health health() {
        if (!properties.pollingEnabled()) {
            return Health.up().withDetail("polling", "выключен: не задан токен бота").build();
        }
        TelegramApiException failure = state.lastFailure();
        if (failure != null && failure.isUnauthorized()) {
            return Health.down().withDetail("reason", "Telegram отклонил токен бота").build();
        }
        Instant lastPolledAt = state.lastPolledAt();
        Duration staleAfter = properties.pollTimeout().multipliedBy(3).plusSeconds(30);
        if (lastPolledAt != null && lastPolledAt.plus(staleAfter).isBefore(state.now())) {
            return Health.down().withDetail("reason", "нет успешного опроса Telegram дольше " + staleAfter).build();
        }
        return Health.up().build();
    }
}
