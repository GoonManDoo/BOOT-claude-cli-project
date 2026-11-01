package com.youngmin.board.post.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.web.multipart.MultipartFile;

/**
 * 게시글 작성/수정 폼 입력값 (요청 DTO).
 * 작성자, 조회수, 공지 여부처럼 사용자가 정하면 안 되는 값은 아예 필드로 두지 않는다.
 * → 누가 폼에 viewCount=9999, notice=true를 끼워 보내도 바인딩될 곳이 없다.
 */
@Getter
@Setter
@NoArgsConstructor
public class PostRequest {

    @NotNull(message = "카테고리를 선택해 주세요.")
    private Long categoryId;

    @NotBlank(message = "제목을 입력해 주세요.")
    @Size(max = 100, message = "제목은 100자 이하로 입력해 주세요.")
    private String title;

    @NotBlank(message = "내용을 입력해 주세요.")
    @Size(max = 10000, message = "내용은 10000자 이하로 입력해 주세요.") // MySQL TEXT(64KB)를 넘지 않게
    private String content;

    /** 새로 올리는 이미지 (input type="file" name="images" multiple) */
    private List<MultipartFile> images = new ArrayList<>();

    /** 수정 화면에서 삭제하려고 체크한 기존 이미지 id */
    private List<Long> deleteImageIds = new ArrayList<>();

    public static PostRequest of(Long categoryId, String title, String content) {
        PostRequest request = new PostRequest();
        request.categoryId = categoryId;
        request.title = title;
        request.content = content;
        return request;
    }
}
