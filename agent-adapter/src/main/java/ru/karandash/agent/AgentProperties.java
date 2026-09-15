package ru.karandash.agent;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.unit.DataSize;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;

@ConfigurationProperties(prefix = "karandash.agent")
public record AgentProperties(
        String serviceToken,
        String cli,
        Duration timeout,
        int maxConcurrentCalls,
        Duration queueTimeout,
        int maxOutputBytes,
        int maxTextLength,
        DataSize maxPhotoSize,
        Path workDir,
        Duration healthCacheTtl,
        Claude claude,
        Codex codex
) {

    public record Claude(
            List<String> command,
            String model,
            String effort,
            BigDecimal maxBudgetUsd
    ) {
    }

    public record Codex(
            List<String> command,
            String model,
            /** Файл auth.json подписочного входа: монтируется секретом, только на чтение. */
            Path authFile,
            /** Постоянный CODEX_HOME. Нужен подписке: CLI обновляет там токен, и обновление должно сохраняться. */
            Path home
    ) {
    }
}
