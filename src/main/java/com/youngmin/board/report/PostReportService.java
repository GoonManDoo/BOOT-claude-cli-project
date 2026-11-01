package com.youngmin.board.report;

import com.youngmin.board.common.exception.BusinessException;
import com.youngmin.board.common.exception.ErrorCode;
import com.youngmin.board.common.exception.UniqueViolations;
import com.youngmin.board.member.MemberRepository;
import com.youngmin.board.post.Post;
import com.youngmin.board.post.PostRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 게시글 신고. 신고가 5번 쌓이는 순간 자동으로 숨긴다.
 *
 * 동시성
 * - 같은 회원의 중복 신고: (member_id, post_id) 유니크 제약 (좋아요와 같은 방식)
 * - 신고 수: UPDATE ... SET report_count = report_count + 1 (조회수와 같은 방식)
 * - 자동 숨김: UPDATE ... SET hidden = true WHERE report_count = 5
 *   위의 +1 UPDATE가 이 행을 잠근 상태라, 같은 트랜잭션 안에서 본 report_count는 다른 요청과 섞이지 않는다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PostReportService {

    public static final int AUTO_HIDE_THRESHOLD = 5;

    private final PostReportRepository postReportRepository;
    private final PostRepository postRepository;
    private final MemberRepository memberRepository;
    private final TransactionTemplate transactionTemplate;

    /** @return 이 신고로 글이 숨김 처리됐으면 true */
    public boolean report(Long postId, Long memberId, String reason) {
        try {
            Boolean hidden = transactionTemplate.execute(status -> {
                Post post = postRepository.findVisibleById(postId)
                        .orElseThrow(() -> new BusinessException(ErrorCode.POST_NOT_FOUND));
                if (post.isWrittenBy(memberId)) {
                    throw new BusinessException(ErrorCode.CANNOT_REPORT_OWN_POST);
                }
                if (postReportRepository.existsByMemberIdAndPostId(memberId, postId)) {
                    throw new BusinessException(ErrorCode.ALREADY_REPORTED);
                }
                postReportRepository.saveAndFlush(
                        PostReport.of(memberRepository.getReferenceById(memberId), post, normalize(reason)));
                postRepository.increaseReportCount(postId);
                int hiddenRows = postRepository.hideIfReportCountReached(postId, AUTO_HIDE_THRESHOLD);
                if (hiddenRows > 0) {
                    log.info("신고 {}회 누적으로 게시글 자동 숨김: postId={}", AUTO_HIDE_THRESHOLD, postId);
                }
                return hiddenRows > 0;
            });
            return Boolean.TRUE.equals(hidden);
        } catch (DataIntegrityViolationException e) {
            if (!UniqueViolations.is(e, PostReport.UK_MEMBER_POST)) {
                throw e; // 중복이 아닌 DB 오류는 숨기지 않는다
            }
            // 같은 회원의 신고가 동시에 두 번 들어와 유니크 제약이 두 번째를 막았다
            throw new BusinessException(ErrorCode.ALREADY_REPORTED);
        }
    }

    private static String normalize(String reason) {
        return reason == null || reason.isBlank() ? null : reason.strip();
    }
}
