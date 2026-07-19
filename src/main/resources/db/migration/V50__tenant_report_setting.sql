-- =========================================================
-- V50: 테넌트 근태 보고서 설정 — 결재(도장)란 표시 on/off.
-- 행 없음 = 기본 미표시(false). 관리자(W019)가 켜면 Excel·인쇄 보고서에 결재란이 나온다.
-- =========================================================

CREATE TABLE IF NOT EXISTS tenant_report_setting (
    tenant_id     BIGINT   NOT NULL COMMENT '테넌트 ID',
    stamp_enabled BOOLEAN  NOT NULL DEFAULT FALSE COMMENT '근태 보고서 결재(도장)란 표시 여부',
    updated_at    DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (tenant_id)
) COMMENT '테넌트 근태 보고서 설정';
