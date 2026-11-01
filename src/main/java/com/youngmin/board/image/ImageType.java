package com.youngmin.board.image;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Optional;
import java.util.Set;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 허용하는 이미지 형식.
 * 확장자는 파일 이름만 바꾸면 얼마든지 속일 수 있으므로, 파일 앞부분의 "매직 넘버"(형식마다 정해진 시작 바이트)로
 * 실제 형식을 확인한다. 예: evil.exe를 cat.jpg로 이름만 바꿔도 시작 바이트가 FF D8 FF가 아니면 거절된다.
 */
@Getter
@RequiredArgsConstructor
public enum ImageType {
    JPEG("image/jpeg", "jpg", Set.of("jpg", "jpeg")),
    PNG("image/png", "png", Set.of("png")),
    GIF("image/gif", "gif", Set.of("gif")),
    WEBP("image/webp", "webp", Set.of("webp"));

    /** 형식 판별에 필요한 앞부분 바이트 수 (WEBP가 12바이트로 가장 길다) */
    public static final int HEADER_SIZE = 12;

    private final String mimeType;
    private final String storedExtension;
    private final Set<String> allowedExtensions;

    /** 파일의 앞부분 바이트로 실제 형식을 알아낸다. 이미지가 아니면 empty. */
    public static Optional<ImageType> detect(byte[] header) {
        if (startsWith(header, 0xFF, 0xD8, 0xFF)) {
            return Optional.of(JPEG);
        }
        if (startsWith(header, 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A)) {
            return Optional.of(PNG);
        }
        if (ascii(header, 0, "GIF87a") || ascii(header, 0, "GIF89a")) {
            return Optional.of(GIF);
        }
        if (ascii(header, 0, "RIFF") && ascii(header, 8, "WEBP")) {
            return Optional.of(WEBP);
        }
        return Optional.empty();
    }

    public static boolean isAllowedExtension(String extension) {
        return Arrays.stream(values()).anyMatch(type -> type.allowedExtensions.contains(extension));
    }

    private static boolean startsWith(byte[] header, int... expected) {
        if (header.length < expected.length) {
            return false;
        }
        for (int i = 0; i < expected.length; i++) {
            if ((header[i] & 0xFF) != expected[i]) {
                return false;
            }
        }
        return true;
    }

    private static boolean ascii(byte[] header, int offset, String text) {
        byte[] expected = text.getBytes(StandardCharsets.US_ASCII);
        if (header.length < offset + expected.length) {
            return false;
        }
        return Arrays.equals(header, offset, offset + expected.length, expected, 0, expected.length);
    }
}
