-- =========================================================
-- V57: 청구서 공급자/공급받는자 라벨 완화 — 세금계산서 법정용어 대신 청구서(발행–수신) 표현으로.
--      공급자→발행처(Billed By/発行元), 공급받는자→받는 곳(Billed To/宛先). 값만 갱신(키는 V47).
-- =========================================================

UPDATE language_master SET lang_value='발행처'   WHERE window_id='W999' AND lang_key='INV_SUPPLIER' AND lang='KOR';
UPDATE language_master SET lang_value='Billed By' WHERE window_id='W999' AND lang_key='INV_SUPPLIER' AND lang='ENG';
UPDATE language_master SET lang_value='発行元'    WHERE window_id='W999' AND lang_key='INV_SUPPLIER' AND lang='JPN';

UPDATE language_master SET lang_value='받는 곳'   WHERE window_id='W999' AND lang_key='INV_BILL_TO' AND lang='KOR';
UPDATE language_master SET lang_value='Billed To' WHERE window_id='W999' AND lang_key='INV_BILL_TO' AND lang='ENG';
UPDATE language_master SET lang_value='宛先'      WHERE window_id='W999' AND lang_key='INV_BILL_TO' AND lang='JPN';
