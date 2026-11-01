package com.youngmin.board.post;

import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * 소프트 삭제 주의: 기본 메서드 findById(), findAll()은 삭제/숨김 글도 가져온다.
 * 사용자 화면에서는 반드시 이름에 Visible/NotDeleted가 들어간 메서드를 쓴다. (조회 조건 누락 방지)
 */
public interface PostRepository extends JpaRepository<Post, Long> {

    /**
     * 목록 검색 조건.
     * - 삭제/숨김 글 제외, 공지는 따로 조회하므로 제외
     * - categoryId가 null이면 전체 카테고리
     * - keyword가 null이면 검색 안 함. type(SearchType 이름)에 따라 하나의 조건만 참이 된다.
     * - 작성자 검색은 members를 조인하지 않고 서브쿼리로 찾는다.
     *   → count 쿼리가 posts만 읽게 되어, 인덱스만으로 개수를 셀 수 있다 (13단계: 11ms → 2.8ms).
     * - keyword는 반드시 escapeLike()를 거친 값이어야 한다. (검색어의 %, _를 글자 그대로 찾도록)
     *   escape 문자로 '\' 대신 '!'를 쓴다. MySQL은 문자열 안의 '\'도 이스케이프로 해석해서 SQL이 깨진다.
     */
    String SEARCH_WHERE = """
            where p.notice = false and p.deleted = false and p.hidden = false
              and (:categoryId is null or p.category.id = :categoryId)
              and (:keyword is null
                   or (:type = 'TITLE' and p.title like concat('%', :keyword, '%') escape '!')
                   or (:type = 'CONTENT' and p.content like concat('%', :keyword, '%') escape '!')
                   or (:type = 'TITLE_CONTENT' and (p.title like concat('%', :keyword, '%') escape '!'
                                                   or p.content like concat('%', :keyword, '%') escape '!'))
                   or (:type = 'AUTHOR' and p.author.id in (
                           select m.id from Member m where m.nickname like concat('%', :keyword, '%') escape '!')))
            """;

    char LIKE_ESCAPE = '!';

    /**
     * LIKE 검색어의 특수 문자를 글자 그대로 찾도록 바꾼다.
     * 그대로 두면 "%"만 검색해도 모든 글이 나오고, "a_b"는 "acb"까지 찾는다.
     */
    static String escapeLike(String keyword) {
        if (keyword == null) {
            return null;
        }
        StringBuilder escaped = new StringBuilder(keyword.length());
        for (char ch : keyword.toCharArray()) {
            if (ch == LIKE_ESCAPE || ch == '%' || ch == '_') {
                escaped.append(LIKE_ESCAPE);
            }
            escaped.append(ch);
        }
        return escaped.toString();
    }

    /**
     * 목록 조회 + 검색 + 페이징.
     * - 작성자/카테고리를 fetch join → 닉네임, 카테고리 이름을 꺼내도 추가 쿼리가 없다 (N+1 방지).
     *   둘 다 게시글 1개당 1개(ManyToOne)라 join해도 행 수가 늘지 않으므로 페이징(limit)과 함께 써도 안전하다.
     * - countQuery: 개수를 셀 때는 fetch도, 조인도 필요 없으므로 따로 적는다.
     *   (fetch join을 count에 그대로 쓰면 Hibernate가 에러를 내고, 조인이 있으면 인덱스만으로 셀 수 없다)
     */
    @Query(value = "select p from Post p join fetch p.author left join fetch p.category " + SEARCH_WHERE,
           countQuery = "select count(p) from Post p " + SEARCH_WHERE)
    Page<Post> search(@Param("type") String type,
                      @Param("keyword") String keyword,
                      @Param("categoryId") Long categoryId,
                      Pageable pageable);

    /** 목록 맨 위에 고정되는 공지. 페이지와 상관없이 항상 같은 결과. */
    @Query("""
            select p from Post p join fetch p.author left join fetch p.category
            where p.notice = true and p.deleted = false and p.hidden = false
            order by p.id desc
            """)
    List<Post> findVisibleNotices();

