package ru.karandash.core.profile;

import ru.karandash.core.calc.GoalDirection;

/** Норма калорий на день по активной цели. */
public record DailyTarget(int kcalMin, int kcalMax, GoalDirection direction) {
}
