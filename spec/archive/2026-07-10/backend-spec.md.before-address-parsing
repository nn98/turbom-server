# 넥스트스텝 백엔드 상세 명세 v2

기존 `backend-spec.md`를 대체하는 상세판. 핵심 변화: 데이터 소스의 **시간 특성이 다른 두 계열**을 아키텍처 레벨에서 분리한다.

- **개폐업 정보** = 정제되어 DB에 이미 적재된 데이터. 백엔드는 **조회**만 한다(정제·최신화는 이 백엔드 책임 밖, 별도 배치/수동).
- **상권 정보** = 요청 시점에 외부 API(소상공인 상가정보)를 **실시간 호출**해 가져온다.

이 둘은 절대 같은 레이어에서 뭉치지 않는다. 조회 식별자는 **PNU**(지번주소 검색 → PNU 해석 → 두 소스 조합).

전제(실데이터 검증 완료, `인허가-데이터-필드명세-v4.md`):
- 인허가 데이터셋(19컬럼, 대분류/소분류 분리 반영)은 정제되어 DB에 적재됨. 이 백엔드는 조회만.
- **PNU 100% 채움**(유도 불필요), **폐업일자 실컬럼 존재**(근사 로직 폐기, closedAtEstimated 항상 false).
- **업종명 공백** → 제거. 대분류/소분류 분리(category=대분류, subCategory=소분류), industryDetail(상가API)은 있으면 우선.
- **주소분리여부 신뢰 불가**(호수 있어도 false) → 대부분 자리=물건 1:1.
- 실규모 10만 건 중 마스킹 5만 제외 → 유효 5만. 한 법정동 10년치로 연대기 충분.

---

## 1. 아키텍처 개요

```
                    ┌─────────────────────────────────────────┐
                    │            Controller (web)             │
                    │  GET /api/sites/search                  │
                    │  GET /api/sites/{pnu}                   │
                    │  GET /api/units/{unitId}                │
                    └────────────────┬────────────────────────┘
                                     │
                    ┌────────────────▼────────────────────────┐
                    │       SiteQueryService (application)     │
                    │  - 두 소스를 조합해 응답 조립             │
                    │  - 부분 실패 격리(상권 실패해도 개폐업 정상)│
                    └───────┬───────────────────────┬──────────┘
                            │                       │
              ┌─────────────▼──────────┐  ┌─────────▼───────────────────┐
              │  개폐업 조회 (DB)       │  │  상권 조회 (외부 API 실시간) │
              │  TenancyQueryService   │  │  MarketInfoService          │
              │  → JPA Repository      │  │  → SangaApiClient (HTTP)    │
              │  → PostgreSQL          │  │  → 소상공인 상가정보 API     │
              │  [읽기 전용, 빠름]      │  │  [네트워크, 느림, 실패 가능] │
              └────────────────────────┘  └─────────────────────────────┘
                     신뢰 경계 A                   신뢰 경계 B
              (우리 DB, 트랜잭션 보장)      (외부 의존, 타임아웃·서킷브레이커)
```

두 경계의 성질이 다르다는 게 이 설계의 전부다:
- **경계 A(DB)**: 항상 성공한다고 가정 가능. 실패하면 그건 500(우리 문제).
- **경계 B(외부 API)**: 언제든 실패할 수 있음(쿼터·타임아웃·장애). 실패해도 A의 결과는 내려간다. B 실패는 500이 아니라 해당 필드 null.

## 2. 레이어 구조 (패키지)

