-- =========================================================
-- V62: 휴가 종류별 "시간 휴가 사용 가능" 플래그(#12·#13)
--  - hourly_enabled=TRUE인 종류에 한해 멤버가 신청 시 날짜/시간 토글로 시간 단위 휴가 신청 가능.
--  - unit(DAY/HOUR)은 잔여 표시/차감 단위로 별개 — 기존 HOUR 단위 종류는 시간 사용 의도로 보아 TRUE 보정.
-- =========================================================
ALTER TABLE leave_type
    ADD COLUMN hourly_enabled BOOLEAN NOT NULL DEFAULT FALSE AFTER unit;

UPDATE leave_type SET hourly_enabled = TRUE WHERE unit = 'HOUR';

-- 관리자 종류 편집 체크박스 라벨(W016). 신청 모달 토글 라벨(MODE_DAY/MODE_HOUR 등)은 기존 시드 재사용.
INSERT IGNORE INTO language_master (window_id, lang_key, lang, lang_value) VALUES
('W016','HOURLY_ENABLED','KOR','시간 휴가 사용 가능'),
('W016','HOURLY_ENABLED','ENG','Allow hourly leave'),
('W016','HOURLY_ENABLED','JPN','時間単位の休暇を許可');
