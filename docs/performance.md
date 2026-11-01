# 성능 점검 (13단계)

- 환경: MySQL 8.4, 게시글 10,000개 / 댓글 50,000개 / 회원 100명
- 데이터: [`docs/perf/seed-data.sql`](perf/seed-data.sql) → `board_perf` 스키마에 구조만 복사해서 넣음 (개발 DB `board`는 그대로)
- 쿼리: [`docs/perf/explain-queries.sql`](perf/explain-queries.sql) (Hibernate가 실제로 보내는 쿼리와 같은 조건)
- 시간: 같은 쿼리를 20번 실행한 평균 (워밍업 3번 제외). 데이터가 1만 건이라 절대값은 작다. **비율**과 **실행계획(type, rows)**을 본다.

## 1. 실행계획 읽는 법

| 항목 | 의미 |
|---|---|
| `type` | `ALL` 테이블 전체 읽기 → `index` 인덱스 전체 읽기 → `range`/`ref` 인덱스로 필요한 범위만 → `const` PK로 1건 (오른쪽일수록 좋다) |
| `key` | 실제로 쓴 인덱스 |
| `rows` | 읽을 것으로 예상한 행 수 |
| `Extra: Using filesort` | 인덱스 순서를 못 써서 따로 정렬함 (행이 많으면 느림) |
| `Extra: Using index` | 테이블 행을 읽지 않고 인덱스만으로 답함 (커버링 인덱스, 빠름) |

## 2. 인덱스 추가 전

인덱스는 PK와 외래 키용(`member_id`, `category_id`, `comments.post_id`)만 있다.

| 쿼리 | type / key | rows | Extra | 시간 |
|---|---|---|---|---|
| Q1 목록(최신순) | index / PRIMARY | 10 | Backward index scan | 1.0ms |
| **Q2 전체 개수** | **ALL** | 9,592 | Using where | **11.2ms** |
| Q3 카테고리 탭 | ref / fk_posts_category | 3,334 | Backward index scan | 0.9ms |
| Q4 카테고리 개수 | ref / fk_posts_category | 3,334 | Using where | 7.9ms |
| **Q5 조회수순** | **ALL** | 9,592 | **Using filesort** | **11.6ms** |
| **Q6 공지** | index / PRIMARY | **9,592** | Backward index scan | **4.8ms** |
| Q7 제목 LIKE 검색 | index / PRIMARY | 10 | Using where | 0.5ms |
| Q8 제목 LIKE 개수 | ALL | 9,592 | Using where | 7.3ms |
| Q9 상세 | const / PRIMARY | 1 | | 0.5ms |
| Q10 댓글 목록 | ref / fk_comments_post | 4 | | 0.5ms |
| Q11 댓글 수 (IN) | range / fk_comments_post | 51 | | 0.6ms |
| Q12 OFFSET 9000 | index / PRIMARY | 9,010 | Backward index scan | 3.9ms |

문제가 되는 곳:
- **Q5 조회수순**: 조회수 순서로 된 인덱스가 없다. 그래서 전체를 읽고 정렬(filesort)한다.
- **Q6 공지**: 공지는 5개뿐인데 `notice` 인덱스가 없다. 그래서 PK를 거꾸로 거의 끝까지 훑는다. 모든 목록 화면에서 실행되는 쿼리라 비용이 크다.
- **Q2 전체 개수**: 페이지마다 실행되는데 테이블 전체를 읽는다.

## 3. 처음 시도한 인덱스와 실패

먼저 인덱스 3개를 만들었다.

| 인덱스 | 컬럼 |
|---|---|
| `idx_posts_status_id` | (notice, deleted, hidden, id) |
| `idx_posts_category_status_id` | (category_id, notice, deleted, hidden, id) |
| `idx_posts_status_views` | (notice, deleted, hidden, view_count, id) |

| 쿼리 | 전 | 후 | 결과 |
|---|---|---|---|
| Q5 조회수순 | 11.6ms | 1.0ms | 좋아짐 (filesort 사라짐) |
| Q6 공지 | 4.8ms | 0.5ms | 좋아짐 (rows 9,592 → 5) |
| **Q2 전체 개수** | 11.2ms | **21.2ms** | **나빠짐** |
| **Q8 LIKE 개수** | 7.3ms | **16.6ms** | **나빠짐** |
| **Q12 OFFSET 9000** | 3.9ms | **21.2ms** | **나빠짐** |
| Q3, Q4 카테고리 | 0.9 / 7.9ms | 1.0 / 7.7ms | 변화 없음 |

원인은 세 가지였다.

1. **카운트 쿼리의 불필요한 조인**
   - count 쿼리가 `members`를 조인한다. 작성자 검색 때문에 붙여 둔 조인이다.
   - 조인하려면 행마다 `member_id`가 필요한데, 이 값은 인덱스에 없다.
   - 그래서 MySQL이 인덱스로 찾은 9천여 행마다 테이블을 다시 읽었다. 무작위 읽기라 전체를 차례로 읽는 것보다 느렸다.
