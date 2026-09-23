package ru.karandash.core.canteen;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/** Открытый разговор о столовой: что человек уже попросил и когда последний раз уточнял. */
@Entity
@Table(name = "lunch_dialog")
public class LunchDialogEntity {

    @Id
    private UUID accountId;

    @Column(nullable = false)
    private String wish;

    @Column(nullable = false)
    private Instant updatedAt;

    protected LunchDialogEntity() {
    }

    LunchDialogEntity(UUID accountId, String wish) {
        this.accountId = accountId;
        this.wish = wish == null ? "" : wish;
        this.updatedAt = Instant.now();
    }

    public UUID getAccountId() {
        return accountId;
    }

    public String getWish() {
        return wish;
    }

    void setWish(String wish) {
        this.wish = wish == null ? "" : wish;
        this.updatedAt = Instant.now();
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
