package ru.karandash.telegram.api;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record Message(
        @JsonProperty("message_id") long messageId,
        User from,
        Chat chat,
        String text,
        String caption,
        List<PhotoSize> photo
) {

    public boolean hasPhoto() {
        return photo != null && !photo.isEmpty();
    }

    public boolean hasText() {
        return text != null && !text.isBlank();
    }
}
