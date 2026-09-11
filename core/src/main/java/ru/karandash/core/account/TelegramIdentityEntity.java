package ru.karandash.core.account;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "telegram_identity")
public class TelegramIdentityEntity {

    @Id
    private Long telegramId;

    @Column(nullable = false)
    private UUID accountId;

    @Column(nullable = false, insertable = false, updatable = false)
    private Instant createdAt;

    protected TelegramIdentityEntity() {
    }

    public Long getTelegramId() {
        return telegramId;
    }

    public UUID getAccountId() {
        return accountId;
    }
}
