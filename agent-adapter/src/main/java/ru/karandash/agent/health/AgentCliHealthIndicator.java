package ru.karandash.agent.health;

import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;
import ru.karandash.agent.AgentProperties;
import ru.karandash.agent.cli.AgentCli;
import ru.karandash.agent.cli.CallDirectory;
import ru.karandash.agent.cli.CliAuthState;
import ru.karandash.agent.cli.CliCallException;
import ru.karandash.agent.cli.CliProcessRunner;
import ru.karandash.agent.cli.ProcessOutcome;

import java.io.IOException;
import java.time.Duration;

/**
 * CLI установлен и видит учётные данные. Проверка локальная, модель не вызывается:
 * годность ключа подтверждает только настоящий вызов, поэтому отказ провайдера в последнем вызове тоже переводит в DOWN.
 */
@Component
public class AgentCliHealthIndicator implements HealthIndicator {

    private static final Duration CHECK_TIMEOUT = Duration.ofSeconds(20);
    private static final int CHECK_OUTPUT_LIMIT = 64 * 1024;

    private final AgentCli cli;
    private final CliProcessRunner runner;
    private final CliAuthState authState;
    private final AgentProperties properties;

    private Health cached;
    private long cachedAtNanos;

    public AgentCliHealthIndicator(
            AgentCli cli,
            CliProcessRunner runner,
            CliAuthState authState,
            AgentProperties properties
    ) {
        this.cli = cli;
        this.runner = runner;
        this.authState = authState;
        this.properties = properties;
    }

    @Override
    public Health health() {
        if (authState.isRejected()) {
            return down("Провайдер модели отклонил учётные данные CLI в последнем вызове");
        }
        return cachedCheck();
    }

    private synchronized Health cachedCheck() {
        long now = System.nanoTime();
        if (cached == null || now - cachedAtNanos > properties.healthCacheTtl().toNanos()) {
            cached = check();
            cachedAtNanos = now;
        }
        return cached;
    }

    private Health check() {
        if (!runner.hasAnyVariable(cli.credentialVariables()) && !cli.hasFileCredentials()) {
            return down("Не заданы учётные данные CLI: " + String.join(" или ", cli.credentialVariables()));
        }
        try (CallDirectory directory = CallDirectory.create(properties.workDir())) {
            ProcessOutcome outcome = runner.run(cli.healthCheck(directory), directory, line -> {
            }, CHECK_TIMEOUT, CHECK_OUTPUT_LIMIT);
            return outcome.exitCode() == 0
                    ? Health.up().withDetail("cli", cli.provider()).build()
                    : down("CLI не видит учётные данные, код выхода " + outcome.exitCode());
        } catch (CliCallException exception) {
            return down("CLI недоступен: " + exception.getMessage());
        } catch (IOException exception) {
            return down("Не удалось подготовить каталог проверки");
        }
    }

    private Health down(String reason) {
        return Health.down().withDetail("cli", cli.provider()).withDetail("reason", reason).build();
    }
}
