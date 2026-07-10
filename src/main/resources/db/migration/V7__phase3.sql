-- =========================================================
-- V7: Phase 3 — 공휴일([H])/근무 스케줄([S])/이메일 온보딩([E]) 합류 단일 파일
--  - 블록 순서(교차 리뷰 CR3-4 확정): [H-1]~[H-4] → [S-1] → [E1]~[E4] → 언어 마스터 시드(H→S→E)
--  - 전 구문 재실행 내성(V4 방식 — 부분 실패 시 flyway repair 후 동일 파일 재실행 가능)
--  - users ALTER는 도메인 구획별 3문([S-1]/[E3]/[E4]) 유지 — 병합하지 않는다(D17)
-- =========================================================

-- ---------------------------------------------------------
-- [H-1] tenant.country 추가 (V6과 동일 근거로 기본 KR — 기존 테넌트는 전부 한국 형식 검증을 통과한 값)
-- ---------------------------------------------------------
ALTER TABLE tenant
    ADD COLUMN IF NOT EXISTS country CHAR(2) NOT NULL DEFAULT 'KR'
        COMMENT '소재국(ISO 3166-1 alpha-2) — 공휴일 동기화·사업자 식별번호 체계 결정' AFTER name;

-- ---------------------------------------------------------
-- [H-2] tenant_profile.country가 있는 행은 그 값으로 backfill (프로필 미등록 테넌트는 기본 KR 유지)
--       컬럼 제거([H-3]) 후 재실행되면 이 UPDATE가 파스 단계에서 실패하므로 information_schema 가드 + EXECUTE IMMEDIATE
-- ---------------------------------------------------------
SET @has_col = (SELECT COUNT(*) FROM information_schema.COLUMNS
                WHERE TABLE_SCHEMA = DATABASE()
                  AND TABLE_NAME = 'tenant_profile' AND COLUMN_NAME = 'country');
SET @sql = IF(@has_col > 0,
    'UPDATE tenant t JOIN tenant_profile p ON p.tenant_id = t.tenant_id SET t.country = p.country',
    'SELECT 1');
EXECUTE IMMEDIATE @sql;

-- ---------------------------------------------------------
-- [H-3] 승격 완료 — profile 쪽 컬럼 제거 (단일 출처 강제. 남기면 두 값이 갈라지는 순간 정본 분쟁)
-- ---------------------------------------------------------
ALTER TABLE tenant_profile DROP COLUMN IF EXISTS country;

-- ---------------------------------------------------------
-- [H-4] holiday: name → holiday_name 리네임 + 유형/타임스탬프 추가
--       기존 행 backfill = COMPANY(DEFAULT로 흡수) — NATIONAL로 분류하면 첫 재동기화의
--       삭제 범위에 들어가 지워질 수 있으므로 불가침인 COMPANY가 안전한 기본값
-- ---------------------------------------------------------
ALTER TABLE holiday
    CHANGE COLUMN IF EXISTS name holiday_name VARCHAR(100) NOT NULL
        COMMENT '공휴일 명칭(NATIONAL은 Nager.Date localName — 현지어)',
    ADD COLUMN IF NOT EXISTS holiday_type VARCHAR(10) NOT NULL DEFAULT 'COMPANY'
        COMMENT '유형(NATIONAL=국가 공휴일, 동기화 대상 / COMPANY=회사 지정, 동기화 불가침)' AFTER holiday_name,
    ADD COLUMN IF NOT EXISTS created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '등록일',
    ADD COLUMN IF NOT EXISTS updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
        ON UPDATE CURRENT_TIMESTAMP COMMENT '수정일',
    ADD CONSTRAINT IF NOT EXISTS ck_holiday_type CHECK (holiday_type IN ('NATIONAL', 'COMPANY'));

-- =========================================================
-- [S-1] V7 work-schedule 몫: 개인 기본 근무 스케줄
--  - 기존 전원 하드코딩(09:00~18:00)을 멤버별 컬럼으로. 기존 행은 DEFAULT로 현행 동작 보존
--  - work_schedule(일자별 오버라이드)은 무변경 — 우선순위: work_schedule > 개인 기본값
-- =========================================================
ALTER TABLE users
    ADD COLUMN IF NOT EXISTS default_work_start TIME NOT NULL DEFAULT '09:00:00'
        COMMENT '개인 기본 시업 시각' AFTER depart_cd,
    ADD COLUMN IF NOT EXISTS default_work_end   TIME NOT NULL DEFAULT '18:00:00'
        COMMENT '개인 기본 종업 시각' AFTER default_work_start;

