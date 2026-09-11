package ru.karandash.core.ai;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "karandash.ai")
public record AiProperties(
        boolean enabled,
        String baseUrl,
        String apiKey,
        String visionModel,
        String textModel
) {
}

