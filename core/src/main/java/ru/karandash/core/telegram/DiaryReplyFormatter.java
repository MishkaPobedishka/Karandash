package ru.karandash.core.telegram;

import org.springframework.stereotype.Component;
import ru.karandash.core.diary.DiaryDay;
import ru.karandash.core.profile.DailyTarget;

import java.time.format.DateTimeFormatter;
import java.util.Optional;

import static ru.karandash.core.telegram.RecognitionReplyFormatter.TELEGRAM_MESSAGE_LIMIT;
import static ru.karandash.core.telegram.RecognitionReplyFormatter.limit;
import static ru.karandash.core.telegram.RecognitionReplyFormatter.range;

/**
 * Показывает дневник за день: что записано, сколько всего вышло и сколько осталось до нормы.
 */
@Component
public class DiaryReplyFormatter {

    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm");

    /** Ответ сразу после нажатия «Записать в дневник». */
    public String saved(DiaryDay day, Optional<DailyTarget> target) {
        return limit("Записал в дневник.\n\n" + body(day, target), TELEGRAM_MESSAGE_LIMIT);
    }

    /** Ответ на команду «покажи дневник». */
    public String day(DiaryDay day, Optional<DailyTarget> target) {
        return day.isEmpty()
                ? TelegramTexts.DIARY_EMPTY + target.map(DiaryReplyFormatter::targetLine).orElse("")
                : limit("Сегодня в дневнике:\n\n" + body(day, target), TELEGRAM_MESSAGE_LIMIT);
    }

    private static String body(DiaryDay day, Optional<DailyTarget> target) {
        StringBuilder text = new StringBuilder();
        for (DiaryDay.Entry entry : day.entries()) {
            text.append(TIME.format(entry.time())).append(" · ").append(entry.title());
            if (entry.kcalMin() != null && entry.kcalMax() != null) {
                text.append(" — ").append(range(entry.kcalMin(), entry.kcalMax())).append(" ккал");
            }
            text.append('\n');
        }
        text.append("\nВсего за день: ").append(range(day.kcalMin(), day.kcalMax())).append(" ккал");
        text.append(", записей: ").append(day.entries().size());
        target.ifPresent(daily -> text.append(remaining(day, daily)));
        return text.toString();
    }

    /**
     * Остаток считаем честно по краям: от нижней границы нормы отнимаем верхнюю границу съеденного,
     * иначе остаток выглядел бы больше, чем он есть.
     */
    private static String remaining(DiaryDay day, DailyTarget target) {
        long left = Math.max(0, target.kcalMin() - day.kcalMax());
        long right = Math.max(0, target.kcalMax() - day.kcalMin());
        String norm = "\nВаша норма: " + range(target.kcalMin(), target.kcalMax()) + " ккал";
        if (right == 0) {
            return norm + ".\nНорма на сегодня уже выбрана.";
        }
        return norm + ", осталось " + range(left, right) + " ккал.";
    }

    private static String targetLine(DailyTarget target) {
        return "\n\nВаша норма: " + range(target.kcalMin(), target.kcalMax()) + " ккал в день.";
    }
}
