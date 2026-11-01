package com.youngmin.board.common.dto;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;

/**
 * 목록 하단의 페이지 번호 계산.
 * 페이지 번호를 10개씩 묶어서 보여 준다. (1~10, 11~20, ...)
 * 화면에 보이는 페이지 번호는 1부터 시작한다. (Spring Data의 Page는 0부터)
 */
@Getter
@RequiredArgsConstructor(access = AccessLevel.PRIVATE)
public class PageNavigation {

    private static final int BLOCK_SIZE = 10;

    private final int currentPage;
    private final int totalPages;
    private final int startPage;
    private final int endPage;
    private final boolean hasPrevious;
    private final boolean hasNext;

    public static PageNavigation of(Page<?> page) {
        int total = page.getTotalPages();
        // 주소창에 page=999처럼 없는 페이지를 넣어도 번호 계산이 깨지지 않게 범위를 맞춘다.
        int current = Math.min(page.getNumber() + 1, Math.max(total, 1));
        int start = ((current - 1) / BLOCK_SIZE) * BLOCK_SIZE + 1;
        int end = Math.min(start + BLOCK_SIZE - 1, total);
        return new PageNavigation(current, total, start, end, start > 1, end < total);
    }

    public int getPreviousPage() {
        return startPage - 1;
    }

    public int getNextPage() {
        return endPage + 1;
    }
}
