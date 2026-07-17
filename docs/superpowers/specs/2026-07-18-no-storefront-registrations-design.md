# 무점포/자가신고형 업종 분리 설계

**Date:** 2026-07-18
**Status:** Approved

---

## 문제

`4113110800105560000-U2`(창곡동 556) 실사례: `단일(상세주소불명)` Unit 하나에 동물판매업 2건 + 통신판매업 2건이 동시에 "영업 중"으로 뜸. 원본 대조 결과 이건 파싱 버그도 샵인샵도 아니고, **원본 인허가 데이터 자체에 층/호 정보가 없어서** 생긴 현상임(`parse_method='NONE'` — 파싱 시도 실패가 아니라 애초에 추출 대상이 원본에 없었다는 뜻).

전체 91,772건을 (category, subCategory)별로 "층/호 정보 없음" 비율을 계산해보니 뚜렷한 이분 구조가 나옴:
- **90% 이상**: 통신판매업(99.5%), 방문판매업(98%), 전화권유판매업(99%), 의료기기판매(임대)업(98.3%), 건강기능식품일반판매업(99.1%), 출판사(100%), 동물판매업(96.5%) 등 47개 조합 — 업종 특성상 원래 물리적 점포 없이 신고 가능(자가/사무실 주소로 등록, 방문·통신·유통 판매업 등)
- **20% 이하**: 일반음식점(20.4%), 휴게음식점(17.5%), 의원(18.5%) 등 정상 매장업종 — 이 비율은 순수 데이터 입력 누락(예: 창곡동 556의 "윤엄마아빠 떡볶이"만 층 정보가 있고 나머지는 없는 것과 같은 종류의 개별 누락)

이 프로젝트가 이번 세션에 직접 적재한 `기타/담배소매업`은 84.5%로 임계값 아래 — 편의점 등에 붙는 부가허가라 실제 매장이 있다는 기존 도메인 지식과 일치, 자동으로 정상 분류됨(수동 예외처리 불필요).

---

## 범위

| 항목 | 포함 여부 |
|---|---|
| 90%+ 업종을 `units[]`에서 분리해 별도 노출 | ✅ |
| 분류 목록의 정적 관리 + 확장 포인트 | ✅ |
| API 계약 변경(`GET /api/sites/{pnu}` 응답에 신규 필드) | ✅ |
| 90%+ 업종을 완전히 제외(안 보여줌) | ❌ — 사용자 판단: "이것도 나름의 판단근거가 될 수 있다"는 이유로 기각. 보여주되 구분만 |
| 20% 이하 업종의 개별 데이터 누락 보정 | ❌ 별도 이슈 — 이번 스코프는 구조적 무점포 업종 분리만 |

---

## 설계

### 분류 데이터 (`NoStorefrontSubCategories`)

`server/scripts/`가 아니라 Java 쪽 정적 참조 데이터로 관리(기존 `IndustryCategoryMapper` 패턴과 동일 — `infra` 레이어가 아니라 이건 도메인 분류라 `domain` 패키지에 둠). `Set<CategoryPair>` 형태로 47개 (category, subCategory) 쌍을 하드코딩하고, `isNoStorefront(category, subCategory)` 판별 메서드 제공.

이 목록은 **90% 임계값을 기계적으로 적용한 결과**이지 업종별 수작업 큐레이션이 아님 — 표본이 매우 작은 항목(예: n=1~3건짜리 "물류창고업체", "박물관 및 미술관" 등)은 통계적으로 약한 신호이므로 코드 주석에 저신뢰 표시. 향후 실사례로 오분류가 확인되면 그때 개별 조정.

### 데이터 흐름 (`TenancyQueryService`) — 상호명 단위로 먼저 판단

**최초 설계(레코드 단위 분류)는 기각** — 계획 수립 중 기존 테스트 픽스처를 실제 대조하다가
`동물병원 더 하임`(PNU `4113110100100340000`) 사례를 발견: 이 병원은 `동물병원`(호실정보 있음,
정상 매장업종) 라이선스와 `동물미용업`/`동물위탁관리업`(90%+ 무점포 후보) 라이선스를 **동시에**
보유. 레코드 단위로 자르면 같은 병원의 이력이 `units[]`와 `noStorefrontRegistrations[]`로
쪼개지는 버그가 생김 — 부가 허가만 따로 뗐다고 실제 매장이 없어지는 게 아님.

