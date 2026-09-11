package ru.karandash.core.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Внутренний API ядра ({@code /internal/**}) — для приёмщика Telegram и других сервисов контура.
 */
@ConfigurationProperties(prefix = "karandash.internal-api")
public record InternalApiProperties(
        String serviceToken
) {
}
