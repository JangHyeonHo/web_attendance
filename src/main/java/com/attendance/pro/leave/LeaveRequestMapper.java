package com.attendance.pro.leave;

import java.time.LocalDateTime;
import java.util.List;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

/**
 * leave_request 매퍼. 승인(APPROVED)만 잔여 차감 대상.
 * 목록은 멤버명·종류명을 조인한 view로 반환(N+1 회피).
 */
@Mapper
public interface LeaveRequestMapper {

    String COLS = "leave_request_id, tenant_id, user_id, leave_type_id, start_at, end_at, minutes, "
            + "day_unit, half_day, reason, status, decided_by, decided_at, decision_note, "
            + "cancel_reason, created_at, updated_at";

    String VIEW_COLS = "r.leave_request_id, r.user_id, u.name AS user_name, r.leave_type_id, "
            + "t.code AS type_code, t.name AS type_name, t.unit, r.start_at, r.end_at, r.minutes, "
            + "r.day_unit, r.half_day, r.reason, r.status, r.decided_at, r.decision_note, "
            + "r.cancel_reason, r.created_at";

    @Select("SELECT " + COLS + " FROM leave_request WHERE tenant_id = #{tenantId} "
            + "AND leave_request_id = #{requestId}")
    LeaveRequest findById(@Param("tenantId") long tenantId, @Param("requestId") long requestId);

    @Insert("""
            INSERT INTO leave_request (tenant_id, user_id, leave_type_id, start_at, end_at, minutes,
                                       day_unit, half_day, reason, status)
            VALUES (#{tenantId}, #{userId}, #{leaveTypeId}, #{startAt}, #{endAt}, #{minutes},
                    #{dayUnit}, #{halfDay}, #{reason}, 'PENDING')
            """)
    @Options(useGeneratedKeys = true, keyProperty = "requestId", keyColumn = "leave_request_id")
    int insert(InsertParam param);

    /**
     * 잔여 소진 합계(분) — APPROVED + CANCEL_REQUESTED. 취소 신청중인 건도 확정 전까지는
     * 유효한 휴가이므로 잔여를 계속 소진한다(확정=CANCELED 시 합산에서 빠져 복원).
     */
    @Select("""
            SELECT COALESCE(SUM(minutes), 0) FROM leave_request
            WHERE tenant_id = #{tenantId} AND user_id = #{userId} AND leave_type_id = #{leaveTypeId}
              AND status IN ('APPROVED', 'CANCEL_REQUESTED')
            """)
    int sumApprovedMinutes(@Param("tenantId") long tenantId, @Param("userId") long userId,
            @Param("leaveTypeId") long leaveTypeId);

    /**
     * 기간 겹침 검사 — 유효 신청(PENDING/APPROVED/CANCEL_REQUESTED)과 [startAt, endAt)이 겹치면 true.
     * 종류 무관(같은 시각에 두 휴가 동시 불가). 반열림 구간 비교(end ≤ 상대start면 안 겹침).
     */
    @Select("""
            SELECT EXISTS(
                SELECT 1 FROM leave_request
                WHERE tenant_id = #{tenantId} AND user_id = #{userId}
                  AND status IN ('PENDING', 'APPROVED', 'CANCEL_REQUESTED')
                  AND start_at < #{endAt} AND end_at > #{startAt}
            )
            """)
    boolean existsOverlap(@Param("tenantId") long tenantId, @Param("userId") long userId,
            @Param("startAt") LocalDateTime startAt, @Param("endAt") LocalDateTime endAt);

    String VIEW_FROM = " FROM leave_request r"
            + " JOIN users u ON u.user_id = r.user_id"
            + " JOIN leave_type t ON t.leave_type_id = r.leave_type_id";

    /** 본인 신청 전건(최신순) — 잔여/사용량 합산 등 내부 계산용(전체 필요). 화면 목록은 페이지 조회로. */
    @Select("SELECT " + VIEW_COLS + VIEW_FROM
            + " WHERE r.tenant_id = #{tenantId} AND r.user_id = #{userId}"
            + " ORDER BY r.start_at DESC, r.leave_request_id DESC")
    List<LeaveRequestView> findViewByUser(@Param("tenantId") long tenantId,
            @Param("userId") long userId);

