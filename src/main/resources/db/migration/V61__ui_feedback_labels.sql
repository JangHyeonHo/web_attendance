-- =========================================================
-- V61: 테스트 피드백 UI 라벨 배치
--  - 비밀번호 재설정 발송 확인 문구(W011, {email} 치환)
--  - 비밀번호 표시/숨김 토글(W999 공용)
--  - 저장 버튼(W999 공용 — 기존 SAVED[완료]와 별개인 동작 버튼)
-- =========================================================

INSERT IGNORE INTO language_master (window_id, lang_key, lang, lang_value) VALUES
('W011','RESET_CONFIRM','KOR','이 메일({email})로 비밀번호 재설정 메일을 전송하겠습니다.'),
('W011','RESET_CONFIRM','ENG','A password reset email will be sent to {email}.'),
('W011','RESET_CONFIRM','JPN','このメール（{email}）にパスワード再設定メールを送信します。'),
('W999','PWD_SHOW','KOR','비밀번호 표시'), ('W999','PWD_SHOW','ENG','Show password'), ('W999','PWD_SHOW','JPN','パスワードを表示'),
('W999','PWD_HIDE','KOR','비밀번호 숨김'), ('W999','PWD_HIDE','ENG','Hide password'), ('W999','PWD_HIDE','JPN','パスワードを非表示'),
('W999','SAVE','KOR','저장'), ('W999','SAVE','ENG','Save'), ('W999','SAVE','JPN','保存');
