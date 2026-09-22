package ru.karandash.core.reminder;

import java.time.LocalTime;

/**
 * Настройка напоминания так, как её видит человек: когда напомнить и включено ли оно.
 */
public record Reminder(ReminderType type, LocalTime at, boolean enabled) {
}
