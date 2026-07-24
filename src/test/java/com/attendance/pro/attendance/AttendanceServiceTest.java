package com.attendance.pro.attendance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.Locale;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.context.support.ResourceBundleMessageSource;

import com.attendance.pro.attendance.AttendanceDtos.CheckRequest;
import com.attendance.pro.attendance.AttendanceDtos.CheckResponse;
import com.attendance.pro.attendance.AttendanceDtos.ConfirmRequest;
import com.attendance.pro.attendance.AttendanceDtos.StatusAlert;
import com.attendance.pro.attendance.AttendanceDtos.StatusResponse;
import com.attendance.pro.attendance.AttendanceDtos.WorkStatus;
import com.attendance.pro.common.ApiException;
import com.attendance.pro.common.Messages;

/**
 * 출결 상태머신(체크 규칙)과 상태 조회, 확정 변조 탐지 테스트.
 */
@ExtendWith(MockitoExtension.class)
class AttendanceServiceTest {

    private static final long TENANT_ID = 1L;
    private static final long USER_ID = 1L;

    @Mock
    private AttendanceMapper attendanceMapper;

    @Mock
    private ScheduleMapper scheduleMapper;

    @Mock
    private com.attendance.pro.holiday.HolidayMapper holidayMapper;

    @Mock
    private com.attendance.pro.tenant.TenantMapper tenantMapper;

    @Mock
    private com.attendance.pro.leave.LeaveRequestMapper leaveRequestMapper;

    @Mock
    private SchedulePatternMapper patternMapper;

    @Mock
    private com.attendance.pro.attendance.close.AttendanceCloseMapper closeMapper;

    private AttendanceService service;

    @BeforeEach
    void setUp() {
        //메시지는 실제 번들로 해석하고, 한국어 기준으로 검증한다
        LocaleContextHolder.setLocale(Locale.KOREAN);
        service = new AttendanceService(attendanceMapper, scheduleMapper, holidayMapper, tenantMapper,
                leaveRequestMapper, patternMapper, closeMapper, realMessages());
    }

    private static final long PATTERN_ID = 100L;

    /** 전 요일 s~e 근무의 정기 패턴(주기 1주) — 요일 무관 검증용(주말 실행 시 dayOff 방지). */
    private static SchedulePattern allDayPattern() {
        return new SchedulePattern(PATTERN_ID, TENANT_ID, USER_ID, 1,
                java.time.LocalDate.of(2024, 1, 1), true, LocalDateTime.now(), LocalDateTime.now());
    }

    private static java.util.List<SchedulePatternSlot> allDaySlots(java.time.LocalTime start,
            java.time.LocalTime end) {
        java.util.List<SchedulePatternSlot> slots = new java.util.ArrayList<>();
        for (int dow = 1; dow <= 7; dow++) {
            slots.add(new SchedulePatternSlot(PATTERN_ID, 0, dow, false, start, end, false));
        }
        return slots;
    }

    /** status()의 오늘 스케줄 해석 경로 스텁(오버라이드/공휴일 없음 + 정기 패턴 09:00~18:00). */
    private void stubTodaySchedule() {
        when(scheduleMapper.findBetween(eq(TENANT_ID), eq(USER_ID), any(), any())).thenReturn(java.util.List.of());
        when(holidayMapper.findHolidaysBetween(eq(TENANT_ID), any(), any())).thenReturn(java.util.List.of());
        when(patternMapper.findByUser(TENANT_ID, USER_ID)).thenReturn(allDayPattern());
        when(patternMapper.findSlots(PATTERN_ID))
                .thenReturn(allDaySlots(java.time.LocalTime.of(9, 0), java.time.LocalTime.of(18, 0)));
    }

    static Messages realMessages() {
        ResourceBundleMessageSource messageSource = new ResourceBundleMessageSource();
        messageSource.setBasename("messages/messages");
        messageSource.setDefaultEncoding("UTF-8");
        messageSource.setFallbackToSystemLocale(false);
        return new Messages(messageSource);
    }

    private AttendanceStamp stamp(AttendanceType type, int status, LocalDateTime at) {
        return new AttendanceStamp(1L, USER_ID, type.code(), status, at, StampSource.AUTO, null, null);
    }

    @Nested
    @DisplayName("체크 규칙(evaluate)")
    class EvaluateRules {

