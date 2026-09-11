package ru.karandash.agent.cli;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class ImageTypeTest {

    @Test
    void detectsBySignature() {
        assertThat(ImageType.detect(bytes(0xFF, 0xD8, 0xFF, 0xE0, 0, 0, 0, 0, 0, 0, 0, 0))).contains(ImageType.JPEG);
        assertThat(ImageType.detect(bytes(0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n', 0, 0, 0, 0))).contains(ImageType.PNG);
        assertThat(ImageType.detect("GIF89a000000".getBytes(StandardCharsets.ISO_8859_1))).contains(ImageType.GIF);
        assertThat(ImageType.detect("RIFF0000WEBPVP8 ".getBytes(StandardCharsets.ISO_8859_1))).contains(ImageType.WEBP);
    }

    @Test
    void rejectsEverythingElse() {
        assertThat(ImageType.detect(null)).isEmpty();
        assertThat(ImageType.detect(new byte[3])).isEmpty();
        assertThat(ImageType.detect("#!/bin/sh\nrm -rf /\n".getBytes(StandardCharsets.UTF_8))).isEmpty();
        assertThat(ImageType.detect("%PDF-1.7 00000".getBytes(StandardCharsets.ISO_8859_1))).isEmpty();
    }

    private static byte[] bytes(int... values) {
        byte[] result = new byte[values.length];
        for (int index = 0; index < values.length; index++) {
            result[index] = (byte) values[index];
        }
        return result;
    }
}
