package com.youngmin.board.common.api;

import com.youngmin.board.common.exception.ErrorCode;
import java.util.List;

/**
 * 모든 API 응답의 공통 형식.
 *
 * 성공: { "success": true,  "data": {...}, "error": null }
 * 실패: { "success": false, "data": null,  "error": { "code": "POST_NOT_FOUND", "message": "...", "fieldErrors": [] } }
 *
 * 클라이언트는 항상 같은 모양을 받으므로, success만 보고 data를 쓸지 error를 보여 줄지 정할 수 있다.
 */
public record ApiResponse<T>(boolean success, T data, ApiError error) {

    public static <T> ApiResponse<T> ok(T data) {
        return new ApiResponse<>(true, data, null);
    }

    public static ApiResponse<Void> fail(ErrorCode errorCode) {
        return fail(errorCode, errorCode.getMessage(), List.of());
    }

    public static ApiResponse<Void> fail(ErrorCode errorCode, String message, List<FieldError> fieldErrors) {
        return new ApiResponse<>(false, null, new ApiError(errorCode.name(), message, fieldErrors));
    }

    public record ApiError(String code, String message, List<FieldError> fieldErrors) {
    }

    /** 검증 실패한 입력값 하나 (예: title - "제목을 입력해 주세요.") */
    public record FieldError(String field, String message) {
    }
}
