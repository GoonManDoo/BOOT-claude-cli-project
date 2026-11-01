package com.youngmin.board.member;

import java.util.List;
import lombok.Getter;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;

/**
 * 로그인한 회원 정보 (세션에 저장되는 객체).
 * Entity(Member)를 세션/화면에 그대로 두지 않고, 필요한 값만 복사해 둔다.
 * - id: 작성자 본인인지 확인할 때 쓴다 (컨트롤러에서 @AuthenticationPrincipal로 꺼냄)
 * - nickname: 화면에서 sec:authentication="principal.nickname" 으로 쓴다
 */
@Getter
public class MemberPrincipal extends User {

    private final Long id;
    private final String nickname;
    private final Role role;

    public MemberPrincipal(Member member) {
        super(member.getLoginId(),
              member.getPassword(),
              List.of(new SimpleGrantedAuthority("ROLE_" + member.getRole().name())));
        this.id = member.getId();
        this.nickname = member.getNickname();
        this.role = member.getRole();
    }

    public boolean isAdmin() {
        return role == Role.ADMIN;
    }
}
