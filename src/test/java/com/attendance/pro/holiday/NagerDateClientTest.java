package com.attendance.pro.holiday;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.attendance.pro.common.ApiException;
import com.sun.net.httpserver.HttpServer;

/**
 * Nager.Date 클라이언트 재시도·파싱 테스트 — HOL-09.
 * base-url을 로컬 스텁 서버로 돌려 검증한다(실 외부 API를 절대 호출하지 않는다).
 */
class NagerDateClientTest {

    private HttpServer server;
    private final AtomicInteger requestCount = new AtomicInteger();
    /** 응답 시나리오 큐: 각 요청의 상태코드(0=정상 JSON) */
    private volatile int[] statusPlan = {};

    private static final String BODY_JSON = """
            [{"date":"2026-03-01","localName":"삼일절","name":"Independence Movement Day",
              "countryCode":"KR","global":true,"counties":null,
              "fixed":true,"launchYear":null,"types":["Public"]}]
            """;

    @BeforeEach
    void setUp() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/api/v3/PublicHolidays", exchange -> {
            int index = requestCount.getAndIncrement();
            int status = index < statusPlan.length ? statusPlan[index] : 0;
            byte[] body = (status == 0 ? BODY_JSON : "error").getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(status == 0 ? 200 : status, body.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(body);
            }
        });
        server.start();
    }

    @AfterEach
    void tearDown() {
        server.stop(0);
    }

    private RestNagerDateClient client() {
        return new RestNagerDateClient("http://127.0.0.1:" + server.getAddress().getPort());
    }

    @Test
    @DisplayName("HOL-02(파서): 정상 응답은 date/localName/global/types를 그대로 바인딩한다")
    void parsesResponse() {
        statusPlan = new int[]{0};

        List<NagerHoliday> holidays = client().fetch(2026, "KR");

        assertThat(holidays).hasSize(1);
        NagerHoliday holiday = holidays.get(0);
        assertThat(holiday.date()).isEqualTo("2026-03-01");
        assertThat(holiday.localName()).isEqualTo("삼일절");
        assertThat(holiday.countryCode()).isEqualTo("KR");
        assertThat(holiday.global()).isTrue();
        assertThat(holiday.types()).containsExactly("Public");
        assertThat(requestCount.get()).isEqualTo(1);
    }

    @Test
    @DisplayName("HOL-09a: 5xx 1회 재시도 후 성공을 채용한다(총 2회 요청)")
    void retriesOnceOn5xx() {
        statusPlan = new int[]{500, 0};

        List<NagerHoliday> holidays = client().fetch(2026, "KR");

        assertThat(holidays).hasSize(1);
        assertThat(requestCount.get()).isEqualTo(2);
    }

    @Test
    @DisplayName("HOL-09b: 4xx는 재시도 없이 즉시 502 확정(요청 1회)")
    void noRetryOn4xx() {
        statusPlan = new int[]{404};

        assertThatThrownBy(() -> client().fetch(2026, "KR"))
                .isInstanceOf(ApiException.class)
                .satisfies(e -> {
                    ApiException apiException = (ApiException) e;
                    assertThat(apiException.getStatus().value()).isEqualTo(502);
                    assertThat(apiException.getCode()).isEqualTo("HOLIDAY_SYNC_UPSTREAM");
                });
        assertThat(requestCount.get()).isEqualTo(1);
    }

    @Test
    @DisplayName("HOL-09c: 5xx 연속(재시도 소진)은 502(요청 2회)")
    void exhaustedRetryFails() {
        statusPlan = new int[]{500, 503};

        assertThatThrownBy(() -> client().fetch(2026, "KR"))
                .isInstanceOf(ApiException.class)
                .satisfies(e -> assertThat(((ApiException) e).getCode()).isEqualTo("HOLIDAY_SYNC_UPSTREAM"));
        assertThat(requestCount.get()).isEqualTo(2);
    }

}
