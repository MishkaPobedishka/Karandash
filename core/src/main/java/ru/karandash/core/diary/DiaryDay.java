package ru.karandash.core.diary;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

/**
 * Дневник за один день одного аккаунта.
 */
public record DiaryDay(LocalDate date, List<Entry> entries, int kcalMin, int kcalMax) {

    public record Entry(UUID id, LocalTime time, String title, Integer kcalMin, Integer kcalMax) {
    }

    /** Одна запись по её номеру: с ней работают экраны правки. */
    public java.util.Optional<Entry> find(UUID id) {
        return entries.stream().filter(entry -> entry.id().equals(id)).findFirst();
    }

    public boolean isEmpty() {
        return entries.isEmpty();
    }
}
