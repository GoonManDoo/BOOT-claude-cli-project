package com.youngmin.board.post;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;

/**
 * 목록 정렬 방식.
 * 클라이언트가 정렬할 컬럼 이름을 직접 보내게 하지 않고, 서버가 정해 둔 정렬만 고르게 한다.
 */
@Getter
@RequiredArgsConstructor
public enum PostSort {
    // id는 저장 순서대로 커지므로 "id 내림차순 = 최신순"이다. PK라서 정렬도 빠르다.
    LATEST("최신순", Sort.by(Sort.Direction.DESC, "id")),
    // 조회수가 같으면 최신 글이 먼저 오게 id를 두 번째 기준으로 둔다. (페이지가 바뀌어도 순서가 흔들리지 않게)
    VIEWS("조회수순", Sort.by(Sort.Direction.DESC, "viewCount").and(Sort.by(Sort.Direction.DESC, "id")));

    private final String label;
    private final Sort sort;
}
