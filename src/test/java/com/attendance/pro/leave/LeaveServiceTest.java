package com.attendance.pro.leave;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.attendance.pro.common.ApiException;
import com.attendance.pro.holiday.HolidayMapper;
import com.attendance.pro.leave.LeaveDtos.LeaveApplyRequest;
import com.attendance.pro.tenant.Tenant;
import com.attendance.pro.tenant.TenantMapper;
import com.attendance.pro.tenant.TenantStatus;
import com.attendance.pro.user.Role;
import com.attendance.pro.user.User;
import com.attendance.pro.user.UserMapper;
import com.attendance.pro.user.UserStatus;

/**
 * 휴가 서비스 테스트 — 환산 헬퍼(DAY-MIN)·근무일 계산(WD)·신청 검증(APPLY)·결재(DEC)·재계산(REC).
 * 해피패스 insert/승인 반영은 실 DB 라이브 스모크에서 검증. 여기서는 순수 로직·검증 분기·결재 가드를 다룬다.
 */
@ExtendWith(MockitoExtension.class)
class LeaveServiceTest {

    private static final long TENANT = 1L;
    private static final long USER = 2L;
    private static final long ANNUAL_ID = 1L;

    @Mock private LeaveTypeMapper typeMapper;
    @Mock private LeaveGrantMapper grantMapper;
    @Mock private LeaveRequestMapper requestMapper;
    @Mock private UserMapper userMapper;
    @Mock private TenantMapper tenantMapper;
    @Mock private HolidayMapper holidayMapper;

    //2026-07-13(월)
    private final Clock clock = Clock.fixed(Instant.parse("2026-07-13T00:00:00Z"), ZoneOffset.UTC);

    private LeaveService service() {
        return new LeaveService(typeMapper, grantMapper, requestMapper, userMapper, tenantMapper,
                holidayMapper, clock);
    }

    private static User member(LocalTime start, LocalTime end, String workDays, LocalDate hire) {
        return new User(USER, TENANT, "hong@acme.co.kr", "hash", null, "홍길동", null,
                start, end, workDays, hire, Role.MEMBER, UserStatus.ACTIVE, false,
                LocalDateTime.now(), LocalDateTime.now());
    }

    private static LeaveType annual() {
        return new LeaveType(ANNUAL_ID, TENANT, "ANNUAL", "연차", true, LeaveUnit.DAY, true, true,
                true, 0, LocalDateTime.now(), LocalDateTime.now());
    }

    private void stubCountry() {
        when(tenantMapper.findById(TENANT))
                .thenReturn(new Tenant(TENANT, "DEFAULT", "회사", "KR", TenantStatus.ACTIVE, null));
    }

    // ===== 환산 헬퍼 =====

    @Test
    @DisplayName("DAY-MIN-01: 1일 = 근무구간 − 법정휴게(09~18 KR = 480분)")
    void standardDayMinutesKr() {
        int min = service().standardDayMinutes(
                member(LocalTime.of(9, 0), LocalTime.of(18, 0), "1111100", null),
                com.attendance.pro.tenant.ProfileCountry.KR);
        assertThat(min).isEqualTo(480);
    }

    @Test
    @DisplayName("DAY-MIN-02: 비정상 스케줄(end<=start)은 480 폴백")
    void standardDayMinutesFallback() {
        int min = service().standardDayMinutes(
                member(LocalTime.of(18, 0), LocalTime.of(9, 0), "1111100", null),
                com.attendance.pro.tenant.ProfileCountry.KR);
        assertThat(min).isEqualTo(480);
    }

    @Test
    @DisplayName("WD-01: 근무 요일 & 공휴일 제외 카운트(월~일 중 평일 5, 토·일 제외)")
    void countWorkingDays() {
        User u = member(LocalTime.of(9, 0), LocalTime.of(18, 0), "1111100", null);
        //2026-07-13(월)~07-19(일): 평일 5일. 07-15(수)를 공휴일로 제외 → 4
        int days = service().countWorkingDays(u, LocalDate.of(2026, 7, 13), LocalDate.of(2026, 7, 19),
                Set.of(LocalDate.of(2026, 7, 15)));
        assertThat(days).isEqualTo(4);
    }

