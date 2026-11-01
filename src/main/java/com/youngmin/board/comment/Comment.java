package com.youngmin.board.comment;

import com.youngmin.board.common.entity.BaseTimeEntity;
import com.youngmin.board.common.exception.BusinessException;
import com.youngmin.board.common.exception.ErrorCode;
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
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 댓글과 대댓글을 한 테이블에 저장한다.
 * - parent == null  → 일반 댓글
 * - parent != null  → 대댓글 (parent는 반드시 일반 댓글. 대댓글의 대댓글은 허용하지 않음)
 *
 * 자기 자신을 참조하는 연관관계(parent)는 ManyToOne 단방향으로만 둔다.
 * 부모 → 자식 목록(OneToMany children)은 일부러 만들지 않았다.
 * 자식 목록이 필요할 때는 게시글의 댓글을 한 번에 조회한 뒤 서비스에서 묶는다 (CommentService.getComments).
 */
@Entity
@Table(name = "comments")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Comment extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 1000)
    private String content;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "member_id", nullable = false, foreignKey = @ForeignKey(name = "fk_comments_member"))
    private Member author;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "post_id", nullable = false, foreignKey = @ForeignKey(name = "fk_comments_post"))
    private Post post;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_id", foreignKey = @ForeignKey(name = "fk_comments_parent"))
    private Comment parent;

    /** 소프트 삭제 표시. 댓글 삭제는 항상 행을 남기고 이 값만 true로 바꾼다 (CommentService.delete). */
    @Column(nullable = false)
    private boolean deleted;

    private Comment(String content, Member author, Post post, Comment parent) {
        this.content = content;
        this.author = author;
        this.post = post;
        this.parent = parent;
    }

    public static Comment create(String content, Member author, Post post, Comment parent) {
        if (parent != null) {
            // 대댓글은 한 단계까지만: 부모가 이미 대댓글이면 거절
            // 부모가 다른 게시글의 댓글이어도 거절 (URL을 조작해 다른 글의 댓글 id를 넣는 경우)
            if (parent.isReply() || !parent.getPost().getId().equals(post.getId())) {
                throw new BusinessException(ErrorCode.INVALID_COMMENT_PARENT);
            }
            if (parent.isDeleted()) {
                throw new BusinessException(ErrorCode.DELETED_COMMENT);
            }
        }
        return new Comment(content, author, post, parent);
    }

    public void update(String content) {
        if (deleted) {
            throw new BusinessException(ErrorCode.DELETED_COMMENT);
        }
        this.content = content; // 변경 감지로 UPDATE
    }

    public void markDeleted() {
        this.deleted = true;
    }

    public boolean isReply() {
        return parent != null;
    }

    public boolean isWrittenBy(Long memberId) {
        return memberId != null && author.getId().equals(memberId);
    }
}
