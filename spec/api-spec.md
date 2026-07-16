# 넥스트스텝 API 명세 v3

이전 `api-spec-v2.md`를 대체한다. **정정**: 실제 화면 스크린샷 검토 결과 상권정보 패널의 위치가 잘못 설계됐었다 — 건물 상세(②)가 아니라 **물건 상세(③)**, 그것도 선택된 이력(Tenancy) 옆에 붙는 "계약·주변상권 정보" 카드였다. v2에서 site 응답에 넣었던 `neighborhood`는 제거하고 unit 응답에 `marketInfo`로 옮긴다.

동시에 필드 성격을 명확히 나눈다. 화면 참고 이미지의 카드는 6개 필드 중 **1개만 실제 API로 만들 수 있고 나머지 5개는 데이터 소스가 없다**(이미지 자체에 "실 데이터 연동 전 예시값입니다" 캡션이 있음):

| 필드 | 소스 상태 |
|---|---|
| 주변 같은업종 개수 | **실제** — 상가API 반경조회 |
| 전용면적 / 보증금·월세 / 권리금 | **소스 없음** — 임대차 계약 정보, 확보한 API 어디에도 없음 |
| 일평균 유동인구 | **소스 없음** — 상가API엔 없는 필드 |
| 주변 공실률 | **소스 없음** — 상가API엔 없는 필드, 서울 열린데이터광장은 서울 한정이라 성남에 부적용 |

`isPlaceholder` 필드로 이 구분을 응답에 명시한다. Claude Code가 없는 소스를 만들려 시도하지 않도록 하는 게 목적.

## 공통 규약

- Base URL: 로컬 `http://localhost:8080`, 운영 Railway 도메인.
- JSON, `Content-Type: application/json; charset=UTF-8`. 인증 없음.
- 날짜 `YYYY-MM-DD`. 값 없으면 `null`.
- 모든 조회 응답에 `disclaimer` 상시 포함.

## 도메인 계층 · 데이터 소스 (재확인)

| 소스 | 만드는 것 | 반영 위치 |
|---|---|---|
| 인허가정보(localdata) | 자리·물건·히스토리(개폐업 이력) | `timeline[]`, `statistics` |
| 인허가정보 — 대분류 | 업종 대분류(예: 동물) | `timeline[].category` |
| 인허가정보 — 소분류 | 업종 소분류(예: 동물미용업) | `timeline[].subCategory` |
| 상권정보(상가 API, 반경조회) | 주변 같은업종 개수(유일한 실제 필드) | `timeline[].marketInfo.sameCategoryNearbyCount` |
| 상권정보(상가 API, 동 단위 매칭) | 히스토리 보강(층/호, 상가API 세부업종) | `units[].industryDetail` 등, `timeline[].industryDetail` 등 |
| (미확보) 임대차·유동인구·공실률 | 소스 없음 — 목업 상수 | `timeline[].marketInfo`의 나머지 필드, `isPlaceholder: true` |

계층: Site 1—N Unit 1—N Tenancy. 자리 키 `pnu`, 물건 키 `unitId`.

## 화면 매핑

| 화면 | 엔드포인트 | 신규 반영 |
|---|---|---|
| ① 랜딩·검색 | `GET /api/sites/search` | 없음 |
| ② 건물(자리) 상세·물건목록 | `GET /api/sites/{pnu}` | `units[]`에 보강 필드만 추가(neighborhood 없음 — **v2에서 제거**) |
| ③ 물건 상세·히스토리 | `GET /api/units/{unitId}` | `timeline[]`에 보강 필드 + **선택 이력별 `marketInfo`(신규)** |

---

## ① GET /api/sites/search

**Query**: `query` (string, 필수)

**200**
```json
{
  "candidates": [
    {
      "pnu": "4113310300104050001",
      "jibunAddress": "경기도 성남시 수정구 금토동 405-1",
      "roadAddress": "경기도 성남시 수정구 대왕판교로 815",
      "latitude": 37.4012,
      "longitude": 127.1045,
      "unitCount": 3,
      "closedCount": 8,
      "currentSubCategory": "일반음식점"
    }
  ]
}
```

| 필드 | 타입 | 설명 |
|---|---|---|
| currentSubCategory | string\|null | 현재 영업 중인 첫 번째 물건의 인허가 소분류. 전체 공실이면 null. Sangga API 미호출 — 추가 지연 없음 |

빈 결과: `{ "candidates": [] }` (200). query 누락: 400 `INVALID_QUERY`.

---

## ② GET /api/sites/{pnu} — 건물 상세 (neighborhood 제거)

