package com.youngmin.board.image;

import static org.assertj.core.api.Assertions.assertThat;

import com.youngmin.board.category.CategoryRepository;
import com.youngmin.board.member.MemberRepository;
import com.youngmin.board.post.Post;
import com.youngmin.board.post.PostRepository;
import com.youngmin.board.support.TestImages;
import com.youngmin.board.support.TestMembers;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

/** 고아 이미지 파일 정리: DB에 없는 오래된 파일만 지우고, 등록된 파일과 방금 만든 파일은 남긴다. */
@SpringBootTest
@Transactional
class OrphanImageCleanerTest {

    @Autowired OrphanImageCleaner cleaner;
    @Autowired ImageStorage imageStorage;
    @Autowired PostImageRepository postImageRepository;
    @Autowired PostRepository postRepository;
    @Autowired MemberRepository memberRepository;
    @Autowired CategoryRepository categoryRepository;

    @Test
    void DB에_없는_오래된_파일만_지운다() throws Exception {
        Instant now = Instant.now();
        Path orphan = writeFile(now.minus(Duration.ofHours(2)));          // 지워져야 함
        Path recentOrphan = writeFile(now.minus(Duration.ofMinutes(10)));  // 방금 만든 파일: 남김
        Path registered = registerImage(now.minus(Duration.ofHours(2)));  // DB에 있음: 남김

        int deleted = cleaner.cleanUp(now);

        assertThat(deleted).isGreaterThanOrEqualTo(1);
        assertThat(Files.exists(orphan)).isFalse();
        assertThat(Files.exists(recentOrphan)).isTrue();
        assertThat(Files.exists(registered)).isTrue();

        Files.deleteIfExists(recentOrphan);
    }

    private Path writeFile(Instant modifiedAt) throws Exception {
        Path file = imageStorage.getRoot().resolve(UUID.randomUUID() + ".png");
        Files.write(file, TestImages.pngBytes());
        Files.setLastModifiedTime(file, FileTime.from(modifiedAt));
        return file;
    }

    /** 실제 업로드처럼 파일을 저장하고 DB에도 등록한다 */
    private Path registerImage(Instant modifiedAt) throws Exception {
        Post post = postRepository.save(Post.create("이미지 글", "내용", TestMembers.user(memberRepository, "이미지"),
                categoryRepository.findAllByOrderBySortOrderAscIdAsc().get(0)));
        ImageStorage.StoredImage stored = imageStorage.store(TestImages.png("images", "kept.png"));
        postImageRepository.saveAndFlush(PostImage.of(post, stored));
        Path file = imageStorage.getRoot().resolve(stored.storedName());
        Files.setLastModifiedTime(file, FileTime.from(modifiedAt));
        return file;
    }
}
