package com.attendance.pro.attendance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.attendance.pro.attendance.AttendanceDtos.DailyResponse;
import com.attendance.pro.attendance.AttendanceDtos.ManualBreakRequest;
import com.attendance.pro.attendance.AttendanceDtos.ManualStampRequest;
import com.attendance.pro.attendance.AttendanceDtos.StampResponse;
import com.attendance.pro.common.ApiException;
import com.attendance.pro.common.Messages;
import com.attendance.pro.holiday.HolidayMapper;
import com.attendance.pro.tenant.TenantMapper;

/**
 * 수동 정정 등록·일자 이력 테스트(Phase 5 — manual-attendance §3).
 */
@ExtendWith(MockitoExtension.class)
class ManualAttendanceServiceTest {

    private static final long TENANT_ID = 10L;
    private static final long USER_ID = 1L;

    @Mock
    private AttendanceMapper attendanceMapper;
    @Mock
    private ScheduleMapper scheduleMapper;
    @Mock
    private HolidayMapper holidayMapper;
    @Mock
    private TenantMapper tenantMapper;
    @Mock
    private Messages messages;

    private AttendanceService service() {
        return new AttendanceService(attendanceMapper, scheduleMapper, holidayMapper, tenantMapper, messages);
    }

    private ManualStampRequest request(LocalDate date, String time, AttendanceType type,
            String reasonCode, String reasonText) {
        return new ManualStampRequest(date, time, type, reasonCode, reasonText);
    }

    @Test
    @DisplayName("정상 등록: MANUAL + 사유로 insert, 지정 시각이 응답에 실린다")
    void manualSuccess() {
        when(messages.get(anyString(), any(Object[].class))).thenReturn("ok");
        LocalDate yesterday = LocalDate.now().minusDays(1);

        StampResponse response = service().manual(TENANT_ID, USER_ID,
                request(yesterday, "18:30", AttendanceType.OFF_WORK, "FORGOT", null));

        assertThat(response.type()).isEqualTo(AttendanceType.OFF_WORK);
        assertThat(response.stampedAt()).isEqualTo(yesterday.atTime(18, 30));
        verify(attendanceMapper).insert(eq(TENANT_ID), eq(USER_ID),
                eq(AttendanceType.OFF_WORK.code()), eq(AttendanceStamp.STATUS_ACTIVE),
                eq(yesterday.atTime(18, 30)), eq(null), eq(null), eq(null), eq("manual"),
                eq(StampSource.MANUAL), eq("FORGOT"), eq(null));
    }

    @Test
    @DisplayName("OTHER + 상세 텍스트: trim되어 저장된다")
    void otherWithTextSaved() {
        when(messages.get(anyString(), any(Object[].class))).thenReturn("ok");
        LocalDate yesterday = LocalDate.now().minusDays(1);

        service().manual(TENANT_ID, USER_ID,
                request(yesterday, "09:00", AttendanceType.GO_TO_WORK, "OTHER", "  시스템 점검  "));

        verify(attendanceMapper).insert(eq(TENANT_ID), eq(USER_ID), eq(AttendanceType.GO_TO_WORK.code()),
                eq(AttendanceStamp.STATUS_ACTIVE), eq(yesterday.atTime(9, 0)),
                eq(null), eq(null), eq(null), eq("manual"),
                eq(StampSource.MANUAL), eq("OTHER"), eq("시스템 점검"));
    }

