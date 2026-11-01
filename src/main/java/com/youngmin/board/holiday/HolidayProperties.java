package com.youngmin.board.holiday;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** application.yaml의 board.holiday.* 설정 (주소, 타임아웃을 코드에 쓰지 않는다) */
@ConfigurationProperties("board.holiday")
public record HolidayProperties(String baseUrl, String countryCode, Duration connectTimeout,
                                Duration readTimeout, Duration failureBackoff) {
}