    /** 본인 신청 내역(최신순) — 페이지 조회(#9: 해가 갈수록 무한 증가하는 목록이라 전건 반환하지 않는다). */
    @Select("SELECT " + VIEW_COLS + VIEW_FROM
            + " WHERE r.tenant_id = #{tenantId} AND r.user_id = #{userId}"
            + " ORDER BY r.start_at DESC, r.leave_request_id DESC"
            + " LIMIT #{size} OFFSET #{offset}")
    List<LeaveRequestView> findViewPageByUser(@Param("tenantId") long tenantId,
            @Param("userId") long userId, @Param("size") int size, @Param("offset") int offset);

    /** 본인 신청 내역 전체 건수(#9 페이지 계산용). */
    @Select("SELECT COUNT(*) FROM leave_request r"
            + " WHERE r.tenant_id = #{tenantId} AND r.user_id = #{userId}")
    long countByUser(@Param("tenantId") long tenantId, @Param("userId") long userId);

    /** 승인 대기 목록(관리자) — 오래된 신청 먼저. */
    @Select("SELECT " + VIEW_COLS + VIEW_FROM
            + " WHERE r.tenant_id = #{tenantId} AND r.status = 'PENDING'"
            + " ORDER BY r.start_at ASC, r.leave_request_id ASC")
    List<LeaveRequestView> findPendingViewByTenant(@Param("tenantId") long tenantId);

    /**
     * 본인 유효 휴가([from, to)와 겹치는 APPROVED/CANCEL_REQUESTED) — 월별 출결에 휴가 표시용(#9).
     * 반열림 구간 겹침. 두 상태 모두 잔여를 소진하는 유효 휴가라 조회 화면에서도 휴가로 본다.
     */
    @Select("SELECT " + VIEW_COLS + VIEW_FROM
            + " WHERE r.tenant_id = #{tenantId} AND r.user_id = #{userId}"
            + "   AND r.status IN ('APPROVED', 'CANCEL_REQUESTED')"
            + "   AND r.start_at < #{to} AND r.end_at > #{from}"
            + " ORDER BY r.start_at ASC, r.leave_request_id ASC")
    List<LeaveRequestView> findApprovedForUserBetween(@Param("tenantId") long tenantId,
            @Param("userId") long userId,
            @Param("from") LocalDateTime from, @Param("to") LocalDateTime to);

    /**
     * 현재/예정 휴가자(관리자 직접 취소용, #11) — 아직 끝나지 않은 APPROVED. 시작일 순.
     * (취소 신청 대기(CANCEL_REQUESTED)는 별도 목록에서 처리하므로 여기선 APPROVED만.)
     */
    @Select("SELECT " + VIEW_COLS + VIEW_FROM
            + " WHERE r.tenant_id = #{tenantId} AND r.status = 'APPROVED' AND r.end_at > #{now}"
            + " ORDER BY r.start_at ASC, r.leave_request_id ASC")
    List<LeaveRequestView> findApprovedActiveByTenant(@Param("tenantId") long tenantId,
            @Param("now") LocalDateTime now);

    /**
     * 결재(승인/반려) — PENDING만 전이(동시 결재 레이스 가드). 반환 0이면 이미 처리됨.
     */
    @Update("""
            UPDATE leave_request
            SET status = #{status}, decided_by = #{decidedBy}, decided_at = NOW(),
                decision_note = #{decisionNote}
            WHERE tenant_id = #{tenantId} AND leave_request_id = #{requestId} AND status = 'PENDING'
            """)
    int decide(@Param("tenantId") long tenantId, @Param("requestId") long requestId,
            @Param("status") LeaveStatus status, @Param("decidedBy") long decidedBy,
            @Param("decisionNote") String decisionNote);

    /**
     * 본인 취소 — <b>PENDING만</b>(시간 제약 없이 항상, 당일 신청 실수도 복구 가능).
     * 승인(APPROVED) 건은 본인이 취소할 수 없고 관리자 처리 대상이다(정책 결정).
     */
    @Update("""
            UPDATE leave_request SET status = 'CANCELED'
            WHERE tenant_id = #{tenantId} AND user_id = #{userId} AND leave_request_id = #{requestId}
              AND status = 'PENDING'
            """)
    int cancelByUser(@Param("tenantId") long tenantId, @Param("userId") long userId,
            @Param("requestId") long requestId);

