package com.youngmin.board.image;

import com.youngmin.board.common.exception.BusinessException;
import com.youngmin.board.common.exception.ErrorCode;

/** 업로드한 이미지가 규칙(개수, 크기, 형식)을 어김. 글쓰기 폼에 필드 에러로 보여 준다. */
public class ImageValidationException extends BusinessException {

    public ImageValidationException(ErrorCode errorCode) {
        super(errorCode);
    }
}
