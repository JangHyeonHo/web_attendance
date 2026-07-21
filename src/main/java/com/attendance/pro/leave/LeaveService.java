package com.attendance.pro.leave;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.attendance.pro.attendance.BreakPolicy;
import com.attendance.pro.common.ApiException;
import com.attendance.pro.holiday.Holiday;
import com.attendance.pro.holiday.HolidayMapper;
import com.attendance.pro.leave.LeaveDtos.LeaveApplyRequest;
import com.attendance.pro.leave.LeaveDtos.LeaveBalanceResponse;
import com.attendance.pro.leave.LeaveDtos.LeaveBalanceRowResponse;
import com.attendance.pro.leave.LeaveDtos.LeaveBulkGrantRequest;
import com.attendance.pro.leave.LeaveDtos.LeaveGrantRequest;
import com.attendance.pro.leave.LeaveDtos.LeaveRequestResponse;
import com.attendance.pro.leave.LeaveDtos.LeaveTypeCreateRequest;
import com.attendance.pro.leave.LeaveDtos.LeaveTypeResponse;
import com.attendance.pro.leave.LeaveDtos.LeaveTypeUpdateRequest;
import com.attendance.pro.leave.LeaveDtos.MemberLeaveDetail;
import com.attendance.pro.leave.LeaveDtos.MemberLeaveSummary;
import com.attendance.pro.leave.LeaveRequestMapper.InsertParam;
import com.attendance.pro.leave.LeaveRequestMapper.LeaveRequestView;
import com.attendance.pro.leave.LeaveTypeMapper.LeaveTypeCreate;
import com.attendance.pro.tenant.ProfileCountry;
import com.attendance.pro.tenant.Tenant;
import com.attendance.pro.tenant.TenantMapper;
import com.attendance.pro.user.Role;
import com.attendance.pro.user.User;
import com.attendance.pro.user.UserMapper;

/**
 * 휴가 서비스 — 종류 관리·부여(법정 자동/수동 조정)·신청·결재.
 *
 * 수량은 전부 <b>분(minutes)</b>. 1일 = 개인 소정근로시간(근무구간 − 법정휴게, {@link #standardDayMinutes}).
 * 잔여 = Σ(유효 grant) − Σ(APPROVED request). PENDING은 신청 시 가용에서 함께 차감해 초과 예약을 막는다.
 * 자세한 결정은 docs/plan/admin-roles-and-leave.md §B-5.
 */
@Service
public class LeaveService {

    private static final int DEFAULT_DAY_MINUTES = 480;

    private final LeaveTypeMapper typeMapper;
    private final LeaveGrantMapper grantMapper;
    private final LeaveRequestMapper requestMapper;
    private final UserMapper userMapper;
    private final TenantMapper tenantMapper;
    private final HolidayMapper holidayMapper;
    private final com.attendance.pro.attendance.ScheduleAdminService scheduleAdminService;
    private final Clock clock;

    @Autowired
    public LeaveService(LeaveTypeMapper typeMapper, LeaveGrantMapper grantMapper,
            LeaveRequestMapper requestMapper, UserMapper userMapper, TenantMapper tenantMapper,
            HolidayMapper holidayMapper,
            com.attendance.pro.attendance.ScheduleAdminService scheduleAdminService) {
        this(typeMapper, grantMapper, requestMapper, userMapper, tenantMapper, holidayMapper,
                scheduleAdminService, Clock.systemDefaultZone());
    }

    LeaveService(LeaveTypeMapper typeMapper, LeaveGrantMapper grantMapper,
            LeaveRequestMapper requestMapper, UserMapper userMapper, TenantMapper tenantMapper,
            HolidayMapper holidayMapper,
            com.attendance.pro.attendance.ScheduleAdminService scheduleAdminService, Clock clock) {
        this.typeMapper = typeMapper;
        this.grantMapper = grantMapper;
        this.requestMapper = requestMapper;
        this.userMapper = userMapper;
        this.tenantMapper = tenantMapper;
        this.holidayMapper = holidayMapper;
        this.scheduleAdminService = scheduleAdminService;
        this.clock = clock;
    }

    // ===== 휴가 종류 =====

    public List<LeaveTypeResponse> listTypes(long tenantId) {
        return typeMapper.findByTenant(tenantId).stream().map(LeaveTypeResponse::of).toList();
    }

