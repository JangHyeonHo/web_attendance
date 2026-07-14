-- =========================================================
-- V36: 회사 정보/결제 설정 화면(W019) 라벨(#14) — 3개국어
--  운영사 계약정보(A) vs 회사 자체관리(B) 분리: 회사가 사업자정보·결제수단을 스스로 관리하고
--  계약 요약(요금제·단가·무료좌석)은 읽기전용으로 확인.
-- =========================================================

-- 헤더/내비 라벨(공용 W999 — LABEL_KEY[W019]='COMPANY_INFO')
INSERT INTO language_master (window_id, lang_key, lang, lang_value) VALUES
('W999','COMPANY_INFO','KOR','회사 정보'), ('W999','COMPANY_INFO','ENG','Company info'), ('W999','COMPANY_INFO','JPN','会社情報');

-- 화면 텍스트(W019)
INSERT INTO language_master (window_id, lang_key, lang, lang_value) VALUES
('W019','COMPANY_INFO_TITLE','KOR','회사 정보 · 결제 설정'), ('W019','COMPANY_INFO_TITLE','ENG','Company & billing settings'), ('W019','COMPANY_INFO_TITLE','JPN','会社情報・決済設定'),
('W019','COMPANY_INFO_NOTE','KOR','사업자 정보와 결제 수단은 회사가 직접 관리합니다. 요금제·단가는 운영사와의 계약값(읽기전용)입니다.'),
('W019','COMPANY_INFO_NOTE','ENG','Manage your business info and payment method here. Plan and pricing are contract values (read-only).'),
('W019','COMPANY_INFO_NOTE','JPN','事業者情報と決済方法は会社が管理します。プラン・単価は契約値（読み取り専用）です。'),
('W019','BIZ_INFO','KOR','사업자 정보'), ('W019','BIZ_INFO','ENG','Business info'), ('W019','BIZ_INFO','JPN','事業者情報'),
('W019','BIZ_REG_NO_KR','KOR','사업자등록번호'), ('W019','BIZ_REG_NO_KR','ENG','Business reg. no.'), ('W019','BIZ_REG_NO_KR','JPN','事業者登録番号'),
('W019','BIZ_REG_NO_JP','KOR','법인번호'), ('W019','BIZ_REG_NO_JP','ENG','Corporate number'), ('W019','BIZ_REG_NO_JP','JPN','法人番号'),
('W019','CEO_NAME','KOR','대표자'), ('W019','CEO_NAME','ENG','Representative'), ('W019','CEO_NAME','JPN','代表者'),
('W019','ADDRESS','KOR','주소'), ('W019','ADDRESS','ENG','Address'), ('W019','ADDRESS','JPN','住所'),
('W019','CONTACT_NAME','KOR','담당자'), ('W019','CONTACT_NAME','ENG','Contact name'), ('W019','CONTACT_NAME','JPN','担当者'),
('W019','CONTACT_EMAIL','KOR','담당 이메일'), ('W019','CONTACT_EMAIL','ENG','Contact email'), ('W019','CONTACT_EMAIL','JPN','担当メール'),
('W019','CONTACT_PHONE','KOR','담당 연락처'), ('W019','CONTACT_PHONE','ENG','Contact phone'), ('W019','CONTACT_PHONE','JPN','担当電話'),
('W019','BIZ_REENTER_HINT','KOR','보안상 사업자번호·연락처는 저장 시 다시 입력해 주세요.'),
('W019','BIZ_REENTER_HINT','ENG','For security, re-enter the business number and phone when saving.'),
('W019','BIZ_REENTER_HINT','JPN','セキュリティのため、保存時に事業者番号・電話番号を再入力してください。'),
('W019','PAYMENT_SETTINGS','KOR','결제 설정'), ('W019','PAYMENT_SETTINGS','ENG','Payment settings'), ('W019','PAYMENT_SETTINGS','JPN','決済設定'),
('W019','CONTRACT_SUMMARY','KOR','계약 요약'), ('W019','CONTRACT_SUMMARY','ENG','Contract summary'), ('W019','CONTRACT_SUMMARY','JPN','契約サマリー'),
('W019','CONTRACT_READONLY_HINT','KOR','요금제·단가·무료 인원은 운영사가 정한 값입니다(수정 불가).'),
('W019','CONTRACT_READONLY_HINT','ENG','Plan, unit price and free seats are set by the provider (read-only).'),
('W019','CONTRACT_READONLY_HINT','JPN','プラン・単価・無料人数は運営会社が設定します（変更不可）。'),
('W019','PLAN','KOR','요금제'), ('W019','PLAN','ENG','Plan'), ('W019','PLAN','JPN','プラン'),
('W019','SEAT_PRICE','KOR','인당 단가'), ('W019','SEAT_PRICE','ENG','Per-seat price'), ('W019','SEAT_PRICE','JPN','1人あたり単価'),
('W019','FREE_SEATS','KOR','무료 인원'), ('W019','FREE_SEATS','ENG','Free seats'), ('W019','FREE_SEATS','JPN','無料人数'),
-- W019에서도 쓰는 공용 라벨 재게시(화면 텍스트로 확실히 전달)
('W019','SUBMIT','KOR','저장'), ('W019','SUBMIT','ENG','Save'), ('W019','SUBMIT','JPN','保存'),
('W019','SAVED','KOR','저장되었습니다'), ('W019','SAVED','ENG','Saved'), ('W019','SAVED','JPN','保存しました'),
('W019','BILL_METHOD','KOR','결제 수단'), ('W019','BILL_METHOD','ENG','Payment method'), ('W019','BILL_METHOD','JPN','決済方法'),
('W019','BILL_METHOD_INVOICE','KOR','계좌이체(세금계산서)'), ('W019','BILL_METHOD_INVOICE','ENG','Bank transfer (invoice)'), ('W019','BILL_METHOD_INVOICE','JPN','銀行振込（請求書）'),
('W019','BILL_METHOD_CARD','KOR','신용카드'), ('W019','BILL_METHOD_CARD','ENG','Credit card'), ('W019','BILL_METHOD_CARD','JPN','クレジットカード'),
('W019','BILL_EMAIL','KOR','청구 이메일'), ('W019','BILL_EMAIL','ENG','Billing email'), ('W019','BILL_EMAIL','JPN','請求先メール'),
('W019','BILL_MEMO','KOR','비고'), ('W019','BILL_MEMO','ENG','Memo'), ('W019','BILL_MEMO','JPN','備考');
