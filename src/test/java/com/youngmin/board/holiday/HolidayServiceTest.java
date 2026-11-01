package com.youngmin.board.holiday;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.time.Duration;
import java.time.LocalDate;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** 외부 API가 실패해도 게시판이 정상 동작하는지 (예외 대신 empty, 실패 후 잠시 호출 중단) */
class HolidayServiceTest {

    HolidayProperties properties = new HolidayProperties("http://unused", "KR",
            Duration.ofSeconds(1), Duration.ofSeconds(1), Duration.ofMinutes(1));

    @Test
    void 공휴일이면_이름을_돌려준다() {
        HolidayClient client = mock(HolidayClient.class);
        given(client.fetchHolidays(2026)).willReturn(Map.of(LocalDate.of(2026, 1, 1), "새해"));

        assertThat(new HolidayService(client, properties).findHolidayName(LocalDate.of(2026, 1, 1))).hasValue("새해");
    }

    @Test
    void API가_실패하면_예외_대신_empty이고_1분_동안은_다시_호출하지_않는다() {
        HolidayClient client = mock(HolidayClient.class);
        given(client.fetchHolidays(2026)).willThrow(
                new HolidayApiException(HolidayApiException.Type.READ_TIMEOUT, "느림", null));
        HolidayService service = new HolidayService(client, properties);

        assertThat(service.findHolidayName(LocalDate.of(2026, 1, 1))).isEmpty();
        assertThat(service.findHolidayName(LocalDate.of(2026, 3, 1))).isEmpty();

        verify(client, times(1)).fetchHolidays(2026); // 두 번째는 호출하지 않았다
    }
}