-- ---------------------------------------------------------
-- [E1] user_token: 초대/재설정 토큰 — 원문 비저장(SHA-256 해시만), 1회용. TTL은 앱이 부여.
-- ---------------------------------------------------------
CREATE TABLE IF NOT EXISTS user_token (
    token_hash CHAR(64)    NOT NULL COMMENT '토큰 SHA-256 해시(hex) — 원문은 어디에도 저장하지 않음',
    tenant_id  BIGINT      NOT NULL COMMENT '테넌트 ID',
    user_id    BIGINT      NOT NULL COMMENT '유저 ID',
    purpose    VARCHAR(10) NOT NULL COMMENT '용도(INVITE/RESET)',
    expires_at DATETIME    NOT NULL COMMENT '만료 시각',
    used_at    DATETIME    NULL COMMENT '사용 시각(NULL만 유효 — 1회용)',
    created_at DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '발급 시각',
    PRIMARY KEY (token_hash),
    KEY idx_user_token_user (tenant_id, user_id),
    CONSTRAINT fk_user_token_tenant FOREIGN KEY (tenant_id) REFERENCES tenant (tenant_id),
    CONSTRAINT fk_user_token_user   FOREIGN KEY (user_id)   REFERENCES users (user_id),
    CONSTRAINT ck_user_token_purpose CHECK (purpose IN ('INVITE', 'RESET'))
) COMMENT '유저 토큰(초대/비밀번호 재설정) — 해시만 저장, 1회용';

-- ---------------------------------------------------------
-- [E2] mail_template: 메일 템플릿 — 행 집합은 시드 6행(purpose×lang) 고정, 수정만 허용
--      자연키 (purpose, lang)가 그대로 PK — 행 집합이 코드(enum×언어)로 닫혀 있어 대리키 불요
-- ---------------------------------------------------------
CREATE TABLE IF NOT EXISTS mail_template (
    purpose     VARCHAR(10)  NOT NULL COMMENT '용도(INVITE/RESET)',
    lang        VARCHAR(5)   NOT NULL COMMENT '언어(KOR/ENG/JPN)',
    subject     VARCHAR(200) NOT NULL COMMENT '제목({변수} 치환)',
    body        TEXT         NOT NULL COMMENT '본문(텍스트 메일, {변수} 치환)',
    created_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '등록일',
    updated_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP
                             ON UPDATE CURRENT_TIMESTAMP COMMENT '수정일',
    PRIMARY KEY (purpose, lang),
    CONSTRAINT ck_mail_template_purpose CHECK (purpose IN ('INVITE', 'RESET'))
) COMMENT '메일 템플릿(SYSTEM_ADMIN 관리)';

