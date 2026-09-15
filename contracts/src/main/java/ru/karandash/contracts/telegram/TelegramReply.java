package ru.karandash.contracts.telegram;

import java.util.List;

/**
 * Ответ ядра приёмщику: готовые тексты для пользователя и кнопки к последнему из них.
 * {@code duplicate = true} — апдейт уже обрабатывался, отвечать пользователю не нужно.
 */
public record TelegramReply(
        boolean duplicate,
        List<String> messages,
        List<ReplyButton> buttons
) {

    public TelegramReply {
        messages = messages == null ? List.of() : List.copyOf(messages);
        buttons = buttons == null ? List.of() : List.copyOf(buttons);
    }

    public static TelegramReply of(String... messages) {
        return new TelegramReply(false, List.of(messages), List.of());
    }

    public static TelegramReply withButtons(String message, List<ReplyButton> buttons) {
        return new TelegramReply(false, List.of(message), buttons);
    }

    public static TelegramReply duplicateUpdate() {
        return new TelegramReply(true, List.of(), List.of());
    }
}
