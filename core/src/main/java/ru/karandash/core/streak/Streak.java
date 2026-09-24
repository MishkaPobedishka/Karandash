package ru.karandash.core.streak;

import java.time.LocalDate;

/**
 * Серия дней так, как её видит человек.
 *
 * @param days        сколько дней в текущей серии
 * @param best        лучшая серия за всё время
 * @param activeToday сегодня уже записана хотя бы одна еда
 * @param daysLeft    сколько дней подряд ещё можно пропустить, прежде чем серия обнулится
 */
public record Streak(int days, int best, boolean activeToday, LocalDate lastActiveOn, int daysLeft) {

    public boolean atRisk() {
        return days > 0 && !activeToday;
    }
}
