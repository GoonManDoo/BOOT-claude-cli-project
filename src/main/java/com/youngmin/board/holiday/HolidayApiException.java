package com.youngmin.board.holiday;

import lombok.Getter;

/** 공휴일 API 호출 실패. 원인을 종류별로 나눠서 로그와 대응을 다르게 할 수 있게 한다. */
@Getter
public class HolidayApiException extends RuntimeException {

    public enum Type {
        /** 4xx: 우리 요청이 잘못됨 (주소, 파라미터, 키). 다시 불러도 같은 결과 → 설정을 고쳐야 한다. */
        CLIENT_ERROR,
        /** 5xx: 상대 서버 문제. 잠시 후 다시 시도할 만하다. */
        SERVER_ERROR,
        /** 연결 자체를 맺지 못함 (서버 다운, DNS 실패, 인터넷 끊김) */
        CONNECTION_FAILED,
        /** 연결 타임아웃: 정해진 시간 안에 연결되지 않음 */
        CONNECT_TIMEOUT,
        /** 응답 타임아웃: 연결은 됐는데 응답이 늦음 */
        READ_TIMEOUT,
        /** 응답 형식이 예상과 다름 */
        INVALID_RESPONSE
    }

    private final Type type;

    public HolidayApiException(Type type, String message, Throwable cause) {
        super("[" + type + "] " + message, cause);
        this.type = type;
    }
}
