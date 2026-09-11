package ru.karandash.telegram.api;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public record ApiResponse<T>(
        boolean ok,
        T result,
        String description,
        @JsonProperty("error_code") Integer errorCode,
        Parameters parameters
) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Parameters(
            @JsonProperty("retry_after") Integer retryAfter
    ) {
    }
}
