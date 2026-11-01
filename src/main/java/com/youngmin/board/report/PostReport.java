package com.youngmin.board.report;

import com.youngmin.board.common.entity.BaseTimeEntity;
import com.youngmin.board.member.Member;
import com.youngmin.board.post.Post;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 게시글 신고. 한 회원은 같은 글을 한 번만 신고할 수 있다.
 * 좋아요와 같은 원리로 (member_id, post_id) 유니크 제약이 중복을 막는다.
 * 신고는 지우거나 취소하지 않으므로 소프트 삭제 때문에 유니크 제약이 충돌할 일도 없다.
 */
@Entity
@Table(name = "post_reports",
       uniqueConstraints = @UniqueConstraint(name = PostReport.UK_MEMBER_POST, columnNames = {"member_id", "post_id"}))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PostReport extends BaseTimeEntity {

    public static final String UK_MEMBER_POST = "uk_post_reports_member_post";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "member_id", nullable = false, foreignKey = @ForeignKey(name = "fk_post_reports_member"))
    private Member member;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "post_id", nullable = false, foreignKey = @ForeignKey(name = "fk_post_reports_post"))
    private Post post;

    @Column(length = 200)
    private String reason;

    private PostReport(Member member, Post post, String reason) {
        this.member = member;
        this.post = post;
        this.reason = reason;
    }

    public static PostReport of(Member member, Post post, String reason) {
        return new PostReport(member, post, reason);
    }
}
