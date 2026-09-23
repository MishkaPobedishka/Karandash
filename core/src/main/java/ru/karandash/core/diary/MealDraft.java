package ru.karandash.core.diary;

import ru.karandash.contracts.ai.RecognitionResult;

import java.util.UUID;

/**
 * Показанная пользователю оценка, которая ждёт решения: записать в дневник, переоценить или забыть.
 *
 * @param inputText исходное описание еды; для фотографии — {@code null}
 * @param revision  сколько раз оценку уже пересчитывали по замечаниям пользователя
 * @param dialog    вопросы модели и ответы человека по этой еде — всё, что уже выяснили
 */
public record MealDraft(
        UUID id,
        UUID accountId,
        DraftSource source,
        String inputText,
        RecognitionResult result,
        int revision,
        String dialog
) {
}
