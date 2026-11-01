package com.youngmin.board.member;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MemberRepository extends JpaRepository<Member, Long> {

    // 로그인할 때 아이디로 회원 조회 (3단계에서 사용)
    Optional<Member> findByLoginId(String loginId);

    // 회원가입할 때 중복 체크 (2단계에서 사용)
    boolean existsByLoginId(String loginId);

    boolean existsByEmail(String email);
}
