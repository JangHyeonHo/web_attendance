-- =========================================================
-- V87: 확인 모달 안내 문구 — 대상만 표시하지 않고 '무슨 일이 일어나는지'를 항상 안내(규칙화).
--  각 문구는 실제 동작과 일치 확인: 마감 반려→재신청 가능(reopen), 휴가 취소→CANCELED는
--  사용량 집계 제외(잔여 복구), 취소 신청→관리자 승인 후 확정, 비활성/정지→로그인 차단.
-- =========================================================
INSERT INTO language_master (window_id, lang_key, lang, lang_value) VALUES
-- M003 휴가(멤버)
('M003','CANCEL_CONFIRM','KOR','이 휴가 신청을 취소할까요?'),
('M003','CANCEL_CONFIRM','ENG','Cancel this leave request?'),
('M003','CANCEL_CONFIRM','JPN','この休暇申請を取り消しますか？'),
('M003','REQUEST_CANCEL_HINT','KOR','관리자가 승인하면 취소가 확정되고 사용량이 잔여로 복구됩니다.'),
('M003','REQUEST_CANCEL_HINT','ENG','The cancellation takes effect after an administrator approves it; the amount returns to your balance.'),
('M003','REQUEST_CANCEL_HINT','JPN','管理者の承認後に取消が確定し、使用分は残数に戻ります。'),
-- T001 멤버 관리
('T001','DISABLE_CONFIRM','KOR','이 멤버를 비활성화할까요? 로그인할 수 없게 됩니다.'),
('T001','DISABLE_CONFIRM','ENG','Disable this member? They will no longer be able to sign in.'),
('T001','DISABLE_CONFIRM','JPN','このメンバーを無効化しますか？ログインできなくなります。'),
-- T003 휴가 관리
('T003','REJECT_HINT','KOR','반려 사유는 신청자에게 표시됩니다.'),
('T003','REJECT_HINT','ENG','The reason will be shown to the requester.'),
('T003','REJECT_HINT','JPN','差戻し理由は申請者に表示されます。'),
('T003','ADMIN_CANCEL_HINT','KOR','취소하면 이 휴가는 무효 처리되고 사용량이 잔여로 복구됩니다.'),
('T003','ADMIN_CANCEL_HINT','ENG','Cancelling voids this leave and returns the amount to the member''s balance.'),
('T003','ADMIN_CANCEL_HINT','JPN','取消するとこの休暇は無効となり、使用分は残数に戻ります。'),
-- T004 근태 마감 관리
('T004','REJECT_HINT','KOR','반려하면 멤버가 다시 마감을 신청할 수 있습니다.'),
('T004','REJECT_HINT','ENG','If rejected, the member can request closing again.'),
('T004','REJECT_HINT','JPN','差戻し後、メンバーは再度締めを申請できます。'),
-- A001 테넌트 목록(정지 확인)
('A001','SUSPEND_CONFIRM','KOR','이 테넌트를 정지할까요? 소속 사용자들이 로그인할 수 없게 됩니다.'),
('A001','SUSPEND_CONFIRM','ENG','Suspend this tenant? Its users will no longer be able to sign in.'),
('A001','SUSPEND_CONFIRM','JPN','このテナントを停止しますか？所属ユーザーはログインできなくなります。');
