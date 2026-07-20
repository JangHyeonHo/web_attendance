package com.attendance.pro.attendance;

import java.util.List;

import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 반복 근무 패턴 매퍼(#13). 테넌트 전파 규약 준수. 슬롯은 FK ON DELETE CASCADE로 함께 정리.
 */
@Mapper
public interface SchedulePatternMapper {

    @Select("""
            SELECT pattern_id, tenant_id, user_id, cycle_weeks, anchor_monday, active, created_at, updated_at
            FROM schedule_pattern
            WHERE tenant_id = #{tenantId} AND user_id = #{userId} AND active = TRUE
            """)
    SchedulePattern findByUser(@Param("tenantId") long tenantId, @Param("userId") long userId);

    @Select("""
            SELECT pattern_id, week_index, day_of_week, off, start_time, end_time, crosses_midnight
            FROM schedule_pattern_slot
            WHERE pattern_id = #{patternId}
            """)
    List<SchedulePatternSlot> findSlots(@Param("patternId") long patternId);

    /** 사람의 기존 패턴 삭제(슬롯은 CASCADE). 저장 시 교체용. */
    @Delete("DELETE FROM schedule_pattern WHERE tenant_id = #{tenantId} AND user_id = #{userId}")
    int deleteByUser(@Param("tenantId") long tenantId, @Param("userId") long userId);

    @Insert("""
            INSERT INTO schedule_pattern (tenant_id, user_id, cycle_weeks, anchor_monday, active)
            VALUES (#{tenantId}, #{userId}, #{cycleWeeks}, #{anchorMonday}, TRUE)
            """)
    @Options(useGeneratedKeys = true, keyProperty = "patternId", keyColumn = "pattern_id")
    int insertPattern(PatternInsert pattern);

    @Insert("""
            <script>
            INSERT INTO schedule_pattern_slot
                (pattern_id, week_index, day_of_week, off, start_time, end_time, crosses_midnight)
            VALUES
            <foreach collection='slots' item='s' separator=','>
                (#{patternId}, #{s.weekIndex}, #{s.dayOfWeek}, #{s.off},
                 #{s.startTime}, #{s.endTime}, #{s.crossesMidnight})
            </foreach>
            </script>
            """)
    int insertSlots(@Param("patternId") long patternId,
            @Param("slots") List<SchedulePatternSlot> slots);

    /** 패턴 생성 파라미터(생성 키 회수). */
    class PatternInsert {
        private Long patternId;
        private final long tenantId;
        private final long userId;
        private final int cycleWeeks;
        private final java.time.LocalDate anchorMonday;

        public PatternInsert(long tenantId, long userId, int cycleWeeks, java.time.LocalDate anchorMonday) {
            this.tenantId = tenantId;
            this.userId = userId;
            this.cycleWeeks = cycleWeeks;
            this.anchorMonday = anchorMonday;
        }

        public Long getPatternId() {
            return patternId;
        }

        public void setPatternId(Long patternId) {
            this.patternId = patternId;
        }

        public long getTenantId() {
            return tenantId;
        }

        public long getUserId() {
            return userId;
        }

        public int getCycleWeeks() {
            return cycleWeeks;
        }

        public java.time.LocalDate getAnchorMonday() {
            return anchorMonday;
        }
    }
}
