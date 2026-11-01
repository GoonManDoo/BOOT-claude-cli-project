package com.youngmin.board.image;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PostImageRepository extends JpaRepository<PostImage, Long> {

    List<PostImage> findAllByPostIdOrderByIdAsc(Long postId);

    long countByPostId(Long postId);

    /** 이 이름들 중 DB에 등록된 것 (고아 파일 정리용) */
    @Query("select i.storedName from PostImage i where i.storedName in :storedNames")
    Set<String> findExistingStoredNames(@Param("storedNames") Collection<String> storedNames);

    /** 이미지를 보여 줄 때 게시글 상태(삭제/숨김)도 확인해야 하므로 게시글을 같이 가져온다. */
    @Query("select i from PostImage i join fetch i.post where i.id = :id")
    Optional<PostImage> findWithPostById(@Param("id") Long id);
}
