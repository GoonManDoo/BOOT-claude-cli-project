package com.youngmin.board.post.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * API(JSON)용 작성/수정 요청. 화면용 PostRequest는 파일(MultipartFile)을 받는 폼 전용이라 따로 둔다.
 * 서비스에는 PostRequest로 바꿔서 넘긴다 → 서비스 로직은 화면과 API가 같이 쓴다.
 */
public record PostApiRequest(
        @NotNull(message = "카테고리를 선택해 주세요.")
        Long categoryId,

        @NotBlank(message = "제목을 입력해 주세요.")
        @Size(max = 100, message = "제목은 100자 이하로 입력해 주세요.")
        String title,

        @NotBlank(message = "내용을 입력해 주세요.")
        @Size(max = 10000, message = "내용은 10000자 이하로 입력해 주세요.")
        String content) {

    public PostRequest toPostRequest() {
        return PostRequest.of(categoryId, title, content);
    }
}
