package ru.karandash.core.profile;

import ru.karandash.core.calc.GoalDirection;
import ru.karandash.core.calc.Sex;

import java.math.BigDecimal;

/**
 * Ответы пользователя о себе. Пока разговор не закончен, часть полей пустая.
 */
public record ProfileAnswers(
        Sex sex,
        Integer age,
        Integer heightCm,
        BigDecimal weightKg,
        ActivityLevel activity,
        GoalDirection goal
) {

    public static ProfileAnswers empty() {
        return new ProfileAnswers(null, null, null, null, null, null);
    }

    public ProfileAnswers withSex(Sex value) {
        return new ProfileAnswers(value, age, heightCm, weightKg, activity, goal);
    }

    public ProfileAnswers withAge(int value) {
        return new ProfileAnswers(sex, value, heightCm, weightKg, activity, goal);
    }

    public ProfileAnswers withHeight(int value) {
        return new ProfileAnswers(sex, age, value, weightKg, activity, goal);
    }

    public ProfileAnswers withWeight(BigDecimal value) {
        return new ProfileAnswers(sex, age, heightCm, value, activity, goal);
    }

    public ProfileAnswers withActivity(ActivityLevel value) {
        return new ProfileAnswers(sex, age, heightCm, weightKg, value, goal);
    }

    public ProfileAnswers withGoal(GoalDirection value) {
        return new ProfileAnswers(sex, age, heightCm, weightKg, activity, value);
    }
}
