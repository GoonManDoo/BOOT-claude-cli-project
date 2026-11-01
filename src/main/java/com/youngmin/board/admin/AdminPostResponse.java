package com.youngmin.board.admin;

import com.youngmin.board.post.Post;
import java.time.LocalDateTime;

/** 관리자 게시글 목록 한 줄. 삭제/숨김 상태와 신고 수까지 보여 준다. */
public record AdminPostResponse(Long id, String title, String authorNickname, boolean notice,
                                boolean deleted, boolean hidden, int reportCount, LocalDateTime createdAt) {

    static AdminPostResponse from(Post post) {
        return new AdminPostResponse(post.getId(), post.getTitle(), post.getAuthor().getNickname(), post.isNotice(),
                post.isDeleted(), post.isHidden(), post.getReportCount(), post.getCreatedAt());
    }

    public String status() {
        if (deleted) {
            return "삭제됨";
        }
        return hidden ? "숨김" : "공개";
    }
}
