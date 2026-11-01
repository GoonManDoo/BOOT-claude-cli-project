package com.youngmin.board.holiday;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDate;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * 외부 API 실패 종류(4xx, 5xx, 타임아웃, 연결 실패)를 구분하는지 확인한다.
 * 진짜 외부 서버 대신 JDK 내장 HTTP 서버로 원하는 응답을 흉내 낸다 → 인터넷 없이도 항상 같은 결과.
 */
class HolidayClientTest {

    HttpServer server;
    String baseUrl;

    @BeforeEach
    void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/ok/api/v3/PublicHolidays/2026/KR", exchange -> respond(exchange, 200,
                "[{\"date\":\"2026-02-17\",\"localName\":\"설날\",\"name\":\"Lunar New Year\",\"countryCode\":\"KR\"}]"));
        server.createContext("/notfound/", exchange -> respond(exchange, 404, "{}"));
        server.createContext("/error/", exchange -> respond(exchange, 503, "{}"));
        server.createContext("/slow/", exchange -> {
            try {
                Thread.sleep(2000);
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            }
            respond(exchange, 200, "[]");
        });
        server.start();
        baseUrl = "http://127.0.0.1:" + server.getAddress().getPort();
    }

    @AfterEach
    void stopServer() {
        server.stop(0);
    }

    @Test
    void 정상_응답이면_날짜별_공휴일_이름을_돌려준다() {
        assertThat(client(baseUrl + "/ok").fetchHolidays(2026))
                .containsEntry(LocalDate.of(2026, 2, 17), "설날");
    }

    @Test
    void 응답이_4xx면_CLIENT_ERROR() {
        assertType(client(baseUrl + "/notfound"), HolidayApiException.Type.CLIENT_ERROR);
    }

    @Test
    void 응답이_5xx면_SERVER_ERROR() {
        assertType(client(baseUrl + "/error"), HolidayApiException.Type.SERVER_ERROR);
    }

    @Test
    void 응답이_늦으면_READ_TIMEOUT() {
        assertType(client(baseUrl + "/slow"), HolidayApiException.Type.READ_TIMEOUT);
    }

    @Test
    void 서버가_없으면_CONNECTION_FAILED() {
        server.stop(0); // 이 포트에는 이제 아무도 없다
        assertType(client(baseUrl + "/ok"), HolidayApiException.Type.CONNECTION_FAILED);
    }

    private static HolidayClient client(String baseUrl) {
        return new HolidayClient(new HolidayProperties(baseUrl, "KR",
                Duration.ofMillis(500), Duration.ofMillis(300), Duration.ofMinutes(1)));
    }

    private static void assertType(HolidayClient client, HolidayApiException.Type type) {
        assertThatThrownBy(() -> client.fetchHolidays(2026))
                .isInstanceOf(HolidayApiException.class)
                .extracting("type").isEqualTo(type);
    }

    private static void respond(com.sun.net.httpserver.HttpExchange exchange, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }
}
