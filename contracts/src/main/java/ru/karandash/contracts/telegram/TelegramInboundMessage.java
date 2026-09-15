package ru.karandash.contracts.telegram;

/**
 * Входящее событие из личного чата, которое приёмщик передаёт ядру.
 * Либо сообщение ({@code text} и, при наличии, фото отдельной частью multipart-запроса),
 * либо нажатие кнопки под прошлым ответом ({@code callbackData}).
 */
public record TelegramInboundMessage(
        long updateId,
        long telegramUserId,
        String text,
        String callbackData
) {

    public static TelegramInboundMessage message(long updateId, long telegramUserId, String text) {
        return new TelegramInboundMessage(updateId, telegramUserId, text, null);
    }

    public static TelegramInboundMessage button(long updateId, long telegramUserId, String callbackData) {
        return new TelegramInboundMessage(updateId, telegramUserId, null, callbackData);
    }

    public boolean isButton() {
        return callbackData != null && !callbackData.isBlank();
    }
}