    @Test
    @DisplayName("BAL-01: 만기일별 잔여 — 만기 임박순 FIFO 차감(음수 조정 480분이 이른 부여부터 소진)")
    void balanceRowsFifo() {
        when(userMapper.findById(TENANT, USER))
                .thenReturn(member(LocalTime.of(9, 0), LocalTime.of(18, 0), "1111100", null));
        stubCountry();
        when(typeMapper.findByTenant(TENANT)).thenReturn(List.of(annual()));
        when(requestMapper.findViewByUser(TENANT, USER)).thenReturn(List.of());
        //만기 임박순: 2026-07-15(3일=1440) → 2027-07-15(2일=960) → 무기한 조정 −480
        when(grantMapper.findActiveByUser(eq(TENANT), eq(USER), any())).thenReturn(List.of(
                new LeaveGrant(10L, TENANT, USER, ANNUAL_ID, 1440, LocalDate.of(2026, 1, 1),
                        LocalDate.of(2026, 7, 15), LeaveSource.MANUAL, null, null, null,
                        LocalDateTime.now(), LocalDateTime.now()),
                new LeaveGrant(11L, TENANT, USER, ANNUAL_ID, 960, LocalDate.of(2026, 1, 1),
                        LocalDate.of(2027, 7, 15), LeaveSource.MANUAL, null, null, null,
                        LocalDateTime.now(), LocalDateTime.now()),
                new LeaveGrant(12L, TENANT, USER, ANNUAL_ID, -480, LocalDate.of(2026, 1, 1), null,
                        LeaveSource.MANUAL, null, null, null, LocalDateTime.now(), LocalDateTime.now())));

        List<LeaveDtos.LeaveBalanceRowResponse> rows = service().balanceRows(TENANT, USER);

        assertThat(rows).hasSize(2);
        assertThat(rows.get(0).remainingMinutes()).isEqualTo(960); //1440 − 480(FIFO)
        assertThat(rows.get(0).expiresOn()).isEqualTo(LocalDate.of(2026, 7, 15));
        assertThat(rows.get(1).remainingMinutes()).isEqualTo(960); //그대로
        assertThat(rows.get(1).expiresOn()).isEqualTo(LocalDate.of(2027, 7, 15));
    }

    // ===== 신청 검증 =====

    @Test
    @DisplayName("APPLY-01: 종료일이 시작일보다 이르면 400")
    void applyInvalidRange() {
        when(userMapper.findById(TENANT, USER))
                .thenReturn(member(LocalTime.of(9, 0), LocalTime.of(18, 0), "1111100", null));
        when(typeMapper.findById(TENANT, ANNUAL_ID)).thenReturn(annual());
        stubCountry();
        LeaveApplyRequest req = new LeaveApplyRequest(ANNUAL_ID, true,
                LocalDate.of(2026, 7, 21), LocalDate.of(2026, 7, 20), false, null, null, null);
        assertThatThrownBy(() -> service().apply(TENANT, USER, req))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("leave.request.range");
    }

    @Test
    @DisplayName("APPLY-02: 기간에 근무일 없음(토요일) → 400")
    void applyNoWorkingDay() {
        when(userMapper.findById(TENANT, USER))
                .thenReturn(member(LocalTime.of(9, 0), LocalTime.of(18, 0), "1111100", null));
        when(typeMapper.findById(TENANT, ANNUAL_ID)).thenReturn(annual());
        stubCountry();
        when(holidayMapper.findHolidaysBetween(anyLong(), any(), any())).thenReturn(List.of());
        //2026-07-18 토요일
        LeaveApplyRequest req = new LeaveApplyRequest(ANNUAL_ID, true,
                LocalDate.of(2026, 7, 18), LocalDate.of(2026, 7, 18), false, null, null, null);
        assertThatThrownBy(() -> service().apply(TENANT, USER, req))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("leave.request.no-working-day");
    }

    @Test
    @DisplayName("APPLY-03: 잔여 부족 → 400(가용 = 부여 − 승인 − 대기)")
    void applyInsufficient() {
        when(userMapper.findById(TENANT, USER))
                .thenReturn(member(LocalTime.of(9, 0), LocalTime.of(18, 0), "1111100", null));
        when(typeMapper.findById(TENANT, ANNUAL_ID)).thenReturn(annual());
        stubCountry();
        when(holidayMapper.findHolidaysBetween(anyLong(), any(), any())).thenReturn(List.of());
        //부여 240분(반나절)뿐인데 하루(480) 신청
        when(grantMapper.sumEffectiveMinutes(eq(TENANT), eq(USER), eq(ANNUAL_ID), any()))
                .thenReturn(240);
        when(requestMapper.findViewByUser(TENANT, USER)).thenReturn(List.of());
        LeaveApplyRequest req = new LeaveApplyRequest(ANNUAL_ID, true,
                LocalDate.of(2026, 7, 20), LocalDate.of(2026, 7, 20), false, null, null, null);
        assertThatThrownBy(() -> service().apply(TENANT, USER, req))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("leave.request.insufficient");
    }

