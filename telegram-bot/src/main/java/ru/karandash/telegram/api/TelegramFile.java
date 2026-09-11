package ru.karandash.telegram.api;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public record TelegramFile(
        @JsonProperty("file_id") String fileId,
        @JsonProperty("file_size") Long fileSize,
        @JsonProperty("file_path") String filePath
) {
}
