package com.youngmin.board.common.api;

import java.util.List;
import org.springframework.data.domain.Page;

/**
 * API용 페이지 응답.
 * Spring Data의 Page를 그대로 JSON으로 내보내면 내부 구조(pageable, sort...)가 노출되고 버전마다 모양이 바뀔 수 있다.
 * 필요한 값만 정해서 내보낸다. page는 화면과 같이 1부터 시작한다.
 */
public record PageResponse<T>(List<T> content, int page, int size, long totalElements, int totalPages, boolean hasNext) {

    public static <T> PageResponse<T> from(Page<T> page) {
        return new PageResponse<>(page.getContent(), page.getNumber() + 1, page.getSize(),
                page.getTotalElements(), page.getTotalPages(), page.hasNext());
    }
}
