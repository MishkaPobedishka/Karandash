package ru.karandash.core.canteen;

import java.math.BigDecimal;

/**
 * Блюдо из меню столовой. Пищевая ценность известна не у всех — там, где её нет, оценивает модель.
 *
 * @param weightGrams вес порции, если столовая его указала
 * @param kcal        калорийность порции, если известна
 */
public record CanteenDish(
        String category,
        String name,
        BigDecimal price,
        BigDecimal weightGrams,
        BigDecimal kcal,
        BigDecimal proteins,
        BigDecimal fats,
        BigDecimal carbohydrates,
        String photoUrl
) {

    public boolean hasNutrition() {
        return kcal != null;
    }

    /** Ссылка живёт около часа, поэтому годится только для немедленной отправки. */
    public boolean hasPhoto() {
        return photoUrl != null && !photoUrl.isBlank();
    }
}
