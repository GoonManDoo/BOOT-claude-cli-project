package com.youngmin.board.post.dto;

import com.youngmin.board.post.PostSort;
import com.youngmin.board.post.SearchType;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 목록 검색 조건 (쿼리 파라미터 ?categoryId=2&type=TITLE&keyword=스프링&sort=VIEWS).
 * 값이 비어 있으면 기본값을 돌려줘서 화면과 서비스가 null을 신경 쓰지 않게 한다.
 */
@Setter
@NoArgsConstructor
public class PostSearchCondition {

    @Getter
    private Long categoryId;
    private SearchType type;
    private String keyword;
    private PostSort sort;

    public SearchType getType() {
        return type == null ? SearchType.TITLE_CONTENT : type;
    }

    /** 앞뒤 공백을 지우고, 비어 있으면 null (= 검색 안 함) */
    public String getKeyword() {
        return keyword == null || keyword.isBlank() ? null : keyword.strip();
    }

    public PostSort getSort() {
        return sort == null ? PostSort.LATEST : sort;
    }
}
