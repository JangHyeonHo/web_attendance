-- =========================================================
-- V64: 근무 스케줄 유연화 1단계 — 일자 오버라이드에 야간 교대(자정 넘김)·휴무 표현(#13)
--  - crosses_midnight: 종업이 익일인 교대(예: 22:00~익일 06:00). 예정근무·법정휴게 계산이
--    이를 반영(같은 날 구간 가정 해제).
--  - off: 그 날 '근무 없음(휴무)' 오버라이드 — 공휴일(holiday)과 구분되는 스케줄상 휴무.
--  기존 행은 둘 다 FALSE → 해석 결과 종전과 동일(zero disruption).
-- =========================================================
ALTER TABLE work_schedule
    ADD COLUMN crosses_midnight BOOLEAN NOT NULL DEFAULT FALSE AFTER end_time,
    ADD COLUMN off BOOLEAN NOT NULL DEFAULT FALSE AFTER crosses_midnight;
