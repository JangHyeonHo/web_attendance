-- =========================================================
-- V60: 지정공휴일(회사) 매년 반복 + 개별 수정(#8)
--  - recurring 플래그 추가: TRUE면 "매년 같은 날짜" 회사 공휴일.
--    실체화 정책 — 반복 지정 시점에 이미 동기화된 모든 연도로 인스턴스를 채우고,
--    이후 국가공휴일 동기화(연도 생성) 시점에도 해당 연도에 없으면 채운다(같은 명칭 존재 시 생략).
--  - 각 연도 인스턴스는 실제 행 → 연도별 개별 수정(날짜/명칭 이동)·삭제 가능(대리키 유지).
--  - NATIONAL은 반복 대상 아님(동기화가 연도 단위로 관리) → 기본 FALSE, 의미 없음.
-- =========================================================
ALTER TABLE holiday
    ADD COLUMN recurring BOOLEAN NOT NULL DEFAULT FALSE AFTER holiday_type;

-- 라벨(W013) — 반복 체크박스/안내, 수정 모달. 아이콘 툴팁은 공용 EDIT/DELETE(W999) 재사용.
INSERT IGNORE INTO language_master (window_id, lang_key, lang, lang_value) VALUES
('W013','RECURRING','KOR','매년 반복'),        ('W013','RECURRING','ENG','Repeat yearly'),      ('W013','RECURRING','JPN','毎年繰り返し'),
('W013','RECURRING_HINT','KOR','매년 같은 날짜에 자동으로 추가됩니다(동기화된 모든 연도 포함).'),
('W013','RECURRING_HINT','ENG','Automatically added on the same date every year (including all synced years).'),
('W013','RECURRING_HINT','JPN','毎年同じ日付に自動追加されます（同期済みのすべての年度を含む）。'),
('W013','EDIT_HOLIDAY','KOR','공휴일 수정'),    ('W013','EDIT_HOLIDAY','ENG','Edit holiday'),     ('W013','EDIT_HOLIDAY','JPN','祝日の編集');
