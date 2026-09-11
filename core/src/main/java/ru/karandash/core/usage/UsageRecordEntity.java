package ru.karandash.core.usage;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "usage_record")
public class UsageRecordEntity {

    @Id
    private UUID id;

    private UUID accountId;

    @Column(nullable = false)
    private String kind;

    @Column(nullable = false)
    private String provider;

    @Column(nullable = false)
    private String model;

    private Integer tokensIn;

    private Integer tokensOut;

    private BigDecimal cost;

    @Column(nullable = false)
    private long latencyMs;

    @Column(nullable = false)
    private Instant createdAt;

    protected UsageRecordEntity() {
    }

    UsageRecordEntity(
            UUID accountId,
            String kind,
            String provider,
            String model,
            Integer tokensIn,
            Integer tokensOut,
            BigDecimal cost,
            long latencyMs
    ) {
        this.id = UUID.randomUUID();
        this.accountId = accountId;
        this.kind = kind;
        this.provider = provider;
        this.model = model;
        this.tokensIn = tokensIn;
        this.tokensOut = tokensOut;
        this.cost = cost;
        this.latencyMs = latencyMs;
        this.createdAt = Instant.now();
    }

    public UUID getAccountId() {
        return accountId;
    }

    public String getKind() {
        return kind;
    }

    public String getProvider() {
        return provider;
    }

    public String getModel() {
        return model;
    }

    public Integer getTokensIn() {
        return tokensIn;
    }

    public Integer getTokensOut() {
        return tokensOut;
    }

    public BigDecimal getCost() {
        return cost;
    }

    public long getLatencyMs() {
        return latencyMs;
    }
}
