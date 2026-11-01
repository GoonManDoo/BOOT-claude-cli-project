package com.youngmin.board.common.exception;

import java.util.Locale;
import java.util.Optional;
import org.hibernate.exception.ConstraintViolationException;
import org.hibernate.exception.ConstraintViolationException.ConstraintKind;

/**
 * DB 예외가 "어떤 유니크 제약" 위반인지 알아낸다.
 *
 * DataIntegrityViolationException은 유니크 위반, 외래 키 위반, NOT NULL 위반을 모두 포함한다.
 * 그래서 "이 예외 = 중복"이라고 단정하면 다른 원인(예: 탈퇴한 회원 id로 저장)까지 중복으로 묻힌다.
 * Hibernate가 원인 예외에 담아 주는 제약 종류(UNIQUE)와 제약 이름으로 정확히 구분한다.
 * (DB 에러 메시지 문자열을 검사하면 DB 버전이나 언어 설정에 따라 문구가 바뀔 때 깨진다)
 */
public final class UniqueViolations {

    private UniqueViolations() {
    }

    /** 유니크 제약 위반이면 제약 이름(소문자, "members." 같은 테이블 접두사 제거)을 돌려준다. */
    public static Optional<String> constraintName(Throwable exception) {
        for (Throwable cause = exception; cause != null; cause = cause.getCause()) {
            if (cause instanceof ConstraintViolationException violation && violation.getKind() == ConstraintKind.UNIQUE) {
                String name = violation.getConstraintName();
                if (name == null) {
                    return Optional.of("");
                }
                String normalized = name.substring(name.lastIndexOf('.') + 1).toLowerCase(Locale.ROOT);
                return Optional.of(normalized);
            }
        }
        return Optional.empty();
    }

    /** 지정한 유니크 제약 위반인지 */
    public static boolean is(Throwable exception, String constraintName) {
        return constraintName(exception).filter(constraintName::equalsIgnoreCase).isPresent();
    }
}
