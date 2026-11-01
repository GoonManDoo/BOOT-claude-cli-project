package com.youngmin.board.admin;

import com.youngmin.board.member.Member;
import java.time.LocalDateTime;

/** 관리자 회원 목록 한 줄. 비밀번호 해시는 관리자에게도 보여 주지 않는다. */
public record AdminMemberResponse(Long id, String loginId, String nickname, String email, String role,
                                  LocalDateTime createdAt) {

    static AdminMemberResponse from(Member member) {
        return new AdminMemberResponse(member.getId(), member.getLoginId(), member.getNickname(),
                member.getEmail(), member.getRole().name(), member.getCreatedAt());
    }
}
