package com.attendance.pro.holiday;

import java.time.LocalDate;
import java.util.List;

import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

/**
 * holiday 테이블 매퍼.
 * 테넌트 전파 규약(tenantId 첫 {@code @Param} + 전 쿼리 2중 조건) 준수.
 * 구 ScheduleMapper.findHolidayDates는 {@link #findHolidaysBetween}(date+name)으로 대체·이관됐다.
 */
@Mapper
public interface HolidayMapper {

    @Select("""
            SELECT tenant_id, holiday_date, holiday_name, holiday_type, created_at, updated_at
            FROM holiday
            WHERE tenant_id = #{tenantId}
              AND holiday_date >= #{from}
              AND holiday_date < #{to}
            ORDER BY holiday_date ASC
            """)
    List<Holiday> findByRange(@Param("tenantId") long tenantId,
            @Param("from") LocalDate from,
            @Param("to") LocalDate to);

    /**
     * 월별 상세(assembler) 공급용 — 날짜+명칭. 정렬 불요 조회.
     */
    @Select("""
            SELECT tenant_id, holiday_date, holiday_name, holiday_type, created_at, updated_at
            FROM holiday
            WHERE tenant_id = #{tenantId}
              AND holiday_date >= #{from}
              AND holiday_date < #{to}
            """)
    List<Holiday> findHolidaysBetween(@Param("tenantId") long tenantId,
            @Param("from") LocalDate from,
            @Param("to") LocalDate to);

    @Select("""
            SELECT tenant_id, holiday_date, holiday_name, holiday_type, created_at, updated_at
            FROM holiday
            WHERE tenant_id = #{tenantId} AND holiday_date = #{holidayDate}
            """)
    Holiday findByDate(@Param("tenantId") long tenantId, @Param("holidayDate") LocalDate holidayDate);

    /**
     * 수동 등록(항상 COMPANY) — IGNORE 아님: 중복은 DuplicateKeyException으로 노출(서비스가 409).
     */
    @Insert("""
            INSERT INTO holiday (tenant_id, holiday_date, holiday_name, holiday_type)
            VALUES (#{tenantId}, #{holidayDate}, #{holidayName}, 'COMPANY')
            """)
    int insert(@Param("tenantId") long tenantId,
            @Param("holidayDate") LocalDate holidayDate,
            @Param("holidayName") String holidayName);

    /**
     * 동기화 배치 삽입 — 같은 날짜에 COMPANY 행이 있으면 IGNORE로 건너뜀(COMPANY 우선 규칙의 구현).
     * 반환값 = 실제 삽입된 행 수.
     */
    @Insert("""
            <script>
            INSERT IGNORE INTO holiday (tenant_id, holiday_date, holiday_name, holiday_type)
            VALUES
            <foreach collection='entries' item='entry' separator=','>
                (#{tenantId}, #{entry.holidayDate}, #{entry.holidayName}, 'NATIONAL')
            </foreach>
            </script>
            """)
    int insertNationalIgnore(@Param("tenantId") long tenantId,
            @Param("entries") List<NationalHoliday> entries);

    /** 동기화 삽입용 항목. */
    record NationalHoliday(LocalDate holidayDate, String holidayName) {
    }

    /**
     * 명칭만 수정(유형 변경 불가 — NATIONAL↔COMPANY 전환은 삭제 후 재등록이 유일 경로).
     */
    @Update("""
            UPDATE holiday SET holiday_name = #{holidayName}
            WHERE tenant_id = #{tenantId} AND holiday_date = #{holidayDate}
            """)
    int updateName(@Param("tenantId") long tenantId,
            @Param("holidayDate") LocalDate holidayDate,
            @Param("holidayName") String holidayName);

    @Delete("""
            DELETE FROM holiday
            WHERE tenant_id = #{tenantId} AND holiday_date = #{holidayDate}
            """)
    int deleteByDate(@Param("tenantId") long tenantId, @Param("holidayDate") LocalDate holidayDate);

    /**
     * 해당 연도의 NATIONAL만 삭제(타 연도·COMPANY 불가침) — 재동기화의 교체 단계.
     */
    @Delete("""
            DELETE FROM holiday
            WHERE tenant_id = #{tenantId} AND holiday_type = 'NATIONAL'
              AND holiday_date >= #{from} AND holiday_date < #{to}
            """)
    int deleteNationalByYear(@Param("tenantId") long tenantId,
            @Param("from") LocalDate from,
            @Param("to") LocalDate to);

}
