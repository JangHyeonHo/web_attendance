package com.attendance.pro.audit;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;

/**
 * 감사 기록 서비스 — 요청 컨텍스트 추출·길이 절단·실패 삼킴.
 */
@ExtendWith(MockitoExtension.class)
class AuditServiceTest {

    @Mock
    private AuditLogMapper mapper;

    private AuditService service() {
        return new AuditService(mapper);
    }

    @Test
    @DisplayName("AUD-01: IP/UA/경로를 요청에서 뽑아 카테고리·이벤트와 함께 기록")
    void recordsRequestContext() {
        when(mapper.insert(any(), any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(1);
        MockHttpServletRequest req = new MockHttpServletRequest("POST", "/api/v1/auth/login");
        req.setRemoteAddr("203.0.113.7");
        req.addHeader("User-Agent", "TestAgent/1.0");

        service().record(AuditEvent.LOGIN_SUCCESS, 10L, 5L, "hong@acme.co.kr", null, req);

        verify(mapper).insert(eq(10L), eq(5L), eq("AUTH"), eq("LOGIN_SUCCESS"), eq(null),
                eq("hong@acme.co.kr"), eq("203.0.113.7"), eq("TestAgent/1.0"),
                eq("/api/v1/auth/login"));
    }

    @Test
    @DisplayName("AUD-02: 긴 detail은 500자로 절단")
    void truncatesLongDetail() {
        when(mapper.insert(any(), any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(1);
        String longDetail = "x".repeat(1000);
        service().record(AuditEvent.APP_ERROR, null, null, null, longDetail, null);

        ArgumentCaptor<String> detail = ArgumentCaptor.forClass(String.class);
        verify(mapper).insert(any(), any(), eq("ERROR"), eq("APP_ERROR"), detail.capture(),
                any(), any(), any(), any());
        assertThatCode(() -> {
        }).doesNotThrowAnyException();
        org.assertj.core.api.Assertions.assertThat(detail.getValue()).hasSize(500);
    }

    @Test
    @DisplayName("AUD-03: 매퍼 예외는 삼켜서 본 요청 흐름을 깨지 않는다")
    void swallowsMapperFailure() {
        doThrow(new RuntimeException("db down")).when(mapper)
                .insert(any(), any(), any(), any(), any(), any(), any(), any(), any());
        assertThatCode(() -> service().record(AuditEvent.LOGIN_FAIL, null, null, "x@y.z", "r", null))
                .doesNotThrowAnyException();
    }
}
