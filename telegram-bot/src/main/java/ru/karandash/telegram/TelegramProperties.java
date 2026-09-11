package ru.karandash.telegram;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.unit.DataSize;

import java.time.Duration;

@ConfigurationProperties(prefix = "karandash.telegram")
public record TelegramProperties(
        String token,
        String webhookSecret,
        String apiBaseUrl,
        Duration pollTimeout,
        DataSize maxPhotoSize,
        int parallelChats,
        boolean notificationsEnabled
) {

    public TelegramProperties {
        apiBaseUrl = apiBaseUrl == null || apiBaseUrl.isBlank() ? "https://api.telegram.org" : apiBaseUrl;
        pollTimeout = pollTimeout == null ? Duration.ofSeconds(30) : pollTimeout;
        maxPhotoSize = maxPhotoSize == null ? DataSize.ofMegabytes(10) : maxPhotoSize;
        parallelChats = parallelChats <= 0 ? 4 : parallelChats;
    }

    public boolean pollingEnabled() {
        return token != null && !token.isBlank();
    }
}
