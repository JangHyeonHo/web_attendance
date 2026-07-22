-- =========================================================
-- V84: users 개인 기본 근무 컬럼 물리 제거(스케줄 단일화 마무리).
--  근태 기대시간·연차 소정근로는 이제 실효 스케줄(정기 패턴 schedule_pattern + 상세 로타 work_schedule)에서
--  per-member·per-date로 계산한다. V80에서 기존 멤버 정기 스케줄을 이 컬럼들로 백필해 두었으므로
--  더 이상 읽는 코드가 없다 → 컬럼을 제거해 이중 설정(users 기본값 vs 정기 스케줄) 소지를 없앤다.
-- =========================================================
ALTER TABLE users
    DROP COLUMN default_work_start,
    DROP COLUMN default_work_end,
    DROP COLUMN work_days;
