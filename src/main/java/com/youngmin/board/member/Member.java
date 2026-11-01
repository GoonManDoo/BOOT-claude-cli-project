package com.youngmin.board.member;

import com.youngmin.board.common.entity.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(
        name = "members", // MySQL 8.0.17+에서 MEMBER는 예약어라 복수형을 테이블 이름으로 사용
        uniqueConstraints = {
                @UniqueConstraint(name = Member.UK_LOGIN_ID, columnNames = "login_id"),
                @UniqueConstraint(name = Member.UK_EMAIL, columnNames = "email")
        }
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED) // JPA용 기본 생성자. 외부에서 빈 객체를 만들지 못하게 막는다.
public class Member extends BaseTimeEntity {

    public static final String UK_LOGIN_ID = "uk_members_login_id";
    public static final String UK_EMAIL = "uk_members_email";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY) // MySQL AUTO_INCREMENT
    private Long id;

    @Column(name = "login_id", nullable = false, length = 20)
    private String loginId;

    @Column(nullable = false) // BCrypt 해시(60자)가 들어간다. 기본 길이 255로 충분.
    private String password;

    @Column(nullable = false, length = 20)
    private String nickname;

    @Column(nullable = false, length = 100)
    private String email;

    @Enumerated(EnumType.STRING) // 숫자(ORDINAL) 대신 "USER"/"ADMIN" 문자열로 저장
    @Column(nullable = false, length = 20)
    private Role role;

    private Member(String loginId, String password, String nickname, String email, Role role) {
        this.loginId = loginId;
        this.password = password;
        this.nickname = nickname;
        this.email = email;
        this.role = role;
    }

    /**
     * 일반 회원 생성. 회원가입하면 항상 USER 권한을 받는다.
     * password에는 반드시 암호화된(BCrypt) 값을 넘겨야 한다.
     */
    public static Member createUser(String loginId, String encodedPassword, String nickname, String email) {
        return new Member(loginId, encodedPassword, nickname, email, Role.USER);
    }
}
