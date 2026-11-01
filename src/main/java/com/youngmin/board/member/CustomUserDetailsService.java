package com.youngmin.board.member;

import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 로그인 시 Spring Security가 호출한다.
 * 아이디로 회원을 찾아 UserDetails로 돌려주면,
 * 비밀번호 비교(PasswordEncoder.matches)는 Security가 알아서 한다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CustomUserDetailsService implements UserDetailsService {

    private final MemberRepository memberRepository;

    @Override
    public UserDetails loadUserByUsername(String loginId) throws UsernameNotFoundException {
        return memberRepository.findByLoginId(loginId)
                .map(MemberPrincipal::new)
                .orElseThrow(() -> new UsernameNotFoundException("회원을 찾을 수 없습니다: " + loginId));
    }
}
