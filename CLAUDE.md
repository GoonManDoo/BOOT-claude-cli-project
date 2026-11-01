# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project

A server-rendered bulletin board (게시판) with a small REST API. Package root: `com.youngmin.board`. UI text and commit messages are written in Korean. See `README.md` for features, ERD and design decisions, and `docs/performance.md` for the index/EXPLAIN analysis.

Stack: Spring Boot 4.1.2 (Gradle, Java 17 toolchain), Spring MVC (`spring-boot-starter-webmvc`), Spring Security 7, Thymeleaf (+ `thymeleaf-extras-springsecurity6`), Spring Data JPA (Hibernate 7), Bean Validation, Lombok, Caffeine cache, MySQL, DevTools. Jackson is version 3 (`tools.jackson`), but annotations stay in `com.fasterxml.jackson.annotation`.

## Commands

Run from the repository root (on Windows use `gradlew.bat` or `./gradlew` from Git Bash):

```bash
./gradlew bootRun                     # run the app on http://localhost:8080
./gradlew build                       # compile + test + package
./gradlew test                        # all tests (JUnit 5)
./gradlew test --tests "com.youngmin.board.post.PostFlowTest"                    # single test class
./gradlew test --tests "com.youngmin.board.post.PostServiceTest\$Update"          # nested test class
```

## Runtime requirements

- A local MySQL server at `localhost:3306` with a database named `board`, user `root`.
- `DB_PASSWORD` env var is required for `bootRun` **and** for tests: almost all tests are `@SpringBootTest`/`@DataJpaTest` against the real MySQL (no H2). Pure unit tests: `PostServiceTest`, `PostViewTrackerTest`, `ImageStorageTest`, `UniqueViolationsTest`, `HolidayClientTest` (local fake HTTP server), `HolidayServiceTest`.
- Optional env vars: `UPLOAD_DIR` (default `~/board-uploads`), `HOLIDAY_API_URL` (default `https://date.nager.at`).
- Tests store uploads in `build/test-uploads` (system property set in `build.gradle`).
- There is no UI to grant ADMIN: `UPDATE members SET role='ADMIN' WHERE login_id='...'`, then log in again (role is cached in the session principal).

## Configuration notes (`src/main/resources/application.yaml`)

- `ddl-auto: update` — schema comes from entities; no migration scripts. `update` adds tables/columns/indexes but **never drops or alters** them. Renamed/removed indexes must be dropped by hand (see `docs/performance.md`). New NOT NULL FK columns on existing tables are a problem, which is why `posts.category_id` is nullable in the DB (see `CategoryInitializer`).
- `open-in-view: false` — lazy loading must finish inside `@Transactional` service methods; controllers/templates get DTOs only.
- `show-sql`/`format_sql` on; `spring.thymeleaf.cache: false`.
- `spring.servlet.multipart` 10MB/60MB is the hard server limit; the app rule (5MB × 5 images) is in `board.upload.*` and enforced by `ImageStorage`.
- `board.holiday.*`: base URL, connect/read timeouts, failure backoff.

## Architecture

Feature packages, each with its own Controller → Service → Repository and `dto/`:
`member`, `post`, `category`, `comment`, `like`, `image`, `report`, `admin`, `holiday`, `home`. Shared code lives in `common` (`config`, `entity/BaseTimeEntity`, `exception`, `api`, `dto/PageNavigation`).

