package ru.karandash.contracts.ai;

import java.math.BigDecimal;

/**
 * Данные о вызове модели для учёта: кто и сколько потратил.
 * Любое поле может быть пустым, если провайдер его не сообщает.
 */
public record ModelUsage(
        String provider,
        String model,
        Integer inputTokens,
        Integer outputTokens,
        BigDecimal costUsd
) {

    public static ModelUsage unknown() {
        return new ModelUsage(null, null, null, null, null);
    }
}
