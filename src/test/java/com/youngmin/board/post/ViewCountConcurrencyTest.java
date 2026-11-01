package com.youngmin.board.post;

import static org.assertj.core.api.Assertions.assertThat;

import com.youngmin.board.category.CategoryRepository;
import com.youngmin.board.member.Member;
import com.youngmin.board.member.MemberRepository;
import com.youngmin.board.support.TestMembers;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * 조회수 동시성 테스트: 100명이 동시에 조회하면 정확히 100이 올라야 한다.
 * 스레드마다 별도 트랜잭션으로 커밋해야 하므로 @Transactional을 붙이지 않고, @AfterEach에서 직접 지운다.
 */
@SpringBootTest
class ViewCountConcurrencyTest {

    @Autowired PostService postService;
    @Autowired PostRepository postRepository;
    @Autowired MemberRepository memberRepository;
    @Autowired CategoryRepository categoryRepository;

    Member member;
    Post post;

    @BeforeEach
    void setUp() {
        member = TestMembers.user(memberRepository, "조회수");
        post = postRepository.save(Post.create("조회수 테스트", "내용", member,
                categoryRepository.findAllByOrderBySortOrderAscIdAsc().get(0)));
    }

    @AfterEach
    void tearDown() {
        postRepository.deleteById(post.getId());
        memberRepository.deleteById(member.getId());
    }

    @Test
    void 백_명이_동시에_조회하면_조회수가_정확히_100_오른다() throws Exception {
        int threadCount = 100;
        ExecutorService pool = Executors.newFixedThreadPool(32);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<?>> futures = new ArrayList<>();

        for (int i = 0; i < threadCount; i++) {
            futures.add(pool.submit(() -> {
                start.await();
                postService.increaseViewCount(post.getId());
                return null;
            }));
        }
        start.countDown(); // 한꺼번에 출발
        for (Future<?> future : futures) {
            future.get();
        }
        pool.shutdown();

        assertThat(postRepository.findById(post.getId()).orElseThrow().getViewCount()).isEqualTo(100);
    }
}
