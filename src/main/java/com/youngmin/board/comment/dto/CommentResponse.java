package com.youngmin.board.comment.dto;

import com.youngmin.board.comment.Comment;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** 댓글 화면용 DTO. 일반 댓글은 replies에 대댓글 목록을 가진다. */
@Getter
@RequiredArgsConstructor(access = AccessLevel.PRIVATE)
public class CommentResponse {

    public static final String DELETED_MESSAGE = "삭제된 댓글입니다";

    private final Long id;
    private final String content;
    private final Long authorId;
    private final String authorNickname;
    private final boolean deleted;
    private final LocalDateTime createdAt;
    private final List<CommentResponse> replies = new ArrayList<>();

    public static CommentResponse from(Comment comment) {
        if (comment.isDeleted()) {
            // 삭제된 댓글은 내용과 작성자를 화면에 내보내지 않는다.
            return new CommentResponse(comment.getId(), DELETED_MESSAGE, null, null, true, comment.getCreatedAt());
        }
        return new CommentResponse(
                comment.getId(),
                comment.getContent(),
                comment.getAuthor().getId(),
                comment.getAuthor().getNickname(),
                false,
                comment.getCreatedAt());
    }

    /** 화면에 보이는 댓글 수 (삭제 표시된 댓글 제외, 대댓글 포함) */
    public static long countVisible(List<CommentResponse> comments) {
        return comments.stream()
                .mapToLong(c -> (c.isDeleted() ? 0 : 1) + c.getReplies().size())
                .sum();
    }
}
