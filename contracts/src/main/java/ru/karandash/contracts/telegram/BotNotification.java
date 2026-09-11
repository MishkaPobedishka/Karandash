package ru.karandash.contracts.telegram;

/**
 * Асинхронное уведомление пользователю: ядро кладёт его в outbox, приёмщик получает из {@code q.bot.events}.
 */
public record BotNotification(
        long chatId,
        String text
) {

    public static final String EVENT_TYPE = "telegram.notification";
}
