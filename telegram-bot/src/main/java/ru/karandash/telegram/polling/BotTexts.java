package ru.karandash.telegram.polling;

/**
 * Ответы, которые приёмщик даёт сам, не обращаясь к ядру.
 */
final class BotTexts {

    /** Пока модель думает, человек должен видеть, что бот занят, а не молчит. */
    static final String WORKING = "Секунду, считаю… это занимает до полуминуты.";

    static final String UNSUPPORTED = "Пока понимаю только фото еды и текстовое описание. Пришлите одно из двух.";

    static final String PHOTO_TOO_LARGE = "Фото слишком большое. Пришлите его обычным фото, не файлом.";

    static final String PHOTO_UNAVAILABLE = "Не получилось получить фото из Telegram. Попробуйте отправить его ещё раз.";

    static final String CORE_UNAVAILABLE = "Сервис временно недоступен. Попробуйте ещё раз через пару минут.";

    private BotTexts() {
    }
}
