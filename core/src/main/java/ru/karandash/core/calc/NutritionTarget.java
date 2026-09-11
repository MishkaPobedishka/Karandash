package ru.karandash.core.calc;

import java.math.BigDecimal;

public record NutritionTarget(
        int bmr,
        int tdee,
        int kcalMin,
        int kcalMax,
        BigDecimal proteinGramsMin,
        BigDecimal proteinGramsMax,
        BigDecimal fatGramsMin,
        BigDecimal fatGramsMax,
        BigDecimal carbsGramsMin,
        BigDecimal carbsGramsMax
) {
}

