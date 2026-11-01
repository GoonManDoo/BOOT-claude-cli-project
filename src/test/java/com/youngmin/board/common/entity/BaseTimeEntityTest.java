package com.youngmin.board.common.entity;

import static org.assertj.core.api.Assertions.assertThat;

import com.youngmin.board.member.Member;
import com.youngmin.board.member.MemberRepository;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

/**
 * JPA Auditing으로 생성일/수정일이 자동 저장되는지 확인.
 * 실제 MySQL을 사용하므로 DB_PASSWORD 환경 변수가 필요하다. 테스트 후 롤백된다.
 */
@SpringBootTest
@Transactional
class BaseTimeEntityTest {

    @Autowired
    MemberRepository memberRepository;

    @Test
    void 저장하면_생성일과_수정일이_자동으로_채워진다() {
        LocalDateTime before = LocalDateTime.now().minusSeconds(1);

        Member saved = memberRepository.saveAndFlush(
                Member.createUser("audittest", "encoded-password", "감사", "audittest@example.com"));

        assertThat(saved.getCreatedAt()).isAfter(before);
        assertThat(saved.getUpdatedAt()).isAfter(before);
    }
}
