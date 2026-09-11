package ru.karandash.telegram.api;

/**
 * Ошибка Bot API. Сообщение никогда не содержит адрес запроса: в пути адреса стоит токен бота.
 */
public class TelegramApiException extends RuntimeException {

    private final Integer errorCode;
    private final Integer retryAfterSeconds;

    public TelegramApiException(String message, Integer errorCode, Integer retryAfterSeconds) {
        super(message);
        this.errorCode = errorCode;
        this.retryAfterSeconds = retryAfterSeconds;
    }

    public Integer errorCode() {
        return errorCode;
    }

    public Integer retryAfterSeconds() {
        return retryAfterSeconds;
    }

    /** Токен бота отклонён — повторять бессмысленно, нужен новый токен. */
    public boolean isUnauthorized() {
        return errorCode != null && (errorCode == 401 || errorCode == 404);
    }

    /** Повтор не поможет: пользователь заблокировал бота, чат не найден и т. п. */
    public boolean isPermanent() {
        return errorCode != null && (errorCode == 400 || errorCode == 403);
    }
}
