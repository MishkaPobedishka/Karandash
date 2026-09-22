package ru.karandash.core.canteen;

import ru.karandash.contracts.ai.ModelUsage;

import java.math.BigDecimal;
import java.util.List;

/**
 * Готовые варианты обеда: уже сверены с меню, цена посчитана по нему же.
 */
public record LunchSuggestion(List<Option> options, ModelUsage usage) {

    /**
     * @param price сумма по меню, а не то, что назвала модель
     * @param why   одна фраза от модели, почему вариант подходит
     */
    public record Option(
            String title,
            List<CanteenDish> dishes,
            int kcalMin,
            int kcalMax,
            BigDecimal price,
            String why
    ) {
    }
}
