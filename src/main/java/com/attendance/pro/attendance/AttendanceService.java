package com.attendance.pro.attendance;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.attendance.pro.attendance.AttendanceDtos.CheckRequest;
import com.attendance.pro.attendance.AttendanceDtos.CheckResponse;
import com.attendance.pro.attendance.AttendanceDtos.ConfirmRequest;
import com.attendance.pro.attendance.AttendanceDtos.DailyAttendance;
import com.attendance.pro.attendance.AttendanceDtos.DailyResponse;
import com.attendance.pro.attendance.AttendanceDtos.DailyStampEntry;
import com.attendance.pro.attendance.AttendanceDtos.ManualStampRequest;
import com.attendance.pro.attendance.AttendanceDtos.MonthlyResponse;
import com.attendance.pro.attendance.AttendanceDtos.StampResponse;
import com.attendance.pro.attendance.AttendanceDtos.StatusAlert;
import com.attendance.pro.attendance.AttendanceDtos.StatusResponse;
import com.attendance.pro.attendance.AttendanceDtos.WorkStatus;
import com.attendance.pro.common.ApiException;
import com.attendance.pro.common.Messages;
import com.attendance.pro.holiday.Holiday;
import com.attendance.pro.holiday.HolidayMapper;
import com.attendance.pro.tenant.ProfileCountry;
import com.attendance.pro.tenant.Tenant;
import com.attendance.pro.tenant.TenantMapper;

/**
 * 출결 처리 서비스.
 *
 * 출결 등록은 2단계로 처리한다.
 * <ol>
 *   <li>체크: 현재 상태에서 요청한 타입이 가능한지 검사하고, 요청 데이터의 해시를 토큰과 함께 저장</li>
 *   <li>확정: 토큰과 함께 동일한 데이터를 다시 받아 해시를 비교(변조 탐지)한 후 스탬프 등록</li>
 * </ol>
 */
@Service
public class AttendanceService {

    private static final Logger log = LoggerFactory.getLogger(AttendanceService.class);

    /** 상태 알림 기준(시간) */
    private static final long OVERDUE_HOURS = 24;

    private final AttendanceMapper attendanceMapper;
    private final ScheduleMapper scheduleMapper;
    private final HolidayMapper holidayMapper;
    private final TenantMapper tenantMapper;
    private final Messages messages;
    private final MonthlyAttendanceAssembler assembler = new MonthlyAttendanceAssembler();

    public AttendanceService(AttendanceMapper attendanceMapper, ScheduleMapper scheduleMapper,
            HolidayMapper holidayMapper, TenantMapper tenantMapper, Messages messages) {
        this.attendanceMapper = attendanceMapper;
        this.scheduleMapper = scheduleMapper;
        this.holidayMapper = holidayMapper;
        this.tenantMapper = tenantMapper;
        this.messages = messages;
    }

    /**
     * 출결 체크. 현재 상태에서 요청한 타입이 가능한지 검사한다.
     * 가능하면(확인 필요 포함) 확정용 토큰을 발급한다.
     */
    @Transactional
    public CheckResponse check(long tenantId, long userId, CheckRequest request) {
        attendanceMapper.deleteExpiredChecks();
        AttendanceStamp latest = attendanceMapper.findLatest(tenantId, userId);
        ConfirmCode code = evaluate(latest, request.type());

        if (code != null && !code.confirmable()) {
            return CheckResponse.rejected(code, rejectMessage(code, latest, request.type()));
        }
        String token = UUID.randomUUID().toString();
        attendanceMapper.insertCheck(tenantId, token, userId, payloadHash(tenantId, userId, request),
                code == null ? null : code.code());
        if (code == null) {
            return CheckResponse.ok(token);
        }
        return CheckResponse.needsConfirmation(code, confirmMessage(code, latest), token);
    }

