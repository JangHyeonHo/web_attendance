-- =========================================================
-- V33: UX 개편 후속(#10·#11·#12·#13·#14)
--  1) 국가별 기본 휴가 종류를 기존 테넌트에도 시드(#10 — 경조/병가/생리)
--  2) 기본 메일 템플릿을 예쁜 HTML로 교체(#13 — 단일 라인 HTML, pre-wrap 래퍼와 충돌 없음)
--  3) 신규 화면 라벨(제안 카드/일괄부여 검색/결제 정보) 3개국어(#11·#12·#14)
-- 명칭·유급여부 등은 되돌릴 수 있는 기본값(관리자가 종류 화면에서 수정·비활성 가능).
-- =========================================================

-- ---------------------------------------------------------
-- 1) 국가별 기본 휴가 종류 — 기존 테넌트 보정(신규 테넌트는 LeaveService.seedDefaults가 처리)
--    단위 DAY 고정. 없는 종류만 추가(INSERT ... WHERE NOT EXISTS).
-- ---------------------------------------------------------
-- KR: 경조휴가(유급)·병가(무급)·생리휴가(무급)
INSERT INTO leave_type (tenant_id, code, name, paid, unit, requires_approval, is_annual, active, sort_order)
SELECT t.tenant_id, 'CONDOLENCE', '경조휴가', TRUE, 'DAY', TRUE, FALSE, TRUE, 2
FROM tenant t WHERE t.country = 'KR'
  AND NOT EXISTS (SELECT 1 FROM leave_type lt WHERE lt.tenant_id = t.tenant_id AND lt.code = 'CONDOLENCE');
INSERT INTO leave_type (tenant_id, code, name, paid, unit, requires_approval, is_annual, active, sort_order)
SELECT t.tenant_id, 'SICK', '병가', FALSE, 'DAY', TRUE, FALSE, TRUE, 3
FROM tenant t WHERE t.country = 'KR'
  AND NOT EXISTS (SELECT 1 FROM leave_type lt WHERE lt.tenant_id = t.tenant_id AND lt.code = 'SICK');
INSERT INTO leave_type (tenant_id, code, name, paid, unit, requires_approval, is_annual, active, sort_order)
SELECT t.tenant_id, 'MENSTRUAL', '생리휴가', FALSE, 'DAY', TRUE, FALSE, TRUE, 4
FROM tenant t WHERE t.country = 'KR'
  AND NOT EXISTS (SELECT 1 FROM leave_type lt WHERE lt.tenant_id = t.tenant_id AND lt.code = 'MENSTRUAL');

-- JP: 慶弔休暇(有給)·病気休暇(無給)·生理休暇(無給)
INSERT INTO leave_type (tenant_id, code, name, paid, unit, requires_approval, is_annual, active, sort_order)
SELECT t.tenant_id, 'CONDOLENCE', '慶弔休暇', TRUE, 'DAY', TRUE, FALSE, TRUE, 2
FROM tenant t WHERE t.country = 'JP'
  AND NOT EXISTS (SELECT 1 FROM leave_type lt WHERE lt.tenant_id = t.tenant_id AND lt.code = 'CONDOLENCE');
INSERT INTO leave_type (tenant_id, code, name, paid, unit, requires_approval, is_annual, active, sort_order)
SELECT t.tenant_id, 'SICK', '病気休暇', FALSE, 'DAY', TRUE, FALSE, TRUE, 3
FROM tenant t WHERE t.country = 'JP'
  AND NOT EXISTS (SELECT 1 FROM leave_type lt WHERE lt.tenant_id = t.tenant_id AND lt.code = 'SICK');
INSERT INTO leave_type (tenant_id, code, name, paid, unit, requires_approval, is_annual, active, sort_order)
SELECT t.tenant_id, 'MENSTRUAL', '生理休暇', FALSE, 'DAY', TRUE, FALSE, TRUE, 4
FROM tenant t WHERE t.country = 'JP'
  AND NOT EXISTS (SELECT 1 FROM leave_type lt WHERE lt.tenant_id = t.tenant_id AND lt.code = 'MENSTRUAL');

