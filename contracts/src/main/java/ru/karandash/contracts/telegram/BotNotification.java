package ru.karandash.contracts.telegram;

import java.util.List;

/**
 * Асинхронное уведомление пользователю: ядро кладёт его в outbox, приёмщик получает из {@code q.bot.events}.
 * Кнопки необязательны — они нужны, например, чтобы администратор решал судьбу заявки прямо из уведомления.
 */
public record BotNotification(
        long chatId,
        String text,
        List<ReplyButton> buttons
) {

    public static final String EVENT_TYPE = "telegram.notification";

    public BotNotification {
        buttons = buttons == null ? List.of() : List.copyOf(buttons);
    }

    public BotNotification(long chatId, String text) {
        this(chatId, text, List.of());
    }
}
