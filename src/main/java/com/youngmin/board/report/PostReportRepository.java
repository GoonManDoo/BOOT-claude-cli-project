package com.youngmin.board.report;

import org.springframework.data.jpa.repository.JpaRepository;

public interface PostReportRepository extends JpaRepository<PostReport, Long> {

    boolean existsByMemberIdAndPostId(Long memberId, Long postId);

    long countByPostId(Long postId);
}
