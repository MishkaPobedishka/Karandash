package ru.karandash.core.ai;

import ru.karandash.contracts.ai.ModelUsage;
import ru.karandash.contracts.ai.RecognitionResult;

import java.util.Optional;

public interface RecognitionModel {

    RecognitionResult recognizeText(String description);

    RecognitionResult recognizePhoto(byte[] image, String contentType);

    /**
     * Распознавание с данными вызова для {@code usage_record}. Провайдеры, которые их не сообщают, отдают пустые.
     */
    default ModelRecognition recognizeTextWithUsage(String description) {
        return new ModelRecognition(recognizeText(description), ModelUsage.unknown());
    }

    default ModelRecognition recognizePhotoWithUsage(byte[] image, String contentType) {
        return new ModelRecognition(recognizePhoto(image, contentType), ModelUsage.unknown());
    }

    /**
     * Подбор обеда по меню столовой. Провайдеры, которые этого не умеют, возвращают пустой результат —
     * тогда рассылка просто не уходит.
     */
    default Optional<ModelLunchAdvice> adviseLunch(String prompt) {
        return Optional.empty();
    }
}
