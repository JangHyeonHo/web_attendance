-- =========================================================
-- V47: 청구서 문서 양식(W018) 라벨 — 공용(W999). 발행정보·공급자/공급받는자·품목표·비고.
-- =========================================================

INSERT IGNORE INTO language_master (window_id, lang_key, lang, lang_value) VALUES
('W999','INV_NO','KOR','청구번호'),        ('W999','INV_NO','ENG','Invoice No.'),      ('W999','INV_NO','JPN','請求番号'),
('W999','INV_PERIOD','KOR','청구 기간'),   ('W999','INV_PERIOD','ENG','Period'),        ('W999','INV_PERIOD','JPN','請求期間'),
('W999','INV_ISSUED_AT','KOR','발행일'),   ('W999','INV_ISSUED_AT','ENG','Issued'),     ('W999','INV_ISSUED_AT','JPN','発行日'),
('W999','INV_SUPPLIER','KOR','공급자'),    ('W999','INV_SUPPLIER','ENG','From'),        ('W999','INV_SUPPLIER','JPN','供給者'),
('W999','INV_BILL_TO','KOR','공급받는자'), ('W999','INV_BILL_TO','ENG','Bill to'),      ('W999','INV_BILL_TO','JPN','請求先'),
('W999','INV_ITEM','KOR','항목'),          ('W999','INV_ITEM','ENG','Item'),            ('W999','INV_ITEM','JPN','項目'),
('W999','INV_QTY','KOR','수량'),           ('W999','INV_QTY','ENG','Qty'),              ('W999','INV_QTY','JPN','数量'),
('W999','INV_PRICE','KOR','단가'),         ('W999','INV_PRICE','ENG','Unit price'),     ('W999','INV_PRICE','JPN','単価'),
('W999','INV_AMOUNT','KOR','금액'),        ('W999','INV_AMOUNT','ENG','Amount'),        ('W999','INV_AMOUNT','JPN','金額'),
('W999','INV_LINE_FREE','KOR','무료 인원(반값 적용)'),  ('W999','INV_LINE_FREE','ENG','Free seats (half rate)'),  ('W999','INV_LINE_FREE','JPN','無料人数(半額)'),
('W999','INV_LINE_EXTRA','KOR','추가 인원'),            ('W999','INV_LINE_EXTRA','ENG','Additional seats'),       ('W999','INV_LINE_EXTRA','JPN','追加人数'),
('W999','INV_LINE_FREEPLAN','KOR','무료 플랜'),         ('W999','INV_LINE_FREEPLAN','ENG','Free plan'),           ('W999','INV_LINE_FREEPLAN','JPN','無料プラン'),
('W999','INV_NOTE','KOR','인원 증가는 등록일 다음 날부터, 감소는 다음 달부터 일할 반영됩니다. 진행 중인 달은 잠정 금액이며 부가세는 별도 표기입니다.'),
('W999','INV_NOTE','ENG','Added seats are prorated from the day after registration; reductions apply from the next month. The current month is provisional. VAT is shown separately.'),
('W999','INV_NOTE','JPN','増員は登録日の翌日から、減員は翌月から日割りで反映されます。進行中の月は暫定金額で、消費税は別途表示です。');
