package com.youngmin.board.post.dto;

import java.util.List;
import org.springframework.data.domain.Page;

/** 목록 화면에 필요한 것: 이번 페이지의 게시글 + 페이지와 상관없이 맨 위에 고정되는 공지 */
public record PostListPage(Page<PostListResponse> posts, List<PostListResponse> notices) {
}
