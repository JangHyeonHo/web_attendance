package com.attendance.pro.attendance;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

/**
 * attendance / attendance_check 테이블 매퍼.
 *
 * 테넌트 전파 규약: 모든 메소드는 {@code @Param("tenantId")}를 첫 파라미터로 받고
 * user_id 조건에도 {@code AND tenant_id = #{tenantId}}를 병기한다(2중 조건).
 * 예외: {@link #deleteExpiredChecks()} — 시간 조건만의 전 테넌트 청소(데이터 반환 없음).
 */
@Mapper
public interface AttendanceMapper {

    /**
     * 최근 48시간 이내의 가장 최신 출결 스탬프.
     */
    @Select("""
            SELECT attendance_id, user_id, type AS type_code, status, stamped_at,
                   source, reason_code, reason_text
            FROM attendance
            WHERE tenant_id = #{tenantId}
              AND user_id = #{userId}
              AND stamped_at > NOW() - INTERVAL 48 HOUR
            ORDER BY stamped_at DESC, attendance_id DESC
            LIMIT 1
            """)
    AttendanceStamp findLatest(@Param("tenantId") long tenantId, @Param("userId") long userId);

    /**
     * 최근 30일 이내의 가장 최신 출근 스탬프.
     */
    @Select("""
            SELECT attendance_id, user_id, type AS type_code, status, stamped_at,
                   source, reason_code, reason_text
            FROM attendance
            WHERE tenant_id = #{tenantId}
              AND user_id = #{userId}
              AND type = 1
              AND status = 0
              AND stamped_at > NOW() - INTERVAL 30 DAY
            ORDER BY stamped_at DESC, attendance_id DESC
            LIMIT 1
            """)
    AttendanceStamp findLatestGoToWork(@Param("tenantId") long tenantId, @Param("userId") long userId);

    /**
     * 기간 내 전 타입 스탬프(월별 상세용, 시각 오름차순).
     * BREAK(type 4, status 0=시작/1=종료) 포함 — 실휴식 페어링(work-schedule §4)의 입력이 된다.
     * (구현 주: 구 쿼리는 type IN (1,2,3) AND status=0 필터였음 — 타입 1~3은 항상 status 0이므로
     *  필터 제거는 기존 표시 동작 불변 + BREAK 행 추가만.)
     */
    @Select("""
            SELECT attendance_id, user_id, type AS type_code, status, stamped_at,
                   source, reason_code, reason_text
            FROM attendance
            WHERE tenant_id = #{tenantId}
              AND user_id = #{userId}
              AND stamped_at >= #{from}
              AND stamped_at < #{to}
            ORDER BY stamped_at ASC, attendance_id ASC
            """)
    List<AttendanceStamp> findBetween(@Param("tenantId") long tenantId,
            @Param("userId") long userId,
            @Param("from") LocalDate from,
            @Param("to") LocalDate to);

    @Insert("""
            INSERT INTO attendance (tenant_id, user_id, type, status, stamped_at,
                                    latitude, longitude, place_info, terminal,
                                    source, reason_code, reason_text)
            VALUES (#{tenantId}, #{userId}, #{typeCode}, #{status}, #{stampedAt},
                    #{latitude}, #{longitude}, #{placeInfo}, #{terminal},
                    #{source}, #{reasonCode}, #{reasonText})
            """)
    int insert(@Param("tenantId") long tenantId,
            @Param("userId") long userId,
            @Param("typeCode") int typeCode,
            @Param("status") int status,
            @Param("stampedAt") LocalDateTime stampedAt,
            @Param("latitude") Double latitude,
            @Param("longitude") Double longitude,
            @Param("placeInfo") String placeInfo,
            @Param("terminal") String terminal,
            @Param("source") StampSource source,
            @Param("reasonCode") String reasonCode,
            @Param("reasonText") String reasonText);

    /**
     * 수정/삭제 전 대상 확인 — 본인 + MANUAL 행만 반환(자동 기록·타인·미존재는 null → 404).
     */
    @Select("""
            SELECT attendance_id, user_id, type AS type_code, status, stamped_at,
                   source, reason_code, reason_text
            FROM attendance
            WHERE attendance_id = #{attendanceId}
              AND tenant_id = #{tenantId}
              AND user_id = #{userId}
              AND source = 'MANUAL'
            """)
    AttendanceStamp findManualById(@Param("tenantId") long tenantId,
            @Param("userId") long userId,
            @Param("attendanceId") long attendanceId);

    /**
     * 수동 정정 스탬프 수정(잘못 입력 복구 — 본인 + MANUAL 행만, 시각/구분/사유 변경).
     * AUTO 행은 불변 — 조건 불일치는 0행(호출부에서 404, 존재 비노출).
     */
    @Update("""
            UPDATE attendance
            SET type = #{typeCode}, stamped_at = #{stampedAt},
                reason_code = #{reasonCode}, reason_text = #{reasonText}
            WHERE attendance_id = #{attendanceId}
              AND tenant_id = #{tenantId}
              AND user_id = #{userId}
              AND source = 'MANUAL'
            """)
    int updateManual(@Param("tenantId") long tenantId,
            @Param("userId") long userId,
            @Param("attendanceId") long attendanceId,
            @Param("typeCode") int typeCode,
            @Param("stampedAt") LocalDateTime stampedAt,
            @Param("reasonCode") String reasonCode,
            @Param("reasonText") String reasonText);

    // ---- attendance_check (변조 방지 토큰) ----

    @Insert("""
            INSERT INTO attendance_check (token, tenant_id, user_id, payload_hash, confirm_code)
            VALUES (#{token}, #{tenantId}, #{userId}, #{payloadHash}, #{confirmCode})
            """)
    int insertCheck(@Param("tenantId") long tenantId,
            @Param("token") String token,
            @Param("userId") long userId,
            @Param("payloadHash") String payloadHash,
            @Param("confirmCode") Integer confirmCode);

    /**
     * 크로스 테넌트 토큰 방어 지점 — 세션의 tenantId와 불일치하면 "존재하지 않는" 토큰으로 처리된다.
     */
    @Select("""
            SELECT payload_hash
            FROM attendance_check
            WHERE token = #{token} AND user_id = #{userId} AND tenant_id = #{tenantId}
            """)
    String findCheckHash(@Param("tenantId") long tenantId,
            @Param("token") String token,
            @Param("userId") long userId);

    /**
     * 3중 조건 — 같은 테넌트 내 타 유저 토큰 무효화 허점까지 봉쇄.
     */
    @Delete("""
            DELETE FROM attendance_check
            WHERE token = #{token} AND user_id = #{userId} AND tenant_id = #{tenantId}
            """)
    int deleteCheck(@Param("tenantId") long tenantId,
            @Param("token") String token,
            @Param("userId") long userId);

    /**
     * TTL(30분)이 지난 체크 토큰 정리(체크시마다 호출하는 지연 청소).
     * 전 테넌트 대상 시스템 청소가 올바른 동작이므로 tenantId를 받지 않는다.
     */
    @Delete("DELETE FROM attendance_check WHERE created_at < NOW() - INTERVAL 30 MINUTE")
    int deleteExpiredChecks();

}
