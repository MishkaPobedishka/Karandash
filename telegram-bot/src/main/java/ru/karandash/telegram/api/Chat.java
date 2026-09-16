package ru.karandash.telegram.api;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public record Chat(
        long id,
        String type,
        @JsonProperty("first_name") String firstName,
        @JsonProperty("last_name") String lastName,
        String username
) {

    public boolean isPrivate() {
        return "private".equals(type);
    }

    /** Имя, как его видно в Telegram. Пусто, если у чата его нет. */
    public String displayName() {
        String name = ((firstName == null ? "" : firstName) + " " + (lastName == null ? "" : lastName)).strip();
        return name.isEmpty() ? null : name;
    }
}
