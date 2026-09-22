package ru.karandash.core.reminder;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

/**
 * Настройка одного напоминания. Строка появляется, когда человек первый раз что-то меняет
 * или когда напоминание уходит: до этого работают значения по умолчанию.
 * Поля каркаса {@code weekdays}, {@code quiet_from} и {@code quiet_to} пока не используются.
 */
@Entity
@Table(name = "reminder_setting")
public class ReminderEntity {

    @Id
    private UUID id;

    @Column(name = "account_id", nullable = false)
    private UUID accountId;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false)
    private ReminderType type;

    @Column(name = "local_time", nullable = false)
    private LocalTime localTime;

    @Column(name = "enabled", nullable = false)
    private boolean enabled;

    /** Дата последней отправки в часовом поясе человека: защита от второго напоминания за день. */
    @Column(name = "last_sent_on")
    private LocalDate lastSentOn;

    protected ReminderEntity() {
    }

    ReminderEntity(UUID accountId, ReminderType type, LocalTime localTime, boolean enabled) {
        this.id = UUID.randomUUID();
        this.accountId = accountId;
        this.type = type;
        this.localTime = localTime;
        this.enabled = enabled;
    }

    public UUID getAccountId() {
        return accountId;
    }

    public ReminderType getType() {
        return type;
    }

    public LocalTime getLocalTime() {
        return localTime;
    }

    void setLocalTime(LocalTime localTime) {
        this.localTime = localTime;
    }

    public boolean isEnabled() {
        return enabled;
    }

    void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public LocalDate getLastSentOn() {
        return lastSentOn;
    }

    void setLastSentOn(LocalDate lastSentOn) {
        this.lastSentOn = lastSentOn;
    }
}