    /*
     * 메서드 이름 규칙 (소프트 삭제 상태별)
     * - Visible         : 삭제 X, 숨김 X  → 일반 사용자가 보고 다룰 수 있는 글
     * - IncludingHidden : 삭제 X, 숨김 O  → 관리자도 봐야 하는 경우 (숨김 여부는 호출하는 쪽이 판단)
     * - 이름에 아무것도 없는 기본 메서드(findById 등): 삭제된 글까지 전부 → 관리자 기능에서만 쓴다
     */

    /** 상세 조회용: 작성자, 카테고리까지 한 번에. 숨김 글도 가져오므로, 관리자가 아니면 서비스가 404로 바꾼다. */
    @Query("""
            select p from Post p join fetch p.author left join fetch p.category
            where p.id = :id and p.deleted = false
            """)
    Optional<Post> findWithDetailsIncludingHiddenById(@Param("id") Long id);

    /** 수정/삭제/댓글/신고 등 사용자 동작 대상 */
    @Query("select p from Post p where p.id = :id and p.deleted = false and p.hidden = false")
    Optional<Post> findVisibleById(@Param("id") Long id);

    @Query("select count(p) > 0 from Post p where p.id = :id and p.deleted = false and p.hidden = false")
    boolean existsVisibleById(@Param("id") Long id);

    /** 관리자용: 삭제/숨김 글까지 모두 */
    @Query(value = "select p from Post p join fetch p.author left join fetch p.category",
           countQuery = "select count(p) from Post p")
    Page<Post> findAllForAdmin(Pageable pageable);

    /** 관리자용: 신고된 글 */
    @Query(value = "select p from Post p join fetch p.author where p.reportCount > 0",
           countQuery = "select count(p) from Post p where p.reportCount > 0")
    Page<Post> findReported(Pageable pageable);

    /**
     * 조회수 +1. 값을 읽어서 자바에서 더하지 않고 DB가 직접 더한다.
     * UPDATE는 행 잠금을 걸고 실행되므로, 동시에 100번 실행돼도 100이 정확히 오른다.
     */
    @Modifying
    @Query("update Post p set p.viewCount = p.viewCount + 1 where p.id = :id and p.deleted = false and p.hidden = false")
    int increaseViewCount(@Param("id") Long id);

    @Modifying
    @Query("update Post p set p.likeCount = p.likeCount + 1 where p.id = :id")
    int increaseLikeCount(@Param("id") Long id);

    /** 조건부 UPDATE: 0 아래로 내려가지 않게 한다. */
    @Modifying
    @Query("update Post p set p.likeCount = p.likeCount - 1 where p.id = :id and p.likeCount > 0")
    int decreaseLikeCount(@Param("id") Long id);

    @Query("select p.likeCount from Post p where p.id = :id")
    Optional<Long> findLikeCountById(@Param("id") Long id);

    @Modifying
    @Query("update Post p set p.reportCount = p.reportCount + 1 where p.id = :id")
    int increaseReportCount(@Param("id") Long id);

    /**
     * 신고 수가 정확히 threshold가 된 순간에만 숨긴다.
     * 같은 트랜잭션에서 increaseReportCount()가 이 행을 잠가 두었으므로, 동시에 신고가 들어와도
     * reportCount가 threshold인 순간을 보는 요청은 하나뿐이다.
     * (= threshold로 비교하는 이유: 관리자가 복구한 글이 6번째 신고로 바로 다시 숨겨지지 않게)
     */
    @Modifying
    @Query("update Post p set p.hidden = true where p.id = :id and p.reportCount = :threshold")
    int hideIfReportCountReached(@Param("id") Long id, @Param("threshold") int threshold);

    /** 카테고리 기능 이전에 쓴 글을 기본 카테고리로 옮긴다 (CategoryInitializer). */
    @Modifying
    @Query("update Post p set p.category = :category where p.category is null")
    int assignCategoryIfMissing(@Param("category") com.youngmin.board.category.Category category);
}
