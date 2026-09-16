package ru.karandash.core.telegram;

import org.springframework.stereotype.Component;
import ru.karandash.contracts.telegram.ReplyButton;
import ru.karandash.contracts.telegram.TelegramReply;
import ru.karandash.core.calc.GoalDirection;
import ru.karandash.core.calc.NutritionTarget;
import ru.karandash.core.calc.Sex;
import ru.karandash.core.profile.ActivityLevel;
import ru.karandash.core.profile.ProfileAnswers;
import ru.karandash.core.profile.ProfileSetup;
import ru.karandash.core.profile.ProfileStep;
import ru.karandash.core.profile.ProfileSummary;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Разговор о телосложении: какой вопрос задать, что считать ответом и как показать итог.
 * Числа спрашиваем текстом, выбор из нескольких вариантов — кнопками.
 */
@Component
class ProfileDialog {

    private static final int MIN_AGE = 10;
    private static final int MAX_AGE = 100;
    private static final int MIN_HEIGHT = 100;
    private static final int MAX_HEIGHT = 250;
    private static final BigDecimal MIN_WEIGHT = new BigDecimal("30");
    private static final BigDecimal MAX_WEIGHT = new BigDecimal("400");

    /** Вопрос текущего шага вместе с кнопками. */
    TelegramReply question(ProfileSetup setup) {
        List<ReplyButton> buttons = new ArrayList<>(choices(setup.step()));
        buttons.add(new DialogCallback(DialogCallback.Action.PROFILE_CANCEL).button(TelegramTexts.BUTTON_CANCEL));
        return TelegramReply.withButtons(text(setup.step()), buttons);
    }

    /** Ответ числом. Пустой результат — не разобрали, вопрос нужно повторить. */
    Optional<ProfileSetup> applyText(ProfileSetup setup, String text) {
        String cleaned = text.strip().replace(',', '.').replace(" ", "");
        return switch (setup.step()) {
            case AGE -> wholeNumber(cleaned, MIN_AGE, MAX_AGE)
                    .map(age -> next(setup, setup.answers().withAge(age)));
            case HEIGHT -> wholeNumber(cleaned, MIN_HEIGHT, MAX_HEIGHT)
                    .map(height -> next(setup, setup.answers().withHeight(height)));
            case WEIGHT -> decimalNumber(cleaned)
                    .map(weight -> next(setup, setup.answers().withWeight(weight)));
            case SEX, ACTIVITY, GOAL -> Optional.empty();
        };
    }

    /** Ответ кнопкой. Пустой результат — кнопка не от этого шага. */
    Optional<ProfileSetup> applyChoice(ProfileSetup setup, DialogCallback callback) {
        return switch (callback.action()) {
            case PROFILE_SEX -> setup.step() != ProfileStep.SEX ? Optional.empty()
                    : value(Sex.class, callback.payload()).map(sex -> next(setup, setup.answers().withSex(sex)));
            case PROFILE_ACTIVITY -> setup.step() != ProfileStep.ACTIVITY ? Optional.empty()
                    : value(ActivityLevel.class, callback.payload())
                            .map(level -> next(setup, setup.answers().withActivity(level)));
            case PROFILE_GOAL -> setup.step() != ProfileStep.GOAL ? Optional.empty()
                    : value(GoalDirection.class, callback.payload())
                            .map(goal -> next(setup, setup.answers().withGoal(goal)));
            default -> Optional.empty();
        };
    }

    /** Разговор закончен, когда ответили на последний шаг. */
    boolean finished(ProfileSetup setup) {
        return setup.answers().goal() != null;
    }

    String summary(ProfileSummary summary) {
        ProfileAnswers answers = summary.answers();
        NutritionTarget target = summary.target();
        return """
                Записал: %s, %d %s, %d см, %s кг.
                Активность: %s.
                Цель: %s.

                Основной обмен — %d ккал, с активностью выходит %d ккал.
                Ваша норма: %d–%d ккал в день.
                Б %s–%s г · Ж %s–%s г · У %s–%s г

                Сколько осталось на сегодня, покажу в /diary.""".formatted(
                sexName(answers.sex()), answers.age(), years(answers.age()), answers.heightCm(),
                number(answers.weightKg()),
                answers.activity().button().toLowerCase(java.util.Locale.ROOT) + " — " + answers.activity().description(),
                goalName(answers.goal()).toLowerCase(java.util.Locale.ROOT),
                target.bmr(), target.tdee(), target.kcalMin(), target.kcalMax(),
                number(target.proteinGramsMin()), number(target.proteinGramsMax()),
                number(target.fatGramsMin()), number(target.fatGramsMax()),
                number(target.carbsGramsMin()), number(target.carbsGramsMax()));
    }

