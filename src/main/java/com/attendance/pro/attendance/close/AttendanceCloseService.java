package com.attendance.pro.attendance.close;

import java.time.YearMonth;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.attendance.pro.attendance.close.AttendanceCloseDtos.CloseStatusResponse;
import com.attendance.pro.attendance.close.AttendanceCloseDtos.PendingCloseResponse;
import com.attendance.pro.attendance.close.AttendanceCloseMapper.CloseInsert;
import com.attendance.pro.common.ApiException;

/**
 * 근태 마감 워크플로 — 멤버가 지난달 근태를 마감 신청, 인사관리자가 승인/반려.
 * 승인(APPROVED)되면 그 (멤버, 년, 월)의 수동 정정이 잠긴다(AttendanceService가 findStatus로 판정).
 *
 * '다음 달부터 신청' 규약: 대상 월이 이미 종료(현재 연월 &gt; 대상 연월)해야 신청 가능.
 * 진행 중이거나 종료 전인 달은 마감 신청 불가(집계가 확정되지 않았으므로).
 */
@Service
public class AttendanceCloseService {

    private final AttendanceCloseMapper mapper;

    public AttendanceCloseService(AttendanceCloseMapper mapper) {
        this.mapper = mapper;
    }

    /** 마감 신청(본인). 종료된 달만, 이미 마감/신청중이면 409, 반려건은 재신청(reopen). */
    @Transactional
    public CloseStatusResponse request(long tenantId, long userId, int year, int month) {
        requireMonthEnded(year, month);
        requireAfterHire(tenantId, userId, year, month); //입사월 이전 달은 신청 불가(#8)
        AttendanceClose existing = mapper.find(tenantId, userId, year, month);
        if (existing != null) {
            switch (existing.status()) {
            case APPROVED -> throw ApiException.conflict("CLOSE_ALREADY_CLOSED", "attendance.close.already-closed");
            case REQUESTED -> throw ApiException.conflict("CLOSE_ALREADY_REQUESTED", "attendance.close.already-requested");
            case REJECTED -> mapper.reopen(tenantId, userId, year, month); //재신청
            }
        } else {
            mapper.insert(new CloseInsert(tenantId, userId, year, month));
        }
        return status(tenantId, userId, year, month);
    }

    /** 마감 상태 조회(본인) — 근무표 버튼 상태 판단용. */
    @Transactional(readOnly = true)
    public CloseStatusResponse status(long tenantId, long userId, int year, int month) {
        AttendanceClose c = mapper.find(tenantId, userId, year, month);
        boolean ended = isMonthEnded(year, month);
        if (c == null) {
            return new CloseStatusResponse(year, month, null, null, ended, ended, null, null, null);
        }
        boolean canRequest = ended && c.status() == AttendanceCloseStatus.REJECTED;
        return new CloseStatusResponse(year, month, c.status(), c.closeId(), canRequest, ended,
                c.requestedAt(), c.decidedAt(), c.decisionNote());
    }

    /** 마감 신청 취소(본인, REQUESTED만). 승인/반려건은 취소 대상 아님(409). */
    @Transactional
    public CloseStatusResponse cancel(long tenantId, long userId, int year, int month) {
        int affected = mapper.cancelRequested(tenantId, userId, year, month);
        if (affected == 0) {
            throw ApiException.conflict("CLOSE_NOT_CANCELABLE", "attendance.close.not-cancelable");
        }
        return status(tenantId, userId, year, month);
    }

    /** 결재 대기 목록(관리자) — REQUESTED만. */
    @Transactional(readOnly = true)
    public List<PendingCloseResponse> pending(long tenantId) {
        return mapper.findRequested(tenantId).stream()
                .map(this::toResponse)
                .toList();
    }

    /** 마감 완료 목록(관리자) — 선택 월만('마감 취소' 대상). 승인 이력 전체를 나열하지 않는다. */
    @Transactional(readOnly = true)
    public List<PendingCloseResponse> approvedByMonth(long tenantId, int year, int month) {
        return mapper.findApprovedByMonth(tenantId, year, month).stream()
                .map(this::toResponse)
                .toList();
    }

    private PendingCloseResponse toResponse(AttendanceCloseMapper.PendingCloseRow r) {
        return new PendingCloseResponse(r.closeId(), r.userId(), r.userName(),
                r.targetYear(), r.targetMonth(), r.status(), r.requestedAt());
    }

    /** 마감 취소(관리자) — 승인된 마감을 열린(REQUESTED) 상태로 되돌린다. 잠금 해제. */
    @Transactional
    public void reopen(long tenantId, long closeId) {
        int affected = mapper.reopenApproved(tenantId, closeId);
        if (affected == 0) {
            throw ApiException.conflict("CLOSE_NOT_REOPENABLE", "attendance.close.not-reopenable");
        }
    }

    /** 결재(관리자) — 승인/반려. REQUESTED가 아니면 409(이미 결재됨/없음). */
    @Transactional
    public void decide(long tenantId, long approverId, long closeId, boolean approve, String note) {
        String status = approve ? "APPROVED" : "REJECTED";
        int affected = mapper.decide(tenantId, closeId, approverId, status, trimToNull(note));
        if (affected == 0) {
            throw ApiException.conflict("CLOSE_ALREADY_DECIDED", "attendance.close.already-decided");
        }
    }

    /** 그 (멤버, 년, 월)이 마감 승인되어 정정 잠금 상태인가. */
    public boolean isClosed(long tenantId, long userId, int year, int month) {
        return "APPROVED".equals(mapper.findStatus(tenantId, userId, year, month));
    }

    private void requireMonthEnded(int year, int month) {
        if (!isMonthEnded(year, month)) {
            throw ApiException.badRequest("CLOSE_NOT_ENDED", "attendance.close.not-ended");
        }
    }

    /** 입사월 이전 달은 근무 이력이 없으므로 마감 신청 불가(#8). hire_date 없으면 통과. */
    private void requireAfterHire(long tenantId, long userId, int year, int month) {
        java.time.LocalDate hire = mapper.findHireDate(tenantId, userId);
        if (hire != null && java.time.YearMonth.of(year, month).isBefore(java.time.YearMonth.from(hire))) {
            throw ApiException.badRequest("CLOSE_BEFORE_HIRE", "attendance.close.before-hire");
        }
    }

    /** 대상 월이 이미 끝났는가(현재 연월 &gt; 대상 연월). */
    private boolean isMonthEnded(int year, int month) {
        return YearMonth.of(year, month).isBefore(YearMonth.now());
    }

    private String trimToNull(String s) {
        if (s == null) {
            return null;
        }
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }
}
