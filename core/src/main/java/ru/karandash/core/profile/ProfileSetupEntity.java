package ru.karandash.core.profile;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/** Незаконченный разговор о телосложении. */
@Entity
@Table(name = "profile_setup")
public class ProfileSetupEntity {

    @Id
    private UUID accountId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ProfileStep step;

    @Column(nullable = false)
    private String data;

    @Column(nullable = false)
    private Instant updatedAt;

    protected ProfileSetupEntity() {
    }

    ProfileSetupEntity(UUID accountId, ProfileStep step, String data) {
        this.accountId = accountId;
        this.step = step;
        this.data = data;
        this.updatedAt = Instant.now();
    }

    UUID getAccountId() {
        return accountId;
    }

    ProfileStep getStep() {
        return step;
    }

    String getData() {
        return data;
    }

    void update(ProfileStep step, String data) {
        this.step = step;
        this.data = data;
        this.updatedAt = Instant.now();
    }
}
