# docs 인덱스

이 폴더의 문서 지도. 정본 관계: **코드·마이그레이션이 항상 정본**이고, 문서는 그 해설이다
(충돌하면 코드가 맞다). 문서를 추가하면 이 인덱스에도 한 줄 등록한다.

## 구축·운영 (→ [deployment/](deployment/README.md))

서버 구축은 [deployment/README.md](deployment/README.md)에서 시작 — 구축 순서(01~07)와
운영 문서(08~12), 환경변수 레퍼런스, 운영 서버 선택지 비교가 번호순으로 정리돼 있다.

## 조사·의사결정 대기

| 문서 | 내용 | 상태 |
|---|---|---|
| [payment-provider-research.md](payment-provider-research.md) | 결제사 선택지(한/일, Stripe 가능 여부) 조사 | 의사결정 대기 |
| [reverse-geocoding-research.md](reverse-geocoding-research.md) | 좌표→장소명 무료 API 조사(place_info 완성용) | 의사결정 대기 |
| [deployment/production-options.md](deployment/production-options.md) | 운영 서버 구성 선택지·권장 로드맵 | 의사결정 대기 |

## 설계·해설 (현행 시스템)

| 문서 | 내용 |
|---|---|
| [billing-calculation.md](billing-calculation.md) | 과금(좌석) 계산 방식 — 현행 동작·케이스별 정리 |
| [export-and-extensibility.md](export-and-extensibility.md) | Excel·PDF 내보내기 설계와 테넌트별 커스텀 서식 확장 구조 |
| [plan-saas-multitenancy.md](plan-saas-multitenancy.md) | SaaS 멀티테넌시 전환 계획서(전환 완료 — 배경 이해용) |
| [plan/](plan/) | 기능별 설계·크로스리뷰 문서 모음(스케줄·휴가·메일·과금 필드 등) |

## 이력 (아카이브 — 현행과 다를 수 있음)

| 문서 | 내용 |
|---|---|
| [migration-v1-to-v2.md](migration-v1-to-v2.md) | v1→v2 재설계에서 각 구성요소가 어디로 갔는지 전수 추적 |
| [frontend-review.md](frontend-review.md) | v2.0.0 시점 프론트 점검 기록(방향 변경 전) |
| patch-notes-2026-07*.md | 시기별 패치 내역·의사결정 로그 |

## 다른 곳에 있는 정본

- 개발 규약·구조: 루트 [CLAUDE.md](../CLAUDE.md)(백엔드) / [frontend/CLAUDE.md](../frontend/CLAUDE.md)
- 프론트 부품 카탈로그: [frontend/src/components/README.md](../frontend/src/components/README.md)
- 화면 코드 정본: 백엔드 `Screen` enum / 라벨 정본: DB `language_master`(Flyway)
