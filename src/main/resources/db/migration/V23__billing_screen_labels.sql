-- =========================================================
-- V23: 인당 과금 화면 텍스트
--  - W999(공통): 청구서 메뉴 + 컬럼/상태 라벨(운영사 W008·테넌트 W018 공용)
--  - W018: 회사(테넌트) 자사 청구서 화면
--  - W008: 테넌트 상세 결제정보에 인당 단가·무료 인원·마감 UI 추가
-- =========================================================

INSERT IGNORE INTO language_master (window_id, lang_key, lang, lang_value) VALUES
-- W999 공통(헤더 메뉴 + 청구 컬럼/상태)
('W999','BILLING','KOR','청구서'),                 ('W999','BILLING','ENG','Billing'),             ('W999','BILLING','JPN','請求書'),
('W999','BILL_MONTH','KOR','청구월'),              ('W999','BILL_MONTH','ENG','Month'),            ('W999','BILL_MONTH','JPN','請求月'),
('W999','BILL_SEATS_MAX','KOR','최대 인원'),        ('W999','BILL_SEATS_MAX','ENG','Peak seats'),   ('W999','BILL_SEATS_MAX','JPN','最大人数'),
('W999','BILL_SEATS_BILLED','KOR','과금 인원'),     ('W999','BILL_SEATS_BILLED','ENG','Billed'),    ('W999','BILL_SEATS_BILLED','JPN','課金人数'),
('W999','BILL_SEATS_FREE','KOR','무료 인원'),       ('W999','BILL_SEATS_FREE','ENG','Free'),        ('W999','BILL_SEATS_FREE','JPN','無料人数'),
('W999','BILL_UNIT','KOR','인당 단가'),             ('W999','BILL_UNIT','ENG','Unit price'),        ('W999','BILL_UNIT','JPN','1人単価'),
('W999','BILL_SUBTOTAL','KOR','공급가'),            ('W999','BILL_SUBTOTAL','ENG','Subtotal'),      ('W999','BILL_SUBTOTAL','JPN','供給価'),
('W999','BILL_VAT','KOR','부가세'),                ('W999','BILL_VAT','ENG','VAT'),                ('W999','BILL_VAT','JPN','消費税'),
('W999','BILL_TOTAL','KOR','합계'),                ('W999','BILL_TOTAL','ENG','Total'),            ('W999','BILL_TOTAL','JPN','合計'),
('W999','BILL_STATUS','KOR','상태'),               ('W999','BILL_STATUS','ENG','Status'),          ('W999','BILL_STATUS','JPN','状態'),
('W999','BILL_PROVISIONAL','KOR','잠정'),          ('W999','BILL_PROVISIONAL','ENG','Provisional'),('W999','BILL_PROVISIONAL','JPN','暫定'),
('W999','BILL_ISSUED','KOR','확정'),               ('W999','BILL_ISSUED','ENG','Issued'),          ('W999','BILL_ISSUED','JPN','確定'),
-- W018 회사 청구서 화면
('W018','TITLE','KOR','청구서'),                   ('W018','TITLE','ENG','Invoices'),              ('W018','TITLE','JPN','請求書'),
('W018','NOTE','KOR','무료 인원을 초과하는 인원에 인당 단가가 부과되며, 부가세는 별도입니다. 진행 중인 달은 잠정 금액입니다.'),
('W018','NOTE','ENG','Seats above the free allowance are charged at the unit price; VAT is added separately. The current month is a provisional amount.'),
('W018','NOTE','JPN','無料人数を超える人数に1人単価が課金され、消費税は別途です。進行中の月は暫定金額です。'),
('W018','EMPTY','KOR','청구 내역이 없습니다.'),      ('W018','EMPTY','ENG','No invoices.'),          ('W018','EMPTY','JPN','請求履歴がありません。'),
('W018','REFRESH','KOR','새로고침'),               ('W018','REFRESH','ENG','Refresh'),             ('W018','REFRESH','JPN','更新'),
-- W008 테넌트 상세 결제정보 확장(운영사)
('W008','PER_SEAT','KOR','인당 단가(원)'),          ('W008','PER_SEAT','ENG','Per-seat price (KRW)'),('W008','PER_SEAT','JPN','1人単価（ウォン）'),
('W008','PER_SEAT_HINT','KOR','부가세 별도'),       ('W008','PER_SEAT_HINT','ENG','VAT excluded'),  ('W008','PER_SEAT_HINT','JPN','消費税別'),
('W008','FREE_SEATS','KOR','무료 인원'),            ('W008','FREE_SEATS','ENG','Free seats'),       ('W008','FREE_SEATS','JPN','無料人数'),
('W008','INVOICES','KOR','청구서'),                ('W008','INVOICES','ENG','Invoices'),           ('W008','INVOICES','JPN','請求書'),
('W008','CLOSE','KOR','마감'),                     ('W008','CLOSE','ENG','Close'),                 ('W008','CLOSE','JPN','締め'),
('W008','CLOSE_CONFIRM','KOR','이 달을 마감(확정)하시겠습니까? 확정 후에는 금액이 고정됩니다.'),
('W008','CLOSE_CONFIRM','ENG','Close (finalize) this month? The amount is locked after issuing.'),
('W008','CLOSE_CONFIRM','JPN','この月を締め（確定）しますか？確定後は金額が固定されます。');
