package com.youngmin.board.post;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.youngmin.board.category.Category;
import com.youngmin.board.category.CategoryRepository;
import com.youngmin.board.category.CategoryResponse;
import com.youngmin.board.category.CategoryService;
import com.youngmin.board.comment.CommentRepository;
import com.youngmin.board.common.exception.BusinessException;
import com.youngmin.board.common.exception.ErrorCode;
import com.youngmin.board.image.PostImageService;
import com.youngmin.board.member.Member;
import com.youngmin.board.member.MemberRepository;
import com.youngmin.board.post.dto.PostRequest;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * PostService 단위 테스트.
 * Spring도 DB도 띄우지 않는다. 의존 객체(Repository 등)는 Mockito 가짜(mock)로 바꾸고,
 * "서비스의 규칙"(작성자만 수정/삭제, 없는 글은 예외)만 빠르게 검증한다. (테스트 하나에 수 ms)
 * DB 쿼리가 맞는지는 여기서 확인할 수 없다 → PostRepositoryTest(통합)에서 확인한다.
 */
@ExtendWith(MockitoExtension.class)
class PostServiceTest {

    @Mock PostRepository postRepository;
    @Mock MemberRepository memberRepository;
    @Mock CategoryRepository categoryRepository;
    @Mock CommentRepository commentRepository;
    @Mock CategoryService categoryService;
    @Mock PostImageService postImageService;

    @InjectMocks PostService postService;

    Member writer;
    Category category;

    @BeforeEach
    void setUp() {
        writer = member(1L);
        category = Category.of("자유", 1);
        ReflectionTestUtils.setField(category, "id", 10L);
    }

    @Nested
    @DisplayName("작성")
    class Create {

        @Test
        void 글을_저장하고_이미지를_첨부한다() {
            given(categoryService.getCategories()).willReturn(List.of(new CategoryResponse(10L, "자유")));
            given(memberRepository.getReferenceById(1L)).willReturn(writer);
            given(categoryRepository.getReferenceById(10L)).willReturn(category);

            postService.create(PostRequest.of(10L, "제목", "내용"), 1L);

            verify(postRepository).save(any(Post.class));
            verify(postImageService).addImages(any(Post.class), anyList());
        }

        @Test
        void 없는_카테고리면_예외이고_저장하지_않는다() {
            given(categoryService.getCategories()).willReturn(List.of(new CategoryResponse(10L, "자유")));

            assertThatThrownBy(() -> postService.create(PostRequest.of(999L, "제목", "내용"), 1L))
                    .isInstanceOf(BusinessException.class)
                    .extracting("errorCode").isEqualTo(ErrorCode.CATEGORY_NOT_FOUND);
            verify(postRepository, never()).save(any());
        }
    }

    @Nested
    @DisplayName("수정")
    class Update {

        @Test
        void 작성자는_수정할_수_있다() {
            Post post = post(100L, writer);
            given(postRepository.findVisibleById(100L)).willReturn(Optional.of(post));
            given(categoryService.getCategories()).willReturn(List.of(new CategoryResponse(10L, "자유")));
            given(categoryRepository.getReferenceById(10L)).willReturn(category);

            postService.update(100L, PostRequest.of(10L, "바뀐 제목", "바뀐 내용"), 1L);

            // save()를 부르지 않아도 엔티티 값이 바뀌어 있다 → 실제 DB 반영은 변경 감지가 한다
            assertThat(post.getTitle()).isEqualTo("바뀐 제목");
            verify(postRepository, never()).save(any());
        }

        @Test
        void 작성자가_아니면_NOT_AUTHOR_예외이고_글은_그대로다() {
            Post post = post(100L, writer);
            given(postRepository.findVisibleById(100L)).willReturn(Optional.of(post));

            assertThatThrownBy(() -> postService.update(100L, PostRequest.of(10L, "해킹", "해킹"), 2L))
                    .isInstanceOf(BusinessException.class)
                    .extracting("errorCode").isEqualTo(ErrorCode.NOT_AUTHOR);
            assertThat(post.getTitle()).isEqualTo("원래 제목");
        }
    }

    @Nested
    @DisplayName("삭제")
    class Delete {

        @Test
        void 작성자가_삭제하면_소프트_삭제되고_이미지가_지워진다() {
            Post post = post(100L, writer);
            given(postRepository.findVisibleById(100L)).willReturn(Optional.of(post));

            postService.delete(100L, 1L);

            assertThat(post.isDeleted()).isTrue();
            verify(postImageService).removeAll(post);
            verify(postRepository, never()).delete(any()); // 실제 DELETE는 하지 않는다
        }

        @Test
        void 작성자가_아니면_삭제할_수_없다() {
            Post post = post(100L, writer);
            given(postRepository.findVisibleById(100L)).willReturn(Optional.of(post));

            assertThatThrownBy(() -> postService.delete(100L, 2L))
                    .isInstanceOf(BusinessException.class)
                    .extracting("errorCode").isEqualTo(ErrorCode.NOT_AUTHOR);
            assertThat(post.isDeleted()).isFalse();
        }
    }

    @Nested
    @DisplayName("조회")
    class Read {

        @Test
        void 없는_글을_조회하면_POST_NOT_FOUND() {
            given(postRepository.findWithDetailsIncludingHiddenById(404L)).willReturn(Optional.empty());

            assertThatThrownBy(() -> postService.getDetail(404L, false))
                    .isInstanceOf(BusinessException.class)
                    .extracting("errorCode").isEqualTo(ErrorCode.POST_NOT_FOUND);
        }

        @Test
        void 숨김_글은_일반_사용자에게_없는_글처럼_보인다() {
            Post post = post(100L, writer);
            post.hide();
            given(postRepository.findWithDetailsIncludingHiddenById(100L)).willReturn(Optional.of(post));

            assertThatThrownBy(() -> postService.getDetail(100L, false))
                    .extracting("errorCode").isEqualTo(ErrorCode.POST_NOT_FOUND);
            assertThat(postService.getDetail(100L, true).getTitle()).isEqualTo("원래 제목"); // 관리자는 볼 수 있다
        }
    }

    private static Member member(Long id) {
        Member member = Member.createUser("user" + id, "hash", "닉네임" + id, "user" + id + "@example.com");
        ReflectionTestUtils.setField(member, "id", id);
        return member;
    }

    private Post post(Long id, Member author) {
        Post post = Post.create("원래 제목", "원래 내용", author, category);
        ReflectionTestUtils.setField(post, "id", id);
        return post;
    }
}
