package com.youngmin.board.support;

import java.util.Base64;
import org.springframework.mock.web.MockMultipartFile;

/** 테스트용 진짜 이미지 파일 (1x1 픽셀 PNG) */
public final class TestImages {

    private static final byte[] PNG_1X1 = Base64.getDecoder().decode(
            "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8z8DwHwAFBQIAX8jx0gAAAABJRU5ErkJggg==");

    private TestImages() {
    }

    public static MockMultipartFile png(String paramName, String filename) {
        return new MockMultipartFile(paramName, filename, "image/png", PNG_1X1);
    }

    public static byte[] pngBytes() {
        return PNG_1X1.clone();
    }
}
