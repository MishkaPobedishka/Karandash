package ru.karandash.contracts.telegram;

/**
 * Фотография в ответе бота: ссылка, по которой её заберёт Telegram, и подпись.
 * Ссылки столовой подписаны и живут около часа, поэтому отправлять их нужно сразу.
 */
public record ReplyPhoto(String url, String caption) {

    /** Предел Telegram на подпись к фотографии. */
    public static final int MAX_CAPTION = 1024;

    /** Сколько фотографий помещается в один альбом. */
    public static final int MAX_IN_ALBUM = 10;
}
