package com.youngmin.board.holiday;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.youngmin.board.common.config.CacheConfig;
import java.net.http.HttpClient;
import java.net.http.HttpConnectTimeoutException;
import java.net.http.HttpTimeoutException;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * 공휴일 API(https://date.nager.at) 호출. 실패는 HolidayApiException으로 종류를 나눠 던진다.
 *
 * RestClient: Spring 6.1에서 나온 동기 HTTP 클라이언트. RestTemplate과 같은 엔진(ClientHttpRequestFactory)을 쓰지만
 * restClient.get().uri(...).retrieve().body(...)처럼 이어 쓰는(fluent) 방식이고, 상태 코드별 처리(onStatus)를 붙이기 쉽다.
 * RestTemplate은 유지보수 모드라 새 기능이 추가되지 않는다.
 *
 * 타임아웃을 꼭 정하는 이유: 기본값은 사실상 무제한이라, 상대 서버가 응답하지 않으면
 * 우리 서버의 요청 스레드가 계속 붙잡혀 있다가 결국 게시판 전체가 멈출 수 있다.
 */
@Slf4j
@Component
public class HolidayClient {

    private final RestClient restClient;
    private final String countryCode;

    public HolidayClient(HolidayProperties properties) {
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(properties.connectTimeout()) // 연결 타임아웃
                .build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(properties.readTimeout()); // 응답 타임아웃

        this.restClient = RestClient.builder()
                .baseUrl(properties.baseUrl())
                .requestFactory(requestFactory)
                .build();
        this.countryCode = properties.countryCode();
    }

    /**
     * 연도별 공휴일 {날짜 → 이름}. 공휴일은 1년에 한 번 정해지므로 결과를 캐시한다 (하루 유지).
     * 예외가 나면 캐시에 저장되지 않는다 → 실패한 결과가 하루 동안 남는 일은 없다.
     */
    @Cacheable(cacheNames = CacheConfig.HOLIDAYS, key = "#year")
    public Map<LocalDate, String> fetchHolidays(int year) {
        log.info("공휴일 API 호출: year={}", year);
        try {
            HolidayItem[] items = restClient.get()
                    .uri("/api/v3/PublicHolidays/{year}/{country}", year, countryCode)
                    .retrieve()
                    .onStatus(HttpStatusCode::is4xxClientError, (request, response) -> {
                        throw new HolidayApiException(HolidayApiException.Type.CLIENT_ERROR,
                                "공휴일 API 4xx 응답: " + response.getStatusCode(), null);
                    })
                    .onStatus(HttpStatusCode::is5xxServerError, (request, response) -> {
                        throw new HolidayApiException(HolidayApiException.Type.SERVER_ERROR,
                                "공휴일 API 5xx 응답: " + response.getStatusCode(), null);
                    })
                    .body(HolidayItem[].class);
            if (items == null) {
                throw new HolidayApiException(HolidayApiException.Type.INVALID_RESPONSE, "빈 응답", null);
            }
            return Arrays.stream(items)
                    .filter(item -> item.date() != null)
                    .collect(Collectors.toUnmodifiableMap(HolidayItem::date, HolidayItem::localName, (a, b) -> a + ", " + b));
        } catch (ResourceAccessException e) {
            // 네트워크 단계 실패 (연결 거부, DNS 실패, 타임아웃). 원인 예외로 종류를 나눈다.
            throw new HolidayApiException(classify(e), e.getMessage(), e);
        } catch (RestClientException e) {
            throw new HolidayApiException(HolidayApiException.Type.INVALID_RESPONSE, e.getMessage(), e);
        }
    }

    private static HolidayApiException.Type classify(ResourceAccessException e) {
        for (Throwable cause = e; cause != null; cause = cause.getCause()) {
            if (cause instanceof HttpConnectTimeoutException) {
                return HolidayApiException.Type.CONNECT_TIMEOUT;
            }
            if (cause instanceof HttpTimeoutException) {
                return HolidayApiException.Type.READ_TIMEOUT;
            }
        }
        return HolidayApiException.Type.CONNECTION_FAILED;
    }

    /** API 응답 중 필요한 필드만 받는다. 모르는 필드가 와도 무시. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    record HolidayItem(LocalDate date, String localName) {
    }
}
