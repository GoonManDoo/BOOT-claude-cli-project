package com.youngmin.board.image;

import com.youngmin.board.common.entity.BaseTimeEntity;
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

/** 게시글 첨부 이미지. 실제 파일은 디스크에, 파일 정보는 DB에 저장한다. */
@Entity
@Table(name = "post_images",
       uniqueConstraints = @UniqueConstraint(name = "uk_post_images_stored_name", columnNames = "stored_name"))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PostImage extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "post_id", nullable = false, foreignKey = @ForeignKey(name = "fk_post_images_post"))
    private Post post;

    /** 사용자가 올린 원래 파일 이름 (화면 표시용) */
    @Column(nullable = false, length = 255)
    private String originalName;

    /** 디스크에 저장한 이름 (UUID.확장자) */
    @Column(name = "stored_name", nullable = false, length = 100)
    private String storedName;

    @Column(nullable = false, length = 50)
    private String contentType;

    @Column(nullable = false)
    private long size;

    private PostImage(Post post, ImageStorage.StoredImage stored) {
        this.post = post;
        this.originalName = stored.originalName();
        this.storedName = stored.storedName();
        this.contentType = stored.contentType();
        this.size = stored.size();
    }

    public static PostImage of(Post post, ImageStorage.StoredImage stored) {
        return new PostImage(post, stored);
    }
}
