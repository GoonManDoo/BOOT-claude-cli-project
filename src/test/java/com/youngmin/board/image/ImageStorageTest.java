package com.youngmin.board.image;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.youngmin.board.common.exception.ErrorCode;
import com.youngmin.board.support.TestImages;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.util.unit.DataSize;

/** 이미지 검증/저장 단위 테스트 (Spring 없이, 임시 폴더 사용) */
class ImageStorageTest {

    @TempDir
    Path tempDir;

    ImageStorage storage;

    @BeforeEach
    void setUp() {
        storage = new ImageStorage(new UploadProperties(tempDir.toString(), DataSize.ofMegabytes(5), 5));
    }

    @Test
    void 진짜_PNG는_UUID_이름으로_저장된다() {
        ImageStorage.StoredImage stored = storage.store(TestImages.png("images", "cat.png"));

        assertThat(stored.storedName()).matches("[0-9a-f-]{36}\\.png");
        assertThat(stored.originalName()).isEqualTo("cat.png");
        assertThat(Files.exists(tempDir.resolve(stored.storedName()))).isTrue();
    }

    @Test
    void 확장자만_png인_실행_파일은_거절된다() {
        MockMultipartFile exe = new MockMultipartFile("images", "cat.png", "image/png", new byte[]{'M', 'Z', 0, 0, 1, 2, 3});

        assertThatThrownBy(() -> storage.validate(exe))
                .isInstanceOf(ImageValidationException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.INVALID_IMAGE);
    }

    @Test
    void 내용은_PNG인데_확장자가_다르면_거절된다() {
        MockMultipartFile wrongExt = new MockMultipartFile("images", "cat.gif", "image/gif", TestImages.pngBytes());

        assertThatThrownBy(() -> storage.validate(wrongExt)).extracting("errorCode").isEqualTo(ErrorCode.INVALID_IMAGE);
    }

    @Test
    void 허용하지_않는_확장자는_거절된다() {
        MockMultipartFile html = new MockMultipartFile("images", "page.html", "text/html", "<script>".getBytes());

        assertThatThrownBy(() -> storage.validate(html)).extracting("errorCode").isEqualTo(ErrorCode.INVALID_IMAGE);
    }

    @Test
    void 크기가_5MB를_넘으면_거절된다() {
        byte[] big = new byte[5 * 1024 * 1024 + 1];
        System.arraycopy(TestImages.pngBytes(), 0, big, 0, TestImages.pngBytes().length);
        MockMultipartFile file = new MockMultipartFile("images", "big.png", "image/png", big);

        assertThatThrownBy(() -> storage.validate(file)).extracting("errorCode").isEqualTo(ErrorCode.IMAGE_TOO_LARGE);
    }

    @Test
    void 경로가_섞인_파일_이름을_올려도_저장_폴더_밖에_쓰지_않는다() {
        ImageStorage.StoredImage stored = storage.store(TestImages.png("images", "../../../etc/evil.png"));

        assertThat(stored.originalName()).isEqualTo("evil.png"); // 표시용 이름에서도 경로를 뗀다
        assertThat(tempDir.resolve(stored.storedName()).normalize().getParent()).isEqualTo(tempDir);
    }
}
