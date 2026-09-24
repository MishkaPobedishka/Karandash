package ru.karandash.core.telegram;

import org.springframework.stereotype.Component;
import ru.karandash.core.diary.DiaryDay;
import ru.karandash.core.diary.DiaryService;
import ru.karandash.core.profile.ProfileService;
import ru.karandash.core.streak.Streak;
import ru.karandash.core.streak.StreakService;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Вечерняя сводка: что за день записано, сколько осталось до нормы и что с серией дней.
 * Главное в ней — честно сказать, что серия под угрозой и сколько дней ещё можно пропустить.
 */
@Component
public class EveningSummary {

    private final DiaryService diary;
    private final ProfileService profiles;
    private final StreakService streaks;
    private final DiaryReplyFormatter formatter;

    public EveningSummary(
            DiaryService diary,
            ProfileService profiles,
            StreakService streaks,
            DiaryReplyFormatter formatter
    ) {
        this.diary = diary;
        this.profiles = profiles;
        this.streaks = streaks;
        this.formatter = formatter;
    }

    public String text(UUID accountId, LocalDate today) {
        DiaryDay day = diary.today(accountId);
        Streak streak = streaks.today(accountId, today);
        String body = day.isEmpty()
                ? TelegramTexts.SUMMARY_EMPTY
                : formatter.day(day, profiles.dailyTarget(accountId));
        return TelegramTexts.SUMMARY_TITLE + "\n\n" + body + "\n\n" + streakLine(streak);
    }

    /** Строка про серию: она же предупреждение, если сегодня ещё ничего не записано. */
    public static String streakLine(Streak streak) {
        if (streak.days() == 0) {
            return TelegramTexts.STREAK_NONE;
        }
        if (!streak.atRisk()) {
            return TelegramTexts.STREAK_KEPT.formatted(streak.days(), days(streak.days()));
        }
        return streak.daysLeft() == 0
                ? TelegramTexts.STREAK_LAST_CHANCE.formatted(streak.days(), days(streak.days()))
                : TelegramTexts.STREAK_AT_RISK.formatted(streak.days(), days(streak.days()),
                        streak.daysLeft(), days(streak.daysLeft()));
    }

    /** «1 день», «2 дня», «5 дней» — иначе текст выглядит машинным. */
    static String days(int value) {
        int tail = value % 100;
        if (tail >= 11 && tail <= 14) {
            return "дней";
        }
        return switch (value % 10) {
            case 1 -> "день";
            case 2, 3, 4 -> "дня";
            default -> "дней";
        };
    }
}
