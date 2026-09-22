package ru.karandash.core.reminder;

import java.time.LocalTime;
import java.util.Optional;

/**
 * Приёмы пищи, о которых бот напоминает. Время по умолчанию — обычный рабочий день;
 * каждый человек двигает его под себя.
 */
public enum ReminderType {

    BREAKFAST("b", "Завтрак", "🍳", LocalTime.of(8, 30)),
    LUNCH("l", "Обед", "🍲", LocalTime.of(12, 0)),
    DINNER("d", "Ужин", "🍽", LocalTime.of(20, 0));

    private final String code;
    private final String title;
    private final String emoji;
    private final LocalTime defaultTime;

    ReminderType(String code, String title, String emoji, LocalTime defaultTime) {
        this.code = code;
        this.title = title;
        this.emoji = emoji;
        this.defaultTime = defaultTime;
    }

    /** Короткий код для кнопки: в callback_data Telegram помещается мало. */
    public String code() {
        return code;
    }

    public String title() {
        return title;
    }

    public String emoji() {
        return emoji;
    }

    public LocalTime defaultTime() {
        return defaultTime;
    }

    public static Optional<ReminderType> byCode(String code) {
        for (ReminderType type : values()) {
            if (type.code.equals(code)) {
                return Optional.of(type);
            }
        }
        return Optional.empty();
    }
}
