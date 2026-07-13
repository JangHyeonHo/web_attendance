package com.attendance.pro.billing;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * tenant_seat_usage 매퍼(월중 최대 활성 좌석 high-water mark).
 * touch는 현재 활성 좌석 수를 서버에서 세어 GREATEST로만 올린다 — 그 달의 피크를 보존한다.
 */
@Mapper
public interface SeatUsageMapper {

    /**
     * 현재 활성 좌석 수(ACTIVE·비삭제·SYSTEM_ADMIN 제외)를 세어 해당 월 max_seats를 GREATEST로 갱신.
     * 원자적(단일 문) — 동시 갱신에도 피크가 낮아지지 않는다.
     */
    @Insert("""
            INSERT INTO tenant_seat_usage (tenant_id, ym, max_seats)
            SELECT #{tenantId}, #{ym},
                   (SELECT COUNT(*) FROM users
                     WHERE tenant_id = #{tenantId} AND status = 'ACTIVE' AND deleted = FALSE
                       AND role <> 'SYSTEM_ADMIN')
            ON DUPLICATE KEY UPDATE max_seats = GREATEST(max_seats, VALUES(max_seats))
            """)
    int touch(@Param("tenantId") long tenantId, @Param("ym") String ym);

    /** 그 달의 기록된 최대 좌석 수(없으면 null). */
    @Select("""
            SELECT max_seats FROM tenant_seat_usage
            WHERE tenant_id = #{tenantId} AND ym = #{ym}
            """)
    Integer findMaxSeats(@Param("tenantId") long tenantId, @Param("ym") String ym);
}
