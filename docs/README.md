# docs 인덱스

이 폴더의 문서 지도. 정본 관계: **코드·마이그레이션이 항상 정본**이고, 문서는 그 해설이다
(충돌하면 코드가 맞다). 문서를 추가하면 소속 폴더와 이 인덱스에 등록한다.

## 폴더 구조

| 폴더 | 성격 | 이런 걸 찾을 때 |
|---|---|---|
| [deployment/](deployment/README.md) | 서버 구축·운영 절차 | 서버를 올리고, 갱신하고, 백업하는 방법 |
| [research/](#research--조사의사결정-대기) | 조사·의사결정 대기 | 아직 정하지 않은 것들의 판단 재료 |
| [design/](#design--현행-설계-해설) | 현행 시스템 설계 해설 | 지금 코드가 왜 이렇게 동작하는가 |
| [plan/](#plan--기능별-계획설계-원문) | 기능별 계획·설계 원문 | 각 기능이 어떤 요구·결정으로 만들어졌는가 |
| [history/](#history--이력아카이브) | 이력·아카이브 | 과거 경위(현행과 다를 수 있음) |

## deployment/ — 구축·운영

[deployment/README.md](deployment/README.md)에서 시작 — 구축 순서(01~07)·운영(08~12)·
환경변수 레퍼런스·운영 서버 선택지 비교가 번호순으로 정리돼 있다.

## research/ — 조사·의사결정 대기

| 문서 | 내용 |
|---|---|
| [payment-providers.md](research/payment-providers.md) | 결제사 선택지(한/일 사업자별 경로, Stripe 가능 여부) |
| [reverse-geocoding.md](research/reverse-geocoding.md) | 좌표→장소명 무료 API 비교(place_info 완성용) |

운영 서버 형태 비교는 [deployment/production-options.md](deployment/production-options.md)
(구축 문서와 붙어 있는 것이 유용해 deployment에 둔다 — 성격은 의사결정 대기).

## design/ — 현행 설계 해설

| 문서 | 내용 |
|---|---|
| [billing-calculation.md](design/billing-calculation.md) | 과금(좌석) 계산 방식 — 현행 동작·케이스별 정리 |
| [export-and-extensibility.md](design/export-and-extensibility.md) | Excel·PDF 내보내기 설계와 테넌트별 커스텀 서식 확장 구조 |

## plan/ — 기능별 계획·설계 원문

멀티테넌시 전환 시기의 기능별 설계·크로스리뷰 문서 모음(스케줄·휴가·메일·보안·과금 필드 등).
구현이 끝난 것도 "왜 그렇게 정했나"의 근거 문서로 유지한다. 마스터 계획서는
[history/plan-saas-multitenancy.md](history/plan-saas-multitenancy.md).

## history/ — 이력·아카이브

| 문서 | 내용 |
|---|---|
| [patch-notes/](history/patch-notes/) | 시기별 패치 내역·의사결정 로그(D번호) |
| [plan-saas-multitenancy.md](history/plan-saas-multitenancy.md) | SaaS 전환 마스터 계획서(전환 완료) |
| [migration-v1-to-v2.md](history/migration-v1-to-v2.md) | v1→v2 재설계 전수 추적 |
| [frontend-review.md](history/frontend-review.md) | v2.0.0 시점 프론트 점검 기록(방향 변경 전) |

## 다른 곳에 있는 정본

- 개발 규약·구조: 루트 [CLAUDE.md](../CLAUDE.md)(백엔드) / [frontend/CLAUDE.md](../frontend/CLAUDE.md)
- 프론트 부품 카탈로그: [frontend/src/components/README.md](../frontend/src/components/README.md)
- 화면 코드 정본: 백엔드 `Screen` enum / 라벨 정본: DB `language_master`(Flyway)