    public List<LeaveTypeResponse> listActiveTypes(long tenantId) {
        return typeMapper.findActiveByTenant(tenantId).stream().map(LeaveTypeResponse::of).toList();
    }

    /** 시드용 기본 휴가 종류 한 줄(코드·명칭·유급·승인필요·연차성격·정렬). */
    private record DefaultLeaveType(String code, String name, boolean paid,
            boolean requiresApproval, boolean isAnnual, int sortOrder) {
    }

    /**
     * 국가별 기본 휴가 종류(#10) — 소재국에서 보편적인 휴가를 소재국 언어 명칭으로 시드.
     * 유급휴가(연차)·여름휴가에 더해 경조/병가/생리휴가까지. 명칭·유급여부는 되돌릴 수 있는 기본값
     * (관리자가 종류 화면에서 수정·비활성 가능). 미지원 국가는 KR 기본으로 방어.
     */
    private static List<DefaultLeaveType> defaultTypesFor(String country) {
        if ("JP".equals(country)) {
            return List.of(
                    new DefaultLeaveType("ANNUAL", "有給休暇", true, true, true, 0),
                    new DefaultLeaveType("SUMMER", "夏季休暇", true, true, false, 1),
                    new DefaultLeaveType("CONDOLENCE", "慶弔休暇", true, true, false, 2),
                    new DefaultLeaveType("SICK", "病気休暇", false, true, false, 3),
                    new DefaultLeaveType("MENSTRUAL", "生理休暇", false, true, false, 4));
        }
        //KR(기본)
        return List.of(
                new DefaultLeaveType("ANNUAL", "유급휴가", true, true, true, 0),
                new DefaultLeaveType("SUMMER", "여름휴가", true, true, false, 1),
                new DefaultLeaveType("CONDOLENCE", "경조휴가", true, true, false, 2),
                new DefaultLeaveType("SICK", "병가", false, true, false, 3),
                new DefaultLeaveType("MENSTRUAL", "생리휴가", false, true, false, 4));
    }

    /** 신규 테넌트 국가별 기본 휴가 종류 시드 — 테넌트 생성 Tx에서 호출. 재실행 안전(IGNORE). */
    public void seedDefaults(long tenantId, String country) {
        for (DefaultLeaveType d : defaultTypesFor(country)) {
            typeMapper.seedType(tenantId, d.code(), d.name(), d.paid(),
                    d.requiresApproval(), d.isAnnual(), d.sortOrder());
        }
    }

    @Transactional
    public LeaveTypeResponse createType(long tenantId, LeaveTypeCreateRequest req) {
        //코드는 서버 자동생성(#10) — 사용자는 명칭만 입력. 클라이언트가 코드를 보내도 무시하고 유일 코드를 만든다.
        for (int attempt = 0; attempt < 5; attempt++) {
            LeaveTypeCreate create = new LeaveTypeCreate(tenantId, generateTypeCode(), req.name().trim(),
                    req.paid(), req.unit(), req.hourlyEnabledFlag(), req.requiresApproval(), req.sortOrder());
            try {
                typeMapper.insert(create);
                return LeaveTypeResponse.of(typeMapper.findById(tenantId, create.getLeaveTypeId()));
            } catch (DuplicateKeyException e) {
                //자동 코드가 드물게 충돌하면 재생성해 재시도(명칭 중복은 허용 — 코드만 유일)
            }
        }
        throw ApiException.conflict("LEAVE_TYPE_CODE_DUP", "leave.type.code.duplicated");
    }

    /** 사용자에게 보이지 않는 내부 식별 코드 자동생성(≤30자, 테넌트 내 유일). */
    private static String generateTypeCode() {
        return "LT" + java.util.UUID.randomUUID().toString().replace("-", "").substring(0, 12).toUpperCase();
    }

    @Transactional
    public LeaveTypeResponse updateType(long tenantId, long leaveTypeId, LeaveTypeUpdateRequest req) {
        LeaveType existing = requireType(tenantId, leaveTypeId);
        //연차(내장)는 자동계산·잔여 조회의 기준이므로 단위(DAY)·활성 상태를 잠근다(모순 상태 방지)
        LeaveUnit unit = existing.isAnnual() ? existing.unit() : req.unit();
        boolean active = existing.isAnnual() ? true : req.active();
        typeMapper.update(tenantId, existing.leaveTypeId(), req.name().trim(), req.paid(),
                unit, req.hourlyEnabledFlag(), req.requiresApproval(), active, req.sortOrder());
        return LeaveTypeResponse.of(typeMapper.findById(tenantId, leaveTypeId));
    }

