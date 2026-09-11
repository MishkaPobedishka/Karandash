package ru.karandash.contracts.ai;

/**
 * Ответ модели не соответствует контракту распознавания.
 */
public class RecognitionContractException extends RuntimeException {

    public RecognitionContractException(String message) {
        super(message);
    }
}
