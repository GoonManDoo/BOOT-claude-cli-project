package com.youngmin.board.member;

import com.youngmin.board.common.exception.ErrorCode;
import com.youngmin.board.common.exception.UniqueViolations;
import com.youngmin.board.member.dto.MemberJoinRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MemberService {

    private final MemberRepository memberRepository;
    private final PasswordEncoder passwordEncoder;

    @Transactional
    public Long join(MemberJoinRequest request) {
        // 1차 방어: 미리 조회해서 친절한 메시지를 보여 준다.
        if (memberRepository.existsByLoginId(request.getLoginId())) {
            throw new DuplicateMemberException("loginId", ErrorCode.DUPLICATE_LOGIN_ID);
        }
        if (memberRepository.existsByEmail(request.getEmail())) {
            throw new DuplicateMemberException("email", ErrorCode.DUPLICATE_EMAIL);
        }

        // 비밀번호는 평문이 아니라 BCrypt 해시로 저장한다.
        Member member = Member.createUser(
                request.getLoginId(),
                passwordEncoder.encode(request.getPassword()),
                request.getNickname(),
                request.getEmail());

        try {
            return memberRepository.saveAndFlush(member).getId();
        } catch (DataIntegrityViolationException e) {
            // 2차 방어: 동시에 같은 값으로 가입해서 위 검사를 둘 다 통과한 경우,
            // DB 유니크 제약이 막아 준다. Hibernate가 알려 주는 제약 이름으로 어느 필드인지 구분한다.
            if (UniqueViolations.is(e, Member.UK_EMAIL)) {
                throw new DuplicateMemberException("email", ErrorCode.DUPLICATE_EMAIL);
            }
            if (UniqueViolations.is(e, Member.UK_LOGIN_ID)) {
                throw new DuplicateMemberException("loginId", ErrorCode.DUPLICATE_LOGIN_ID);
            }
            throw e; // 중복이 아닌 다른 DB 오류는 숨기지 않는다
        }
    }
}
