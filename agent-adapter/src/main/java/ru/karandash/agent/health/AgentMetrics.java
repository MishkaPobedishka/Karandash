package ru.karandash.agent.health;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;
import ru.karandash.agent.cli.CliAuthState;

/**
 * Метрика учётных данных CLI: 1 — провайдер модели принимает их, 0 — отклонил.
 * Отдельная метрика нужна потому, что подписочный вход умеет врать: {@code login status} отвечает «вошли»,
 * а каждый вызов падает на обновлении токена.
 */
@Component
class AgentMetrics {

    AgentMetrics(MeterRegistry registry, CliAuthState authState) {
        Gauge.builder("karandash.agent.credentials.ok", authState, state -> state.isRejected() ? 0 : 1)
                .description("Принимает ли провайдер модели учётные данные CLI")
                .register(registry);
    }
}