**수정된 규칙**: 같은 PNU 안에서 **같은 businessName**의 레코드를 먼저 묶고, 그 그룹 안에
하나라도 "물리적 신호"(① subCategory가 47개 무점포 목록 밖이거나, ② `parsedFloor`/
`parsedUnitNo` 중 하나라도 값이 있음)가 있으면 **그 상호명의 모든 레코드**를 storefront로
취급(기존 Unit 그룹핑 파이프라인). 물리적 신호가 하나도 없는 상호명만 noStorefront.

```
records(유효, licensedAt 있음)
  → businessName으로 그룹핑(같은 PNU 내)
  → 그룹별로 물리적 신호 있는지 판정
      있음 → storefront pool → 기존 unitGroups() → toUnits() (변경 없음)
      없음 → noStorefront pool → businessName + gap(90일) 병합(기존 mergedTenancies() 재사용)
                                → Site.noStorefrontRegistrations
```

기존 `Tenancy` 타입을 그대로 재사용(새 도메인 타입 안 만듦 — `businessName`/`category`/`subCategory`/`period`/`status` 다 이미 있음). `Site` 레코드에 `List<Tenancy> noStorefrontRegistrations` 필드 추가.

### API 계약 (`GET /api/sites/{pnu}`)

`units[]`와 별도로 `noStorefrontRegistrations[]` 신규 배열. 물리적 자리 개념이 없으므로 `unitId`/`marketInfo`/통계 없이 가볍게:

```json
"noStorefrontRegistrations": [
  {
    "businessName": "에스트(est)",
    "category": "생활",
    "subCategory": "통신판매업",
    "licensedAt": "2019-06-04",
    "closedAt": null,
    "status": "영업"
  }
]
```

`units[]`는 이제 이 업종들이 빠진 순수 물리적 자리 이력만 담음 — 기존 `unitCount`/`closedCount`/검색결과(`SiteCandidateDto`)는 이미 `site.units()` 기준으로 계산되므로 **자동으로 무점포 업종이 제외된 값**이 됨(코드 변경 불필요, 부수 효과로 정확해짐).

---

## 테스트 시나리오

1. **분류 판별**: `NoStorefrontSubCategories.isNoStorefront("생활", "통신판매업")` → true, `isNoStorefront("식품", "일반음식점")` → false
2. **Unit 분리**: 같은 PNU에 일반음식점 1건 + 통신판매업 2건 → `units[]`엔 일반음식점만 1개 Unit으로, `noStorefrontRegistrations[]`엔 통신판매업 2건
3. **무점포 업종끼리 gap 병합**: 같은 businessName의 통신판매업이 gap 90일 이내 카테고리만 바뀌며 재등록 → 하나로 병합(기존 `mergedTenancies` 로직 그대로 적용됨을 확인)
4. **전부 무점포인 PNU**: Unit이 0개, `noStorefrontRegistrations`만 있는 사례 → `units: []`, 검색결과 `unitCount: 0`이지만 사이트 자체는 정상 조회됨
5. **기존 회귀**: `기타/담배소매업`(84.5%, 임계값 미만)은 여전히 `units[]`에 남아있어야 함

---

## 한계 및 후속 작업

| 항목 | 상태 |
|---|---|
| 47개 목록은 90% 임계값 기계적 산출 — 개별 업종 타당성 수작업 검증 안 함 | 표본 작은 항목(n≤5) 위주로 향후 재검토 여지 있음 |
| `noStorefrontRegistrations`도 `mergedTenancies` 재사용(businessName+gap 90일) | 이 로직은 원래 "같은 자리 같은 가게" 가정으로 설계됨 — 무점포 업종엔 "같은 자리"가 없으니 이 재사용이 완벽히 맞는 전제는 아님. 일단 동일 로직 적용, 이상하면 재설계 |
| 20% 이하 정상업종의 개별 데이터 누락(예: 일반음식점 20.4%) | 이번 스코프 아님 — 여전히 `단일(상세주소불명)` Unit으로 남음 |
