# 내보내기(Export)와 확장 구조

근태/청구서의 Excel·PDF 내보내기 설계와, **회사(테넌트)별 커스텀 서식**을 코어 수정 없이 얹는 방법.

## 1. 선택한 라이브러리 — "가장 안전한" 기준

| 용도 | 방식 | 라이선스 | 비고 |
|------|------|----------|------|
| Excel(.xlsx) | **Apache POI** (`poi-ooxml`) | Apache-2.0 | 상용 SaaS에 안전한 퍼미시브 라이선스 |
| PDF | **브라우저 인쇄**(`window.print()` + `@media print`) | 없음(0 의존성) | 가장 안전. "인쇄→PDF 저장" 요구에 부합, HTML이라 커스텀 용이 |

> ⚠️ 회피: **iText 7 (AGPL)** — 폐쇄형 상용 서비스는 유료 라이선스가 강제됨. 그래서 PDF는 라이브러리 없이 브라우저 인쇄로 처리.

## 2. Excel 내보내기 — 확장점(SPI)

핵심 원칙: **유지보수 부담은 "테넌트 수"가 아니라 "확장점 수"에 비례**. 코어는 테넌트 정체성으로 분기하지 않고, exporter를 key로 선택한다.

```
AttendanceExcelExporter (interface, SPI)   ← 코어가 유지하는 유일한 계약
  ├─ DefaultAttendanceExcelExporter  key="default"   (표준 서식)
  └─ <회사>AttendanceExcelExporter    key="acme"      (커스텀 — 새 @Component만 추가)
AttendanceExporters (resolver)  — key로 수집, keyForTenant(tenantId)로 선택
```

- `GET /api/v1/attendance/monthly/export?year=&month=` → 그 테넌트의 exporter가 만든 .xlsx 스트림.
- 파일: `src/main/java/com/attendance/pro/attendance/export/`.

### 커스텀 서식 추가 방법(코어 수정 없음)
1. `AttendanceExcelExporter`를 구현한 `@Component` 작성 — `key()`는 고유값(예: 회사코드), `toXlsx(...)`에 그 회사 서식.
2. `AttendanceExporters.keyForTenant(tenantId)`가 그 테넌트에 해당 key를 돌려주도록 매핑(향후 테넌트 설정/entitlement에서 조회).
3. 끝. 코어(컨트롤러·기존 exporter)는 그대로. Spring이 자동 주입·등록.

> 커스텀 서식이 특정 회사 비용으로 개발된 자산이면, 그 exporter 모듈을 **별도 JAR/모듈로 물리 분리**해 그 회사 배포에만 탑재한다(소유 경계 = 모듈 경계). 코어는 SPI만 알면 되므로 섞이지 않는다.

## 3. PDF 인쇄 — HTML 템플릿 확장

- 인쇄 대상은 화면의 `.printable` 영역. 전역 `@media print`가 이 서브트리만 인쇄하고 앱 크롬(`.header`/`.sidebar`/`.toolbar-actions`/`.no-print`)은 숨긴다. `.print-only`는 인쇄 시에만 나오는 머리말(회사·문서명·기간).
- 회사별 인쇄 서식 커스텀은 **인쇄용 HTML/CSS 템플릿 교체**로 확장(서버 PDF 엔진 불필요). 향후 테넌트별 인쇄 레이아웃을 entitlement로 선택하도록 확장 가능.

## 4. 향후 — 플랜/전용 기능 아키텍처(설계 메모)

내보내기 exporter와 동일한 **확장점(SPI) + entitlement** 원칙을 제품 전반에 적용한다.

- **플랜**: `무료(≤5인)` → `기본(6인+ 표준기능)` → `엔터프라이즈(전용 개발)`. `tenant_billing.plan`(현재 `BASIC`)을 `FREE/BASIC/ENTERPRISE`로 확장.
- **전용 기능**: 코어에 `if(테넌트==A)` 금지. 도메인 이벤트/SPI에만 꽂히는 **별도 모듈(플러그인)** 로 구현, `tenant_feature`(테넌트별 on/off) 조회로 활성화. 코어는 SPI 안정성만 유지 → 전용 기능이 N개 늘어도 코어 파일은 안 늘어난다.
- **소유권**: 전용 모듈은 그 회사 자산 → 물리 분리·그 회사 배포에만 탑재(타사 재판매 없음).

이 문서는 청사진이며, 플랜 가격/전용 기능 범위는 운영 정책으로 확정 후 착수한다.
