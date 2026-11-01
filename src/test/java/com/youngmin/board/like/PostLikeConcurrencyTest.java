package com.youngmin.board.like;

import static org.assertj.core.api.Assertions.assertThat;

import com.youngmin.board.like.dto.LikeResponse;
import com.youngmin.board.member.Member;
import com.youngmin.board.member.MemberRepository;
import com.youngmin.board.post.Post;
import com.youngmin.board.post.PostRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 좋아요 동시성 테스트.
 * 여러 스레드가 진짜로 동시에 커밋해야 하므로 @Transactional을 붙이지 않는다.
 * (붙이면 모든 작업이 테스트 트랜잭션 하나에 묶여서 동시성 상황이 재현되지 않는다)
 * 그래서 만든 데이터는 @AfterEach에서 직접 지운다.
 */
@SpringBootTest
class PostLikeConcurrencyTest {

    @Autowired PostLikeService postLikeService;
    @Autowired PostLikeRepository postLikeRepository;
    @Autowired PostRepository postRepository;
    @Autowired MemberRepository memberRepository;
    @Autowired TransactionTemplate transactionTemplate;
    @Autowired com.youngmin.board.category.CategoryRepository categoryRepository;

    Member member;
    Post post;

    @BeforeEach
    void setUp() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        member = memberRepository.save(Member.createUser("lk" + suffix, "hash", "동시성", "lk" + suffix + "@example.com"));
        post = postRepository.save(Post.create("동시성 테스트", "내용", member,
                categoryRepository.findAllByOrderBySortOrderAscIdAsc().get(0)));
    }

    @AfterEach
    void tearDown() {
        transactionTemplate.executeWithoutResult(status -> {
            postLikeRepository.deleteAllByPostId(post.getId());
            postRepository.deleteById(post.getId());
            memberRepository.deleteById(member.getId());
        });
    }

    @Test
    void 같은_회원이_동시에_좋아요를_10번_보내도_1개만_저장되고_좋아요_수는_1이다() throws Exception {
        int threadCount = 10;
        ExecutorService pool = Executors.newFixedThreadPool(threadCount);
        CountDownLatch ready = new CountDownLatch(threadCount);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<LikeResponse>> results = new ArrayList<>();

        for (int i = 0; i < threadCount; i++) {
            results.add(pool.submit(() -> {
                ready.countDown();
                start.await(); // 모든 스레드가 준비될 때까지 기다렸다가 한꺼번에 출발
                return postLikeService.like(post.getId(), member.getId());
            }));
        }
        ready.await();
        start.countDown();

        for (Future<LikeResponse> result : results) {
            // 예외 없이 모두 "좋아요 상태"를 돌려받아야 한다 (중복 요청도 정상 응답)
            assertThat(result.get().isLiked()).isTrue();
        }
        pool.shutdown();

        assertThat(postLikeRepository.countByPostId(post.getId())).isEqualTo(1);
        assertThat(postRepository.findLikeCountById(post.getId())).hasValue(1L);
    }
}
