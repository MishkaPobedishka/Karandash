package ru.karandash.contracts.telegram;

import java.util.List;

/**
 * Ядро просит приёмщика узнать, как зовут этих людей: у ядра нет доступа к Telegram,
 * а имя нужно, чтобы администратор видел не голые номера.
 */
public record TelegramNameRequest(List<Long> telegramIds) {

    public static final String EVENT_TYPE = "telegram.resolve-names";

    public TelegramNameRequest {
        telegramIds = telegramIds == null ? List.of() : List.copyOf(telegramIds);
    }
}
