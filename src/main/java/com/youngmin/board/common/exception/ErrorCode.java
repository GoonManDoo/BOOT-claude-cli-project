package com.youngmin.board.common.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;

/**
 * 에러 종류를 한곳에서 관리한다.
 * 각 에러는 "HTTP 상태 코드 + 사용자에게 보여 줄 메시지"를 가진다.
 * 화면은 메시지를, API는 이름(name())을 에러 코드로 함께 내려 준다.
 */
@Getter
@RequiredArgsConstructor
public enum ErrorCode {

    // 공통
    INVALID_INPUT(HttpStatus.BAD_REQUEST, "잘못된 요청입니다."),
    UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "로그인이 필요합니다."),
    ACCESS_DENIED(HttpStatus.FORBIDDEN, "접근 권한이 없습니다."),
    NOT_AUTHOR(HttpStatus.FORBIDDEN, "작성자만 수정하거나 삭제할 수 있습니다."),
    NOT_FOUND(HttpStatus.NOT_FOUND, "페이지를 찾을 수 없습니다."),
    METHOD_NOT_ALLOWED(HttpStatus.METHOD_NOT_ALLOWED, "지원하지 않는 요청 방식입니다."),
    CONTENT_TOO_LARGE(HttpStatus.CONTENT_TOO_LARGE, "업로드할 수 있는 크기를 넘었습니다."),
    UNSUPPORTED_MEDIA_TYPE(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "지원하지 않는 요청 형식입니다. (JSON으로 보내 주세요)"),
    INTERNAL_SERVER_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "요청을 처리하는 중 오류가 발생했습니다."),

    // 회원
    MEMBER_NOT_FOUND(HttpStatus.NOT_FOUND, "회원을 찾을 수 없습니다."),
    DUPLICATE_LOGIN_ID(HttpStatus.CONFLICT, "이미 사용 중인 아이디입니다"),
    DUPLICATE_EMAIL(HttpStatus.CONFLICT, "이미 사용 중인 이메일입니다"),

    // 카테고리
    CATEGORY_NOT_FOUND(HttpStatus.BAD_REQUEST, "존재하지 않는 카테고리입니다."),

    // 게시글
    POST_NOT_FOUND(HttpStatus.NOT_FOUND, "게시글을 찾을 수 없습니다."),
    NOTICE_ADMIN_ONLY(HttpStatus.FORBIDDEN, "공지사항은 관리자만 쓸 수 있습니다."),

    // 이미지
    TOO_MANY_IMAGES(HttpStatus.BAD_REQUEST, "이미지는 최대 5장까지 올릴 수 있습니다."),
    IMAGE_TOO_LARGE(HttpStatus.BAD_REQUEST, "이미지는 한 장에 5MB까지 올릴 수 있습니다."),
    INVALID_IMAGE(HttpStatus.BAD_REQUEST, "이미지 파일(jpg, png, gif, webp)만 올릴 수 있습니다."),
    IMAGE_NOT_FOUND(HttpStatus.NOT_FOUND, "이미지를 찾을 수 없습니다."),
    IMAGE_STORE_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "이미지를 저장하지 못했습니다."),

    // 댓글
    COMMENT_NOT_FOUND(HttpStatus.NOT_FOUND, "댓글을 찾을 수 없습니다."),
    INVALID_COMMENT_PARENT(HttpStatus.BAD_REQUEST, "이 댓글에는 답글을 달 수 없습니다."),
    DELETED_COMMENT(HttpStatus.BAD_REQUEST, "삭제된 댓글입니다."),

    // 신고
    ALREADY_REPORTED(HttpStatus.CONFLICT, "이미 신고한 게시글입니다."),
    CANNOT_REPORT_OWN_POST(HttpStatus.BAD_REQUEST, "자신의 글은 신고할 수 없습니다.");

    private final HttpStatus status;
    private final String message;

    /**
     * Spring MVC가 던지는 예외(ErrorResponse)의 HTTP 상태를 ErrorCode로 바꾼다.
     * 화면용(GlobalExceptionHandler)과 API용(ApiExceptionHandler) 예외 처리가 같이 쓴다.
     */
    public static ErrorCode fromHttpStatus(HttpStatusCode status) {
        return switch (status.value()) {
            case 404 -> NOT_FOUND;
            case 405 -> METHOD_NOT_ALLOWED;
            case 413 -> CONTENT_TOO_LARGE;
            case 415 -> UNSUPPORTED_MEDIA_TYPE;
            default -> status.is4xxClientError() ? INVALID_INPUT : INTERNAL_SERVER_ERROR;
        };
    }
}
