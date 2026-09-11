package ru.karandash.core.ai;

import ru.karandash.contracts.ai.RecognitionResult;

public interface RecognitionModel {

    RecognitionResult recognizeText(String description);

    RecognitionResult recognizePhoto(byte[] image, String contentType);
}

