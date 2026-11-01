package com.youngmin.board.comment;

/** 게시글별 댓글 수 조회 결과 (인터페이스 프로젝션: 쿼리의 별칭 postId, commentCount와 이름이 맞아야 한다) */
public interface CommentCount {

    Long getPostId();

    Long getCommentCount();
}
