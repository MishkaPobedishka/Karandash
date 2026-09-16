package ru.karandash.core.profile;

import ru.karandash.core.calc.NutritionTarget;

/** Телосложение с целью и посчитанной по ним нормой. */
public record ProfileSummary(ProfileAnswers answers, NutritionTarget target) {
}
