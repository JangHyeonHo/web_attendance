package com.attendance.pro.leave;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
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
    private final Clock clock;

    @Autowired
    public LeaveService(LeaveTypeMapper typeMapper, LeaveGrantMapper grantMapper,
            LeaveRequestMapper requestMapper, UserMapper userMapper, TenantMapper tenantMapper,
            HolidayMapper holidayMapper) {
        this(typeMapper, grantMapper, requestMapper, userMapper, tenantMapper, holidayMapper,
                Clock.systemDefaultZone());
    }

    LeaveService(LeaveTypeMapper typeMapper, LeaveGrantMapper grantMapper,
            LeaveRequestMapper requestMapper, UserMapper userMapper, TenantMapper tenantMapper,
            HolidayMapper holidayMapper, Clock clock) {
        this.typeMapper = typeMapper;
        this.grantMapper = grantMapper;
        this.requestMapper = requestMapper;
        this.userMapper = userMapper;
        this.tenantMapper = tenantMapper;
        this.holidayMapper = holidayMapper;
        this.clock = clock;
    }

    // ===== 휴가 종류 =====

    public List<LeaveTypeResponse> listTypes(long tenantId) {
        return typeMapper.findByTenant(tenantId).stream().map(LeaveTypeResponse::of).toList();
    }

    public List<LeaveTypeResponse> listActiveTypes(long tenantId) {
        return typeMapper.findActiveByTenant(tenantId).stream().map(LeaveTypeResponse::of).toList();
    }

    @Transactional
    public LeaveTypeResponse createType(long tenantId, LeaveTypeCreateRequest req) {
        LeaveTypeCreate create = new LeaveTypeCreate(tenantId, req.code().trim(), req.name().trim(),
                req.paid(), req.unit(), req.requiresApproval(), req.sortOrder());
        try {
            typeMapper.insert(create);
        } catch (DuplicateKeyException e) {
            throw ApiException.conflict("LEAVE_TYPE_CODE_DUP", "leave.type.code.duplicated");
        }
        return LeaveTypeResponse.of(typeMapper.findById(tenantId, create.getLeaveTypeId()));
    }

    @Transactional
    public LeaveTypeResponse updateType(long tenantId, long leaveTypeId, LeaveTypeUpdateRequest req) {
        LeaveType existing = requireType(tenantId, leaveTypeId);
        typeMapper.update(tenantId, existing.leaveTypeId(), req.name().trim(), req.paid(),
                req.unit(), req.requiresApproval(), req.active(), req.sortOrder());
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
        for (LeaveType type : typeMapper.findActiveByTenant(tenantId)) {
            int granted = grantMapper.sumEffectiveMinutes(tenantId, userId, type.leaveTypeId(), today);
            int used = 0;
            int pending = 0;
            for (LeaveRequestView r : requests) {
                if (r.leaveTypeId() != type.leaveTypeId()) {
                    continue;
                }
                if (r.status() == LeaveStatus.APPROVED) {
                    used += r.minutes();
                } else if (r.status() == LeaveStatus.PENDING) {
                    pending += r.minutes();
                }
            }
            int remaining = granted - used;
            out.add(new LeaveBalanceResponse(type.leaveTypeId(), type.code(), type.name(),
                    type.unit(), type.isAnnual(), granted, used, pending, remaining, dayMinutes));
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
        int updated = requestMapper.cancelByUser(tenantId, userId, requestId, LocalDateTime.now(clock));
        if (updated == 0) {
            //존재하지 않거나(타인/미존재) 이미 시작·처리되어 취소 불가
            LeaveRequest existing = requestMapper.findById(tenantId, requestId);
            if (existing == null || existing.userId() != userId) {
                throw ApiException.notFound("LEAVE_REQUEST_NOT_FOUND", "leave.request.not-found");
            }
            throw ApiException.badRequest("LEAVE_NOT_CANCELABLE", "leave.request.not-cancelable");
        }
    }

    // ===== 결재/부여(관리자) =====

    public List<LeaveRequestResponse> pendingRequests(long tenantId) {
        return requestMapper.findPendingViewByTenant(tenantId).stream()
                .map(LeaveRequestResponse::of).toList();
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
        return new MemberLeaveDetail(user.userId(), user.name(), user.hireDate(), dayMinutes,
                balances, reqResponses);
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
            out.add(new MemberLeaveSummary(user.userId(), user.name(), user.hireDate(),
                    annualRemaining, dayMinutes));
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
            if (r.status() == LeaveStatus.APPROVED) {
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

    /** 1일 = 근무구간(종업−시업) − 법정휴게. 스케줄이 비정상이면 480분 폴백. */
    int standardDayMinutes(User user, ProfileCountry country) {
        LocalTime start = user.defaultWorkStart();
        LocalTime end = user.defaultWorkEnd();
        if (start == null || end == null || !end.isAfter(start)) {
            return DEFAULT_DAY_MINUTES;
        }
        Duration span = Duration.between(start, end);
        long brk = BreakPolicy.of(country).requiredBreak(span).toMinutes();
        long work = span.toMinutes() - brk;
        return (int) Math.max(1, work);
    }

    /** [start,end] 중 개인 근무 요일이면서 공휴일이 아닌 날 수. */
    int countWorkingDays(User user, LocalDate start, LocalDate end, Set<LocalDate> holidays) {
        String workDays = user.workDays() != null ? user.workDays() : "1111100";
        int count = 0;
        for (LocalDate d = start; !d.isAfter(end); d = d.plusDays(1)) {
            int idx = d.getDayOfWeek().getValue() - 1; //1=Mon..7=Sun → 0..6
            boolean works = idx < workDays.length() && workDays.charAt(idx) == '1';
            if (works && !holidays.contains(d)) {
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
