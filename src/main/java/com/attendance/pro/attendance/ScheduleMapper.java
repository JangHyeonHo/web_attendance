package com.attendance.pro.attendance;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
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

    /**
     * 월 로타(일자 오버라이드) 범위 삭제 — 저장 시 그 달을 통째로 대체(#13).
     */
    @Delete("""
            DELETE FROM work_schedule
            WHERE tenant_id = #{tenantId} AND user_id = #{userId}
              AND work_date >= #{from} AND work_date < #{to}
            """)
    int deleteRotaInRange(@Param("tenantId") long tenantId, @Param("userId") long userId,
            @Param("from") LocalDate from, @Param("to") LocalDate to);

    /**
     * 월 로타 셀 벌크 삽입(#13) — 관리자가 지정한 날짜만. 미지정일은 개인 기본 스케줄로 폴백(행 없음).
     * uk(user_id, work_date) 중복은 최신값으로 갱신(같은 저장 내 중복 방어).
     */
    @Insert("""
            <script>
            INSERT INTO work_schedule
                (tenant_id, user_id, work_date, start_time, end_time, crosses_midnight, off, holiday)
            VALUES
            <foreach collection='cells' item='c' separator=','>
                (#{tenantId}, #{userId}, #{c.workDate}, #{c.startTime}, #{c.endTime},
                 #{c.crossesMidnight}, #{c.off}, #{c.holiday})
            </foreach>
            ON DUPLICATE KEY UPDATE
                start_time = VALUES(start_time), end_time = VALUES(end_time),
                crosses_midnight = VALUES(crosses_midnight), off = VALUES(off), holiday = VALUES(holiday)
            </script>
            """)
    int upsertRota(@Param("tenantId") long tenantId, @Param("userId") long userId,
            @Param("cells") List<RotaCell> cells);

    /** 로타 셀 파라미터(일자 오버라이드 1행). off=휴무, crossesMidnight=야간교대. */
    record RotaCell(LocalDate workDate, LocalTime startTime, LocalTime endTime,
            boolean crossesMidnight, boolean off, boolean holiday) {
    }

}
