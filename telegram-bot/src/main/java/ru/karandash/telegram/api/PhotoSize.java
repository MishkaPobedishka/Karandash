package ru.karandash.telegram.api;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public record PhotoSize(
        @JsonProperty("file_id") String fileId,
        int width,
        int height,
        @JsonProperty("file_size") Long fileSize
) {
}