    @Test
    @DisplayName("APPLY-05: 기간이 기존 신청과 겹치면 400(이중 차감 방지)")
    void applyOverlap() {
        when(userMapper.findById(TENANT, USER))
                .thenReturn(member(LocalTime.of(9, 0), LocalTime.of(18, 0), "1111100", null));
        when(typeMapper.findById(TENANT, ANNUAL_ID)).thenReturn(annual());
        stubCountry();
        when(holidayMapper.findHolidaysBetween(anyLong(), any(), any())).thenReturn(List.of());
        when(requestMapper.existsOverlap(eq(TENANT), eq(USER), any(), any())).thenReturn(true);
        LeaveApplyRequest req = new LeaveApplyRequest(ANNUAL_ID, true,
                LocalDate.of(2026, 7, 20), LocalDate.of(2026, 7, 20), false, null, null, null);
        assertThatThrownBy(() -> service().apply(TENANT, USER, req))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("leave.request.overlap");
    }

    @Test
    @DisplayName("APPLY-04: 시간단위 종료<=시작 또는 날짜 다르면 400")
    void applyHourInvalid() {
        when(userMapper.findById(TENANT, USER))
                .thenReturn(member(LocalTime.of(9, 0), LocalTime.of(18, 0), "1111100", null));
        when(typeMapper.findById(TENANT, ANNUAL_ID)).thenReturn(annual());
        stubCountry();
        LeaveApplyRequest req = new LeaveApplyRequest(ANNUAL_ID, false, null, null, null,
                LocalDateTime.of(2026, 7, 20, 16, 0), LocalDateTime.of(2026, 7, 20, 14, 0), null);
        assertThatThrownBy(() -> service().apply(TENANT, USER, req))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("leave.request.range");
    }

    // ===== 결재 =====

    @Test
    @DisplayName("DEC-01: 이미 처리된(APPROVED) 신청 결재 → 409")
    void decideAlreadyDecided() {
        LeaveRequest approved = new LeaveRequest(9L, TENANT, USER, ANNUAL_ID,
                LocalDateTime.of(2026, 7, 20, 0, 0), LocalDateTime.of(2026, 7, 21, 0, 0), 480,
                true, false, null, LeaveStatus.APPROVED, 3L, LocalDateTime.now(), null, null,
                LocalDateTime.now(), LocalDateTime.now());
        when(requestMapper.findById(TENANT, 9L)).thenReturn(approved);
        assertThatThrownBy(() -> service().decide(TENANT, 3L, 9L, true, null))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("leave.request.already-decided");
    }

    @Test
    @DisplayName("DEC-02: 승인 시점 잔여 부족 → 409(부여 − 승인 < 신청)")
    void decideInsufficientAtApproval() {
        LeaveRequest pending = new LeaveRequest(9L, TENANT, USER, ANNUAL_ID,
                LocalDateTime.of(2026, 7, 20, 0, 0), LocalDateTime.of(2026, 7, 21, 0, 0), 480,
                true, false, null, LeaveStatus.PENDING, null, null, null, null,
                LocalDateTime.now(), LocalDateTime.now());
        when(requestMapper.findById(TENANT, 9L)).thenReturn(pending);
        when(grantMapper.sumEffectiveMinutes(eq(TENANT), eq(USER), eq(ANNUAL_ID), any()))
                .thenReturn(240);
        when(requestMapper.sumApprovedMinutes(TENANT, USER, ANNUAL_ID)).thenReturn(0);
        assertThatThrownBy(() -> service().decide(TENANT, 3L, 9L, true, null))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("leave.request.insufficient");
    }

    @Test
    @DisplayName("DEC-03: 반려는 잔여 확인 없이 REJECTED 전이")
    void decideReject() {
        LeaveRequest pending = new LeaveRequest(9L, TENANT, USER, ANNUAL_ID,
                LocalDateTime.of(2026, 7, 20, 0, 0), LocalDateTime.of(2026, 7, 21, 0, 0), 480,
                true, false, null, LeaveStatus.PENDING, null, null, null, null,
                LocalDateTime.now(), LocalDateTime.now());
        when(requestMapper.findById(TENANT, 9L)).thenReturn(pending);
        when(requestMapper.decide(eq(TENANT), eq(9L), eq(LeaveStatus.REJECTED), eq(3L), any()))
                .thenReturn(1);
        service().decide(TENANT, 3L, 9L, false, "인력 부족");
        verify(requestMapper).decide(TENANT, 9L, LeaveStatus.REJECTED, 3L, "인력 부족");
    }

