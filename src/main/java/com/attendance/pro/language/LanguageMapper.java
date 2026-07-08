package com.attendance.pro.language;

import java.util.List;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * language_master 테이블 매퍼.
 */
@Mapper
public interface LanguageMapper {

    @Select("""
            <script>
            SELECT language_id, window_id, lang_key, lang, lang_value
            FROM language_master
            <where>
                <if test='windowId != null'>AND window_id = #{windowId}</if>
                <if test='lang != null'>AND lang = #{lang}</if>
            </where>
            ORDER BY window_id, lang_key, lang
            </script>
            """)
    List<LanguageEntry> find(@Param("windowId") String windowId, @Param("lang") String lang);

    /**
     * 등록(동일 키가 있으면 값 갱신).
     */
    @Insert("""
            INSERT INTO language_master (window_id, lang_key, lang, lang_value)
            VALUES (#{windowId}, #{langKey}, #{lang}, #{langValue})
            ON DUPLICATE KEY UPDATE lang_value = #{langValue}
            """)
    int upsert(@Param("windowId") String windowId,
            @Param("langKey") String langKey,
            @Param("lang") String lang,
            @Param("langValue") String langValue);

}
