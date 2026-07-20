-- =========================================================
-- V71: 급여 정산(참고) — 가산수당 적용 설정 + 정산 표시 라벨
--  - tenant_report_setting.pay_premium_enabled: §56 연장·야간·휴일 가산 적용 여부.
--    상시 5인 미만 사업장(현행 가산 미대상)은 false로 끈다. 기본 TRUE(≥5인 가정, 보수적 준법).
--  - 회사 설정(W020) 토글 라벨 + 급여 정산 명세(W006/근무표) 라벨 3개 언어.
-- =========================================================
ALTER TABLE tenant_report_setting
    ADD COLUMN IF NOT EXISTS pay_premium_enabled BOOLEAN NOT NULL DEFAULT TRUE
        COMMENT '연장·야간·휴일 가산수당 적용(§56). 5인 미만 사업장은 false' AFTER stamp_enabled;

-- 회사 설정(W020) 가산 적용 토글 --------------------------
INSERT IGNORE INTO language_master (window_id, lang_key, lang, lang_value) VALUES
('W020','PAY_PREMIUM_TOGGLE','KOR','연장·야간·휴일 가산수당 적용'),
('W020','PAY_PREMIUM_TOGGLE','ENG','Apply overtime/night/holiday premiums'),
('W020','PAY_PREMIUM_TOGGLE','JPN','時間外・深夜・休日の割増を適用'),
('W020','PAY_PREMIUM_HINT','KOR','상시 5인 이상 사업장은 켜 두세요. 5인 미만은 가산 의무 대상이 아니므로 끌 수 있습니다(급여 정산 참고값에 반영).'),
('W020','PAY_PREMIUM_HINT','ENG','Keep on for workplaces with 5+ employees. Under 5 may turn it off (reflected in the reference payroll).'),
('W020','PAY_PREMIUM_HINT','JPN','常時5人以上の事業場はオンのままに。5人未満は割増義務の対象外のためオフにできます（給与精算の参考値に反映）。'),

-- 급여 정산(참고) 명세 라벨 — 근무표(W006) 하단 표시 --------
('W006','PAYROLL_TITLE','KOR','급여 정산 (참고)'),        ('W006','PAYROLL_TITLE','ENG','Payroll settlement (reference)'), ('W006','PAYROLL_TITLE','JPN','給与精算（参考）'),
('W006','PAYROLL_NOTE','KOR','근태 기반 참고 계산입니다. 실지급·4대보험·세금·수당 산입은 별도 급여 시스템을 따릅니다.'),
('W006','PAYROLL_NOTE','ENG','Reference calculation from attendance only. Actual pay, insurance, tax and allowances follow a separate payroll system.'),
('W006','PAYROLL_NOTE','JPN','勤怠に基づく参考計算です。実支給・社会保険・税・手当は別の給与システムに従います。'),
('W006','PAYROLL_BASE','KOR','월 기본급'),                ('W006','PAYROLL_BASE','ENG','Base salary'),        ('W006','PAYROLL_BASE','JPN','月額基本給'),
('W006','PAYROLL_HOURLY','KOR','통상시급'),              ('W006','PAYROLL_HOURLY','ENG','Hourly wage'),      ('W006','PAYROLL_HOURLY','JPN','時給換算'),
('W006','PAYROLL_OT','KOR','연장수당'),                  ('W006','PAYROLL_OT','ENG','Overtime'),             ('W006','PAYROLL_OT','JPN','時間外手当'),
('W006','PAYROLL_NIGHT','KOR','야간가산'),               ('W006','PAYROLL_NIGHT','ENG','Night premium'),     ('W006','PAYROLL_NIGHT','JPN','深夜割増'),
('W006','PAYROLL_HOLIDAY','KOR','휴일수당'),             ('W006','PAYROLL_HOLIDAY','ENG','Holiday work'),    ('W006','PAYROLL_HOLIDAY','JPN','休日手当'),
('W006','PAYROLL_DEDUCT','KOR','무노동 공제'),           ('W006','PAYROLL_DEDUCT','ENG','No-work deduction'), ('W006','PAYROLL_DEDUCT','JPN','ノーワーク控除'),
('W006','PAYROLL_NET','KOR','가감 합계'),                ('W006','PAYROLL_NET','ENG','Net adjustment'),      ('W006','PAYROLL_NET','JPN','増減合計'),
('W006','PAYROLL_UNSET','KOR','월 기본급 미입력 — 멤버 관리에서 입력하면 정산이 표시됩니다.'),
('W006','PAYROLL_UNSET','ENG','No base salary set — enter it in member management to see the settlement.'),
('W006','PAYROLL_UNSET','JPN','月額基本給が未入力です — メンバー管理で入力すると精算が表示されます。');
