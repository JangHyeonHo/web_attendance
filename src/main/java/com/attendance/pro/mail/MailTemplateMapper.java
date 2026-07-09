package com.attendance.pro.mail;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import com.attendance.pro.user.TokenPurpose;

/**
 * mail_template 테이블 매퍼.
 * 글로벌(제품) 테이블 — tenantId 규약 예외로 LanguageMapper와 동급.
 */
@Mapper
public interface MailTemplateMapper {

    @Select("""
            SELECT purpose, lang, subject, body, created_at, updated_at
            FROM mail_template
            ORDER BY purpose, lang
            """)
    List<MailTemplate> findAll();

    @Select("""
            SELECT purpose, lang, subject, body, created_at, updated_at
            FROM mail_template
            WHERE purpose = #{purpose} AND lang = #{lang}
            """)
    MailTemplate find(@Param("purpose") TokenPurpose purpose, @Param("lang") String lang);

    @Update("""
            UPDATE mail_template SET subject = #{subject}, body = #{body}
            WHERE purpose = #{purpose} AND lang = #{lang}
            """)
    int update(@Param("purpose") TokenPurpose purpose,
            @Param("lang") String lang,
            @Param("subject") String subject,
            @Param("body") String body);

}
