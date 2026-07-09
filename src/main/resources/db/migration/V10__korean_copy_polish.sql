-- =========================================================
-- V10: 한국어 카피 전문 교정 반영 (사용자 노출 문구)
--  - 전문 교정 검토에서 확정된 번역투/맞춤법/용어 불일치 수정 (KOR만 — ENG/JPN 무변경)
--  - 시드 마이그레이션(V3/V5/V7)은 불변 유지, UPDATE로 교정(기존 설치·신규 설치 동일 결과)
--  - API 메시지(messages.properties)의 동일 계열 교정은 코드 측에서 반영
-- =========================================================

-- W005 출결: "유저님" 호칭 제거, 일본어식 한자어(취득) 교체
UPDATE language_master SET lang_value='현재 상태는'
 WHERE window_id='W005' AND lang_key='STATUS_PREFIX' AND lang='KOR';
UPDATE language_master SET lang_value='위치 정보를 가져오지 못했습니다.'
 WHERE window_id='W005' AND lang_key='GEO_FAIL' AND lang='KOR';

-- W006 월별 상세: 외래어 표기 오류(스케쥴)·모호한 열 이름
UPDATE language_master SET lang_value='근무 스케줄'
 WHERE window_id='W006' AND lang_key='USERSCHE' AND lang='KOR';
UPDATE language_master SET lang_value='출퇴근 기록'
 WHERE window_id='W006' AND lang_key='INPUTTIME' AND lang='KOR';

-- W000 랜딩: ENG(one business day)/JPN(1営業日)과 약속 수준 정렬
UPDATE language_master SET lang_value='도입 문의를 보내 주시면 1영업일 이내에 회신드립니다.'
 WHERE window_id='W000' AND lang_key='LANDING_CTA_SUB' AND lang='KOR';

-- W007 테넌트 관리: 안내 대상(시스템 관리자) 기준 문장으로 교정
UPDATE language_master SET lang_value='공휴일 자동 등록에 실패했습니다. 고객사 관리자가 공휴일 화면에서 직접 동기화하도록 안내해 주세요.'
 WHERE window_id='W007' AND lang_key='HOLIDAY_SYNC_FAILED_NOTICE' AND lang='KOR';

-- W009 멤버 관리: 상태/동작 라벨 구분, 확인 문구 의문형 통일(W013과 정렬)
UPDATE language_master SET lang_value='초대 대기'
 WHERE window_id='W009' AND lang_key='STATUS_PENDING' AND lang='KOR';
UPDATE language_master SET lang_value='비활성화'
 WHERE window_id='W009' AND lang_key='DISABLE' AND lang='KOR';
UPDATE language_master SET lang_value='이 멤버를 삭제할까요? 출결 기록은 보존됩니다.'
 WHERE window_id='W009' AND lang_key='DELETE_CONFIRM' AND lang='KOR';

-- W010/W011 비밀번호 온보딩: 직역투 교정, 초대 기반 서비스에 맞는 표현
UPDATE language_master SET lang_value='재설정 메일 다시 요청'
 WHERE window_id='W010' AND lang_key='GO_RESET' AND lang='KOR';
UPDATE language_master SET lang_value='등록된 이메일을 입력하면 비밀번호 재설정 링크를 보내드립니다.'
 WHERE window_id='W011' AND lang_key='RESET_DESC' AND lang='KOR';
UPDATE language_master SET lang_value='계정이 등록되어 있는 경우 재설정 메일이 발송됩니다. 받은편지함을 확인해 주세요.'
 WHERE window_id='W011' AND lang_key='RESET_SENT' AND lang='KOR';

-- W013 공휴일: 결과 표시는 규칙 용어(우선)가 아니라 결과(유지)로
UPDATE language_master SET lang_value='동기화 완료: 추가 {inserted} / 삭제 {deleted} / 회사 지정 유지 {skipped}'
 WHERE window_id='W013' AND lang_key='SYNC_DONE' AND lang='KOR';

-- 폐지된 초기 비밀번호 방식(D21)의 잔존 라벨 제거 — 사용처 없음 확인 완료
DELETE FROM language_master
 WHERE window_id='W007' AND lang_key IN ('INITIAL_PWD','INITIAL_PWD_NOTE');

-- 초대 메일(INVITE KOR) 본문: 만료 안내와 무시 안내를 별도 단락으로(다른 5개 템플릿과 동일 구성)
UPDATE mail_template SET body = REPLACE(body, '사용할 수 있습니다.
본인에게 온 메일이 아니라면', '사용할 수 있습니다.

본인에게 온 메일이 아니라면')
 WHERE purpose='INVITE' AND lang='KOR';
