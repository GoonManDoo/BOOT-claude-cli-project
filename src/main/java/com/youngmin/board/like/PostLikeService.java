package com.youngmin.board.like;

import com.youngmin.board.common.exception.BusinessException;
import com.youngmin.board.common.exception.ErrorCode;
import com.youngmin.board.common.exception.UniqueViolations;
import com.youngmin.board.like.dto.LikeResponse;
import com.youngmin.board.member.MemberRepository;
import com.youngmin.board.post.PostRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 좋아요 / 좋아요 취소.
 *
 * "토글" 하나로 만들지 않고 좋아요(POST)와 취소(DELETE)를 나눴다.
 * 토글이면 빠르게 두 번 누른 요청이 동시에 들어올 때 서버가 "좋아요 → 취소"인지 "좋아요 → 좋아요"인지 알 수 없다.
 * 원하는 상태를 보내게 하면 같은 요청이 여러 번 와도 결과가 같다 (멱등성).
 *
 * 동시성 처리
 * - 중복 저장: (member_id, post_id) 유니크 제약으로 막는다.
 * - 좋아요 수: 실제로 행이 추가/삭제된 요청만 UPDATE ... SET like_count = like_count ± 1 을 실행한다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PostLikeService {

    private final PostLikeRepository postLikeRepository;
    private final PostRepository postRepository;
    private final MemberRepository memberRepository;
    private final TransactionTemplate transactionTemplate;

    /**
     * 좋아요.
     * 이 메서드에는 @Transactional을 붙이지 않고, 트랜잭션을 TransactionTemplate으로 안쪽에만 연다.
     * INSERT가 유니크 제약에 걸리면 그 트랜잭션은 롤백만 가능한 상태가 되므로,
     * 트랜잭션이 완전히 끝난 "바깥"에서 예외를 잡아야 정상 응답을 돌려줄 수 있다.
     */
    public LikeResponse like(Long postId, Long memberId) {
        try {
            transactionTemplate.executeWithoutResult(status -> {
                if (!postRepository.existsVisibleById(postId)) { // 삭제/숨김 글에는 좋아요 불가
                    throw new BusinessException(ErrorCode.POST_NOT_FOUND);
                }
                if (postLikeRepository.existsByMemberIdAndPostId(memberId, postId)) {
                    return; // 이미 좋아요 상태: 아무것도 바꾸지 않는다
                }
                // getReferenceById: SELECT 없이 id만 가진 프록시로 연관관계를 건다
                postLikeRepository.saveAndFlush(PostLike.of(
                        memberRepository.getReferenceById(memberId),
                        postRepository.getReferenceById(postId)));
                postRepository.increaseLikeCount(postId); // INSERT가 성공한 요청만 +1
            });
        } catch (DataIntegrityViolationException e) {
            if (!UniqueViolations.is(e, PostLike.UK_MEMBER_POST)) {
                throw e; // 중복이 아닌 DB 오류(예: 없는 회원 id)는 숨기지 않는다
            }
            // 동시에 들어온 다른 요청이 먼저 저장했다. 유니크 제약이 두 번째 INSERT를 막았고,
            // 이 요청의 트랜잭션은 통째로 롤백됐으므로 좋아요 수도 오르지 않았다. 결과적으로 "이미 좋아요" 상태.
            log.info("중복 좋아요 요청 무시: memberId={}, postId={}", memberId, postId);
        }
        return getStatus(postId, memberId);
    }

    /** 좋아요 취소. 실제로 지운 요청만 -1. */
    @Transactional
    public LikeResponse unlike(Long postId, Long memberId) {
        int deleted = postLikeRepository.deleteByMemberIdAndPostId(memberId, postId);
        if (deleted > 0) {
            postRepository.decreaseLikeCount(postId);
        }
        return getStatus(postId, memberId);
    }

    @Transactional(readOnly = true)
    public boolean isLiked(Long postId, Long memberId) {
        return memberId != null && postLikeRepository.existsByMemberIdAndPostId(memberId, postId);
    }

    private LikeResponse getStatus(Long postId, Long memberId) {
        long likeCount = postRepository.findLikeCountById(postId)
                .orElseThrow(() -> new BusinessException(ErrorCode.POST_NOT_FOUND));
        return new LikeResponse(postLikeRepository.existsByMemberIdAndPostId(memberId, postId), likeCount);
    }
}