-- ---------------------------------------------------------
-- 2) 기본 메일 템플릿 HTML화(#13) — 전역 기본(mail_template). 회사 오버라이드(tenant_mail_template)는 불변.
--    단일 라인 HTML: SmtpMailSender의 pre-wrap 래퍼/프론트 미리보기 pre-wrap과 충돌하지 않는다.
-- ---------------------------------------------------------
UPDATE mail_template SET body =
'<div style="max-width:480px;margin:0 auto;font-family:sans-serif;color:#1f2937"><div style="background:#0f766e;padding:20px 24px;border-radius:12px 12px 0 0"><span style="color:#ffffff;font-size:18px;font-weight:700">{tenantName}</span></div><div style="border:1px solid #e5e7eb;border-top:none;border-radius:0 0 12px 12px;padding:24px 24px 28px"><p style="font-size:16px;font-weight:600;margin:0 0 14px">{memberName}님, 환영합니다.</p><p style="margin:0 0 22px;line-height:1.7;color:#374151">{inviterName}님이 회원님을 {tenantName} 출결 시스템에 초대했습니다. 아래 버튼을 눌러 비밀번호를 설정하면 바로 이용할 수 있습니다.</p><div style="text-align:center;margin:0 0 22px"><a href="{actionUrl}" style="display:inline-block;background:#0f766e;color:#ffffff;text-decoration:none;padding:13px 32px;border-radius:8px;font-weight:600;font-size:15px">비밀번호 설정하기</a></div><p style="margin:0;color:#6b7280;font-size:13px;line-height:1.6">이 링크는 {expiresAt}까지 유효합니다.<br>본인이 요청하지 않았다면 이 메일을 무시하셔도 됩니다.</p></div></div>'
WHERE purpose = 'INVITE' AND lang = 'KOR';

UPDATE mail_template SET body =
'<div style="max-width:480px;margin:0 auto;font-family:sans-serif;color:#1f2937"><div style="background:#0f766e;padding:20px 24px;border-radius:12px 12px 0 0"><span style="color:#ffffff;font-size:18px;font-weight:700">{tenantName}</span></div><div style="border:1px solid #e5e7eb;border-top:none;border-radius:0 0 12px 12px;padding:24px 24px 28px"><p style="font-size:16px;font-weight:600;margin:0 0 14px">Welcome, {memberName}!</p><p style="margin:0 0 22px;line-height:1.7;color:#374151">{inviterName} has invited you to the {tenantName} attendance system. Set your password with the button below to get started.</p><div style="text-align:center;margin:0 0 22px"><a href="{actionUrl}" style="display:inline-block;background:#0f766e;color:#ffffff;text-decoration:none;padding:13px 32px;border-radius:8px;font-weight:600;font-size:15px">Set your password</a></div><p style="margin:0;color:#6b7280;font-size:13px;line-height:1.6">This link is valid until {expiresAt}.<br>If you did not expect this email, you can safely ignore it.</p></div></div>'
WHERE purpose = 'INVITE' AND lang = 'ENG';

UPDATE mail_template SET body =
'<div style="max-width:480px;margin:0 auto;font-family:sans-serif;color:#1f2937"><div style="background:#0f766e;padding:20px 24px;border-radius:12px 12px 0 0"><span style="color:#ffffff;font-size:18px;font-weight:700">{tenantName}</span></div><div style="border:1px solid #e5e7eb;border-top:none;border-radius:0 0 12px 12px;padding:24px 24px 28px"><p style="font-size:16px;font-weight:600;margin:0 0 14px">{memberName}さん、ようこそ。</p><p style="margin:0 0 22px;line-height:1.7;color:#374151">{inviterName}さんがあなたを{tenantName}の勤怠システムに招待しました。下のボタンからパスワードを設定してご利用ください。</p><div style="text-align:center;margin:0 0 22px"><a href="{actionUrl}" style="display:inline-block;background:#0f766e;color:#ffffff;text-decoration:none;padding:13px 32px;border-radius:8px;font-weight:600;font-size:15px">パスワードを設定</a></div><p style="margin:0;color:#6b7280;font-size:13px;line-height:1.6">このリンクは{expiresAt}まで有効です。<br>心当たりがない場合は、このメールを無視してください。</p></div></div>'
WHERE purpose = 'INVITE' AND lang = 'JPN';

UPDATE mail_template SET body =
'<div style="max-width:480px;margin:0 auto;font-family:sans-serif;color:#1f2937"><div style="background:#0f766e;padding:20px 24px;border-radius:12px 12px 0 0"><span style="color:#ffffff;font-size:18px;font-weight:700">{tenantName}</span></div><div style="border:1px solid #e5e7eb;border-top:none;border-radius:0 0 12px 12px;padding:24px 24px 28px"><p style="font-size:16px;font-weight:600;margin:0 0 14px">{memberName}님, 안녕하세요.</p><p style="margin:0 0 22px;line-height:1.7;color:#374151">{tenantName} 계정의 비밀번호 재설정 요청을 접수했습니다. 아래 버튼을 눌러 새 비밀번호를 설정하세요.</p><div style="text-align:center;margin:0 0 22px"><a href="{actionUrl}" style="display:inline-block;background:#0f766e;color:#ffffff;text-decoration:none;padding:13px 32px;border-radius:8px;font-weight:600;font-size:15px">비밀번호 재설정</a></div><p style="margin:0;color:#6b7280;font-size:13px;line-height:1.6">이 링크는 {expiresAt}까지 유효합니다.<br>본인이 요청하지 않았다면 이 메일을 무시하셔도 됩니다.</p></div></div>'
WHERE purpose = 'RESET' AND lang = 'KOR';

UPDATE mail_template SET body =
'<div style="max-width:480px;margin:0 auto;font-family:sans-serif;color:#1f2937"><div style="background:#0f766e;padding:20px 24px;border-radius:12px 12px 0 0"><span style="color:#ffffff;font-size:18px;font-weight:700">{tenantName}</span></div><div style="border:1px solid #e5e7eb;border-top:none;border-radius:0 0 12px 12px;padding:24px 24px 28px"><p style="font-size:16px;font-weight:600;margin:0 0 14px">Hello, {memberName}.</p><p style="margin:0 0 22px;line-height:1.7;color:#374151">We received a request to reset the password for your {tenantName} account. Set a new password with the button below.</p><div style="text-align:center;margin:0 0 22px"><a href="{actionUrl}" style="display:inline-block;background:#0f766e;color:#ffffff;text-decoration:none;padding:13px 32px;border-radius:8px;font-weight:600;font-size:15px">Reset password</a></div><p style="margin:0;color:#6b7280;font-size:13px;line-height:1.6">This link is valid until {expiresAt}.<br>If you did not request this, you can safely ignore this email.</p></div></div>'
WHERE purpose = 'RESET' AND lang = 'ENG';

UPDATE mail_template SET body =
'<div style="max-width:480px;margin:0 auto;font-family:sans-serif;color:#1f2937"><div style="background:#0f766e;padding:20px 24px;border-radius:12px 12px 0 0"><span style="color:#ffffff;font-size:18px;font-weight:700">{tenantName}</span></div><div style="border:1px solid #e5e7eb;border-top:none;border-radius:0 0 12px 12px;padding:24px 24px 28px"><p style="font-size:16px;font-weight:600;margin:0 0 14px">{memberName}さん、こんにちは。</p><p style="margin:0 0 22px;line-height:1.7;color:#374151">{tenantName}アカウントのパスワード再設定リクエストを受け付けました。下のボタンから新しいパスワードを設定してください。</p><div style="text-align:center;margin:0 0 22px"><a href="{actionUrl}" style="display:inline-block;background:#0f766e;color:#ffffff;text-decoration:none;padding:13px 32px;border-radius:8px;font-weight:600;font-size:15px">パスワードを再設定</a></div><p style="margin:0;color:#6b7280;font-size:13px;line-height:1.6">このリンクは{expiresAt}まで有効です。<br>心当たりがない場合は、このメールを無視してください。</p></div></div>'
WHERE purpose = 'RESET' AND lang = 'JPN';

-- ---------------------------------------------------------
-- 3) 신규 화면 라벨(3개국어)
-- ---------------------------------------------------------
-- 휴가 관리(W016): 일괄부여 검색 + 법정 제안 카드(#9·#11)
INSERT INTO language_master (window_id, lang_key, lang, lang_value) VALUES
('W016','MEMBER_SEARCH','KOR','이름 검색'),          ('W016','MEMBER_SEARCH','ENG','Search by name'),     ('W016','MEMBER_SEARCH','JPN','名前で検索'),
('W016','SUGGEST_TITLE','KOR','법정 연차 제안'),      ('W016','SUGGEST_TITLE','ENG','Statutory annual leave'), ('W016','SUGGEST_TITLE','JPN','法定年休の提案'),
('W016','SUGGEST_CURRENT','KOR','현재 연차 잔여'),    ('W016','SUGGEST_CURRENT','ENG','Current balance'),  ('W016','SUGGEST_CURRENT','JPN','現在の年休残'),
('W016','SUGGEST_HINT','KOR','제안을 적용하면 법정 계산값에 맞춰 연차가 자동 부여(조정)됩니다.'),
('W016','SUGGEST_HINT','ENG','Applying grants annual leave to match the statutory amount.'),
('W016','SUGGEST_HINT','JPN','適用すると法定計算に合わせて年休が付与（調整）されます。'),
('W016','SUGGEST_NEED_HIRE','KOR','입사일을 먼저 등록하면 법정 제안이 계산됩니다.'),
('W016','SUGGEST_NEED_HIRE','ENG','Register the hire date first to compute the suggestion.'),
('W016','SUGGEST_NEED_HIRE','JPN','入社日を登録すると法定提案が計算されます。');

-- 청구서/결제(W999 공용 — 기존 BILL_* 위치): 결제 정보 등록(#14)
INSERT INTO language_master (window_id, lang_key, lang, lang_value) VALUES
('W999','BILL_INVOICES','KOR','청구서'),              ('W999','BILL_INVOICES','ENG','Invoices'),          ('W999','BILL_INVOICES','JPN','請求書'),
('W999','BILL_PAYMENT_INFO','KOR','결제 정보'),        ('W999','BILL_PAYMENT_INFO','ENG','Payment info'),  ('W999','BILL_PAYMENT_INFO','JPN','決済情報'),
('W999','BILL_METHOD','KOR','결제 수단'),              ('W999','BILL_METHOD','ENG','Payment method'),      ('W999','BILL_METHOD','JPN','決済方法'),
('W999','BILL_METHOD_INVOICE','KOR','계좌이체(세금계산서)'), ('W999','BILL_METHOD_INVOICE','ENG','Bank transfer (invoice)'), ('W999','BILL_METHOD_INVOICE','JPN','銀行振込（請求書）'),
('W999','BILL_METHOD_CARD','KOR','신용카드'),          ('W999','BILL_METHOD_CARD','ENG','Credit card'),    ('W999','BILL_METHOD_CARD','JPN','クレジットカード'),
('W999','BILL_EMAIL','KOR','청구 이메일'),            ('W999','BILL_EMAIL','ENG','Billing email'),        ('W999','BILL_EMAIL','JPN','請求先メール'),
('W999','BILL_MEMO','KOR','비고'),                    ('W999','BILL_MEMO','ENG','Memo'),                  ('W999','BILL_MEMO','JPN','備考');
