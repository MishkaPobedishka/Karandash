package ru.karandash.core.notifications;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class BudgetWarningPolicyTest {

    private final BudgetWarningPolicy policy = new BudgetWarningPolicy(new BigDecimal("0.90"));

    @Test
    void warnsBeforeLimitAndDoesNotRepeat() {
        BudgetTransition first = policy.transition(BudgetState.AVAILABLE, 1890, 2100);
        BudgetTransition second = policy.transition(first.state(), 2000, 2100);

        assertThat(first).isEqualTo(new BudgetTransition(
                BudgetState.APPROACHING, BudgetEvent.APPROACHING_LIMIT));
        assertThat(second.event()).isEqualTo(BudgetEvent.NONE);
    }

    @Test
    void emitsExhaustedWhenLimitIsReached() {
        BudgetTransition result = policy.transition(BudgetState.APPROACHING, 2100, 2100);

        assertThat(result).isEqualTo(new BudgetTransition(
                BudgetState.EXHAUSTED, BudgetEvent.LIMIT_EXHAUSTED));
    }

    @Test
    void resetsAfterMealCorrection() {
        BudgetTransition reset = policy.transition(BudgetState.EXHAUSTED, 1500, 2100);
        BudgetTransition warnedAgain = policy.transition(reset.state(), 1900, 2100);

        assertThat(reset).isEqualTo(new BudgetTransition(BudgetState.AVAILABLE, BudgetEvent.NONE));
        assertThat(warnedAgain.event()).isEqualTo(BudgetEvent.APPROACHING_LIMIT);
    }
}

