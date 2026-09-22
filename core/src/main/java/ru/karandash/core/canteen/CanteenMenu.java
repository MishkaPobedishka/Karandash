package ru.karandash.core.canteen;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * Меню столовой на сегодня: API отдаёт то, что есть прямо сейчас, без дат.
 */
public record CanteenMenu(List<CanteenDish> dishes) {

    public CanteenMenu {
        dishes = dishes == null ? List.of() : List.copyOf(dishes);
    }

    public boolean isEmpty() {
        return dishes.isEmpty();
    }

    /** Блюдо по названию — так проверяем, что модель не выдумала того, чего в меню нет. */
    public Optional<CanteenDish> find(String name) {
        if (name == null) {
            return Optional.empty();
        }
        String wanted = normalize(name);
        return dishes.stream().filter(dish -> normalize(dish.name()).equals(wanted)).findFirst();
    }

    private static String normalize(String value) {
        return value.strip().toLowerCase(Locale.ROOT).replace('ё', 'е').replaceAll("\s+", " ");
    }
}