**200**
```json
{
  "site": {
    "pnu": "4113310300104050001",
    "jibunAddress": "경기도 성남시 수정구 금토동 405-1",
    "roadAddress": "경기도 성남시 수정구 대왕판교로 815",
    "latitude": 37.4012,
    "longitude": 127.1045
  },
  "units": [
    {
      "unitId": "4113310300104050001-U1",
      "label": "115-4호",
      "currentBusinessName": "치킨나라",
      "currentStatus": "영업",
      "totalTenancyCount": 5,
      "closedCount": 4,
      "averageSurvivalMonths": 27,
      "industryDetail": "후라이드/양념치킨",
      "locationSource": "sangga_api",
      "parsedFloor": null,
      "parsedUnitNo": "115-4",
      "parseConfidence": "HIGH"
    }
  ],
  "disclaimer": {
    "dataAsOf": "2026-07-04",
    "note": "인허가 신고 기준 데이터로 실제 영업 현황과 차이가 있을 수 있습니다."
  }
}
```

### `units[]` 필드

| 필드 | 타입 | 설명 |
|---|---|---|
| unitId | string | 물건 키 |
| label | string | **[변경]** 아래 "label 산출 규칙" 참고 |
| currentBusinessName | string\|null | 현재 영업 가게명(공실이면 null) |
| currentStatus | `영업`\|`공실` | |
| totalTenancyCount | number | 거쳐간 가게 수 |
| closedCount | number | 폐업 수 |
| averageSurvivalMonths | number\|null | 폐업 이력만 평균 |
| industryDetail | string\|null | 세부 업종(상가API indsSclsNm). 공실·보강실패 시 null |
| locationSource | `"license"`\|`"sangga_api"`\|`"overlap_inferred"` | label 출처 |
| parsedFloor | string\|null | **[신규]** 도로명주소 상세에서 파싱한 층수(예: `"1"`, `"B1"`). 없으면 null |
| parsedUnitNo | string\|null | **[신규]** 파싱한 호수(예: `"115-4"`, `"202"`). 없으면 null |
| parseConfidence | `"HIGH"`\|`"LOW"`\|null | **[신규]** 파싱 신뢰도. `HIGH`만 `label`·`parsedFloor`·`parsedUnitNo` 조합을 신뢰 가능. `LOW`는 정규식이 상세주소 패턴을 못 잡아 원본을 보존만 한 상태 — 화면에 별도 뱃지/구분 없이 그대로 쓰면 부정확한 값으로 오인될 수 있음 |

- pnu 없음: 404 `SITE_NOT_FOUND`
- units 정렬: 폐업 많은 순.

### `label` 산출 규칙 (2026-07-10 변경 — 상세주소 오프라인 파싱 반영)

- 도로명주소의 상세 부분(콤마~괄호 사이, 예: "성남대로 151, **분당엠코헤리츠 115-4호** (구미동)")을 `AddressDetailParser`로 미리 파싱해 `data.sql`에 `parsed_*` 컬럼으로 구워 넣고, `label`은 그 컬럼 값을 조합해 만든다 — 응답 시점 실시간 정규식 파싱은 더 이상 하지 않는다(런타임 비용 0).
- 조합 규칙은 `parseConfidence == "HIGH"`일 때만 적용:
  1. `parsedUnitNo`가 있으면 `(parsedFloor + "층 " if parsedFloor else "") + parsedUnitNo + "호"` (예: `"115-4호"`, `"1층 202호"`)
  2. `parsedUnitNo`는 없고 `parsedFloor`만 있으면 `parsedFloor + "층"`
  3. 층/호 정보가 전혀 없고 건물명만 파싱됐으면 건물명 문자열 그대로(단, 건물명 자체는 별도 필드로 응답에 노출되지 않음 — `label`에만 반영)
- `parseConfidence`가 `"LOW"`이거나 null이면(파싱 실패·원본 패턴 미매칭) 위 조합을 시도하지 않고 항상 `"단일 점포"`로 표시한다 — 기존에 존재하던 "괄호가 먼저 나오면 층 정보가 통째로 유실되는" 버그(예: "1(일부)층" 패턴)도 이번에 같이 수정됨.
- 실측 데이터(47,532건) 기준 `parseConfidence` 분포: `HIGH` 89.2%, `LOW` 10.8%.
- **프론트 반영 권장**: `parseConfidence`가 `LOW`인 물건은 `label`이 무조건 `"단일 점포"`로만 내려오므로, 상세 위치 정보를 더 정확히 보여주고 싶다면 물건 상세 화면(③)에서 `jibunAddress`/`roadAddress` 원문을 함께 노출하는 걸 권장. `HIGH`인 경우에만 `parsedFloor`/`parsedUnitNo` 배지를 별도로 붙이는 것도 가능.

---

## ③ GET /api/units/{unitId} — 물건 상세·히스토리 (marketInfo 신규)

