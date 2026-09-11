package ru.karandash.core.telegram;

public record IncomingPhoto(
        byte[] bytes,
        String contentType
) {
}
