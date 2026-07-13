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
            + "created_at, updated_at";

    String VIEW_COLS = "r.leave_request_id, r.user_id, u.name AS user_name, r.leave_type_id, "
            + "t.code AS type_code, t.name AS type_name, t.unit, r.start_at, r.end_at, r.minutes, "
            + "r.day_unit, r.half_day, r.reason, r.status, r.decided_at, r.decision_note, r.created_at";

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

    /** 잔여 계산용 — 종류별 APPROVED 차감 합계(분). */
    @Select("""
            SELECT COALESCE(SUM(minutes), 0) FROM leave_request
            WHERE tenant_id = #{tenantId} AND user_id = #{userId} AND leave_type_id = #{leaveTypeId}
              AND status = 'APPROVED'
            """)
    int sumApprovedMinutes(@Param("tenantId") long tenantId, @Param("userId") long userId,
            @Param("leaveTypeId") long leaveTypeId);

    /**
     * 기간 겹침 검사 — 같은 유저의 PENDING/APPROVED 신청과 [startAt, endAt)이 겹치면 true.
     * 종류 무관(같은 시각에 두 휴가 동시 불가). 반열림 구간 비교(end ≤ 상대start면 안 겹침).
     */
    @Select("""
            SELECT EXISTS(
                SELECT 1 FROM leave_request
                WHERE tenant_id = #{tenantId} AND user_id = #{userId}
                  AND status IN ('PENDING', 'APPROVED')
                  AND start_at < #{endAt} AND end_at > #{startAt}
            )
            """)
    boolean existsOverlap(@Param("tenantId") long tenantId, @Param("userId") long userId,
            @Param("startAt") LocalDateTime startAt, @Param("endAt") LocalDateTime endAt);

    String VIEW_FROM = " FROM leave_request r"
            + " JOIN users u ON u.user_id = r.user_id"
            + " JOIN leave_type t ON t.leave_type_id = r.leave_type_id";

    /** 본인 신청 내역(최신순). */
    @Select("SELECT " + VIEW_COLS + VIEW_FROM
            + " WHERE r.tenant_id = #{tenantId} AND r.user_id = #{userId}"
            + " ORDER BY r.start_at DESC, r.leave_request_id DESC")
    List<LeaveRequestView> findViewByUser(@Param("tenantId") long tenantId,
            @Param("userId") long userId);

    /** 승인 대기 목록(관리자) — 오래된 신청 먼저. */
    @Select("SELECT " + VIEW_COLS + VIEW_FROM
            + " WHERE r.tenant_id = #{tenantId} AND r.status = 'PENDING'"
            + " ORDER BY r.start_at ASC, r.leave_request_id ASC")
    List<LeaveRequestView> findPendingViewByTenant(@Param("tenantId") long tenantId);

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
            LocalDateTime createdAt) {
    }
}
