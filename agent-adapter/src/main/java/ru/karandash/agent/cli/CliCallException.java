package ru.karandash.agent.cli;

/**
 * Вызов CLI-агента не удался. Сообщение никогда не содержит пользовательский ввод или вывод модели.
 */
public class CliCallException extends RuntimeException {

    public enum Reason {
        START_FAILED,
        TIMEOUT,
        OUTPUT_LIMIT,
        UNSAFE_BEHAVIOR,
        AUTH_REJECTED,
        FAILED,
        BAD_OUTPUT
    }

    private final Reason reason;

    public CliCallException(Reason reason, String message) {
        super(message);
        this.reason = reason;
    }

    public CliCallException(Reason reason, String message, Throwable cause) {
        super(message, cause);
        this.reason = reason;
    }

    public Reason reason() {
        return reason;
    }
}
