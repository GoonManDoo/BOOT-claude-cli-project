package com.youngmin.board.like.dto;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** 좋아요 처리 결과 (JSON으로 응답: {"liked": true, "likeCount": 3}) */
@Getter
@RequiredArgsConstructor
public class LikeResponse {

    private final boolean liked;
    private final long likeCount;
}
