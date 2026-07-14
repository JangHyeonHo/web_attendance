package com.attendance.pro.leave;

import java.time.LocalDate;
import java.util.List;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * leave_grant 매퍼. 잔여 계산은 서비스가 grant 합계 − APPROVED 신청 합계로 산출한다.
 * AUTO 연차는 (tenant,user,type,leave_year) 유니크로 upsert(재계산 시 1행 갱신).
 */
@Mapper
public interface LeaveGrantMapper {

    String COLS = "leave_grant_id, tenant_id, user_id, leave_type_id, minutes, effective_from, "
            + "expires_on, source, leave_year, memo, granted_by, created_at, updated_at";

    @Select("SELECT " + COLS + " FROM leave_grant WHERE tenant_id = #{tenantId} "
            + "AND user_id = #{userId} ORDER BY effective_from DESC, leave_grant_id DESC")
    List<LeaveGrant> findByUser(@Param("tenantId") long tenantId, @Param("userId") long userId);

    /**
     * 기준일에 유효한 부여 행 — 만기 임박순(만기 없는 것은 뒤). 만기일별 잔여 표시(FIFO 차감)용.
     */
    @Select("SELECT " + COLS + " FROM leave_grant WHERE tenant_id = #{tenantId} AND user_id = #{userId} "
            + "AND effective_from <= #{asOf} AND (expires_on IS NULL OR expires_on >= #{asOf}) "
            + "ORDER BY (expires_on IS NULL), expires_on ASC, leave_grant_id ASC")
    List<LeaveGrant> findActiveByUser(@Param("tenantId") long tenantId, @Param("userId") long userId,
            @Param("asOf") LocalDate asOf);

    /**
     * (tenant,user,type) 부여 행을 FOR UPDATE로 잠근다 — 신청/승인 잔여 검사~기록을 직렬화해
     * 동시 결재로 인한 초과 부여를 막는다(마지막 관리자 보호의 FOR UPDATE와 같은 규율).
     * 부여 행이 없으면 잠글 행도 없지만, 그 경우 가용=0이라 초과 예약 자체가 불가능하다.
     */
    @Select("SELECT leave_grant_id FROM leave_grant WHERE tenant_id = #{tenantId} "
            + "AND user_id = #{userId} AND leave_type_id = #{leaveTypeId} FOR UPDATE")
    List<Long> lockByUserType(@Param("tenantId") long tenantId, @Param("userId") long userId,
            @Param("leaveTypeId") long leaveTypeId);

    /**
     * 기준일에 유효한 부여 합계(분) — effective_from ≤ asOf, (expires_on NULL 또는 ≥ asOf).
     * 결과 NULL(행 없음)은 서비스에서 0으로 처리.
     */
    @Select("""
            SELECT COALESCE(SUM(minutes), 0) FROM leave_grant
            WHERE tenant_id = #{tenantId} AND user_id = #{userId} AND leave_type_id = #{leaveTypeId}
              AND effective_from <= #{asOf}
              AND (expires_on IS NULL OR expires_on >= #{asOf})
            """)
    int sumEffectiveMinutes(@Param("tenantId") long tenantId, @Param("userId") long userId,
            @Param("leaveTypeId") long leaveTypeId, @Param("asOf") LocalDate asOf);

    @Insert("""
            INSERT INTO leave_grant (tenant_id, user_id, leave_type_id, minutes, effective_from,
                                     expires_on, source, leave_year, memo, granted_by)
            VALUES (#{tenantId}, #{userId}, #{leaveTypeId}, #{minutes}, #{effectiveFrom},
                    #{expiresOn}, #{source}, #{leaveYear}, #{memo}, #{grantedBy})
            """)
    int insert(@Param("tenantId") long tenantId, @Param("userId") long userId,
            @Param("leaveTypeId") long leaveTypeId, @Param("minutes") int minutes,
            @Param("effectiveFrom") LocalDate effectiveFrom, @Param("expiresOn") LocalDate expiresOn,
            @Param("source") LeaveSource source, @Param("leaveYear") Integer leaveYear,
            @Param("memo") String memo, @Param("grantedBy") Long grantedBy);

    /**
     * AUTO 연차 upsert — (tenant,user,type,leave_year) 유니크 충돌 시 분·유효구간 갱신.
     * 재계산이 반복돼도 행이 늘지 않고 최신 법정값으로 수렴한다.
     */
    @Insert("""
            INSERT INTO leave_grant (tenant_id, user_id, leave_type_id, minutes, effective_from,
                                     expires_on, source, leave_year, memo, granted_by)
            VALUES (#{tenantId}, #{userId}, #{leaveTypeId}, #{minutes}, #{effectiveFrom},
                    #{expiresOn}, 'AUTO', #{leaveYear}, #{memo}, #{grantedBy})
            ON DUPLICATE KEY UPDATE minutes = VALUES(minutes),
                                    effective_from = VALUES(effective_from),
                                    expires_on = VALUES(expires_on),
                                    memo = VALUES(memo),
                                    granted_by = VALUES(granted_by)
            """)
    int upsertAuto(@Param("tenantId") long tenantId, @Param("userId") long userId,
            @Param("leaveTypeId") long leaveTypeId, @Param("minutes") int minutes,
            @Param("effectiveFrom") LocalDate effectiveFrom, @Param("expiresOn") LocalDate expiresOn,
            @Param("leaveYear") Integer leaveYear, @Param("memo") String memo,
            @Param("grantedBy") Long grantedBy);
}