    // ===== 잔여 =====

    /** 멤버 본인/관리자 공통 — 활성 종류별 잔여(요청 목록에서 사용·대기 파생). */
    public List<LeaveBalanceResponse> balances(long tenantId, long userId) {
        User user = requireUser(tenantId, userId);
        ProfileCountry country = countryOf(tenantId);
        int dayMinutes = standardDayMinutes(user, country);
        List<LeaveRequestView> requests = requestMapper.findViewByUser(tenantId, userId);
        return balancesFor(tenantId, userId, dayMinutes, requests);
    }

    private List<LeaveBalanceResponse> balancesFor(long tenantId, long userId, int dayMinutes,
            List<LeaveRequestView> requests) {
        LocalDate today = LocalDate.now(clock);
        List<LeaveBalanceResponse> out = new ArrayList<>();
        //전체 종류를 순회하되 비활성은 부여·사용·대기가 있을 때만 노출(소진 이력 은폐 방지)
        for (LeaveType type : typeMapper.findByTenant(tenantId)) {
            int granted = grantMapper.sumEffectiveMinutes(tenantId, userId, type.leaveTypeId(), today);
            int used = 0;
            int pending = 0;
            for (LeaveRequestView r : requests) {
                if (r.leaveTypeId() != type.leaveTypeId()) {
                    continue;
                }
                //APPROVED와 취소신청중(CANCEL_REQUESTED)은 확정 전까지 잔여를 소진
                if (r.status() == LeaveStatus.APPROVED || r.status() == LeaveStatus.CANCEL_REQUESTED) {
                    used += r.minutes();
                } else if (r.status() == LeaveStatus.PENDING) {
                    pending += r.minutes();
                }
            }
            if (!type.active() && granted == 0 && used == 0 && pending == 0) {
                continue;
            }
            int remaining = granted - used;
            out.add(new LeaveBalanceResponse(type.leaveTypeId(), type.code(), type.name(),
                    type.unit(), type.isAnnual(), granted, used, pending, remaining, dayMinutes));
        }
        return out;
    }

    /**
     * 만기일별 잔여 목록(멤버 잔여 화면) — 부여 행 하나당 남은 분 + 만기일.
     * 사용분(승인·취소신청중)과 음수 조정은 <b>만기 임박순(FIFO)</b>으로 차감해, 먼저 소멸하는
     * 부여부터 소진된 것으로 본다. 남은 분이 0 초과인 행만 반환(만기 임박순).
     */
    public List<LeaveBalanceRowResponse> balanceRows(long tenantId, long userId) {
        User user = requireUser(tenantId, userId);
        ProfileCountry country = countryOf(tenantId);
        int dayMinutes = standardDayMinutes(user, country);
        LocalDate today = LocalDate.now(clock);
        List<LeaveRequestView> requests = requestMapper.findViewByUser(tenantId, userId);
        //만기 임박순 정렬된 활성 부여 행
        Map<Long, List<LeaveGrant>> grantsByType = new LinkedHashMap<>();
        for (LeaveGrant grant : grantMapper.findActiveByUser(tenantId, userId, today)) {
            grantsByType.computeIfAbsent(grant.leaveTypeId(), k -> new ArrayList<>()).add(grant);
        }
        Map<Long, LeaveType> typeById = new LinkedHashMap<>();
        for (LeaveType type : typeMapper.findByTenant(tenantId)) {
            typeById.put(type.leaveTypeId(), type);
        }
        List<LeaveBalanceRowResponse> out = new ArrayList<>();
        for (Map.Entry<Long, List<LeaveGrant>> entry : grantsByType.entrySet()) {
            LeaveType type = typeById.get(entry.getKey());
            if (type == null) {
                continue;
            }
            //차감 풀 = 사용분(승인·취소신청중) + 음수 부여(조정). 대기(PENDING)는 잔여에서 빼지 않는다(집계와 동일).
            int consume = 0;
            for (LeaveRequestView r : requests) {
                if (r.leaveTypeId() == type.leaveTypeId()
                        && (r.status() == LeaveStatus.APPROVED || r.status() == LeaveStatus.CANCEL_REQUESTED)) {
                    consume += r.minutes();
                }
            }
            List<LeaveGrant> positives = new ArrayList<>();
            for (LeaveGrant grant : entry.getValue()) {
                if (grant.minutes() >= 0) {
                    positives.add(grant); //이미 만기 임박순
                } else {
                    consume += -grant.minutes();
                }
            }
            for (LeaveGrant grant : positives) {
                int take = Math.min(grant.minutes(), consume);
                consume -= take;
                int remaining = grant.minutes() - take;
                if (remaining > 0) {
                    out.add(new LeaveBalanceRowResponse(type.leaveTypeId(), type.name(), type.unit(),
                            remaining, grant.expiresOn(), dayMinutes));
                }
            }
        }
        return out;
    }

