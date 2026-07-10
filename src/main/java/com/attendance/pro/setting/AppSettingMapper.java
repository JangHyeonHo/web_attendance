package com.attendance.pro.setting;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * app_setting 테이블 매퍼(시스템 전역 키-값 — 테넌트 소속 없음).
 */
@Mapper
public interface AppSettingMapper {

    @Select("SELECT setting_value FROM app_setting WHERE setting_key = #{key}")
    String findValue(@Param("key") String key);

    @Insert("""
            INSERT INTO app_setting (setting_key, setting_value)
            VALUES (#{key}, #{value})
            ON DUPLICATE KEY UPDATE setting_value = #{value}
            """)
    int upsert(@Param("key") String key, @Param("value") String value);

}
