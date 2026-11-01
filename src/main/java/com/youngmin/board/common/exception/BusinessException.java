package com.youngmin.board.common.exception;

import lombok.Getter;

/**
 * 직접 만드는 모든 예외의 부모 클래스.
 * "게시글이 없다", "권한이 없다"처럼 비즈니스 규칙을 어겼을 때 Service에서 던진다.
 *
 * 사용 예: throw new BusinessException(ErrorCode.MEMBER_NOT_FOUND);
 * 처리: GlobalExceptionHandler가 ErrorCode의 상태 코드와 메시지로 에러 화면을 보여 준다.
 */
@Getter
public class BusinessException extends RuntimeException {

    private final ErrorCode errorCode;

    public BusinessException(ErrorCode errorCode) {
        super(errorCode.getMessage());
        this.errorCode = errorCode;
    }
}
