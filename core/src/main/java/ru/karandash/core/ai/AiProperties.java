package ru.karandash.core.ai;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "karandash.ai")
public record AiProperties(
        AiProvider provider,
        String baseUrl,
        String apiKey,
        String visionModel,
        String textModel,
        Agent agent
) {

    public AiProperties {
        provider = provider == null ? AiProvider.DISABLED : provider;
    }

    /** Агент-адаптер: отдельный сервис, который запускает CLI-агента. */
    public record Agent(
            String baseUrl,
            String serviceToken,
            Duration connectTimeout,
            Duration readTimeout
    ) {
    }
}
