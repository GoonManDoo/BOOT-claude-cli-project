package com.youngmin.board.support;

import com.youngmin.board.member.Member;
import com.youngmin.board.member.MemberRepository;
import com.youngmin.board.member.Role;
import java.util.UUID;
import org.springframework.test.util.ReflectionTestUtils;

/** 테스트용 회원 생성 도우미. 아이디/이메일이 겹치지 않게 무작위 접미사를 붙인다. */
public final class TestMembers {

    private TestMembers() {
    }

    public static Member user(MemberRepository repository, String nickname) {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        return repository.save(Member.createUser("t" + suffix, "hash", nickname, "t" + suffix + "@example.com"));
    }

    /** 관리자 권한은 화면에서 만들 수 없으므로(SQL로 변경) 테스트에서는 필드를 직접 바꾼다. */
    public static Member admin(MemberRepository repository, String nickname) {
        Member member = user(repository, nickname);
        ReflectionTestUtils.setField(member, "role", Role.ADMIN);
        return repository.saveAndFlush(member);
    }
}
