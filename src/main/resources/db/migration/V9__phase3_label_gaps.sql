-- =========================================================
-- V9: Phase 3 다관점 리뷰에서 발견된 화면 텍스트 누락 보충
--  - 화면별(window_id) 시드에 없고 W999(공통)에도 없어 키 이름이 그대로
--    표시되던 조합들. 값은 기존 화면의 동일 키 문구를 그대로 따른다.
-- =========================================================

INSERT IGNORE INTO language_master (window_id, lang_key, lang, lang_value) VALUES
-- W007(테넌트 관리): 최초 관리자 초대 메일 발송 실패 안내(W009와 동일 문구)
('W007','MAIL_FAILED','KOR','메일 발송에 실패했습니다. 재발송해 주세요.'),
('W007','MAIL_FAILED','ENG','Failed to send email. Please resend.'),
('W007','MAIL_FAILED','JPN','メール送信に失敗しました。再送信してください。'),
-- W010(비밀번호 설정): 본인 확인 정보 라벨
('W010','NAME','KOR','이름'),
('W010','NAME','ENG','Name'),
('W010','NAME','JPN','名前'),
('W010','TENANT_NAME','KOR','회사명'),
('W010','TENANT_NAME','ENG','Company name'),
('W010','TENANT_NAME','JPN','会社名'),
('W010','EXPIRES_AT','KOR','링크 만료'),
('W010','EXPIRES_AT','ENG','Link expires'),
('W010','EXPIRES_AT','JPN','リンク有効期限'),
-- W012/W014(메일 템플릿 관리): 목록 열 라벨
('W012','TPL_PURPOSE','KOR','용도'),
('W012','TPL_PURPOSE','ENG','Purpose'),
('W012','TPL_PURPOSE','JPN','用途'),
('W012','LANG','KOR','언어'),
('W012','LANG','ENG','Language'),
('W012','LANG','JPN','言語'),
('W012','UPDATED_AT','KOR','수정일'),
('W012','UPDATED_AT','ENG','Updated'),
('W012','UPDATED_AT','JPN','更新日'),
('W014','TPL_PURPOSE','KOR','용도'),
('W014','TPL_PURPOSE','ENG','Purpose'),
('W014','TPL_PURPOSE','JPN','用途'),
('W014','LANG','KOR','언어'),
('W014','LANG','ENG','Language'),
('W014','LANG','JPN','言語'),
('W014','UPDATED_AT','KOR','수정일'),
('W014','UPDATED_AT','ENG','Updated'),
('W014','UPDATED_AT','JPN','更新日'),
-- W013(공휴일 관리): 행 삭제 버튼
('W013','DELETE','KOR','삭제'),
('W013','DELETE','ENG','Delete'),
('W013','DELETE','JPN','削除'),
-- W999(공통): 저장 완료 표시(템플릿 저장 등 — 하드코딩 'OK' 대체)
('W999','SAVED','KOR','저장되었습니다'),
('W999','SAVED','ENG','Saved'),
('W999','SAVED','JPN','保存しました');
