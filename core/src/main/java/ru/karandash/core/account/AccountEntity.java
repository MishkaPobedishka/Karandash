package ru.karandash.core.account;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "account")
public class AccountEntity {

    @Id
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AccountStatus status;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AccountRole role;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AccessState access;

    private Instant accessChangedAt;

    /** Администратор, чьё решение по заявке оказалось первым. */
    private UUID decidedBy;

    @Column(nullable = false)
    private Instant createdAt;

    protected AccountEntity() {
    }

    AccountEntity(UUID id, AccountStatus status, AccountRole role, AccessState access) {
        this.id = id;
        this.status = status;
        this.role = role;
        this.access = access;
        this.createdAt = Instant.now();
        this.accessChangedAt = this.createdAt;
    }

    public UUID getId() {
        return id;
    }

    public AccountStatus getStatus() {
        return status;
    }

    public AccountRole getRole() {
        return role;
    }

    public AccessState getAccess() {
        return access;
    }

    void setRole(AccountRole role) {
        this.role = role;
    }

    void setAccess(AccessState access) {
        this.access = access;
        this.accessChangedAt = Instant.now();
    }

    public UUID getDecidedBy() {
        return decidedBy;
    }

    void setDecidedBy(UUID decidedBy) {
        this.decidedBy = decidedBy;
    }
}
