package com.youngmin.board.post.dto;

import com.youngmin.board.image.PostImageResponse;
import com.youngmin.board.post.Post;
import java.time.LocalDateTime;
import java.util.List;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** 게시글 상세 화면/API용 응답 DTO. 화면에 필요한 값만 담는다. (작성자의 비밀번호, 이메일 등은 없음) */
@Getter
@RequiredArgsConstructor(access = AccessLevel.PRIVATE)
public class PostDetailResponse {

    private final Long id;
    private final Long categoryId;
    private final String categoryName;
    private final String title;
    private final String content;
    private final Long authorId;
    private final String authorNickname;
    private final boolean notice;
    private final boolean hidden;
    private final long viewCount;
    private final long likeCount;
    private final LocalDateTime createdAt;
    private final LocalDateTime updatedAt;
    private final List<PostImageResponse> images;

    /** post의 작성자/카테고리가 이미 로딩된 상태(fetch join)에서 호출해야 추가 쿼리가 없다. */
    public static PostDetailResponse of(Post post, List<PostImageResponse> images) {
        return new PostDetailResponse(
                post.getId(),
                post.getCategory() == null ? null : post.getCategory().getId(),
                post.getCategory() == null ? "미분류" : post.getCategory().getName(),
                post.getTitle(),
                post.getContent(),
                post.getAuthor().getId(),
                post.getAuthor().getNickname(),
                post.isNotice(),
                post.isHidden(),
                post.getViewCount(),
                post.getLikeCount(),
                post.getCreatedAt(),
                post.getUpdatedAt(),
                images);
    }

    public boolean isEdited() {
        return updatedAt != null && createdAt != null && updatedAt.isAfter(createdAt.plusSeconds(1));
    }
}