    // ===== 신청(멤버) =====

    @Transactional
    public LeaveRequestResponse apply(long tenantId, long userId, LeaveApplyRequest req) {
        User user = requireUser(tenantId, userId);
        LeaveType type = requireType(tenantId, req.leaveTypeId());
        if (!type.active()) {
            throw ApiException.badRequest("LEAVE_TYPE_INACTIVE", "leave.type.inactive");
        }
        ProfileCountry country = countryOf(tenantId);
        int dayMinutes = standardDayMinutes(user, country);

        //잔여 검사~기록 직렬화(동시 신청 초과 예약 방지)
        grantMapper.lockByUserType(tenantId, userId, type.leaveTypeId());

        LocalDateTime startAt;
        LocalDateTime endAt;
        int minutes;
        boolean halfDay = false;

        if (req.dayUnit()) {
            LocalDate start = req.startDate();
            LocalDate end = req.endDate() != null ? req.endDate() : start;
            if (start == null || end.isBefore(start)) {
                throw ApiException.badRequest("LEAVE_RANGE", "leave.request.range");
            }
            halfDay = req.isHalfDay();
            if (halfDay && !start.equals(end)) {
                throw ApiException.badRequest("LEAVE_RANGE", "leave.request.range");
            }
            Set<LocalDate> holidays = holidaysBetween(tenantId, start, end);
            int workingDays = countWorkingDays(user, start, end, holidays);
            if (workingDays <= 0) {
                throw ApiException.badRequest("LEAVE_NO_WORKING_DAY", "leave.request.no-working-day");
            }
            minutes = halfDay ? Math.max(1, dayMinutes / 2) : workingDays * dayMinutes;
            startAt = start.atStartOfDay();
            endAt = end.plusDays(1).atStartOfDay();
        } else {
            //시간 단위 신청은 그 종류가 시간 휴가를 허용할 때만(#12)
            if (!type.hourlyEnabled()) {
                throw ApiException.badRequest("LEAVE_HOURLY_NOT_ALLOWED", "leave.request.hourly-not-allowed");
            }
            LocalDateTime s = req.startTime();
            LocalDateTime e = req.endTime();
            if (s == null || e == null || !e.isAfter(s) || !s.toLocalDate().equals(e.toLocalDate())) {
                throw ApiException.badRequest("LEAVE_RANGE", "leave.request.range");
            }
            minutes = (int) Duration.between(s, e).toMinutes();
            if (minutes <= 0) {
                throw ApiException.badRequest("LEAVE_RANGE", "leave.request.range");
            }
            startAt = s;
            endAt = e;
        }

        //기간 겹침 금지(같은 시각에 두 휴가 불가 — 이중 차감 방지)
        if (requestMapper.existsOverlap(tenantId, userId, startAt, endAt)) {
            throw ApiException.badRequest("LEAVE_OVERLAP", "leave.request.overlap");
        }

        //가용 = 유효 부여 − 승인 − 대기(초과 예약 방지)
        int available = availableMinutes(tenantId, userId, type.leaveTypeId());
        if (minutes > available) {
            throw ApiException.badRequest("LEAVE_INSUFFICIENT", "leave.request.insufficient");
        }

        InsertParam param = new InsertParam(tenantId, userId, type.leaveTypeId(), startAt, endAt,
                minutes, req.dayUnit(), halfDay, trimToNull(req.reason()));
        requestMapper.insert(param);
        long requestId = param.getRequestId();

        //승인 불필요 종류는 즉시 확정(본인 결재로 기록)
        if (!type.requiresApproval()) {
            requestMapper.decide(tenantId, requestId, LeaveStatus.APPROVED, userId, null);
        }
        return findRequestResponse(tenantId, userId, requestId);
    }

