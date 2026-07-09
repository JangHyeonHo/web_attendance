-- =========================================================
-- V8: 회사(테넌트)별 메일 템플릿 오버라이드
--  - 기본 템플릿은 V7의 mail_template(전역, SYSTEM_ADMIN 관리) 그대로 제공
--  - 고객사(TENANT_ADMIN)가 자기 회사 문구만 오버라이드 — 행이 없으면 전역 기본값 사용
--  - 발송 해석 순서: tenant_mail_template → mail_template
-- =========================================================

CREATE TABLE IF NOT EXISTS tenant_mail_template (
    tenant_id   BIGINT       NOT NULL COMMENT '테넌트 ID',
    purpose     VARCHAR(10)  NOT NULL COMMENT '용도(INVITE/RESET)',
    lang        VARCHAR(5)   NOT NULL COMMENT '언어(KOR/ENG/JPN)',
    subject     VARCHAR(200) NOT NULL COMMENT '제목({변수} 치환)',
    body        TEXT         NOT NULL COMMENT '본문(텍스트 메일, {변수} 치환)',
    created_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '등록일',
    updated_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP
                             ON UPDATE CURRENT_TIMESTAMP COMMENT '수정일',
    PRIMARY KEY (tenant_id, purpose, lang),
    CONSTRAINT fk_tenant_mail_template_tenant
        FOREIGN KEY (tenant_id) REFERENCES tenant (tenant_id),
    CONSTRAINT ck_tenant_mail_template_purpose CHECK (purpose IN ('INVITE', 'RESET'))
) COMMENT '테넌트별 메일 템플릿 오버라이드(없으면 전역 mail_template 기본값)';

-- ---------------------------------------------------------
-- W014(회사 메일 템플릿 관리 — TENANT_ADMIN) 화면 텍스트 시드
-- W012와 같은 키 구성 + 오버라이드 상태·되돌리기 라벨
-- ---------------------------------------------------------
INSERT IGNORE INTO language_master (window_id, lang_key, lang, lang_value) VALUES
('W014','TPL_TITLE','KOR','회사 메일 템플릿'),
('W014','TPL_TITLE','ENG','Company mail templates'),
('W014','TPL_TITLE','JPN','会社メールテンプレート'),
('W014','TPL_SUBJECT','KOR','제목'),
('W014','TPL_SUBJECT','ENG','Subject'),
('W014','TPL_SUBJECT','JPN','件名'),
('W014','TPL_BODY','KOR','본문'),
('W014','TPL_BODY','ENG','Body'),
('W014','TPL_BODY','JPN','本文'),
('W014','TPL_VARS_HINT','KOR','사용 가능 변수: {memberName} {tenantName} {actionUrl} {expiresAt} {inviterName}'),
('W014','TPL_VARS_HINT','ENG','Available variables: {memberName} {tenantName} {actionUrl} {expiresAt} {inviterName}'),
('W014','TPL_VARS_HINT','JPN','使用可能な変数: {memberName} {tenantName} {actionUrl} {expiresAt} {inviterName}'),
('W014','PREVIEW','KOR','미리보기'),
('W014','PREVIEW','ENG','Preview'),
('W014','PREVIEW','JPN','プレビュー'),
('W014','TPL_SOURCE','KOR','상태'),
('W014','TPL_SOURCE','ENG','Source'),
('W014','TPL_SOURCE','JPN','状態'),
('W014','TPL_DEFAULT','KOR','기본 템플릿'),
('W014','TPL_DEFAULT','ENG','Default'),
('W014','TPL_DEFAULT','JPN','既定テンプレート'),
('W014','TPL_OVERRIDDEN','KOR','회사 설정'),
('W014','TPL_OVERRIDDEN','ENG','Customized'),
('W014','TPL_OVERRIDDEN','JPN','会社設定'),
('W014','TPL_REVERT','KOR','기본값으로 되돌리기'),
('W014','TPL_REVERT','ENG','Revert to default'),
('W014','TPL_REVERT','JPN','既定に戻す'),
('W999','MAIL_TEMPLATES','KOR','메일 템플릿'),
('W999','MAIL_TEMPLATES','ENG','Mail templates'),
('W999','MAIL_TEMPLATES','JPN','メールテンプレート');
