package ru.karandash.core.canteen;

import org.springframework.stereotype.Service;
import ru.karandash.core.diary.DiaryDay;
import ru.karandash.core.diary.DiaryService;
import ru.karandash.core.profile.DailyTarget;
import ru.karandash.core.profile.ProfileService;
import ru.karandash.core.usage.UsageRecorder;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Подбор обеда для конкретного человека: собирает его дневник и норму, зовёт модель и учитывает вызов.
 */
@Service
public class LunchContext {

    private static final String USAGE_KIND = "LUNCH_ADVICE";
    /**
     * Подбор стоит вызова модели и почти полминуты ожидания, поэтому держим готовый ответ в памяти:
     * вторая кнопка и повторное нажатие отвечают сразу. Кэш сбрасывается, как только человек
     * что-то записал в дневник, — иначе совет считался бы от устаревшего остатка.
     */
    private static final Duration CACHE_FOR = Duration.ofHours(2);

    private final LunchAdvisor advisor;
    private final DiaryService diary;
    private final ProfileService profiles;
    private final UsageRecorder usageRecorder;
    private final Map<UUID, Cached> cache = new ConcurrentHashMap<>();

    public LunchContext(
            LunchAdvisor advisor,
            DiaryService diary,
            ProfileService profiles,
            UsageRecorder usageRecorder
    ) {
        this.advisor = advisor;
        this.diary = diary;
        this.profiles = profiles;
        this.usageRecorder = usageRecorder;
    }

    public Optional<LunchSuggestion> suggest(UUID accountId, CanteenMenu menu) {
        DiaryDay today = diary.today(accountId);
        Cached cached = cache.get(accountId);
        if (cached != null && cached.stillGood(today)) {
            return Optional.of(withFreshPhotos(cached.suggestion(), menu));
        }
        long startedAt = System.nanoTime();
        Optional<DailyTarget> target = profiles.dailyTarget(accountId);
        Optional<LunchSuggestion> suggestion = advisor.advise(menu, today, target);
        suggestion.ifPresent(result -> {
            usageRecorder.record(accountId, USAGE_KIND, provider(result), result.usage(),
                    Duration.ofNanos(System.nanoTime() - startedAt));
            cache.put(accountId, new Cached(result, today, Instant.now()));
        });
        return suggestion;
    }

    /**
     * Из кэша блюда приходят со вчерашними ссылками на фотографии — подставляем свежие из текущего меню.
     * Блюдо, которого в меню уже нет, оставляем как есть: текст совета от этого не портится.
     */
    private static LunchSuggestion withFreshPhotos(LunchSuggestion suggestion, CanteenMenu menu) {
        List<LunchSuggestion.Option> options = new ArrayList<>();
        for (LunchSuggestion.Option option : suggestion.options()) {
            List<CanteenDish> dishes = option.dishes().stream()
                    .map(dish -> menu.find(dish.name()).orElse(dish))
                    .toList();
            options.add(new LunchSuggestion.Option(option.title(), dishes, option.kcalMin(), option.kcalMax(),
                    option.price(), option.why()));
        }
        return new LunchSuggestion(options, suggestion.usage());
    }

    /** Готовый подбор и дневник, по которому он собран. */
    private record Cached(LunchSuggestion suggestion, DiaryDay diary, Instant at) {

        boolean stillGood(DiaryDay today) {
            return diary.date().equals(today.date())
                    && diary.kcalMin() == today.kcalMin()
                    && diary.kcalMax() == today.kcalMax()
                    && Duration.between(at, Instant.now()).compareTo(CACHE_FOR) < 0;
        }
    }

    /** Сколько калорий у человека осталось на сегодня — этим подписываем совет. */
    public Optional<LunchRemaining> remaining(UUID accountId) {
        return profiles.dailyTarget(accountId).map(target -> {
            DiaryDay today = diary.today(accountId);
            return new LunchRemaining(
                    Math.max(0, target.kcalMin() - today.kcalMax()),
                    Math.max(0, target.kcalMax() - today.kcalMin()));
        });
    }

    private static String provider(LunchSuggestion suggestion) {
        return suggestion.usage() == null || suggestion.usage().provider() == null
                ? "unknown"
                : suggestion.usage().provider();
    }
}
