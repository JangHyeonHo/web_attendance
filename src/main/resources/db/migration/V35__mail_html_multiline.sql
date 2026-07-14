-- =========================================================
-- V35: 기본 메일 템플릿을 '편집 가능한' 여러 줄 HTML로(#13)
--  - V33에서 한 줄 HTML로 넣었더니 관리자가 편집 불가(가독성 0)라는 피드백.
--  - 이제 들여쓰기/개행 있는 HTML로 저장. 렌더는 SmtpMailSender/미리보기가 HTML을 감지해
--    pre-wrap 없이 그대로 그리므로, 태그 사이 개행이 화면에 빈 줄로 새지 않는다.
--  - 회사 오버라이드(tenant_mail_template)는 건드리지 않음.
-- =========================================================

UPDATE mail_template SET body =
'<div style="max-width:480px;margin:0 auto;font-family:sans-serif;color:#1f2937">
  <div style="background:#0f766e;padding:20px 24px;border-radius:12px 12px 0 0">
    <span style="color:#ffffff;font-size:18px;font-weight:700">{tenantName}</span>
  </div>
  <div style="border:1px solid #e5e7eb;border-top:none;border-radius:0 0 12px 12px;padding:24px 24px 28px">
    <p style="font-size:16px;font-weight:600;margin:0 0 14px">{memberName}님, 환영합니다.</p>
    <p style="margin:0 0 22px;line-height:1.7;color:#374151">
      {inviterName}님이 회원님을 {tenantName} 출결 시스템에 초대했습니다.
      아래 버튼을 눌러 비밀번호를 설정하면 바로 이용할 수 있습니다.
    </p>
    <div style="text-align:center;margin:0 0 22px">
      <a href="{actionUrl}" style="display:inline-block;background:#0f766e;color:#ffffff;text-decoration:none;padding:13px 32px;border-radius:8px;font-weight:600;font-size:15px">비밀번호 설정하기</a>
    </div>
    <p style="margin:0;color:#6b7280;font-size:13px;line-height:1.6">
      이 링크는 {expiresAt}까지 유효합니다.<br>
      본인이 요청하지 않았다면 이 메일을 무시하셔도 됩니다.
    </p>
  </div>
</div>'
WHERE purpose = 'INVITE' AND lang = 'KOR';

UPDATE mail_template SET body =
'<div style="max-width:480px;margin:0 auto;font-family:sans-serif;color:#1f2937">
  <div style="background:#0f766e;padding:20px 24px;border-radius:12px 12px 0 0">
    <span style="color:#ffffff;font-size:18px;font-weight:700">{tenantName}</span>
  </div>
  <div style="border:1px solid #e5e7eb;border-top:none;border-radius:0 0 12px 12px;padding:24px 24px 28px">
    <p style="font-size:16px;font-weight:600;margin:0 0 14px">Welcome, {memberName}!</p>
    <p style="margin:0 0 22px;line-height:1.7;color:#374151">
      {inviterName} has invited you to the {tenantName} attendance system.
      Set your password with the button below to get started.
    </p>
    <div style="text-align:center;margin:0 0 22px">
      <a href="{actionUrl}" style="display:inline-block;background:#0f766e;color:#ffffff;text-decoration:none;padding:13px 32px;border-radius:8px;font-weight:600;font-size:15px">Set your password</a>
    </div>
    <p style="margin:0;color:#6b7280;font-size:13px;line-height:1.6">
      This link is valid until {expiresAt}.<br>
      If you did not expect this email, you can safely ignore it.
    </p>
  </div>
</div>'
WHERE purpose = 'INVITE' AND lang = 'ENG';

UPDATE mail_template SET body =
'<div style="max-width:480px;margin:0 auto;font-family:sans-serif;color:#1f2937">
  <div style="background:#0f766e;padding:20px 24px;border-radius:12px 12px 0 0">
    <span style="color:#ffffff;font-size:18px;font-weight:700">{tenantName}</span>
  </div>
  <div style="border:1px solid #e5e7eb;border-top:none;border-radius:0 0 12px 12px;padding:24px 24px 28px">
    <p style="font-size:16px;font-weight:600;margin:0 0 14px">{memberName}さん、ようこそ。</p>
    <p style="margin:0 0 22px;line-height:1.7;color:#374151">
      {inviterName}さんがあなたを{tenantName}の勤怠システムに招待しました。
      下のボタンからパスワードを設定してご利用ください。
    </p>
    <div style="text-align:center;margin:0 0 22px">
      <a href="{actionUrl}" style="display:inline-block;background:#0f766e;color:#ffffff;text-decoration:none;padding:13px 32px;border-radius:8px;font-weight:600;font-size:15px">パスワードを設定</a>
    </div>
    <p style="margin:0;color:#6b7280;font-size:13px;line-height:1.6">
      このリンクは{expiresAt}まで有効です。<br>
      心当たりがない場合は、このメールを無視してください。
    </p>
  </div>
</div>'
WHERE purpose = 'INVITE' AND lang = 'JPN';

UPDATE mail_template SET body =
'<div style="max-width:480px;margin:0 auto;font-family:sans-serif;color:#1f2937">
  <div style="background:#0f766e;padding:20px 24px;border-radius:12px 12px 0 0">
    <span style="color:#ffffff;font-size:18px;font-weight:700">{tenantName}</span>
  </div>
  <div style="border:1px solid #e5e7eb;border-top:none;border-radius:0 0 12px 12px;padding:24px 24px 28px">
    <p style="font-size:16px;font-weight:600;margin:0 0 14px">{memberName}님, 안녕하세요.</p>
    <p style="margin:0 0 22px;line-height:1.7;color:#374151">
      {tenantName} 계정의 비밀번호 재설정 요청을 접수했습니다.
      아래 버튼을 눌러 새 비밀번호를 설정하세요.
    </p>
    <div style="text-align:center;margin:0 0 22px">
      <a href="{actionUrl}" style="display:inline-block;background:#0f766e;color:#ffffff;text-decoration:none;padding:13px 32px;border-radius:8px;font-weight:600;font-size:15px">비밀번호 재설정</a>
    </div>
    <p style="margin:0;color:#6b7280;font-size:13px;line-height:1.6">
      이 링크는 {expiresAt}까지 유효합니다.<br>
      본인이 요청하지 않았다면 이 메일을 무시하셔도 됩니다.
    </p>
  </div>
</div>'
WHERE purpose = 'RESET' AND lang = 'KOR';

UPDATE mail_template SET body =
'<div style="max-width:480px;margin:0 auto;font-family:sans-serif;color:#1f2937">
  <div style="background:#0f766e;padding:20px 24px;border-radius:12px 12px 0 0">
    <span style="color:#ffffff;font-size:18px;font-weight:700">{tenantName}</span>
  </div>
  <div style="border:1px solid #e5e7eb;border-top:none;border-radius:0 0 12px 12px;padding:24px 24px 28px">
    <p style="font-size:16px;font-weight:600;margin:0 0 14px">Hello, {memberName}.</p>
    <p style="margin:0 0 22px;line-height:1.7;color:#374151">
      We received a request to reset the password for your {tenantName} account.
      Set a new password with the button below.
    </p>
    <div style="text-align:center;margin:0 0 22px">
      <a href="{actionUrl}" style="display:inline-block;background:#0f766e;color:#ffffff;text-decoration:none;padding:13px 32px;border-radius:8px;font-weight:600;font-size:15px">Reset password</a>
    </div>
    <p style="margin:0;color:#6b7280;font-size:13px;line-height:1.6">
      This link is valid until {expiresAt}.<br>
      If you did not request this, you can safely ignore this email.
    </p>
  </div>
</div>'
WHERE purpose = 'RESET' AND lang = 'ENG';

UPDATE mail_template SET body =
'<div style="max-width:480px;margin:0 auto;font-family:sans-serif;color:#1f2937">
  <div style="background:#0f766e;padding:20px 24px;border-radius:12px 12px 0 0">
    <span style="color:#ffffff;font-size:18px;font-weight:700">{tenantName}</span>
  </div>
  <div style="border:1px solid #e5e7eb;border-top:none;border-radius:0 0 12px 12px;padding:24px 24px 28px">
    <p style="font-size:16px;font-weight:600;margin:0 0 14px">{memberName}さん、こんにちは。</p>
    <p style="margin:0 0 22px;line-height:1.7;color:#374151">
      {tenantName}アカウントのパスワード再設定リクエストを受け付けました。
      下のボタンから新しいパスワードを設定してください。
    </p>
    <div style="text-align:center;margin:0 0 22px">
      <a href="{actionUrl}" style="display:inline-block;background:#0f766e;color:#ffffff;text-decoration:none;padding:13px 32px;border-radius:8px;font-weight:600;font-size:15px">パスワードを再設定</a>
    </div>
    <p style="margin:0;color:#6b7280;font-size:13px;line-height:1.6">
      このリンクは{expiresAt}まで有効です。<br>
      心当たりがない場合は、このメールを無視してください。
    </p>
  </div>
</div>'
WHERE purpose = 'RESET' AND lang = 'JPN';
