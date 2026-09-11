package ru.karandash.contracts.ai;

/**
 * Ответ агент-адаптера: результат распознавания и данные вызова для учёта.
 */
public record AgentRecognitionResponse(
        RecognitionResult result,
        ModelUsage usage
) {
}