    @Test
    @DisplayName("거부: BREAK 타입 / 미지 사유 / OTHER 텍스트 누락 / 미래 / 90일 초과 — 전부 400 + DB 무변경")
    void rejections() {
        LocalDate yesterday = LocalDate.now().minusDays(1);

        assertThatThrownBy(() -> service().manual(TENANT_ID, USER_ID,
                request(yesterday, "12:00", AttendanceType.BREAK, "FORGOT", null)))
                .isInstanceOf(ApiException.class)
                .hasFieldOrPropertyWithValue("code", "MANUAL_TYPE_INVALID");

        assertThatThrownBy(() -> service().manual(TENANT_ID, USER_ID,
                request(yesterday, "12:00", AttendanceType.GO_TO_WORK, "SLEEPY", null)))
                .isInstanceOf(ApiException.class)
                .hasFieldOrPropertyWithValue("code", "MANUAL_REASON_UNSUPPORTED");

        assertThatThrownBy(() -> service().manual(TENANT_ID, USER_ID,
                request(yesterday, "12:00", AttendanceType.GO_TO_WORK, "OTHER", "   ")))
                .isInstanceOf(ApiException.class)
                .hasFieldOrPropertyWithValue("code", "MANUAL_REASON_TEXT_REQUIRED");

        assertThatThrownBy(() -> service().manual(TENANT_ID, USER_ID,
                request(LocalDate.now().plusDays(1), "09:00", AttendanceType.GO_TO_WORK, "FORGOT", null)))
                .isInstanceOf(ApiException.class)
                .hasFieldOrPropertyWithValue("code", "MANUAL_FUTURE");

        assertThatThrownBy(() -> service().manual(TENANT_ID, USER_ID,
                request(LocalDate.now().minusDays(91), "09:00", AttendanceType.GO_TO_WORK, "FORGOT", null)))
                .isInstanceOf(ApiException.class)
                .hasFieldOrPropertyWithValue("code", "MANUAL_TOO_OLD");

        verify(attendanceMapper, never()).insert(anyLong(), anyLong(), anyInt(), anyInt(),
                any(), any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("daily: 그 날짜의 전 스탬프(중복·수동 포함)가 시각 순으로, BREAK 종료는 breakEnd=true")
    void dailyHistory() {
        LocalDate date = LocalDate.of(2026, 7, 9);
        when(attendanceMapper.findBetween(TENANT_ID, USER_ID, date, date.plusDays(1)))
                .thenReturn(List.of(
                        new AttendanceStamp(1L, USER_ID, AttendanceType.GO_TO_WORK.code(), 0,
                                date.atTime(9, 0), StampSource.AUTO, null, null),
                        new AttendanceStamp(2L, USER_ID, AttendanceType.BREAK.code(),
                                AttendanceStamp.STATUS_BREAK_ENDED, date.atTime(13, 0),
                                StampSource.AUTO, null, null),
                        new AttendanceStamp(3L, USER_ID, AttendanceType.OFF_WORK.code(), 0,
                                date.atTime(18, 0), StampSource.MANUAL, "FORGOT", null)));

        DailyResponse response = service().daily(TENANT_ID, USER_ID, date);

        assertThat(response.stamps()).hasSize(3);
        assertThat(response.stamps().get(0).source()).isEqualTo(StampSource.AUTO);
        assertThat(response.stamps().get(1).breakEnd()).isTrue();
        assertThat(response.stamps().get(2).source()).isEqualTo(StampSource.MANUAL);
        assertThat(response.stamps().get(2).reasonCode()).isEqualTo("FORGOT");
    }


    private AttendanceStamp manualRow(long id, AttendanceType type, int status) {
        return new AttendanceStamp(id, USER_ID, type.code(), status, LocalDate.now().atStartOfDay(),
                StampSource.MANUAL, "FORGOT", null);
    }

    @Test
    @DisplayName("updateManual: 본인 MANUAL 근무 행이면 시각/구분/사유가 수정된다(등록과 동일 검증 규칙)")
    void updateManual() {
        when(messages.get(anyString(), any(Object[].class))).thenReturn("ok");
        LocalDate yesterday = LocalDate.now().minusDays(1);
        when(attendanceMapper.findManualById(TENANT_ID, USER_ID, 7L))
                .thenReturn(manualRow(7L, AttendanceType.GO_TO_WORK, AttendanceStamp.STATUS_ACTIVE));
        when(attendanceMapper.updateManual(eq(TENANT_ID), eq(USER_ID), eq(7L),
                eq(AttendanceType.OFF_WORK.code()), eq(yesterday.atTime(17, 30)),
                eq("DEVICE"), eq(null))).thenReturn(1);

        StampResponse response = service().updateManual(TENANT_ID, USER_ID, 7L,
                request(yesterday, "17:30", AttendanceType.OFF_WORK, "DEVICE", null));

        assertThat(response.stampedAt()).isEqualTo(yesterday.atTime(17, 30));
        verify(attendanceMapper).updateManual(eq(TENANT_ID), eq(USER_ID), eq(7L),
                eq(AttendanceType.OFF_WORK.code()), eq(yesterday.atTime(17, 30)),
                eq("DEVICE"), eq(null));
    }

    @Test
    @DisplayName("updateManual: 휴식 행은 시각·사유만 정정(BREAK 유지, status 미변경)")
    void updateManualBreak() {
        when(messages.get(anyString(), any(Object[].class))).thenReturn("ok");
        LocalDate yesterday = LocalDate.now().minusDays(1);
        when(attendanceMapper.findManualById(TENANT_ID, USER_ID, 9L))
                .thenReturn(manualRow(9L, AttendanceType.BREAK, AttendanceStamp.STATUS_ACTIVE));
        when(attendanceMapper.updateManual(eq(TENANT_ID), eq(USER_ID), eq(9L),
                eq(AttendanceType.BREAK.code()), eq(yesterday.atTime(12, 10)),
                eq("FORGOT"), eq(null))).thenReturn(1);

        service().updateManual(TENANT_ID, USER_ID, 9L,
                request(yesterday, "12:10", AttendanceType.BREAK, "FORGOT", null));

        verify(attendanceMapper).updateManual(eq(TENANT_ID), eq(USER_ID), eq(9L),
                eq(AttendanceType.BREAK.code()), eq(yesterday.atTime(12, 10)), eq("FORGOT"), eq(null));
    }

    @Test
    @DisplayName("updateManual: 미존재는 404, 휴식↔근무 구분 전환은 400 + DB 무변경")
    void updateManualRejections() {
        LocalDate yesterday = LocalDate.now().minusDays(1);
        when(attendanceMapper.findManualById(TENANT_ID, USER_ID, 8L)).thenReturn(null);
        assertThatThrownBy(() -> service().updateManual(TENANT_ID, USER_ID, 8L,
                request(yesterday, "17:30", AttendanceType.OFF_WORK, "FORGOT", null)))
                .isInstanceOf(ApiException.class)
                .hasFieldOrPropertyWithValue("code", "MANUAL_NOT_FOUND");

        //근무 행을 BREAK로 전환 시도 → 구분 전환 불가
        when(attendanceMapper.findManualById(TENANT_ID, USER_ID, 7L))
                .thenReturn(manualRow(7L, AttendanceType.GO_TO_WORK, AttendanceStamp.STATUS_ACTIVE));
        assertThatThrownBy(() -> service().updateManual(TENANT_ID, USER_ID, 7L,
                request(yesterday, "12:00", AttendanceType.BREAK, "FORGOT", null)))
                .isInstanceOf(ApiException.class)
                .hasFieldOrPropertyWithValue("code", "MANUAL_TYPE_INVALID");

        verify(attendanceMapper, never()).updateManual(anyLong(), anyLong(), anyLong(),
                anyInt(), any(), anyString(), any());
    }

    @Test
    @DisplayName("manualBreak: 시작·종료 쌍으로 insert(시작=ACTIVE, 종료=BREAK_ENDED), 순서 뒤집히면 400")
    void manualBreak() {
        when(messages.get(anyString(), any(Object[].class))).thenReturn("ok");
        LocalDate yesterday = LocalDate.now().minusDays(1);

        service().manualBreak(TENANT_ID, USER_ID,
                new ManualBreakRequest(yesterday, "12:00", "13:00", "FORGOT", null));

        verify(attendanceMapper).insert(eq(TENANT_ID), eq(USER_ID), eq(AttendanceType.BREAK.code()),
                eq(AttendanceStamp.STATUS_ACTIVE), eq(yesterday.atTime(12, 0)),
                eq(null), eq(null), eq(null), eq("manual"), eq(StampSource.MANUAL), eq("FORGOT"), eq(null));
        verify(attendanceMapper).insert(eq(TENANT_ID), eq(USER_ID), eq(AttendanceType.BREAK.code()),
                eq(AttendanceStamp.STATUS_BREAK_ENDED), eq(yesterday.atTime(13, 0)),
                eq(null), eq(null), eq(null), eq("manual"), eq(StampSource.MANUAL), eq("FORGOT"), eq(null));

        assertThatThrownBy(() -> service().manualBreak(TENANT_ID, USER_ID,
                new ManualBreakRequest(yesterday, "13:00", "12:00", "FORGOT", null)))
                .isInstanceOf(ApiException.class)
                .hasFieldOrPropertyWithValue("code", "MANUAL_BREAK_RANGE");
    }

}