    /**
     * 멤버 취소 신청 — 본인 APPROVED 건 중 <b>시작 전(당일 제외)</b>만 CANCEL_REQUESTED로 전이.
     * 당일·시작된 휴가는 tomorrowStart 조건에 걸려 0행 → 관리자 직접 취소만 가능.
     */
    @Update("""
            UPDATE leave_request
            SET status = 'CANCEL_REQUESTED', cancel_reason = #{cancelReason}
            WHERE tenant_id = #{tenantId} AND user_id = #{userId} AND leave_request_id = #{requestId}
              AND status = 'APPROVED' AND start_at >= #{tomorrowStart}
            """)
    int requestCancelByUser(@Param("tenantId") long tenantId, @Param("userId") long userId,
            @Param("requestId") long requestId, @Param("cancelReason") String cancelReason,
            @Param("tomorrowStart") LocalDateTime tomorrowStart);

    /**
     * 관리자 취소 확정 — APPROVED 또는 CANCEL_REQUESTED를 CANCELED로. 잔여 자동 복원.
     * cancelReason은 신청 사유가 있으면 유지, 없으면(관리자 직접 취소) 새로 기록.
     */
    @Update("""
            UPDATE leave_request
            SET status = 'CANCELED', decided_by = #{adminId}, decided_at = NOW(),
                cancel_reason = COALESCE(cancel_reason, #{cancelReason})
            WHERE tenant_id = #{tenantId} AND leave_request_id = #{requestId}
              AND status IN ('APPROVED', 'CANCEL_REQUESTED')
            """)
    int cancelByAdmin(@Param("tenantId") long tenantId, @Param("requestId") long requestId,
            @Param("adminId") long adminId, @Param("cancelReason") String cancelReason);

    /**
     * 관리자가 취소 신청을 반려 — CANCEL_REQUESTED를 APPROVED로 되돌린다(잔여 그대로 소진 유지).
     */
    @Update("""
            UPDATE leave_request
            SET status = 'APPROVED', decided_by = #{adminId}, decided_at = NOW(),
                decision_note = #{note}
            WHERE tenant_id = #{tenantId} AND leave_request_id = #{requestId}
              AND status = 'CANCEL_REQUESTED'
            """)
    int rejectCancelByAdmin(@Param("tenantId") long tenantId, @Param("requestId") long requestId,
            @Param("adminId") long adminId, @Param("note") String note);

    /** 취소 신청 목록(관리자) — 오래된 신청 먼저. */
    @Select("SELECT " + VIEW_COLS + VIEW_FROM
            + " WHERE r.tenant_id = #{tenantId} AND r.status = 'CANCEL_REQUESTED'"
            + " ORDER BY r.start_at ASC, r.leave_request_id ASC")
    List<LeaveRequestView> findCancelRequestedViewByTenant(@Param("tenantId") long tenantId);

    /** 신청 등록 파라미터(자동 생성 키 반환). */
    class InsertParam {
        private Long requestId;
        private final long tenantId;
        private final long userId;
        private final long leaveTypeId;
        private final LocalDateTime startAt;
        private final LocalDateTime endAt;
        private final int minutes;
        private final boolean dayUnit;
        private final boolean halfDay;
        private final String reason;

        public InsertParam(long tenantId, long userId, long leaveTypeId, LocalDateTime startAt,
                LocalDateTime endAt, int minutes, boolean dayUnit, boolean halfDay, String reason) {
            this.tenantId = tenantId;
            this.userId = userId;
            this.leaveTypeId = leaveTypeId;
            this.startAt = startAt;
            this.endAt = endAt;
            this.minutes = minutes;
            this.dayUnit = dayUnit;
            this.halfDay = halfDay;
            this.reason = reason;
        }

        public Long getRequestId() {
            return requestId;
        }

        public void setRequestId(Long requestId) {
            this.requestId = requestId;
        }

        public long getTenantId() {
            return tenantId;
        }

        public long getUserId() {
            return userId;
        }

        public long getLeaveTypeId() {
            return leaveTypeId;
        }

        public LocalDateTime getStartAt() {
            return startAt;
        }

        public LocalDateTime getEndAt() {
            return endAt;
        }

        public int getMinutes() {
            return minutes;
        }

        public boolean isDayUnit() {
            return dayUnit;
        }

        public boolean isHalfDay() {
            return halfDay;
        }

        public String getReason() {
            return reason;
        }
    }

    /** 목록 표시용 조인 view. */
    record LeaveRequestView(
            long leaveRequestId,
            long userId,
            String userName,
            long leaveTypeId,
            String typeCode,
            String typeName,
            LeaveUnit unit,
            LocalDateTime startAt,
            LocalDateTime endAt,
            int minutes,
            boolean dayUnit,
            boolean halfDay,
            String reason,
            LeaveStatus status,
            LocalDateTime decidedAt,
            String decisionNote,
            String cancelReason,
            LocalDateTime createdAt) {
    }
}
