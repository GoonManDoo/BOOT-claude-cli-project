package com.youngmin.board.post;

import static org.assertj.core.api.Assertions.assertThat;

import com.youngmin.board.category.Category;
import com.youngmin.board.category.CategoryRepository;
import com.youngmin.board.common.config.JpaAuditingConfig;
import com.youngmin.board.member.Member;
import com.youngmin.board.member.MemberRepository;
import com.youngmin.board.support.TestMembers;
import jakarta.persistence.EntityManager;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;

/**
 * PostRepository 검색/페이징 쿼리 테스트.
 * @DataJpaTest: JPA 관련 빈만 띄운다 (컨트롤러, 서비스, Security 없음) → 전체 통합 테스트보다 가볍다.
 * replace = NONE: 내장 DB(H2)로 바꾸지 않고 실제 MySQL을 쓴다. (JPQL이 MySQL에서 맞게 동작하는지가 중요하므로)
 * 기존 데이터와 섞이지 않게, 테스트마다 새 카테고리를 만들고 그 카테고리로만 검색한다. 끝나면 롤백된다.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(JpaAuditingConfig.class) // @DataJpaTest는 @Configuration을 스캔하지 않으므로 Auditing 설정을 직접 가져온다
class PostRepositoryTest {

    @Autowired PostRepository postRepository;
    @Autowired CategoryRepository categoryRepository;
    @Autowired MemberRepository memberRepository;
    @Autowired EntityManager em;

    Category category;
    Member alice;
    Member bob;

    @BeforeEach
    void setUp() {
        category = categoryRepository.save(Category.of("테스트" + UUID.randomUUID().toString().substring(0, 8), 99));
        alice = TestMembers.user(memberRepository, "앨리스");
        bob = TestMembers.user(memberRepository, "밥");
        for (int i = 1; i <= 15; i++) {
            Member author = i % 3 == 0 ? bob : alice;
            postRepository.save(Post.create("스프링 글 " + i, "본문 " + i, author, category));
        }
        postRepository.save(Post.create("JPA 이야기", "스프링 데이터 JPA 본문", bob, category));
    }

    @Test
    void 페이지_크기만큼_자르고_전체_개수와_페이지_수를_계산한다() {
        Page<Post> first = search(null, null, 0, PostSort.LATEST);
        Page<Post> second = search(null, null, 1, PostSort.LATEST);

        assertThat(first.getContent()).hasSize(10);
        assertThat(first.getTotalElements()).isEqualTo(16);
        assertThat(first.getTotalPages()).isEqualTo(2);
        assertThat(second.getContent()).hasSize(6);
        // 최신순: 마지막에 쓴 글이 첫 페이지 맨 위
        assertThat(first.getContent().get(0).getTitle()).isEqualTo("JPA 이야기");
    }

    @Test
    void 검색_종류별로_조건이_다르게_적용된다() {
        assertThat(search("TITLE", "스프링", 0, PostSort.LATEST).getTotalElements()).isEqualTo(15);
        assertThat(search("CONTENT", "스프링", 0, PostSort.LATEST).getTotalElements()).isEqualTo(1);
        assertThat(search("TITLE_CONTENT", "스프링", 0, PostSort.LATEST).getTotalElements()).isEqualTo(16);
        assertThat(search("AUTHOR", "밥", 0, PostSort.LATEST).getTotalElements()).isEqualTo(6);
    }

    @Test
    void 삭제_숨김_공지는_목록에서_빠진다() {
        Post deleted = postRepository.save(Post.create("삭제될 글", "x", alice, category));
        deleted.softDelete();
        Post hidden = postRepository.save(Post.create("숨길 글", "x", alice, category));
        hidden.hide();
        postRepository.save(Post.createNotice("공지 글", "x", alice, category));
        em.flush();

        assertThat(search(null, null, 0, PostSort.LATEST).getTotalElements()).isEqualTo(16);
        assertThat(postRepository.findVisibleNotices()).extracting(Post::getTitle).contains("공지 글");
    }

    @Test
    void 조회수순으로_정렬하고_같으면_최신_글이_먼저다() {
        Post popular = search("TITLE", "스프링 글 3", 0, PostSort.LATEST).getContent().get(0);
        for (int i = 0; i < 5; i++) {
            postRepository.increaseViewCount(popular.getId());
        }
        em.clear();

        Page<Post> byViews = search(null, null, 0, PostSort.VIEWS);
        assertThat(byViews.getContent().get(0).getId()).isEqualTo(popular.getId());
        assertThat(byViews.getContent().get(0).getViewCount()).isEqualTo(5);
        assertThat(byViews.getContent().get(1).getTitle()).isEqualTo("JPA 이야기");
    }

    private Page<Post> search(String type, String keyword, int page, PostSort sort) {
        String searchType = type == null ? "TITLE_CONTENT" : type;
        return postRepository.search(searchType, keyword, category.getId(), PageRequest.of(page, 10, sort.getSort()));
    }
}
