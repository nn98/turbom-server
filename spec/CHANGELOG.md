# CHANGELOG

작업 스펙(Claude Code가 실제 참조하는 4개 파일: `api-spec.md`, `frontend-spec.md`, `backend-spec.md`, `schema.sql`)의 버전 이력. 2026-07-09부터 **파일명 고정 + 이 로그로 이력 관리** 방식으로 전환. 그 이전(v1·v2)은 파일 자체를 삭제해서 원문이 남아있지 않음 — 델타 서술은 `의사결정-기록.md`가 유일한 기록.

로컬 저장 경로(`D:\Dev\_Woowahan-Techcourse\woowaTon`) 루트는 git 미관리이므로, 저장할 때마다 `archive/YYYY-MM-DD/`에 스냅샷을 남기고 이 로그에 한 줄 추가하는 걸 권장.

## 왜 이 방식으로 바꿨나

애초에 `api-spec-v2.md` → `api-spec-v3.md`처럼 파일명에 버전을 접미사로 붙이고, 옛 파일은 "혼선 방지"로 삭제하는 방식을 썼음. 문제는 둘이 상충함 — 접미사 버저닝의 존재 이유가 이력 보존인데, 옛 파일을 지우면 이력이 안 남음. 게다가 `backend-spec.md`는 접미사 없이 계속 같은 파일을 덮어써서 스펙 4개끼리 버저닝 방식 자체가 달랐음. 로컬 루트가 git 밖이라는 게 확인되면서, 파일명이 매번 바뀌면 다른 문서들의 상호 참조(`api-spec.md를 참조하라` 같은 문장)가 매번 깨지는 문제도 있었음 — 실제로 v3 전환 때 기획서 원본이 옛 파일명을 그대로 가리키고 있었고, 심지어 v2 시점의 설계(neighborhood)를 그대로 서술하고 있던 게 이번에 발견돼 정정함.

## 이력

### 2026-07-10 (10차) — 도로명주소 상세 오프라인 파싱 반영 (`api-spec.md`·`schema.sql`)

`units[]`/`unit` 응답의 `label`이 응답 시점 실시간 정규식(`TenancyQueryService.unitLabel(String)`)으로 만들어지고 있었는데, 버그(괄호가 먼저 나오면 층 정보가 통째로 유실 — 예: "1(일부)층") 및 낮은 커버리지(건물명 단독·"일부" 수식어 케이스 다수가 "단일 점포"로 뭉뚱그려짐)가 확인됨.

- **아키텍처 결정**: 런타임 파싱 대신 `data.sql`을 오프라인 1회성으로 재생성해 `parsed_building_name`/`parsed_floor`/`parsed_unit_no`/`parse_confidence`/`parse_method` 5개 컬럼을 데이터셋 자체에 영속화(런타임 비용 0). 신규 파서 `AddressDetailParser`(순수 자바, 단위테스트 12건), 재생성 도구 `AddressDatasetRegenerator`(`src/main/java/com/nextstep/tools/`, `main()` 독립 실행) 추가
- **실측 결과**(47,532건 전체 재생성 후 검증): `parse_confidence` `HIGH` 89.2%(42,421건) / `LOW` 10.8%(5,111건). `parse_method` `REGEX` 81.2% / `UNPARSED` 10.8% / `NONE`(애초에 상세주소 없음) 8.1%
- `TenancyQueryService.unitLabel()`을 영속 컬럼 기반 조합 로직으로 교체(정규식 완전 제거, 버그 자동 해소) — `parseConfidence != "HIGH"`면 항상 `"단일 점포"` 폴백
- `schema.sql`에 5개 컬럼 추가, `units[]`(②)와 `unit`(③) 응답에 `parsedFloor`/`parsedUnitNo`/`parseConfidence` 3개 필드 신규 노출. `api-spec.md`에 "label 산출 규칙" 절 추가
- `frontend-spec.md` 타입(`UnitSummary`/`UnitDetail.unit`)·목데이터 7블록에 3개 필드 반영, `backend-spec.md` §3.2 Unit 도메인모델에 근거·참조 추가 — 스펙 4개 교차 정합성 확인 완료
- 수정 전 스냅샷: `spec/archive/2026-07-10/{api-spec,frontend-spec,backend-spec}.md.before-address-parsing`

