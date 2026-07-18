-- =========================================================
-- V34: 피드백 후속 라벨(#11 입사일 · #12 변수 표현 비개발자화)
--  - #12: '사용 가능한 변수'(개발자 용어) → '자동 입력 항목'(비개발자용). 실 서비스(스티비/채널톡/MS 差し込み) 관행 참고.
--  - #11: 멤버 등록 화면 입사일 라벨
-- =========================================================

-- #12 변수 안내 문구를 비개발자용으로
UPDATE language_master SET lang_value = '자동 입력 항목'      WHERE window_id='W999' AND lang_key='VARIABLES' AND lang='KOR';
UPDATE language_master SET lang_value = 'Auto-filled fields'   WHERE window_id='W999' AND lang_key='VARIABLES' AND lang='ENG';
UPDATE language_master SET lang_value = '差し込み項目'         WHERE window_id='W999' AND lang_key='VARIABLES' AND lang='JPN';

UPDATE language_master SET lang_value = '항목을 누르면 본문에 삽입되고, 발송 시 실제 정보로 자동으로 바뀝니다.'
  WHERE window_id='W999' AND lang_key='VAR_HINT' AND lang='KOR';
UPDATE language_master SET lang_value = 'Click an item to insert it; it is replaced with the real value when the email is sent.'
  WHERE window_id='W999' AND lang_key='VAR_HINT' AND lang='ENG';
UPDATE language_master SET lang_value = '項目をクリックすると本文に挿入され、送信時に実際の値に自動で置き換わります。'
  WHERE window_id='W999' AND lang_key='VAR_HINT' AND lang='JPN';

-- #11 멤버 등록 입사일(공용 W999)
INSERT INTO language_master (window_id, lang_key, lang, lang_value) VALUES
('W999','HIRE_DATE','KOR','입사일(선택)'),           ('W999','HIRE_DATE','ENG','Hire date (optional)'),   ('W999','HIRE_DATE','JPN','入社日（任意）'),
('W999','HIRE_DATE_PLACEHOLDER','KOR','미입력 시 등록일'), ('W999','HIRE_DATE_PLACEHOLDER','ENG','Defaults to today'), ('W999','HIRE_DATE_PLACEHOLDER','JPN','未入力なら登録日');
