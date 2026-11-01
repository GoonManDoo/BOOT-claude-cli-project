package com.youngmin.board.comment;

import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CommentRepository extends JpaRepository<Comment, Long> {

    /**
     * 게시글의 댓글 + 대댓글 전체를 작성자와 함께 한 번에 조회 (쿼리 1번).
     * id 순서로 가져오면 부모 댓글이 항상 자기 대댓글보다 먼저 나온다.
     */
    @Query("select c from Comment c join fetch c.author where c.post.id = :postId order by c.id")
    List<Comment> findAllWithAuthorByPostId(@Param("postId") Long postId);

    /**
     * 목록에 보이는 게시글들의 댓글 수를 한 번에 센다 (IN + GROUP BY, 쿼리 1번).
     * "삭제된 댓글입니다"로 남아 있는 댓글은 세지 않는다.
     */
    @Query("""
            select c.post.id as postId, count(c) as commentCount
            from Comment c
            where c.post.id in :postIds and c.deleted = false
            group by c.post.id
            """)
    List<CommentCount> countByPostIds(@Param("postIds") Collection<Long> postIds);
}
