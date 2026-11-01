package com.youngmin.board.member.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 회원가입 폼 입력값.
 * Entity(Member)를 화면에 직접 쓰지 않고 이 DTO로 받는다.
 * 폼 바인딩(@ModelAttribute)과 th:field를 쓰려면 기본 생성자와 getter/setter가 필요하다.
 */
@Getter
@Setter
@NoArgsConstructor
public class MemberJoinRequest {

    @NotBlank(message = "아이디를 입력해 주세요.")
    @Size(min = 4, max = 20, message = "아이디는 4~20자로 입력해 주세요.")
    private String loginId;

    @NotBlank(message = "비밀번호를 입력해 주세요.")
    @Size(min = 8, max = 72, message = "비밀번호는 8~72자로 입력해 주세요.") // BCrypt는 72바이트까지만 사용
    private String password;

    @NotBlank(message = "닉네임을 입력해 주세요.")
    @Size(max = 20, message = "닉네임은 20자 이하로 입력해 주세요.")
    private String nickname;

    @NotBlank(message = "이메일을 입력해 주세요.")
    @Email(message = "이메일 형식이 올바르지 않습니다.")
    @Size(max = 100, message = "이메일은 100자 이하로 입력해 주세요.")
    private String email;
}