-- 시드(INSERT IGNORE — 관리자가 수정한 행은 덮지 않는다, V3/V5 방식). 실값은 email-onboarding §7 확정 초안.
INSERT IGNORE INTO mail_template (purpose, lang, subject, body) VALUES
('INVITE','KOR','[{tenantName}] 출결 시스템 초대 — 비밀번호를 설정해 주세요',
'{memberName}님, 안녕하세요.

{inviterName}님이 {tenantName}의 출결 시스템에 회원님을 초대했습니다.
아래 링크에서 비밀번호를 설정하면 바로 이용하실 수 있습니다.

{actionUrl}

이 링크는 {expiresAt}까지 1회만 사용할 수 있습니다.
본인에게 온 메일이 아니라면 무시해 주세요.'),
('INVITE','ENG','[{tenantName}] You''re invited — set your password',
'Hello {memberName},

{inviterName} has invited you to {tenantName}''s attendance system. Set your password at the link below to get started.

{actionUrl}

This link can be used once and expires at {expiresAt}.

If you did not expect this email, please ignore it.'),
('INVITE','JPN','[{tenantName}] 勤怠システムへのご招待 — パスワード設定のお願い',
'{memberName} 様

{inviterName} 様が {tenantName} の勤怠システムにあなたを招待しました。以下のリンクからパスワードを設定すると、すぐにご利用いただけます。

{actionUrl}

このリンクは {expiresAt} まで、1回のみ有効です。

お心当たりのない場合は、このメールを破棄してください。'),
('RESET','KOR','[{tenantName}] 비밀번호 재설정 안내',
'{memberName}님, 안녕하세요.

{tenantName} 출결 시스템의 비밀번호 재설정 요청을 받았습니다. 아래 링크에서 새 비밀번호를 설정해 주세요.

{actionUrl}

이 링크는 {expiresAt}까지 1회만 사용할 수 있습니다.

요청하신 적이 없다면 이 메일을 무시해 주세요. 비밀번호는 변경되지 않습니다.'),
('RESET','ENG','[{tenantName}] Password reset request',
'Hello {memberName},

We received a request to reset your password for {tenantName}. Set a new password at the link below.

{actionUrl}

This link can be used once and expires at {expiresAt}.

If you did not request this, please ignore this email. Your password will not change.'),
('RESET','JPN','[{tenantName}] パスワード再設定のご案内',
'{memberName} 様

{tenantName} 勤怠システムのパスワード再設定のリクエストを受け付けました。以下のリンクから新しいパスワードを設定してください。

{actionUrl}

このリンクは {expiresAt} まで、1回のみ有効です。

お心当たりのない場合は破棄してください。パスワードは変更されません。');

-- ---------------------------------------------------------
-- [E3] users.password_changed_at: 재로그인 강제의 기준 시각
-- ---------------------------------------------------------
-- DATETIME(6): 초 단위 절삭이면 같은 초에 발급된 세션이 무효화를 비껴가는 레이스가 생긴다(실측)
ALTER TABLE users
    ADD COLUMN IF NOT EXISTS password_changed_at DATETIME(6) NULL
        COMMENT '비밀번호 최종 변경 시각 — 이전 발급 세션은 무효(NULL=이력 없음, 기존 유저)' AFTER password_hash;

-- ---------------------------------------------------------
-- [E4] 소프트 삭제 후 재등록 허용: UNIQUE(tenant_id,email)이 삭제 행까지 점유하는 문제 해소.
--      email_key = 활성 행만 채워지는 생성 컬럼(NULL은 UNIQUE 비충돌, 앱 유지보수 불요)
--      → 오송신 삭제 후 같은/올바른 이메일 재등록 가능, 삭제 행의 email 원문은 감사용 보존.
--      UNIQUE 신구 교체는 단일문 원자성 유지(V4 [3-4] 관례).
-- ---------------------------------------------------------
ALTER TABLE users
    ADD COLUMN IF NOT EXISTS email_key VARCHAR(100)
        AS (CASE WHEN deleted THEN NULL ELSE email END) PERSISTENT
        COMMENT '활성 행의 이메일 사본(UNIQUE 전용 — 삭제 행은 NULL)' AFTER email,
    ADD UNIQUE KEY IF NOT EXISTS uk_users_tenant_email_key (tenant_id, email_key),
    DROP KEY IF EXISTS uk_users_tenant_email;

-- =========================================================
-- 언어 마스터 시드 (INSERT IGNORE, 3개국어 — H → S → E 순, 도메인 구획 주석 유지)
-- =========================================================

-- ---- [H] 공휴일 도메인 ----
INSERT IGNORE INTO language_master (window_id, lang_key, lang, lang_value) VALUES
('W999','HOLIDAYS','KOR','공휴일'),
('W999','HOLIDAYS','ENG','Holidays'),
('W999','HOLIDAYS','JPN','祝日'),
('W007','COUNTRY','KOR','소재국'),
('W007','COUNTRY','ENG','Country'),
('W007','COUNTRY','JPN','所在国'),
('W007','COUNTRY_KR','KOR','대한민국'),
('W007','COUNTRY_KR','ENG','South Korea'),
('W007','COUNTRY_KR','JPN','韓国'),
('W007','COUNTRY_JP','KOR','일본'),
('W007','COUNTRY_JP','ENG','Japan'),
('W007','COUNTRY_JP','JPN','日本'),
('W007','HOLIDAY_SYNC_FAILED_NOTICE','KOR','공휴일 자동 등록에 실패했습니다. 고객사 관리자가 공휴일 화면에서 동기화해 주세요.'),
('W007','HOLIDAY_SYNC_FAILED_NOTICE','ENG','Automatic holiday registration failed. Ask the tenant admin to sync on the holidays screen.'),
('W007','HOLIDAY_SYNC_FAILED_NOTICE','JPN','祝日の自動登録に失敗しました。管理者が祝日画面で同期してください。'),
('W013','HOLIDAYS_TITLE','KOR','공휴일 관리'),
('W013','HOLIDAYS_TITLE','ENG','Holiday Management'),
('W013','HOLIDAYS_TITLE','JPN','祝日管理'),
('W013','YEAR','KOR','년'),
('W013','YEAR','ENG','Year'),
('W013','YEAR','JPN','年'),
('W013','DATE','KOR','날짜'),
('W013','DATE','ENG','Date'),
('W013','DATE','JPN','日付'),
('W013','NAME','KOR','명칭'),
('W013','NAME','ENG','Name'),
('W013','NAME','JPN','名称'),
('W013','TYPE','KOR','유형'),
('W013','TYPE','ENG','Type'),
('W013','TYPE','JPN','種別'),
('W013','TYPE_NATIONAL','KOR','국가 공휴일'),
('W013','TYPE_NATIONAL','ENG','National'),
('W013','TYPE_NATIONAL','JPN','国民の祝日'),
('W013','TYPE_COMPANY','KOR','회사 지정'),
('W013','TYPE_COMPANY','ENG','Company'),
('W013','TYPE_COMPANY','JPN','会社指定'),
('W013','SYNC','KOR','국가 공휴일 동기화'),
('W013','SYNC','ENG','Sync national holidays'),
('W013','SYNC','JPN','祝日を同期'),
('W013','SYNC_CONFIRM','KOR','이 연도의 국가 공휴일을 최신 데이터로 교체합니다. 회사 지정 공휴일은 유지됩니다. 실행할까요?'),
('W013','SYNC_CONFIRM','ENG','Replace this year''s national holidays with the latest data. Company holidays are kept. Proceed?'),
('W013','SYNC_CONFIRM','JPN','この年の国民の祝日を最新データで置き換えます。会社指定は維持されます。実行しますか？'),
('W013','SYNC_DONE','KOR','동기화 완료: 추가 {inserted} / 삭제 {deleted} / 회사 지정 우선 {skipped}'),
('W013','SYNC_DONE','ENG','Synced: added {inserted}, removed {deleted}, kept company {skipped}'),
('W013','SYNC_DONE','JPN','同期完了：追加 {inserted}・削除 {deleted}・会社指定優先 {skipped}'),
('W013','SYNC_REVERT_WARN','KOR','국가 공휴일을 수정·삭제해도 다음 동기화에서 원래대로 복원됩니다. 영구히 바꾸려면 삭제 후 회사 지정으로 등록하세요.'),
('W013','SYNC_REVERT_WARN','ENG','Edits to national holidays are restored on the next sync. To make it permanent, delete and re-register as a company holiday.'),
('W013','SYNC_REVERT_WARN','JPN','国民の祝日への変更は次回の同期で元に戻ります。恒久的に変えるには削除して会社指定で登録してください。'),
('W013','ADD_HOLIDAY','KOR','회사 공휴일 등록'),
('W013','ADD_HOLIDAY','ENG','Add company holiday'),
('W013','ADD_HOLIDAY','JPN','会社の祝日を登録'),
('W013','DELETE_CONFIRM','KOR','이 공휴일을 삭제할까요?'),
('W013','DELETE_CONFIRM','ENG','Delete this holiday?'),
('W013','DELETE_CONFIRM','JPN','この祝日を削除しますか？'),
('W013','EMPTY','KOR','등록된 공휴일이 없습니다. 동기화를 실행해 주세요.'),
('W013','EMPTY','ENG','No holidays registered. Run a sync.'),
('W013','EMPTY','JPN','祝日が未登録です。同期を実行してください。');

-- ---- [S] 근무 스케줄 도메인 ----
INSERT IGNORE INTO language_master (window_id, lang_key, lang, lang_value) VALUES
('W009','WORK_START','KOR','근무 시작'),
('W009','WORK_START','ENG','Work start'),
('W009','WORK_START','JPN','始業時刻'),
('W009','WORK_END','KOR','근무 종료'),
('W009','WORK_END','ENG','Work end'),
('W009','WORK_END','JPN','終業時刻'),
('W009','EDIT_SCHEDULE','KOR','스케줄 수정'),
('W009','EDIT_SCHEDULE','ENG','Edit schedule'),
('W009','EDIT_SCHEDULE','JPN','スケジュール編集'),
('W006','BREAK_ACTUAL','KOR','실휴식'),
('W006','BREAK_ACTUAL','ENG','Break (actual)'),
('W006','BREAK_ACTUAL','JPN','休憩（実績）'),
('W006','BREAK_STATUTORY','KOR','법정 휴게'),
('W006','BREAK_STATUTORY','ENG','Statutory break'),
('W006','BREAK_STATUTORY','JPN','法定休憩'),
('W006','TOTAL_WORK','KOR','총 근무시간'),
('W006','TOTAL_WORK','ENG','Total work'),
('W006','TOTAL_WORK','JPN','総労働時間'),
('W006','MONTH_TOTAL','KOR','월 합계'),
('W006','MONTH_TOTAL','ENG','Monthly total'),
('W006','MONTH_TOTAL','JPN','月合計'),
('W005','TODAY_SCHEDULE','KOR','오늘 근무'),
('W005','TODAY_SCHEDULE','ENG','Today''s schedule'),
('W005','TODAY_SCHEDULE','JPN','本日の勤務');

-- ---- [E] 이메일 온보딩 도메인 ----
INSERT IGNORE INTO language_master (window_id, lang_key, lang, lang_value) VALUES
('W001','FORGOT_PWD','KOR','비밀번호를 잊으셨나요?'),
('W001','FORGOT_PWD','ENG','Forgot your password?'),
('W001','FORGOT_PWD','JPN','パスワードをお忘れですか？'),
('W010','SETUP_TITLE','KOR','비밀번호 설정'),
('W010','SETUP_TITLE','ENG','Set your password'),
('W010','SETUP_TITLE','JPN','パスワード設定'),
('W010','SETUP_INVITE_DESC','KOR','초대를 확인했습니다. 사용할 비밀번호를 설정해 주세요.'),
('W010','SETUP_INVITE_DESC','ENG','Invitation confirmed. Choose your password.'),
('W010','SETUP_INVITE_DESC','JPN','招待を確認しました。パスワードを設定してください。'),
('W010','SETUP_RESET_DESC','KOR','본인 확인이 완료되었습니다. 새 비밀번호를 설정해 주세요.'),
('W010','SETUP_RESET_DESC','ENG','Verified. Choose a new password.'),
('W010','SETUP_RESET_DESC','JPN','本人確認が完了しました。新しいパスワードを設定してください。'),
('W010','SETUP_DONE','KOR','비밀번호가 설정되었습니다. 로그인해 주세요.'),
('W010','SETUP_DONE','ENG','Password set. Please sign in.'),
('W010','SETUP_DONE','JPN','パスワードを設定しました。ログインしてください。'),
('W010','TOKEN_INVALID_DESC','KOR','링크가 유효하지 않거나 만료되었습니다.'),
('W010','TOKEN_INVALID_DESC','ENG','This link is invalid or has expired.'),
('W010','TOKEN_INVALID_DESC','JPN','リンクが無効か、期限切れです。'),
('W010','NEW_PWD','KOR','새 비밀번호'),
('W010','NEW_PWD','ENG','New password'),
('W010','NEW_PWD','JPN','新しいパスワード'),
('W010','NEW_PWD_CONFIRM','KOR','새 비밀번호 확인'),
('W010','NEW_PWD_CONFIRM','ENG','Confirm new password'),
('W010','NEW_PWD_CONFIRM','JPN','新しいパスワード（確認）'),
('W010','PWD_MISMATCH','KOR','비밀번호가 일치하지 않습니다.'),
('W010','PWD_MISMATCH','ENG','Passwords do not match.'),
('W010','PWD_MISMATCH','JPN','パスワードが一致しません。'),
('W010','GO_RESET','KOR','재설정 다시 요청'),
('W010','GO_RESET','ENG','Request a new reset'),
('W010','GO_RESET','JPN','再設定を再リクエスト'),
('W011','RESET_TITLE','KOR','비밀번호 재설정'),
('W011','RESET_TITLE','ENG','Reset password'),
('W011','RESET_TITLE','JPN','パスワード再設定'),
('W011','RESET_DESC','KOR','가입한 이메일을 입력하면 비밀번호 재설정 링크를 보내드립니다.'),
('W011','RESET_DESC','ENG','Enter your email and we will send a password reset link.'),
('W011','RESET_DESC','JPN','登録済みのメールアドレスを入力すると、再設定リンクをお送りします。'),
('W011','RESET_SUBMIT','KOR','재설정 메일 발송'),
('W011','RESET_SUBMIT','ENG','Send reset email'),
('W011','RESET_SUBMIT','JPN','再設定メールを送信'),
('W011','RESET_SENT','KOR','계정이 있다면 재설정 메일을 보냈습니다. 받은편지함을 확인해 주세요.'),
('W011','RESET_SENT','ENG','If an account exists, we sent a reset email. Check your inbox.'),
('W011','RESET_SENT','JPN','アカウントが存在する場合、再設定メールを送信しました。受信箱をご確認ください。'),
('W012','TPL_TITLE','KOR','메일 템플릿 관리'),
('W012','TPL_TITLE','ENG','Mail templates'),
('W012','TPL_TITLE','JPN','メールテンプレート管理'),
('W012','TPL_SUBJECT','KOR','제목'),
('W012','TPL_SUBJECT','ENG','Subject'),
('W012','TPL_SUBJECT','JPN','件名'),
('W012','TPL_BODY','KOR','본문'),
('W012','TPL_BODY','ENG','Body'),
('W012','TPL_BODY','JPN','本文'),
('W012','TPL_VARS_HINT','KOR','사용 가능 변수: {memberName} {tenantName} {actionUrl} {expiresAt} {inviterName}'),
('W012','TPL_VARS_HINT','ENG','Available variables: {memberName} {tenantName} {actionUrl} {expiresAt} {inviterName}'),
('W012','TPL_VARS_HINT','JPN','使用可能な変数: {memberName} {tenantName} {actionUrl} {expiresAt} {inviterName}'),
('W012','PREVIEW','KOR','미리보기'),
('W012','PREVIEW','ENG','Preview'),
('W012','PREVIEW','JPN','プレビュー'),
('W009','CONFIRM_SEND_DESC','KOR','이 주소로 초대 메일을 발송합니다.'),
('W009','CONFIRM_SEND_DESC','ENG','An invitation will be sent to this address.'),
('W009','CONFIRM_SEND_DESC','JPN','このアドレスに招待メールを送信します。'),
('W009','SEND_INVITE','KOR','초대 메일 발송'),
('W009','SEND_INVITE','ENG','Send invitation'),
('W009','SEND_INVITE','JPN','招待メールを送信'),
('W009','INVITE_SENT','KOR','초대 메일 발송됨'),
('W009','INVITE_SENT','ENG','Invitation sent'),
('W009','INVITE_SENT','JPN','招待メールを送信しました'),
('W009','MAIL_FAILED','KOR','메일 발송에 실패했습니다. 재발송해 주세요.'),
('W009','MAIL_FAILED','ENG','Failed to send email. Please resend.'),
('W009','MAIL_FAILED','JPN','メール送信に失敗しました。再送信してください。'),
('W009','INVITE_EXPIRED','KOR','만료 — 재발송 필요'),
('W009','INVITE_EXPIRED','ENG','Expired — resend needed'),
('W009','INVITE_EXPIRED','JPN','期限切れ — 再送信が必要'),
('W009','RESEND','KOR','재발송'),
('W009','RESEND','ENG','Resend'),
('W009','RESEND','JPN','再送信'),
('W009','DELETE','KOR','삭제'),
('W009','DELETE','ENG','Delete'),
('W009','DELETE','JPN','削除'),
('W009','DELETE_CONFIRM','KOR','이 멤버를 삭제합니다. 출결 기록은 보존됩니다.'),
('W009','DELETE_CONFIRM','ENG','This member will be deleted. Attendance records are kept.'),
('W009','DELETE_CONFIRM','JPN','このメンバーを削除します。勤怠記録は保持されます。'),
('W999','MAIL_TEMPLATES','KOR','메일 템플릿'),
('W999','MAIL_TEMPLATES','ENG','Mail templates'),
('W999','MAIL_TEMPLATES','JPN','メールテンプレート'),
('W007','ADMIN_INVITE_SENT','KOR','관리자 초대 메일 발송됨'),
('W007','ADMIN_INVITE_SENT','ENG','Administrator invitation sent'),
('W007','ADMIN_INVITE_SENT','JPN','管理者招待メールを送信しました'),
('W007','ADMIN_INVITE_RESEND','KOR','관리자 초대 재발송'),
('W007','ADMIN_INVITE_RESEND','ENG','Resend admin invitation'),
('W007','ADMIN_INVITE_RESEND','JPN','管理者招待を再送信');
