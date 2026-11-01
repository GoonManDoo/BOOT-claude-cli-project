package com.youngmin.board.common.api;

import com.youngmin.board.common.exception.ErrorCode;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;

/**
 * API에서 Security가 직접 막는 경우(컨트롤러까지 가지 않음)에도 같은 JSON 형식으로 응답한다.
 * - 401: 로그인(인증) 정보가 없거나 틀림
 * - 403: 로그인은 했지만 권한이 없음
 *
 * 401 응답에 WWW-Authenticate 헤더를 일부러 넣지 않는다.
 * 이 헤더가 있으면 브라우저가 로그인 창을 띄우고, 입력한 Basic 인증 정보를 기억했다가
 * 다른 사이트에서 보낸 요청에도 자동으로 붙인다(CSRF 위험). 헤더가 없으면 브라우저는 인증 정보를 기억하지 않는다.
 */
public final class ApiSecurityHandlers {

    private ApiSecurityHandlers() {
    }

    public static AuthenticationEntryPoint authenticationEntryPoint() {
        return (request, response, e) -> ApiErrorWriter.write(response, ErrorCode.UNAUTHORIZED);
    }

    public static AccessDeniedHandler accessDeniedHandler() {
        return (request, response, e) -> ApiErrorWriter.write(response, ErrorCode.ACCESS_DENIED);
    }
}
