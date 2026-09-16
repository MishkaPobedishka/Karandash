package ru.karandash.core.profile;

import java.math.BigDecimal;

/**
 * Коэффициенты активности для формулы Миффлина — Сан Жеора: на них умножается основной обмен.
 */
public enum ActivityLevel {

    SEDENTARY("1.2", "Почти не двигаюсь", "сидячая работа, спорта почти нет"),
    LIGHT("1.375", "1–3 раза в неделю", "лёгкие тренировки"),
    MEDIUM("1.55", "3–5 раз в неделю", "регулярные тренировки"),
    HIGH("1.725", "6–7 раз в неделю", "много тренировок"),
    EXTREME("1.9", "Очень много", "тяжёлый физический труд или две тренировки в день");

    private final BigDecimal coefficient;
    private final String button;
    private final String description;

    ActivityLevel(String coefficient, String button, String description) {
        this.coefficient = new BigDecimal(coefficient);
        this.button = button;
        this.description = description;
    }

    public BigDecimal coefficient() {
        return coefficient;
    }

    public String button() {
        return button;
    }

    public String description() {
        return description;
    }
}
