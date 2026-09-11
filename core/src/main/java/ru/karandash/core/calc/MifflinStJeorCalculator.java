package ru.karandash.core.calc;

import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;

@Component
public class MifflinStJeorCalculator {

    private static final BigDecimal MIN_ACTIVITY = new BigDecimal("1.2");
    private static final BigDecimal MAX_ACTIVITY = new BigDecimal("1.9");
    private static final BigDecimal MAX_ADJUSTMENT = new BigDecimal("0.20");
    private static final BigDecimal RANGE_HALF_WIDTH = new BigDecimal("0.05");

    public NutritionTarget calculate(NutritionCalculationRequest request) {
        validate(request);

        BigDecimal sexOffset = request.sex() == Sex.MALE
                ? BigDecimal.valueOf(5)
                : BigDecimal.valueOf(-161);
        BigDecimal bmrValue = request.weightKg().multiply(BigDecimal.TEN)
                .add(request.heightCm().multiply(new BigDecimal("6.25")))
                .subtract(BigDecimal.valueOf(request.age()).multiply(BigDecimal.valueOf(5)))
                .add(sexOffset);
        int bmr = round(bmrValue);
        int tdee = round(bmrValue.multiply(request.activityCoefficient()));

        BigDecimal directionMultiplier = switch (request.direction()) {
            case LOSS -> BigDecimal.ONE.subtract(request.adjustmentFraction());
            case MAINTENANCE -> BigDecimal.ONE;
            case GAIN -> BigDecimal.ONE.add(request.adjustmentFraction());
        };
        BigDecimal target = BigDecimal.valueOf(tdee).multiply(directionMultiplier);
        int floor = request.sex() == Sex.FEMALE ? 1200 : 1500;
        int kcalMin = Math.max(floor, round(target.multiply(BigDecimal.ONE.subtract(RANGE_HALF_WIDTH))));
        int kcalMax = Math.max(kcalMin, round(target.multiply(BigDecimal.ONE.add(RANGE_HALF_WIDTH))));

        return new NutritionTarget(
                bmr,
                tdee,
                kcalMin,
                kcalMax,
                grams(kcalMin, "0.25", 4),
                grams(kcalMax, "0.30", 4),
                grams(kcalMin, "0.25", 9),
                grams(kcalMax, "0.30", 9),
                grams(kcalMin, "0.40", 4),
                grams(kcalMax, "0.50", 4)
        );
    }

    private void validate(NutritionCalculationRequest request) {
        Objects.requireNonNull(request, "Параметры расчёта обязательны");
        Objects.requireNonNull(request.weightKg(), "Вес обязателен");
        Objects.requireNonNull(request.heightCm(), "Рост обязателен");
        Objects.requireNonNull(request.sex(), "Пол обязателен");
        Objects.requireNonNull(request.activityCoefficient(), "Коэффициент активности обязателен");
        Objects.requireNonNull(request.direction(), "Цель обязательна");
        Objects.requireNonNull(request.adjustmentFraction(), "Величина изменения обязательна");

        if (request.weightKg().signum() <= 0 || request.heightCm().signum() <= 0 || request.age() <= 0) {
            throw new IllegalArgumentException("Вес, рост и возраст должны быть положительными");
        }
        if (request.activityCoefficient().compareTo(MIN_ACTIVITY) < 0
                || request.activityCoefficient().compareTo(MAX_ACTIVITY) > 0) {
            throw new IllegalArgumentException("Коэффициент активности должен быть от 1.2 до 1.9");
        }
        if (request.adjustmentFraction().signum() < 0
                || request.adjustmentFraction().compareTo(MAX_ADJUSTMENT) > 0) {
            throw new IllegalArgumentException("Изменение цели должно быть от 0 до 20 процентов");
        }
    }

    private int round(BigDecimal value) {
        return value.setScale(0, RoundingMode.HALF_UP).intValueExact();
    }

    private BigDecimal grams(int kcal, String share, int kcalPerGram) {
        return BigDecimal.valueOf(kcal)
                .multiply(new BigDecimal(share))
                .divide(BigDecimal.valueOf(kcalPerGram), 1, RoundingMode.HALF_UP);
    }
}

