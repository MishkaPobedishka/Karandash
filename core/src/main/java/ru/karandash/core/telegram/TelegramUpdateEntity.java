package ru.karandash.core.telegram;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "telegram_update")
public class TelegramUpdateEntity {

    @Id
    private Long updateId;

    @Column(nullable = false, insertable = false, updatable = false)
    private Instant receivedAt;

    protected TelegramUpdateEntity() {
    }

    public Long getUpdateId() {
        return updateId;
    }
}
