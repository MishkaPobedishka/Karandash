package ru.karandash.core.telegram;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "karandash.telegram")
public record TelegramIntakeProperties(
        int maxTextLength,
        Duration updateRetention
) {

    public TelegramIntakeProperties {
        maxTextLength = maxTextLength <= 0 ? 2000 : maxTextLength;
        updateRetention = updateRetention == null ? Duration.ofHours(48) : updateRetention;
    }
}
