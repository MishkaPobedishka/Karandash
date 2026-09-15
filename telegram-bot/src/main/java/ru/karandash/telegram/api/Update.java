package ru.karandash.telegram.api;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public record Update(
        @JsonProperty("update_id") long updateId,
        Message message,
        @JsonProperty("callback_query") CallbackQuery callbackQuery
) {

    /** Чат, в котором произошёл апдейт, или {@code null}, если понять его нельзя. */
    public Long chatId() {
        if (message != null && message.chat() != null) {
            return message.chat().id();
        }
        if (callbackQuery == null) {
            return null;
        }
        if (callbackQuery.message() != null && callbackQuery.message().chat() != null) {
            return callbackQuery.message().chat().id();
        }
        // Личный чат: идентификатор чата совпадает с идентификатором пользователя.
        return callbackQuery.from() == null ? null : callbackQuery.from().id();
    }
}
