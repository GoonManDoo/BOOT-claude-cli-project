package com.youngmin.board.holiday;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 게시판이 쓰는 공휴일 기능. 외부 API가 실패해도 예외를 밖으로 던지지 않는다.
 * → 공휴일 표시만 빠지고 게시글 상세 화면은 정상으로 보인다.
 *
 * 실패하면 failureBackoff(1분) 동안은 다시 호출하지 않는다.
 * 외부 서버가 죽었는데 매 요청마다 호출하면, 모든 상세 화면이 타임아웃(최대 수 초)만큼 느려지기 때문이다.
 * (서킷 브레이커를 아주 단순하게 만든 것. 본격적으로는 Resilience4j 같은 라이브러리를 쓴다)
 */
@Slf4j
@Service
public class HolidayService {

    private final HolidayClient holidayClient;
    private final HolidayProperties properties;
    private final Map<Integer, Instant> lastFailureByYear = new ConcurrentHashMap<>();

    public HolidayService(HolidayClient holidayClient, HolidayProperties properties) {
        this.holidayClient = holidayClient;
        this.properties = properties;
    }

    /** 그 날짜가 공휴일이면 이름, 아니면(또는 API 실패 시) empty */
    public Optional<String> findHolidayName(LocalDate date) {
        int year = date.getYear();
        Instant lastFailure = lastFailureByYear.get(year);
        if (lastFailure != null && lastFailure.plus(properties.failureBackoff()).isAfter(Instant.now())) {
            return Optional.empty(); // 최근에 실패했다 → 잠시 호출하지 않는다
        }
        try {
            Map<LocalDate, String> holidays = holidayClient.fetchHolidays(year); // 캐시에 있으면 API 호출 없음
            lastFailureByYear.remove(year);
            return Optional.ofNullable(holidays.get(date));
        } catch (HolidayApiException e) {
            switch (e.getType()) {
                case CLIENT_ERROR, INVALID_RESPONSE -> log.error("공휴일 API 요청/응답 오류 (설정 확인 필요): {}", e.getMessage());
                default -> log.warn("공휴일 API 일시 장애: {}", e.getMessage());
            }
        } catch (RuntimeException e) {
            log.error("공휴일 조회 중 예상하지 못한 오류", e);
        }
        lastFailureByYear.put(year, Instant.now());
        return Optional.empty();
    }
}
