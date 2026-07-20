# 업종(BusinessType) 도메인 재설계 — 위치확실성/관련인허가/상태신뢰도

## 배경

`TenancyQueryService`(302줄)가 절차적으로 모든 케이스 판단을 떠맡고 있다:
`partitionByStorefront`(무점포 판정), `unitGroups`/`unitKey`(같은 호실 병합), `mergeByGap`(같은
상호 재인허가 병합)이 `LicensedBusinessRecordEntity`를 직접 조작하는 private 메서드들로 늘어서
있다. "무점포 업종" 판정은 `NoStorefrontSubCategories`라는 정적 `Set` 룩업 클래스(90% 임계값
휴리스틱)가 담당하고, `Site`/`Unit`/`Tenancy`는 데이터만 든 record로 행동이 거의 없다.

실제 데이터를 다루면서 이 구조로는 처리하기 어려운 세 가지 케이스가 확인됐다:

1. **집단급식소/위탁급식영업 페어링** — 같은 물건(주소)에서 나오지만 서로 다른
   category/subCategory로 별도 인허가가 뜬다. 앞으로도 늘어날 수 있는 패턴(예: 유사 위탁/공동
   운영 업종 조합).
2. **위치미특정 레코드** — 편의점 등에 붙는 부가허가라 실제 매장은 있지만, 인허가 신청 시 상세주소
   (층/호)를 안 담아 특정 Unit으로 묶을 수 없는 레코드. "매장 자체가 없는" 통신판매업 등
   (`NoStorefrontSubCategories`)과는 다른 축의 문제. **주의 — 이건 담배소매업이라는 카테고리의
   고정 속성이 아니다.** `NoStorefrontSubCategoriesTest.담배소매업은_무점포가_아니다`와
   `의사결정-기록.md`(turbom-spec) §9에 이미 실측·기각 기록이 있음: 층/호 정보 없음 비율이
   84.5%(90% 임계값 미만)라 카테고리 전체를 무점포로 취급하면 정상적으로 호실 정보가 있는 나머지
   15.5%까지 잘못 묶인다. 그래서 이번 설계는 이 축을 **카테고리 단위가 아니라 레코드 단위**로
   판정한다(해당 레코드에 실제 파싱된 층/호/건물명이 전혀 없는가).
3. **상태 신뢰도 낮은 인허가** — 두 가지 서로 다른 원인이 같은 증상(businessStatus/licensed_at이
   실제와 어긋난 채 남음)으로 나타난다:
   - **폐업신고 누락**: 담배소매업은 담배사업법상 점포 간 거리제한(50~100m)이 있어 기존 인허가를
     반납하지 않고 버티며 권리금을 요구하는 "알박기"가 흔하고, 별개로 세무서 폐업신고와 지자체
     담배소매인 폐업신고가 이원화돼 있어 후자를 누락한 채 방치되는 경우도 많다. 의도적/비의도적
     여부와 무관하게 결과는 동일.
   - **인허가 승계로 인한 licensed_at 왜곡**: `의사결정-기록.md`(turbom-spec) §9에 이미 실측 기록됨
     — 담배소매업은 실제 운영 상호·프랜차이즈가 바뀌어도 인허가 자체(와 그 `licensed_at`)가
     갱신되지 않고 승계되는 경우가 있음. 실사례(`4113111600002700009-U1`, 씨유 성남대왕판교로점)는
     `licensed_at=1999-01-15`인데 씨유 브랜드 자체가 2012년 이후 명칭 — 27년짜리 통짜 Tenancy로
     보임. 브랜드 런칭연도보다 이른 비율이 씨유 10.1%·GS25 5.6%·이마트24 5.2%로 실측돼 흔한
     패턴임이 확인됨. §9는 "상태: 미해결, 기록만"으로 남아있고, 후보 해법 중 하나로 "같은 자리의
     다른 레코드로 시작일을 보정"을 제안해뒀다 — 이번 설계의 `reliabilitySignal`이 그 후보를
     일반화한 첫 구현이지만, §9가 자체적으로 지적하듯 곁에 참고할 다른 레코드가 없으면 이 방식도
     한계가 있다(완전 해결 아님, 부분 개선).