**200**
```json
{
  "unit": {
    "unitId": "4113310300104050001-U1",
    "label": "115-4호",
    "jibunAddress": "경기도 성남시 수정구 금토동 405-1",
    "roadAddress": "경기도 성남시 수정구 대왕판교로 815",
    "parsedFloor": null,
    "parsedUnitNo": "115-4",
    "parseConfidence": "HIGH"
  },
  "statistics": {
    "totalTenancyCount": 5,
    "closedCount": 4,
    "averageSurvivalMonths": 27,
    "longestSurvivalMonths": 44,
    "shortestSurvivalMonths": 11
  },
  "timeline": [
    {
      "tenancyId": "t-1001",
      "businessName": "고기굽는집",
      "category": "음식_일반음식점",
      "industryDetail": null,
      "licensedAt": "2013-05-02",
      "closedAt": "2017-01-10",
      "status": "폐업",
      "survivalMonths": 44,
      "closedAtEstimated": false,
      "enrichmentSource": "license_only",
      "marketInfo": {
        "isPlaceholder": true,
        "leaseAreaSqm": 42.6,
        "depositKrw": 50000000,
        "monthlyRentKrw": 2800000,
        "keyMoneyKrw": 0,
        "dailyFloatingPopulation": 21400,
        "sameCategoryNearbyCount": 14,
        "vacancyRatePercent": 6.2,
        "asOf": "2026-07-04",
        "totalStoreCount": 28,
        "categoryBreakdown": [
          { "code": "I2", "name": "음식", "count": 14, "ratio": 0.5 },
          { "code": "G2", "name": "소매", "count": 8, "ratio": 0.2857142857142857 },
          { "code": "S2", "name": "수리·개인", "count": 6, "ratio": 0.21428571428571427 }
        ]
      }
    },
    {
      "tenancyId": "t-1005",
      "businessName": "치킨나라",
      "category": "음식_일반음식점",
      "industryDetail": "후라이드/양념치킨",
      "licensedAt": "2023-01-15",
      "closedAt": null,
      "status": "영업",
      "survivalMonths": 41,
      "closedAtEstimated": false,
      "enrichmentSource": "sangga_api",
      "marketInfo": {
        "isPlaceholder": true,
        "leaseAreaSqm": 42.6,
        "depositKrw": 50000000,
        "monthlyRentKrw": 2800000,
        "keyMoneyKrw": 0,
        "dailyFloatingPopulation": 21400,
        "sameCategoryNearbyCount": 11,
        "vacancyRatePercent": 6.2,
        "asOf": "2026-07-04",
        "totalStoreCount": 28,
        "categoryBreakdown": [
          { "code": "I2", "name": "음식", "count": 11, "ratio": 0.39285714285714285 },
          { "code": "G2", "name": "소매", "count": 10, "ratio": 0.35714285714285715 },
          { "code": "S2", "name": "수리·개인", "count": 7, "ratio": 0.25 }
        ]
      }
    }
  ],
  "disclaimer": {
    "dataAsOf": "2026-07-04",
    "note": "인허가 신고 기준 데이터로 실제 영업 현황과 차이가 있을 수 있습니다."
  }
}
```

### `unit` 필드 (신규 부분)

| 필드 | 타입 | 설명 |
|---|---|---|
| parsedFloor | string\|null | **[신규]** ②와 동일. `parseConfidence`가 `HIGH`일 때만 신뢰 가능 |
| parsedUnitNo | string\|null | **[신규]** ②와 동일 |
| parseConfidence | `"HIGH"`\|`"LOW"`\|null | **[신규]** ②의 "label 산출 규칙" 참고 |

### `timeline[]` 필드 (신규 부분)

| 필드 | 타입 | 설명 |
|---|---|---|
| tenancyId | string | **[신규]** "가게 자세히 보기" 드롭다운이 선택할 키 |
| category | string | 인허가 대분류(예: 음식, 동물). 항상 존재 |
| subCategory | string | 인허가 소분류(예: 일반음식점, 동물미용업). 항상 존재 |
| industryDetail | string\|null | 상가API 세부업종. **있으면 이걸 우선 표시, 없으면 subCategory로 폴백**. 폐업 이력은 원천적으로 null |
| enrichmentSource | `"sangga_api"`\|`"license_only"` | 보강 성공 여부 |
| **marketInfo** | object | **[신규]** 아래 표 |

세 업종 필드 구분: `category`(대분류, 필수) → `subCategory`(소분류, 필수) → `industryDetail`(상가API 세부, 영업중만·있으면 우선). 화면에선 category를 타임라인 기본 표시, 물건 상세 클릭 시 subCategory 노출, industryDetail 있으면 그걸 우선.

### `marketInfo` 객체 — 필드별 소스 구분이 핵심

