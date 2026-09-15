package ru.karandash.contracts.telegram;

/**
 * Кнопка под ответом бота. {@code data} уходит в Telegram как callback_data и возвращается при нажатии,
 * поэтому в неё нельзя класть ничего, кроме короткого служебного кода.
 */
public record ReplyButton(String text, String data) {

    /** Предел Telegram на callback_data — 64 байта. */
    public static final int MAX_DATA_BYTES = 64;
}