    public List<LeaveRequestResponse> myRequests(long tenantId, long userId) {
        return requestMapper.findViewByUser(tenantId, userId).stream()
                .map(LeaveRequestResponse::of).toList();
    }

    @Transactional
    public void cancel(long tenantId, long userId, long requestId) {
        int updated = requestMapper.cancelByUser(tenantId, userId, requestId);
        if (updated == 0) {
            //존재하지 않거나(타인/미존재) 이미 결재되어(승인/반려) 본인 취소 불가
            LeaveRequest existing = requestMapper.findById(tenantId, requestId);
            if (existing == null || existing.userId() != userId) {
                throw ApiException.notFound("LEAVE_REQUEST_NOT_FOUND", "leave.request.not-found");
            }
            throw ApiException.badRequest("LEAVE_NOT_CANCELABLE", "leave.request.not-cancelable");
        }
    }

    /**
     * 멤버 취소 신청(승인 휴가) — 시작 전(당일 제외)만 가능. 당일·시작된 휴가는 관리자 직접 취소 대상.
     * 확정 전까지 CANCEL_REQUESTED로 잔여를 계속 소진한다(관리자 확정 시 복원).
     */
    @Transactional
    public void requestCancel(long tenantId, long userId, long requestId, String reason) {
        LocalDateTime tomorrowStart = LocalDate.now(clock).plusDays(1).atStartOfDay();
        int updated = requestMapper.requestCancelByUser(tenantId, userId, requestId, reason.trim(),
                tomorrowStart);
        if (updated == 0) {
            LeaveRequest existing = requestMapper.findById(tenantId, requestId);
            if (existing == null || existing.userId() != userId) {
                throw ApiException.notFound("LEAVE_REQUEST_NOT_FOUND", "leave.request.not-found");
            }
            if (existing.status() == LeaveStatus.APPROVED) {
                //승인건이지만 당일·시작됨 → 관리자 직접 취소만
                throw ApiException.badRequest("LEAVE_CANCEL_SAME_DAY", "leave.request.cancel-same-day");
            }
            throw ApiException.badRequest("LEAVE_NOT_CANCELABLE", "leave.request.not-cancelable");
        }
    }

    // ===== 결재/부여(관리자) =====

    public List<LeaveRequestResponse> pendingRequests(long tenantId) {
        return requestMapper.findPendingViewByTenant(tenantId).stream()
                .map(LeaveRequestResponse::of).toList();
    }

    /** 취소 신청 목록(관리자). */
    public List<LeaveRequestResponse> cancelRequests(long tenantId) {
        return requestMapper.findCancelRequestedViewByTenant(tenantId).stream()
                .map(LeaveRequestResponse::of).toList();
    }

    /**
     * 현재/예정 휴가자 목록(관리자, #11) — 아직 끝나지 않은 APPROVED. 당일이라 멤버가 취소 신청을
     * 못 만든 경우에도 관리자가 직접 취소(cancelByAdmin)할 수 있게 노출한다.
     */
    public List<LeaveRequestResponse> approvedLeaves(long tenantId) {
        return requestMapper.findApprovedActiveByTenant(tenantId, java.time.LocalDateTime.now(clock)).stream()
                .map(LeaveRequestResponse::of).toList();
    }

    /** 관리자 취소 확정 — 승인건/취소신청건을 CANCELED로(잔여 복원). 취소사유 필수. */
    @Transactional
    public void cancelByAdmin(long tenantId, long adminId, long requestId, String reason) {
        int updated = requestMapper.cancelByAdmin(tenantId, requestId, adminId, reason.trim());
        if (updated == 0) {
            LeaveRequest existing = requestMapper.findById(tenantId, requestId);
            if (existing == null) {
                throw ApiException.notFound("LEAVE_REQUEST_NOT_FOUND", "leave.request.not-found");
            }
            //APPROVED/CANCEL_REQUESTED가 아님(대기/반려/이미 취소)
            throw ApiException.conflict("LEAVE_NOT_CANCELABLE", "leave.request.not-cancelable");
        }
    }

