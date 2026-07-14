-- =========================================================
-- V32: 메일 템플릿 변수 안내표 라벨(W999 공통) — 복사 가능 표 + HTML 미리보기(#11/#12)
-- =========================================================
INSERT IGNORE INTO language_master (window_id, lang_key, lang, lang_value) VALUES
('W999','VARIABLES','KOR','사용 가능한 변수'), ('W999','VARIABLES','ENG','Available variables'), ('W999','VARIABLES','JPN','使用可能な変数'),
('W999','VAR_DESC','KOR','설명'), ('W999','VAR_DESC','ENG','Description'), ('W999','VAR_DESC','JPN','説明'),
('W999','VAR_HINT','KOR','변수를 눌러 복사하세요'), ('W999','VAR_HINT','ENG','Click a variable to copy'), ('W999','VAR_HINT','JPN','変数をクリックしてコピー'),
('W999','COPIED','KOR','복사됨'), ('W999','COPIED','ENG','Copied'), ('W999','COPIED','JPN','コピーしました'),
('W999','VAR_MEMBER_NAME','KOR','받는 사람 이름'), ('W999','VAR_MEMBER_NAME','ENG','Recipient name'), ('W999','VAR_MEMBER_NAME','JPN','受信者名'),
('W999','VAR_TENANT_NAME','KOR','회사명'), ('W999','VAR_TENANT_NAME','ENG','Company name'), ('W999','VAR_TENANT_NAME','JPN','会社名'),
('W999','VAR_ACTION_URL','KOR','초대/재설정 링크 (필수)'), ('W999','VAR_ACTION_URL','ENG','Invite/reset link (required)'), ('W999','VAR_ACTION_URL','JPN','招待/再設定リンク (必須)'),
('W999','VAR_EXPIRES_AT','KOR','만료 일시'), ('W999','VAR_EXPIRES_AT','ENG','Expiry time'), ('W999','VAR_EXPIRES_AT','JPN','有効期限'),
('W999','VAR_INVITER_NAME','KOR','초대한 사람 이름'), ('W999','VAR_INVITER_NAME','ENG','Inviter name'), ('W999','VAR_INVITER_NAME','JPN','招待者名');