2. **선택도가 낮은 조건**
   - `notice=0 AND deleted=0 AND hidden=0`은 전체 행의 약 95%에 해당한다.
   - 이렇게 대부분의 행이 맞는 조건은 인덱스로 찾아도 이득이 없다. 오히려 "인덱스로 찾기 → 테이블 다시 읽기"가 테이블 전체를 차례로 읽는 것보다 느리다.
   - 옵티마이저는 값이 2개뿐인 컬럼이라 절반(4,785행)이 맞을 거라고 추정하고 이 인덱스를 골랐다.
3. **효과 없는 인덱스**
   - 카테고리 복합 인덱스는 외래 키 인덱스(`category_id`)와 결과가 같았다. 카테고리 하나가 전체의 1/3이라 뒤의 상태 컬럼이 거르는 행이 거의 없기 때문이다.
   - `idx_posts_status_id`는 `idx_posts_status_views`가 같은 일을 해서 필요 없었다.

## 4. 최종: 인덱스 1개 + 쿼리 수정

**인덱스**
- `idx_posts_status_views (notice, deleted, hidden, view_count)`
- `idx_posts_category (category_id)`: 외래 키용

**쿼리 수정**
- count 쿼리에서 `members` 조인을 뺐다.
- 작성자 검색은 조인 대신 서브쿼리로 바꿨다: `p.author.id in (select m.id from Member m where m.nickname like ...)`

| 쿼리 | 전 | 최종 | 실행계획 변화 |
|---|---|---|---|
| Q2 전체 개수 | 11.2ms | **2.8ms** | ALL → **ref, Using index** |
| Q4 카테고리 개수 | 7.9ms | **5.2ms** | 조인 제거 |
| Q5 조회수순 | 11.6ms | **1.0ms** | ALL + filesort → **ref, Backward index scan** |
| Q6 공지 | 4.8ms | **0.5ms** | rows 9,592 → **5** |
| Q1, Q3, Q7, Q9~Q11 | 0.5~1.0ms | 같음 | 원래 빠름 |
| Q8 LIKE 개수 | 7.3ms | 19.3ms | ⚠️ 아래 6절 |
| Q12 OFFSET 9000 | 3.9ms | 21.1ms | ⚠️ 아래 5절 |

### 복합 인덱스 컬럼 순서를 이렇게 정한 이유

1. **같음(=) 조건 컬럼을 앞에 둔다.** `notice`, `deleted`, `hidden`은 항상 `=`로 비교한다.
   - 같음 조건이 앞에 모두 걸려야, 그다음 컬럼 순서대로 정렬된 구간을 바로 쓸 수 있다.
   - `notice`를 맨 앞에 둔 이유는 공지 조회(`notice = 1`)를 이 인덱스 하나로 빠르게 찾기 위해서다. 공지는 5행뿐이다.
2. **정렬 컬럼을 마지막에 둔다.** `view_count`가 마지막이라, 같음 조건이 같은 구간 안에서는 이미 조회수 순서로 정렬돼 있다.
   - MySQL이 인덱스를 거꾸로 읽으면(Backward index scan) `ORDER BY view_count DESC`가 정렬 없이 끝난다.
   - 순서를 `(view_count, notice, ...)`로 바꾸면 이 구간을 쓸 수 없다.
3. **id는 넣지 않았다.** InnoDB의 보조 인덱스에는 PK(`id`)가 자동으로 맨 끝에 붙는다.
   - 그래서 `(notice, deleted, hidden, view_count)`는 실제로는 `(..., view_count, id)`다.
   - 같은 조회수일 때 `id DESC`로 정렬하는 것도 인덱스가 해결한다.
4. **개수(count)도 이 인덱스만으로 셀 수 있다.** 조건 컬럼이 인덱스에 모두 들어 있어서 테이블을 읽지 않는다(Using index).

### 개발 DB에 반영하는 SQL

`ddl-auto: update`는 새 인덱스를 만들기만 하고 지우지는 않는다. 처음 시도한 인덱스를 직접 지운다.

```sql
DROP INDEX idx_posts_status_id ON posts;
DROP INDEX idx_posts_category_status_id ON posts;
DROP INDEX idx_posts_status_views ON posts;
CREATE INDEX idx_posts_status_views ON posts (notice, deleted, hidden, view_count);
-- idx_posts_category(category_id)는 앱을 실행하면 Hibernate가 만든다
```

## 5. 깊은 페이지(OFFSET)의 한계

`LIMIT 9000, 10`은 앞의 9,000행을 읽고 버린 뒤 10행을 돌려준다. 인덱스가 있어도 **읽는 양은 페이지 번호에 비례한다.**