        @Test
        @DisplayName("최근 기록이 없으면 출근만 허용")
        void noRecentRecord() {
            assertThat(service.evaluate(null, AttendanceType.GO_TO_WORK)).isNull();
            assertThat(service.evaluate(null, AttendanceType.OFF_WORK)).isEqualTo(ConfirmCode.NOT_WORKING_YET);
            assertThat(service.evaluate(null, AttendanceType.EARLY_DEPARTURE)).isEqualTo(ConfirmCode.NOT_WORKING_YET);
            assertThat(service.evaluate(null, AttendanceType.BREAK)).isEqualTo(ConfirmCode.NOT_WORKING_YET);
        }

        @Test
        @DisplayName("같은 타입 반복은 덮어쓰기 확인(휴식 제외)")
        void sameTypeRepeat() {
            LocalDateTime now = LocalDateTime.now();
            assertThat(service.evaluate(stamp(AttendanceType.GO_TO_WORK, 0, now), AttendanceType.GO_TO_WORK))
                    .isEqualTo(ConfirmCode.ALREADY_WORKING);
            assertThat(service.evaluate(stamp(AttendanceType.OFF_WORK, 0, now), AttendanceType.OFF_WORK))
                    .isEqualTo(ConfirmCode.ALREADY_OFF_WORK);
            assertThat(service.evaluate(stamp(AttendanceType.EARLY_DEPARTURE, 0, now), AttendanceType.EARLY_DEPARTURE))
                    .isEqualTo(ConfirmCode.ALREADY_EARLY_DEPARTURE);
        }

        @Test
        @DisplayName("휴식 반복은 시작/종료 토글이므로 허용")
        void breakRepeatAllowed() {
            assertThat(service.evaluate(stamp(AttendanceType.BREAK, 0, LocalDateTime.now()), AttendanceType.BREAK))
                    .isNull();
        }

        @Test
        @DisplayName("같은 날 퇴근/조퇴 후 출근은 재출근 확인")
        void reAttendSameDay() {
            //자정 직후 실행 시 minusHours(1)이 전날이 되는 시각 의존을 없앤다(오늘 00:00 고정)
            LocalDateTime today = java.time.LocalDate.now().atStartOfDay();
            assertThat(service.evaluate(stamp(AttendanceType.OFF_WORK, 0, today), AttendanceType.GO_TO_WORK))
                    .isEqualTo(ConfirmCode.RE_ATTEND);
            assertThat(service.evaluate(stamp(AttendanceType.EARLY_DEPARTURE, 0, today), AttendanceType.GO_TO_WORK))
                    .isEqualTo(ConfirmCode.RE_ATTEND);
        }

        @Test
        @DisplayName("휴식 기록 상태에서는 재출근 불가")
        void cannotAttendOnBreak() {
            assertThat(service.evaluate(stamp(AttendanceType.BREAK, 0, LocalDateTime.now()), AttendanceType.GO_TO_WORK))
                    .isEqualTo(ConfirmCode.ON_BREAK_CANNOT_ATTEND);
            assertThat(service.evaluate(stamp(AttendanceType.BREAK, 1, LocalDateTime.now()), AttendanceType.GO_TO_WORK))
                    .isEqualTo(ConfirmCode.ON_BREAK_CANNOT_ATTEND);
        }

        @Test
        @DisplayName("출근 중이 아니면 퇴근/조퇴 불가, 휴식 종료 후에는 가능")
        void offWorkRules() {
            LocalDateTime yesterday = LocalDateTime.now().minusHours(30);
            //전날 퇴근 기록만 있는 상태(다른 날이므로 같은타입 반복이 아님)에서 조퇴 요청
            assertThat(service.evaluate(stamp(AttendanceType.OFF_WORK, 0, yesterday), AttendanceType.EARLY_DEPARTURE))
                    .isEqualTo(ConfirmCode.NOT_ON_DUTY);
            //휴식 진행 중이면 퇴근 불가
            assertThat(service.evaluate(stamp(AttendanceType.BREAK, 0, LocalDateTime.now()), AttendanceType.OFF_WORK))
                    .isEqualTo(ConfirmCode.NOT_ON_DUTY);
            //출근 중이면 퇴근 가능
            assertThat(service.evaluate(stamp(AttendanceType.GO_TO_WORK, 0, LocalDateTime.now()), AttendanceType.OFF_WORK))
                    .isNull();
            //휴식 종료 상태면 퇴근 가능
            assertThat(service.evaluate(stamp(AttendanceType.BREAK, 1, LocalDateTime.now()), AttendanceType.OFF_WORK))
                    .isNull();
        }

        @Test
        @DisplayName("직전 기록이 퇴근/조퇴면 휴식 불가")
        void breakRules() {
            LocalDateTime yesterday = LocalDateTime.now().minusHours(30);
            assertThat(service.evaluate(stamp(AttendanceType.OFF_WORK, 0, yesterday), AttendanceType.BREAK))
                    .isEqualTo(ConfirmCode.CANNOT_BREAK);
            assertThat(service.evaluate(stamp(AttendanceType.GO_TO_WORK, 0, LocalDateTime.now()), AttendanceType.BREAK))
                    .isNull();
        }
    }