### 2026-07-10 (9차) — `schema.sql` 멱등화 (다중 테스트 컨텍스트 DB 공유 충돌 수정)

`server` 백엔드에서 `mvn test` 전체 스위트 실행 시 `SiteControllerTest`(`@SpringBootTest`+`@AutoConfigureMockMvc`)가 `Table "SITE" already exists`로 실패하는 문제 발견. 원인: `application.yml`이 이름 있는 인메모리 H2(`jdbc:h2:mem:nextstep;DB_CLOSE_DELAY=-1`)를 쓰는데, 애노테이션 조합이 다른 `SiteControllerTest`와 `TenancyQueryServiceTest`가 Spring에서 서로 다른 ApplicationContext로 뜨면서 같은 이름의 DB를 공유함. `DB_CLOSE_DELAY=-1`이 DB를 계속 살려두므로, 두 번째 컨텍스트가 뜰 때 `spring.sql.init.mode: always`가 `schema.sql`을 재실행하다가 이미 존재하는 테이블과 충돌. 자식→부모 역순 `DROP TABLE IF EXISTS ... CASCADE` 4줄을 파일 맨 앞에 추가해 재실행에 멱등하도록 수정(테이블 정의·인덱스는 무변경). `server/src/main/resources/schema.sql`에 동일 수정 반영, `mvn test` 전체 재통과 확인. 수정 전 스냅샷: `spec_before/schema-2026-07-10-before-idempotent-drop.sql`.

### 2026-07-10 (8차) — 프론트 제작 프롬프트 v2 병합 (`spec/프론트-제작-프롬프트.md`)

세 소스를 병합: ① 기존 v1 프롬프트, ② `frontent-UI-design-prompt.md`(Loveable용 디자인 프롬프트, 이번에 `spec_before/`→`spec/`로 승격), ③ nextstep-client 실 검색화면(`Home.tsx`+`Map.tsx`) 리버스 프롬프팅.

- 구조는 v1 유지, 디자인 프롬프트의 보고서 페이지 11섹션 순서(Header→Summary→종합분석→주변상권분석→위험도→인사이트→운영이력→통계→가게자세히보기→체크리스트→CTA)로 확장
- `## 검색 페이지`는 디자인 프롬프트의 다단계 서술 대신 nextstep-client `Map.tsx`(지도+플로팅패널+세그먼트탭) 리버스 프롬프팅으로 교체 — 사용자 확정
- **주변상권분석/체크리스트가 요구하는 확장 필드(업종구성/경쟁도/전체점포수/최근개업수/상권특징)는 `api-spec.md`에 없음** — 실측 불가로 이미 확정된 사실(§6). 백엔드 확장 없이 프론트 전용 mock 타입(`MarketAnalysisMock`)으로 분리, "예시" 캡션 필수. 차후 기능 보강 대상으로 명시
- nextstep-client `insights.ts`(riskLevel/repeatCategoryFailure/signals/diagnosis/checklist) 포팅 명시 — 사용자 확정, 새로 작성하지 않음
- shadcn/ui 신규 채택(디자인 프롬프트 따름) — 사용자 확정. `npx shadcn@latest init -t vite` 기준 설치 절차 확인 후 반영(context7)
- 디자인 프롬프트 하단 Supabase Edge Function/IndexedDB/API키 직접입력/프로젝트명 "코스잇다" 섹션은 우리 스택(Spring Boot+Railway)과 무관한 다른 템플릿 잔재로 판단, 전부 제외 — 사용자 확정
- 수정 전 스냅샷: `archive/2026-07-10/프론트-제작-프롬프트.md.before-merge`

**병합 직후 재검토(같은 날)로 추가 발견·수정**: `frontend-spec.md` §3 라우트 표·§4② 헤딩이 여전히 `/sites/:pnu`(경로파라미터)로 남아있었음 — 실제로는 `/map?q=&pnu=`(지도+쿼리스트링) 방식임을 반영해 정정, ②의 지도가 "마커 1개"로 서술돼 있던 것도 실제 구현(후보 전체 마커+`fitBounds`)에 맞게 수정. `프론트-연동계약서.md`의 ① 랜딩 클릭 목적지도 동일하게 정정. shadcn/ui 설치 명령·컴포넌트 10종 실존 여부는 context7로 재검증 완료(문제 없음).

