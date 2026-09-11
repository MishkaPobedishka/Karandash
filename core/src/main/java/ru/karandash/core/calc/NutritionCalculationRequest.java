package ru.karandash.core.calc;

import java.math.BigDecimal;

public record NutritionCalculationRequest(
        BigDecimal weightKg,
        BigDecimal heightCm,
        int age,
        Sex sex,
        BigDecimal activityCoefficient,
        GoalDirection direction,
        BigDecimal adjustmentFraction
) {
}