    @Nested
    @DisplayName("체크/확정 플로우")
    class CheckConfirmFlow {

        @Test
        @DisplayName("체크 통과시 토큰이 발급된다")
        void checkIssuesToken() {
            when(attendanceMapper.findLatest(TENANT_ID, USER_ID)).thenReturn(null);
            CheckRequest request = new CheckRequest(AttendanceType.GO_TO_WORK, 37.5, 127.0, "서울", "Chrome", null);

            CheckResponse response = service.check(TENANT_ID, USER_ID, request);

            assertThat(response.allowed()).isTrue();
            assertThat(response.requiresConfirmation()).isFalse();
            assertThat(response.token()).isNotBlank();
        }

        @Test
        @DisplayName("불가 코드면 토큰 없이 거절된다")
        void checkRejected() {
            when(attendanceMapper.findLatest(TENANT_ID, USER_ID)).thenReturn(null);
            CheckRequest request = new CheckRequest(AttendanceType.OFF_WORK, null, null, null, null, null);

            CheckResponse response = service.check(TENANT_ID, USER_ID, request);

            assertThat(response.allowed()).isFalse();
            assertThat(response.token()).isNull();
            assertThat(response.code()).isEqualTo(ConfirmCode.NOT_WORKING_YET);
        }

        @Test
        @DisplayName("확정시 체크 시점과 데이터가 다르면 변조로 거절된다")
        void confirmDetectsTampering() {
            when(attendanceMapper.findLatest(TENANT_ID, USER_ID)).thenReturn(null);
            CheckRequest checkRequest = new CheckRequest(AttendanceType.GO_TO_WORK, 37.5, 127.0, "서울", "Chrome", null);
            //체크시 저장된 해시를 캡쳐
            final String[] storedHash = new String[1];
            when(attendanceMapper.insertCheck(eq(TENANT_ID), anyString(), eq(USER_ID), anyString(), any()))
                    .thenAnswer(inv -> {
                        storedHash[0] = inv.getArgument(3);
                        return 1;
                    });
            CheckResponse checkResponse = service.check(TENANT_ID, USER_ID, checkRequest);
            when(attendanceMapper.findCheckHash(TENANT_ID, checkResponse.token(), USER_ID)).thenReturn(storedHash[0]);

            //위치 정보를 변조하여 확정 요청
            ConfirmRequest tampered = new ConfirmRequest(checkResponse.token(),
                    AttendanceType.GO_TO_WORK, 35.0, 129.0, "부산", "Chrome", null);

            //예외는 메시지 키를 담고, 실제 문구는 GlobalExceptionHandler가 요청 언어로 해석한다
            assertThatThrownBy(() -> service.confirm(TENANT_ID, USER_ID, tampered))
                    .isInstanceOf(ApiException.class)
                    .satisfies(e -> {
                        ApiException apiException = (ApiException) e;
                        assertThat(apiException.getCode()).isEqualTo("CHECK_MISMATCH");
                        assertThat(apiException.getMessageKey()).isEqualTo("attendance.check.mismatch");
                    });
        }

        @Test
        @DisplayName("확정시 동일 데이터면 스탬프가 등록된다")
        void confirmStamps() {
            when(attendanceMapper.findLatest(TENANT_ID, USER_ID)).thenReturn(null);
            CheckRequest checkRequest = new CheckRequest(AttendanceType.GO_TO_WORK, 37.5, 127.0, "서울", "Chrome", null);
            final String[] storedHash = new String[1];
            when(attendanceMapper.insertCheck(eq(TENANT_ID), anyString(), eq(USER_ID), anyString(), any()))
                    .thenAnswer(inv -> {
                        storedHash[0] = inv.getArgument(3);
                        return 1;
                    });
            CheckResponse checkResponse = service.check(TENANT_ID, USER_ID, checkRequest);
            when(attendanceMapper.findCheckHash(TENANT_ID, checkResponse.token(), USER_ID)).thenReturn(storedHash[0]);

            ConfirmRequest confirm = new ConfirmRequest(checkResponse.token(),
                    AttendanceType.GO_TO_WORK, 37.5, 127.0, "서울", "Chrome", null);
            var response = service.confirm(TENANT_ID, USER_ID, confirm);

            assertThat(response.type()).isEqualTo(AttendanceType.GO_TO_WORK);
            assertThat(response.message()).contains("출근");
        }

