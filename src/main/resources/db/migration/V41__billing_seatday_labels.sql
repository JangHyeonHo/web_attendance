-- =========================================================
-- V41: 등록 시점 일할계산(seat-day) 청구서 표기 라벨
--  - W999 공통: 좌석-일 / 일수 라벨(회사 W018·운영사 W008 공용)
--  - W018 회사 청구서 안내문에 "등록 시점 일할" 문구 반영
-- =========================================================

INSERT IGNORE INTO language_master (window_id, lang_key, lang, lang_value) VALUES
('W999','BILL_SEATDAYS','KOR','좌석-일 / 일수'),
('W999','BILL_SEATDAYS','ENG','Seat-days / days'),
('W999','BILL_SEATDAYS','JPN','席日 / 日数');

-- 안내문: 일할계산 원칙(등록일 다음 날부터, 감원은 다음 달 반영)을 회사가 알 수 있게
UPDATE language_master SET lang_value =
 '무료 인원 초과분에 인당 단가가 부과되며 부가세는 별도입니다. 인원 증가는 등록일 다음 날부터 일할 계산되고, 감소는 다음 달부터 반영됩니다. 진행 중인 달은 잠정 금액입니다.'
 WHERE window_id='W018' AND lang_key='NOTE' AND lang='KOR';
UPDATE language_master SET lang_value =
 'Seats above the free allowance are charged at the unit price; VAT is added separately. Added seats are prorated from the day after registration, and reductions apply from the next month. The current month is a provisional amount.'
 WHERE window_id='W018' AND lang_key='NOTE' AND lang='ENG';
UPDATE language_master SET lang_value =
 '無料人数を超える分に1人単価が課金され、消費税は別途です。増員は登録日の翌日から日割り計算され、減員は翌月から反映されます。進行中の月は暫定金額です。'
 WHERE window_id='W018' AND lang_key='NOTE' AND lang='JPN';
