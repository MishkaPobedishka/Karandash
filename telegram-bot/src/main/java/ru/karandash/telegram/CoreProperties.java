package ru.karandash.telegram;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "karandash.core")
public record CoreProperties(
        String baseUrl,
        String serviceToken,
        Duration connectTimeout,
        Duration readTimeout
) {

    public CoreProperties {
        connectTimeout = connectTimeout == null ? Duration.ofSeconds(5) : connectTimeout;
        readTimeout = readTimeout == null ? Duration.ofSeconds(180) : readTimeout;
    }
}
