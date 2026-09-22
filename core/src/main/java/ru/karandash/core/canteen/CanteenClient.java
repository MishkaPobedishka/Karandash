package ru.karandash.core.canteen;

import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Меню столовой из API-шлюза холдинга. Эндпоинт отдаёт то, что есть сегодня, — дат в ответе нет.
 * Токен доступа в логи не попадает.
 */
@Service
@EnableConfigurationProperties(CanteenProperties.class)
public class CanteenClient {

    private static final Logger log = LoggerFactory.getLogger(CanteenClient.class);
    /**
     * Меню за день не меняется, поэтому держим его в памяти. Срок короткий: в меню лежат подписанные
     * ссылки на фотографии, они живут час, и отдавать почти просроченные не хочется.
     */
    private static final Duration CACHE_FOR = Duration.ofMinutes(10);

    private final CanteenProperties properties;
    private final RestClient restClient;
    private final AtomicReference<CachedMenu> cache = new AtomicReference<>();

    public CanteenClient(CanteenProperties properties, RestClient.Builder restClientBuilder) {
        this.properties = properties;
        this.restClient = restClientBuilder.baseUrl(properties.baseUrl()).build();
    }

    /** Сегодняшнее меню без напитков и прочего, что не обед. Пусто — значит взять его не удалось. */
    public Optional<CanteenMenu> today() {
        if (!properties.configured()) {
            log.debug("Столовая выключена или без токена — меню не запрашиваем");
            return Optional.empty();
        }
        CachedMenu cached = cache.get();
        if (cached != null && Duration.between(cached.takenAt(), Instant.now()).compareTo(CACHE_FOR) < 0) {
            return Optional.of(cached.menu());
        }
        try {
            JsonNode body = restClient.get()
                    .uri(builder -> builder.path("/api/v1/canteen/menu/")
                            .queryParam("cafe", properties.cafe()).build())
                    .header("Authorization", "Bearer " + properties.token())
                    .accept(MediaType.APPLICATION_JSON)
                    .retrieve()
                    .body(JsonNode.class);
            CanteenMenu menu = parse(body);
            log.info("Меню столовой {}: блюд {}", properties.cafe(), menu.dishes().size());
            if (menu.isEmpty()) {
                return Optional.empty();
            }
            cache.set(new CachedMenu(menu, Instant.now()));
            return Optional.of(menu);
        } catch (RuntimeException exception) {
            // В сообщении исключения может оказаться URL с параметрами — берём только тип ошибки.
            log.warn("Меню столовой не получено: {}", exception.getClass().getSimpleName());
            return Optional.empty();
        }
    }

    CanteenMenu parse(JsonNode body) {
        List<CanteenDish> dishes = new ArrayList<>();
        if (body == null) {
            return new CanteenMenu(dishes);
        }
        for (JsonNode category : body.path("result").path("categories")) {
            String name = category.path("name").asText("");
            if (properties.skipCategories().stream().anyMatch(skip -> skip.equalsIgnoreCase(name))) {
                continue;
            }
            for (JsonNode product : category.path("products")) {
                JsonNode nutrition = product.path("nutritionalInfo");
                dishes.add(new CanteenDish(
                        name,
                        product.path("name").asText("").strip(),
                        number(product.path("price")),
                        number(product.path("weight")),
                        number(nutrition.path("calories")),
                        number(nutrition.path("proteins")),
                        number(nutrition.path("fats")),
                        number(nutrition.path("carbohydrates")),
                        photo(product)));
            }
        }
        dishes.removeIf(dish -> dish.name().isBlank());
        return new CanteenMenu(dishes);
    }

    /** Полноразмерная фотография блюда; если её нет — миниатюра из списка. */
    private static String photo(JsonNode product) {
        String original = text(product.path("originalImage"));
        return original != null && !original.isBlank() ? original : text(product.path("image"));
    }

    private static String text(JsonNode node) {
        return node == null || node.isMissingNode() || node.isNull() ? null : node.asText(null);
    }

    private record CachedMenu(CanteenMenu menu, Instant takenAt) {
    }

    private static BigDecimal number(JsonNode node) {
        return node == null || node.isMissingNode() || node.isNull() ? null : node.decimalValue();
    }
}
