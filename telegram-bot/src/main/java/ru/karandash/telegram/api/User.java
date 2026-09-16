package ru.karandash.telegram.api;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public record User(
        long id,
        @JsonProperty("is_bot") boolean bot,
        @JsonProperty("first_name") String firstName,
        @JsonProperty("last_name") String lastName,
        String username
) {

    /** Имя, как его видно в Telegram. Пусто, если человек скрыл имя. */
    public String displayName() {
        String name = ((firstName == null ? "" : firstName) + " " + (lastName == null ? "" : lastName)).strip();
        return name.isEmpty() ? null : name;
    }
}