    /**
     * 출결 확정. 체크 시점과 데이터가 동일한지 해시로 검증한 후 스탬프를 등록한다.
     */
    @Transactional
    public StampResponse confirm(long tenantId, long userId, ConfirmRequest request) {
        String storedHash = attendanceMapper.findCheckHash(tenantId, request.token(), userId);
        if (storedHash == null || !storedHash.equals(payloadHash(tenantId, userId, request.toCheckRequest()))) {
            throw ApiException.badRequest("CHECK_MISMATCH", "attendance.check.mismatch");
        }
        attendanceMapper.deleteCheck(tenantId, request.token(), userId);

        //휴식 스탬프인 경우: 직전 기록이 진행 중인 휴식이면 이번 스탬프는 휴식 종료
        int status = AttendanceStamp.STATUS_ACTIVE;
        if (request.type() == AttendanceType.BREAK) {
            AttendanceStamp latest = attendanceMapper.findLatest(tenantId, userId);
            if (latest != null && latest.type() == AttendanceType.BREAK
                    && latest.status() == AttendanceStamp.STATUS_ACTIVE) {
                status = AttendanceStamp.STATUS_BREAK_ENDED;
            }
        }
        LocalDateTime now = LocalDateTime.now();
        attendanceMapper.insert(tenantId, userId, request.type().code(), status, now,
                request.latitude(), request.longitude(), request.placeInfo(), request.terminal(),
                StampSource.AUTO, null, null);
        log.debug("attendance stamped: userId={}, type={}, status={}", userId, request.type(), status);

        String message = messages.get("attendance.stamp.success",
                now.format(DateTimeFormatter.ofPattern("HH:mm")), messages.get(request.type().labelKey()));
        return new StampResponse(request.type(), now, message);
    }

    /** 수동 정정으로 소급 등록 가능한 최대 일수 */
    private static final long MANUAL_MAX_DAYS = 90;

    /**
     * 수동 정정 등록(Phase 5 — manual-attendance §3).
     * 상태머신 검사 없음(정정 목적) — attendance는 append-only라 원래 스탬프도 행으로 남고,
     * 채용 규칙(마지막 값 우선)은 월별 조립기의 기존 규칙을 그대로 따른다.
     * BREAK는 시작/종료 페어링 정합성 문제로 대상 외(400).
     */
    @Transactional
    public StampResponse manual(long tenantId, long userId, ManualStampRequest request) {
        if (request.type() == AttendanceType.BREAK) {
            throw ApiException.badRequest("MANUAL_TYPE_INVALID", "attendance.manual.type.invalid");
        }
        ManualReason reason = ManualReason.of(request.reasonCode());
        if (reason == null) {
            throw ApiException.badRequest("MANUAL_REASON_UNSUPPORTED", "attendance.manual.reason.unsupported");
        }
        String reasonText = request.reasonText() == null ? null : request.reasonText().trim();
        if (reasonText != null && reasonText.isEmpty()) {
            reasonText = null;
        }
        if (reason == ManualReason.OTHER && reasonText == null) {
            //기타는 자유 텍스트가 곧 사유 — 없으면 정정 근거가 남지 않는다
            throw ApiException.badRequest("MANUAL_REASON_TEXT_REQUIRED", "attendance.manual.reason.text-required");
        }
        LocalDateTime stampedAt = LocalDateTime.of(request.date(),
                java.time.LocalTime.parse(request.time()));
        LocalDateTime now = LocalDateTime.now();
        if (stampedAt.isAfter(now)) {
            throw ApiException.badRequest("MANUAL_FUTURE", "attendance.manual.future");
        }
        if (request.date().isBefore(now.toLocalDate().minusDays(MANUAL_MAX_DAYS))) {
            throw ApiException.badRequest("MANUAL_TOO_OLD", "attendance.manual.too-old");
        }
        //좌표·장소 없음 — 위치는 자동 스탬프의 무결성 장치이지 정정의 입력이 아니다(§3)
        attendanceMapper.insert(tenantId, userId, request.type().code(), AttendanceStamp.STATUS_ACTIVE,
                stampedAt, null, null, null, "manual", StampSource.MANUAL, reason.name(), reasonText);
        log.info("manual attendance stamped: userId={}, type={}, at={}, reason={}",
                userId, request.type(), stampedAt, reason);

        String message = messages.get("attendance.manual.success",
                stampedAt.format(DateTimeFormatter.ofPattern("MM-dd HH:mm")),
                messages.get(request.type().labelKey()));
        return new StampResponse(request.type(), stampedAt, message);
    }

