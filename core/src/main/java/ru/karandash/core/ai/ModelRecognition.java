package ru.karandash.core.ai;

import ru.karandash.contracts.ai.ModelUsage;
import ru.karandash.contracts.ai.RecognitionResult;

/**
 * Результат распознавания вместе с данными вызова модели для учёта.
 */
public record ModelRecognition(
        RecognitionResult result,
        ModelUsage usage
) {
}
