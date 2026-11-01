package com.youngmin.board.image;

import java.util.Optional;
import java.util.function.Supplier;
import org.springframework.validation.BindingResult;

/**
 * 이미지를 첨부하는 폼(글쓰기, 글 수정, 공지 쓰기)의 공통 저장 흐름.
 *
 * 1) 입력값 검증 에러가 있으면 저장하지 않는다.
 * 2) 저장하다가 이미지 규칙(개수, 크기, 형식)을 어기면, 에러 화면 대신 폼의 images 필드 에러로 바꾼다.
 *    (서비스 트랜잭션은 이미 롤백됐으므로 글도 저장되지 않았다)
 */
public final class ImageForms {

    private ImageForms() {
    }

    /** @return 저장에 성공하면 action의 결과(게시글 id), 폼을 다시 보여 줘야 하면 empty */
    public static <T> Optional<T> submit(BindingResult bindingResult, Supplier<T> action) {
        if (bindingResult.hasErrors()) {
            return Optional.empty();
        }
        try {
            return Optional.of(action.get());
        } catch (ImageValidationException e) {
            bindingResult.rejectValue("images", e.getErrorCode().name(), e.getMessage());
            return Optional.empty();
        }
    }
}
