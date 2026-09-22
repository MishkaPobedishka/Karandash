package ru.karandash.core.ai;

import ru.karandash.contracts.ai.LunchAdvice;
import ru.karandash.contracts.ai.ModelUsage;

/**
 * Подобранный обед вместе с данными вызова для учёта.
 */
public record ModelLunchAdvice(LunchAdvice advice, ModelUsage usage) {
}
