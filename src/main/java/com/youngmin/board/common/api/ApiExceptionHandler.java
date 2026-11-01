package com.youngmin.board.common.api;

import com.youngmin.board.common.exception.BusinessException;
import com.youngmin.board.common.exception.ErrorCode;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.ErrorResponse;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * API(@RestController)에서 난 예외를 JSON(ApiResponse)으로 바꾼다.
 *
 * 화면용 GlobalExceptionHandler와 나눈 방법:
 * - annotations = RestController.class: @RestController가 붙은 컨트롤러에만 적용된다.
 * - @Order(HIGHEST_PRECEDENCE): 두 핸들러가 모두 해당될 때 이쪽이 먼저 선택된다.
 *   (GlobalExceptionHandler는 범위 제한이 없어서 API 컨트롤러에도 해당되기 때문)
 */
@Slf4j
@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice(annotations = RestController.class)
public class ApiExceptionHandler {

    /** 비즈니스 예외: ErrorCode의 상태 코드 그대로 (403 작성자 아님, 404 없는 글 ...) */
    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiResponse<Void>> handleBusinessException(BusinessException e) {
        log.warn("API BusinessException [{}]: {}", e.getErrorCode(), e.getMessage());
        return toResponse(e.getErrorCode());
    }

    /** @Valid @RequestBody 검증 실패 → 400 + 어떤 필드가 왜 틀렸는지 */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleValidation(MethodArgumentNotValidException e) {
        List<ApiResponse.FieldError> fieldErrors = e.getBindingResult().getFieldErrors().stream()
                .map(error -> new ApiResponse.FieldError(error.getField(), error.getDefaultMessage()))
                .toList();
        return ResponseEntity.status(ErrorCode.INVALID_INPUT.getStatus())
                .body(ApiResponse.fail(ErrorCode.INVALID_INPUT, "입력값이 올바르지 않습니다.", fieldErrors));
    }

    /** JSON 문법 오류, 타입이 안 맞는 값 등 → 400 */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiResponse<Void>> handleNotReadable(HttpMessageNotReadableException e) {
        return ResponseEntity.status(ErrorCode.INVALID_INPUT.getStatus())
                .body(ApiResponse.fail(ErrorCode.INVALID_INPUT, "요청 본문(JSON) 형식이 올바르지 않습니다.", List.of()));
    }

    /** Security 예외는 Security가 처리하도록 다시 던진다 (401/403 JSON은 SecurityConfig의 핸들러가 만든다) */
    @ExceptionHandler({AccessDeniedException.class, AuthenticationException.class})
    public void rethrowSecurityException(RuntimeException e) {
        throw e;
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleException(Exception e) {
        if (e instanceof ErrorResponse errorResponse) {
            log.warn("API {}: {}", e.getClass().getSimpleName(), e.getMessage());
            return toResponse(ErrorCode.fromHttpStatus(errorResponse.getStatusCode()));
        }
        log.error("API 처리되지 않은 예외", e);
        return toResponse(ErrorCode.INTERNAL_SERVER_ERROR);
    }

    private ResponseEntity<ApiResponse<Void>> toResponse(ErrorCode errorCode) {
        return ResponseEntity.status(errorCode.getStatus()).body(ApiResponse.fail(errorCode));
    }
}
