package ru.karandash.core.canteen;

import org.springframework.stereotype.Service;
import ru.karandash.core.diary.DiaryDay;
import ru.karandash.core.diary.DiaryService;
import ru.karandash.core.profile.DailyTarget;
import ru.karandash.core.profile.ProfileService;
import ru.karandash.core.usage.UsageRecorder;

import java.time.Duration;
import java.util.Optional;
import java.util.UUID;

/**
 * Подбор обеда для конкретного человека: собирает его дневник и норму, зовёт модель и учитывает вызов.
 */
@Service
public class LunchContext {

    private static final String USAGE_KIND = "LUNCH_ADVICE";

    private final LunchAdvisor advisor;
    private final DiaryService diary;
    private final ProfileService profiles;
    private final UsageRecorder usageRecorder;

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
        long startedAt = System.nanoTime();
        DiaryDay today = diary.today(accountId);
        Optional<DailyTarget> target = profiles.dailyTarget(accountId);
        Optional<LunchSuggestion> suggestion = advisor.advise(menu, today, target);
        suggestion.ifPresent(result -> usageRecorder.record(accountId, USAGE_KIND, provider(result), result.usage(),
                Duration.ofNanos(System.nanoTime() - startedAt)));
        return suggestion;
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
