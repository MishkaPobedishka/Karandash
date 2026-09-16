package ru.karandash.core.profile;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import ru.karandash.core.calc.GoalDirection;
import ru.karandash.core.calc.NutritionTarget;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Цель по калориям. Активная цель у аккаунта одна — прошлые переводятся в REPLACED.
 */
@Entity
@Table(name = "goal")
public class GoalEntity {

    static final String STATUS_ACTIVE = "ACTIVE";
    static final String STATUS_REPLACED = "REPLACED";

    @Id
    private UUID id;

    @Column(nullable = false)
    private UUID accountId;

    @Column(nullable = false)
    private String type;

    @Column(nullable = false)
    private int kcalMin;

    @Column(nullable = false)
    private int kcalMax;

    @Column(name = "protein_g_min", nullable = false)
    private BigDecimal proteinGMin;

    @Column(name = "protein_g_max", nullable = false)
    private BigDecimal proteinGMax;

    @Column(name = "fat_g_min", nullable = false)
    private BigDecimal fatGMin;

    @Column(name = "fat_g_max", nullable = false)
    private BigDecimal fatGMax;

    @Column(name = "carbs_g_min", nullable = false)
    private BigDecimal carbsGMin;

    @Column(name = "carbs_g_max", nullable = false)
    private BigDecimal carbsGMax;

    @Column(nullable = false)
    private String status;

    @Column(nullable = false)
    private Instant createdAt;

    protected GoalEntity() {
    }

    GoalEntity(UUID accountId, GoalDirection direction, NutritionTarget target) {
        this.id = UUID.randomUUID();
        this.accountId = accountId;
        this.type = direction.name();
        this.kcalMin = target.kcalMin();
        this.kcalMax = target.kcalMax();
        this.proteinGMin = target.proteinGramsMin();
        this.proteinGMax = target.proteinGramsMax();
        this.fatGMin = target.fatGramsMin();
        this.fatGMax = target.fatGramsMax();
        this.carbsGMin = target.carbsGramsMin();
        this.carbsGMax = target.carbsGramsMax();
        this.status = STATUS_ACTIVE;
        this.createdAt = Instant.now();
    }

    GoalDirection direction() {
        return GoalDirection.valueOf(type);
    }

    public int getKcalMin() {
        return kcalMin;
    }

    public int getKcalMax() {
        return kcalMax;
    }

    public BigDecimal getProteinGMin() {
        return proteinGMin;
    }

    public BigDecimal getProteinGMax() {
        return proteinGMax;
    }

    void replace() {
        this.status = STATUS_REPLACED;
    }
}