    /**
     * 일자 스탬프 이력(본인) — 그 달력 날짜의 전 스탬프.
     * attendance는 append-only이므로 중복 스탬프(출근 2번 등)·수동 정정이 전부 나온다(§3).
     */
    @Transactional(readOnly = true)
    public DailyResponse daily(long tenantId, long userId, LocalDate date) {
        List<DailyStampEntry> entries = attendanceMapper
                .findBetween(tenantId, userId, date, date.plusDays(1)).stream()
                .map(stamp -> new DailyStampEntry(stamp.stampedAt(), stamp.type(),
                        stamp.type() == AttendanceType.BREAK
                                && stamp.status() == AttendanceStamp.STATUS_BREAK_ENDED,
                        stamp.source(), stamp.reasonCode(), stamp.reasonText()))
                .toList();
        return new DailyResponse(date, entries);
    }

    /**
     * 출결 체크 규칙(기존 시스템의 err_cd 1~8 규칙을 계승).
     *
     * @return 문제 없으면 null, 확인 필요/불가면 해당 코드
     */
    ConfirmCode evaluate(AttendanceStamp latest, AttendanceType requested) {
        //최근(48시간 이내) 기록이 없으면 출근만 허용
        if (latest == null) {
            return requested == AttendanceType.GO_TO_WORK ? null : ConfirmCode.NOT_WORKING_YET;
        }
        AttendanceType latestType = latest.type();

        //같은 타입 반복(휴식 제외)은 덮어쓰기 확인
        if (latestType == requested) {
            switch (requested) {
            case GO_TO_WORK:
                return ConfirmCode.ALREADY_WORKING;
            case OFF_WORK:
                return ConfirmCode.ALREADY_OFF_WORK;
            case EARLY_DEPARTURE:
                return ConfirmCode.ALREADY_EARLY_DEPARTURE;
            case BREAK:
                break; //휴식 반복은 시작/종료 토글이므로 허용
            }
        }

        switch (requested) {
        case GO_TO_WORK:
            //같은 날 퇴근/조퇴 완료 후의 출근은 재출근 확인
            if ((latestType == AttendanceType.OFF_WORK || latestType == AttendanceType.EARLY_DEPARTURE)
                    && latest.stampedAt().toLocalDate().isEqual(LocalDate.now())) {
                return ConfirmCode.RE_ATTEND;
            }
            //휴식 기록 상태에서는 재출근 불가
            if (latestType == AttendanceType.BREAK) {
                return ConfirmCode.ON_BREAK_CANNOT_ATTEND;
            }
            return null;
        case OFF_WORK:
        case EARLY_DEPARTURE:
            //출근 중(또는 휴식 종료 후)이어야 퇴근/조퇴 가능
            if (latestType == AttendanceType.GO_TO_WORK
                    || (latestType == AttendanceType.BREAK && latest.status() == AttendanceStamp.STATUS_BREAK_ENDED)) {
                return null;
            }
            return ConfirmCode.NOT_ON_DUTY;
        case BREAK:
            //출근 중이거나 휴식(시작/종료)이어야 휴식 가능
            if (latestType == AttendanceType.GO_TO_WORK || latestType == AttendanceType.BREAK) {
                return null;
            }
            return ConfirmCode.CANNOT_BREAK;
        }
        return null;
    }

    private String confirmMessage(ConfirmCode code, AttendanceStamp latest) {
        String label = latest == null ? "" : messages.get(latest.type().labelKey());
        return messages.get(code.messageKey(), label);
    }

    private String rejectMessage(ConfirmCode code, AttendanceStamp latest, AttendanceType requested) {
        //NOT_ON_DUTY는 요청 타입명을, CANNOT_BREAK는 직전 기록 타입명을 표시
        if (code == ConfirmCode.NOT_ON_DUTY) {
            return messages.get(code.messageKey(), messages.get(requested.labelKey()));
        }
        return confirmMessage(code, latest);
    }