        @Test
        @DisplayName("진행 중인 휴식이 있으면 휴식 확정은 휴식 종료로 등록된다")
        void breakToggleEnds() {
            //체크: 휴식 중 + 휴식 요청 -> 허용
            when(attendanceMapper.findLatest(TENANT_ID, USER_ID))
                    .thenReturn(stamp(AttendanceType.BREAK, AttendanceStamp.STATUS_ACTIVE, LocalDateTime.now().minusMinutes(30)));
            final String[] storedHash = new String[1];
            when(attendanceMapper.insertCheck(eq(TENANT_ID), anyString(), eq(USER_ID), anyString(), any()))
                    .thenAnswer(inv -> {
                        storedHash[0] = inv.getArgument(3);
                        return 1;
                    });
            CheckResponse checkResponse = service.check(TENANT_ID, USER_ID,
                    new CheckRequest(AttendanceType.BREAK, null, null, null, null, null));
            when(attendanceMapper.findCheckHash(TENANT_ID, checkResponse.token(), USER_ID)).thenReturn(storedHash[0]);

            final int[] insertedStatus = new int[]{-1};
            when(attendanceMapper.insert(eq(TENANT_ID), anyLong(), eq(AttendanceType.BREAK.code()), anyInt(),
                    any(), any(), any(), any(), any(), any(), any(), any())).thenAnswer(inv -> {
                        insertedStatus[0] = inv.getArgument(3);
                        return 1;
                    });

            service.confirm(TENANT_ID, USER_ID, new ConfirmRequest(checkResponse.token(), AttendanceType.BREAK, null, null, null, null, null));

            assertThat(insertedStatus[0]).isEqualTo(AttendanceStamp.STATUS_BREAK_ENDED);
        }

        @Test
        @DisplayName("확정 시 비고가 스탬프와 함께 저장된다(공백뿐이면 null)")
        void confirmStoresNote() {
            when(attendanceMapper.findLatest(TENANT_ID, USER_ID)).thenReturn(null);
            CheckRequest checkRequest = new CheckRequest(AttendanceType.GO_TO_WORK, 37.5, 127.0, "서울", "Chrome",
                    "  고객 대응으로 특근  ");
            final String[] storedHash = new String[1];
            when(attendanceMapper.insertCheck(eq(TENANT_ID), anyString(), eq(USER_ID), anyString(), any()))
                    .thenAnswer(inv -> {
                        storedHash[0] = inv.getArgument(3);
                        return 1;
                    });
            CheckResponse checkResponse = service.check(TENANT_ID, USER_ID, checkRequest);
            when(attendanceMapper.findCheckHash(TENANT_ID, checkResponse.token(), USER_ID)).thenReturn(storedHash[0]);

            final String[] insertedNote = new String[]{"unset"};
            when(attendanceMapper.insert(eq(TENANT_ID), anyLong(), anyInt(), anyInt(),
                    any(), any(), any(), any(), any(), any(), any(), any())).thenAnswer(inv -> {
                        insertedNote[0] = inv.getArgument(11);
                        return 1;
                    });

            service.confirm(TENANT_ID, USER_ID, new ConfirmRequest(checkResponse.token(),
                    AttendanceType.GO_TO_WORK, 37.5, 127.0, "서울", "Chrome", "  고객 대응으로 특근  "));

            //trim되어 저장(공백만이면 null이 되는 것은 normalizeNote 계약)
            assertThat(insertedNote[0]).isEqualTo("고객 대응으로 특근");
        }

        @Test
        @DisplayName("비고가 체크 시점과 다르면 변조로 거절된다")
        void noteTamperDetected() {
            when(attendanceMapper.findLatest(TENANT_ID, USER_ID)).thenReturn(null);
            final String[] storedHash = new String[1];
            when(attendanceMapper.insertCheck(eq(TENANT_ID), anyString(), eq(USER_ID), anyString(), any()))
                    .thenAnswer(inv -> {
                        storedHash[0] = inv.getArgument(3);
                        return 1;
                    });
            CheckResponse checkResponse = service.check(TENANT_ID, USER_ID,
                    new CheckRequest(AttendanceType.GO_TO_WORK, null, null, null, null, "원래 비고"));
            when(attendanceMapper.findCheckHash(TENANT_ID, checkResponse.token(), USER_ID)).thenReturn(storedHash[0]);

            ConfirmRequest tampered = new ConfirmRequest(checkResponse.token(),
                    AttendanceType.GO_TO_WORK, null, null, null, null, "바꿔치기한 비고");

            assertThatThrownBy(() -> service.confirm(TENANT_ID, USER_ID, tampered))
                    .isInstanceOf(ApiException.class)
                    .satisfies(e -> assertThat(((ApiException) e).getCode()).isEqualTo("CHECK_MISMATCH"));
        }
    }