세 가지 모두 "업종(category/subCategory)마다 정해지는 고정 속성"을 다루지만, 2번은 실제로는
레코드 단위 판정이 필요하다는 게 §9로 이미 검증돼 있었다 — 그래서 `BusinessType`은 "업종 고유의
성질"(무점포 여부, 관련 인허가, 신뢰도 계산 로직)만 담당하고, "이 레코드가 실제로 위치를
특정할 수 있는가"는 별도로 `LocationIdentity`(레코드 단위)가 판단한다.

## 목표

- `NoStorefrontSubCategories`(boolean 판정, 47개 카테고리 목록)를 `BusinessType.locationCertainty()`로
  그대로 승격시키고(값·의미 변화 없음), 관련 인허가 페어링과 상태 신뢰도 신호를 같은 도메인
  개념에 추가한다. "위치미특정"은 BusinessType의 축이 아니라 `LocationIdentity`가 레코드 단위로
  판단하는 별도 축(§9 실측 근거 — 카테고리 단위로 하면 담배소매업 15.5%를 잘못 배제하게 됨).
- `TenancyQueryService`의 절차적 로직을 책임이 분리된 협력자 클래스들로 재편해 "기능·책임·역할
  협력 구조"를 만든다.
- 지금 아는 3개 축으로 하드코딩하지 않고, 향후 업종별로 진짜 다른 판별 알고리즘이 필요해질 때를
  대비해 다형성 확장 지점을 열어둔다.
- DB 스키마는 건드리지 않는다 — 순수 애플리케이션/도메인 계층 리팩터링.

## 비목표 (이번 스코프 아님)

- 좌표(x/y) + 구조화 파싱 + 원본 문자열을 점수화해서 신뢰도를 계산하는 정교한 퍼지 매칭. 이건
  데이터 정확도를 비약적으로 올릴 수 있는 **매우 중요한 다음 단계**로 남겨두되, 이번 설계에서는
  우선순위 체인(구조화 파싱 → 좌표 근접 보조 → 원본 문자열 폴백)의 단순 버전만 구현한다.
- 3자 이상(A-B-C) 관련 인허가 그룹의 전이적 병합 — 지금 알려진 페어는 전부 2개짜리라 pairwise만
  다룬다.
- `Unit.tenancies()`처럼 반복 질의가 없는 단순 리스트를 일급 컬렉션으로 감싸는 것 — 실제로 여러
  곳에서 재사용되는 질의 로직이 있는 컬렉션만 감싼다.

## 아키텍처

