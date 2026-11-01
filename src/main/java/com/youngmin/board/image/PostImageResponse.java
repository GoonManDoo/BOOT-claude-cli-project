package com.youngmin.board.image;

/** 화면/API에 내보내는 이미지 정보. 저장 경로는 숨기고 id로만 접근한다 (/post-images/{id}). */
public record PostImageResponse(Long id, String originalName, String url) {

    static PostImageResponse from(PostImage image) {
        return new PostImageResponse(image.getId(), image.getOriginalName(), "/post-images/" + image.getId());
    }
}
