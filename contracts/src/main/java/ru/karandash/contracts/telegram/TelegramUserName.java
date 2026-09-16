package ru.karandash.contracts.telegram;

/**
 * Имя человека из Telegram, которое приёмщик возвращает ядру.
 */
public record TelegramUserName(long telegramId, String displayName, String username) {
}
