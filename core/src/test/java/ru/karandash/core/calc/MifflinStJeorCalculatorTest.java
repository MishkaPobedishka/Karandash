package ru.karandash.core.calc;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MifflinStJeorCalculatorTest {

    private final MifflinStJeorCalculator calculator = new MifflinStJeorCalculator();

    @Test
    void calculatesLossTargetForMale() {
        NutritionTarget target = calculator.calculate(new NutritionCalculationRequest(
                new BigDecimal("80"),
                new BigDecimal("180"),
                35,
                Sex.MALE,
                new BigDecimal("1.55"),
                GoalDirection.LOSS,
                new BigDecimal("0.15")
        ));

        assertThat(target.bmr()).isEqualTo(1755);
        assertThat(target.tdee()).isEqualTo(2720);
        assertThat(target.kcalMin()).isEqualTo(2196);
        assertThat(target.kcalMax()).isEqualTo(2428);
        assertThat(target.proteinGramsMin()).isPositive();
    }

    @Test
    void appliesFemaleSafetyFloor() {
        NutritionTarget target = calculator.calculate(new NutritionCalculationRequest(
                new BigDecimal("45"),
                new BigDecimal("155"),
                60,
                Sex.FEMALE,
                new BigDecimal("1.2"),
                GoalDirection.LOSS,
                new BigDecimal("0.20")
        ));

        assertThat(target.kcalMin()).isEqualTo(1200);
        assertThat(target.kcalMax()).isEqualTo(1200);
    }

    @Test
    void rejectsUnknownActivityCoefficient() {
        NutritionCalculationRequest request = new NutritionCalculationRequest(
                new BigDecimal("80"), new BigDecimal("180"), 35, Sex.MALE,
                new BigDecimal("2.0"), GoalDirection.MAINTENANCE, BigDecimal.ZERO
        );

        assertThatThrownBy(() -> calculator.calculate(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("1.2");
    }
}

