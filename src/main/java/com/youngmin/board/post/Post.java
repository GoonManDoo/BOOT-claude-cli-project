package com.youngmin.board.post;

import com.youngmin.board.category.Category;
import com.youngmin.board.common.entity.BaseTimeEntity;
import com.youngmin.board.member.Member;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 게시글.
 *
 * 인덱스 (13단계 성능 점검 결과, 자세한 이유와 측정값은 docs/performance.md)
 * - idx_posts_status_views (notice, deleted, hidden, view_count)
 *     같음(=) 조건 컬럼 3개 다음에 정렬 컬럼 view_count.
 *     조회수순 정렬의 filesort 제거(11.6ms → 1.0ms), 공지 조회(notice = 1, 9592행 스캔 → 5행),
 *     전체 개수를 인덱스만으로 계산(11.2ms → 2.8ms, Using index).
 * - idx_posts_category (category_id): 카테고리 탭. 외래 키에도 인덱스가 필요하다.
 * 최신순 목록은 PK(id) 역순 스캔으로 이미 빠르므로 따로 만들지 않았다.
 */
@Entity
@Table(name = "posts", indexes = {
        @Index(name = "idx_posts_status_views", columnList = "notice, deleted, hidden, view_count"),
        @Index(name = "idx_posts_category", columnList = "category_id")
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Post extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String title;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;

    /**
     * 작성자 (게시글 N : 회원 1).
     * LAZY: 게시글을 조회할 때 회원을 바로 읽지 않고, 실제로 회원 정보를 쓸 때 읽는다.
     *       ManyToOne의 기본값은 EAGER(즉시 로딩)라서 직접 LAZY로 바꿔야 한다.
     */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "member_id", nullable = false, foreignKey = @ForeignKey(name = "fk_posts_member"))
    private Member author;

    /**
     * 카테고리 (게시글 N : 카테고리 1).
     * DB 컬럼은 NULL 허용이다. 이유는 CategoryInitializer 주석 참고. 새 글은 반드시 카테고리를 가진다.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "category_id", foreignKey = @ForeignKey(name = "fk_posts_category"))
    private Category category;

    /** 공지 여부. 공지는 관리자만 쓸 수 있고 목록 맨 위에 고정된다. */
    @Column(nullable = false)
    private boolean notice;

    /** 조회수. 값을 읽어서 더하지 않고, PostRepository.increaseViewCount()의 UPDATE 쿼리로만 올린다. */
    @Column(nullable = false)
    private long viewCount;

    /** 좋아요 수. PostRepository의 조건부 UPDATE 쿼리로만 바꾼다. */
    @Column(nullable = false)
    private long likeCount;

    /** 신고 수. 5가 되는 순간 자동으로 숨김 처리된다. */
    @Column(nullable = false)
    private int reportCount;

    /** 소프트 삭제: 행은 남기고 표시만 한다. 목록/검색/상세에서 보이지 않는다. */
    @Column(nullable = false)
    private boolean deleted;

    private LocalDateTime deletedAt;

    /** 숨김: 신고 누적이나 관리자가 숨긴 글. 관리자는 복구할 수 있다. */
    @Column(nullable = false)
    private boolean hidden;

    private Post(String title, String content, Member author, Category category, boolean notice) {
        this.title = title;
        this.content = content;
        this.author = author;
        this.category = category;
        this.notice = notice;
    }

    public static Post create(String title, String content, Member author, Category category) {
        return new Post(title, content, author, category, false);
    }

    public static Post createNotice(String title, String content, Member author, Category category) {
        return new Post(title, content, author, category, true);
    }

    /**
     * 수정. save()를 다시 부르지 않아도, 트랜잭션이 끝날 때 JPA가 바뀐 값을 찾아 UPDATE 한다 (변경 감지).
     */
    public void update(String title, String content, Category category) {
        this.title = title;
        this.content = content;
        this.category = category;
    }

    public void softDelete() {
        this.deleted = true;
        this.deletedAt = LocalDateTime.now();
    }

    public void hide() {
        this.hidden = true;
    }

    public void restore() {
        this.hidden = false;
    }

    /** 일반 사용자에게 보이는 글인지 */
    public boolean isVisible() {
        return !deleted && !hidden;
    }

    /** 작성자 본인인지 확인. author가 지연 로딩 프록시여도 getId()는 추가 쿼리 없이 바로 꺼낼 수 있다. */
    public boolean isWrittenBy(Long memberId) {
        return memberId != null && author.getId().equals(memberId);
    }
}
