package com.attendance.pro.user;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * users 테이블 매퍼.
 * record 매핑을 위해 생성자 인자명 기반 자동매핑
 * (mybatis.configuration.arg-name-based-constructor-auto-mapping=true)을 사용한다.
 */
@Mapper
public interface UserMapper {

    @Select("""
            SELECT user_id, email, password_hash, name, depart_cd,
                   is_admin AS admin, deleted, created_at, updated_at
            FROM users
            WHERE email = #{email} AND deleted = FALSE
            """)
    User findByEmail(@Param("email") String email);

    @Insert("""
            INSERT INTO users (email, password_hash, name, depart_cd, is_admin)
            VALUES (#{email}, #{passwordHash}, #{name}, #{departCd}, #{admin})
            """)
    @Options(useGeneratedKeys = true, keyProperty = "userId", keyColumn = "user_id")
    int insert(UserCreate user);

    @Select("SELECT EXISTS(SELECT 1 FROM users WHERE email = #{email})")
    boolean existsByEmail(@Param("email") String email);

}