    /** 관리자가 취소 신청을 반려 — CANCEL_REQUESTED를 APPROVED로 되돌린다. */
    @Transactional
    public void rejectCancel(long tenantId, long adminId, long requestId, String note) {
        int updated = requestMapper.rejectCancelByAdmin(tenantId, requestId, adminId,
                trimToNull(note));
        if (updated == 0) {
            LeaveRequest existing = requestMapper.findById(tenantId, requestId);
            if (existing == null) {
                throw ApiException.notFound("LEAVE_REQUEST_NOT_FOUND", "leave.request.not-found");
            }
            throw ApiException.conflict("LEAVE_NOT_CANCELABLE", "leave.request.not-cancelable");
        }
    }

    @Transactional
    public void decide(long tenantId, long deciderId, long requestId, boolean approve, String note) {
        LeaveRequest existing = requestMapper.findById(tenantId, requestId);
        if (existing == null) {
            throw ApiException.notFound("LEAVE_REQUEST_NOT_FOUND", "leave.request.not-found");
        }
        if (existing.status() != LeaveStatus.PENDING) {
            throw ApiException.conflict("LEAVE_ALREADY_DECIDED", "leave.request.already-decided");
        }
        if (approve) {
            //부여 행 잠금 후 잔여 재확인 — 동시 결재로 인한 초과 부여 방지(read-then-write 직렬화)
            grantMapper.lockByUserType(tenantId, existing.userId(), existing.leaveTypeId());
            //승인 시점 잔여 재확인(부여 − 승인). 대기(본 건 포함)는 아직 승인 전이라 제외.
            int granted = grantMapper.sumEffectiveMinutes(tenantId, existing.userId(),
                    existing.leaveTypeId(), LocalDate.now(clock));
            int used = requestMapper.sumApprovedMinutes(tenantId, existing.userId(),
                    existing.leaveTypeId());
            if (existing.minutes() > granted - used) {
                throw ApiException.conflict("LEAVE_INSUFFICIENT", "leave.request.insufficient");
            }
        }
        LeaveStatus target = approve ? LeaveStatus.APPROVED : LeaveStatus.REJECTED;
        int updated = requestMapper.decide(tenantId, requestId, target, deciderId, trimToNull(note));
        if (updated == 0) {
            throw ApiException.conflict("LEAVE_ALREADY_DECIDED", "leave.request.already-decided");
        }
    }

    @Transactional
    public void grantManual(long tenantId, long granterId, LeaveGrantRequest req) {
        User user = requireUser(tenantId, req.userId());
        LeaveType type = requireType(tenantId, req.leaveTypeId());
        ProfileCountry country = countryOf(tenantId);
        int dayMinutes = standardDayMinutes(user, country);
        int minutes = (int) Math.round(req.days() * dayMinutes);
        grantMapper.insert(tenantId, user.userId(), type.leaveTypeId(), minutes,
                LocalDate.now(clock), req.expiresOn(), LeaveSource.MANUAL, null,
                trimToNull(req.memo()), granterId);
    }

    /**
     * 일괄 부여(#9) — 여러 멤버에 같은 종류·일수를 한 트랜잭션으로 부여. 부여 일수(분)는
     * 멤버별 표준 근무분이 다를 수 있어 각자 환산한다. 하나라도 검증 실패면 전체 롤백.
     *
     * @return 부여한 멤버 수
     */
    @Transactional
    public int grantManualBulk(long tenantId, long granterId, LeaveBulkGrantRequest req) {
        LeaveType type = requireType(tenantId, req.leaveTypeId());
        ProfileCountry country = countryOf(tenantId);
        String memo = trimToNull(req.memo());
        LocalDate effectiveFrom = LocalDate.now(clock);
        //중복 userId는 한 번만(같은 멤버 두 번 부여 방지)
        for (long userId : req.userIds().stream().distinct().toList()) {
            User user = requireUser(tenantId, userId);
            int minutes = (int) Math.round(req.days() * standardDayMinutes(user, country));
            grantMapper.insert(tenantId, user.userId(), type.leaveTypeId(), minutes,
                    effectiveFrom, req.expiresOn(), LeaveSource.MANUAL, null, memo, granterId);
        }
        return (int) req.userIds().stream().distinct().count();
    }

