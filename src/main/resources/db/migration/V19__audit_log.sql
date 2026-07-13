-- =========================================================
-- V19: 감사 로그(audit_log)
--  인증 이벤트(로그인 성공/실패/로그아웃/세션 회수)와 애플리케이션 에러를 영속 기록.
--  기존 로그인 실패 카운트는 LoginRateLimiter 인메모리라 재시작 시 소실 → 감사용 영속 기록 신설.
--  FK 없음: 감사는 유저/테넌트 삭제 후에도 보존, 로그인 실패는 user_id가 없다(NULL).
-- =========================================================

CREATE TABLE audit_log (
    audit_id     BIGINT       NOT NULL AUTO_INCREMENT COMMENT '감사 ID',
    tenant_id    BIGINT       NULL COMMENT '테넌트(비로그인/미식별 이벤트는 NULL)',
    user_id      BIGINT       NULL COMMENT '유저(미식별 시 NULL)',
    category     VARCHAR(20)  NOT NULL COMMENT '분류(AUTH/ERROR)',
    event        VARCHAR(40)  NOT NULL COMMENT '이벤트 코드',
    detail       VARCHAR(500) NULL COMMENT '상세(사유·에러 요약 등)',
    actor_email  VARCHAR(100) NULL COMMENT '행위자 이메일(user_id 없을 때 식별)',
    ip           VARCHAR(45)  NULL COMMENT '클라이언트 IP(IPv6 대응 45자)',
    user_agent   VARCHAR(300) NULL COMMENT 'User-Agent',
    request_path VARCHAR(200) NULL COMMENT '요청 경로',
    created_at   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '발생 시각',
    PRIMARY KEY (audit_id),
    KEY idx_audit_tenant_time (tenant_id, created_at),
    KEY idx_audit_user_time (user_id, created_at),
    KEY idx_audit_cat_time (category, created_at)
) COMMENT '감사 로그(인증 이벤트·애플리케이션 에러)';
