package ru.karandash.core.notifications;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.math.BigDecimal;

@Configuration
public class BudgetConfiguration {

    @Bean
    BudgetWarningPolicy budgetWarningPolicy(
            @Value("${karandash.notifications.budget-warning-ratio:0.90}") BigDecimal warningRatio
    ) {
        return new BudgetWarningPolicy(warningRatio);
    }
}

