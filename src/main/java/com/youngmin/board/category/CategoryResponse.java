package com.youngmin.board.category;

/** 캐시에 저장하는 카테고리 정보. 캐시에는 엔티티 대신 바뀌지 않는(immutable) DTO를 넣는다. */
public record CategoryResponse(Long id, String name) {

    static CategoryResponse from(Category category) {
        return new CategoryResponse(category.getId(), category.getName());
    }
}
