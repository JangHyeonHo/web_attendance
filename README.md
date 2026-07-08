# web_attendance — 웹 출결 시스템

Spring Boot + MyBatis + Oracle 기반의 웹 출결(근태) 관리 시스템 백엔드.

프론트엔드(SPA, 별도 저장소/포트 3000 가정)가 **`/api` 단일 엔드포인트**로
"어떤 화면을 띄울지(GET)"와 "화면의 프로세스 실행(POST)"을 요청하고,
백엔드가 화면 아이디(`W000`~)와 다국어 텍스트, 처리 결과를 돌려주는 구조다.

## 주요 기능

- **로그인/세션 관리** — 이메일 + 비밀번호(SHA-512 해시) 로그인, 세션 스코프 빈(`UserSessionInfo`)으로 로그인 상태 유지, 관리자(`USER_RANK = -1`) 구분
- **출결 처리** — 출근(1)/퇴근(2)/조퇴(3)/휴식(4) 스탬프 등록
  - *체크(action=1) → 확정(action=2)* 2단계 처리: 체크 시점 요청 데이터의 SHA-512 해시를 DB에 기록해 두고, 확정 시점에 다시 해시를 비교해 **중간 변조를 탐지**
  - 상태 검증: 출근 전 퇴근 불가, 중복 스탬프 덮어쓰기 확인, 휴식 중 재출근 불가 등 (확인 코드 `err_cd` 1~8)
  - 위치정보(위도/경도/장소), 단말 정보 기록
- **출결 상세 조회(action=3)** — 월별 스케쥴(`SCHEDULE` + 휴일)과 출결 스탬프를 대조해 일자별 출근/퇴근 시각 생성(자정 넘긴 퇴근은 2400을 더한 HHmm으로 표현, 48시간 초과는 미퇴근 처리)
- **다국어(i18n)** — 언어 마스터 테이블(`LANGAUGE_MST`)을 기동시 메모리에 적재, 화면 아이디+로케일별 텍스트 제공 (한국어/영어, 일본어는 한국어로 대체)
- **관리자 도구** (`/lang_mst`, `/admin_settings`, `/data_settings`, `/data_saving`)
  - 언어 마스터 등록 화면(Thymeleaf)
  - 자바 코드에 정의한 테이블 스키마(`DaoManagement`)로 **DDL 자동 생성/컬럼 변경 감지**, 초기 데이터 투입, TSV 백업
- **로직 서비스 로그** — 체크/에러 이력을 DB에 기록, 일주일 지난 성공 데이터는 기동시 자동 삭제(배치 대용)

## 화면 아이디

| ID | 화면 | 로그인 필요 |
|------|------------|:---:|
| W000 | 인덱스(홈) | |
| W001 | 로그인 | |
| W002 | 로그아웃 | |
| W003 | 회원가입(미완성) | |
| W004 | 관리자 | (테스트중, 예정) |
| W005 | 출결 | ✔ |
| W006 | 출결 상세 | ✔ |
| W999 | 공통(헤더) | |

## API 계약

### `GET /api?win_id={화면ID}&lang={KOR|ENG}`
화면 전개. 응답:

```json
{
  "window": "W005",            // 실제 표시할 화면 ID (로그인 여부에 따라 보정됨)
  "windows": { "...": "..." }, // 해당 화면의 다국어 텍스트
  "headers": { "...": "..." }, // 공통(W999) 텍스트
  "user_name": "홍길동",
  "datas": { "att_sts": "출근 중", "att_time": "07/08 09:00:00", "att_msg": "" } // W005일 때
}
```

### `POST /api` (JSON)
프로세스 처리. 공통 요청 필드: `win_id`, `action`. 공통 응답 필드: `res`(`S`/`E`), `msg`(메시지 배열).

| 화면 | action | 내용 |
|------|--------|------|
| W001 | - | 로그인 (`user_email`, `user_pwd`) → 성공시 `window`가 W004/W005로 전환 |
| W005 | 1 | 출결 체크 (`attendance_type`, 위치정보 등) → `result`(변조 확인 키), `err_cd` |
| W005 | 2 | 출결 확정 (체크시 받은 `result` 포함 동일 데이터) |
| W006 | 3 | 월별 출결 상세 (`years`, `months`) → `schedule` 배열 |

## 아키텍처

```
src/main/java/com/attendance/pro/
├── WebAttendanceApplication.java   # 기동 클래스
├── config/
│   ├── InitializeConfig.java       # 기동시 언어 마스터 적재 + 로그 정리
│   ├── UserSessionInfo.java        # 세션 스코프 로그인 정보
│   └── WebConfig.java              # 개발용 CORS (기본 비활성)
├── controller/
│   ├── RootController.java         # /api GET(화면 전개) / POST(프로세스)
│   ├── AdminController.java        # 관리자/초기 셋업 화면
│   └── WindowManagement.java       # 화면 ID 정의 + 다국어 텍스트 보관(컨트롤러 공통 부모)
├── service/
│   ├── ProcService.java            # 프로세스 서비스 계약(인터페이스)
│   ├── BaseService.java            # 공통 기능(형변환, 변조 체크, 에러 기록)
│   ├── UserManagementService.java  # 로그인
│   └── AttendanceService.java      # 출결 체크/확정/상세
├── dao/                            # MyBatis 매퍼 인터페이스 (+ DaoManagement: 테이블 정의)
├── dto/                            # 테이블 대응 DTO
├── response/                       # 응답 전용 객체
└── common/                         # 공통 유틸(CodeMap, Redirector) + 관리자 DDL 로직

src/main/resources/
├── application.properties
├── mapper/*.xml                    # MyBatis SQL
├── templates/                      # 관리자용 Thymeleaf 화면
└── static/                         # 관리자 화면용 정적 리소스(sb-admin-2)
```