### 2026-07-10 (7차) — 정합성 점검(스펙 문서 간 교차검증, 코드 변경 없음)

프론트/백 실 구현 착수 전, `spec/` 8개 문서 + `CLAUDE.md`를 전수 교차검증해 버전 변동 과정에서 갱신 안 된 잔재를 정리:

- **`backend-spec.md` §3.2 LocationSource.LICENSE 근거 정정**: `호실분리여부`+`호실단위지번주소`(구 v2, 36컬럼 데이터셋 기준)를 실제 19컬럼 필드명 `주소분리여부`로 교체. 실측상 이 필드는 **전부 false**(`인허가-데이터-필드명세-v4.md` §3)라 LICENSE 분기가 실데이터에서 사실상 발동 안 함을 명시
- **좌표 지오코딩 폴백(VWorld·상가API lon/lat) 전면 불필요로 정정**: `인허가-데이터-필드명세-v4.md` §7 실측(원본좌표 결측 0%, 10건 표본)을 근거로 `CLAUDE.md`(§8·§9·§10 3곳), `상권조회-API-명세.md`(§4 lon/lat 필드), `의사결정-기록.md`(D-GEO)를 정정. `backend-spec.md` §6 Site.coordinate 매핑에 근거·잔여 리스크(전량 적재 시 개별 변환 실패 가능성, `COORD_CONVERT_FAIL` 처리 유지) 명시
- **"18컬럼" 잔재 정정**: `backend-spec.md`(2곳)·`schema.sql` 헤더 주석이 v3(18컬럼) 시점 그대로 남아있었음 — 본문(sub_category 컬럼 등)은 이미 19컬럼 기준이라 실제 동작엔 영향 없었음. 19컬럼으로 통일
- **`frontend-spec.md` 자기참조 깨짐 수정**: 도입부가 개명 전 파일명 `api-spec-v3.md`를 참조하고 있었음(1차 개편 때 `api-spec.md`로 개명됨) → `api-spec.md`로 수정
- **`인허가-데이터-필드명세-v4.md` §8 자기모순 수정**: 본문은 `subCategory`로 이미 정정했는데 "다음 반영 대상" 문장에만 옛 필드명 `businessType`이 남아있었음
- 수정 전 스냅샷: `archive/2026-07-10/`

### 2026-07-09 (6차) — 디렉토리 재편(spec/ · spec_before/) + 백엔드 구현 착수

- 현재 적용 중인 최신 스펙만 루트의 `spec/`로 이동: `api-spec.md`·`frontend-spec.md`·`backend-spec.md`·`schema.sql`·`CHANGELOG.md`·`의사결정-기록.md`·`상권조회-API-명세.md`·`인허가-데이터-필드명세-v4.md`·`데이터_예시.csv`·`sangga_client.py`·`test_client.py`
- 버전 접미사 붙은 옛 파일(`api-spec-v3.md`·`frontend-spec-v3.md`·`schema-v3.sql`·`10-backend-spec.md`·`11-backend-spec.md`·`20-frontend-spec.md`)과 옛 계약 스냅샷 디렉토리(`files/`·`files0/`·`files1/`), 갱신 안 된 `프론트-연동계약서.md`·`프론트-제작-프롬프트.md`는 전부 `spec_before/`로 이동(삭제 아님, 원본 파일명 그대로 보존)
- `CLAUDE.md` 파일맵을 새 경로(`spec/` 접두)로 갱신
- **적재 데이터 실사**: `data_uncleaning/`의 raw CSV들을 열어보니 `schema.sql`이 가정한 19컬럼(category/subCategory 분리 + 폐업일자 실컬럼) 구조와 일치하는 대용량 파일이 없음이 확인됨. 9.8만행짜리 `PNU(지번)기반_개폐업정보현황_성남시_10년.csv`는 폐업일자 컬럼 자체가 없고, `식품_일반음식점_...csv`는 일반음식점 단일업종뿐. `spec/데이터_예시.csv`(20행)만 정확히 목표 구조와 일치 — 이건 예시값일 뿐 전체 규모 데이터셋은 아님(사용자 확인)
- **범위 확정**: 대용량 실데이터 적재는 이번 백엔드 서버 구현과 별개 작업으로 분리. 이번 세션은 `spec/schema.sql` 그대로 Spring Boot 서버(전 6단계, 상가API 클라이언트 포함)를 완성하고, 로컬 개발/데모용 시드는 `데이터_예시.csv`와 동일 구조의 소규모 데이터로 채움
- `ingestion/`(Node 파이프라인)은 구버전 스키마(`category_code` 단일 컬럼 등) 기준이라 현재 스키마에 그대로 못 씀을 확인, 재사용하지 않기로 함

