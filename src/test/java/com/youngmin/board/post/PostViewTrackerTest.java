package com.youngmin.board.post;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpSession;

/** 조회수 중복 방지 기록 단위 테스트. 시계를 직접 움직여서 30분 경과를 흉내 낸다. */
class PostViewTrackerTest {

    MutableClock clock = new MutableClock(Instant.parse("2026-11-01T00:00:00Z"));
    PostViewTracker tracker = new PostViewTracker(clock);
    MockHttpSession session = new MockHttpSession();

    @Test
    void 삼십분_안에는_한_번만_세고_지나면_다시_센다() {
        assertThat(tracker.shouldCount(session, 1L)).isTrue();
        clock.advance(Duration.ofMinutes(29));
        assertThat(tracker.shouldCount(session, 1L)).isFalse();
        clock.advance(Duration.ofMinutes(1));
        assertThat(tracker.shouldCount(session, 1L)).isTrue();
    }

    @Test
    void 삼십분이_지난_기록은_세션에서_지워진다() {
        for (long postId = 1; postId <= 10; postId++) {
            tracker.shouldCount(session, postId);
        }
        assertThat(tracker.recordCount(session)).isEqualTo(10);

        clock.advance(Duration.ofMinutes(31));
        tracker.shouldCount(session, 99L);

        assertThat(tracker.recordCount(session)).isEqualTo(1); // 99번 기록만 남는다
    }

    @Test
    void 기록은_세션당_최대_개수를_넘지_않는다() {
        for (long postId = 1; postId <= PostViewTracker.MAX_ENTRIES + 100; postId++) {
            tracker.shouldCount(session, postId);
            clock.advance(Duration.ofMillis(1));
        }
        assertThat(tracker.recordCount(session)).isEqualTo(PostViewTracker.MAX_ENTRIES);
        // 가장 최근 글은 남아 있어서 다시 세지 않는다
        assertThat(tracker.shouldCount(session, (long) PostViewTracker.MAX_ENTRIES + 100)).isFalse();
    }

    static class MutableClock extends Clock {
        private Instant now;

        MutableClock(Instant now) {
            this.now = now;
        }

        void advance(Duration duration) {
            now = now.plus(duration);
        }

        @Override
        public Instant instant() {
            return now;
        }

        @Override
        public ZoneId getZone() {
            return ZoneId.of("UTC");
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }
    }
}
