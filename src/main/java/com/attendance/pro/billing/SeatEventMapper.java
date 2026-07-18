package com.attendance.pro.billing;

import java.time.LocalDate;
import java.util.List;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * seat_change_event 매퍼 — 좌석 변동 이벤트 로그(등록 시점 일할계산 재생용).
 * 활성 좌석 = ACTIVE·비삭제·SYSTEM_ADMIN 제외. 변동(증감)이 있을 때만 1건씩 append한다.
 */
@Mapper
public interface SeatEventMapper {

    /** 현재 활성 좌석 수(과금 대상). */
    @Select("""
            SELECT COUNT(*) FROM users
             WHERE tenant_id = #{tenantId} AND status = 'ACTIVE'
               AND deleted = FALSE AND role <> 'SYSTEM_ADMIN'
            """)
    int countActiveSeats(@Param("tenantId") long tenantId);

    /** 가장 최근 이벤트의 활성 좌석 수(없으면 null) — 중복 기록 방지용. */
    @Select("""
            SELECT active_seats FROM seat_change_event
             WHERE tenant_id = #{tenantId}
             ORDER BY event_date DESC, id DESC LIMIT 1
            """)
    Integer lastActiveSeats(@Param("tenantId") long tenantId);

    /** 좌석 변동 1건 기록. */
    @Insert("""
            INSERT INTO seat_change_event (tenant_id, event_date, active_seats)
            VALUES (#{tenantId}, #{eventDate}, #{activeSeats})
            """)
    int insert(@Param("tenantId") long tenantId, @Param("eventDate") LocalDate eventDate,
            @Param("activeSeats") int activeSeats);

    /** 지정일 "이전"(그 날 미포함)의 마지막 활성 좌석 수 — 그 달 기초좌석 산정용(없으면 null=0). */
    @Select("""
            SELECT active_seats FROM seat_change_event
             WHERE tenant_id = #{tenantId} AND event_date < #{firstDay}
             ORDER BY event_date DESC, id DESC LIMIT 1
            """)
    Integer activeBefore(@Param("tenantId") long tenantId, @Param("firstDay") LocalDate firstDay);

    /** 그 달[firstDay..lastDay] 안의 좌석 변동 이벤트(날짜·id 오름차순). */
    @Select("""
            SELECT event_date AS eventDate, active_seats AS activeSeats
              FROM seat_change_event
             WHERE tenant_id = #{tenantId} AND event_date BETWEEN #{firstDay} AND #{lastDay}
             ORDER BY event_date ASC, id ASC
            """)
    List<SeatEvent> inMonth(@Param("tenantId") long tenantId,
            @Param("firstDay") LocalDate firstDay, @Param("lastDay") LocalDate lastDay);
}
