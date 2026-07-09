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

    private AttendanceService service;

    @BeforeEach
    void setUp() {
        //메시지는 실제 번들로 해석하고, 한국어 기준으로 검증한다
        LocaleContextHolder.setLocale(Locale.KOREAN);
        service = new AttendanceService(attendanceMapper, scheduleMapper, realMessages());
    }

    static Messages realMessages() {
        ResourceBundleMessageSource messageSource = new ResourceBundleMessageSource();
        messageSource.setBasename("messages/messages");
        messageSource.setDefaultEncoding("UTF-8");
        messageSource.setFallbackToSystemLocale(false);
        return new Messages(messageSource);
    }

    private AttendanceStamp stamp(AttendanceType type, int status, LocalDateTime at) {
        return new AttendanceStamp(1L, USER_ID, type.code(), status, at);
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
            CheckRequest request = new CheckRequest(AttendanceType.GO_TO_WORK, 37.5, 127.0, "서울", "Chrome");

            CheckResponse response = service.check(TENANT_ID, USER_ID, request);

            assertThat(response.allowed()).isTrue();
            assertThat(response.requiresConfirmation()).isFalse();
            assertThat(response.token()).isNotBlank();
        }

        @Test
        @DisplayName("불가 코드면 토큰 없이 거절된다")
        void checkRejected() {
            when(attendanceMapper.findLatest(TENANT_ID, USER_ID)).thenReturn(null);
            CheckRequest request = new CheckRequest(AttendanceType.OFF_WORK, null, null, null, null);

            CheckResponse response = service.check(TENANT_ID, USER_ID, request);

            assertThat(response.allowed()).isFalse();
            assertThat(response.token()).isNull();
            assertThat(response.code()).isEqualTo(ConfirmCode.NOT_WORKING_YET);
        }

        @Test
        @DisplayName("확정시 체크 시점과 데이터가 다르면 변조로 거절된다")
        void confirmDetectsTampering() {
            when(attendanceMapper.findLatest(TENANT_ID, USER_ID)).thenReturn(null);
            CheckRequest checkRequest = new CheckRequest(AttendanceType.GO_TO_WORK, 37.5, 127.0, "서울", "Chrome");
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
                    AttendanceType.GO_TO_WORK, 35.0, 129.0, "부산", "Chrome");

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
            CheckRequest checkRequest = new CheckRequest(AttendanceType.GO_TO_WORK, 37.5, 127.0, "서울", "Chrome");
            final String[] storedHash = new String[1];
            when(attendanceMapper.insertCheck(eq(TENANT_ID), anyString(), eq(USER_ID), anyString(), any()))
                    .thenAnswer(inv -> {
                        storedHash[0] = inv.getArgument(3);
                        return 1;
                    });
            CheckResponse checkResponse = service.check(TENANT_ID, USER_ID, checkRequest);
            when(attendanceMapper.findCheckHash(TENANT_ID, checkResponse.token(), USER_ID)).thenReturn(storedHash[0]);

            ConfirmRequest confirm = new ConfirmRequest(checkResponse.token(),
                    AttendanceType.GO_TO_WORK, 37.5, 127.0, "서울", "Chrome");
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
                    new CheckRequest(AttendanceType.BREAK, null, null, null, null));
            when(attendanceMapper.findCheckHash(TENANT_ID, checkResponse.token(), USER_ID)).thenReturn(storedHash[0]);

            final int[] insertedStatus = new int[]{-1};
            when(attendanceMapper.insert(eq(TENANT_ID), anyLong(), eq(AttendanceType.BREAK.code()), anyInt(),
                    any(), any(), any(), any(), any())).thenAnswer(inv -> {
                        insertedStatus[0] = inv.getArgument(3);
                        return 1;
                    });

            service.confirm(TENANT_ID, USER_ID, new ConfirmRequest(checkResponse.token(), AttendanceType.BREAK, null, null, null, null));

            assertThat(insertedStatus[0]).isEqualTo(AttendanceStamp.STATUS_BREAK_ENDED);
        }
    }

    @Nested
    @DisplayName("상태 조회")
    class StatusQuery {

        @Test
        @DisplayName("기록 없음 -> 출근 대기")
        void waiting() {
            when(attendanceMapper.findLatest(TENANT_ID, USER_ID)).thenReturn(null);
            StatusResponse response = service.status(TENANT_ID, USER_ID);
            assertThat(response.status()).isEqualTo(WorkStatus.WAITING);
            assertThat(response.stampedAt()).isNull();
        }

        @Test
        @DisplayName("출근 -> 출근 중, 24시간 경과시 퇴근 알림")
        void working() {
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
            when(attendanceMapper.findLatest(TENANT_ID, USER_ID))
                    .thenReturn(stamp(AttendanceType.OFF_WORK, 0, LocalDateTime.now().minusHours(26)));
            assertThat(service.status(TENANT_ID, USER_ID).status()).isEqualTo(WorkStatus.WAITING);
        }

        @Test
        @DisplayName("오늘 퇴근 -> 퇴근 완료")
        void offWorkToday() {
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
            service.check(TENANT_ID, USER_ID, new CheckRequest(AttendanceType.GO_TO_WORK, null, null, null, null));
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
                    new CheckRequest(AttendanceType.GO_TO_WORK, null, null, null, null));
            when(attendanceMapper.findCheckHash(TENANT_ID, checkResponse.token(), USER_ID))
                    .thenReturn(storedHash[0]);

            service.confirm(TENANT_ID, USER_ID,
                    new ConfirmRequest(checkResponse.token(), AttendanceType.GO_TO_WORK, null, null, null, null));

            verify(attendanceMapper).findCheckHash(eq(TENANT_ID), anyString(), eq(USER_ID));
            verify(attendanceMapper).deleteCheck(eq(TENANT_ID), anyString(), eq(USER_ID));
            verify(attendanceMapper).insert(eq(TENANT_ID), eq(USER_ID), anyInt(), anyInt(),
                    any(), any(), any(), any(), any());
        }

        @Test
        @DisplayName("타 테넌트에서 발급된 토큰은 해시 조회가 null → 변조로 거절(크로스 테넌트 토큰 방어)")
        void crossTenantTokenRejected() {
            //세션 테넌트(TENANT_ID)로 조회하면 타 테넌트 토큰은 존재하지 않는 것으로 처리된다
            when(attendanceMapper.findCheckHash(eq(TENANT_ID), anyString(), eq(USER_ID))).thenReturn(null);
            ConfirmRequest request = new ConfirmRequest("other-tenant-token",
                    AttendanceType.GO_TO_WORK, null, null, null, null);
            assertThatThrownBy(() -> service.confirm(TENANT_ID, USER_ID, request))
                    .isInstanceOf(ApiException.class)
                    .satisfies(e -> assertThat(((ApiException) e).getCode()).isEqualTo("CHECK_MISMATCH"));
        }
    }

}
