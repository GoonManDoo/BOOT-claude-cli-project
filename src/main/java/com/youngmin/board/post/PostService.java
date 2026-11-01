package com.youngmin.board.post;

import com.youngmin.board.category.CategoryRepository;
import com.youngmin.board.category.CategoryService;
import com.youngmin.board.comment.CommentCount;
import com.youngmin.board.comment.CommentRepository;
import com.youngmin.board.common.exception.BusinessException;
import com.youngmin.board.common.exception.ErrorCode;
import com.youngmin.board.image.PostImageService;
import com.youngmin.board.member.Member;
import com.youngmin.board.member.MemberRepository;
import com.youngmin.board.member.Role;
import com.youngmin.board.post.dto.PostDetailResponse;
import com.youngmin.board.post.dto.PostListPage;
import com.youngmin.board.post.dto.PostListResponse;
import com.youngmin.board.post.dto.PostRequest;
import com.youngmin.board.post.dto.PostSearchCondition;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 게시글 비즈니스 로직. 화면용 PostController와 API용 PostApiController가 함께 쓴다.
 *
 * 클래스에 @Transactional(readOnly = true): 조회 메서드는 기본으로 읽기 전용 트랜잭션.
 *   - 지연 로딩(post.getAuthor().getNickname())이 트랜잭션 안에서 안전하게 동작한다.
 *   - 읽기 전용이면 Hibernate가 변경 감지용 스냅샷 비교(flush)를 생략해서 조금 더 가볍다.
 * 데이터를 바꾸는 메서드에만 @Transactional을 다시 붙여 쓰기 트랜잭션으로 바꾼다.
 *   - 메서드 안의 작업이 모두 성공하거나 모두 취소된다 (예: 글 저장 + 이미지 저장).
 *   - 메서드가 끝날 때 커밋하면서 변경 감지가 일어난다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PostService {

    public static final int PAGE_SIZE = 10;

    private final PostRepository postRepository;
    private final MemberRepository memberRepository;
    private final CategoryRepository categoryRepository;
    private final CommentRepository commentRepository;
    private final CategoryService categoryService;
    private final PostImageService postImageService;

    /**
     * 목록 화면: 검색 결과 + 맨 위에 고정되는 공지. 실행되는 쿼리 4번 (게시글 수와 상관없이 고정):
     *   1) 게시글 + 작성자 + 카테고리 (fetch join, limit 10)
     *   2) 전체 개수 (count) - 첫 페이지인데 10개보다 적게 나오면 Spring Data가 생략한다
     *   3) 공지 + 작성자 + 카테고리 (fetch join)
     *   4) 게시글과 공지의 댓글 수를 한 번에 (IN + GROUP BY)
     */
    public PostListPage getListPage(PostSearchCondition condition, int page) {
        Page<Post> posts = searchPosts(condition, page);
        List<Post> notices = postRepository.findVisibleNotices();

        List<Post> all = new ArrayList<>(posts.getContent());
        all.addAll(notices);
        Map<Long, Long> commentCounts = countComments(all);

        return new PostListPage(
                posts.map(post -> toListResponse(post, commentCounts)),
                notices.stream().map(post -> toListResponse(post, commentCounts)).toList());
    }

    /** 검색 결과만 (API용, 공지 없음). 쿼리 3번 */
    public Page<PostListResponse> search(PostSearchCondition condition, int page) {
        Page<Post> posts = searchPosts(condition, page);
        Map<Long, Long> commentCounts = countComments(posts.getContent());
        return posts.map(post -> toListResponse(post, commentCounts));
    }

    private Page<Post> searchPosts(PostSearchCondition condition, int page) {
        Pageable pageable = PageRequest.of(Math.max(page - 1, 0), PAGE_SIZE, condition.getSort().getSort());
        return postRepository.search(condition.getType().name(),
                PostRepository.escapeLike(condition.getKeyword()), // 검색어의 %, _를 글자 그대로 찾는다
                condition.getCategoryId(), pageable);
    }

    private static PostListResponse toListResponse(Post post, Map<Long, Long> commentCounts) {
        return PostListResponse.of(post, commentCounts.getOrDefault(post.getId(), 0L));
    }

    /** 상세. 삭제된 글은 아무도 못 보고, 숨김 글은 관리자만 본다. */
    public PostDetailResponse getDetail(Long postId, boolean viewerIsAdmin) {
        Post post = postRepository.findWithDetailsIncludingHiddenById(postId)
                .orElseThrow(() -> new BusinessException(ErrorCode.POST_NOT_FOUND));
        if (post.isHidden() && !viewerIsAdmin) {
            throw new BusinessException(ErrorCode.POST_NOT_FOUND);
        }
        return PostDetailResponse.of(post, postImageService.getImages(postId));
    }

    /** 수정 폼에 채울 값. 폼을 보여 주는 단계에서도 작성자인지 확인한다 (URL 직접 입력 차단). */
    public PostRequest getEditForm(Long postId, Long memberId) {
        Post post = findVisiblePost(postId);
        validateAuthor(post, memberId);
        Long categoryId = post.getCategory() == null ? null : post.getCategory().getId();
        return PostRequest.of(categoryId, post.getTitle(), post.getContent());
    }

    @Transactional
    public Long create(PostRequest request, Long memberId) {
        validateCategory(request.getCategoryId());
        Post post = Post.create(request.getTitle(), request.getContent(),
                memberRepository.getReferenceById(memberId),
                categoryRepository.getReferenceById(request.getCategoryId()));
        postRepository.save(post); // 새 엔티티는 save()로 영속성 컨텍스트에 넣어야 INSERT 된다
        postImageService.addImages(post, request.getImages());
        return post.getId();
    }

    /**
     * 공지 작성. URL(/admin/**)에서 이미 ADMIN만 들어오게 막았지만, 서비스에서도 한 번 더 확인한다.
     * (나중에 다른 경로에서 이 메서드를 호출해도 규칙이 지켜지도록)
     */
    @Transactional
    public Long createNotice(PostRequest request, Long memberId) {
        Member member = memberRepository.findById(memberId)
                .orElseThrow(() -> new BusinessException(ErrorCode.MEMBER_NOT_FOUND));
        if (member.getRole() != Role.ADMIN) {
            throw new BusinessException(ErrorCode.NOTICE_ADMIN_ONLY);
        }
        validateCategory(request.getCategoryId());
        Post post = Post.createNotice(request.getTitle(), request.getContent(), member,
                categoryRepository.getReferenceById(request.getCategoryId()));
        postRepository.save(post);
        postImageService.addImages(post, request.getImages());
        return post.getId();
    }

    /**
     * 수정. save()를 부르지 않는다.
     * findVisiblePost()로 가져온 post는 영속성 컨텍스트가 관리하는 엔티티다.
     * 값을 바꾸면 트랜잭션 커밋 직전에 JPA가 처음 읽었을 때의 스냅샷과 비교해서 UPDATE를 만든다 (변경 감지).
     */
    @Transactional
    public void update(Long postId, PostRequest request, Long memberId) {
        Post post = findVisiblePost(postId);
        validateAuthor(post, memberId);
        validateCategory(request.getCategoryId());
        post.update(request.getTitle(), request.getContent(),
                categoryRepository.getReferenceById(request.getCategoryId()));
        postImageService.removeImages(post, request.getDeleteImageIds());
        postImageService.addImages(post, request.getImages());
    }

    /**
     * 소프트 삭제: 행은 남기고 deleted = true. 목록/검색/상세에서 사라진다.
     * 이미지는 다시 볼 일이 없으므로 DB 행과 실제 파일을 지운다 (파일은 커밋 후).
     * 댓글과 좋아요는 그대로 둔다 (글이 안 보이므로 함께 안 보인다).
     */
    @Transactional
    public void delete(Long postId, Long memberId) {
        Post post = findVisiblePost(postId);
        validateAuthor(post, memberId);
        postImageService.removeAll(post);
        post.softDelete();
    }

    /**
     * 조회수 +1 (UPDATE posts SET view_count = view_count + 1).
     * 엔티티의 값을 바꾸는 방식(post.setViewCount(post.getViewCount() + 1))은 동시에 조회하면 숫자가 누락된다.
     */
    @Transactional
    public void increaseViewCount(Long postId) {
        postRepository.increaseViewCount(postId);
    }

    private Map<Long, Long> countComments(List<Post> posts) {
        if (posts.isEmpty()) {
            return Map.of();
        }
        List<Long> postIds = posts.stream().map(Post::getId).toList();
        return commentRepository.countByPostIds(postIds).stream()
                .collect(Collectors.toMap(CommentCount::getPostId, CommentCount::getCommentCount));
    }

    /** 캐시된 카테고리 목록으로 확인 → DB 조회 없음 */
    private void validateCategory(Long categoryId) {
        boolean exists = categoryService.getCategories().stream().anyMatch(c -> c.id().equals(categoryId));
        if (!exists) {
            throw new BusinessException(ErrorCode.CATEGORY_NOT_FOUND);
        }
    }

    private Post findVisiblePost(Long postId) {
        return postRepository.findVisibleById(postId)
                .orElseThrow(() -> new BusinessException(ErrorCode.POST_NOT_FOUND));
    }

    /** 서버 측 권한 검증: 화면에서 버튼을 숨겨도 URL을 직접 호출할 수 있으므로 여기서 반드시 막는다. */
    private void validateAuthor(Post post, Long memberId) {
        if (!post.isWrittenBy(memberId)) {
            throw new BusinessException(ErrorCode.NOT_AUTHOR);
        }
    }
}
