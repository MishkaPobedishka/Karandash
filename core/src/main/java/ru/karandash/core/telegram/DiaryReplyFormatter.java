package ru.karandash.core.telegram;

import org.springframework.stereotype.Component;
import ru.karandash.core.diary.DiaryDay;

import java.time.format.DateTimeFormatter;

import static ru.karandash.core.telegram.RecognitionReplyFormatter.TELEGRAM_MESSAGE_LIMIT;
import static ru.karandash.core.telegram.RecognitionReplyFormatter.limit;
import static ru.karandash.core.telegram.RecognitionReplyFormatter.range;

/**
 * Показывает дневник за день: что записано и сколько всего вышло.
 */
@Component
public class DiaryReplyFormatter {

    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm");

    /** Ответ сразу после нажатия «Записать в дневник». */
    public String saved(DiaryDay day) {
        return limit("Записал в дневник.\n\n" + body(day), TELEGRAM_MESSAGE_LIMIT);
    }

    /** Ответ на команду «покажи дневник». */
    public String day(DiaryDay day) {
        return day.isEmpty() ? TelegramTexts.DIARY_EMPTY : limit("Сегодня в дневнике:\n\n" + body(day),
                TELEGRAM_MESSAGE_LIMIT);
    }

    private static String body(DiaryDay day) {
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
        return text.toString();
    }
}
