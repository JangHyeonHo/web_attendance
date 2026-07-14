package com.attendance.pro.user;

import java.util.List;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

/**
 * users 테이블 매퍼.
 * record 매핑을 위해 생성자 인자명 기반 자동매핑
 * (mybatis.configuration.arg-name-based-constructor-auto-mapping=true)을 사용한다.
 *
 * 테넌트 전파 규약: 테넌트 소유 테이블을 만지는 모든 메소드는
 * {@code @Param("tenantId")}를 첫 파라미터로 받고, 조건에 {@code tenant_id = #{tenantId}}를 병기한다.
 */
@Mapper
public interface UserMapper {

    @Select("""
            SELECT user_id, tenant_id, email, password_hash, password_changed_at, name, depart_cd,
                   default_work_start, default_work_end, work_days, hire_date,
                   role, status, deleted, created_at, updated_at
            FROM users
            WHERE tenant_id = #{tenantId} AND email = #{email} AND deleted = FALSE
            """)
    User findByEmail(@Param("tenantId") long tenantId, @Param("email") String email);

    /**
     * 2중 조건 — 타 테넌트 userId는 null(호출부에서 404, 존재 비노출).
     */
    @Select("""
            SELECT user_id, tenant_id, email, password_hash, password_changed_at, name, depart_cd,
                   default_work_start, default_work_end, work_days, hire_date,
                   role, status, deleted, created_at, updated_at
            FROM users
            WHERE tenant_id = #{tenantId} AND user_id = #{userId} AND deleted = FALSE
            """)
    User findById(@Param("tenantId") long tenantId, @Param("userId") long userId);

    /**
     * 멤버 관리 화면용 목록. SYSTEM_ADMIN은 테넌트 멤버가 아니므로 제외한다
     * (V4 이관으로 DEFAULT 테넌트에 공존해도 TENANT_ADMIN에게 노출/조작 대상이 아님).
     */
    @Select("""
            SELECT user_id, tenant_id, email, password_hash, password_changed_at, name, depart_cd,
                   default_work_start, default_work_end, work_days, hire_date,
                   role, status, deleted, created_at, updated_at
            FROM users
            WHERE tenant_id = #{tenantId} AND deleted = FALSE
              AND role <> 'SYSTEM_ADMIN'
            ORDER BY name ASC, user_id ASC
            """)
    List<User> findByTenant(@Param("tenantId") long tenantId);

    @Insert("""
            INSERT INTO users (tenant_id, email, password_hash, name, depart_cd,
                               default_work_start, default_work_end, hire_date, role, status)
            VALUES (#{tenantId}, #{email}, #{passwordHash}, #{name}, #{departCd},
                    #{defaultWorkStart}, #{defaultWorkEnd}, COALESCE(#{hireDate}, CURDATE()), #{role}, #{status})
            """)
    @Options(useGeneratedKeys = true, keyProperty = "userId", keyColumn = "user_id")
    int insert(UserCreate user);

    /**
     * 활성 행 기준 중복 검사 — deleted 필터로 [E4] UNIQUE(email_key)와 판정 기준을 일치시킨다(CR3-8).
     */
    @Select("""
            SELECT EXISTS(SELECT 1 FROM users
                          WHERE tenant_id = #{tenantId} AND email = #{email} AND deleted = FALSE)
            """)
    boolean existsByEmail(@Param("tenantId") long tenantId, @Param("email") String email);

    /**
     * 비밀번호 교체 — password_changed_at을 SQL에서 동시 세팅(시각 이원화 방지).
     * 이 시각 이전 발급 세션은 SessionRevalidationInterceptor가 회수한다.
     */
    @Update("""
            UPDATE users SET password_hash = #{passwordHash}, password_changed_at = NOW(6)
            WHERE tenant_id = #{tenantId} AND user_id = #{userId} AND deleted = FALSE
            """)
    int updatePassword(@Param("tenantId") long tenantId, @Param("userId") long userId,
            @Param("passwordHash") String passwordHash);

