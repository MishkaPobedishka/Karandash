package ru.karandash.core.diary;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

/**
 * Запись дневника: подтверждённая пользователем порция еды. Всегда принадлежит одному аккаунту.
 */
@Entity
@Table(name = "meal")
public class MealEntity {

    /** Пользователь подтвердил оценку кнопкой. */
    static final String STATUS_CONFIRMED = "CONFIRMED";

    @Id
    private UUID id;

    @Column(nullable = false)
    private UUID accountId;

    @Column(nullable = false)
    private LocalDate localDate;

    @Column(nullable = false)
    private LocalTime localTime;

    @Column(nullable = false)
    private String inputSource;

    @Column(nullable = false)
    private String status;

    private String inputText;

    private Integer kcalMin;

    private Integer kcalMax;

    private Integer kcalLikely;

    // Стратегия имён Hibernate не делит "gMin" на «г» и «мин» — колонку называем явно.
    @Column(name = "protein_g_min")
    private BigDecimal proteinGMin;

    @Column(name = "protein_g_max")
    private BigDecimal proteinGMax;

    @Column(name = "fat_g_min")
    private BigDecimal fatGMin;

    @Column(name = "fat_g_max")
    private BigDecimal fatGMax;

    @Column(name = "carbs_g_min")
    private BigDecimal carbsGMin;

    @Column(name = "carbs_g_max")
    private BigDecimal carbsGMax;

    @Column(nullable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    protected MealEntity() {
    }

    MealEntity(UUID accountId, LocalDate localDate, LocalTime localTime, DraftSource source, String inputText) {
        this.id = UUID.randomUUID();
        this.accountId = accountId;
        this.localDate = localDate;
        this.localTime = localTime;
        this.inputSource = source.name();
        this.status = STATUS_CONFIRMED;
        this.inputText = inputText;
        this.createdAt = Instant.now();
        this.updatedAt = this.createdAt;
    }

    void setNutrition(
            Integer kcalMin,
            Integer kcalMax,
            BigDecimal proteinGMin,
            BigDecimal proteinGMax,
            BigDecimal fatGMin,
            BigDecimal fatGMax,
            BigDecimal carbsGMin,
            BigDecimal carbsGMax
    ) {
        this.kcalMin = kcalMin;
        this.kcalMax = kcalMax;
        this.kcalLikely = kcalMin == null || kcalMax == null ? null : (kcalMin + kcalMax) / 2;
        this.proteinGMin = proteinGMin;
        this.proteinGMax = proteinGMax;
        this.fatGMin = fatGMin;
        this.fatGMax = fatGMax;
        this.carbsGMin = carbsGMin;
        this.carbsGMax = carbsGMax;
    }

    /** Пересчёт порции: «съел половину» и «была двойная» — самые частые поправки. */
    void scale(BigDecimal factor) {
        this.kcalMin = scaleInt(kcalMin, factor);
        this.kcalMax = scaleInt(kcalMax, factor);
        this.kcalLikely = kcalMin == null || kcalMax == null ? null : (kcalMin + kcalMax) / 2;
        this.proteinGMin = scaleDecimal(proteinGMin, factor);
        this.proteinGMax = scaleDecimal(proteinGMax, factor);
        this.fatGMin = scaleDecimal(fatGMin, factor);
        this.fatGMax = scaleDecimal(fatGMax, factor);
        this.carbsGMin = scaleDecimal(carbsGMin, factor);
        this.carbsGMax = scaleDecimal(carbsGMax, factor);
        this.updatedAt = Instant.now();
    }

    static Integer scaleInt(Integer value, BigDecimal factor) {
        return value == null
                ? null
                : BigDecimal.valueOf(value).multiply(factor).setScale(0, java.math.RoundingMode.HALF_UP).intValue();
    }

    static BigDecimal scaleDecimal(BigDecimal value, BigDecimal factor) {
        return value == null ? null : value.multiply(factor).setScale(2, java.math.RoundingMode.HALF_UP);
    }

    public UUID getId() {
        return id;
    }

    public UUID getAccountId() {
        return accountId;
    }

    public LocalDate getLocalDate() {
        return localDate;
    }

    public LocalTime getLocalTime() {
        return localTime;
    }

    public String getInputText() {
        return inputText;
    }

    public Integer getKcalMin() {
        return kcalMin;
    }

    public Integer getKcalMax() {
        return kcalMax;
    }
}
