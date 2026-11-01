package com.youngmin.board.common.exception;

import com.youngmin.board.common.api.ApiErrorWriter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.ErrorResponse;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.View;

/**
 * 모든 컨트롤러에서 처리되지 않은 예외를 받아 에러 화면(templates/error.html)을 보여 준다.
 *
 * 폼 입력 검증 에러(BindingResult)와 회원가입 중복 에러는 여기로 오지 않는다.
 * 그런 에러는 컨트롤러가 직접 잡아서 "폼 화면 + 필드 메시지"로 보여 주는 게 사용자에게 더 낫기 때문이다.
 */
@Slf4j
@ControllerAdvice
public class GlobalExceptionHandler {

    /** 직접 던진 비즈니스 예외: ErrorCode에 정해 둔 상태 코드와 메시지를 그대로 사용 */
    @ExceptionHandler(BusinessException.class)
    public ModelAndView handleBusinessException(BusinessException e) {
        log.warn("BusinessException [{}]: {}", e.getErrorCode(), e.getMessage());
        return errorPage(e.getErrorCode());
    }

    /** 업로드 크기가 서버 한도(spring.servlet.multipart)를 넘음 → 413 */
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ModelAndView handleMaxUploadSize(MaxUploadSizeExceededException e) {
        log.warn("업로드 크기 초과: {}", e.getMessage());
        return errorPage(ErrorCode.CONTENT_TOO_LARGE);
    }

    /**
     * Spring Security 예외는 여기서 처리하지 않고 다시 던진다.
     * 그래야 Security가 로그인 화면으로 보내거나(미인증) 403을 내려 준다(권한 없음).
     * 아래 Exception 핸들러가 이것까지 잡으면 권한 문제가 500 에러로 둔갑한다.
     */
    @ExceptionHandler({AccessDeniedException.class, AuthenticationException.class})
    public void rethrowSecurityException(RuntimeException e) {
        throw e;
    }

    /** 그 밖의 모든 예외 */
    @ExceptionHandler(Exception.class)
    public ModelAndView handleException(Exception e) {
        // Spring MVC가 던지는 예외(없는 URL, 잘못된 HTTP 메서드, 파라미터 타입 오류 등)는
        // ErrorResponse를 구현하고 있어서 알맞은 상태 코드를 알 수 있다. 500으로 뭉뚱그리지 않는다.
        if (e instanceof ErrorResponse errorResponse) {
            log.warn("{}: {}", e.getClass().getSimpleName(), e.getMessage());
            return errorPage(ErrorCode.fromHttpStatus(errorResponse.getStatusCode()));
        }

        // 예상하지 못한 예외: 원인 파악을 위해 스택 트레이스를 남기고, 사용자에게는 내부 정보를 숨긴다.
        log.error("처리되지 않은 예외", e);
        return errorPage(ErrorCode.INTERNAL_SERVER_ERROR);
    }

    private ModelAndView errorPage(ErrorCode errorCode) {
        if (isApiRequest()) {
            // 컨트롤러가 없는 /api 주소(404 등)는 ApiExceptionHandler(@RestController 전용)가 받지 못하고 여기로 온다.
            // API 클라이언트에게 HTML을 주지 않도록 같은 JSON 형식으로 응답한다.
            ModelAndView json = new ModelAndView((View) (model, request, response) ->
                    ApiErrorWriter.write(response, errorCode));
            json.setStatus(errorCode.getStatus());
            return json;
        }
        ModelAndView mav = new ModelAndView("error");
        mav.setStatus(errorCode.getStatus()); // 화면만 에러가 아니라 HTTP 응답 코드도 맞춘다
        mav.addObject("status", errorCode.getStatus().value());
        // "message"는 Spring Boot 기본 에러 처리도 쓰는 이름이라, 겹치지 않게 전용 이름을 쓴다.
        mav.addObject("errorMessage", errorCode.getMessage());
        return mav;
    }

    private static boolean isApiRequest() {
        return RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes
                && attributes.getRequest().getRequestURI().startsWith("/api/");
    }
}
