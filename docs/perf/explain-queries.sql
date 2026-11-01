-- =====================================================================
-- 실행계획 분석용 쿼리 (board_perf 스키마, seed-data.sql 실행 후)
-- 애플리케이션(Hibernate)이 실제로 보내는 쿼리와 같은 조건/정렬이다. (콘솔 SQL 로그 참고)
-- DBeaver: 쿼리에 커서를 두고 Ctrl+Shift+E(실행계획) 또는 앞에 EXPLAIN을 붙여 실행
--
-- 볼 것: type(ALL=풀스캔, index=인덱스 풀스캔, range/ref=인덱스로 범위 찾기, const=PK 1건),
--        key(쓴 인덱스), rows(읽을 것으로 예상한 행 수), Extra(Using filesort = 따로 정렬함)
-- =====================================================================
USE board_perf;

-- Q1. 목록 (전체, 최신순) - PostRepository.search
EXPLAIN SELECT p.id, p.title, p.view_count, p.like_count, p.created_at, m.nickname, c.name
FROM posts p JOIN members m ON m.id = p.member_id LEFT JOIN categories c ON c.id = p.category_id
WHERE p.notice = 0 AND p.deleted = 0 AND p.hidden = 0
ORDER BY p.id DESC LIMIT 0, 10;

-- Q2. 목록 전체 개수 (countQuery: members 조인 없음 → 인덱스만으로 계산)
EXPLAIN SELECT COUNT(p.id) FROM posts p
WHERE p.notice = 0 AND p.deleted = 0 AND p.hidden = 0;

-- Q3. 카테고리 탭 목록 (질문)
EXPLAIN SELECT p.id, p.title, m.nickname
FROM posts p JOIN members m ON m.id = p.member_id LEFT JOIN categories c ON c.id = p.category_id
WHERE p.notice = 0 AND p.deleted = 0 AND p.hidden = 0 AND p.category_id = 2
ORDER BY p.id DESC LIMIT 0, 10;

-- Q4. 카테고리 탭 개수
EXPLAIN SELECT COUNT(p.id) FROM posts p
WHERE p.notice = 0 AND p.deleted = 0 AND p.hidden = 0 AND p.category_id = 2;

-- Q5. 조회수순 정렬
EXPLAIN SELECT p.id, p.title, m.nickname
FROM posts p JOIN members m ON m.id = p.member_id
WHERE p.notice = 0 AND p.deleted = 0 AND p.hidden = 0
ORDER BY p.view_count DESC, p.id DESC LIMIT 0, 10;

-- Q6. 공지 (모든 목록 화면에서 실행) - PostRepository.findVisibleNotices
EXPLAIN SELECT p.id, p.title, m.nickname
FROM posts p JOIN members m ON m.id = p.member_id
WHERE p.notice = 1 AND p.deleted = 0 AND p.hidden = 0
ORDER BY p.id DESC;

-- Q7. 제목 검색 (LIKE '%스프링%')
EXPLAIN SELECT p.id, p.title, m.nickname
FROM posts p JOIN members m ON m.id = p.member_id
WHERE p.notice = 0 AND p.deleted = 0 AND p.hidden = 0 AND p.title LIKE '%스프링%'
ORDER BY p.id DESC LIMIT 0, 10;

-- Q8. 제목 검색 개수
EXPLAIN SELECT COUNT(p.id) FROM posts p
WHERE p.notice = 0 AND p.deleted = 0 AND p.hidden = 0 AND p.title LIKE '%스프링%';

-- Q9. 상세 - PostRepository.findWithDetailsIncludingHiddenById
EXPLAIN SELECT p.*, m.nickname, c.name
FROM posts p JOIN members m ON m.id = p.member_id LEFT JOIN categories c ON c.id = p.category_id
WHERE p.id = 5000 AND p.deleted = 0;

-- Q10. 상세의 댓글 목록 - CommentRepository.findAllWithAuthorByPostId
EXPLAIN SELECT c.*, m.nickname FROM comments c JOIN members m ON m.id = c.member_id
WHERE c.post_id = 5000 ORDER BY c.id;

-- Q11. 목록의 댓글 수 - CommentRepository.countByPostIds
EXPLAIN SELECT c.post_id, COUNT(c.id) FROM comments c
WHERE c.post_id IN (9990, 9991, 9992, 9993, 9994, 9995, 9996, 9997, 9998, 9999) AND c.deleted = 0
GROUP BY c.post_id;

-- Q12. 아주 뒤 페이지 (OFFSET 9000)
EXPLAIN SELECT p.id, p.title FROM posts p
WHERE p.notice = 0 AND p.deleted = 0 AND p.hidden = 0
ORDER BY p.id DESC LIMIT 9000, 10;