    /**
     * 활성 TENANT_ADMIN 수(마지막 관리자 보호용).
     * 동시 상호 강등 레이스에서 0명 테넌트가 되지 않도록 FOR UPDATE로 행을 잠근다.
     */
    @Select("""
            SELECT COUNT(*)
            FROM users
            WHERE tenant_id = #{tenantId} AND role = 'TENANT_ADMIN'
              AND status = 'ACTIVE' AND deleted = FALSE
            FOR UPDATE
            """)
    int countActiveTenantAdmins(@Param("tenantId") long tenantId);

    @Update("""
            UPDATE users SET role = #{role}
            WHERE tenant_id = #{tenantId} AND user_id = #{userId} AND deleted = FALSE
            """)
    int updateRole(@Param("tenantId") long tenantId, @Param("userId") long userId, @Param("role") Role role);

    @Update("""
            UPDATE users SET status = #{status}
            WHERE tenant_id = #{tenantId} AND user_id = #{userId} AND deleted = FALSE
            """)
    int updateStatus(@Param("tenantId") long tenantId, @Param("userId") long userId,
            @Param("status") UserStatus status);

    /**
     * 개인 기본 근무 스케줄 수정 — 2중 조건(테넌트 전파 규약).
     */
    @Update("""
            UPDATE users SET default_work_start = #{workStart}, default_work_end = #{workEnd},
                             work_days = #{workDays}
            WHERE tenant_id = #{tenantId} AND user_id = #{userId} AND deleted = FALSE
            """)
    int updateWorkSchedule(@Param("tenantId") long tenantId, @Param("userId") long userId,
            @Param("workStart") java.time.LocalTime workStart,
            @Param("workEnd") java.time.LocalTime workEnd,
            @Param("workDays") String workDays);

    /**
     * 로그인 성공 시 현재 유효 세션 토큰 교체(단일 세션 강제) — 이전 세션 스냅샷 토큰과 달라져
     * 다음 요청에 SessionRevalidationInterceptor가 이전 세션을 회수한다.
     */
    @Update("""
            UPDATE users SET session_token = #{sessionToken}
            WHERE tenant_id = #{tenantId} AND user_id = #{userId} AND deleted = FALSE
            """)
    int updateSessionToken(@Param("tenantId") long tenantId, @Param("userId") long userId,
            @Param("sessionToken") String sessionToken);

    /**
     * 요청 단위 세션 재검증에 필요한 최소 필드만 한 번에 조회(SessionRevalidationInterceptor 전용).
     * status·role·password_changed_at·session_token을 함께 읽어 재검증의 DB 왕복을 1건으로 줄인다
     * (전체 User 조회 + 별도 토큰 조회 2건 → 1건). 미식별 유저는 null.
     */
    @Select("""
            SELECT status, role, password_changed_at, session_token
            FROM users
            WHERE tenant_id = #{tenantId} AND user_id = #{userId} AND deleted = FALSE
            """)
    RevalidationState findRevalidationState(@Param("tenantId") long tenantId, @Param("userId") long userId);

    /** 세션 재검증용 스냅샷(계정 상태·role·비번 변경시각·단일 세션 토큰). */
    record RevalidationState(
            UserStatus status,
            Role role,
            java.time.LocalDateTime passwordChangedAt,
            String sessionToken) {
    }

    /**
     * 입사일 수정(연차 자동계산 기산 보정) — 2중 조건. 휴가 관리 화면에서 관리자가 조정.
     */
    @Update("""
            UPDATE users SET hire_date = #{hireDate}
            WHERE tenant_id = #{tenantId} AND user_id = #{userId} AND deleted = FALSE
            """)
    int updateHireDate(@Param("tenantId") long tenantId, @Param("userId") long userId,
            @Param("hireDate") java.time.LocalDate hireDate);

    /**
     * 소프트 삭제 — 출결 기록 보존(FK user_id 잔존). email_key 생성 컬럼이 NULL이 되어
     * 같은 이메일 재등록이 가능해진다(V7 [E4]).
     */
    @Update("""
            UPDATE users SET deleted = TRUE
            WHERE tenant_id = #{tenantId} AND user_id = #{userId} AND deleted = FALSE
            """)
    int softDelete(@Param("tenantId") long tenantId, @Param("userId") long userId);

}
