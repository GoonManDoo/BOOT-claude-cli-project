package com.youngmin.board.common.api;

import com.youngmin.board.common.exception.ErrorCode;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.springframework.http.MediaType;

/**
 * 컨트롤러 밖(Security 필터, 컨트롤러가 없는 /api 주소)에서 API 에러 응답을 직접 쓸 때 쓴다.
 * 모양은 ApiResponse.fail(errorCode)와 같다. 메시지가 ErrorCode의 고정 문구라 JSON을 직접 만든다.
 */
public final class ApiErrorWriter {

    private ApiErrorWriter() {
    }

    public static void write(HttpServletResponse response, ErrorCode errorCode) throws IOException {
        response.setStatus(errorCode.getStatus().value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.getWriter().write("""
                {"success":false,"data":null,"error":{"code":"%s","message":"%s","fieldErrors":[]}}"""
                .formatted(errorCode.name(), errorCode.getMessage()));
    }
}
