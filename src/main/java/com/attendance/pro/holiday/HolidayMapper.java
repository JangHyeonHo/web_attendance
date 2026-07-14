package com.attendance.pro.holiday;

import java.time.LocalDate;
import java.util.List;

import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * holiday 테이블 매퍼.
 * 테넌트 전파 규약(tenantId 첫 {@code @Param} + 전 쿼리 2중 조건) 준수.
 * V29부터 대리키(holiday_id) — 같은 날짜 중복 허용(읽기전용, 개별 수정/삭제 없음).
 */
@Mapper
public interface HolidayMapper {

    String COLS = "holiday_id, tenant_id, holiday_date, holiday_name, holiday_type, created_at, updated_at";

    @Select("SELECT " + COLS + " FROM holiday "
            + "WHERE tenant_id = #{tenantId} AND holiday_date >= #{from} AND holiday_date < #{to} "
            + "ORDER BY holiday_date ASC, holiday_id ASC")
    List<Holiday> findByRange(@Param("tenantId") long tenantId,
            @Param("from") LocalDate from,
            @Param("to") LocalDate to);

    /**
     * 월별 상세(assembler) 공급용 — 날짜+명칭. 정렬 불요 조회.
     */
    @Select("SELECT " + COLS + " FROM holiday "
            + "WHERE tenant_id = #{tenantId} AND holiday_date >= #{from} AND holiday_date < #{to}")
    List<Holiday> findHolidaysBetween(@Param("tenantId") long tenantId,
            @Param("from") LocalDate from,
            @Param("to") LocalDate to);

    @Select("SELECT " + COLS + " FROM holiday "
            + "WHERE tenant_id = #{tenantId} AND holiday_id = #{holidayId}")
    Holiday findById(@Param("tenantId") long tenantId, @Param("holidayId") long holidayId);

    /**
     * 수동 등록(항상 COMPANY) — 날짜 중복 허용(대리키). 생성 키를 holidayId로 회수.
     */
    @Insert("""
            INSERT INTO holiday (tenant_id, holiday_date, holiday_name, holiday_type)
            VALUES (#{tenantId}, #{holidayDate}, #{holidayName}, 'COMPANY')
            """)
    @Options(useGeneratedKeys = true, keyProperty = "holidayId", keyColumn = "holiday_id")
    int insert(HolidayInsert holiday);

    /**
     * 동기화 배치 삽입 — 해당 연도 NATIONAL 삭제 후 신규 삽입(COMPANY와 날짜 공존 허용).
     * 반환값 = 삽입된 행 수.
     */
    @Insert("""
            <script>
            INSERT INTO holiday (tenant_id, holiday_date, holiday_name, holiday_type)
            VALUES
            <foreach collection='entries' item='entry' separator=','>
                (#{tenantId}, #{entry.holidayDate}, #{entry.holidayName}, 'NATIONAL')
            </foreach>
            </script>
            """)
    int insertNational(@Param("tenantId") long tenantId,
            @Param("entries") List<NationalHoliday> entries);

    /** 동기화 삽입용 항목. */
    record NationalHoliday(LocalDate holidayDate, String holidayName) {
    }

    /** 수동 등록용 파라미터(생성 키 회수). */
    class HolidayInsert {
        private Long holidayId;
        private final long tenantId;
        private final LocalDate holidayDate;
        private final String holidayName;

        public HolidayInsert(long tenantId, LocalDate holidayDate, String holidayName) {
            this.tenantId = tenantId;
            this.holidayDate = holidayDate;
            this.holidayName = holidayName;
        }

        public Long getHolidayId() {
            return holidayId;
        }

        public void setHolidayId(Long holidayId) {
            this.holidayId = holidayId;
        }

        public long getTenantId() {
            return tenantId;
        }

        public LocalDate getHolidayDate() {
            return holidayDate;
        }

        public String getHolidayName() {
            return holidayName;
        }
    }

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
