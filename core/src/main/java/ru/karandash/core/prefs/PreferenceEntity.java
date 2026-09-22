package ru.karandash.core.prefs;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/** Настройка пользователя из общей таблицы предпочтений. */
@Entity
@Table(name = "preference")
public class PreferenceEntity {

    @Id
    private UUID id;

    @Column(nullable = false)
    private UUID accountId;

    @Column(nullable = false)
    private String type;

    @Column(nullable = false)
    private String value;

    @Column(nullable = false)
    private String source;

    @Column(nullable = false)
    private boolean confirmed;

    @Column(nullable = false)
    private Instant updatedAt;

    protected PreferenceEntity() {
    }

    PreferenceEntity(UUID accountId, String type, String value) {
        this.id = UUID.randomUUID();
        this.accountId = accountId;
        this.type = type;
        this.value = value;
        this.source = "USER";
        this.confirmed = true;
        this.updatedAt = Instant.now();
    }

    String getValue() {
        return value;
    }

    void setValue(String value) {
        this.value = value;
        this.updatedAt = Instant.now();
    }
}
