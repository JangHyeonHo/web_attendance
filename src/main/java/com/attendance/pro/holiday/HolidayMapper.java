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

    String COLS = "holiday_id, tenant_id, holiday_date, holiday_name, holiday_type, recurring, "
            + "created_at, updated_at";

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
     * 반복 실체화(다른 연도 인스턴스)도 같은 경로 — recurring 플래그를 그대로 저장한다.
     */
    @Insert("""
            INSERT INTO holiday (tenant_id, holiday_date, holiday_name, holiday_type, recurring)
            VALUES (#{tenantId}, #{holidayDate}, #{holidayName}, 'COMPANY', #{recurring})
            """)
    @Options(useGeneratedKeys = true, keyProperty = "holidayId", keyColumn = "holiday_id")
    int insert(HolidayInsert holiday);

    /**
     * 회사 공휴일(COMPANY) 1건 수정 — 날짜/명칭/반복 플래그. 대리키 기준, type 한정으로
     * 국가 공휴일(NATIONAL)은 이 경로로 변경되지 않는다. 매칭 0이면 서비스가 404.
     */
    @org.apache.ibatis.annotations.Update("""
            UPDATE holiday
            SET holiday_date = #{holidayDate}, holiday_name = #{holidayName}, recurring = #{recurring}
            WHERE tenant_id = #{tenantId} AND holiday_id = #{holidayId}
              AND holiday_type = 'COMPANY'
            """)
    int updateCompany(@Param("tenantId") long tenantId, @Param("holidayId") long holidayId,
            @Param("holidayDate") LocalDate holidayDate, @Param("holidayName") String holidayName,
            @Param("recurring") boolean recurring);

    /**
     * 반복 지정된 회사 공휴일 전량(명칭별 실체화 템플릿 원본) — 날짜 내림차순(최신 정의 우선).
     * 서비스가 명칭 기준으로 중복 제거해 "명칭 → 최신 월-일" 템플릿을 만든다.
     */
    @Select("SELECT " + COLS + " FROM holiday "
            + "WHERE tenant_id = #{tenantId} AND holiday_type = 'COMPANY' AND recurring = TRUE "
            + "ORDER BY holiday_date DESC, holiday_id DESC")
    List<Holiday> findRecurringCompany(@Param("tenantId") long tenantId);

    /**
     * 동기화(NATIONAL 보유) 연도 집합 — 반복 공휴일을 실체화할 대상 연도들.
     */
    @Select("SELECT DISTINCT YEAR(holiday_date) FROM holiday "
            + "WHERE tenant_id = #{tenantId} AND holiday_type = 'NATIONAL'")
    List<Integer> syncedYears(@Param("tenantId") long tenantId);

    /**
     * 해당 연도에 같은 명칭 공휴일이 이미 있는지(반복 실체화 멱등 키 — 명칭·연도).
     */
    @Select("SELECT COUNT(*) FROM holiday "
            + "WHERE tenant_id = #{tenantId} AND holiday_name = #{holidayName} "
            + "  AND holiday_date >= #{from} AND holiday_date < #{to}")
    int countByNameInYear(@Param("tenantId") long tenantId, @Param("holidayName") String holidayName,
            @Param("from") LocalDate from, @Param("to") LocalDate to);

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

    /** 수동 등록용 파라미터(생성 키 회수). recurring은 반복 실체화 행에도 그대로 저장. */
    class HolidayInsert {
        private Long holidayId;
        private final long tenantId;
        private final LocalDate holidayDate;
        private final String holidayName;
        private final boolean recurring;

        public HolidayInsert(long tenantId, LocalDate holidayDate, String holidayName, boolean recurring) {
            this.tenantId = tenantId;
            this.holidayDate = holidayDate;
            this.holidayName = holidayName;
            this.recurring = recurring;
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

        public boolean isRecurring() {
            return recurring;
        }
    }

    /**
     * 회사 공휴일(COMPANY) 1건 삭제 — 대리키 기준. 국가 공휴일(NATIONAL)은 이 경로로 지워지지 않는다
     * (type 조건으로 차단 — 국가 공휴일은 동기화만 관리, #7). 매칭 0이면 서비스가 404.
     */
    @Delete("""
            DELETE FROM holiday
            WHERE tenant_id = #{tenantId} AND holiday_id = #{holidayId}
              AND holiday_type = 'COMPANY'
            """)
    int deleteCompanyById(@Param("tenantId") long tenantId, @Param("holidayId") long holidayId);

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
