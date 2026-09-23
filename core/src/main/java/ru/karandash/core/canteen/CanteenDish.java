package ru.karandash.core.canteen;

import java.math.BigDecimal;

/**
 * Блюдо из меню столовой. Пищевая ценность известна не у всех — там, где её нет, оценивает модель.
 *
 * <p><b>Столовая отдаёт пищевую ценность на 100 г</b>, а вес — за порцию: картофельное пюре 200 г
 * с «90,7 ккал» — это 90,7 ккал в ста граммах и 181 ккал в тарелке. Поэтому наружу — и в тексты
 * пользователю, и в запросы модели — уходит уже посчитанная порция.
 *
 * @param weightGrams вес порции, если столовая его указала
 * @param kcal        калорийность ста граммов, если известна
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

    /** Пищевая ценность порции считается только тогда, когда известен и вес. */
    public boolean hasPortionNutrition() {
        return kcal != null && weightGrams != null && weightGrams.signum() > 0;
    }

    public BigDecimal kcalPerPortion() {
        return perPortion(kcal);
    }

    public BigDecimal proteinsPerPortion() {
        return perPortion(proteins);
    }

    public BigDecimal fatsPerPortion() {
        return perPortion(fats);
    }

    public BigDecimal carbohydratesPerPortion() {
        return perPortion(carbohydrates);
    }

    private BigDecimal perPortion(BigDecimal per100g) {
        if (per100g == null || !hasPortionNutrition()) {
            return null;
        }
        return per100g.multiply(weightGrams)
                .divide(new BigDecimal("100"), 1, java.math.RoundingMode.HALF_UP);
    }

    /** Ссылка живёт около часа, поэтому годится только для немедленной отправки. */
    public boolean hasPhoto() {
        return photoUrl != null && !photoUrl.isBlank();
    }
}
