-- =========================================================
-- V86: 화면 코드 규칙 재발급 — 역할 접두사 체계로 전환.
--  M###=멤버 본인 업무 / T###=테넌트 관리(총관리자·인사관리자) / A###=운영사(SYSTEM_ADMIN)
--  / W###=역할 귀속 없는 공통(랜딩·로그인·비밀번호·W999 공통 라벨은 유지).
--  Screen enum(코드 상수)과 language_master.window_id를 같은 코드로 맞춘다.
-- =========================================================
UPDATE language_master SET window_id = 'M001' WHERE window_id = 'W005'; -- 출결(멤버 홈)
UPDATE language_master SET window_id = 'M002' WHERE window_id = 'W006'; -- 출결 상세
UPDATE language_master SET window_id = 'M003' WHERE window_id = 'W015'; -- 휴가

UPDATE language_master SET window_id = 'T001' WHERE window_id = 'W009'; -- 멤버 관리
UPDATE language_master SET window_id = 'T002' WHERE window_id = 'W013'; -- 공휴일 관리
UPDATE language_master SET window_id = 'T003' WHERE window_id = 'W016'; -- 휴가 관리
UPDATE language_master SET window_id = 'T004' WHERE window_id = 'W021'; -- 근태 마감 관리
UPDATE language_master SET window_id = 'T005' WHERE window_id = 'W014'; -- 회사 메일 템플릿
UPDATE language_master SET window_id = 'T006' WHERE window_id = 'W018'; -- 청구서
UPDATE language_master SET window_id = 'T007' WHERE window_id = 'W019'; -- 회사 정보/결제
UPDATE language_master SET window_id = 'T008' WHERE window_id = 'W020'; -- 회사 설정

UPDATE language_master SET window_id = 'A001' WHERE window_id = 'W007'; -- 테넌트 목록(운영사 홈)
UPDATE language_master SET window_id = 'A002' WHERE window_id = 'W008'; -- 테넌트 상세
UPDATE language_master SET window_id = 'A003' WHERE window_id = 'W017'; -- 감사 로그
UPDATE language_master SET window_id = 'A004' WHERE window_id = 'W012'; -- 글로벌 메일 템플릿
UPDATE language_master SET window_id = 'A005' WHERE window_id = 'W004'; -- 언어 마스터 관리
