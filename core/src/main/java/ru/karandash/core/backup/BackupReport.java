package ru.karandash.core.backup;

/**
 * Что сообщает о себе ночная копия. Отчитываться может любой сервис кластера — имя различает их.
 *
 * @param service    чья копия: karandash, chimney, tracker…
 * @param ok         копия получилась
 * @param sizeBytes  размер файла копии
 * @param durationMs сколько заняла
 * @param message    короткое пояснение, если не получилось
 */
public record BackupReport(String service, boolean ok, long sizeBytes, long durationMs, String message) {

    /** Длинные сообщения об ошибке в базу не кладём: в них бывает лишнее. */
    public static final int MAX_MESSAGE_LENGTH = 500;
    /** Имя сервиса — короткий ярлык для метрики, не произвольный текст. */
    public static final int MAX_SERVICE_LENGTH = 32;
    public static final String DEFAULT_SERVICE = "karandash";

    public BackupReport {
        service = service == null || service.isBlank()
                ? DEFAULT_SERVICE
                : service.strip().toLowerCase(java.util.Locale.ROOT).replaceAll("[^a-z0-9_.-]", "-");
        if (service.length() > MAX_SERVICE_LENGTH) {
            service = service.substring(0, MAX_SERVICE_LENGTH);
        }
        message = message == null || message.length() <= MAX_MESSAGE_LENGTH
                ? message
                : message.substring(0, MAX_MESSAGE_LENGTH);
    }
}
