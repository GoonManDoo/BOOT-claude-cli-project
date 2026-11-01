package com.youngmin.board.member;

import com.youngmin.board.common.exception.BusinessException;
import com.youngmin.board.common.exception.ErrorCode;
import lombok.Getter;

/**
 * 아이디/이메일 중복 시 던지는 예외.
 * 공통 BusinessException을 상속하고, 에러를 표시할 폼 필드 이름(loginId, email)을 추가로 담는다.
 * MemberController가 직접 잡아서 회원가입 폼의 필드 에러로 보여 준다.
 */
@Getter
public class DuplicateMemberException extends BusinessException {

    private final String field;

    public DuplicateMemberException(String field, ErrorCode errorCode) {
        super(errorCode);
        this.field = field;
    }
}
