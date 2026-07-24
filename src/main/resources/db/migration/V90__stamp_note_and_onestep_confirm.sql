-- =========================================================
-- V90: 스탬프 비고 + 중복 등록 원스텝 확인 라벨.
--  - 중복 퇴근/출근·재출근은 첫 등록 모달에 경고를 미리 표시하고 2차 확인 모달을 생략
--    (문구는 실제 동작 기준: 스탬프는 append-only, 월별 조립은 마지막 값 우선 = 시각 갱신 효과).
--  - 자동 스탬프 비고: 일자 상세에서 사후 작성/수정(시각·구분·위치 불변).
--    등록 모달에서는 받지 않는다(출퇴근 순간의 입력 부담 배제).
--  - RETRY 라벨 명확화: 무엇을 재요청하는지 알 수 없던 '재요청' → '위치 재요청'
--    (스탬프 모달의 해당 버튼은 위도·경도 재취득 버튼).
-- =========================================================
INSERT INTO language_master (window_id, lang_key, lang, lang_value) VALUES
-- M001 스탬프 등록 모달(중복 경고)
('M001','ALREADY_OFF_HINT','KOR','이미 퇴근한 상태입니다 — 등록하면 퇴근 시각이 이 시각으로 갱신됩니다.'),
('M001','ALREADY_OFF_HINT','ENG','You have already clocked out — registering updates your clock-out time to now.'),
('M001','ALREADY_OFF_HINT','JPN','すでに退勤済みです — 登録すると退勤時刻がこの時刻に更新されます。'),
('M001','ALREADY_WORK_HINT','KOR','이미 출근한 상태입니다 — 등록하면 출근 시각이 이 시각으로 갱신됩니다.'),
('M001','ALREADY_WORK_HINT','ENG','You have already clocked in — registering updates your clock-in time to now.'),
('M001','ALREADY_WORK_HINT','JPN','すでに出勤済みです — 登録すると出勤時刻がこの時刻に更新されます。'),
('M001','RE_ATTEND_HINT','KOR','오늘 이미 퇴근했습니다 — 등록하면 재출근으로 기록됩니다.'),
('M001','RE_ATTEND_HINT','ENG','You already clocked out today — registering records a re-attendance.'),
('M001','RE_ATTEND_HINT','JPN','本日はすでに退勤しています — 登録すると再出勤として記録されます。'),
-- M002 일자 상세: 자동 스탬프 비고 작성/수정
('M002','NOTE','KOR','비고'),
('M002','NOTE','ENG','Note'),
('M002','NOTE','JPN','備考'),
('M002','NOTE_TITLE','KOR','비고 작성'),
('M002','NOTE_TITLE','ENG','Write note'),
('M002','NOTE_TITLE','JPN','備考の作成'),
('M002','NOTE_HINT','KOR','비고는 이 기록과 함께 저장되어 일자 상세에 표시됩니다.'),
('M002','NOTE_HINT','ENG','The note is stored with this record and shown in the daily detail.'),
('M002','NOTE_HINT','JPN','備考はこの記録とともに保存され、日別詳細に表示されます。');

-- 스탬프 모달의 '재요청'은 위치(위도·경도) 재취득 버튼 — 대상을 라벨에 명시
UPDATE language_master SET lang_value = '위치 재요청' WHERE window_id = 'M001' AND lang_key = 'RETRY' AND lang = 'KOR';
UPDATE language_master SET lang_value = 'Re-fetch location' WHERE window_id = 'M001' AND lang_key = 'RETRY' AND lang = 'ENG';
UPDATE language_master SET lang_value = '位置を再取得' WHERE window_id = 'M001' AND lang_key = 'RETRY' AND lang = 'JPN';
