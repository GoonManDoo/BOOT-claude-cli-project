package com.youngmin.board.post;

import jakarta.servlet.http.HttpSession;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.util.WebUtils;

/**
 * 같은 사람이 새로고침을 반복해도 일정 시간 동안은 조회수를 한 번만 올리기 위한 기록.
 *
 * "같은 사람"은 세션(JSESSIONID 쿠키)으로 구분한다. 로그인하지 않은 사용자도 세션이 있으므로 똑같이 동작한다.
 * 세션에 { 게시글 id → 마지막으로 조회수를 올린 시각 }을 저장한다.
 *
 * 세션 메모리가 끝없이 늘지 않도록
 * - 30분이 지난 기록은 지운다 (다시 세어도 되는 기록이라 남겨 둘 이유가 없다)
 * - 30분 안에 아주 많은 글을 본 경우(크롤러 등)를 대비해 세션당 최대 MAX_ENTRIES개까지만 둔다
 *
 * 한계: 쿠키를 지우거나 브라우저를 바꾸면 새 사람으로 본다.
 *       서버가 여러 대면 세션을 공유해야 한다 (Spring Session + Redis 등).
 */
@Component
public class PostViewTracker {

    static final Duration VIEW_WINDOW = Duration.ofMinutes(30);
    static final int MAX_ENTRIES = 500;
    private static final String SESSION_KEY = "VIEWED_POSTS";

    private final Clock clock;

    @Autowired
    public PostViewTracker() {
        this(Clock.systemDefaultZone());
    }

    /** 테스트에서 시간을 마음대로 흘려보내기 위한 생성자 */
    PostViewTracker(Clock clock) {
        this.clock = clock;
    }

    /** 조회수를 올려야 하면 true. 기록도 함께 남긴다. */
    public boolean shouldCount(HttpSession session, Long postId) {
        Map<Long, Instant> viewed = getViewedPosts(session);
        Instant now = clock.instant();
        removeExpired(viewed, now);

        boolean[] counted = {false};
        // compute는 같은 key에 대해 원자적으로 실행된다 → 같은 세션에서 새로고침 요청이 동시에 와도 한 번만 true
        viewed.compute(postId, (id, lastViewedAt) -> {
            if (lastViewedAt != null && !isExpired(lastViewedAt, now)) {
                return lastViewedAt; // 아직 30분이 안 지났다
            }
            counted[0] = true;
            return now;
        });
        return counted[0];
    }

    /** 만료된 기록을 지우고, 그래도 너무 많으면 가장 오래된 기록부터 지운다. */
    private static void removeExpired(Map<Long, Instant> viewed, Instant now) {
        viewed.values().removeIf(viewedAt -> isExpired(viewedAt, now));
        while (viewed.size() >= MAX_ENTRIES) {
            viewed.entrySet().stream()
                    .min(Map.Entry.comparingByValue())
                    .ifPresent(oldest -> viewed.remove(oldest.getKey(), oldest.getValue()));
        }
    }

    private static boolean isExpired(Instant viewedAt, Instant now) {
        return !viewedAt.plus(VIEW_WINDOW).isAfter(now);
    }

    @SuppressWarnings("unchecked")
    private Map<Long, Instant> getViewedPosts(HttpSession session) {
        // 같은 세션의 요청 두 개가 동시에 처음 들어와도 Map이 하나만 만들어지게 세션 단위로 잠근다
        synchronized (WebUtils.getSessionMutex(session)) {
            Map<Long, Instant> viewed = (Map<Long, Instant>) session.getAttribute(SESSION_KEY);
            if (viewed == null) {
                viewed = new ConcurrentHashMap<>();
                session.setAttribute(SESSION_KEY, viewed);
            }
            return viewed;
        }
    }

    /** 테스트용: 세션에 남아 있는 기록 수 */
    @SuppressWarnings("unchecked")
    int recordCount(HttpSession session) {
        Map<Long, Instant> viewed = (Map<Long, Instant>) session.getAttribute(SESSION_KEY);
        return viewed == null ? 0 : viewed.size();
    }
}