```
com.nextstep
├── web
│   ├── SiteController                 // 3개 엔드포인트
│   ├── dto/                           // 요청·응답 DTO (API 계약과 1:1)
│   └── GlobalExceptionHandler         // 도메인 예외 → 계약 에러 JSON
├── application
│   ├── SiteQueryService               // 오케스트레이션: 개폐업 + 상권 조합
│   ├── TenancyQueryService            // 개폐업 DB 조회 전담
│   └── MarketInfoService              // 상권 API 실시간 호출 전담
├── domain
│   ├── site/       Site, Pnu(값객체)
│   ├── unit/       Unit, UnitLabel, LocationSource
│   ├── tenancy/    Tenancy, TenancyPeriod, SurvivalMonths
│   ├── statistics/ UnitStatistics (도메인 계산 로직)
│   └── market/     MarketInfo, NearbyCategoryCount (외부 데이터의 도메인 표현)
└── infra
    ├── persistence/  JPA 엔티티·리포지토리 (개폐업 조회)
    ├── geo/          EPSG:5174 → WGS84 좌표변환
    └── sangga/       SangaApiClient, 응답 매핑 (상권 호출)
```

의존 방향: web → application → domain ← infra. domain은 Spring·JPA·HTTP 전부 비의존(순수 자바). MarketInfo는 외부에서 왔지만 domain에 그 개념(값객체)만 두고, 실제 호출은 infra.sangga가 담당.

## 3. 도메인 모델 (상세)

### 3.1 Site (자리)

```
Site
- pnu: Pnu                    // 값객체, 19자리 검증 내장
- jibunAddress: String
- roadAddress: String
- coordinate: Coordinate?     // 값객체(경도·위도), 없을 수 있음(nullable)
- units: List<Unit>           // 이 자리의 물건들
```

- `Pnu` 값객체: 19자리 형식 검증, 앞 10자리(법정동코드) 추출 메서드. 인허가 데이터에 PNU가 직접 오므로 유도 불필요, 검증만.
- `Coordinate` 값객체: 좌표계 변환은 infra에서 끝내고 domain엔 WGS84(위경도)만.

### 3.2 Unit (물건)

```
Unit
- unitId: UnitId              // {pnu}-U{seq}
- label: UnitLabel            // 호실단위주소 or "물건 A" or "단일 점포"
- locationSource: LocationSource   // LICENSE | SANGGA_API | OVERLAP_INFERRED
- tenancies: List<Tenancy>
- statistics: UnitStatistics  // 파생, 아래 계산 규칙
```

- `LocationSource` 우선순위: **LICENSE 최상위** — 인허가의 `주소분리여부='Y'`가 있으면 관청이 이미 구분한 것이므로 가장 신뢰. 그다음 SANGGA_API(상가 API 층/호 매칭), 최후 OVERLAP_INFERRED(영업기간 겹침 추정).
- **주의**: 실 19컬럼 데이터셋 실측 결과 `주소분리여부`는 **전부 false**로 확인됨(`인허가-데이터-필드명세-v4.md` §3 — 지번주소 문자열에 "201호"·"2층"이 있어도 값이 false, 신뢰 불가). 즉 LICENSE 분기는 우선순위상 최상위지만 실데이터에서는 사실상 발동하지 않는다 — 구현 시 이 분기가 "죽은 코드"처럼 보여도 정상이며, 실제 물건분리는 대부분 SANGGA_API 또는 OVERLAP_INFERRED로 떨어진다는 걸 전제하고 작업할 것. (이전 판(v2 데이터셋, 36컬럼)엔 `호실분리여부`+`호실단위지번주소`라는 별도 필드가 있었으나 현재 19컬럼 데이터셋엔 존재하지 않음 — 용어 혼용 금지)

### 3.3 Tenancy (이력)

```
Tenancy
- businessName: String
- category: String            // 대분류 (예: 동물), NOT NULL, 타임라인 표시
- subCategory: String         // 소분류 (예: 동물미용업), NOT NULL, 상세 표시
- period: TenancyPeriod       // 값객체: licensedAt, closedAt?
- status: String              // licensed_business_record.business_status 원본값
- survivalMonths: SurvivalMonths  // period로부터 계산

TenancyPeriod (값객체)
- licensedAt: LocalDate
- closedAt: LocalDate?        // null이면 영업중. 폐업일자 컬럼 실값 사용(전제)
- survivalMonths(): 폐업이면 licensedAt~closedAt, 영업중이면 licensedAt~today (월 내림)
```

