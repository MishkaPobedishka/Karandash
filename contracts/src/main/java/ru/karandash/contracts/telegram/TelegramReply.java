package ru.karandash.contracts.telegram;

import java.util.List;

/**
 * Ответ ядра приёмщику: готовые тексты для пользователя и кнопки к последнему из них.
 * {@code duplicate = true} — апдейт уже обрабатывался, отвечать пользователю не нужно.
 * {@code replaceMessage = true} — ответ на нажатие кнопки, который должен заменить собой то же сообщение,
 * а не уходить новым: так меню переключается на месте.
 */
public record TelegramReply(
        boolean duplicate,
        List<String> messages,
        List<ReplyButton> buttons,
        boolean replaceMessage
) {

    public TelegramReply {
        messages = messages == null ? List.of() : List.copyOf(messages);
        buttons = buttons == null ? List.of() : List.copyOf(buttons);
    }

    public TelegramReply(boolean duplicate, List<String> messages, List<ReplyButton> buttons) {
        this(duplicate, messages, buttons, false);
    }

    public static TelegramReply of(String... messages) {
        return new TelegramReply(false, List.of(messages), List.of(), false);
    }

    public static TelegramReply withButtons(String message, List<ReplyButton> buttons) {
        return new TelegramReply(false, List.of(message), buttons, false);
    }

    /** Экран меню: заменяет собой сообщение, под которым нажали кнопку. */
    public static TelegramReply screen(String message, List<ReplyButton> buttons) {
        return new TelegramReply(false, List.of(message), buttons, true);
    }

    public static TelegramReply duplicateUpdate() {
        return new TelegramReply(true, List.of(), List.of(), false);
    }
}
