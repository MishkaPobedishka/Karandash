package ru.karandash.core.streak;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/** Серия дней одного человека: текущая, лучшая и день последней записи. */
@Entity
@Table(name = "streak")
public class StreakEntity {

    @Id
    private UUID accountId;

    @Column(nullable = false)
    private int currentDays;

    @Column(nullable = false)
    private int bestDays;

    private LocalDate lastActiveOn;

    @Column(nullable = false)
    private Instant updatedAt;

    protected StreakEntity() {
    }

    StreakEntity(UUID accountId) {
        this.accountId = accountId;
        this.currentDays = 0;
        this.bestDays = 0;
        this.updatedAt = Instant.now();
    }

    public int getCurrentDays() {
        return currentDays;
    }

    public int getBestDays() {
        return bestDays;
    }

    public LocalDate getLastActiveOn() {
        return lastActiveOn;
    }

    void markActive(LocalDate day, int days) {
        this.currentDays = days;
        this.bestDays = Math.max(bestDays, days);
        this.lastActiveOn = day;
        this.updatedAt = Instant.now();
    }

    void reset() {
        this.currentDays = 0;
        this.updatedAt = Instant.now();
    }
}