### 2026-07-09 (5차) — 상가API 공식 명세 확정 (hwp 원문)

- 사용자가 업로드한 공식 활용가이드(hwp)를 hwp5html로 표 구조까지 추출해 읽음
- **BASE_URL 확정**: `/sdsc/`가 아니라 `/sdsc2/`였음(코드 기본값 수정)
- **오퍼레이션 함정 발견**: "반경내 상권조회"(storeZoneInRadius, #2, 폴리곤 데이터)와 "반경내 상가업소조회"(storeListInRadius, #10, 우리가 실제 쓰는 것)는 이름이 비슷해도 완전히 다른 오퍼레이션. 코드 URL 오타 위험 요소였음
- **설계 개선**: 반경조회에 `indsSclsCd` 서버 측 필터가 있음을 확인 → marketInfo.sameCategoryNearbyCount 산출을 "클라이언트 필터링"에서 "서버 필터 + totalCount 그대로 사용"으로 단순화(정확도·성능 개선)
- 응답 스키마 확정: `numOfRows`/`pageNo`/`totalCount`가 `body` 하위, `items`와 형제 노드(우리 클라이언트 코드가 우연히 정확했음이 확인됨)
- `enrichment/sangga_client.py`: BASE_URL 수정, `fetch_radius_same_category_count` 신규 메서드 추가, `_request_page`→`_request` 공통 재시도 헬퍼로 리팩터링, 테스트 3건 추가(20개 전체 통과)
- `상권조회-API-명세.md` v1(추정 다수, archive)를 hwp 원문 기반 v2로 전면 교체
- `backend-spec.md` 4.2절(상권 조회 파이프라인) 서버 필터 방식으로 갱신

### 2026-07-09 (4차) — 대분류/소분류 컬럼 분리 (업종 3계층 확정)

- 데이터셋 19컬럼으로 변경(대분류_소분류 묶였던 것을 원본 행안부 구조대로 분리)
- 업종 3계층 확정: `category`(대분류, 타임라인 표시) / `subCategory`(소분류, 상세 표시) / `industryDetail`(상가API 세부, 있으면 우선). 별도 필드 유지로 출처 보존(사용자 확정 A안)
- `businessType`(업종명) 필드 **제거** — 전부 공백, 소분류가 역할 대체
- 주소분리여부: 사용자 입력 표현 제각각(101호=1층1호)이라 신뢰불가 → 자리=물건 1:1 기본 확정
- 반영: `api-spec.md`·`frontend-spec.md`(타입·목데이터 JSON 7블록 재검증)·`schema.sql`(sub_category 컬럼)·`backend-spec.md`·`프론트-연동계약서.md`·`프론트-제작-프롬프트.md`·`인허가-데이터-필드명세-v4.md`(v3 아카이브)

### 2026-07-09 (3차) — 실데이터셋 검증 + category/businessType 분리 + 프론트 연동계약

- 실 CSV(18컬럼) 검증 완료 → 가정을 실측으로 대체. `인허가-데이터-필드명세-v3.md` 신규(v2 아카이브)
- 확정 사항: PNU 100%·폐업일자 실값·좌표 100%(투영, 변환필요)·**업종명 전부 공백**·**주소분리여부 전부 false**
- API 계약: `category`(대분류, NOT NULL) / `businessType`(업종명, nullable) / `industryDetail`(상가API) 3필드 분리. `api-spec.md`·`frontend-spec.md` 목데이터·타입 반영, JSON 7블록 재검증 통과
- `schema.sql` v5: 실 CSV 컬럼 확정(original_x/y 보존, address_corrected 마스킹 지표, business_type nullable)
- 물건분리 D-1 재확정: 주소분리여부 신뢰불가 → 자리=물건 1:1 기본, 연대기는 "한 PNU에 여러 Tenancy 시간순"
- 신규: `프론트-연동계약서.md`(엔드포인트·타입·도메인 한 장), `프론트-제작-프롬프트.md`(Claude Code용)
- 마스킹 처리 규칙 신설(10만→유효 5만, ingestion_exclusion_log)

### 2026-07-09 (2차) — 백엔드 상세 아키텍처 + 인허가 신형 스키마 (archive/2026-07-09-detailed/)

- **개폐업(DB조회) / 상권(API 실시간호출) 두 파이프라인을 아키텍처 레벨에서 분리** — 시간 특성이 다른 두 소스를 신뢰 경계 A(DB)/B(외부API)로 나누고, B 실패가 A를 막지 않는 부분실패 격리를 SiteQueryService의 try-catch로 구현
- `backend-spec.md`를 상세판으로 교체(레이어·도메인모델·데이터흐름·착수순서). 이전판은 archive에 보존
- `schema.sql` v4: **상권 테이블 전부 제거**(tenancy_market_info, site_radius_store_cache) — 상권은 DB 영속화 안 하고 인메모리 캐시만. DB는 개폐업만
- 인허가 신형 데이터셋 반영: PNU 직접 제공(유도 불필요), **폐업일자 실값**(추가 전제 → closedAtEstimated 항상 false), 호실분리여부 기반 물건 분리(locationSource LICENSE 최우선)
- 조회 식별자 = PNU로 두 소스(개폐업 DB + 상권 API) 연결
- 근거: `인허가-데이터-필드명세-v2.md`(신형 컬럼 36개 분석), `backend-spec.md` 1·4장

### 2026-07-09 (1차) — 캐노니컬 전환 + marketInfo 정정 스냅샷 (archive/2026-07-09/)

- 파일명에서 버전 접미사 제거: `api-spec-v3.md`→`api-spec.md`, `frontend-spec-v3.md`→`frontend-spec.md`, `schema-v3.sql`→`schema.sql`, `10-backend-spec.md`→`backend-spec.md`
- 내용상 실질 버전은 "v3"에 해당 — 스크린샷 기반 정정 완료 상태(아래 참조)
- 기획서 원본(`우아한바톤-넥스트스텝-기획-v0.3.md`)이 이 정정 이전(v2 설계: neighborhood 건물상세) 내용을 그대로 담고 있던 걸 발견, marketInfo 기준으로 동기화

### (소급 기록) v2 → v3 — marketInfo 위치·필드 정정

- 계기: 실제 구현 화면 스크린샷 확인
- 변경: `GET /api/sites/{pnu}`의 `neighborhood` 객체 제거 → `GET /api/units/{unitId}`의 `timeline[].marketInfo`로 이전
- 필드 재정의: 6필드 중 `sameCategoryNearbyCount`만 실값(상가API), 나머지 5필드(전용면적/보증금/월세/권리금/유동인구/공실률)는 `isPlaceholder:true` 목업으로 확정
- DDL: `neighborhood_snapshot`(site-level) 폐기 → `tenancy_market_info`(이력 단위) + `site_radius_store_cache` 신설
- 근거: `의사결정-기록.md` 5장·6장

### (소급 기록) v1 → v2 — 물건(Unit) 계층 도입 + 상권정보 반영

- Site-Tenancy 2계층 → Site-Unit-Tenancy 3계층으로 확장(D-U1: 지번 접두사 수집 + 상세주소/영업기간겹침 분리 규칙)
- 상가API 보강 필드(industryDetail/locationSource/enrichmentSource) 도입
- 지오코딩 VWorld, 지도 react-leaflet, 스타일 Tailwind 확정(D-GEO/D-MAP/D-STYLE)
- 원문 파일(`00-api-contract-v1.md`, 구 `api-spec.md`, 구 `frontend-spec.md`, 구 `schema.sql`)은 삭제되어 복구 불가. 델타만 `의사결정-기록.md` 3장에 서술로 남음

## 앞으로 저장할 때

1. 수정 전 현재 파일을 `archive/YYYY-MM-DD/`에 복사(스냅샷)
2. 캐노니컬 파일(`api-spec.md` 등)을 직접 수정
3. 이 CHANGELOG에 날짜+요지 한 단락 추가
4. 서로 다른 스펙 파일이 이번 변경으로 어긋나지 않는지 교차 확인(이번에 기획서-API계약 불일치를 뒤늦게 발견한 사례 참고)