Key conventions (keep them when adding features):
- **Two security filter chains** (`common/config/SecurityConfig`): `/api/**` = stateless HTTP Basic, CSRF off, JSON 401/403 (`ApiSecurityHandlers`, written by `ApiErrorWriter`); everything else = form login + session + CSRF. URL rules are order-sensitive (`/posts/new` must come before `GET /posts/*`). CSRF-off is safe only because API POST/PUT are `consumes = application/json` (forms get 415) and the 401 has no `WWW-Authenticate` header (browsers never cache Basic credentials) — keep both.
- **Errors**: services throw `BusinessException(ErrorCode)`. Screen controllers get an error page from `GlobalExceptionHandler`; `@RestController`s get `ApiResponse` JSON from `ApiExceptionHandler` (`@Order(HIGHEST_PRECEDENCE)`). Unmapped `/api/**` paths also get JSON. Both handlers rethrow Spring Security exceptions and map Spring MVC exceptions with `ErrorCode.fromHttpStatus`. Recoverable form errors (validation, duplicate member, image rules) are shown as field errors instead; image-form controllers use `ImageForms.submit(bindingResult, action)`.
- **Authorization** is checked in services (`validateAuthor`), not only by hiding buttons. Controllers take `@AuthenticationPrincipal MemberPrincipal` (has `id`, `nickname`, `role`).
- **Soft delete**: `posts.deleted/hidden`, `comments.deleted`. `JpaRepository.findById/findAll` return deleted rows. Naming in `PostRepository`: `Visible` = not deleted and not hidden (user-facing: `findVisibleById`, `existsVisibleById`, `findVisibleNotices`, `search`); `IncludingHidden` = not deleted, hidden included (`findWithDetailsIncludingHiddenById`, caller decides by role); plain JPA methods = admin only. Likes are hard-deleted (toggle data) to avoid unique-key conflicts.
- **Search**: keywords must go through `PostRepository.escapeLike()` (escape char `!`, not `\` which MySQL string literals also interpret). The count query must stay join-free so it can be answered from `idx_posts_status_views`.
- **Counters are changed only with UPDATE queries** (`view_count + 1`, conditional `like_count ± 1`, `report_count + 1`), never by reading and setting the entity value. Duplicates (likes, reports, members) are blocked by DB unique constraints; catch `DataIntegrityViolationException` outside the transaction (`TransactionTemplate`, because the failed transaction is rollback-only) and treat it as a duplicate **only** if `UniqueViolations.is(e, Entity.UK_...)` — rethrow anything else. Constraint names are constants on the entities.
- **N+1**: ManyToOne associations are LAZY; list/detail queries use `join fetch` for author/category. Never fetch-join a collection together with paging. Comment counts use one `IN + GROUP BY` query per page.
- **Images**: stored as `UUID.ext` under `board.upload.dir`, served only via `GET /post-images/{id}`. File writes/deletes are tied to the transaction outcome (`TransactionSynchronization` in `PostImageService`). Files left behind are removed by `OrphanImageCleaner` (`@Scheduled`, daily 04:00, `board.upload.orphan-cleanup-cron`; skips files younger than 1h).
- **View-count dedupe** (`PostViewTracker`): per-session map, expired entries pruned, capped at 500; takes a `Clock` for tests.
- **Caching**: Caffeine via `CacheConfig` (`categories` 1h, `holidays` 1d). `@Cacheable` only works when called from another bean.
- PRG: every POST that changes data redirects.
- Templates: `templates/fragments/header.html` (top menu, uses `principal.nickname`, so MVC tests rendering pages need a real `MemberPrincipal` via `user(...)`, not `@WithMockUser`; see `support/TestMembers`), `fragments/comment.html`, `error.html` (shared by `GlobalExceptionHandler` and Boot's `/error`).

## Tests

- Integration tests use `@Transactional` (rolled back). Inside one test transaction a service exception only marks the transaction rollback-only, so check `TestTransaction.isFlaggedForRollback()` instead of counting rows. Use `em.flush(); em.clear();` before re-reading to see real DB state (bulk UPDATEs bypass the persistence context).
- Concurrency tests (`PostLikeConcurrencyTest`, `ViewCountConcurrencyTest`) must **not** be `@Transactional`; they clean up in `@AfterEach`.
- Java method names cannot start with a digit (Korean test names like `4xx는_...` fail to compile).
- `http/posts-api.http`: IntelliJ HTTP Client requests for the REST API.

## 프로젝트 목적

- Spring Boot 4.1 + Java 17 + Thymeleaf + Spring Data JPA + MySQL로 만드는 게시판 연습 프로젝트
- 목적: JPA와 Spring Boot를 익히고, AI 코딩 도구로 개발하는 경험 쌓기

## 작업 규칙

- 모든 답변과 설명은 한국어로 할 것
- Controller → Service → Repository 계층을 지킬 것
- Entity를 화면에 직접 넘기지 말고 DTO를 사용할 것
- DB 비밀번호 같은 민감 정보를 코드에 쓰지 말 것 (환경 변수 `DB_PASSWORD` 사용)
- 한 번에 많이 만들지 말고, 기능 하나씩 단계별로 진행할 것
- 코드를 바꾼 뒤에는 무엇을 왜 바꿨는지 한국어로 설명할 것
- git commit과 push는 사용자가 직접 할 테니 하지 말 것
