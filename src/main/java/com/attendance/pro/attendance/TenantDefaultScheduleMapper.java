package com.attendance.pro.attendance;

import java.time.LocalTime;
import java.util.List;

import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 회사(테넌트) 신규 멤버 기본 스케줄 — 요일별 근무/휴무·시간 템플릿(V80).
 * 멤버 등록 시 이 템플릿이 그 멤버의 정기 스케줄(schedule_pattern)로 복제된다.
 */
@Mapper
public interface TenantDefaultScheduleMapper {

    @Select("""
            SELECT day_of_week AS dayOfWeek, off, start_time AS startTime,
                   end_time AS endTime, crosses_midnight AS crossesMidnight
            FROM tenant_default_schedule
            WHERE tenant_id = #{tenantId}
            ORDER BY day_of_week
            """)
    List<DefaultDay> findByTenant(@Param("tenantId") long tenantId);

    /** 템플릿 교체 저장 — 기존 삭제 후 7행 삽입(회사설정 편집용). */
    @Delete("DELETE FROM tenant_default_schedule WHERE tenant_id = #{tenantId}")
    int deleteByTenant(@Param("tenantId") long tenantId);

    @Insert("""
            <script>
            INSERT INTO tenant_default_schedule
                (tenant_id, day_of_week, off, start_time, end_time, crosses_midnight)
            VALUES
            <foreach collection='days' item='d' separator=','>
                (#{tenantId}, #{d.dayOfWeek}, #{d.off}, #{d.startTime}, #{d.endTime}, #{d.crossesMidnight})
            </foreach>
            </script>
            """)
    int insertDays(@Param("tenantId") long tenantId, @Param("days") List<DefaultDay> days);

    /** 요일별 기본 스케줄 1행(월~일). */
    record DefaultDay(int dayOfWeek, boolean off, LocalTime startTime, LocalTime endTime,
            boolean crossesMidnight) {
    }
}
