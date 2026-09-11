package ru.karandash.core.notifications;

import java.math.BigDecimal;
import java.math.RoundingMode;

public class BudgetWarningPolicy {

    private final BigDecimal warningRatio;

    public BudgetWarningPolicy(BigDecimal warningRatio) {
        if (warningRatio == null || warningRatio.signum() <= 0 || warningRatio.compareTo(BigDecimal.ONE) >= 0) {
            throw new IllegalArgumentException("Порог предупреждения должен быть между 0 и 1");
        }
        this.warningRatio = warningRatio;
    }

    public BudgetTransition transition(BudgetState previous, int consumedKcal, int upperLimitKcal) {
        if (previous == null || consumedKcal < 0 || upperLimitKcal <= 0) {
            throw new IllegalArgumentException("Некорректное состояние дневного бюджета");
        }

        int warningAt = BigDecimal.valueOf(upperLimitKcal)
                .multiply(warningRatio)
                .setScale(0, RoundingMode.CEILING)
                .intValueExact();
        BudgetState current = consumedKcal >= upperLimitKcal
                ? BudgetState.EXHAUSTED
                : consumedKcal >= warningAt ? BudgetState.APPROACHING : BudgetState.AVAILABLE;

        if (current == previous) {
            return new BudgetTransition(current, BudgetEvent.NONE);
        }
        if (current == BudgetState.AVAILABLE) {
            return new BudgetTransition(current, BudgetEvent.NONE);
        }
        if (current == BudgetState.EXHAUSTED) {
            return new BudgetTransition(current, BudgetEvent.LIMIT_EXHAUSTED);
        }
        return new BudgetTransition(current, BudgetEvent.APPROACHING_LIMIT);
    }
}

