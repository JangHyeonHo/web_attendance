-- =========================================================
-- V70: 멤버 급여(월 기본급) — 근태 마감 시 급여 정산(참고) 산출의 기준값
--  - users.base_monthly_salary: 월 기본급(원/円, 정수). 통상시급 환산의 기준.
--    · 한국: ÷209h(주휴 포함), 일본: ÷월평균소정근로시간 — 국가별 divisor는 PayPolicy가 결정.
--  - 무노동 공제/연장·야간·휴일 가산의 기준 통상임금 대용값(실지급·4대보험·세금은 별도 급여시스템).
-- 정수 원 관례(billing과 동일). 월 기본급은 INT 상한(약 21억) 초과 여지가 있어 BIGINT.
-- =========================================================
ALTER TABLE users
    ADD COLUMN IF NOT EXISTS base_monthly_salary BIGINT NULL
        COMMENT '월 기본급(원/円, 정수) — 급여 정산 기준. NULL=미입력' AFTER hire_date;

-- 멤버 관리 화면(W009) 급여 라벨 3개 언어 -------------------
INSERT IGNORE INTO language_master (window_id, lang_key, lang, lang_value) VALUES
('W009','SALARY','KOR','월 기본급'),          ('W009','SALARY','ENG','Monthly base salary'), ('W009','SALARY','JPN','月額基本給'),
('W009','SALARY_HINT','KOR','급여 정산(참고) 계산의 기준 금액입니다. 선택 입력.'),
('W009','SALARY_HINT','ENG','Base amount for the reference payroll settlement. Optional.'),
('W009','SALARY_HINT','JPN','給与精算（参考）計算の基準額です。任意入力。'),
('W009','SALARY_UNSET','KOR','미입력'),        ('W009','SALARY_UNSET','ENG','Not set'),        ('W009','SALARY_UNSET','JPN','未入力'),
('W009','SALARY_SAVE','KOR','급여 저장'),      ('W009','SALARY_SAVE','ENG','Save salary'),     ('W009','SALARY_SAVE','JPN','給与を保存');
