package com.youngmin.board.post;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** 게시글 검색 기준. 이름(name())이 PostRepository.search 쿼리의 :type 값으로 쓰인다. */
@Getter
@RequiredArgsConstructor
public enum SearchType {
    TITLE_CONTENT("제목+내용"),
    TITLE("제목"),
    CONTENT("내용"),
    AUTHOR("작성자");

    private final String label;
}
