-- =========================================================
-- V20: 감사 로그 조회 화면(W017, SYSTEM_ADMIN) 텍스트
-- =========================================================

INSERT IGNORE INTO language_master (window_id, lang_key, lang, lang_value) VALUES
-- 헤더 메뉴(공통 W999)
('W999','AUDIT_LOG','KOR','감사 로그'),        ('W999','AUDIT_LOG','ENG','Audit log'),       ('W999','AUDIT_LOG','JPN','監査ログ'),
-- W017 화면
('W017','TITLE','KOR','감사 로그'),            ('W017','TITLE','ENG','Audit log'),           ('W017','TITLE','JPN','監査ログ'),
('W017','TIME','KOR','시각'),                 ('W017','TIME','ENG','Time'),                 ('W017','TIME','JPN','時刻'),
('W017','CATEGORY','KOR','분류'),             ('W017','CATEGORY','ENG','Category'),         ('W017','CATEGORY','JPN','分類'),
('W017','EVENT','KOR','이벤트'),              ('W017','EVENT','ENG','Event'),               ('W017','EVENT','JPN','イベント'),
('W017','TENANT','KOR','테넌트'),             ('W017','TENANT','ENG','Tenant'),             ('W017','TENANT','JPN','テナント'),
('W017','ACTOR','KOR','행위자'),              ('W017','ACTOR','ENG','Actor'),               ('W017','ACTOR','JPN','実行者'),
('W017','IP','KOR','IP'),                    ('W017','IP','ENG','IP'),                     ('W017','IP','JPN','IP'),
('W017','PATH','KOR','경로'),                 ('W017','PATH','ENG','Path'),                 ('W017','PATH','JPN','パス'),
('W017','DETAIL','KOR','상세'),               ('W017','DETAIL','ENG','Detail'),             ('W017','DETAIL','JPN','詳細'),
('W017','FILTER_ALL','KOR','전체'),           ('W017','FILTER_ALL','ENG','All'),            ('W017','FILTER_ALL','JPN','全て'),
('W017','CAT_AUTH','KOR','인증'),             ('W017','CAT_AUTH','ENG','Auth'),             ('W017','CAT_AUTH','JPN','認証'),
('W017','CAT_ERROR','KOR','에러'),            ('W017','CAT_ERROR','ENG','Error'),           ('W017','CAT_ERROR','JPN','エラー'),
('W017','REFRESH','KOR','새로고침'),           ('W017','REFRESH','ENG','Refresh'),           ('W017','REFRESH','JPN','更新');
