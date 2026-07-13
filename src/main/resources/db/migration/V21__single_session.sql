-- =========================================================
-- V21: 단일 세션 강제(session_token)
--  로그인 성공 시마다 새 토큰을 발급·저장하고, 세션 스냅샷 토큰과 DB 토큰이 다르면
--  SessionRevalidationInterceptor가 세션을 회수한다(비밀번호 변경 킬스위치와 동일 패턴).
--  → 새 기기 로그인이 이전 기기 세션을 다음 요청에 자동으로 밀어낸다(마지막 로그인만 유효).
--  NULL 허용: 배포 전 세션은 SessionUser 직렬화 변경으로 어차피 재로그인(스냅샷 토큰 = 새 토큰).
-- =========================================================

ALTER TABLE users
    ADD COLUMN IF NOT EXISTS session_token VARCHAR(64) NULL COMMENT '현재 유효 세션 토큰(단일 세션 강제)'
        AFTER password_changed_at;
