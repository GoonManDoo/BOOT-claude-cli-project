package com.youngmin.board.member;

/**
 * 회원 권한.
 * Spring Security에 넘길 때는 "ROLE_" 접두사가 붙은 이름(ROLE_USER, ROLE_ADMIN)으로 쓰인다.
 */
public enum Role {
    USER,
    ADMIN
}