| 필드 | 타입 | 소스 | 비고 |
|---|---|---|---|
| isPlaceholder | boolean | — | `true`면 이 객체 전체가 예시값. 현재는 항상 `true`(임대차·유동인구·공실률 소스 미확보) |
| leaseAreaSqm | number\|null | **없음(목업)** | 전용면적 |
| depositKrw | number\|null | **없음(목업)** | 보증금 |
| monthlyRentKrw | number\|null | **없음(목업)** | 월세 |
| keyMoneyKrw | number\|null | **없음(목업)** | 권리금(0=무) |
| dailyFloatingPopulation | number\|null | **없음(목업)** | 일평균 유동인구 |
| sameCategoryNearbyCount | number\|null | **실제 — 상가API 반경조회** | 자리 좌표 기준 반경(300m) 내 동일 업종(대분류 매핑) 점포 수. `isPlaceholder`와 무관하게 항상 실값 시도, 실패 시에만 null |
| vacancyRatePercent | number\|null | **없음(목업)** | 주변 공실률 |
| asOf | string(date) | — | 기준일 |
| totalStoreCount | number\|null | **[신규] 실제 — 상가API 반경조회** | 업종 필터 없이 같은 반경 내 전체 점포 수. `sameCategoryNearbyCount` 조회는 성공했는데 이 호출만 실패하면 null(별개 호출로 격리) |
| categoryBreakdown | array\|null | **[신규] 실제 — 상가API 반경조회** | 반경 내 상가 대분류별 점포수·비중(`ratio`=count/totalStoreCount). 아래 표. 실패 시 빈 배열 |

`categoryBreakdown[]` 원소: `{ code, name, count, ratio }` — `code`/`name`은 상가API 대분류 코드·이름(예: `I2`/`음식`), `count`는 그 대분류의 반경 내 점포수, `ratio`는 0~1 소수(전체 대비 비중). 프론트는 이 배열로 업종을 선택하게 하고, 선택한 업종의 `count`/`ratio`를 보여줄 수 있다 — `sameCategoryNearbyCount`는 **이 물건의 현재 업종** 기준 고정값이라는 점과 구분할 것.

인허가 소분류(136종 실측)와 상가API 대분류(G2 소매·I1 숙박·I2 음식·L1 부동산·M1 과학·기술·N1 시설관리·임대·P1 교육·Q1 보건의료·R1 예술·스포츠·S2 수리·개인, 10종)는 서로 다른 분류 체계라 공식 매핑표가 없다 — 도축업·제조업·도매업처럼 애초에 "상가 상권" 개념이 없는 소분류는 매핑하지 않고, 그 경우 `sameCategoryNearbyCount`/`totalStoreCount`/`categoryBreakdown` 전부 null·빈 배열로 응답한다(실패가 아니라 "비교 대상 없음").

`sameCategoryNearbyCount`·`totalStoreCount`·`categoryBreakdown`은 선택된 이력이 어느 것이든 **현재 시점 기준 동일 값**이 나간다 — 상가API가 현재 스냅샷만 주기 때문에 과거 이력을 조회해도 "그 시절 주변 상황"은 알 수 없다. 이 값이 과거를 재현한 게 아니라 "지금 기준"이라는 걸 화면에 명시해야 한다(marketInfo.asOf가 그 역할).

- unitId 없음: 404 `UNIT_NOT_FOUND`
- `timeline`은 `licensedAt` 오름차순.
- `marketInfo`는 매 이력 항목마다 내려가지만(드롭다운에서 어느 걸 선택해도 즉시 표시 가능하도록), 값 자체는 목업 5필드(leaseAreaSqm~vacancyRatePercent) 한정으로 전 항목 동일. `sameCategoryNearbyCount`/`totalStoreCount`/`categoryBreakdown`은 선택 물건의 업종·좌표에 따라 달라질 수 있음(물건마다 대표 업종이 다르면).

---

## 공통 에러

```json
{ "error": "SITE_NOT_FOUND", "message": "해당 자리를 찾을 수 없습니다." }
```

| code | HTTP | 상황 |
|---|---|---|
| INVALID_QUERY | 400 | query 누락/공백 |
| SITE_NOT_FOUND | 404 | pnu 없음 |
| UNIT_NOT_FOUND | 404 | unitId 없음 |
| INTERNAL_ERROR | 500 | 서버 오류 |

`sameCategoryNearbyCount`/`totalStoreCount`/`categoryBreakdown` 산출 실패(상가API 오류)는 별도 에러 코드 없이 해당 필드만 `null`(또는 빈 배열)로 응답한다 — 전체 요청은 계속 200. 세 필드는 각각 독립적인 API 호출 결과라 하나만 실패할 수도 있다(`sameCategoryNearbyCount`는 있는데 `totalStoreCount`/`categoryBreakdown`만 null인 경우 등).

