package com.youngmin.board.admin;

import com.youngmin.board.common.exception.BusinessException;
import com.youngmin.board.common.exception.ErrorCode;
import com.youngmin.board.member.MemberRepository;
import com.youngmin.board.post.Post;
import com.youngmin.board.post.PostRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 관리자 기능. 접근 제한은 SecurityConfig의 /admin/** hasRole("ADMIN")이 담당한다. */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AdminService {

    private static final int PAGE_SIZE = 20;

    private final MemberRepository memberRepository;
    private final PostRepository postRepository;

    public Page<AdminMemberResponse> getMembers(int page) {
        return memberRepository.findAll(pageRequest(page, Sort.by(Sort.Direction.DESC, "id")))
                .map(AdminMemberResponse::from);
    }

    /** 삭제/숨김 글 포함 전체 (작성자 fetch join) */
    public Page<AdminPostResponse> getPosts(int page) {
        return postRepository.findAllForAdmin(pageRequest(page, Sort.by(Sort.Direction.DESC, "id")))
                .map(AdminPostResponse::from);
    }

    /** 신고가 많은 순 */
    public Page<AdminPostResponse> getReportedPosts(int page) {
        Sort sort = Sort.by(Sort.Direction.DESC, "reportCount").and(Sort.by(Sort.Direction.DESC, "id"));
        return postRepository.findReported(pageRequest(page, sort)).map(AdminPostResponse::from);
    }

    @Transactional
    public void hide(Long postId) {
        findNotDeleted(postId).hide(); // 변경 감지로 UPDATE
    }

    /**
     * 숨김 해제. 신고 수는 그대로 둔다 (누가 몇 번 신고했는지 기록이 남아야 한다).
     * 자동 숨김은 신고 수가 "정확히 5가 되는 순간"에만 일어나므로, 복구 후 6번째 신고로 다시 숨겨지지는 않는다.
     */
    @Transactional
    public void restore(Long postId) {
        findNotDeleted(postId).restore();
    }

    /** 작성자가 삭제한 글은 복구 대상이 아니다 (이미지 파일도 지워졌다) */
    private Post findNotDeleted(Long postId) {
        Post post = postRepository.findById(postId)
                .orElseThrow(() -> new BusinessException(ErrorCode.POST_NOT_FOUND));
        if (post.isDeleted()) {
            throw new BusinessException(ErrorCode.POST_NOT_FOUND);
        }
        return post;
    }

    private static PageRequest pageRequest(int page, Sort sort) {
        return PageRequest.of(Math.max(page - 1, 0), PAGE_SIZE, sort);
    }
}
