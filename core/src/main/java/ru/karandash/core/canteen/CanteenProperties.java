package ru.karandash.core.canteen;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.List;

/**
 * Столовая Строительного двора: откуда берём меню и что из него не еда.
 *
 * @param cafe             какая столовая: 1 — ЦО в Тюмени
 * @param token            токен доступа к API-шлюзу, живёт в секрете
 * @param skipCategories   категории, которые в подбор обеда не идут
 */
@ConfigurationProperties(prefix = "karandash.canteen")
public record CanteenProperties(
        boolean enabled,
        String baseUrl,
        int cafe,
        String token,
        Duration timeout,
        List<String> skipCategories
) {

    private static final List<String> DEFAULT_SKIP = List.of(
            "Холодные напитки", "Горячие напитки", "Авторские напитки", "Конфеты", "Снеки");

    public CanteenProperties {
        baseUrl = baseUrl == null || baseUrl.isBlank()
                ? "https://api-gateway.prod.hr-holding.itlabs.io"
                : baseUrl.strip();
        cafe = cafe <= 0 ? 1 : cafe;
        timeout = timeout == null ? Duration.ofSeconds(20) : timeout;
        skipCategories = skipCategories == null || skipCategories.isEmpty() ? DEFAULT_SKIP : List.copyOf(skipCategories);
    }

    public boolean configured() {
        return enabled && token != null && !token.isBlank();
    }
}