    @Nested
    @DisplayName("자동 스탬프 비고")
    class AutoNote {

        @Test
        @DisplayName("본인 AUTO 스탬프의 비고만 갱신된다(trim 적용)")
        void updatesNote() {
            when(attendanceMapper.findAutoById(TENANT_ID, USER_ID, 7L))
                    .thenReturn(stamp(AttendanceType.OFF_WORK, AttendanceStamp.STATUS_ACTIVE, LocalDateTime.now()));

            service.updateAutoNote(TENANT_ID, USER_ID, 7L, "  실수로 중복 등록  ");

            verify(attendanceMapper).updateAutoNote(TENANT_ID, USER_ID, 7L, "실수로 중복 등록");
        }

        @Test
        @DisplayName("공백뿐인 비고는 null로 저장된다(비고 삭제)")
        void blankBecomesNull() {
            when(attendanceMapper.findAutoById(TENANT_ID, USER_ID, 7L))
                    .thenReturn(stamp(AttendanceType.OFF_WORK, AttendanceStamp.STATUS_ACTIVE, LocalDateTime.now()));

            service.updateAutoNote(TENANT_ID, USER_ID, 7L, "   ");

            verify(attendanceMapper).updateAutoNote(TENANT_ID, USER_ID, 7L, null);
        }

        @Test
        @DisplayName("대상이 없으면(타인·MANUAL·미존재) 404")
        void notFound() {
            when(attendanceMapper.findAutoById(TENANT_ID, USER_ID, 99L)).thenReturn(null);

            assertThatThrownBy(() -> service.updateAutoNote(TENANT_ID, USER_ID, 99L, "x"))
                    .isInstanceOf(ApiException.class)
                    .satisfies(e -> assertThat(((ApiException) e).getCode()).isEqualTo("STAMP_NOT_FOUND"));
        }

        @Test
        @DisplayName("마감 승인된 월의 기록에는 비고를 쓸 수 없다")
        void closedMonthRejected() {
            LocalDateTime at = LocalDateTime.now();
            when(attendanceMapper.findAutoById(TENANT_ID, USER_ID, 7L))
                    .thenReturn(stamp(AttendanceType.OFF_WORK, AttendanceStamp.STATUS_ACTIVE, at));
            when(closeMapper.findStatus(TENANT_ID, USER_ID, at.getYear(), at.getMonthValue()))
                    .thenReturn("APPROVED");

            assertThatThrownBy(() -> service.updateAutoNote(TENANT_ID, USER_ID, 7L, "x"))
                    .isInstanceOf(ApiException.class)
                    .satisfies(e -> assertThat(((ApiException) e).getCode()).isEqualTo("MONTH_CLOSED"));
        }
    }

    @Nested
    @DisplayName("상태 조회")
    class StatusQuery {

        @Test
        @DisplayName("기록 없음 -> 출근 대기(오늘 근무 09:00~18:00 동봉 — WSC-S-05 U)")
        void waiting() {
            stubTodaySchedule();
            when(attendanceMapper.findLatest(TENANT_ID, USER_ID)).thenReturn(null);
            StatusResponse response = service.status(TENANT_ID, USER_ID);
            assertThat(response.status()).isEqualTo(WorkStatus.WAITING);
            assertThat(response.stampedAt()).isNull();
            assertThat(response.todayScheduleStart()).isEqualTo("09:00");
            assertThat(response.todayScheduleEnd()).isEqualTo("18:00");
        }

        @Test
        @DisplayName("정기 스케줄(10:00~19:00)이 있으면 오늘 근무에 반영된다")
        void personalDefaultsInStatus() {
            when(scheduleMapper.findBetween(eq(TENANT_ID), eq(USER_ID), any(), any()))
                    .thenReturn(java.util.List.of());
            when(holidayMapper.findHolidaysBetween(eq(TENANT_ID), any(), any())).thenReturn(java.util.List.of());
            //요일 무관하게 정기 스케줄 반영을 검증(주말 실행 시 dayOff로 빠지는 것 방지) — 전 요일 근무
            when(patternMapper.findByUser(TENANT_ID, USER_ID)).thenReturn(allDayPattern());
            when(patternMapper.findSlots(PATTERN_ID))
                    .thenReturn(allDaySlots(java.time.LocalTime.of(10, 0), java.time.LocalTime.of(19, 0)));
            when(attendanceMapper.findLatest(TENANT_ID, USER_ID)).thenReturn(null);

            StatusResponse response = service.status(TENANT_ID, USER_ID);

            assertThat(response.todayScheduleStart()).isEqualTo("10:00");
            assertThat(response.todayScheduleEnd()).isEqualTo("19:00");
        }

