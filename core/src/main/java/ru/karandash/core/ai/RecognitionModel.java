package ru.karandash.core.ai;

import ru.karandash.contracts.ai.ModelUsage;
import ru.karandash.contracts.ai.RecognitionResult;

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
}
