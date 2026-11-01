package com.youngmin.board.comment.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** 댓글 작성/수정 입력값. parentId가 있으면 대댓글. */
@Getter
@Setter
@NoArgsConstructor
public class CommentRequest {

    @NotBlank(message = "댓글 내용을 입력해 주세요.")
    @Size(max = 1000, message = "댓글은 1000자 이하로 입력해 주세요.")
    private String content;

    private Long parentId;

    public static CommentRequest of(String content, Long parentId) {
        CommentRequest request = new CommentRequest();
        request.content = content;
        request.parentId = parentId;
        return request;
    }
}
