package ru.karandash.core.diary;

import ru.karandash.contracts.ai.RecognitionResult;

import java.util.UUID;

/**
 * Показанная пользователю оценка, которая ждёт решения: записать в дневник, переоценить или забыть.
 *
 * @param inputText исходное описание еды; для фотографии — {@code null}
 * @param revision  сколько раз оценку уже пересчитывали по замечаниям пользователя
 */
public record MealDraft(
        UUID id,
        UUID accountId,
        DraftSource source,
        String inputText,
        RecognitionResult result,
        int revision
) {
}
