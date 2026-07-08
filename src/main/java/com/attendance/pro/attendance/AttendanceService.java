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
import java.util.Set;
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
import com.attendance.pro.attendance.AttendanceDtos.MonthlyResponse;
import com.attendance.pro.attendance.AttendanceDtos.StampResponse;
import com.attendance.pro.attendance.AttendanceDtos.StatusAlert;
import com.attendance.pro.attendance.AttendanceDtos.StatusResponse;
import com.attendance.pro.attendance.AttendanceDtos.WorkStatus;
import com.attendance.pro.common.ApiException;

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
    private final MonthlyAttendanceAssembler assembler = new MonthlyAttendanceAssembler();

    public AttendanceService(AttendanceMapper attendanceMapper, ScheduleMapper scheduleMapper) {
        this.attendanceMapper = attendanceMapper;
        this.scheduleMapper = scheduleMapper;
    }

    /**
     * 출결 체크. 현재 상태에서 요청한 타입이 가능한지 검사한다.
     * 가능하면(확인 필요 포함) 확정용 토큰을 발급한다.
     */
    @Transactional
    public CheckResponse check(long userId, CheckRequest request) {
        attendanceMapper.deleteExpiredChecks();
        AttendanceStamp latest = attendanceMapper.findLatest(userId);
        ConfirmCode code = evaluate(latest, request.type());

        if (code != null && !code.confirmable()) {
            return CheckResponse.rejected(code, rejectMessage(code, latest, request.type()));
        }
        String token = UUID.randomUUID().toString();
        attendanceMapper.insertCheck(token, userId, payloadHash(userId, request),
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
    public StampResponse confirm(long userId, ConfirmRequest request) {
        String storedHash = attendanceMapper.findCheckHash(request.token(), userId);
        if (storedHash == null || !storedHash.equals(payloadHash(userId, request.toCheckRequest()))) {
            throw ApiException.badRequest("CHECK_MISMATCH", "데이터가 올바르지 않습니다. 다시 처리해 주세요.");
        }
        attendanceMapper.deleteCheck(request.token());

        //휴식 스탬프인 경우: 직전 기록이 진행 중인 휴식이면 이번 스탬프는 휴식 종료
        int status = AttendanceStamp.STATUS_ACTIVE;
        if (request.type() == AttendanceType.BREAK) {
            AttendanceStamp latest = attendanceMapper.findLatest(userId);
            if (latest != null && latest.type() == AttendanceType.BREAK
                    && latest.status() == AttendanceStamp.STATUS_ACTIVE) {
                status = AttendanceStamp.STATUS_BREAK_ENDED;
            }
        }
        LocalDateTime now = LocalDateTime.now();
        attendanceMapper.insert(userId, request.type().code(), status, now,
                request.latitude(), request.longitude(), request.placeInfo(), request.terminal());
        log.debug("attendance stamped: userId={}, type={}, status={}", userId, request.type(), status);

        String message = "현재 시간 %s에 %s 하셨습니다.".formatted(
                now.format(DateTimeFormatter.ofPattern("HH:mm")), request.type().label());
        return new StampResponse(request.type(), now, message);
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
        String label = latest == null ? "" : latest.type().label();
        return code.message(label);
    }

    private String rejectMessage(ConfirmCode code, AttendanceStamp latest, AttendanceType requested) {
        //NOT_ON_DUTY는 요청 타입명을, CANNOT_BREAK는 직전 기록 타입명을 표시
        if (code == ConfirmCode.NOT_ON_DUTY) {
            return code.message(requested.label());
        }
        return confirmMessage(code, latest);
    }

    /**
     * 현재 출결 상태(출근 대기/출근 중/퇴근 완료 등) 취득.
     */
    @Transactional(readOnly = true)
    public StatusResponse status(long userId) {
        AttendanceStamp latest = attendanceMapper.findLatest(userId);
        if (latest == null) {
            return StatusResponse.of(WorkStatus.WAITING, null, null);
        }
        LocalDateTime now = LocalDateTime.now();
        long hoursSince = java.time.Duration.between(latest.stampedAt(), now).toHours();
        boolean sameDay = latest.stampedAt().toLocalDate().isEqual(now.toLocalDate());

        switch (latest.type()) {
        case GO_TO_WORK:
            return StatusResponse.of(WorkStatus.WORKING, latest.stampedAt(),
                    hoursSince > OVERDUE_HOURS ? StatusAlert.OVERDUE_OFF_WORK : null);
        case OFF_WORK:
            //퇴근이 오늘이면 퇴근 완료, 아니면 출근 대기
            return sameDay
                    ? StatusResponse.of(WorkStatus.OFF_WORK_DONE, latest.stampedAt(), null)
                    : StatusResponse.of(WorkStatus.WAITING, null, null);
        case EARLY_DEPARTURE:
            return sameDay
                    ? StatusResponse.of(WorkStatus.EARLY_DEPARTURE_DONE, latest.stampedAt(), null)
                    : StatusResponse.of(WorkStatus.WAITING, null, null);
        case BREAK:
            if (latest.status() == AttendanceStamp.STATUS_ACTIVE) {
                return StatusResponse.of(WorkStatus.ON_BREAK, latest.stampedAt(),
                        hoursSince > OVERDUE_HOURS ? StatusAlert.OVERDUE_BREAK_END : null);
            }
            //휴식 종료 상태면 기준 시각은 최근 출근 스탬프
            AttendanceStamp lastGo = attendanceMapper.findLatestGoToWork(userId);
            LocalDateTime baseTime = lastGo == null ? latest.stampedAt() : lastGo.stampedAt();
            long hoursSinceGo = java.time.Duration.between(baseTime, now).toHours();
            return StatusResponse.of(WorkStatus.BREAK_ENDED, baseTime,
                    hoursSinceGo > OVERDUE_HOURS ? StatusAlert.OVERDUE_OFF_WORK : null);
        }
        return StatusResponse.of(WorkStatus.WAITING, null, null);
    }

    /**
     * 월별 출결 상세 취득.
     */
    @Transactional(readOnly = true)
    public MonthlyResponse monthly(long userId, int year, int month) {
        if (month < 1 || month > 12) {
            throw ApiException.badRequest("INVALID_MONTH", "월은 1~12 사이로 입력해 주세요.");
        }
        YearMonth yearMonth = YearMonth.of(year, month);
        LocalDate from = yearMonth.atDay(1);
        LocalDate to = yearMonth.plusMonths(1).atDay(1);

        List<LocalDate> monthDays = from.datesUntil(to).toList();
        Map<LocalDate, WorkSchedule> schedules = scheduleMapper.findBetween(userId, from, to).stream()
                .collect(Collectors.toMap(WorkSchedule::workDate, Function.identity()));
        Set<LocalDate> holidays = Set.copyOf(scheduleMapper.findHolidayDates(from, to));
        //야근(자정 넘긴 퇴근) 판정을 위해 다음달 1일치 스탬프까지 함께 조회
        List<AttendanceStamp> stamps = attendanceMapper.findBetween(userId, from, to.plusDays(1));

        List<DailyAttendance> days = assembler.assemble(monthDays, schedules, holidays, stamps);
        return new MonthlyResponse(year, month, days);
    }

    /**
     * 체크/확정 데이터의 변조 탐지용 SHA-256 해시.
     */
    private String payloadHash(long userId, CheckRequest request) {
        String canonical = userId + "|" + request.type().code()
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