    // ===== 취소 =====

    @Test
    @DisplayName("CANCEL-01: 당일·시작된 승인 휴가는 멤버 취소 신청 불가 → 400 cancel-same-day")
    void requestCancelSameDay() {
        when(requestMapper.requestCancelByUser(eq(TENANT), eq(USER), eq(9L), any(), any()))
                .thenReturn(0);
        LeaveRequest approvedToday = new LeaveRequest(9L, TENANT, USER, ANNUAL_ID,
                LocalDateTime.of(2026, 7, 13, 0, 0), LocalDateTime.of(2026, 7, 14, 0, 0), 480,
                true, false, null, LeaveStatus.APPROVED, 3L, LocalDateTime.now(), null, null,
                LocalDateTime.now(), LocalDateTime.now());
        when(requestMapper.findById(TENANT, 9L)).thenReturn(approvedToday);
        assertThatThrownBy(() -> service().requestCancel(TENANT, USER, 9L, "개인 사정"))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("leave.request.cancel-same-day");
    }

    @Test
    @DisplayName("CANCEL-02: 관리자 직접 취소 → CANCELED 전이(취소사유 기록)")
    void cancelByAdmin() {
        when(requestMapper.cancelByAdmin(TENANT, 9L, 3L, "회사 사정")).thenReturn(1);
        service().cancelByAdmin(TENANT, 3L, 9L, "회사 사정");
        verify(requestMapper).cancelByAdmin(TENANT, 9L, 3L, "회사 사정");
    }

    // ===== 재계산 =====

    @Test
    @DisplayName("REC-01: KR 3년 근속 연차 재계산 = 16일 × 480 = 7680분 upsert")
    void recomputeAnnualKr() {
        User u = member(LocalTime.of(9, 0), LocalTime.of(18, 0), "1111100", LocalDate.of(2023, 1, 1));
        when(userMapper.findById(TENANT, USER)).thenReturn(u);
        when(typeMapper.findAnnual(TENANT)).thenReturn(annual());
        stubCountry();
        service().recomputeAnnual(TENANT, 3L, USER);
        ArgumentCaptor<Integer> minutes = ArgumentCaptor.forClass(Integer.class);
        verify(grantMapper).upsertAuto(eq(TENANT), eq(USER), eq(ANNUAL_ID), minutes.capture(),
                any(), any(), eq(2026), any(), eq(3L));
        assertThat(minutes.getValue()).isEqualTo(16 * 480);
    }

    // ===== 일괄 부여(#9) =====

    @Test
    @DisplayName("BULK-01: 두 멤버에 3일 일괄 부여 → 각자 표준분 환산(480×3=1440)으로 insert 2회, 중복 userId는 1회")
    void grantBulk() {
        long user2 = 5L;
        User u1 = member(LocalTime.of(9, 0), LocalTime.of(18, 0), "1111100", LocalDate.of(2023, 1, 1));
        User u2 = new User(user2, TENANT, "kim@acme.co.kr", "hash", null, "김철수", null,
                LocalTime.of(9, 0), LocalTime.of(18, 0), "1111100", LocalDate.of(2024, 1, 1),
                Role.MEMBER, UserStatus.ACTIVE, false, LocalDateTime.now(), LocalDateTime.now());
        when(typeMapper.findById(TENANT, ANNUAL_ID)).thenReturn(annual());
        when(userMapper.findById(TENANT, USER)).thenReturn(u1);
        when(userMapper.findById(TENANT, user2)).thenReturn(u2);
        stubCountry();

        //중복 USER는 한 번만 부여되어야 한다
        int count = service().grantManualBulk(TENANT, 9L,
                new LeaveDtos.LeaveBulkGrantRequest(List.of(USER, user2, USER), ANNUAL_ID, 3.0, null, "여름 특별"));

        assertThat(count).isEqualTo(2);
        verify(grantMapper).insert(eq(TENANT), eq(USER), eq(ANNUAL_ID), eq(1440),
                any(), any(), eq(LeaveSource.MANUAL), any(), eq("여름 특별"), eq(9L));
        verify(grantMapper).insert(eq(TENANT), eq(user2), eq(ANNUAL_ID), eq(1440),
                any(), any(), eq(LeaveSource.MANUAL), any(), eq("여름 특별"), eq(9L));
    }
}
