package com.youngmin.board.like;

import com.youngmin.board.common.entity.BaseTimeEntity;
import com.youngmin.board.member.Member;
import com.youngmin.board.post.Post;
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
 * 좋아요 (회원 1명이 게시글 1개에 1번).
 * (member_id, post_id) 유니크 제약이 중복 좋아요를 막는 최종 방어선이다.
 * 애플리케이션에서 아무리 조심해도, 동시에 들어온 두 요청은 둘 다 "아직 없음"을 볼 수 있다.
 * DB 제약은 서버가 몇 대든, 요청이 몇 개든 두 번째 INSERT를 반드시 거절한다.
 */
@Entity
@Table(name = "post_likes",
       uniqueConstraints = @UniqueConstraint(name = PostLike.UK_MEMBER_POST, columnNames = {"member_id", "post_id"}))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PostLike extends BaseTimeEntity {

    public static final String UK_MEMBER_POST = "uk_post_likes_member_post";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "member_id", nullable = false, foreignKey = @ForeignKey(name = "fk_post_likes_member"))
    private Member member;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "post_id", nullable = false, foreignKey = @ForeignKey(name = "fk_post_likes_post"))
    private Post post;

    private PostLike(Member member, Post post) {
        this.member = member;
        this.post = post;
    }

    public static PostLike of(Member member, Post post) {
        return new PostLike(member, post);
    }
}
