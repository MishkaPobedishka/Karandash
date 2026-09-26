package ru.karandash.core.diary;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import ru.karandash.contracts.ai.RecognitionItem;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Позиция внутри записи дневника — как её оценила модель.
 * Колонка user_corrections не отображается: у неё есть значение по умолчанию в схеме.
 */
@Entity
@Table(name = "food_item")
public class FoodItemEntity {

    /** Оценку дала модель, пользователь её только подтвердил. */
    private static final String SOURCE_MODEL = "MODEL";

    @Id
    private UUID id;

    /** Детали позиции пересчитываются вместе с записью, иначе в дневнике сойдутся не все числа. */
    void scale(BigDecimal factor) {
        this.amountMin = MealEntity.scaleDecimal(amountMin, factor);
        this.amountMax = MealEntity.scaleDecimal(amountMax, factor);
        this.kcalMin = MealEntity.scaleInt(kcalMin, factor);
        this.kcalMax = MealEntity.scaleInt(kcalMax, factor);
        this.proteinGMin = MealEntity.scaleDecimal(proteinGMin, factor);
        this.proteinGMax = MealEntity.scaleDecimal(proteinGMax, factor);
        this.fatGMin = MealEntity.scaleDecimal(fatGMin, factor);
        this.fatGMax = MealEntity.scaleDecimal(fatGMax, factor);
        this.carbsGMin = MealEntity.scaleDecimal(carbsGMin, factor);
        this.carbsGMax = MealEntity.scaleDecimal(carbsGMax, factor);
    }

    @Column(nullable = false)
    private UUID mealId;

    @Column(nullable = false)
    private String name;

    private BigDecimal amountMin;

    private BigDecimal amountMax;

    private String unit;

    private Integer kcalMin;

    private Integer kcalMax;

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
    private String estimateSource;

    private BigDecimal confidence;

    protected FoodItemEntity() {
    }

    FoodItemEntity(UUID mealId, RecognitionItem item) {
        this.id = UUID.randomUUID();
        this.mealId = mealId;
        this.name = item.name().strip();
        this.amountMin = item.portionGramsMin();
        this.amountMax = item.portionGramsMax();
        this.unit = item.portionGramsMin() == null && item.portionGramsMax() == null ? null : "g";
        this.kcalMin = item.kcalMin();
        this.kcalMax = item.kcalMax();
        this.proteinGMin = item.proteinGramsMin();
        this.proteinGMax = item.proteinGramsMax();
        this.fatGMin = item.fatGramsMin();
        this.fatGMax = item.fatGramsMax();
        this.carbsGMin = item.carbsGramsMin();
        this.carbsGMax = item.carbsGramsMax();
        this.estimateSource = SOURCE_MODEL;
        this.confidence = item.confidence();
    }

    public UUID getMealId() {
        return mealId;
    }

    public String getName() {
        return name;
    }
}
