package com.attendance.pro.user;

import java.time.LocalDateTime;
import java.util.List;

import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

/**
 * user_token 테이블 매퍼.
 * 테넌트 전파 규약(tenantId 첫 {@code @Param} + 2중 조건) 준수.
 * 예외: {@link #findByHash}는 해시가 전역 유일 PK이므로 규약의 예외로 명기 — 토큰 행이 tenant_id를 보유한다.
 */
@Mapper
public interface UserTokenMapper {

    @Insert("""
            INSERT INTO user_token (tenant_id, token_hash, user_id, purpose, expires_at)
            VALUES (#{tenantId}, #{tokenHash}, #{userId}, #{purpose}, #{expiresAt})
            """)
    int insert(@Param("tenantId") long tenantId,
            @Param("tokenHash") String tokenHash,
            @Param("userId") long userId,
            @Param("purpose") TokenPurpose purpose,
            @Param("expiresAt") LocalDateTime expiresAt);

    /**
     * 해시 PK 단건 조회(전역 유일 — tenantId 규약 예외). 토큰이 곧 테넌트 스코프다.
     */
    @Select("""
            SELECT token_hash, tenant_id, user_id, purpose, expires_at, used_at, created_at
            FROM user_token
            WHERE token_hash = #{tokenHash}
            """)
    UserToken findByHash(@Param("tokenHash") String tokenHash);

    @Update("""
            UPDATE user_token SET used_at = NOW()
            WHERE tenant_id = #{tenantId} AND token_hash = #{tokenHash} AND used_at IS NULL
            """)
    int markUsed(@Param("tenantId") long tenantId, @Param("tokenHash") String tokenHash);

    /**
     * 유저의 유효(미사용) 토큰 전멸 — 정지·삭제·설정 성공 시의 무효화.
     * used_at이 찍힌 행은 이미 사용 불능이므로 감사 흔적으로 남긴다(만료 청소가 최종 정리).
     */
    @Delete("""
            DELETE FROM user_token
            WHERE tenant_id = #{tenantId} AND user_id = #{userId} AND used_at IS NULL
            """)
    int deleteByUser(@Param("tenantId") long tenantId, @Param("userId") long userId);

    /**
     * 같은 (user, purpose)의 기존 행 제거 — 재발급 시 "살아있는 링크는 최신 1개 이하" 불변식.
     */
    @Delete("""
            DELETE FROM user_token
            WHERE tenant_id = #{tenantId} AND user_id = #{userId} AND purpose = #{purpose}
            """)
    int deleteByUserAndPurpose(@Param("tenantId") long tenantId,
            @Param("userId") long userId,
            @Param("purpose") TokenPurpose purpose);

    /**
     * 만료 30일 경과 토큰 청소(발급 시 부수 실행 — deleteExpiredChecks 패턴 계승).
     * 30일 보존은 오송신 감사 추적용. 전 테넌트 시스템 청소라 tenantId를 받지 않는다.
     */
    @Delete("DELETE FROM user_token WHERE expires_at < NOW() - INTERVAL 30 DAY")
    int deleteExpired();

    /** 유효 INVITE 토큰의 유저별 만료 시각(멤버 목록의 inviteExpiresAt 조립용). */
    record InviteExpiry(long userId, LocalDateTime expiresAt) {
    }

    @Select("""
            SELECT user_id, expires_at
            FROM user_token
            WHERE tenant_id = #{tenantId} AND purpose = 'INVITE'
              AND used_at IS NULL AND expires_at > NOW()
            """)
    List<InviteExpiry> findInviteExpiries(@Param("tenantId") long tenantId);

}
