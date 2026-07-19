-- =========================================================
-- V43: 무료 인원 반값 과금(2단 요금제) 청구서 표기 라벨
--  - W999 공통: '무료 인원(반값)' 라벨
--  - W018 회사 청구서 안내문에 반값 정책 반영
-- =========================================================

INSERT IGNORE INTO language_master (window_id, lang_key, lang, lang_value) VALUES
('W999','BILL_FREE_HALF','KOR','무료 인원(반값)'),
('W999','BILL_FREE_HALF','ENG','Free seats (½ rate)'),
('W999','BILL_FREE_HALF','JPN','無料人数(半額)');

-- 안내문: 무료 인원 초과 시 무료 인원수만큼은 반값, 초과분은 정단가
UPDATE language_master SET lang_value =
 '무료 인원을 초과하면 무료 인원수만큼은 인당 단가의 절반, 초과 인원은 정단가로 과금되며 부가세는 별도입니다. 인원 증가는 등록일 다음 날부터 일할 계산되고, 감소는 다음 달부터 반영됩니다. 진행 중인 달은 잠정 금액입니다.'
 WHERE window_id='W018' AND lang_key='NOTE' AND lang='KOR';
UPDATE language_master SET lang_value =
 'Once the free allowance is exceeded, the free seats are charged at half the unit price and the additional seats at the full rate; VAT is added separately. Added seats are prorated from the day after registration, and reductions apply from the next month. The current month is a provisional amount.'
 WHERE window_id='W018' AND lang_key='NOTE' AND lang='ENG';
UPDATE language_master SET lang_value =
 '無料人数を超えると、無料人数分は1人単価の半額、超過人数は正規単価で課金され、消費税は別途です。増員は登録日の翌日から日割り計算され、減員は翌月から反映されます。進行中の月は暫定金額です。'
 WHERE window_id='W018' AND lang_key='NOTE' AND lang='JPN';
