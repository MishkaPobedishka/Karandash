package ru.karandash.contracts.ai;

/**
 * Ответ агент-адаптера на подбор обеда и данные вызова для учёта.
 */
public record AgentLunchResponse(LunchAdvice advice, ModelUsage usage) {
}
