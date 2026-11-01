package com.youngmin.board.common.exception;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.SQLException;
import org.hibernate.exception.ConstraintViolationException;
import org.hibernate.exception.ConstraintViolationException.ConstraintKind;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;

class UniqueViolationsTest {

    @Test
    void 유니크_위반이면_테이블_접두사를_뗀_제약_이름을_돌려준다() {
        DataIntegrityViolationException e = wrap(ConstraintKind.UNIQUE, "members.uk_members_email");

        assertThat(UniqueViolations.constraintName(e)).hasValue("uk_members_email");
        assertThat(UniqueViolations.is(e, "uk_members_email")).isTrue();
        assertThat(UniqueViolations.is(e, "uk_members_login_id")).isFalse();
    }

    @Test
    void 외래_키_위반은_중복으로_보지_않는다() {
        DataIntegrityViolationException e = wrap(ConstraintKind.FOREIGN_KEY, "fk_post_likes_member");

        assertThat(UniqueViolations.constraintName(e)).isEmpty();
        assertThat(UniqueViolations.is(e, "fk_post_likes_member")).isFalse();
    }

    @Test
    void HTTP_상태를_ErrorCode로_바꾼다() {
        assertThat(ErrorCode.fromHttpStatus(HttpStatus.NOT_FOUND)).isEqualTo(ErrorCode.NOT_FOUND);
        assertThat(ErrorCode.fromHttpStatus(HttpStatus.METHOD_NOT_ALLOWED)).isEqualTo(ErrorCode.METHOD_NOT_ALLOWED);
        assertThat(ErrorCode.fromHttpStatus(HttpStatus.UNSUPPORTED_MEDIA_TYPE)).isEqualTo(ErrorCode.UNSUPPORTED_MEDIA_TYPE);
        assertThat(ErrorCode.fromHttpStatus(HttpStatus.CONTENT_TOO_LARGE)).isEqualTo(ErrorCode.CONTENT_TOO_LARGE);
        assertThat(ErrorCode.fromHttpStatus(HttpStatus.BAD_REQUEST)).isEqualTo(ErrorCode.INVALID_INPUT);
        assertThat(ErrorCode.fromHttpStatus(HttpStatus.SERVICE_UNAVAILABLE)).isEqualTo(ErrorCode.INTERNAL_SERVER_ERROR);
    }

    private static DataIntegrityViolationException wrap(ConstraintKind kind, String constraintName) {
        ConstraintViolationException hibernate = new ConstraintViolationException(
                "violation", new SQLException("db message"), "insert ...", kind, constraintName);
        return new DataIntegrityViolationException("could not execute statement", hibernate);
    }
}