```
domain/businesstype/
  BusinessType (interface)          — locationCertainty(), relatedTypeKeys(), reliabilitySignal(LocationContext)
  StandardBusinessType (record)     — 데이터 테이블 기반 기본 구현 (대부분의 업종)
  BusinessTypeKey (record)          — (category, subCategory)
  RelatedTypeKeys                   — Set<BusinessTypeKey> 일급 컬렉션. pairsWith(BusinessTypeKey)
  BusinessTypeRegistry              — Map<BusinessTypeKey, BusinessType> 조회. registerPair(a, b)로
                                       양방향 등록(비대칭 등록 실수 방지). 미등록 키는 안전한 기본값
                                       (LOCATED / 페어 없음 / 항상 CONFIRMED) 반환 — 예외 없음
  LocationCertainty (enum)          — LOCATED / NO_PHYSICAL_STORE. **기존 `NoStorefrontSubCategories`의
                                       boolean과 의미·값 완전히 동일**(47개 카테고리 목록 그대로 이관,
                                       행동 변화 없음). "위치미특정"은 여기 없다 — §9 실측대로
                                       카테고리 속성이 아니라 레코드별 데이터 완비 여부라, 아래
                                       LocationIdentity가 레코드 단위로 판단한다
  ReliabilitySignal (record)        — level(CONFIRMED/NEEDS_VERIFICATION) + reason
  LocationContext                   — List<LicensedBusinessRecordEntity> 일급 컬렉션(같은 물건으로
                                       판정된 형제 레코드 묶음). recordsLicensedAfter(LocalDate),
                                       hasOtherBusinessName(String). BusinessType.reliabilitySignal()의
                                       파라미터 타입이라 이 패키지에 둔다 — domain/site가 만들어서
                                       넘기기만 하고, businesstype→site 역참조는 없음(순환 방지)

domain/site/
  LocationIdentity (신규)            — 우선순위 체인(구조화 파싱 1순위 → 좌표 근접 2순위 보조 →
                                       원본 문자열 정규화 폴백)으로 "같은 물건" 여부 판정.
                                       TenancyQueryService.unitKey()를 대체. 형제 레코드를 모아
                                       businesstype.LocationContext로 감싸 넘긴다. **레코드에 파싱된
                                       층/호/건물명이 전부 없으면(구조화 파싱도 좌표도 원본 문자열도
                                       비어있으면) `UNLOCATED`로 판정** — 이건 특정 카테고리 속성이
                                       아니라 레코드 단위 데이터 완비 여부의 결과. 담배소매업이 이
                                       경로를 가장 자주 타지만(§9 실측 84.5%), 다른 카테고리 레코드도
                                       상세주소가 비어있으면 동일하게 걸린다
  Site, Unit, Tenancy                — 기존 record 유지. BusinessType을 참조해 자기 자신에 대한
                                       판단(locationCertainty 등)에 답하는 얇은 위임 메서드 추가.
                                       Unit에 relatedLicenseGroups: RelatedLicenseGroups 필드 추가
  RelatedLicenseGroups              — List<RelatedLicenseGroup> 일급 컬렉션. groupContaining(Tenancy)
  RelatedLicenseGroup               — (businessTypeKeyPair, List<Tenancy>) — relatedTypeKeys로 연결된
                                       두 businessName 각각의 병합된 Tenancy 이력을 담는다

application/
  TenancyQueryService                — 협력자들을 조율하는 얇은 오케스트레이터로 축소
  SitePartitioner (신규)              — storefront/무점포 2분류(BusinessType.locationCertainty() 사용,
                                       기존 partitionByStorefront와 동일 기준) 후, storefront 쪽만
                                       UnitGrouper로 넘김
  UnitGrouper (신규)                  — LocationIdentity로 Unit 그룹 형성(기존 unitGroups()).
                                       LocationIdentity가 `UNLOCATED`로 판정한 레코드는 Unit이 아니라
                                       Site의 별도 목록으로 뺌
  TenancyMerger (신규)                — mergeByGap 로직 이관
  RelatedLicenseLinker (신규)         — 같은 Unit 안에서 서로 다른 businessName이라도
                                       BusinessType.relatedTypeKeys()로 연결되면 관련 인허가로 묶음
```

**핵심 흐름**: `LicensedBusinessRecordEntity` 목록 → `SitePartitioner`가 `BusinessType` 조회해서
매장/무점포 2분류(기존과 동일) → storefront 그룹은 `UnitGrouper`(`LocationIdentity` 사용)로 Unit
단위 재편, 이 과정에서 위치 정보가 전혀 없는 레코드는 레코드 단위로 걸러져 Site의 별도 목록으로
빠짐 → 각 Unit 안에서 `TenancyMerger` + `RelatedLicenseLinker`가 인허가 이력/페어링 정리 →
`TenancyQueryService`가 결과를 `Site`로 조립.

**의도적으로 안 하는 것**: `BusinessType`을 지금 당장 서브클래스 여러 개짜리 계층으로 만들지
않는다. 인터페이스로 확장 지점만 열어두고, 지금은 `StandardBusinessType` 하나로 대부분 커버한다.
담배소매업 전용 신뢰도 로직처럼 진짜 다른 알고리즘이 필요해지면 그때 새 구현체
(예: `TobaccoRetailBusinessType`)를 추가한다.

## 세 케이스 매핑

