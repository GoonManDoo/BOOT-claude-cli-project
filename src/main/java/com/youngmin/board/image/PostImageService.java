package com.youngmin.board.image;

import com.youngmin.board.common.exception.BusinessException;
import com.youngmin.board.common.exception.ErrorCode;
import com.youngmin.board.post.Post;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

/**
 * 게시글 이미지 (DB 행 + 디스크 파일)를 함께 관리한다.
 *
 * DB는 롤백되지만 디스크 파일은 롤백되지 않는다. 그래서 파일 작업 시점을 트랜잭션 결과에 맞춘다.
 * - 저장: 파일을 먼저 쓰고, 트랜잭션이 롤백되면 그 파일을 지운다 (afterCompletion).
 * - 삭제: DB에서 먼저 지우고, 트랜잭션이 커밋된 뒤에 파일을 지운다 (afterCommit).
 *   커밋 전에 파일을 지웠는데 롤백되면, DB에는 이미지가 있는데 파일은 없는 상태가 된다.
 *
 * add/remove 메서드는 PostService의 트랜잭션 안에서 호출된다 (MANDATORY: 트랜잭션 없이 부르면 에러).
 */
@Service
@RequiredArgsConstructor
public class PostImageService {

    private final PostImageRepository postImageRepository;
    private final ImageStorage imageStorage;
    private final UploadProperties uploadProperties;

    @Transactional(propagation = Propagation.MANDATORY)
    public void addImages(Post post, List<MultipartFile> files) {
        List<MultipartFile> uploads = nonEmpty(files);
        if (uploads.isEmpty()) {
            return;
        }
        long total = postImageRepository.countByPostId(post.getId()) + uploads.size();
        if (total > uploadProperties.maxCount()) {
            throw new ImageValidationException(ErrorCode.TOO_MANY_IMAGES);
        }
        // 한 장이라도 잘못되면 아무것도 저장하지 않도록, 전부 검사한 뒤에 저장을 시작한다
        uploads.forEach(imageStorage::validate);

        for (MultipartFile file : uploads) {
            ImageStorage.StoredImage stored = imageStorage.store(file);
            deleteFileIfRolledBack(stored.storedName());
            postImageRepository.save(PostImage.of(post, stored));
        }
    }

    /** 수정 화면에서 체크한 이미지 삭제. 다른 게시글의 이미지 id가 섞여 와도 무시한다. */
    @Transactional(propagation = Propagation.MANDATORY)
    public void removeImages(Post post, List<Long> imageIds) {
        if (imageIds == null || imageIds.isEmpty()) {
            return;
        }
        List<PostImage> targets = postImageRepository.findAllById(imageIds).stream()
                .filter(image -> image.getPost().getId().equals(post.getId()))
                .toList();
        delete(targets);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void removeAll(Post post) {
        delete(postImageRepository.findAllByPostIdOrderByIdAsc(post.getId()));
    }

    @Transactional(readOnly = true)
    public List<PostImageResponse> getImages(Long postId) {
        return postImageRepository.findAllByPostIdOrderByIdAsc(postId).stream()
                .map(PostImageResponse::from)
                .toList();
    }

    /** 이미지 보기. 삭제된 글의 이미지는 없고, 숨김 글의 이미지는 관리자만 볼 수 있다. */
    @Transactional(readOnly = true)
    public ImageFile load(Long imageId, boolean viewerIsAdmin) {
        PostImage image = postImageRepository.findWithPostById(imageId)
                .orElseThrow(() -> new BusinessException(ErrorCode.IMAGE_NOT_FOUND));
        Post post = image.getPost();
        if (post.isDeleted() || (post.isHidden() && !viewerIsAdmin)) {
            throw new BusinessException(ErrorCode.IMAGE_NOT_FOUND);
        }
        return new ImageFile(imageStorage.load(image.getStoredName()), image.getContentType());
    }

    private void delete(List<PostImage> images) {
        if (images.isEmpty()) {
            return;
        }
        List<String> storedNames = images.stream().map(PostImage::getStoredName).toList();
        postImageRepository.deleteAll(images);
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                storedNames.forEach(imageStorage::delete);
            }
        });
    }

    private void deleteFileIfRolledBack(String storedName) {
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                if (status == STATUS_ROLLED_BACK) {
                    imageStorage.delete(storedName);
                }
            }
        });
    }

    private static List<MultipartFile> nonEmpty(List<MultipartFile> files) {
        // 파일을 고르지 않고 제출해도 브라우저는 빈 파일 하나를 보낸다 → 걸러 낸다
        return files == null ? List.of() : files.stream().filter(file -> file != null && !file.isEmpty()).toList();
    }

    public record ImageFile(Resource resource, String contentType) {
    }
}
