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
            SELECT user_id, tenant_id, email, password_hash, name, depart_cd,
                   role, status, deleted, created_at, updated_at
            FROM users
            WHERE tenant_id = #{tenantId} AND email = #{email} AND deleted = FALSE
            """)
    User findByEmail(@Param("tenantId") long tenantId, @Param("email") String email);

    /**
     * 2중 조건 — 타 테넌트 userId는 null(호출부에서 404, 존재 비노출).
     */
    @Select("""
            SELECT user_id, tenant_id, email, password_hash, name, depart_cd,
                   role, status, deleted, created_at, updated_at
            FROM users
            WHERE tenant_id = #{tenantId} AND user_id = #{userId} AND deleted = FALSE
            """)
    User findById(@Param("tenantId") long tenantId, @Param("userId") long userId);

    @Select("""
            SELECT user_id, tenant_id, email, password_hash, name, depart_cd,
                   role, status, deleted, created_at, updated_at
            FROM users
            WHERE tenant_id = #{tenantId} AND deleted = FALSE
            ORDER BY name ASC, user_id ASC
            """)
    List<User> findByTenant(@Param("tenantId") long tenantId);

    @Insert("""
            INSERT INTO users (tenant_id, email, password_hash, name, depart_cd, role, status)
            VALUES (#{tenantId}, #{email}, #{passwordHash}, #{name}, #{departCd}, #{role}, #{status})
            """)
    @Options(useGeneratedKeys = true, keyProperty = "userId", keyColumn = "user_id")
    int insert(UserCreate user);

    @Select("SELECT EXISTS(SELECT 1 FROM users WHERE tenant_id = #{tenantId} AND email = #{email})")
    boolean existsByEmail(@Param("tenantId") long tenantId, @Param("email") String email);

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

}