    /** Кнопка «заполнить» для тех, у кого нормы ещё нет. */
    static ReplyButton startButton(boolean hasProfile) {
        return new DialogCallback(DialogCallback.Action.PROFILE_START)
                .button(hasProfile ? TelegramTexts.BUTTON_PROFILE_CHANGE : TelegramTexts.BUTTON_PROFILE_FILL);
    }

    private static ProfileSetup next(ProfileSetup setup, ProfileAnswers answers) {
        ProfileStep[] steps = ProfileStep.values();
        int index = setup.step().ordinal();
        ProfileStep nextStep = index + 1 < steps.length ? steps[index + 1] : setup.step();
        return new ProfileSetup(nextStep, answers);
    }

    private static String text(ProfileStep step) {
        return switch (step) {
            case SEX -> "Посчитаю вашу норму калорий. Сначала: вы мужчина или женщина?";
            case AGE -> "Сколько вам полных лет? Ответьте числом.";
            case HEIGHT -> "Какой у вас рост в сантиметрах?";
            case WEIGHT -> "Какой сейчас вес в килограммах? Можно с запятой, например 72,5.";
            case ACTIVITY -> "Сколько двигаетесь за неделю?";
            case GOAL -> "К чему идём?";
        };
    }

    private static List<ReplyButton> choices(ProfileStep step) {
        return switch (step) {
            case SEX -> List.of(
                    sexButton(Sex.MALE), sexButton(Sex.FEMALE));
            case ACTIVITY -> List.of(ActivityLevel.values()).stream()
                    .map(level -> new DialogCallback(DialogCallback.Action.PROFILE_ACTIVITY, level.name())
                            .button(level.button()))
                    .toList();
            case GOAL -> List.of(
                    goalButton(GoalDirection.LOSS), goalButton(GoalDirection.MAINTENANCE), goalButton(GoalDirection.GAIN));
            case AGE, HEIGHT, WEIGHT -> List.of();
        };
    }

    private static ReplyButton sexButton(Sex sex) {
        return new DialogCallback(DialogCallback.Action.PROFILE_SEX, sex.name()).button(sexName(sex));
    }

    private static ReplyButton goalButton(GoalDirection goal) {
        return new DialogCallback(DialogCallback.Action.PROFILE_GOAL, goal.name()).button(goalName(goal));
    }

    private static String sexName(Sex sex) {
        return sex == Sex.MALE ? "Мужчина" : "Женщина";
    }

    private static String goalName(GoalDirection goal) {
        return switch (goal) {
            case LOSS -> "Сбросить вес";
            case MAINTENANCE -> "Держать вес";
            case GAIN -> "Набрать вес";
        };
    }

    private static String years(int age) {
        int lastTwo = age % 100;
        int last = age % 10;
        if (lastTwo >= 11 && lastTwo <= 14) {
            return "лет";
        }
        return switch (last) {
            case 1 -> "год";
            case 2, 3, 4 -> "года";
            default -> "лет";
        };
    }

    private static Optional<Integer> wholeNumber(String text, int min, int max) {
        try {
            int value = Integer.parseInt(text);
            return value >= min && value <= max ? Optional.of(value) : Optional.empty();
        } catch (NumberFormatException exception) {
            return Optional.empty();
        }
    }

    private static Optional<BigDecimal> decimalNumber(String text) {
        try {
            BigDecimal value = new BigDecimal(text).setScale(1, RoundingMode.HALF_UP);
            return value.compareTo(MIN_WEIGHT) >= 0 && value.compareTo(MAX_WEIGHT) <= 0
                    ? Optional.of(value)
                    : Optional.empty();
        } catch (NumberFormatException exception) {
            return Optional.empty();
        }
    }

    private static <T extends Enum<T>> Optional<T> value(Class<T> type, String name) {
        try {
            return Optional.of(Enum.valueOf(type, name));
        } catch (IllegalArgumentException exception) {
            return Optional.empty();
        }
    }

    /** Не больше одного знака после запятой, десятичный разделитель — запятая. */
    private static String number(BigDecimal value) {
        return value.setScale(Math.min(Math.max(value.scale(), 0), 1), RoundingMode.HALF_UP)
                .stripTrailingZeros().toPlainString().replace('.', ',');
    }
}