        @Test
        @DisplayName("오늘이 공휴일이면 오늘 근무는 null/null(비표시)")
        void holidayTodayHidesSchedule() {
            when(scheduleMapper.findBetween(eq(TENANT_ID), eq(USER_ID), any(), any()))
                    .thenReturn(java.util.List.of());
            java.time.LocalDate today = java.time.LocalDate.now();
            when(holidayMapper.findHolidaysBetween(eq(TENANT_ID), any(), any())).thenReturn(java.util.List.of(
                    new com.attendance.pro.holiday.Holiday(1L, TENANT_ID, today, "삼일절",
                            com.attendance.pro.holiday.HolidayType.NATIONAL, false,
                            LocalDateTime.now(), LocalDateTime.now())));
            when(attendanceMapper.findLatest(TENANT_ID, USER_ID)).thenReturn(null);

            StatusResponse response = service.status(TENANT_ID, USER_ID);

            assertThat(response.todayScheduleStart()).isNull();
            assertThat(response.todayScheduleEnd()).isNull();
        }

        @Test
        @DisplayName("출근 -> 출근 중, 24시간 경과시 퇴근 알림")
        void working() {
            stubTodaySchedule();
            when(attendanceMapper.findLatest(TENANT_ID, USER_ID))
                    .thenReturn(stamp(AttendanceType.GO_TO_WORK, 0, LocalDateTime.now().minusHours(2)));
            assertThat(service.status(TENANT_ID, USER_ID).status()).isEqualTo(WorkStatus.WORKING);
            assertThat(service.status(TENANT_ID, USER_ID).alert()).isNull();

            when(attendanceMapper.findLatest(TENANT_ID, USER_ID))
                    .thenReturn(stamp(AttendanceType.GO_TO_WORK, 0, LocalDateTime.now().minusHours(30)));
            StatusResponse overdue = service.status(TENANT_ID, USER_ID);
            assertThat(overdue.status()).isEqualTo(WorkStatus.WORKING);
            assertThat(overdue.alert()).isEqualTo(StatusAlert.OVERDUE_OFF_WORK);
        }

        @Test
        @DisplayName("어제 퇴근 기록만 있으면 출근 대기")
        void offWorkYesterday() {
            stubTodaySchedule();
            when(attendanceMapper.findLatest(TENANT_ID, USER_ID))
                    .thenReturn(stamp(AttendanceType.OFF_WORK, 0, LocalDateTime.now().minusHours(26)));
            assertThat(service.status(TENANT_ID, USER_ID).status()).isEqualTo(WorkStatus.WAITING);
        }

        @Test
        @DisplayName("오늘 퇴근 -> 퇴근 완료")
        void offWorkToday() {
            stubTodaySchedule();
            LocalDateTime today = LocalDateTime.now().withHour(0).withMinute(30);
            when(attendanceMapper.findLatest(TENANT_ID, USER_ID))
                    .thenReturn(stamp(AttendanceType.OFF_WORK, 0, today));
            StatusResponse response = service.status(TENANT_ID, USER_ID);
            assertThat(response.status()).isEqualTo(WorkStatus.OFF_WORK_DONE);
            assertThat(response.stampedAt()).isEqualTo(today);
        }

        @Test
        @DisplayName("휴식 중 / 휴식 종료 상태 매핑")
        void breakStatus() {
            stubTodaySchedule();
            when(attendanceMapper.findLatest(TENANT_ID, USER_ID))
                    .thenReturn(stamp(AttendanceType.BREAK, 0, LocalDateTime.now().minusMinutes(10)));
            assertThat(service.status(TENANT_ID, USER_ID).status()).isEqualTo(WorkStatus.ON_BREAK);

            when(attendanceMapper.findLatest(TENANT_ID, USER_ID))
                    .thenReturn(stamp(AttendanceType.BREAK, 1, LocalDateTime.now().minusMinutes(10)));
            LocalDateTime goTime = LocalDateTime.now().minusHours(3);
            when(attendanceMapper.findLatestGoToWork(TENANT_ID, USER_ID))
                    .thenReturn(stamp(AttendanceType.GO_TO_WORK, 0, goTime));
            StatusResponse response = service.status(TENANT_ID, USER_ID);
            assertThat(response.status()).isEqualTo(WorkStatus.BREAK_ENDED);
            assertThat(response.stampedAt()).isEqualTo(goTime);
        }
    }