    /** 연차 자동 재계산(멤버 1인) — 현 연도 AUTO 연차 grant upsert. */
    @Transactional
    public void recomputeAnnual(long tenantId, long granterId, long userId) {
        User user = requireUser(tenantId, userId);
        recomputeAnnualInternal(tenantId, granterId, user, requireAnnualType(tenantId),
                countryOf(tenantId));
    }

    /** 연차 자동 재계산(테넌트 전체 ACTIVE 멤버). */
    @Transactional
    public int recomputeAnnualAll(long tenantId, long granterId) {
        LeaveType annual = requireAnnualType(tenantId);
        ProfileCountry country = countryOf(tenantId);
        int count = 0;
        for (User user : userMapper.findByTenant(tenantId)) {
            if (user.role() == Role.SYSTEM_ADMIN || user.hireDate() == null) {
                continue;
            }
            recomputeAnnualInternal(tenantId, granterId, user, annual, country);
            count++;
        }
        return count;
    }

    /** 법정 연차 제안(미리보기 — 부여하지 않는다). 연차 종류 없거나 입사일 미설정이면 null. */
    private Integer suggestedAnnualMinutes(User user, LeaveType annual, ProfileCountry country) {
        if (annual == null || user.hireDate() == null) {
            return null;
        }
        int days = LeaveAccrualPolicy.of(country).entitledDays(user.hireDate(), LocalDate.now(clock));
        return days * standardDayMinutes(user, country);
    }

    private void recomputeAnnualInternal(long tenantId, long granterId, User user, LeaveType annual,
            ProfileCountry country) {
        if (user.hireDate() == null) {
            throw ApiException.badRequest("LEAVE_HIRE_DATE", "leave.hire-date.required");
        }
        LocalDate today = LocalDate.now(clock);
        int days = LeaveAccrualPolicy.of(country).entitledDays(user.hireDate(), today);
        int minutes = days * standardDayMinutes(user, country);
        int validityYears = country == ProfileCountry.JP ? 2 : 1;
        LocalDate expires = today.plusYears(validityYears);
        grantMapper.upsertAuto(tenantId, user.userId(), annual.leaveTypeId(), minutes, today,
                expires, today.getYear(), "법정 연차 자동계산", granterId);
    }

    @Transactional
    public void updateHireDate(long tenantId, long userId, LocalDate hireDate) {
        requireUser(tenantId, userId);
        userMapper.updateHireDate(tenantId, userId, hireDate);
    }

    // ===== 관리자: 멤버 상세/개요 =====

    public MemberLeaveDetail memberDetail(long tenantId, long userId) {
        User user = requireUser(tenantId, userId);
        ProfileCountry country = countryOf(tenantId);
        int dayMinutes = standardDayMinutes(user, country);
        List<LeaveRequestView> requests = requestMapper.findViewByUser(tenantId, userId);
        List<LeaveBalanceResponse> balances = balancesFor(tenantId, userId, dayMinutes, requests);
        List<LeaveRequestResponse> reqResponses = requests.stream()
                .map(LeaveRequestResponse::of).toList();
        Integer suggested = suggestedAnnualMinutes(user, typeMapper.findAnnual(tenantId), country);
        return new MemberLeaveDetail(user.userId(), user.name(), user.hireDate(), dayMinutes,
                suggested, balances, reqResponses);
    }

    public List<MemberLeaveSummary> memberOverview(long tenantId) {
        LeaveType annual = typeMapper.findAnnual(tenantId);
        ProfileCountry country = countryOf(tenantId);
        LocalDate today = LocalDate.now(clock);
        List<MemberLeaveSummary> out = new ArrayList<>();
        for (User user : userMapper.findByTenant(tenantId)) {
            if (user.role() == Role.SYSTEM_ADMIN) {
                continue;
            }
            int dayMinutes = standardDayMinutes(user, country);
            Integer annualRemaining = null;
            if (annual != null) {
                int granted = grantMapper.sumEffectiveMinutes(tenantId, user.userId(),
                        annual.leaveTypeId(), today);
                int used = requestMapper.sumApprovedMinutes(tenantId, user.userId(),
                        annual.leaveTypeId());
                annualRemaining = granted - used;
            }
            Integer suggested = suggestedAnnualMinutes(user, annual, country);
            out.add(new MemberLeaveSummary(user.userId(), user.name(), user.hireDate(),
                    annualRemaining, suggested, dayMinutes));
        }
        return out;
    }

