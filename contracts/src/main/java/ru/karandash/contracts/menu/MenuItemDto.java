package ru.karandash.contracts.menu;

import java.math.BigDecimal;
import java.time.Instant;

public record MenuItemDto(
        MenuSource source,
        String externalId,
        String name,
        String portion,
        Integer kcalMin,
        Integer kcalMax,
        BigDecimal proteinGrams,
        BigDecimal fatGrams,
        BigDecimal carbsGrams,
        BigDecimal price,
        String url,
        boolean available,
        Instant fetchedAt,
        Instant expiresAt
) {
}