- `closedAtEstimated`는 이제 **불필요**(폐업일자 실값이 온다는 전제). 단, 구형/신형 데이터가 섞일 가능성에 대비해 필드는 유지하되 신형은 항상 false.

### 3.4 UnitStatistics (도메인 계산)

```
UnitStatistics (Unit의 tenancies로부터 계산)
- totalTenancyCount: 전체 이력 수
- closedCount: 폐업 이력 수
- averageSurvivalMonths: 폐업 이력만 평균 (없으면 null)
- longestSurvivalMonths / shortestSurvivalMonths: 폐업 이력 중 (없으면 null)
```

계산은 domain 내부 순수 로직. 적재 시 배치로 미리 계산해 저장할지(읽기 최적화), 조회 시 계산할지는 성능 판단 — 해커톤 규모에선 **조회 시 계산으로 충분**(자리당 이력 수십 건 이하).

### 3.5 MarketInfo (상권, 외부 데이터의 도메인 표현)

```
MarketInfo
- sameCategoryNearbyCount: Int?   // 상가 API 반경조회 결과 (유일한 실값)
- placeholderFields: 목업 6필드    // 전용면적·보증금·월세·권리금·유동인구·공실률
- isPlaceholder: true (항상)
- asOf: LocalDate
```

- `sameCategoryNearbyCount`만 외부 API 실값, 실패 시 null. 나머지는 소스 없어 목업(별도 조사로 확정됨, `의사결정-기록.md` 6장).
- domain엔 값객체로만 존재. 실제 API 호출·매핑은 infra.sangga.

## 4. 두 파이프라인의 명확한 분리 ★

### 4.1 개폐업 조회 파이프라인 (DB, 읽기 전용)

```
지번주소/PNU 입력
  → PnuResolver: 검색어를 PNU로 해석 (지번 LIKE 검색 or PNU 직접)
  → TenancyQueryService.findBySite(pnu)
    → SiteRepository.findByPnu (JPA)
    → UnitRepository.findBySitePnu
    → TenancyRepository.findByUnitIds
  → domain 조립 (Site → Units → Tenancies → Statistics 계산)
  → 반환
```

- 전부 우리 DB. 트랜잭션 `@Transactional(readOnly = true)`.
- 외부 의존 없음 → 이 경로는 상권 API 상태와 무관하게 항상 동작.
- **정제·최신화는 이 파이프라인 밖**: 인허가 데이터는 별도 배치(또는 수동 적재)로 이미 DB에 있다고 가정. 이 백엔드는 그걸 읽기만 함. 최신화 주기·방식은 이 명세의 책임이 아님(사용자 확정).

### 4.2 상권 조회 파이프라인 (외부 API, 실시간)

```
PNU + 좌표 + 대상 업종(소분류코드)
  → MarketInfoService.fetch(pnu, coordinate, indsSclsCd)
    → SangaApiClient.fetchRadiusSameCategoryCount(cx, cy, radius=300, indsSclsCd)
      [HTTP 호출, storeListInRadius(#10) — storeZoneInRadius(#2)와 혼동 금지]
    → 서버가 indsSclsCd로 이미 필터링한 응답의 totalCount를 그대로 사용
    → MarketInfo 값객체 조립 (실값 1 + 목업 6)
  → 반환 (실패 시 sameCategoryNearbyCount=null인 MarketInfo)
```

