package com.youngmin.board.category;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 게시판 카테고리 (자유, 질문, 정보 ...). 게시글은 카테고리 하나에 속한다. */
@Entity
@Table(name = "categories",
       uniqueConstraints = @UniqueConstraint(name = "uk_categories_name", columnNames = "name"))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Category {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 20)
    private String name;

    /** 탭에 보이는 순서 (작을수록 앞) */
    @Column(nullable = false)
    private int sortOrder;

    private Category(String name, int sortOrder) {
        this.name = name;
        this.sortOrder = sortOrder;
    }

    public static Category of(String name, int sortOrder) {
        return new Category(name, sortOrder);
    }
}
