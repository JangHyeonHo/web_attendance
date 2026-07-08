package com.attendance.pro.attendance;

import java.time.LocalDate;
import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * work_schedule / holiday 테이블 매퍼.
 */
@Mapper
public interface ScheduleMapper {

    @Select("""
            SELECT schedule_id, user_id, work_date, start_time, end_time, holiday
            FROM work_schedule
            WHERE user_id = #{userId}
              AND work_date >= #{from}
              AND work_date < #{to}
            ORDER BY work_date ASC
            """)
    List<WorkSchedule> findBetween(@Param("userId") long userId,
            @Param("from") LocalDate from,
            @Param("to") LocalDate to);

    @Select("""
            SELECT holiday_date
            FROM holiday
            WHERE holiday_date >= #{from}
              AND holiday_date < #{to}
            """)
    List<LocalDate> findHolidayDates(@Param("from") LocalDate from, @Param("to") LocalDate to);

}