    /**
     * 현재 출결 상태(출근 대기/출근 중/퇴근 완료 등) 취득.
     */
    @Transactional(readOnly = true)
    public StatusResponse status(long tenantId, long userId) {
        //오늘의 해석된 스케줄(W005 "오늘 근무" 표시 — work-schedule §5-3)
        TodaySchedule today = resolveTodaySchedule(tenantId, userId);
        AttendanceStamp latest = attendanceMapper.findLatest(tenantId, userId);
        if (latest == null) {
            return statusOf(WorkStatus.WAITING, null, null, today);
        }
        LocalDateTime now = LocalDateTime.now();
        long hoursSince = java.time.Duration.between(latest.stampedAt(), now).toHours();
        boolean sameDay = latest.stampedAt().toLocalDate().isEqual(now.toLocalDate());

        switch (latest.type()) {
        case GO_TO_WORK:
            return statusOf(WorkStatus.WORKING, latest.stampedAt(),
                    hoursSince > OVERDUE_HOURS ? StatusAlert.OVERDUE_OFF_WORK : null, today);
        case OFF_WORK:
            //퇴근이 오늘이면 퇴근 완료, 아니면 출근 대기
            return sameDay
                    ? statusOf(WorkStatus.OFF_WORK_DONE, latest.stampedAt(), null, today)
                    : statusOf(WorkStatus.WAITING, null, null, today);
        case EARLY_DEPARTURE:
            return sameDay
                    ? statusOf(WorkStatus.EARLY_DEPARTURE_DONE, latest.stampedAt(), null, today)
                    : statusOf(WorkStatus.WAITING, null, null, today);
        case BREAK:
            if (latest.status() == AttendanceStamp.STATUS_ACTIVE) {
                return statusOf(WorkStatus.ON_BREAK, latest.stampedAt(),
                        hoursSince > OVERDUE_HOURS ? StatusAlert.OVERDUE_BREAK_END : null, today);
            }
            //휴식 종료 상태면 기준 시각은 최근 출근 스탬프
            AttendanceStamp lastGo = attendanceMapper.findLatestGoToWork(tenantId, userId);
            LocalDateTime baseTime = lastGo == null ? latest.stampedAt() : lastGo.stampedAt();
            long hoursSinceGo = java.time.Duration.between(baseTime, now).toHours();
            return statusOf(WorkStatus.BREAK_ENDED, baseTime,
                    hoursSinceGo > OVERDUE_HOURS ? StatusAlert.OVERDUE_OFF_WORK : null, today);
        }
        return statusOf(WorkStatus.WAITING, null, null, today);
    }

    /** 오늘의 해석된 스케줄(휴일이면 null/null). */
    private record TodaySchedule(String start, String end) {
        static final TodaySchedule HOLIDAY = new TodaySchedule(null, null);
    }

    /**
     * 우선순위 해석(work_schedule > 개인 기본값 > 상수 — work-schedule §3-1)을 오늘 1일에 적용.
     */
    private TodaySchedule resolveTodaySchedule(long tenantId, long userId) {
        LocalDate today = LocalDate.now();
        WorkSchedule override = scheduleMapper.findBetween(tenantId, userId, today, today.plusDays(1))
                .stream().findFirst().orElse(null);
        boolean publicHoliday = !holidayMapper.findHolidaysBetween(tenantId, today, today.plusDays(1)).isEmpty();
        if (publicHoliday || (override != null && override.holiday())) {
            return TodaySchedule.HOLIDAY;
        }
        WorkDefaults defaults = scheduleMapper.findWorkDefaults(tenantId, userId);
        //요일 휴무(일자 오버라이드 없음)는 휴일과 동일 표시 — 스탬프 자체는 휴일처럼 가능(§4)
        if (override == null && defaults != null
                && !WorkDefaults.worksOn(defaults.workDays(), today.getDayOfWeek())) {
            return TodaySchedule.HOLIDAY;
        }
        java.time.LocalTime baseStart = defaults != null && defaults.start() != null
                ? defaults.start() : MonthlyAttendanceAssembler.DEFAULT_START;
        java.time.LocalTime baseEnd = defaults != null && defaults.end() != null
                ? defaults.end() : MonthlyAttendanceAssembler.DEFAULT_END;
        java.time.LocalTime start = override != null && override.startTime() != null
                ? override.startTime() : baseStart;
        java.time.LocalTime end = override != null && override.endTime() != null
                ? override.endTime() : baseEnd;
        DateTimeFormatter format = DateTimeFormatter.ofPattern("HH:mm");
        return new TodaySchedule(start.format(format), end.format(format));
    }

