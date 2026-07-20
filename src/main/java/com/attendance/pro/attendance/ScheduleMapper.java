package com.attendance.pro.attendance;

import java.time.LocalDate;
import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * work_schedule 전용 매퍼(구 findHolidayDates는 HolidayMapper.findHolidaysBetween으로 이관).
 * 테넌트 전파 규약(tenantId 첫 파라미터 + 2중 조건) 적용.
 */
@Mapper
public interface ScheduleMapper {

    @Select("""
            SELECT schedule_id, user_id, work_date, start_time, end_time,
                   crosses_midnight, off, holiday
            FROM work_schedule
            WHERE tenant_id = #{tenantId}
              AND user_id = #{userId}
              AND work_date >= #{from}
              AND work_date < #{to}
            ORDER BY work_date ASC
            """)
    List<WorkSchedule> findBetween(@Param("tenantId") long tenantId,
            @Param("userId") long userId,
            @Param("from") LocalDate from,
            @Param("to") LocalDate to);

    /**
     * 개인 기본 근무 시각 — users 테이블 SELECT지만 "그날의 스케줄 해석"이라는 근태 질의이므로
     * 스케줄 매퍼 소유(attendance→user 패키지 의존 회피).
     */
    @Select("""
            SELECT default_work_start AS `start`, default_work_end AS `end`, work_days
            FROM users
            WHERE tenant_id = #{tenantId} AND user_id = #{userId} AND deleted = FALSE
            """)
    WorkDefaults findWorkDefaults(@Param("tenantId") long tenantId, @Param("userId") long userId);

}
