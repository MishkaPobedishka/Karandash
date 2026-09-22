package ru.karandash.contracts.telegram;

/**
 * Кнопка под ответом бота. {@code data} уходит в Telegram как callback_data и возвращается при нажатии,
 * поэтому в неё нельзя класть ничего, кроме короткого служебного кода.
 *
 * @param row номер ряда: {@code 0} — кнопка занимает строку целиком, одинаковый номер ставит кнопки рядом
 */
public record ReplyButton(String text, String data, int row) {

    /** Предел Telegram на callback_data — 64 байта. */
    public static final int MAX_DATA_BYTES = 64;

    /** Кнопка на всю строку: так выглядят почти все подписи бота, они длинные. */
    public static final int OWN_ROW = 0;

    public ReplyButton(String text, String data) {
        this(text, data, OWN_ROW);
    }

    public ReplyButton inRow(int row) {
        return new ReplyButton(text, data, row);
    }
}
