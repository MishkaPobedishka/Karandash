package ru.karandash.contracts.telegram;

import java.util.List;

/**
 * Ответ ядра приёмщику: готовые тексты для пользователя.
 * {@code duplicate = true} — апдейт уже обрабатывался, отвечать пользователю не нужно.
 */
public record TelegramReply(
        boolean duplicate,
        List<String> messages
) {

    public static TelegramReply of(String... messages) {
        return new TelegramReply(false, List.of(messages));
    }

    public static TelegramReply duplicateUpdate() {
        return new TelegramReply(true, List.of());
    }
}