| 케이스 | locationCertainty (BusinessType, 카테고리 단위) | LocationIdentity 결과 (레코드 단위) | relatedTypeKeys | reliabilitySignal |
|---|---|---|---|---|
| 통신판매업 등(기존 무점포 목록) | `NO_PHYSICAL_STORE` | 해당 없음(Unit 그룹핑 자체를 안 거침, 기존과 동일) | 없음 | 항상 CONFIRMED |
| 담배소매업 — 상세주소 있는 레코드(84.5% 중 나머지, 실측 15.5%) | `LOCATED` | 정상 Unit 그룹핑 | 없음 | 같은 PNU(`LocationContext`) 안에 이 레코드보다 `licensedAt`이 늦은 다른 businessName이 있으면 NEEDS_VERIFICATION, 아니면 CONFIRMED |
| 담배소매업 — 상세주소 없는 레코드(실측 84.5%) | `LOCATED`(카테고리 자체는 매장 있음) | `UNLOCATED` → Unit이 아니라 Site 별도 목록 | 없음 | 위와 동일 규칙(Unit에 안 묶여도 계산은 가능 — LocationContext는 PNU 단위) |
| 집단급식소 | `LOCATED` | 정상 Unit 그룹핑 | `{위탁급식영업}` | CONFIRMED (기본) |
| 위탁급식영업 | `LOCATED` | 정상 Unit 그룹핑 | `{집단급식소}` | CONFIRMED (기본) |
| 미등록 업종 | `LOCATED` (기본값) | 정상 Unit 그룹핑 | 없음 (기본값) | CONFIRMED (기본값) |

## 데이터 흐름 (실사례로 검증)

