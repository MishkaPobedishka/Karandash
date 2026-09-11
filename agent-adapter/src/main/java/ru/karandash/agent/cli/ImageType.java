package ru.karandash.agent.cli;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Optional;

/**
 * Поддерживаемые форматы фото. Формат определяется по сигнатуре файла, а не по заявленному типу.
 */
public enum ImageType {
    JPEG("image/jpeg", "jpg"),
    PNG("image/png", "png"),
    GIF("image/gif", "gif"),
    WEBP("image/webp", "webp");

    private static final byte[] PNG_SIGNATURE = {(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n'};

    private final String mediaType;
    private final String extension;

    ImageType(String mediaType, String extension) {
        this.mediaType = mediaType;
        this.extension = extension;
    }

    public String mediaType() {
        return mediaType;
    }

    public String extension() {
        return extension;
    }

    public static Optional<ImageType> detect(byte[] bytes) {
        if (bytes == null || bytes.length < 12) {
            return Optional.empty();
        }
        if ((bytes[0] & 0xFF) == 0xFF && (bytes[1] & 0xFF) == 0xD8 && (bytes[2] & 0xFF) == 0xFF) {
            return Optional.of(JPEG);
        }
        if (Arrays.equals(bytes, 0, PNG_SIGNATURE.length, PNG_SIGNATURE, 0, PNG_SIGNATURE.length)) {
            return Optional.of(PNG);
        }
        String head = new String(bytes, 0, 12, StandardCharsets.ISO_8859_1);
        if (head.startsWith("GIF87a") || head.startsWith("GIF89a")) {
            return Optional.of(GIF);
        }
        if (head.startsWith("RIFF") && head.startsWith("WEBP", 8)) {
            return Optional.of(WEBP);
        }
        return Optional.empty();
    }
}
