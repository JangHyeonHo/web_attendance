package com.attendance.pro.attendance.close;

import java.util.List;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

/**
 * attendance_close 매퍼. 테넌트 전파 규약(tenantId 첫 파라미터 + 2중 조건).
 * record 매핑은 생성자 인자명 기반 자동매핑(설정 활성).
 */
@Mapper
public interface AttendanceCloseMapper {

    String COLS = "close_id, tenant_id, user_id, target_year, target_month, status, "
            + "requested_at, approver_id, decided_at, decision_note";

    /** (멤버, 연, 월) 단건 — 상태 판정·재신청 분기용. 없으면 null. */
    @Select("SELECT " + COLS + " FROM attendance_close "
            + "WHERE tenant_id = #{tenantId} AND user_id = #{userId} "
            + "AND target_year = #{year} AND target_month = #{month}")
    AttendanceClose find(@Param("tenantId") long tenantId, @Param("userId") long userId,
            @Param("year") int year, @Param("month") int month);

    /** 마감 잠금 판정 — APPROVED면 그 달은 정정 불가. 성능상 status만 조회. */
    @Select("SELECT status FROM attendance_close "
            + "WHERE tenant_id = #{tenantId} AND user_id = #{userId} "
            + "AND target_year = #{year} AND target_month = #{month}")
    String findStatus(@Param("tenantId") long tenantId, @Param("userId") long userId,
            @Param("year") int year, @Param("month") int month);

    /** 결재 대기 목록(테넌트 전체, 신청 순). 멤버 이름을 조인해 함께 반환. */
    @Select("""
            SELECT c.close_id AS closeId, c.user_id AS userId, u.name AS userName,
                   c.target_year AS targetYear, c.target_month AS targetMonth,
                   c.requested_at AS requestedAt
            FROM attendance_close c
            JOIN users u ON u.user_id = c.user_id AND u.tenant_id = c.tenant_id
            WHERE c.tenant_id = #{tenantId} AND c.status = 'REQUESTED'
            ORDER BY c.requested_at ASC
            """)
    List<PendingCloseRow> findPending(@Param("tenantId") long tenantId);

    /** 결재 대기 목록 행(멤버 이름 포함). */
    record PendingCloseRow(long closeId, long userId, String userName,
            int targetYear, int targetMonth, java.time.LocalDateTime requestedAt) {
    }

    @Insert("""
            INSERT INTO attendance_close (tenant_id, user_id, target_year, target_month, status, requested_at)
            VALUES (#{tenantId}, #{userId}, #{year}, #{month}, 'REQUESTED', NOW())
            """)
    @Options(useGeneratedKeys = true, keyProperty = "closeId", keyColumn = "close_id")
    int insert(CloseInsert row);

    /** 반려된 행을 다시 신청 상태로(재신청). REQUESTED로 되돌리고 결재 정보 초기화. */
    @Update("""
            UPDATE attendance_close
            SET status = 'REQUESTED', requested_at = NOW(),
                approver_id = NULL, decided_at = NULL, decision_note = NULL
            WHERE tenant_id = #{tenantId} AND user_id = #{userId}
              AND target_year = #{year} AND target_month = #{month} AND status = 'REJECTED'
            """)
    int reopen(@Param("tenantId") long tenantId, @Param("userId") long userId,
            @Param("year") int year, @Param("month") int month);

    /** 본인 REQUESTED 신청 취소(삭제). APPROVED/REJECTED는 대상 아님(영향 행 0). */
    @org.apache.ibatis.annotations.Delete("""
            DELETE FROM attendance_close
            WHERE tenant_id = #{tenantId} AND user_id = #{userId}
              AND target_year = #{year} AND target_month = #{month} AND status = 'REQUESTED'
            """)
    int cancelRequested(@Param("tenantId") long tenantId, @Param("userId") long userId,
            @Param("year") int year, @Param("month") int month);

    /** 결재(승인/반려) — REQUESTED만 전이(충돌 시 영향 행 0 → 409). */
    @Update("""
            UPDATE attendance_close
            SET status = #{status}, approver_id = #{approverId}, decided_at = NOW(), decision_note = #{note}
            WHERE tenant_id = #{tenantId} AND close_id = #{closeId} AND status = 'REQUESTED'
            """)
    int decide(@Param("tenantId") long tenantId, @Param("closeId") long closeId,
            @Param("approverId") long approverId, @Param("status") String status,
            @Param("note") String note);

    /** INSERT 파라미터(자동 생성 키 회수용 가변 클래스). */
    class CloseInsert {
        private Long closeId;
        private final long tenantId;
        private final long userId;
        private final int year;
        private final int month;

        public CloseInsert(long tenantId, long userId, int year, int month) {
            this.tenantId = tenantId;
            this.userId = userId;
            this.year = year;
            this.month = month;
        }

        public Long getCloseId() {
            return closeId;
        }

        public void setCloseId(Long closeId) {
            this.closeId = closeId;
        }

        public long getTenantId() {
            return tenantId;
        }

        public long getUserId() {
            return userId;
        }

        public int getYear() {
            return year;
        }

        public int getMonth() {
            return month;
        }
    }
}
