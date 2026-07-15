-- =========================================================
-- V38: 관리자 좌측 사이드바(내비 방향 A) 섹션 라벨 — 3개국어(공용 W999)
--  PC + 관리자 이상 전용. 관리 메뉴를 섹션(조직/휴가·근태/설정/운영)으로 묶어 세로 전개.
-- =========================================================
INSERT INTO language_master (window_id, lang_key, lang, lang_value) VALUES
('W999','NAV_SEC_ORG','KOR','조직'),              ('W999','NAV_SEC_ORG','ENG','Organization'),        ('W999','NAV_SEC_ORG','JPN','組織'),
('W999','NAV_SEC_LEAVE','KOR','휴가 · 근태 관리'), ('W999','NAV_SEC_LEAVE','ENG','Leave & attendance'), ('W999','NAV_SEC_LEAVE','JPN','休暇・勤怠管理'),
('W999','NAV_SEC_SETTINGS','KOR','설정'),         ('W999','NAV_SEC_SETTINGS','ENG','Settings'),        ('W999','NAV_SEC_SETTINGS','JPN','設定'),
('W999','NAV_SEC_OPS','KOR','운영'),              ('W999','NAV_SEC_OPS','ENG','Operations'),           ('W999','NAV_SEC_OPS','JPN','運営'),
('W999','NAV_ADMIN','KOR','관리 메뉴'),           ('W999','NAV_ADMIN','ENG','Admin menu'),             ('W999','NAV_ADMIN','JPN','管理メニュー'),
('W999','NAV_TOGGLE','KOR','메뉴 접기/펼치기'),   ('W999','NAV_TOGGLE','ENG','Toggle menu'),           ('W999','NAV_TOGGLE','JPN','メニュー開閉');