- **일반 매장** (예: 위례서울치과병원류): 기존과 동일 경로, 변화 없음.
- **담배소매업 — 상세주소 없는 레코드(84.5%)**: `BusinessTypeRegistry`는 `LOCATED` 반환(카테고리
  자체는 매장 있음, `NoStorefrontSubCategories`와 동일 판정 유지) → `UnitGrouper`가 `LocationIdentity`로
  묶으려다 구조화 파싱·좌표·원본 문자열 전부 없음을 확인해 `UNLOCATED` → Unit이 아니라 Site의
  별도 목록(`unlocatedRegistrations`)으로 보냄 → 표시 문구 "담배소매업이 등록된/등록되었던
  점포입니다" → 같은 PNU 안에 더 늦게 시작한 다른 상호가 있으면 `reliabilitySignal =
  NEEDS_VERIFICATION`. API 노출은 기존 `noStorefrontRegistrations`와 같은 DTO 모양
  (`NoStorefrontRegistrationDto`)을 재사용하되 `SiteDetailResponse`에 별도 필드
  `unlocatedRegistrations`로 나란히 추가한다 — "매장 없음"과 "매장은 있는데 위치 미특정"은 의미가
  달라 같은 리스트에 섞지 않는다. **다른 카테고리(정상 매장업종)라도 상세주소가 우연히 전부
  비어있으면 같은 경로를 탄다** — 이건 의도된 동작(§9가 확인했듯 카테고리 속성이 아니라 데이터
  완비 여부의 결과이므로).
- **담배소매업 — 상세주소 있는 레코드(15.5%)**: `LocationIdentity`가 정상적으로 Unit을 특정 —
  기존 로직과 동일하게 처리된다. 카테고리 통짜 배제였다면 이 15.5%까지 같이 빠졌을 것(§9가 기각한
  이유).
- **집단급식소/위탁급식영업**: `LocationIdentity`가 주소 기준으로 같은 물건으로 판정 → 같은 Unit에
  묶임 → `RelatedLicenseLinker`가 두 businessName의 `BusinessType.relatedTypeKeys()`가 서로를
  가리키는 걸 확인 → `Unit.relatedLicenseGroups`에 묶임 → API 응답에서 "관련 인허가" 그룹으로 노출.

## 에러 처리 / 엣지케이스

- **미등록 (category, subCategory)**: 예외 대신 안전한 기본값 반환(`LOCATED`/페어 없음/항상
  `CONFIRMED`) — 신규 업종 데이터가 들어와도 서비스가 죽지 않는다. `NoStorefrontSubCategories`가
  null-safe한 것과 동일한 원칙.
- **relatedTypeKeys 비대칭 등록 실수**: `BusinessTypeRegistry.registerPair(a, b)`로 양방향을 한 번에
  등록. 대칭성 검증 테스트로 커버.
- **3자 이상 관련 그룹**: 지금 구현은 pairwise만. 필요해지면 `RelatedLicenseGroups`가 전이적
  병합(union-find류)으로 확장될 수 있다는 점만 남겨두고 구현은 안 한다.
- **NEEDS_VERIFICATION 오탐 가능성**: "같은 PNU에서 더 늦게 시작한 다른 상호" 규칙은 정밀 판정이
  아니라 참고 신호다. 완전히 무관한 두 사업이 같은 PNU(같은 건물, 다른 층)에 있으면 오탐 가능.
  UI 표기도 확정("폐업했습니다")이 아니라 "확인 필요" 배지 수준으로 약하게 노출한다.
- **DB 스키마 변경 없음**: `schema.sql`은 그대로.
- **성능**: `LocationContext`는 이미 `findByPnuOrderByLicensedAtAscIdAsc()`로 한 번에 긁어온 목록
  안에서 메모리 계산만 하므로 추가 쿼리 없음 — N+1 위험 없음.
- **기존 테스트 호환성**: 순수 구조 이동이므로 기존 79개 테스트는 그대로 통과해야 한다. 단
  `reliabilitySignal`/`relatedLicenseGroups`/`unlocatedRegistrations`는 응답 DTO에 새 필드가
  생기므로 이 부분은 새 테스트로 커버한다.

## 테스트 전략

- **신규 유닛 테스트**: `BusinessTypeRegistry`(미등록 키 기본값, `registerPair` 대칭성, 기존
  `NoStorefrontSubCategoriesTest`의 47개 카테고리 케이스가 `locationCertainty()`로 그대로
  이관돼도 값이 안 바뀌는지), `LocationIdentity`(우선순위 체인 각 단계 — 구조화 파싱 일치/좌표
  근접/원본 문자열 폴백/전부 없음→`UNLOCATED`), `SitePartitioner`(매장/무점포 2분류),
  `RelatedLicenseLinker`(정상 페어링 + 페어 없는 경우), 담배소매업 `reliabilitySignal` 단순 규칙
  (같은 PNU에 더 늦은 타 상호 있음/없음), **상세주소 있는 담배소매업 레코드가 `UNLOCATED`로
  잘못 빠지지 않는지**(§9 회귀 방지용 핵심 케이스).
- **실제 데이터 기반 픽스처**: 이 리포 관례대로(예: `docs/superpowers/plans/2026-07-16-unit-dedup.md`의
  창곡동 509 사례) 실제 시드 데이터에서 집단급식소/위탁급식영업 페어 사례 1건, 담배소매업 사례
  1건을 찾아 그대로 테스트 픽스처로 사용한다.
- **회귀 보장**: 기존 79개 테스트(특히 `PersistenceSmokeTest`의 정확한 행수, `SiteControllerTest`)가
  그대로 통과하는 것이 게이트 — 리팩터링이 순수 구조 이동이라는 걸 증명한다.
- **신규 API 계약 테스트**: `SiteControllerTest`/`SiteQueryService`에 새 필드(관련 인허가 그룹,
  reliabilitySignal, 위치미특정 목록) 노출 검증 케이스를 추가한다.

## 다음 단계 (이번 스코프 아님, but 중요)

좌표(x/y) + 구조화 파싱 + 원본 문자열 정규화를 가중치·점수화해서 "같은 유닛일 확률"을 계산하는
퍼지 매칭. 상세주소가 자유 기재라 오류가 많다는 게 지금 정확도 문제의 근본 원인이라, 이 축을 제대로
풀면 정확도가 크게 오를 것으로 예상된다. 다만 임계값/가중치 튜닝과 검증 비용이 커서 이번 구조
개선과는 별도 스펙으로 분리한다.
