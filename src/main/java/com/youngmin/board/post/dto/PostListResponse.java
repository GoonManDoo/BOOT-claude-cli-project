package com.youngmin.board.post.dto;

import com.youngmin.board.post.Post;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** 게시글 목록 한 줄에 필요한 값. 목록에는 본문(content)이 필요 없으므로 담지 않는다. */
@Getter
@RequiredArgsConstructor(access = AccessLevel.PRIVATE)
public class PostListResponse {

    private final Long id;
    private final String categoryName;
    private final String title;
    private final String authorNickname;
    private final boolean notice;
    private final long viewCount;
    private final long likeCount;
    private final long commentCount;
    private final LocalDateTime createdAt;

    public static PostListResponse of(Post post, long commentCount) {
        return new PostListResponse(
                post.getId(),
                post.getCategory() == null ? "미분류" : post.getCategory().getName(),
                post.getTitle(),
                post.getAuthor().getNickname(),
                post.isNotice(),
                post.getViewCount(),
                post.getLikeCount(),
                commentCount,
                post.getCreatedAt());
    }
}
