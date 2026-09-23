package ru.karandash.core.canteen;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
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

    /**
     * Отпечаток меню: пока он не меняется, столовая отдаёт тот же список и подбор можно не пересчитывать.
     * Днём меню дополняют — тогда отпечаток другой и подбор считается заново.
     */
    public String fingerprint() {
        StringBuilder text = new StringBuilder();
        for (CanteenDish dish : dishes) {
            text.append(dish.category()).append('|').append(dish.name())
                    .append('|').append(dish.price()).append('|').append(dish.weightGrams()).append(';');
        }
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256")
                    .digest(text.toString().getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash, 0, 8);
        } catch (NoSuchAlgorithmException exception) {
            // SHA-256 есть в любой JVM; если вдруг нет — считаем меню всегда новым.
            return String.valueOf(System.nanoTime());
        }
    }

    private static String normalize(String value) {
        return value.strip().toLowerCase(Locale.ROOT).replace('ё', 'е').replaceAll("\s+", " ");
    }
}
