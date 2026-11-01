package com.youngmin.board.like;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PostLikeRepository extends JpaRepository<PostLike, Long> {

    boolean existsByMemberIdAndPostId(Long memberId, Long postId);

    long countByPostId(Long postId);

    /**
     * 좋아요 취소. 지운 행 수를 돌려준다 (1이면 실제로 지움, 0이면 원래 없었음).
     * 동시에 두 번 취소해도 행 잠금 때문에 한 요청만 1을 받는다 → 그 요청만 좋아요 수를 줄인다.
     */
    @Modifying
    @Query("delete from PostLike l where l.member.id = :memberId and l.post.id = :postId")
    int deleteByMemberIdAndPostId(@Param("memberId") Long memberId, @Param("postId") Long postId);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("delete from PostLike l where l.post.id = :postId")
    int deleteAllByPostId(@Param("postId") Long postId);
}
