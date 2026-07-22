-- =========================================================
-- V80: 회사(테넌트) '신규 멤버 기본 스케줄' + 기존 멤버 정기 스케줄 백필.
--  목적: 근태 기대시간·연차 소정근로를 개인 기본값(users.default_work_*)이 아니라
--        실효 스케줄(정기 패턴 + 상세 로타)에서 per-member·per-date로 계산하도록 단일화.
--   - tenant_default_schedule: 요일별 근무/휴무·시간 템플릿(멤버 등록 시 그 멤버 정기 스케줄로 복제).
--   - 기존 멤버 중 정기 스케줄(schedule_pattern)이 없는 사람은 현재 users 기본값으로 패턴을 생성(백필)해
--     모든 멤버가 정기 스케줄을 갖도록 → 이후 users 개인 기본값 사용처를 단계적으로 제거.
-- =========================================================
CREATE TABLE tenant_default_schedule (
    tenant_id        BIGINT  NOT NULL,
    day_of_week      TINYINT NOT NULL,           -- 1..7 (월..일)
    off              BOOLEAN NOT NULL DEFAULT FALSE,
    start_time       TIME    NULL,
    end_time         TIME    NULL,
    crosses_midnight BOOLEAN NOT NULL DEFAULT FALSE,
    PRIMARY KEY (tenant_id, day_of_week),
    CONSTRAINT fk_tds_tenant FOREIGN KEY (tenant_id) REFERENCES tenant (tenant_id)
);

-- 기존 테넌트 기본 스케줄 시드: 월–금 09:00–18:00 근무, 토·일 휴무
INSERT INTO tenant_default_schedule (tenant_id, day_of_week, off, start_time, end_time, crosses_midnight)
SELECT t.tenant_id, d.dow, d.is_off, d.st, d.et, FALSE
FROM tenant t
CROSS JOIN (
    SELECT 1 AS dow, FALSE AS is_off, '09:00:00' AS st, '18:00:00' AS et
    UNION ALL SELECT 2, FALSE, '09:00:00', '18:00:00'
    UNION ALL SELECT 3, FALSE, '09:00:00', '18:00:00'
    UNION ALL SELECT 4, FALSE, '09:00:00', '18:00:00'
    UNION ALL SELECT 5, FALSE, '09:00:00', '18:00:00'
    UNION ALL SELECT 6, TRUE,  NULL,       NULL
    UNION ALL SELECT 7, TRUE,  NULL,       NULL
) d;

-- 정기 스케줄이 없는 기존 멤버에 패턴 생성(주기 1주, 기준 월요일은 2024-01-01=월)
INSERT INTO schedule_pattern (tenant_id, user_id, cycle_weeks, anchor_monday, active)
SELECT u.tenant_id, u.user_id, 1, '2024-01-01', TRUE
FROM users u
WHERE u.deleted = FALSE AND u.role <> 'SYSTEM_ADMIN'
  AND NOT EXISTS (
      SELECT 1 FROM schedule_pattern p
      WHERE p.tenant_id = u.tenant_id AND p.user_id = u.user_id
  );

-- 각 요일 슬롯을 users 기본값(work_days + default_work_start/end)에서 채운다.
-- work_days는 월~일 [01]{7} — SUBSTRING(work_days, dow, 1)='1'이면 근무, 아니면 휴무.
INSERT INTO schedule_pattern_slot (pattern_id, week_index, day_of_week, off, start_time, end_time, crosses_midnight)
SELECT p.pattern_id, 0, d.dow,
       CASE WHEN SUBSTRING(u.work_days, d.dow, 1) = '1' THEN FALSE ELSE TRUE END,
       CASE WHEN SUBSTRING(u.work_days, d.dow, 1) = '1' THEN u.default_work_start ELSE NULL END,
       CASE WHEN SUBSTRING(u.work_days, d.dow, 1) = '1' THEN u.default_work_end ELSE NULL END,
       FALSE
FROM schedule_pattern p
JOIN users u ON u.tenant_id = p.tenant_id AND u.user_id = p.user_id
CROSS JOIN (
    SELECT 1 AS dow UNION ALL SELECT 2 UNION ALL SELECT 3 UNION ALL SELECT 4
    UNION ALL SELECT 5 UNION ALL SELECT 6 UNION ALL SELECT 7
) d
WHERE NOT EXISTS (
    SELECT 1 FROM schedule_pattern_slot s
    WHERE s.pattern_id = p.pattern_id AND s.week_index = 0 AND s.day_of_week = d.dow
);
