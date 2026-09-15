package ru.karandash.core.diary;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

/**
 * Дневник за один день одного аккаунта.
 */
public record DiaryDay(LocalDate date, List<Entry> entries, int kcalMin, int kcalMax) {

    public record Entry(LocalTime time, String title, Integer kcalMin, Integer kcalMax) {
    }

    public boolean isEmpty() {
        return entries.isEmpty();
    }
}
