package ru.karandash.core.backup;

/**
 * Что сообщает о себе ночная копия базы.
 *
 * @param ok         копия получилась
 * @param sizeBytes  размер файла копии
 * @param durationMs сколько заняла
 * @param message    короткое пояснение, если не получилось
 */
public record BackupReport(boolean ok, long sizeBytes, long durationMs, String message) {

    /** Длинные сообщения об ошибке в базу не кладём: в них бывает лишнее. */
    public static final int MAX_MESSAGE_LENGTH = 500;

    public BackupReport {
        message = message == null || message.length() <= MAX_MESSAGE_LENGTH
                ? message
                : message.substring(0, MAX_MESSAGE_LENGTH);
    }
}
