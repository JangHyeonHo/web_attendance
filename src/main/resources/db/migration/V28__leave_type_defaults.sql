-- =========================================================
-- V28: 휴가 종류 기본 시드 정리(#8)
--  - 연차(ANNUAL) 기본 표시명을 '유급휴가'로(커스터마이즈 안 한 테넌트만)
--  - 여름휴가(SUMMER) 기본 종류를 전 테넌트에 1행씩 시드
--  - 단위(unit)는 모두 DAY 고정 — 신규 UI에서 일/시간 분리를 제거(컬럼/제약은 레거시 호환 유지)
-- =========================================================

-- 연차 기본명 '연차' → '유급휴가' (테넌트가 이름을 바꾼 경우는 건드리지 않음)
UPDATE leave_type
SET name = '유급휴가'
WHERE code = 'ANNUAL' AND name = '연차';

-- 여름휴가(SUMMER) 시드 — 없는 테넌트에만. 유급·승인필요·일단위·비연차.
INSERT INTO leave_type (tenant_id, code, name, paid, unit, requires_approval, is_annual, active, sort_order)
SELECT t.tenant_id, 'SUMMER', '여름휴가', TRUE, 'DAY', TRUE, FALSE, TRUE, 1
FROM tenant t
WHERE NOT EXISTS (
    SELECT 1 FROM leave_type lt WHERE lt.tenant_id = t.tenant_id AND lt.code = 'SUMMER'
);
