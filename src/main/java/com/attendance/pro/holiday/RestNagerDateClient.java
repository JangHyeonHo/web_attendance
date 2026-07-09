package com.attendance.pro.holiday;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatus;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import com.attendance.pro.common.ApiException;

/**
 * Nager.Date RestClient 구현.
 * <ul>
 *   <li>타임아웃: connect 3초 / read 5초 — 무한 대기로 요청 스레드를 잡아두지 않는다</li>
 *   <li>재시도: IO 예외·5xx에 한해 1회(총 2회, 간격 500ms 고정). GET 멱등이라 안전.
 *       4xx는 즉시 실패 확정(404 = 해당 연도/국가 데이터 없음)</li>
 *   <li>프록시: JVM 표준 시스템 프로퍼티(https.proxyHost/Port)만 존중 — 전용 설정 키 미도입</li>
 * </ul>
 */
@Component
public class RestNagerDateClient implements NagerDateClient {

    private static final Logger log = LoggerFactory.getLogger(RestNagerDateClient.class);
    private static final long RETRY_DELAY_MILLIS = 500L;

    private final RestClient restClient;

    public RestNagerDateClient(@Value("${app.holiday.nager.base-url:https://date.nager.at}") String baseUrl) {
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(3))
                .build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(Duration.ofSeconds(5));
        this.restClient = RestClient.builder()
                .baseUrl(baseUrl)
                .requestFactory(requestFactory)
                .build();
    }

    @Override
    public List<NagerHoliday> fetch(int year, String countryCode) {
        RuntimeException lastFailure = null;
        for (int attempt = 1; attempt <= 2; attempt++) {
            try {
                List<NagerHoliday> body = restClient.get()
                        .uri("/api/v3/PublicHolidays/{year}/{country}", year, countryCode)
                        .retrieve()
                        .body(new ParameterizedTypeReference<>() {
                        });
                return body == null ? List.of() : body;
            } catch (RestClientResponseException e) {
                if (!e.getStatusCode().is5xxServerError()) {
                    //4xx는 재시도하지 않는다 — 즉시 실패 확정
                    log.warn("nager fetch client error: year={}, country={}, status={}",
                            year, countryCode, e.getStatusCode());
                    throw upstreamFailure();
                }
                lastFailure = e;
            } catch (ResourceAccessException e) {
                lastFailure = e;
            }
            if (attempt == 1) {
                sleep();
            }
        }
        log.warn("nager fetch failed after retry: year={}, country={}", year, countryCode, lastFailure);
        throw upstreamFailure();
    }

    private void sleep() {
        try {
            Thread.sleep(RETRY_DELAY_MILLIS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw upstreamFailure();
        }
    }

    static ApiException upstreamFailure() {
        return new ApiException(HttpStatus.BAD_GATEWAY, "HOLIDAY_SYNC_UPSTREAM", "holiday.sync.upstream");
    }

}
