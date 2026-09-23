package ru.karandash.core.canteen;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

/**
 * Столовая Строительного двора: откуда берём меню и что из него не еда.
 *
 * @param cafe             какая столовая: 1 — ЦО в Тюмени
 * @param token            токен доступа к API-шлюзу, живёт в секрете
 * @param skipCategories   категории, которые в подбор обеда не идут
 * @param cache            сколько держать меню в памяти: столовая дополняет его в течение дня
 * @param zone             часовой пояс столовой: по нему считаем будни и время утренней рассылки
 * @param weekendsOff      по выходным столовая ЦО закрыта, а её API всё равно отдаёт пятничное меню
 */
@ConfigurationProperties(prefix = "karandash.canteen")
public record CanteenProperties(
        boolean enabled,
        String baseUrl,
        int cafe,
        String token,
        Duration timeout,
        List<String> skipCategories,
        Duration cache,
        ZoneId zone,
        Boolean weekendsOff
) {

    private static final List<String> DEFAULT_SKIP = List.of(
            "Холодные напитки", "Горячие напитки", "Авторские напитки", "Конфеты", "Снеки", "Алкоголь");

    public CanteenProperties {
        baseUrl = baseUrl == null || baseUrl.isBlank()
                ? "https://api-gateway.prod.hr-holding.itlabs.io"
                : baseUrl.strip();
        cafe = cafe <= 0 ? 1 : cafe;
        timeout = timeout == null ? Duration.ofSeconds(20) : timeout;
        skipCategories = skipCategories == null || skipCategories.isEmpty() ? DEFAULT_SKIP : List.copyOf(skipCategories);
        // Дольше часа держать нельзя: в меню лежат подписанные ссылки на фотографии, они живут час.
        cache = cache == null || cache.isNegative() || cache.compareTo(Duration.ofHours(1)) > 0
                ? Duration.ofMinutes(10)
                : cache;
        zone = zone == null ? ZoneId.of("Europe/Moscow") : zone;
        weekendsOff = weekendsOff == null || weekendsOff;
    }

    /** Работает ли столовая сегодня: по выходным меню не показываем и рассылку не шлём. */
    public boolean workday() {
        return workday(LocalDate.now(zone));
    }

    boolean workday(LocalDate date) {
        if (!weekendsOff) {
            return true;
        }
        DayOfWeek day = date.getDayOfWeek();
        return day != DayOfWeek.SATURDAY && day != DayOfWeek.SUNDAY;
    }

    public boolean configured() {
        return enabled && token != null && !token.isBlank();
    }
}
