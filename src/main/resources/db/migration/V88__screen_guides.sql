-- =========================================================
-- V88: 관리자 화면 상단 가이드(SCREEN_GUIDE) — '이 화면이 무엇을 하는 화면인지'를
--  비개발자 눈높이로 안내하는 한 단락을 전 관리자 화면(T/A)에 통일 키로 도입.
--  각 문구는 실제 화면 기능과 대조해 작성(멤버 등록=초대 메일, 마감 승인=월 잠금·해제 가능,
--  국가 공휴일 동기화=회사 지정 유지, 테넌트 생성=초대+공휴일 동기화, 카드 원본 미저장 등).
--  개행은 CSS 자동 줄바꿈에 맡기지 않고 절(구문) 단위로 직접 지정 —
--  한 줄이 한 호흡으로 읽히게. 렌더는 .screen-guide의 white-space: pre-line.
--  기존의 화면 설명용 개별 키(CLOSE_ADMIN_SUB·COMPANY_INFO_NOTE·COMPANY_SETTINGS_NOTE)는
--  SCREEN_GUIDE로 대체하고 삭제한다. (T006 NOTE는 요금 계산 규칙 안내로 존치)
-- =========================================================
INSERT INTO language_master (window_id, lang_key, lang, lang_value) VALUES
-- T001 멤버
('T001','SCREEN_GUIDE','KOR','회사 멤버를 등록하고 관리하는 화면입니다.
멤버를 등록하면 본인이 비밀번호를 설정할 수 있는 초대 메일이 발송되고,
행의 아이콘으로 정보 수정·비활성화와 근무 스케줄 편집을 할 수 있습니다.
이름·이메일·부서 검색 외에
특정 날짜·시각에 근무 중인 멤버를 찾는 것도 가능합니다.'),
('T001','SCREEN_GUIDE','ENG','Register and manage your company members.
Registering a member sends an invitation email so they can set their own password,
and the icons on each row let you edit details, deactivate, or adjust work schedules.
You can search by name, email, or department,
or find who is on duty at a specific date and time.'),
('T001','SCREEN_GUIDE','JPN','会社のメンバーを登録・管理する画面です。
メンバーを登録すると本人がパスワードを設定できる招待メールが送られ、
行のアイコンから情報の修正・無効化や勤務スケジュールの編集ができます。
氏名・メール・部署での検索のほか、
特定の日時に勤務中のメンバーを探すこともできます。'),
-- T002 공휴일
('T002','SCREEN_GUIDE','KOR','회사가 쉬는 날을 연도별로 관리하는 화면입니다.
국가 공휴일을 최신 데이터로 동기화할 수 있고
(회사 지정 공휴일은 그대로 유지됩니다),
창립기념일처럼 회사만의 휴일을 매년 반복 여부와 함께 등록할 수 있습니다.'),
('T002','SCREEN_GUIDE','ENG','Manage your company''s days off by year.
You can sync national holidays with the latest data
(company-designated holidays are kept),
and add your own holidays such as a founding anniversary,
optionally repeating every year.'),
('T002','SCREEN_GUIDE','JPN','会社の休日を年度ごとに管理する画面です。
国の祝日を最新データに同期でき（会社指定の休日は維持されます）、
創立記念日のような会社独自の休日を毎年繰り返しの設定とともに登録できます。'),
-- T003 휴가 관리
('T003','SCREEN_GUIDE','KOR','멤버들의 휴가를 한곳에서 관리하는 화면입니다.
결재 탭에서 휴가 신청과 취소 신청을 승인·반려하고,
멤버 잔여 탭에서 부여·사용·잔여 확인과 수동·일괄 부여를 할 수 있으며,
휴가 종류 탭에서 회사에서 사용하는 휴가 항목을 관리합니다.'),
('T003','SCREEN_GUIDE','ENG','Manage member leave in one place.
Approve or reject leave and cancellation requests on the Approvals tab,
check granted/used/remaining amounts and grant leave manually or in bulk
on the Balances tab,
and manage your company''s leave types on the Types tab.'),
('T003','SCREEN_GUIDE','JPN','メンバーの休暇をまとめて管理する画面です。
決裁タブで休暇申請・取消申請を承認／差し戻しし、
残数タブで付与・使用・残数の確認や手動／一括付与ができ、
休暇種類タブで会社で使う休暇項目を管理します。'),
-- T004 근태 마감(기존 CLOSE_ADMIN_SUB 대체·상세화)
('T004','SCREEN_GUIDE','KOR','멤버가 신청한 월 근태 마감을 결재하는 화면입니다.
승인하면 그 달의 출결 기록이 잠겨 더 이상 정정할 수 없게 되고,
잘못 승인한 경우 마감 해제로 결재 전 상태로 되돌릴 수 있습니다.
승인된 월은 아래에서 급여 정산(참고)도 확인할 수 있습니다.'),
('T004','SCREEN_GUIDE','ENG','Review monthly attendance close requests from members.
Approving locks that month''s records against further correction;
if approved by mistake, you can reopen to return it to the pre-approval state.
For approved months you can also check the payroll summary (for reference) below.'),
('T004','SCREEN_GUIDE','JPN','メンバーが申請した月次勤怠締めを決裁する画面です。
承認するとその月の勤怠記録はロックされ訂正できなくなり、
誤って承認した場合は締め解除で決裁前の状態に戻せます。
承認済みの月は下部で給与精算（参考）も確認できます。'),
-- T005 메일 템플릿(테넌트)
('T005','SCREEN_GUIDE','KOR','초대·비밀번호 안내처럼 시스템이 자동으로 보내는
메일의 문구를 회사에 맞게 바꾸는 화면입니다.
수정하지 않은 템플릿은 운영사의 기본 문구가 그대로 사용됩니다.'),
('T005','SCREEN_GUIDE','ENG','Customize the wording of automatic system emails,
such as invitations and password notices.
Templates you have not modified use the operator''s default wording.'),
('T005','SCREEN_GUIDE','JPN','招待やパスワード案内など、
システムが自動送信するメールの文面を会社に合わせて変更する画面です。
修正していないテンプレートは運営会社の既定の文面がそのまま使われます。'),
-- T006 청구서
('T006','SCREEN_GUIDE','KOR','회사의 월 이용 요금을 확인하고 청구서를 문서로 열람·인쇄하는 화면입니다.
금액이 계산되는 방식은 아래 안내를 참고하세요.'),
('T006','SCREEN_GUIDE','ENG','Check your company''s monthly service charges and view or print invoices.
See the note below for how amounts are calculated.'),
('T006','SCREEN_GUIDE','JPN','会社の月額利用料金を確認し、請求書を文書として閲覧・印刷する画面です。
金額の計算方法は下の案内をご覧ください。'),
-- T007 회사 정보(기존 COMPANY_INFO_NOTE 대체·상세화)
('T007','SCREEN_GUIDE','KOR','사업자 정보·주소와 결제 수단 등 회사의 기본 정보를 관리하는 화면입니다.
요금제·단가 같은 계약 조건은 운영사와의 계약값이라
여기서는 확인만 할 수 있습니다.'),
('T007','SCREEN_GUIDE','ENG','Manage your company''s basic information
such as business details, address, and payment method.
Contract terms like plan and unit price are set with the operator
and are view-only here.'),
('T007','SCREEN_GUIDE','JPN','事業者情報・住所や決済手段など会社の基本情報を管理する画面です。
プランや単価などの契約条件は運営会社との契約値のため、
ここでは確認のみ可能です。'),
-- T008 회사 설정(기존 COMPANY_SETTINGS_NOTE 대체·상세화)
('T008','SCREEN_GUIDE','KOR','회사 공통의 운영 기준을 정하는 화면입니다.
신규 멤버에게 자동 적용되는 기본 근무 스케줄,
근태 보고서의 결재란 표시,
급여 정산(참고)에 쓰는 가산 반영 여부를 설정합니다.'),
('T008','SCREEN_GUIDE','ENG','Set company-wide operating rules:
the default work schedule applied to new members,
the approval stamp box on attendance reports,
and whether statutory premiums are reflected in the payroll reference.'),
('T008','SCREEN_GUIDE','JPN','会社共通の運用基準を定める画面です。
新規メンバーに自動適用される基本勤務スケジュール、
勤怠レポートの決裁欄表示、
給与精算（参考）に使う割増の反映有無を設定します。'),
-- A001 테넌트
('A001','SCREEN_GUIDE','KOR','서비스를 이용하는 고객사를 관리하는 화면입니다.
고객사를 생성하면 관리자 초대 메일 발송과
국가 공휴일 동기화가 자동으로 진행되고,
각 행에서 상세(기업·결제 정보) 확인,
초대 재발송, 이용 정지·재개를 할 수 있습니다.'),
('A001','SCREEN_GUIDE','ENG','Manage the customer companies using the service.
Creating one automatically sends an admin invitation email
and syncs national holidays;
from each row you can open details (business/billing info),
resend the invitation, or suspend/resume the account.'),
('A001','SCREEN_GUIDE','JPN','サービスを利用する顧客企業を管理する画面です。
作成すると管理者招待メールの送信と祝日の同期が自動で行われ、
各行から詳細（企業・決済情報）の確認、
招待の再送、利用停止／再開ができます。'),
-- A002 테넌트 상세(임베드 패널)
('A002','SCREEN_GUIDE','KOR','선택한 고객사의 기업 정보와 결제 정보를 확인·수정하는 패널입니다.
카드 정보 원본은 저장되지 않으며 결제용 빌링키만 등록합니다.'),
('A002','SCREEN_GUIDE','ENG','View and edit the selected company''s business and billing information.
Raw card details are never stored;
only the billing key used for payment is registered.'),
('A002','SCREEN_GUIDE','JPN','選択した顧客企業の企業情報と決済情報を確認・修正するパネルです。
カード情報の原本は保存されず、決済用のビリングキーのみ登録します。'),
-- A003 감사 로그
('A003','SCREEN_GUIDE','KOR','시스템에서 일어난 주요 작업 기록을 시간순으로 확인하는 화면입니다.
누가 언제 로그인했는지, 어떤 관리 작업이 있었는지, 오류가 있었는지를
필터로 추려 볼 수 있습니다(최근 200건).'),
('A003','SCREEN_GUIDE','ENG','Review a chronological record of key system activity.
Filter to see who signed in and when,
what administrative actions occurred, and any errors (latest 200 entries).'),
('A003','SCREEN_GUIDE','JPN','システムで行われた主要な操作記録を時系列で確認する画面です。
誰がいつログインしたか、どんな管理操作があったか、
エラーの有無をフィルターで絞り込めます（直近200件）。'),
-- A004 글로벌 메일 템플릿
('A004','SCREEN_GUIDE','KOR','모든 고객사에 공통으로 적용되는 메일 기본 템플릿을 관리하는 화면입니다.
고객사가 자체 수정한 템플릿이 있으면
그 고객사에는 수정본이 우선 적용됩니다.'),
('A004','SCREEN_GUIDE','ENG','Manage the default email templates shared by all customer companies.
If a company has customized a template,
its own version takes precedence for that company.'),
('A004','SCREEN_GUIDE','JPN','すべての顧客企業に共通で適用されるメールの既定テンプレートを管理する画面です。
顧客企業が独自に修正したテンプレートがある場合、
その企業には修正版が優先されます。'),
-- A005 운영 설정(테마·언어 마스터)
('A005','SCREEN_GUIDE','KOR','운영사 공통 설정 화면입니다.
전 화면에 적용되는 계절 테마를 고르고,
화면에 표시되는 문구(한국어·영어·일본어)를
화면 ID와 텍스트 키 단위로 관리합니다.'),
('A005','SCREEN_GUIDE','ENG','Operator-wide settings.
Choose the seasonal theme applied across all screens,
and manage on-screen text (Korean, English, Japanese)
by screen ID and text key.'),
('A005','SCREEN_GUIDE','JPN','運営会社共通の設定画面です。
全画面に適用される季節テーマを選び、
画面に表示される文言（韓国語・英語・日本語）を
画面IDとテキストキー単位で管理します。');

-- A005 메뉴·화면 타이틀: '관리자' → '운영 설정' (화면 타이틀 신설과 함께 메뉴 라벨도 일치)
UPDATE language_master SET lang_value = '운영 설정' WHERE window_id = 'W999' AND lang_key = 'ADMIN' AND lang = 'KOR';
UPDATE language_master SET lang_value = 'Operations Settings' WHERE window_id = 'W999' AND lang_key = 'ADMIN' AND lang = 'ENG';
UPDATE language_master SET lang_value = '運営設定' WHERE window_id = 'W999' AND lang_key = 'ADMIN' AND lang = 'JPN';

-- SCREEN_GUIDE로 대체된 기존 화면 설명 키 제거
DELETE FROM language_master WHERE window_id = 'T004' AND lang_key = 'CLOSE_ADMIN_SUB';
DELETE FROM language_master WHERE window_id = 'T007' AND lang_key = 'COMPANY_INFO_NOTE';
DELETE FROM language_master WHERE window_id = 'W999' AND lang_key = 'COMPANY_SETTINGS_NOTE';
