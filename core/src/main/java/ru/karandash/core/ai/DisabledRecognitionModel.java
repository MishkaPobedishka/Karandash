package ru.karandash.core.ai;

import ru.karandash.contracts.ai.RecognitionResult;

final class DisabledRecognitionModel implements RecognitionModel {

    @Override
    public RecognitionResult recognizeText(String description) {
        throw new ModelUnavailableException("Слой модели не настроен");
    }

    @Override
    public RecognitionResult recognizePhoto(byte[] image, String contentType) {
        throw new ModelUnavailableException("Слой модели не настроен");
    }
}