    @Nested
    @DisplayName("월별 상세(monthly) — 스케줄/공휴일/정책 합성")
    class MonthlyQuery {

        private void stubMonthly() {
            //일부 테스트가 개별 스텁으로 덮어쓰므로 기본값은 lenient
            org.mockito.Mockito.lenient().when(scheduleMapper.findBetween(eq(TENANT_ID), eq(USER_ID), any(), any()))
                    .thenReturn(java.util.List.of());
            org.mockito.Mockito.lenient().when(holidayMapper.findHolidaysBetween(eq(TENANT_ID), any(), any()))
                    .thenReturn(java.util.List.of());
            org.mockito.Mockito.lenient().when(attendanceMapper.findBetween(eq(TENANT_ID), eq(USER_ID), any(), any()))
                    .thenReturn(java.util.List.of());
            //정기 패턴 09:00~18:00(전 요일) — 스케줄 단일화 후 근무일·시각은 실효 스케줄에서
            org.mockito.Mockito.lenient().when(patternMapper.findByUser(TENANT_ID, USER_ID))
                    .thenReturn(allDayPattern());
            org.mockito.Mockito.lenient().when(patternMapper.findSlots(PATTERN_ID))
                    .thenReturn(allDaySlots(java.time.LocalTime.of(9, 0), java.time.LocalTime.of(18, 0)));
            org.mockito.Mockito.lenient().when(tenantMapper.findById(TENANT_ID))
                    .thenReturn(new com.attendance.pro.tenant.Tenant(
                            TENANT_ID, "ACME", "에이크미(주)", "KR",
                            com.attendance.pro.tenant.TenantStatus.ACTIVE, LocalDateTime.now()));
        }

        @Test
        @DisplayName("ISO-15: monthly가 패턴/공휴일/테넌트 조회에 세션 tenantId를 전달한다")
        void monthlyPropagatesTenantId() {
            stubMonthly();

            service.monthly(TENANT_ID, USER_ID, 2026, 7);

            verify(patternMapper).findByUser(eq(TENANT_ID), eq(USER_ID));
            verify(holidayMapper).findHolidaysBetween(eq(TENANT_ID), any(), any());
            verify(tenantMapper).findById(eq(TENANT_ID));
        }

        @Test
        @DisplayName("CALC-09: 월 합계는 workMinutes non-null 합산")
        void monthlyTotalSumsNonNull() {
            stubMonthly();
            when(attendanceMapper.findBetween(eq(TENANT_ID), eq(USER_ID), any(), any()))
                    .thenReturn(java.util.List.of(
                            new AttendanceStamp(1L, USER_ID, AttendanceType.GO_TO_WORK.code(), 0,
                                    LocalDateTime.of(2026, 7, 1, 9, 0), StampSource.AUTO, null, null),
                            new AttendanceStamp(2L, USER_ID, AttendanceType.OFF_WORK.code(), 0,
                                    LocalDateTime.of(2026, 7, 1, 18, 0), StampSource.AUTO, null, null),
                            new AttendanceStamp(3L, USER_ID, AttendanceType.GO_TO_WORK.code(), 0,
                                    LocalDateTime.of(2026, 7, 2, 9, 0), StampSource.AUTO, null, null),
                            new AttendanceStamp(4L, USER_ID, AttendanceType.OFF_WORK.code(), 0,
                                    LocalDateTime.of(2026, 7, 2, 13, 0), StampSource.AUTO, null, null)));

            var response = service.monthly(TENANT_ID, USER_ID, 2026, 7);

            //7/1: 540-60=480, 7/2: 240-60=180(휴식 미기록·KR 9h 스케줄), 그 외 null
            assertThat(response.totalWorkMinutes()).isEqualTo(660);
        }

        @Test
        @DisplayName("HOL-06(연계): 공휴일 날짜는 days에 holidayName 동봉")
        void monthlyCarriesHolidayName() {
            stubMonthly();
            when(holidayMapper.findHolidaysBetween(eq(TENANT_ID), any(), any()))
                    .thenReturn(java.util.List.of(new com.attendance.pro.holiday.Holiday(
                            1L, TENANT_ID, java.time.LocalDate.of(2026, 7, 17), "제헌절",
                            com.attendance.pro.holiday.HolidayType.NATIONAL, false,
                            LocalDateTime.now(), LocalDateTime.now())));

            var response = service.monthly(TENANT_ID, USER_ID, 2026, 7);

            var day17 = response.days().get(16);
            assertThat(day17.holiday()).isTrue();
            assertThat(day17.holidayName()).isEqualTo("제헌절");
        }