## DB 테이블 (Oracle)

| 테이블 | 용도 |
|--------|------|
| `USER_MST` | 회원 마스터 (이메일 PK, SHA-512 비밀번호, 부서, 등급) |
| `LANGAUGE_MST` | 언어 마스터 (화면ID + 키 + 언어 → 텍스트) ※ 테이블명 오타는 운영 DB 호환을 위해 유지 |
| `ATTENDANCE` | 출결 스탬프 (일자+SEQ, 타입, 상태, 위치, 단말) |
| `SCHEDULE` 계열 | 유저별 근무 스케쥴 + 휴일 |
| 로직 서비스 테이블 | 체크/에러 이력 (변조 확인용 해시 보관) |

공통 컬럼: `REGIST_USER/DATE`, `UPDATE_USER/DATE/CNT`, `DEL_FLG`

## 실행 방법

요구사항: **JDK 21+, Oracle DB**

```bash
export DB_URL="jdbc:oracle:thin:@localhost:1521/xe"
export DB_USERNAME="..."
export DB_PASSWORD="..."

./mvnw spring-boot:run    # http://localhost:9080
```

DB 접속 정보는 저장소에 커밋하지 않는다. 미지정시 로컬 기본값(`localhost:1521/xe`, admin/admin)을 사용한다.

최초 셋업: DB 계정 생성 후 `GET /admin_settings`(테이블 생성) → `GET /data_settings`(초기 데이터 투입) 순으로 호출.

## 2026-07 현대화 작업 내역 (v1.1.0)

5년 전(2021년) 작성된 코드를 다음과 같이 업그레이드했다.

**플랫폼 버전업**
- Spring Boot `2.5.2` → `3.5.6`, Java `8` → `21`, `javax.*` → `jakarta.*`
- MyBatis Spring Boot Starter `2.2.0` → `3.0.5`, `ojdbc8` → `ojdbc11`
- 미사용 의존성 제거: `mail`, `web-services`, `webflux`, `reactor-test`, `spring-session-core`

**보안/설정**
- MyBatis 매퍼 XML을 `static/`(웹에 그대로 노출되는 공개 폴더)에서 `resources/mapper/`로 이동, 커스텀 `MyBatisConfig` 삭제 후 스타터 자동 설정 사용
- `application.properties`에 하드코딩되어 있던 AWS RDS 접속 정보(호스트/계정/비밀번호)를 환경변수로 외부화 — **커밋 이력에 남아있으므로 해당 DB 계정은 반드시 폐기/변경할 것**
- 로그인 처리시 비밀번호 해시를 로그에 출력하던 코드 제거

**명명규칙**
- `InititalizeConfig` → `InitializeConfig` (오타)
- 인터페이스인데 `~Impl`로 명명됐던 `BaseServiceImpl` → `ProcService`
- `other` 패키지 → `common`
- 화면 ID 상수를 `public static String Login` → `public static final String LOGIN` 형태의 표준 상수로
- `isEqualMultyisOne` → `isAnyEqual`, `CodeMap.Korean/English` → `KOREAN/ENGLISH`
- 테스트 클래스를 잘못된 패키지(`...pro.starter`)에서 기동 클래스와 같은 패키지로 이동

**코드 현대화**
- 필드 주입(`@Autowired`/`@Resource`) → 생성자 주입, `@Controller("/")` 오용 수정
- 타입별로 10벌 중복돼 있던 `isEqual` 오버로드를 `Objects.equals` 기반 하나로 통합
- `SimpleDateFormat("YYYYMMdd")`(주차 연도 — 연말연초에 잘못된 연도가 나오는 버그) → `yyyyMMdd` 수정
- `e.printStackTrace()` → 로거, 문자열 `+=` 루프 → `StringBuilder`, 다이아몬드 연산자, `HexFormat`, 패턴매칭 `instanceof`, 화살표 `switch` 적용
- 죽은 코드/주석 처리된 블록 제거, 주요 클래스·메소드 Javadoc 보강

**동작 호환성**: API 응답 형식(JSON 키/값)과 SQL은 변경하지 않았다. 기존 프론트엔드/DB 스키마와 그대로 호환된다.

## 남은 과제 (TODO)

- [ ] 회원가입(W003) 프로세스 미구현
- [ ] 관리자 화면(W004) 로그인 보호 활성화 (`WindowManagement.createConfirmPath`의 test용 false)
- [ ] 비밀번호 해시를 솔트 없는 SHA-512 → bcrypt/argon2로 이관 (사용자 재설정 플로우 필요)
- [ ] `/admin_settings` 등 셋업 엔드포인트에 인증 추가 (현재 무인증 — 운영 배포 전 필수)
- [ ] 출결 상세의 `java.util.Date`/`Calendar` 로직을 `java.time`으로 이관 + 단위 테스트 작성
- [ ] `LANGAUGE_MST` 테이블명 오타 정리(마이그레이션 필요)
