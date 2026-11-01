package com.youngmin.board.image;

import com.youngmin.board.common.exception.BusinessException;
import com.youngmin.board.common.exception.ErrorCode;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.stream.Stream;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

/**
 * 이미지 파일을 로컬 폴더(board.upload.dir)에 저장/삭제한다. DB는 다루지 않는다.
 *
 * 저장 파일 이름은 항상 "UUID.확장자"로 서버가 정한다. 사용자가 올린 파일 이름은 경로에 절대 쓰지 않는다.
 * - 이름이 겹쳐 다른 사람 파일을 덮어쓰는 문제 방지
 * - "../../application.yaml" 같은 이름으로 저장 폴더 밖에 쓰는 경로 조작(Path Traversal) 방지
 * - "a.jsp", "a.html" 같은 실행/스크립트 파일 이름 방지 (확장자도 실제 형식 기준으로 서버가 붙인다)
 */
@Slf4j
@Component
public class ImageStorage {

    private final Path root;
    private final long maxFileSize;

    public ImageStorage(UploadProperties properties) {
        this.root = Paths.get(properties.dir()).toAbsolutePath().normalize();
        this.maxFileSize = properties.maxFileSize().toBytes();
        try {
            Files.createDirectories(root);
        } catch (IOException e) {
            throw new UncheckedIOException("업로드 폴더를 만들 수 없습니다: " + root, e);
        }
        log.info("이미지 저장 폴더: {}", root);
    }

    /** 크기, 확장자, 실제 형식(매직 넘버)을 확인한다. 통과하면 실제 형식을 돌려준다. */
    public ImageType validate(MultipartFile file) {
        if (file.getSize() > maxFileSize) {
            throw new ImageValidationException(ErrorCode.IMAGE_TOO_LARGE);
        }
        String extension = extensionOf(file.getOriginalFilename());
        if (!ImageType.isAllowedExtension(extension)) {
            throw new ImageValidationException(ErrorCode.INVALID_IMAGE);
        }
        ImageType actualType = ImageType.detect(readHeader(file))
                .orElseThrow(() -> new ImageValidationException(ErrorCode.INVALID_IMAGE));
        // 확장자와 실제 형식이 다르면 거절 (예: PNG 내용인데 이름이 .gif)
        if (!actualType.getAllowedExtensions().contains(extension)) {
            throw new ImageValidationException(ErrorCode.INVALID_IMAGE);
        }
        return actualType;
    }

    /** 검증 후 UUID 이름으로 저장한다. */
    public StoredImage store(MultipartFile file) {
        ImageType type = validate(file);
        String storedName = UUID.randomUUID() + "." + type.getStoredExtension();
        Path target = resolve(storedName);
        try (InputStream in = file.getInputStream()) {
            Files.copy(in, target);
        } catch (IOException e) {
            throw new BusinessException(ErrorCode.IMAGE_STORE_FAILED);
        }
        return new StoredImage(cleanOriginalName(file.getOriginalFilename()), storedName, type.getMimeType(), file.getSize());
    }

    public Resource load(String storedName) {
        Path path = resolve(storedName);
        if (!Files.isReadable(path)) {
            throw new BusinessException(ErrorCode.IMAGE_NOT_FOUND);
        }
        return new FileSystemResource(path);
    }

    /**
     * 파일 삭제. 실패해도 예외를 던지지 않는다 (DB 작업은 이미 끝났으므로).
     * 지우지 못한 파일은 OrphanImageCleaner가 나중에 정리한다.
     *
     * @return 지웠거나 원래 없었으면 true, 지우지 못했으면 false
     */
    public boolean delete(String storedName) {
        try {
            Files.deleteIfExists(resolve(storedName));
            return true;
        } catch (IOException | RuntimeException e) {
            log.warn("이미지 파일 삭제 실패 (정리 작업에서 다시 시도): {}", storedName, e);
            return false;
        }
    }

    /** 저장 폴더의 파일 중 마지막 수정 시각이 cutoff보다 오래된 파일 이름 */
    public List<String> listFilesOlderThan(Instant cutoff) {
        try (Stream<Path> files = Files.list(root)) {
            return files.filter(Files::isRegularFile)
                    .filter(path -> lastModified(path).isBefore(cutoff))
                    .map(path -> path.getFileName().toString())
                    .toList();
        } catch (IOException e) {
            throw new UncheckedIOException("업로드 폴더를 읽을 수 없습니다: " + root, e);
        }
    }

    private static Instant lastModified(Path path) {
        try {
            return Files.getLastModifiedTime(path).toInstant();
        } catch (IOException e) {
            return Instant.MAX; // 읽을 수 없으면 지우지 않는다
        }
    }

    public Path getRoot() {
        return root;
    }

    /** 저장 폴더 안의 경로만 허용한다. */
    private Path resolve(String storedName) {
        Path path = root.resolve(storedName).normalize();
        if (!path.startsWith(root)) {
            throw new BusinessException(ErrorCode.IMAGE_NOT_FOUND);
        }
        return path;
    }

    private byte[] readHeader(MultipartFile file) {
        try (InputStream in = file.getInputStream()) {
            return in.readNBytes(ImageType.HEADER_SIZE);
        } catch (IOException e) {
            throw new ImageValidationException(ErrorCode.INVALID_IMAGE);
        }
    }

    private static String extensionOf(String filename) {
        String extension = StringUtils.getFilenameExtension(filename);
        return extension == null ? "" : extension.toLowerCase(Locale.ROOT);
    }

    /** 원래 이름은 화면 표시용으로만 DB에 저장한다. 경로 부분은 떼고 길이를 제한한다. */
    private static String cleanOriginalName(String filename) {
        String name = StringUtils.getFilename(StringUtils.cleanPath(filename == null ? "" : filename.replace('\\', '/')));
        if (!StringUtils.hasText(name)) {
            name = "image";
        }
        return name.length() > 200 ? name.substring(name.length() - 200) : name;
    }

    public record StoredImage(String originalName, String storedName, String contentType, long size) {
    }
}
