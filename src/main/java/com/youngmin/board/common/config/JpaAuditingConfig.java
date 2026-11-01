package com.youngmin.board.common.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

/**
 * JPA Auditing(생성일/수정일 자동 저장) 활성화.
 *
 * BoardApplication에 직접 붙이지 않고 별도 설정 클래스로 분리한 이유:
 * @WebMvcTest 같은 슬라이스 테스트는 메인 클래스의 애너테이션을 읽지만 JPA 빈은 띄우지 않아서,
 * 메인 클래스에 @EnableJpaAuditing이 있으면 "JPA metamodel must not be empty" 에러가 난다.
 */
@Configuration
@EnableJpaAuditing
public class JpaAuditingConfig {
}
