package com.attendance.pro.attendance;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * attendance / attendance_check 테이블 매퍼.
 */
@Mapper
public interface AttendanceMapper {

    /**
     * 최근 48시간 이내의 가장 최신 출결 스탬프.
     */
    @Select("""
            SELECT attendance_id, user_id, type AS type_code, status, stamped_at
            FROM attendance
            WHERE user_id = #{userId}
              AND stamped_at > NOW() - INTERVAL 48 HOUR
            ORDER BY stamped_at DESC, attendance_id DESC
            LIMIT 1
            """)
    AttendanceStamp findLatest(@Param("userId") long userId);

    /**
     * 최근 30일 이내의 가장 최신 출근 스탬프.
     */
    @Select("""
            SELECT attendance_id, user_id, type AS type_code, status, stamped_at
            FROM attendance
            WHERE user_id = #{userId}
              AND type = 1
              AND status = 0
              AND stamped_at > NOW() - INTERVAL 30 DAY
            ORDER BY stamped_at DESC, attendance_id DESC
            LIMIT 1
            """)
    AttendanceStamp findLatestGoToWork(@Param("userId") long userId);

    /**
     * 기간 내 출근/퇴근/조퇴 스탬프(월별 상세용, 시각 오름차순).
     */
    @Select("""
            SELECT attendance_id, user_id, type AS type_code, status, stamped_at
            FROM attendance
            WHERE user_id = #{userId}
              AND type IN (1, 2, 3)
              AND status = 0
              AND stamped_at >= #{from}
              AND stamped_at < #{to}
            ORDER BY stamped_at ASC, attendance_id ASC
            """)
    List<AttendanceStamp> findBetween(@Param("userId") long userId,
            @Param("from") LocalDate from,
            @Param("to") LocalDate to);

    @Insert("""
            INSERT INTO attendance (user_id, type, status, stamped_at, latitude, longitude, place_info, terminal)
            VALUES (#{userId}, #{typeCode}, #{status}, #{stampedAt},
                    #{latitude}, #{longitude}, #{placeInfo}, #{terminal})
            """)
    int insert(@Param("userId") long userId,
            @Param("typeCode") int typeCode,
            @Param("status") int status,
            @Param("stampedAt") LocalDateTime stampedAt,
            @Param("latitude") Double latitude,
            @Param("longitude") Double longitude,
            @Param("placeInfo") String placeInfo,
            @Param("terminal") String terminal);

    // ---- attendance_check (변조 방지 토큰) ----

    @Insert("""
            INSERT INTO attendance_check (token, user_id, payload_hash, confirm_code)
            VALUES (#{token}, #{userId}, #{payloadHash}, #{confirmCode})
            """)
    int insertCheck(@Param("token") String token,
            @Param("userId") long userId,
            @Param("payloadHash") String payloadHash,
            @Param("confirmCode") Integer confirmCode);

    @Select("""
            SELECT payload_hash
            FROM attendance_check
            WHERE token = #{token} AND user_id = #{userId}
            """)
    String findCheckHash(@Param("token") String token, @Param("userId") long userId);

    @Delete("DELETE FROM attendance_check WHERE token = #{token}")
    int deleteCheck(@Param("token") String token);

    /**
     * 하루가 지난 체크 토큰 정리(체크시마다 호출하는 지연 청소).
     */
    @Delete("DELETE FROM attendance_check WHERE created_at < NOW() - INTERVAL 1 DAY")
    int deleteExpiredChecks();

}
