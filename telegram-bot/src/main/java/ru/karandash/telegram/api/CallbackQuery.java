package ru.karandash.telegram.api;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Нажатие кнопки под сообщением бота. {@code message} — то сообщение, под которым нажали;
 * у очень старых сообщений Telegram его не присылает.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record CallbackQuery(
        String id,
        User from,
        Message message,
        String data
) {
}
