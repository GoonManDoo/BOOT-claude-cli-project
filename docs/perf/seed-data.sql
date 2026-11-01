-- =====================================================================
-- 성능 점검용 테스트 데이터: 게시글 1만 개, 댓글 5만 개 (MySQL 8.4)
--
-- 개발 DB(board)를 건드리지 않도록 board_perf 스키마를 새로 만들고,
-- board의 테이블 "구조만" 복사(CREATE TABLE ... LIKE)한 뒤 데이터를 넣는다.
-- DBeaver에서 이 파일 전체를 실행하면 된다 (Alt+X: 스크립트 실행).
-- 다 쓴 뒤 지우려면: DROP DATABASE board_perf;
-- =====================================================================

CREATE DATABASE IF NOT EXISTS board_perf DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
USE board_perf;

DROP TABLE IF EXISTS comments;
DROP TABLE IF EXISTS posts;
DROP TABLE IF EXISTS categories;
DROP TABLE IF EXISTS members;

-- LIKE는 컬럼과 인덱스를 복사한다 (외래 키는 복사하지 않는다 → 대량 입력이 빠르다)
CREATE TABLE members LIKE board.members;
CREATE TABLE categories LIKE board.categories;
CREATE TABLE posts LIKE board.posts;
CREATE TABLE comments LIKE board.comments;

-- 재귀 CTE로 1~50000 숫자를 만들기 위해 재귀 깊이 제한(기본 1000)을 늘린다
SET SESSION cte_max_recursion_depth = 100000;

INSERT INTO categories (id, name, sort_order) VALUES (1, '자유', 1), (2, '질문', 2), (3, '정보', 3);

-- 회원 100명
INSERT INTO members (login_id, password, nickname, email, role, created_at, updated_at)
WITH RECURSIVE seq(n) AS (SELECT 1 UNION ALL SELECT n + 1 FROM seq WHERE n < 100)
SELECT CONCAT('perf', n), '$2a$10$dummy.hash.for.performance.test.only.xxxxxxxxxxxxxxx',
       CONCAT('회원', n), CONCAT('perf', n, '@example.com'), 'USER', NOW(), NOW()
FROM seq;

-- 게시글 1만 개
-- 제목 앞머리를 5종류로 섞어서 검색 테스트에 쓴다. 공지 5개, 삭제 약 3%, 숨김 약 1%.
INSERT INTO posts (title, content, member_id, category_id, notice, view_count, like_count, report_count,
                   deleted, deleted_at, hidden, created_at, updated_at)
WITH RECURSIVE seq(n) AS (SELECT 1 UNION ALL SELECT n + 1 FROM seq WHERE n < 10000)
SELECT CONCAT(ELT(1 + n % 5, '스프링', 'JPA', 'MySQL', '자바', '질문'), ' 게시글 ', n),
       CONCAT('테스트 본문 ', n, ' ', REPEAT('내용입니다. ', 30)),
       1 + n % 100,
       1 + n % 3,
       n <= 5,
       FLOOR(RAND(n) * 1000),
       FLOOR(RAND(n + 1) * 50),
       0,
       n % 33 = 0,
       IF(n % 33 = 0, NOW(), NULL),
       n % 97 = 0,
       NOW() - INTERVAL (10000 - n) * 50 MINUTE,
       NOW() - INTERVAL (10000 - n) * 50 MINUTE
FROM seq;

-- 댓글 5만 개 (게시글에 무작위로 분배, 5%는 삭제 표시)
INSERT INTO comments (content, member_id, post_id, parent_id, deleted, created_at, updated_at)
WITH RECURSIVE seq(n) AS (SELECT 1 UNION ALL SELECT n + 1 FROM seq WHERE n < 50000)
SELECT CONCAT('댓글 ', n), 1 + n % 100, 1 + FLOOR(RAND(n) * 10000), NULL, n % 20 = 0, NOW(), NOW()
FROM seq;

-- 옵티마이저가 쓸 통계 정보를 새로 계산한다
ANALYZE TABLE posts, comments, members;

SELECT (SELECT COUNT(*) FROM posts) AS posts, (SELECT COUNT(*) FROM comments) AS comments;