    // ===== 내부 헬퍼 =====

    private int availableMinutes(long tenantId, long userId, long leaveTypeId) {
        int granted = grantMapper.sumEffectiveMinutes(tenantId, userId, leaveTypeId,
                LocalDate.now(clock));
        int used = 0;
        int pending = 0;
        for (LeaveRequestView r : requestMapper.findViewByUser(tenantId, userId)) {
            if (r.leaveTypeId() != leaveTypeId) {
                continue;
            }
            if (r.status() == LeaveStatus.APPROVED || r.status() == LeaveStatus.CANCEL_REQUESTED) {
                used += r.minutes();
            } else if (r.status() == LeaveStatus.PENDING) {
                pending += r.minutes();
            }
        }
        return granted - used - pending;
    }

    private LeaveRequestResponse findRequestResponse(long tenantId, long userId, long requestId) {
        return requestMapper.findViewByUser(tenantId, userId).stream()
                .filter(v -> v.leaveRequestId() == requestId)
                .findFirst()
                .map(LeaveRequestResponse::of)
                .orElseThrow(() -> ApiException.notFound("LEAVE_REQUEST_NOT_FOUND",
                        "leave.request.not-found"));
    }

    /**
     * 1일 = 개인 소정근로시간 — 멤버의 실효 스케줄(정기 패턴) 근무일 평균 근무분(근무구간 − 법정휴게).
     * 스케줄 단일화: users 개인 기본값 대신 정기 스케줄에서 산출한다. 스케줄이 없으면 480분 폴백.
     */
    int standardDayMinutes(User user, ProfileCountry country) {
        return scheduleAdminService.standardDayMinutes(user.tenantId(), user.userId(),
                BreakPolicy.of(country), DEFAULT_DAY_MINUTES);
    }

    /**
     * [start,end] 중 근무 예정일(공휴일 제외) 수 — 실효 스케줄(상세 오버라이드 &gt; 정기 패턴)로 판정.
     * 스케줄 단일화: 개인 근무요일(users.work_days) 대신 정기+상세 스케줄을 기준으로 한다.
     */
    int countWorkingDays(User user, LocalDate start, LocalDate end, Set<LocalDate> holidays) {
        int count = 0;
        for (LocalDate d = start; !d.isAfter(end); d = d.plusDays(1)) {
            if (holidays.contains(d)) {
                continue;
            }
            if (scheduleAdminService.isWorkday(user.tenantId(), user.userId(), d)) {
                count++;
            }
        }
        return count;
    }

    private Set<LocalDate> holidaysBetween(long tenantId, LocalDate start, LocalDate end) {
        List<Holiday> list = holidayMapper.findHolidaysBetween(tenantId, start, end.plusDays(1));
        return list.stream().map(Holiday::holidayDate).collect(Collectors.toSet());
    }

    private LeaveType requireType(long tenantId, long leaveTypeId) {
        LeaveType type = typeMapper.findById(tenantId, leaveTypeId);
        if (type == null) {
            throw ApiException.notFound("LEAVE_TYPE_NOT_FOUND", "leave.type.not-found");
        }
        return type;
    }

    private LeaveType requireAnnualType(long tenantId) {
        LeaveType annual = typeMapper.findAnnual(tenantId);
        if (annual == null) {
            throw ApiException.badRequest("LEAVE_ANNUAL_NONE", "leave.annual.none");
        }
        return annual;
    }

    private User requireUser(long tenantId, long userId) {
        User user = userMapper.findById(tenantId, userId);
        if (user == null) {
            throw ApiException.notFound("MEMBER_NOT_FOUND", "member.not-found");
        }
        return user;
    }

    private ProfileCountry countryOf(long tenantId) {
        Tenant tenant = tenantMapper.findById(tenantId);
        if (tenant == null) {
            throw ApiException.notFound("TENANT_NOT_FOUND", "tenant.not-found");
        }
        ProfileCountry country = ProfileCountry.of(tenant.country());
        return country == null ? ProfileCountry.KR : country;
    }

    private static String trimToNull(String s) {
        if (s == null) {
            return null;
        }
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }
}
