package ru.karandash.contracts.telegram;

/**
 * Входящее событие из личного чата, которое приёмщик передаёт ядру.
 * Либо сообщение ({@code text} и, при наличии, фото отдельной частью multipart-запроса),
 * либо нажатие кнопки под прошлым ответом ({@code callbackData}).
 *
 * @param userName   имя из профиля Telegram — нужно, чтобы администратор видел, кого он пускает
 * @param userHandle имя пользователя без «собаки», если оно у человека есть
 */
public record TelegramInboundMessage(
        long updateId,
        long telegramUserId,
        String userName,
        String userHandle,
        String text,
        String callbackData
) {

    public static TelegramInboundMessage message(long updateId, long telegramUserId, String text) {
        return message(updateId, telegramUserId, null, null, text);
    }

    public static TelegramInboundMessage message(
            long updateId,
            long telegramUserId,
            String userName,
            String userHandle,
            String text
    ) {
        return new TelegramInboundMessage(updateId, telegramUserId, userName, userHandle, text, null);
    }

    public static TelegramInboundMessage button(long updateId, long telegramUserId, String callbackData) {
        return button(updateId, telegramUserId, null, null, callbackData);
    }

    public static TelegramInboundMessage button(
            long updateId,
            long telegramUserId,
            String userName,
            String userHandle,
            String callbackData
    ) {
        return new TelegramInboundMessage(updateId, telegramUserId, userName, userHandle, null, callbackData);
    }

    public boolean isButton() {
        return callbackData != null && !callbackData.isBlank();
    }
}
