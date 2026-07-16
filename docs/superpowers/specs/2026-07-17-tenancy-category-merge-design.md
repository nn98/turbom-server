# 동일 가게 다중업종 신고 병합 설계

**Date:** 2026-07-17
**Status:** Approved

---

## 문제

인허가 원본(`licensed_business_record`)은 업종(category/subCategory)마다 별도 row로 존재한다.
같은 가게가 여러 업종 허가를 동시에 또는 순차로 보유하면, 지금은 각 row가 그대로 별개의
`Tenancy`가 되어 [[2026-07-16-unit-dedup-design]]에서 정리한 Unit(물리적 위치) 병합을 거친 뒤에도
**같은 물건 안에서 같은 상호가 여러 번 나타난다.**

예: 카페 A가 "휴게음식점업" 허가(2020-01~2022-03)와 "즉석판매제조가공업" 허가(2022-03~현재)를
같은 자리에서 순차로 보유 — 실제로는 계속 영업 중인데 타임라인에는 "폐업 후 재개업"으로 보이고,
`UnitStatistics`의 `closedCount`/`averageSurvivalMonths`가 실제 회전율보다 부풀려진다.

이 문서는 `LicensedBusinessRecordEntity`에 사업자등록번호 같은 업종-간 공통 식별자가 없다는
전제 위에서, businessName 정확 일치와 인허가일자 gap만으로 병합 여부를 판단하는 범위를 다룬다.

---

## 범위

| 항목 | 포함 여부 |
|---|---|
| 같은 Unit 내 같은 businessName 레코드의 재직기간 병합 | ✅ |
| gap 임계값(90일) 이내 병합, 초과 시 별개 재직 유지 | ✅ |
| 상호명 변경(리브랜딩) 감지 | ❌ 신호 없음, 별도 이슈 |
| API 응답 스키마 변경 | ❌ (Tenancy 필드 형태 유지) |

---

## 핵심 개념

**병합 단위는 "Unit 내 businessName"** — [[2026-07-16-unit-dedup-design]]의 `unitKey()`로 이미
물리적 위치가 확정된 그룹 안에서, businessName이 정확히 같은 raw record를 추가로 묶는다.
지번+상호명 정확 일치 원칙(CLAUDE.md §8, 상가API 조인 규칙)과 동일한 기준을 재사용한다.

**gap 임계값 90일** — 두 레코드의 간격(이전 레코드의 유효 종료일 → 다음 레코드의 licensedAt)이
겹치거나 90일 이내면 "업종 전환 중 행정 처리 지연"으로 보고 하나의 재직으로 병합한다. 90일을
초과하면 "같은 이름으로 재입점"으로 보고 별개 재직으로 유지한다. 90은 실측 근거가 아닌 초기값 —
`ponytail:` 주석으로 표시하고 실사례로 오판이 나오면 조정한다.

---

## 설계

### 병합 알고리즘 (`TenancyQueryService` 내 새 단계)

기존 파이프라인: `records → unitGroups(위치별) → toUnit → toTenancy(레코드 1:1)`

변경 파이프라인: `records → unitGroups(위치별) → businessName별 재그룹 → gap 병합 → toTenancy(병합그룹 1:1)`

```
for each UnitGroup:
    businessName으로 재그룹 (LinkedHashMap, 최초 등장 순서 유지)
    각 businessName 그룹 내부:
        licensedAt 오름차순 정렬
        순회하며 병합그룹 누적:
          - 병합그룹의 "유효 종료일" = 그룹 내 아무 레코드나 closedAt==null이면 무한대(영업중),
            아니면 그룹 내 max(closedAt)
          - 다음 레코드의 licensedAt이 (유효 종료일 + 90일) 이내면 같은 병합그룹에 흡수
          - 초과하면 새 병합그룹 시작
```

### 병합그룹 → Tenancy 변환

| 필드 | 계산 |
|---|---|
| `id` | 대표 레코드(아래 기준)의 id |
| `businessName` | 그룹 키와 동일 |
| `licensedAt` | 그룹 내 최소 licensedAt |
| `closedAt` | 그룹 내 하나라도 `closedAt==null`(영업중)이면 `null`, 전부 폐업이면 최대 `closedAt` |
| `category`/`subCategory`/`status` | **대표 레코드** 기준 — 영업중인 레코드가 있으면 그중 licensedAt이 가장 늦은 것, 없으면 closedAt이 가장 늦은 것 |

대표 레코드 선정은 "현재 이 가게를 가장 잘 설명하는 업종"을 보여주기 위함 — 여러 업종을
동시에 표시하지 않고 최신 상태 하나로 대표한다(리스트화는 API 계약 변경이 필요해 이번 스코프 밖).

### 통계 영향

`UnitStatistics.from(tenancies)`는 입력 리스트를 그대로 쓰므로, 병합된 Tenancy 리스트가 들어가면
`totalTenancyCount`/`closedCount`/`averageSurvivalMonths`가 자동으로 "실제 가게 회전 수" 기준이
된다 — `UnitStatistics` 자체는 변경 불필요.

---

## 변경 파일

| 파일 | 변경 내용 |
|---|---|
| `TenancyQueryService.java` | `toUnits()`/`toUnit()` 사이에 businessName+gap 병합 단계 추가 |
| `TenancyQueryServiceTest.java` | gap 이내 병합 / gap 초과 분리 / 업종만 다른 동시병존 병합 시나리오 테스트 |

---

## 테스트 시나리오

1. **gap 이내 업종 전환 병합**: businessName 동일, 카테고리 다른 두 레코드, 첫 레코드 closedAt과
   둘째 레코드 licensedAt 간격 90일 이내 → Tenancy 1개, `closedAt=null`(둘째가 영업중이므로)
2. **gap 초과 재입점 분리**: 같은 조건이나 간격 91일 이상 → Tenancy 2개 유지
3. **완전 겹침(동시 보유)**: 두 업종 허가가 기간이 겹침(둘 다 licensedAt 이후 계속 영업중) → Tenancy 1개
4. **다른 businessName은 병합 안 함**: 같은 Unit, 다른 상호명 → 기존과 동일하게 별도 Tenancy 유지 (회귀 확인)
5. **단일 레코드는 그대로**: 업종 하나뿐인 businessName → 병합 로직 통과해도 결과 동일 (회귀 확인)

---

## 프론트엔드 영향

API 응답 구조·필드 타입 변경 없음. 같은 Unit 안에 같은 businessName의 Tenancy 항목 수가
줄어들 수 있다는 점만 인지하면 됨(타임라인이 더 정확해지는 방향).
