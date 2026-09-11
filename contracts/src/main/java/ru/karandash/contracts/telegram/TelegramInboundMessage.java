package ru.karandash.contracts.telegram;

/**
 * Входящее сообщение из личного чата, которое приёмщик передаёт ядру.
 * Фото, если есть, идёт отдельной частью multipart-запроса; {@code text} — текст сообщения или подпись к фото.
 */
public record TelegramInboundMessage(
        long updateId,
        long telegramUserId,
        String text
) {
}