    /**
     * 상태 응답 조립(라벨을 요청 로케일로 해석).
     */
    private StatusResponse statusOf(WorkStatus status, LocalDateTime stampedAt, StatusAlert alert,
            TodaySchedule today) {
        return new StatusResponse(status, messages.get(status.labelKey()), stampedAt,
                alert, alert == null ? null : messages.get(alert.labelKey()),
                today.start(), today.end());
    }

    /**
     * 월별 출결 상세 취득.
     */
    @Transactional(readOnly = true)
    public MonthlyResponse monthly(long tenantId, long userId, int year, int month) {
        if (month < 1 || month > 12) {
            throw ApiException.badRequest("INVALID_MONTH", "attendance.month.invalid");
        }
        YearMonth yearMonth = YearMonth.of(year, month);
        LocalDate from = yearMonth.atDay(1);
        LocalDate to = yearMonth.plusMonths(1).atDay(1);

        List<LocalDate> monthDays = from.datesUntil(to).toList();
        Map<LocalDate, WorkSchedule> schedules = scheduleMapper.findBetween(tenantId, userId, from, to).stream()
                .collect(Collectors.toMap(WorkSchedule::workDate, Function.identity()));
        //공휴일은 명칭 포함 Map(판정은 containsKey — 정본: holiday-plan §6, CR3-2)
        Map<LocalDate, String> holidays = holidayMapper.findHolidaysBetween(tenantId, from, to).stream()
                .collect(Collectors.toMap(Holiday::holidayDate, Holiday::holidayName));
        //야근(자정 넘긴 퇴근) 판정을 위해 다음달 1일치 스탬프까지 함께 조회(BREAK 포함)
        List<AttendanceStamp> stamps = attendanceMapper.findBetween(tenantId, userId, from, to.plusDays(1));
        //개인 기본 스케줄 + 테넌트 소재국 법정 휴게 정책(세션 tenantId 전파 — ISO-15)
        WorkDefaults defaults = scheduleMapper.findWorkDefaults(tenantId, userId);
        Tenant tenant = tenantMapper.findById(tenantId);
        ProfileCountry country = tenant == null ? null : ProfileCountry.of(tenant.country());
        //country 미설정/미지원이면 KR로 동작(안전한 실패 — X10)
        BreakPolicy policy = BreakPolicy.of(country == null ? ProfileCountry.KR : country);

        List<DailyAttendance> days = assembler.assemble(monthDays, schedules, holidays, stamps,
                defaults == null ? null : defaults.start(),
                defaults == null ? null : defaults.end(),
                defaults == null ? null : defaults.workDays(),
                policy);
        int totalWorkMinutes = days.stream()
                .map(DailyAttendance::workMinutes)
                .filter(java.util.Objects::nonNull)
                .mapToInt(Integer::intValue)
                .sum();
        return new MonthlyResponse(year, month, days, totalWorkMinutes);
    }

    /**
     * 체크/확정 데이터의 변조 탐지용 SHA-256 해시.
     * tenantId를 계산 입력에 포함한다(심층 방어 — 행이 잘못 매칭되어도 해시 대조에서 한 번 더 실패).
     */
    private String payloadHash(long tenantId, long userId, CheckRequest request) {
        String canonical = tenantId + "|" + userId + "|" + request.type().code()
                + "|" + orEmpty(request.latitude())
                + "|" + orEmpty(request.longitude())
                + "|" + orEmpty(request.placeInfo())
                + "|" + orEmpty(request.terminal());
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(md.digest(canonical.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 algorithm not available", e);
        }
    }

    private String orEmpty(Object value) {
        return value == null ? "" : value.toString();
    }

}