- **정정(공식 활용가이드 hwp 원문 확인, `상권조회-API-명세.md` 참조)**: 클라이언트에서 응답 items를 받아 `indsSclsNm == category`로 하나씩 비교하는 방식이 아니라, 요청 시점에 `indsSclsCd` 파라미터로 **서버 측 필터링**을 걸고 그 결과의 `totalCount`를 그대로 쓴다. `numOfRows=1`로 최소 페이로드만 요청 — 개별 item 파싱조차 불필요. 정확도(서버가 필터링하므로 클라이언트 로직 버그 여지 없음)와 성능(응답 크기 최소화) 둘 다 이득.
- 오퍼레이션 이름 함정: "반경내 상권조회"(`storeZoneInRadius`, #2)는 상권 폴리곤 경계 데이터를 주는 **다른 오퍼레이션**. 우리가 쓰는 건 "반경내 상가업소조회"(`storeListInRadius`, #10).
- radius 상한 2000m(공식 문서 명시). 우리 고정값 300m는 여유 있음.
- 외부 HTTP. 타임아웃(예: 3초), 재시도(2회), 실패 시 예외 삼키고 null 반환.
- **캐싱**: 같은 PNU+업종에 대한 반경조회 결과는 짧게 캐시(예: 인메모리 TTL 10분). 요청마다 외부 API를 때리면 쿼터(개발계정 일 1,000건) 소진.
- 이 파이프라인의 실패는 **절대 개폐업 조회를 막지 않는다**.

### 4.3 두 파이프라인의 조합 (SiteQueryService)

```
SiteQueryService.getSiteDetail(pnu):
  tenancyResult = tenancyQueryService.findBySite(pnu)   // 경계 A, 실패=500
  if (tenancyResult 없음) throw SiteNotFound

  // 경계 B는 try-catch로 격리
  marketInfo = try { marketInfoService.fetch(...) }
               catch (Exception) { MarketInfo.unavailable() }

  return 조립(tenancyResult, marketInfo)
```

이 try-catch 격리가 부분 실패 설계의 실제 구현 지점. 개폐업(A)은 트랜잭션으로 보장, 상권(B)은 실패해도 A 결과에 null 필드만 얹혀서 나감.

## 5. 엔드포인트별 두 소스 사용 (계약 `api-spec.md` 준수)

| 엔드포인트 | 개폐업(DB) | 상권(API) |
|---|---|---|
| `GET /api/sites/search` | 지번 LIKE 검색으로 PNU 후보 | 사용 안 함 |
| `GET /api/sites/{pnu}` | Site+Units+각 Unit 통계 | 사용 안 함(물건 목록만) |
| `GET /api/units/{unitId}` | Unit+Tenancies+통계 | **각 이력에 marketInfo 실시간 조립** |

- 상권 API 실시간 호출이 실제로 일어나는 건 `GET /api/units/{unitId}` 한 곳뿐. 여기서만 경계 B가 관여.
- search·sites는 순수 DB 조회라 빠르고 항상 성공.

## 6. 인허가 데이터 → 도메인 매핑 (실 CSV 19컬럼, `인허가-데이터-필드명세-v4.md`)

| 도메인 | 소스 컬럼 |
|---|---|
| Site.pnu | PNU (100% 채움, 직접 사용) |
| Site.jibunAddress / roadAddress | 지번주소 / 도로명주소 |
| Site.coordinate | 원본좌표X/Y(EPSG:5174) → WGS84 변환(infra.geo). 결측 0%(10건 표본 실측, `인허가-데이터-필드명세-v4.md` §7) → VWorld·상가API 등 외부 지오코딩 폴백 불필요. 단 전량 적재 시 변환 실패 개별 건은 여전히 발생 가능 — 지도 마커만 제외(§9 마스킹 규칙과 동일 처리, 목록엔 노출) |
| Site.addressCorrected | 주소보정성공여부 (마스킹 품질) |
| Unit | 같은 PNU 안에서 `jibunAddress`가 다르면 별도 물건으로 그룹핑. `roadAddress` 차이는 같은 지번 물건의 이력으로 묶는다. unitId는 `{pnu}-U{n}`. label 기본 "단일 점포" |
| Tenancy.businessName | 사업장명 |
| Tenancy.category | 대분류 (NOT NULL, 타임라인 표시) |
| Tenancy.subCategory | 소분류 (NOT NULL, 상세 표시) |
| Tenancy.licensedAt | 인허가일자 |
| Tenancy.closedAt | 폐업일자 (실값, 공백=영업중) |
| Tenancy.status | 영업상태. `영업/정상`은 `영업`으로 응답하고, 그 외 값은 원본 문자열 유지 |

**마스킹 처리**: 주소보정성공여부·마스킹 흔적으로 저품질 레코드 제외(ingestion_exclusion_log). "10만→유효 5만"의 실체이자 발표의 데이터 정직성 근거.

## 7. 스택·배포

- Java 17, Spring Boot 3.x, Spring Web, Spring Data JPA
- DB: PostgreSQL(Railway). 로컬 H2(파일) 개발
- HTTP 클라이언트: Spring RestClient 또는 WebClient(상가 API 호출용)
- 캐시: Spring Cache(@Cacheable) + Caffeine(인메모리 TTL)
- 배포: Railway
- 서킷브레이커(선택): Resilience4j — 상가 API 연속 실패 시 일정 시간 호출 스킵하고 바로 null. 해커톤 스코프엔 과할 수 있어 단순 try-catch+타임아웃으로 시작, 여유되면 추가.

## 8. 조회 식별자 = PNU (일관성)

- 사용자 진입: 지번주소 텍스트 검색 → `search`가 PNU 후보 반환
- 이후 모든 조회는 PNU(또는 PNU 파생 unitId) 기준
- 상권 API 호출도 PNU로 Site를 찾아 그 좌표로 반경조회 트리거
- 즉 PNU가 개폐업(DB)과 상권(API) 두 소스를 잇는 유일한 조인 키

## 9. schema.sql 반영 사항 (폐업일자·호실분리 반영)

- `licensed_business_record.closed_at` = 폐업일자 실값(nullable, null=영업중). `closed_at_estimated`는 API 호환을 위해 조립 시 항상 false
- `licensed_business_record.business_status`는 원본값을 보존한다. API 응답에서는 `영업/정상`만 `영업`으로 표시하고, 그 외 상태는 원본 문자열을 그대로 전달
- `licensed_business_record`는 CSV 19컬럼 구조를 영문 컬럼명으로 보존하고, Site/Unit/Tenancy 계층은 Java 조회 서비스에서 조립
- **MarketInfo는 테이블 없음**: 실시간 API 호출 결과라 DB에 영속화하지 않음(캐시만). schema.sql에서 marketInfo 관련 테이블 제거 — 이게 이전 버전과의 핵심 차이(이전엔 캐시 테이블을 뒀지만, "상권=실시간 호출" 원칙을 명확히 하려면 DB 영속화 자체를 안 하는 게 설계 의도에 맞음. 인메모리 캐시로 충분).

## 10. 착수 순서 (Claude Code)

1. 프로젝트 스캐폰딩 + 계약 기반 컨트롤러 스텁(고정 목 응답).
2. domain 순수 모델(Site/Unit/Tenancy/값객체/UnitStatistics) + 단위 테스트.
3. infra.persistence: JPA 엔티티·리포지토리, 개폐업 조회 파이프라인(4.1).
4. TenancyQueryService + SiteQueryService(상권 없이 개폐업만) → search·sites 엔드포인트 완성.
5. infra.geo: EPSG:5174 원본좌표를 WGS84 위경도로 변환.
6. infra.sangga: SangaApiClient(HTTP), MarketInfoService(4.2).
6. SiteQueryService에 상권 조합(4.3, try-catch 격리) → units 엔드포인트 완성.
7. 캐시·타임아웃·Railway 배포.

**개폐업만으로 4번까지 가면 이미 핵심 서비스가 동작한다** — 상권(5~6)은 그 위에 얹는 부가층. 이 순서가 부분 실패 설계와 일치(상권 없어도 서비스 성립).
