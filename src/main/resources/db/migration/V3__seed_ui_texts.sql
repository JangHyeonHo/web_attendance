-- =========================================================
-- V3: 화면 UI 텍스트 시드 (한/영/일)
-- 프론트엔드가 코드 내장 사전 없이 언어 마스터만으로 동작하기 위한 초기 데이터.
-- INSERT IGNORE: 관리자가 이미 등록/수정한 키는 덮어쓰지 않는다.
--
-- 화면 배치:
--   W999 공통(헤더/버튼 등 전 화면 공유) / W000 인덱스 / W003 회원가입
--   W004 관리자 / W005 출결 / W006 출결 상세
-- =========================================================

INSERT IGNORE INTO language_master (window_id, lang_key, lang, lang_value) VALUES
-- ---- W999 공통 ----
('W999','HOME','KOR','홈'),                     ('W999','HOME','ENG','Home'),                       ('W999','HOME','JPN','ホーム'),
('W999','LOGIN','KOR','로그인'),                ('W999','LOGIN','ENG','Login'),                     ('W999','LOGIN','JPN','ログイン'),
('W999','LOGOUT','KOR','로그아웃'),             ('W999','LOGOUT','ENG','Logout'),                   ('W999','LOGOUT','JPN','ログアウト'),
('W999','SIGNUP','KOR','회원가입'),             ('W999','SIGNUP','ENG','Sign up'),                  ('W999','SIGNUP','JPN','会員登録'),
('W999','ADMIN','KOR','관리자'),                ('W999','ADMIN','ENG','Admin'),                     ('W999','ADMIN','JPN','管理者'),
('W999','ATTEND','KOR','출근'),                 ('W999','ATTEND','ENG','Clock in'),                 ('W999','ATTEND','JPN','出勤'),
('W999','SUBMIT','KOR','등록'),                 ('W999','SUBMIT','ENG','Submit'),                   ('W999','SUBMIT','JPN','登録'),
('W999','CANCEL','KOR','취소'),                 ('W999','CANCEL','ENG','Cancel'),                   ('W999','CANCEL','JPN','キャンセル'),
('W999','LOADING','KOR','불러오는 중...'),      ('W999','LOADING','ENG','Loading...'),              ('W999','LOADING','JPN','読み込み中...'),
('W999','EMAIL','KOR','이메일'),                ('W999','EMAIL','ENG','Email'),                     ('W999','EMAIL','JPN','メールアドレス'),
('W999','PWD','KOR','비밀번호'),                ('W999','PWD','ENG','Password'),                    ('W999','PWD','JPN','パスワード'),
-- ---- W000 인덱스 ----
('W000','INDEX_TITLE','KOR','웹 출결 관리 시스템'), ('W000','INDEX_TITLE','ENG','Web Attendance System'), ('W000','INDEX_TITLE','JPN','Web勤怠管理システム'),
('W000','INDEX_SUB','KOR','Web Attendance v2.0'),  ('W000','INDEX_SUB','ENG','Web Attendance v2.0'),     ('W000','INDEX_SUB','JPN','Web Attendance v2.0'),
-- ---- W003 회원가입 ----
('W003','NAME','KOR','이름'),                   ('W003','NAME','ENG','Name'),                       ('W003','NAME','JPN','名前'),
('W003','DEPART','KOR','부서 코드(선택)'),      ('W003','DEPART','ENG','Department code (optional)'),('W003','DEPART','JPN','部署コード（任意）'),
('W003','SIGNUP_DONE','KOR','가입이 완료되었습니다. 로그인해 주세요.'), ('W003','SIGNUP_DONE','ENG','Registration completed. Please log in.'), ('W003','SIGNUP_DONE','JPN','登録が完了しました。ログインしてください。'),
-- ---- W004 관리자 ----
('W004','ADMIN_I18N_TITLE','KOR','언어 마스터 관리'), ('W004','ADMIN_I18N_TITLE','ENG','Language master'), ('W004','ADMIN_I18N_TITLE','JPN','言語マスタ管理'),
('W004','WINDOW_ID','KOR','화면 ID'),           ('W004','WINDOW_ID','ENG','Window ID'),             ('W004','WINDOW_ID','JPN','画面ID'),
('W004','LANG_KEY','KOR','텍스트 키'),          ('W004','LANG_KEY','ENG','Text key'),               ('W004','LANG_KEY','JPN','テキストキー'),
('W004','LANG','KOR','언어'),                   ('W004','LANG','ENG','Language'),                   ('W004','LANG','JPN','言語'),
('W004','LANG_VALUE','KOR','텍스트 값'),        ('W004','LANG_VALUE','ENG','Text value'),           ('W004','LANG_VALUE','JPN','テキスト値'),
-- ---- W005 출결 ----
('W005','OFFWORK','KOR','퇴근'),                ('W005','OFFWORK','ENG','Clock out'),               ('W005','OFFWORK','JPN','退勤'),
('W005','EARLY','KOR','조퇴'),                  ('W005','EARLY','ENG','Leave early'),               ('W005','EARLY','JPN','早退'),
('W005','BREAKTIME','KOR','휴식'),              ('W005','BREAKTIME','ENG','Break'),                 ('W005','BREAKTIME','JPN','休憩'),
('W005','ATTDETAILS','KOR','출결 조회'),        ('W005','ATTDETAILS','ENG','Attendance detail'),    ('W005','ATTDETAILS','JPN','勤怠照会'),
('W005','STATUS_PREFIX','KOR','현재 유저님의 상태는'), ('W005','STATUS_PREFIX','ENG','Your current status is'), ('W005','STATUS_PREFIX','JPN','現在のステータスは'),
('W005','STATUS_SUFFIX','KOR','입니다'),        ('W005','STATUS_SUFFIX','ENG',' '),                 ('W005','STATUS_SUFFIX','JPN','です'),
('W005','STAMPED_AT','KOR','등록 시각'),        ('W005','STAMPED_AT','ENG','Stamped at'),           ('W005','STAMPED_AT','JPN','打刻時刻'),
('W005','CURRENT_TIME','KOR','현재 시간'),      ('W005','CURRENT_TIME','ENG','Current time'),       ('W005','CURRENT_TIME','JPN','現在時刻'),
('W005','LATITUDE','KOR','위도'),               ('W005','LATITUDE','ENG','Latitude'),               ('W005','LATITUDE','JPN','緯度'),
('W005','LONGITUDE','KOR','경도'),              ('W005','LONGITUDE','ENG','Longitude'),             ('W005','LONGITUDE','JPN','経度'),
('W005','GEO_FAIL','KOR','위치 정보 취득에 실패했습니다.'), ('W005','GEO_FAIL','ENG','Failed to get your location.'), ('W005','GEO_FAIL','JPN','位置情報の取得に失敗しました。'),
('W005','CONFIRM_STAMP','KOR','등록하시겠습니까?'), ('W005','CONFIRM_STAMP','ENG','Register this stamp?'), ('W005','CONFIRM_STAMP','JPN','登録しますか？'),
('W005','RETRY','KOR','재요청'),                ('W005','RETRY','ENG','Retry'),                     ('W005','RETRY','JPN','再取得'),
-- ---- W006 출결 상세 ----
('W006','ATTDETAILS','KOR','출결 조회'),        ('W006','ATTDETAILS','ENG','Attendance detail'),    ('W006','ATTDETAILS','JPN','勤怠照会'),
('W006','YEAR','KOR','년'),                     ('W006','YEAR','ENG','Year'),                       ('W006','YEAR','JPN','年'),
('W006','MONTH','KOR','월'),                    ('W006','MONTH','ENG','Month'),                     ('W006','MONTH','JPN','月'),
('W006','DATES','KOR','날짜'),                  ('W006','DATES','ENG','Date'),                      ('W006','DATES','JPN','日付'),
('W006','USERSCHE','KOR','유저 스케쥴'),        ('W006','USERSCHE','ENG','Schedule'),               ('W006','USERSCHE','JPN','スケジュール'),
('W006','INPUTTIME','KOR','입력시간'),          ('W006','INPUTTIME','ENG','Stamped'),               ('W006','INPUTTIME','JPN','打刻時間'),
('W006','SCHE_IN','KOR','출근 시간'),           ('W006','SCHE_IN','ENG','Start'),                   ('W006','SCHE_IN','JPN','始業'),
('W006','SCHE_OUT','KOR','퇴근 시간'),          ('W006','SCHE_OUT','ENG','End'),                    ('W006','SCHE_OUT','JPN','終業'),
('W006','HOLIDAY','KOR','휴일'),                ('W006','HOLIDAY','ENG','Holiday'),                 ('W006','HOLIDAY','JPN','休日');