| 방식 | 쿼리 | 시간 |
|---|---|---|
| OFFSET | `ORDER BY id DESC LIMIT 9000, 10` | 21.1ms |
| 커서(키셋) | `WHERE id < (마지막으로 본 id) ORDER BY id DESC LIMIT 10` | **0.55ms** |

대안은 두 가지다.
- **커서 방식(no-offset)**: "다음" 버튼에 마지막 글 id를 넘긴다. 페이지가 아무리 뒤여도 항상 10행만 읽는다. 대신 "37페이지로 바로 가기"는 할 수 없다. 무한 스크롤에 맞다.
- **페이지 수 제한**: 실제 사용자는 뒤 페이지를 거의 보지 않는다. 100페이지까지만 보여 주고, 나머지는 검색으로 찾게 한다.

## 6. LIKE '%검색어%'가 인덱스를 못 타는 이유와 대안

B-Tree 인덱스는 **앞에서부터** 정렬돼 있다. 국어사전에서 "스프링으로 **시작하는** 단어"는 바로 찾지만, "가운데에 스프링이 **들어 있는** 단어"는 처음부터 끝까지 넘겨 봐야 하는 것과 같다.
- `title LIKE '스프링%'`: 앞부분이 정해져 있다 → 인덱스 range 검색 가능
- `title LIKE '%스프링%'`: 앞부분을 모른다 → 모든 행을 읽어야 한다 (지금 구조에서 데이터 양에 비례해 느려진다)

| 대안 | 설명 | 장단점 |
|---|---|---|
| **FULLTEXT + ngram 파서** | `ALTER TABLE posts ADD FULLTEXT INDEX ft_posts_title (title) WITH PARSER ngram;` 검색은 `MATCH(title) AGAINST('스프링' IN BOOLEAN MODE)` | MySQL만으로 가능. 한국어는 ngram(2글자 단위)으로 쪼갠다. 단점: 인덱스가 크고, 흔한 단어는 오히려 느릴 수 있다 |
| **앞부분 일치로 바꾸기** | `LIKE '스프링%'` | 인덱스를 쓴다. 대신 "가운데 포함" 검색이 안 된다 |
| **검색 엔진** | Elasticsearch, OpenSearch | 형태소 분석, 오타 교정, 정확도순 정렬. 대신 운영할 시스템이 하나 늘어난다 |

실제로 FULLTEXT(ngram)도 측정해 봤다.
- 실행계획은 `type=fulltext`로 바뀌었다.
- 그런데 '스프링'은 전체 글의 약 20%(1,919개)에 들어 있는 흔한 단어라, 개수 쿼리가 17.8ms로 LIKE(7.3ms)보다 느렸다.
- FULLTEXT는 **데이터가 많고 검색어가 드물수록** 효과가 크다. 1만 건에서는 LIKE도 충분하다.
- 그래서 지금은 적용하지 않았다. 게시글이 수십만 건이 되면 FULLTEXT나 검색 엔진으로 옮긴다.

## 7. N+1 점검 결과 (전체 코드)

| 화면/기능 | 쿼리 수 | 확인 내용 |
|---|---|---|
| 목록 | 4 (고정) | 목록+작성자+카테고리 fetch join / count / 공지 fetch join / 게시글과 공지의 댓글 수를 한 번에 IN |
| 상세 | 4~5 (고정) | 글+작성자+카테고리 fetch join / 이미지 / 조회수 UPDATE / 댓글+작성자 fetch join / (로그인 시) 좋아요 여부 |
| 댓글 대댓글 묶기 | 0 추가 | `comment.getParent().getId()`는 프록시에서 id만 꺼내서 쿼리가 없다 |
| 관리자 게시글/신고 목록 | 2 | 작성자 fetch join + count |
| 관리자 회원 목록 | 2 | 연관관계 없음 |
| 카테고리 탭/글쓰기 | 0 | Caffeine 캐시 |
| 공휴일 | 0 (하루 1번) | Caffeine 캐시 |

남은 N+1은 없다. (14단계 리뷰에서 일반 글과 공지의 댓글 수 쿼리 2개를 1개로 합쳤다: 5회 → 4회)

## 8. 직접 확인하기 (DBeaver)

1. `docs/perf/seed-data.sql` 전체 실행 (Alt+X)
2. `docs/perf/explain-queries.sql`의 쿼리를 하나씩 실행해서 `type`, `key`, `rows`, `Extra`를 본다.
3. 전후 비교를 해 보려면 이렇게 한다.
   - 인덱스 지우기: `DROP INDEX idx_posts_status_views ON posts;`
   - Q2, Q5, Q6을 실행한다 → `type=ALL`, `Using filesort`가 보인다.
   - 다시 만들기: `CREATE INDEX idx_posts_status_views ON posts (notice, deleted, hidden, view_count);`
   - 다시 실행한다 → `ref`가 보인다.
4. 다 쓰면 `DROP DATABASE board_perf;`
