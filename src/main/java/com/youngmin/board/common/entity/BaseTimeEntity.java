package com.youngmin.board.common.entity;

import jakarta.persistence.Column;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.MappedSuperclass;
import java.time.LocalDateTime;
import lombok.Getter;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

/**
 * 모든 엔티티가 상속하는 공통 시간 필드.
 *
 * - @MappedSuperclass: 이 클래스 자체는 테이블이 되지 않고, 필드만 자식 엔티티의 컬럼으로 들어간다.
 * - @EntityListeners(AuditingEntityListener): 저장/수정 직전에 JPA 이벤트를 받아 날짜를 자동으로 채운다.
 *   (JpaAuditingConfig의 @EnableJpaAuditing이 있어야 동작한다)
 */
@Getter
@MappedSuperclass
@EntityListeners(AuditingEntityListener.class)
public abstract class BaseTimeEntity {

    @CreatedDate
    @Column(updatable = false) // 생성일은 처음 INSERT 때만 쓰고, UPDATE 때는 바꾸지 않는다
    private LocalDateTime createdAt;

    @LastModifiedDate
    private LocalDateTime updatedAt;
}