        @Test
        @DisplayName("JP 테넌트는 JP 정책으로 산출(6h 스케줄 → 법정휴게 0 — WSC-S-04 U)")
        void jpTenantUsesJpPolicy() {
            stubMonthly();
            when(tenantMapper.findById(TENANT_ID)).thenReturn(new com.attendance.pro.tenant.Tenant(
                    TENANT_ID, "ACME", "에이크미(주)", "JP",
                    com.attendance.pro.tenant.TenantStatus.ACTIVE, LocalDateTime.now()));
            //6시간 정각 스케줄(09:00~15:00) → JP 법정휴게 0
            when(patternMapper.findSlots(PATTERN_ID))
                    .thenReturn(allDaySlots(java.time.LocalTime.of(9, 0), java.time.LocalTime.of(15, 0)));

            var response = service.monthly(TENANT_ID, USER_ID, 2026, 7);

            assertThat(response.days().get(0).statutoryBreakMinutes()).isEqualTo(0);
        }
    }


    @Nested
    @DisplayName("서비스 tenantId 전파 (ISO-14)")
    class TenantIdPropagation {

        @Test
        @DisplayName("ISO-14a: status가 세션의 tenantId를 매퍼에 그대로 전달한다")
        void statusPropagatesTenantId() {
            when(attendanceMapper.findLatest(TENANT_ID, USER_ID)).thenReturn(null);
            service.status(TENANT_ID, USER_ID);
            verify(attendanceMapper).findLatest(eq(TENANT_ID), eq(USER_ID));
        }

        @Test
        @DisplayName("ISO-14b: check가 조회/토큰 저장 모두 tenantId 스코프로 호출한다")
        void checkPropagatesTenantId() {
            when(attendanceMapper.findLatest(TENANT_ID, USER_ID)).thenReturn(null);
            service.check(TENANT_ID, USER_ID, new CheckRequest(AttendanceType.GO_TO_WORK, null, null, null, null, null));
            verify(attendanceMapper).findLatest(eq(TENANT_ID), eq(USER_ID));
            verify(attendanceMapper).insertCheck(eq(TENANT_ID), anyString(), eq(USER_ID), anyString(), any());
        }

        @Test
        @DisplayName("ISO-14c: confirm이 토큰 조회/삭제/스탬프 등록 모두 tenantId 스코프로 호출한다")
        void confirmPropagatesTenantId() {
            when(attendanceMapper.findLatest(TENANT_ID, USER_ID)).thenReturn(null);
            final String[] storedHash = new String[1];
            when(attendanceMapper.insertCheck(eq(TENANT_ID), anyString(), eq(USER_ID), anyString(), any()))
                    .thenAnswer(inv -> {
                        storedHash[0] = inv.getArgument(3);
                        return 1;
                    });
            CheckResponse checkResponse = service.check(TENANT_ID, USER_ID,
                    new CheckRequest(AttendanceType.GO_TO_WORK, null, null, null, null, null));
            when(attendanceMapper.findCheckHash(TENANT_ID, checkResponse.token(), USER_ID))
                    .thenReturn(storedHash[0]);

            service.confirm(TENANT_ID, USER_ID,
                    new ConfirmRequest(checkResponse.token(), AttendanceType.GO_TO_WORK, null, null, null, null, null));

            verify(attendanceMapper).findCheckHash(eq(TENANT_ID), anyString(), eq(USER_ID));
            verify(attendanceMapper).deleteCheck(eq(TENANT_ID), anyString(), eq(USER_ID));
            verify(attendanceMapper).insert(eq(TENANT_ID), eq(USER_ID), anyInt(), anyInt(),
                    any(), any(), any(), any(), any(), eq(StampSource.AUTO), any(), any());
        }

        @Test
        @DisplayName("타 테넌트에서 발급된 토큰은 해시 조회가 null → 변조로 거절(크로스 테넌트 토큰 방어)")
        void crossTenantTokenRejected() {
            //세션 테넌트(TENANT_ID)로 조회하면 타 테넌트 토큰은 존재하지 않는 것으로 처리된다
            when(attendanceMapper.findCheckHash(eq(TENANT_ID), anyString(), eq(USER_ID))).thenReturn(null);
            ConfirmRequest request = new ConfirmRequest("other-tenant-token",
                    AttendanceType.GO_TO_WORK, null, null, null, null, null);
            assertThatThrownBy(() -> service.confirm(TENANT_ID, USER_ID, request))
                    .isInstanceOf(ApiException.class)
                    .satisfies(e -> assertThat(((ApiException) e).getCode()).isEqualTo("CHECK_MISMATCH"));
        }
    }

}
