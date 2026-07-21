package com.attendance.pro.attendance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.attendance.pro.attendance.ScheduleAdminService.RotaCellRequest;
import com.attendance.pro.attendance.ScheduleAdminService.RotaSaveRequest;
import com.attendance.pro.attendance.ScheduleMapper.RotaCell;
import com.attendance.pro.common.ApiException;
import com.attendance.pro.user.User;
import com.attendance.pro.user.UserMapper;
import com.attendance.pro.user.UserStatus;

@ExtendWith(MockitoExtension.class)
class ScheduleAdminServiceTest {

    private static final long TENANT = 10L;
    private static final long USER = 7L;

    @Mock
    private ScheduleMapper scheduleMapper;
    @Mock
    private SchedulePatternMapper patternMapper;
    @Mock
    private UserMapper userMapper;
    @Mock
    private TenantDefaultScheduleMapper tenantDefaultScheduleMapper;

    private ScheduleAdminService service() {
        return new ScheduleAdminService(scheduleMapper, patternMapper, userMapper, tenantDefaultScheduleMapper);
    }

    private void stubMember() {
        when(userMapper.findById(TENANT, USER)).thenReturn(new User(USER, TENANT, "u@a.co", "hash",
                null, "홍길동", "H", null, null,
                com.attendance.pro.user.Role.MEMBER, UserStatus.ACTIVE, false,
                LocalDateTime.now(), LocalDateTime.now()));
    }

    @Test
    @DisplayName("월 로타 저장: 그 달 삭제 후 셀 삽입 — 휴무·야간교대 셀 매핑")
    void saveRotaMapsCells() {
        stubMember();
        RotaSaveRequest req = new RotaSaveRequest(2026, 7, List.of(
                new RotaCellRequest(LocalDate.of(2026, 7, 1), true, null, null, false),        //휴무
                new RotaCellRequest(LocalDate.of(2026, 7, 2), false, LocalTime.of(22, 0), LocalTime.of(6, 0), true), //야간
                new RotaCellRequest(LocalDate.of(2026, 7, 3), false, LocalTime.of(10, 0), LocalTime.of(19, 0), false))); //주간

        service().saveMonthRota(TENANT, USER, req);

        verify(scheduleMapper).deleteRotaInRange(TENANT, USER,
                LocalDate.of(2026, 7, 1), LocalDate.of(2026, 8, 1));
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<RotaCell>> captor = ArgumentCaptor.forClass(List.class);
        verify(scheduleMapper).upsertRota(org.mockito.ArgumentMatchers.eq(TENANT),
                org.mockito.ArgumentMatchers.eq(USER), captor.capture());
        List<RotaCell> cells = captor.getValue();
        assertThat(cells).hasSize(3);
        assertThat(cells.get(0).off()).isTrue();
        assertThat(cells.get(0).startTime()).isNull();
        assertThat(cells.get(1).crossesMidnight()).isTrue();
        assertThat(cells.get(1).endTime()).isEqualTo(LocalTime.of(6, 0));
        assertThat(cells.get(2).crossesMidnight()).isFalse();
    }

    @Test
    @DisplayName("빈 셀 목록이면 그 달 오버라이드 전부 해제(삭제만, 삽입 안 함)")
    void saveEmptyClearsMonth() {
        stubMember();
        service().saveMonthRota(TENANT, USER, new RotaSaveRequest(2026, 7, List.of()));
        verify(scheduleMapper).deleteRotaInRange(anyLong(), anyLong(), any(), any());
        verify(scheduleMapper, never()).upsertRota(anyLong(), anyLong(), any());
    }

    @Test
    @DisplayName("야간 아닌데 종업<=시업이면 400, 근무 셀에 시각 없으면 400")
    void invalidCellsRejected() {
        stubMember();
        RotaSaveRequest badOrder = new RotaSaveRequest(2026, 7, List.of(
                new RotaCellRequest(LocalDate.of(2026, 7, 1), false, LocalTime.of(18, 0), LocalTime.of(9, 0), false)));
        assertThatThrownBy(() -> service().saveMonthRota(TENANT, USER, badOrder))
                .isInstanceOf(ApiException.class)
                .satisfies(e -> assertThat(((ApiException) e).getCode()).isEqualTo("SCHEDULE_CELL_ORDER"));

        RotaSaveRequest noTime = new RotaSaveRequest(2026, 7, List.of(
                new RotaCellRequest(LocalDate.of(2026, 7, 1), false, null, null, false)));
        assertThatThrownBy(() -> service().saveMonthRota(TENANT, USER, noTime))
                .satisfies(e -> assertThat(((ApiException) e).getCode()).isEqualTo("SCHEDULE_CELL_TIME"));

        verify(scheduleMapper, never()).upsertRota(anyLong(), anyLong(), any());
    }

    @Test
    @DisplayName("존재하지 않는 멤버면 404")
    void unknownMember404() {
        when(userMapper.findById(TENANT, USER)).thenReturn(null);
        assertThatThrownBy(() -> service().saveMonthRota(TENANT, USER, new RotaSaveRequest(2026, 7, List.of())))
                .satisfies(e -> assertThat(((ApiException) e).getCode()).isEqualTo("MEMBER_NOT_FOUND"));
    }
}
