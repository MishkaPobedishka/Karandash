package ru.karandash.telegram.core;

public class CoreUnavailableException extends RuntimeException {

    public CoreUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
