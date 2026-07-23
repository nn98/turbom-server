# BusinessType 도메인 재설계 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** `NoStorefrontSubCategories`(정적 Set)와 `TenancyQueryService`의 절차적 로직을 `BusinessType` 도메인 개념 + 협력자 클래스들로 재편해, 집단급식소/위탁급식영업 페어링·위치미특정 레코드·상태 신뢰도 낮은 인허가를 객체지향적으로 처리한다.

**Architecture:** `domain.businesstype`(BusinessType 인터페이스, 카테고리 단위 속성) + `domain.site.LocationIdentity`(레코드 단위 위치 판정) + `domain.unit`(RelatedLicenseGroup) 세 도메인 개념을, `application` 계층의 작은 협력자(SitePartitioner/UnitGrouper/TenancyMerger/RelatedLicenseLinker)가 조율. `TenancyQueryService`는 이 협력자들을 호출하는 얇은 오케스트레이터로 축소.

**Tech Stack:** Java 21, Spring Boot 3.3.4, Spring Data JPA, JUnit 5 + AssertJ, MockMvc.

## Global Constraints

- domain 패키지(`com.nextstep.domain.*`)는 Spring·JPA·HTTP 전부 비의존(순수 자바) — `backend-spec.md` §2. `LicensedBusinessRecordEntity`(JPA)는 domain 클래스에 절대 파라미터로 넘기지 않는다. 필요한 필드만 원시 타입/도메인 값객체로 변환해서 넘긴다.
- `NoStorefrontSubCategories`의 47개 (category, subCategory) 목록과 그 의미는 값 변화 없이 그대로 이관한다.
- 기존 `mvn test` **98개**(2026-07-23 기준 — 원안 작성 시점 79개에서 그 사이 배포된 두 기능,
  토큰검색·Unit충돌감지재분리로 늘어남) 테스트는 최종적으로 전부 통과해야 한다(`PersistenceSmokeTest`의
  정확한 행수 검증 포함). 그룹핑 로직이 바뀌는 지점(`csv_동일_pnu의_상세주소별_물건을_리스팅한다`)은
  이 테스트 자체의 기존 관례(2026-07-18 주석 참고)대로 실측값으로 갱신한다 — 추측하지 않는다.
- DB 스키마(`schema.sql`)는 건드리지 않는다.
- 커밋마다 `mvn test`(관련 클래스만이라도) 통과 확인.
- 작업 디렉터리: 배포 서버 `/home/ubuntu/app-build`(turbom-server 클론, origin/main과 동기화됨, 최신
  커밋 `f76669c` — 2026-07-23에 이 계획을 최신 코드 기준으로 보정한 커밋. **주의**: 이 계획은
  2026-07-20 작성 당시 최신 커밋을 `3e25e97`로 적어뒀지만, 그 사이(7/22) `AddressQuery`(토큰 AND
  검색)와 `OccupancySpan`+Unit 충돌감지/재분리가 배포됐다 — Task 8·12는 이미 그 두 기능을 반영해
  보정됨(각 태스크 본문의 "2026-07-23 보정" 문단 참고), 나머지 태스크는 영향 없음).
- **이 계획이 이번 세션의 우선 작업**이다 — 사이에 진행된 turbom-spec 전체 문서 스윕(2026-07-20,
  `CHANGELOG.md` 20~21차)은 별개 작업이고 이 계획의 태스크 내용에 영향 없음(스윕은 API 계약
  문서·상권API·schema.sql 대상이었고, 이 계획은 `TenancyQueryService` 도메인 리팩터링이라 겹치는
  파일이 없음). 다만 그 스윕에서 얻은 교훈(문서가 코드를 못 따라가면 다음 세션이 잘못된 전제로
  작업함)을 반영해 Task 15를 추가함 — 구현이 끝나면 캐노니컬 문서도 그 자리에서 같이 갱신한다.

---

## Task 1: BusinessType 값객체 4종 (BusinessTypeKey, LocationCertainty, ReliabilitySignal, RelatedTypeKeys)

**Files:**
- Create: `src/main/java/com/nextstep/domain/businesstype/BusinessTypeKey.java`
- Create: `src/main/java/com/nextstep/domain/businesstype/LocationCertainty.java`
- Create: `src/main/java/com/nextstep/domain/businesstype/ReliabilitySignal.java`
- Create: `src/main/java/com/nextstep/domain/businesstype/RelatedTypeKeys.java`
- Test: `src/test/java/com/nextstep/domain/businesstype/RelatedTypeKeysTest.java`

**Interfaces:**
- Produces: `BusinessTypeKey(String category, String subCategory)`, `LocationCertainty{LOCATED, NO_PHYSICAL_STORE}`, `ReliabilitySignal(Level level, String reason)` with `ReliabilitySignal.confirmed()`/`ReliabilitySignal.needsVerification(String reason)` static factories and `ReliabilitySignal.Level{CONFIRMED, NEEDS_VERIFICATION}`, `RelatedTypeKeys(Set<BusinessTypeKey> keys)` with `RelatedTypeKeys.none()`, `pairsWith(BusinessTypeKey)`, `values()`.

- [ ] **Step 1: Write the failing test for RelatedTypeKeys**

```java
package com.nextstep.domain.businesstype;

import org.junit.jupiter.api.Test;
import java.util.Set;
import static org.assertj.core.api.Assertions.assertThat;

class RelatedTypeKeysTest {

    @Test
    void 등록된_키와는_페어다() {
        BusinessTypeKey 위탁급식영업 = new BusinessTypeKey("식품", "위탁급식영업");
        RelatedTypeKeys keys = new RelatedTypeKeys(Set.of(위탁급식영업));

        assertThat(keys.pairsWith(위탁급식영업)).isTrue();
    }

    @Test
    void 등록안된_키와는_페어가_아니다() {
        RelatedTypeKeys keys = new RelatedTypeKeys(Set.of(new BusinessTypeKey("식품", "위탁급식영업")));

        assertThat(keys.pairsWith(new BusinessTypeKey("기타", "담배소매업"))).isFalse();
    }

    @Test
    void none은_아무것과도_페어가_아니다() {
        assertThat(RelatedTypeKeys.none().pairsWith(new BusinessTypeKey("식품", "집단급식소"))).isFalse();
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd /home/ubuntu/app-build && mvn test -Dtest=RelatedTypeKeysTest -q`
Expected: FAIL — `BusinessTypeKey`/`RelatedTypeKeys` 클래스가 없어 컴파일 에러.

- [ ] **Step 3: Create the four value objects**

`src/main/java/com/nextstep/domain/businesstype/BusinessTypeKey.java`:
```java
package com.nextstep.domain.businesstype;

public record BusinessTypeKey(String category, String subCategory) {
}
```

`src/main/java/com/nextstep/domain/businesstype/LocationCertainty.java`:
```java
package com.nextstep.domain.businesstype;

public enum LocationCertainty {
    LOCATED,
    NO_PHYSICAL_STORE
}
```

`src/main/java/com/nextstep/domain/businesstype/ReliabilitySignal.java`:
```java
package com.nextstep.domain.businesstype;

public record ReliabilitySignal(Level level, String reason) {

    public enum Level {
        CONFIRMED,
        NEEDS_VERIFICATION
    }

    public static ReliabilitySignal confirmed() {
        return new ReliabilitySignal(Level.CONFIRMED, null);
    }

    public static ReliabilitySignal needsVerification(String reason) {
        return new ReliabilitySignal(Level.NEEDS_VERIFICATION, reason);
    }
}
```

`src/main/java/com/nextstep/domain/businesstype/RelatedTypeKeys.java`:
```java
package com.nextstep.domain.businesstype;

import java.util.Set;

public record RelatedTypeKeys(Set<BusinessTypeKey> keys) {

    public static RelatedTypeKeys none() {
        return new RelatedTypeKeys(Set.of());
    }

    public boolean pairsWith(BusinessTypeKey other) {
        return keys.contains(other);
    }

    public Set<BusinessTypeKey> values() {
        return keys;
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `cd /home/ubuntu/app-build && mvn test -Dtest=RelatedTypeKeysTest -q`
Expected: PASS, 3 tests.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/nextstep/domain/businesstype/BusinessTypeKey.java \
        src/main/java/com/nextstep/domain/businesstype/LocationCertainty.java \
        src/main/java/com/nextstep/domain/businesstype/ReliabilitySignal.java \
        src/main/java/com/nextstep/domain/businesstype/RelatedTypeKeys.java \
        src/test/java/com/nextstep/domain/businesstype/RelatedTypeKeysTest.java
git commit -m "feat: add BusinessType value objects (key, location certainty, reliability signal, related-type keys)"
```

---

## Task 2: LocationContext (일급 컬렉션, domain 순수)

**Files:**
- Create: `src/main/java/com/nextstep/domain/businesstype/LocationContext.java`
- Test: `src/test/java/com/nextstep/domain/businesstype/LocationContextTest.java`

**Interfaces:**
- Consumes: 없음(순수 도메인, 값 타입만 사용).
- Produces: `LocationContext(List<SiblingRecord> siblingRecords)`, nested `LocationContext.SiblingRecord(String businessName, LocalDate licensedAt)`, `hasLaterOtherBusinessName(String businessName, LocalDate licensedAt): boolean`.

**주의**: `LicensedBusinessRecordEntity`(JPA)를 직접 안 담는다 — Global Constraints 참고. 이 타입은 application 계층이 엔티티에서 `SiblingRecord`로 변환해서 넘긴다(Task 9에서 그 변환 코드 작성).

- [ ] **Step 1: Write the failing test**

```java
package com.nextstep.domain.businesstype;

import org.junit.jupiter.api.Test;
import java.time.LocalDate;
import java.util.List;
import static org.assertj.core.api.Assertions.assertThat;

class LocationContextTest {

    @Test
    void 다른_상호가_더_늦게_시작했으면_true() {
        LocationContext context = new LocationContext(List.of(
            new LocationContext.SiblingRecord("씨유 성남대왕판교로점", LocalDate.of(1999, 1, 15)),
            new LocationContext.SiblingRecord("판교스터디카페", LocalDate.of(2020, 3, 1))
        ));

        assertThat(context.hasLaterOtherBusinessName("씨유 성남대왕판교로점", LocalDate.of(1999, 1, 15)))
            .isTrue();
    }

    @Test
    void 같은_상호만_있으면_false() {
        LocationContext context = new LocationContext(List.of(
            new LocationContext.SiblingRecord("씨유 성남대왕판교로점", LocalDate.of(1999, 1, 15))
        ));

        assertThat(context.hasLaterOtherBusinessName("씨유 성남대왕판교로점", LocalDate.of(1999, 1, 15)))
            .isFalse();
    }

    @Test
    void 다른_상호가_있어도_더_이르면_false() {
        LocationContext context = new LocationContext(List.of(
            new LocationContext.SiblingRecord("씨유 성남대왕판교로점", LocalDate.of(2010, 1, 1)),
            new LocationContext.SiblingRecord("옛날가게", LocalDate.of(1990, 1, 1))
        ));

        assertThat(context.hasLaterOtherBusinessName("씨유 성남대왕판교로점", LocalDate.of(2010, 1, 1)))
            .isFalse();
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd /home/ubuntu/app-build && mvn test -Dtest=LocationContextTest -q`
Expected: FAIL — `LocationContext` 없음.

- [ ] **Step 3: Implement LocationContext**

```java
package com.nextstep.domain.businesstype;

import java.time.LocalDate;
import java.util.List;

public record LocationContext(List<SiblingRecord> siblingRecords) {

    public record SiblingRecord(String businessName, LocalDate licensedAt) {
    }

    public boolean hasLaterOtherBusinessName(String businessName, LocalDate licensedAt) {
        return siblingRecords.stream().anyMatch(r ->
            !r.businessName().equals(businessName) && r.licensedAt().isAfter(licensedAt));
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `cd /home/ubuntu/app-build && mvn test -Dtest=LocationContextTest -q`
Expected: PASS, 3 tests.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/nextstep/domain/businesstype/LocationContext.java \
        src/test/java/com/nextstep/domain/businesstype/LocationContextTest.java
git commit -m "feat: add LocationContext value object for reliability-signal peer-record checks"
```

---

## Task 3: BusinessType 인터페이스 + StandardBusinessType

**Files:**
- Create: `src/main/java/com/nextstep/domain/businesstype/BusinessType.java`
- Create: `src/main/java/com/nextstep/domain/businesstype/StandardBusinessType.java`
- Test: `src/test/java/com/nextstep/domain/businesstype/StandardBusinessTypeTest.java`

**Interfaces:**
- Consumes: `LocationCertainty`, `RelatedTypeKeys`, `ReliabilitySignal`, `LocationContext`, `BusinessTypeKey` (Task 1, 2).
- Produces: `BusinessType` interface with `locationCertainty(): LocationCertainty`, `relatedTypeKeys(): RelatedTypeKeys`, `reliabilitySignal(String businessName, LocalDate licensedAt, LocationContext context): ReliabilitySignal`. `StandardBusinessType(LocationCertainty locationCertainty, RelatedTypeKeys relatedTypeKeys, boolean peerBasedReliability)` — 마지막 인자가 true면 담배소매업 규칙(같은 자리에 더 늦게 시작한 다른 상호 있으면 NEEDS_VERIFICATION) 적용, false면 항상 CONFIRMED.

- [ ] **Step 1: Write the failing test**

```java
package com.nextstep.domain.businesstype;

import org.junit.jupiter.api.Test;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import static org.assertj.core.api.Assertions.assertThat;

class StandardBusinessTypeTest {

    @Test
    void peerBasedReliability_꺼져있으면_항상_CONFIRMED() {
        StandardBusinessType type = new StandardBusinessType(
            LocationCertainty.LOCATED, RelatedTypeKeys.none(), false);
        LocationContext context = new LocationContext(List.of(
            new LocationContext.SiblingRecord("다른가게", LocalDate.of(2099, 1, 1))));

        ReliabilitySignal signal = type.reliabilitySignal("가게", LocalDate.of(2000, 1, 1), context);

        assertThat(signal.level()).isEqualTo(ReliabilitySignal.Level.CONFIRMED);
    }

    @Test
    void peerBasedReliability_켜져있고_더_늦은_타상호_있으면_NEEDS_VERIFICATION() {
        StandardBusinessType type = new StandardBusinessType(
            LocationCertainty.LOCATED, RelatedTypeKeys.none(), true);
        LocationContext context = new LocationContext(List.of(
            new LocationContext.SiblingRecord("씨유 성남대왕판교로점", LocalDate.of(1999, 1, 15)),
            new LocationContext.SiblingRecord("판교스터디카페", LocalDate.of(2020, 3, 1))));

        ReliabilitySignal signal = type.reliabilitySignal(
            "씨유 성남대왕판교로점", LocalDate.of(1999, 1, 15), context);

        assertThat(signal.level()).isEqualTo(ReliabilitySignal.Level.NEEDS_VERIFICATION);
        assertThat(signal.reason()).isNotBlank();
    }

    @Test
    void peerBasedReliability_켜져있어도_타상호가_없으면_CONFIRMED() {
        StandardBusinessType type = new StandardBusinessType(
            LocationCertainty.LOCATED, RelatedTypeKeys.none(), true);
        LocationContext context = new LocationContext(List.of(
            new LocationContext.SiblingRecord("씨유 성남대왕판교로점", LocalDate.of(1999, 1, 15))));

        ReliabilitySignal signal = type.reliabilitySignal(
            "씨유 성남대왕판교로점", LocalDate.of(1999, 1, 15), context);

        assertThat(signal.level()).isEqualTo(ReliabilitySignal.Level.CONFIRMED);
    }

    @Test
    void locationCertainty와_relatedTypeKeys는_생성자값_그대로_반환() {
        RelatedTypeKeys pair = new RelatedTypeKeys(Set.of(new BusinessTypeKey("식품", "위탁급식영업")));
        StandardBusinessType type = new StandardBusinessType(LocationCertainty.LOCATED, pair, false);

        assertThat(type.locationCertainty()).isEqualTo(LocationCertainty.LOCATED);
        assertThat(type.relatedTypeKeys()).isEqualTo(pair);
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd /home/ubuntu/app-build && mvn test -Dtest=StandardBusinessTypeTest -q`
Expected: FAIL — 클래스 없음.

- [ ] **Step 3: Implement BusinessType + StandardBusinessType**

`src/main/java/com/nextstep/domain/businesstype/BusinessType.java`:
```java
package com.nextstep.domain.businesstype;

import java.time.LocalDate;

public interface BusinessType {

    LocationCertainty locationCertainty();

    RelatedTypeKeys relatedTypeKeys();

    ReliabilitySignal reliabilitySignal(String businessName, LocalDate licensedAt, LocationContext context);
}
```

`src/main/java/com/nextstep/domain/businesstype/StandardBusinessType.java`:
```java
package com.nextstep.domain.businesstype;

import java.time.LocalDate;

public record StandardBusinessType(
    LocationCertainty locationCertainty,
    RelatedTypeKeys relatedTypeKeys,
    boolean peerBasedReliability
) implements BusinessType {

    @Override
    public ReliabilitySignal reliabilitySignal(String businessName, LocalDate licensedAt, LocationContext context) {
        if (!peerBasedReliability) {
            return ReliabilitySignal.confirmed();
        }
        if (context.hasLaterOtherBusinessName(businessName, licensedAt)) {
            return ReliabilitySignal.needsVerification(
                "같은 자리에 이 인허가보다 더 늦게 시작한 다른 상호가 있음 — 폐업신고 누락 또는 "
                    + "인허가 승계로 licensedAt이 실제 개업일보다 이를 수 있음(turbom-spec 의사결정-기록.md §9)");
        }
        return ReliabilitySignal.confirmed();
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `cd /home/ubuntu/app-build && mvn test -Dtest=StandardBusinessTypeTest -q`
Expected: PASS, 4 tests.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/nextstep/domain/businesstype/BusinessType.java \
        src/main/java/com/nextstep/domain/businesstype/StandardBusinessType.java \
        src/test/java/com/nextstep/domain/businesstype/StandardBusinessTypeTest.java
git commit -m "feat: add BusinessType interface and StandardBusinessType default implementation"
```

---

## Task 4: BusinessTypeRegistry (NoStorefrontSubCategories 이관 + 담배소매업/급식 페어 등록)

**Files:**
- Create: `src/main/java/com/nextstep/domain/businesstype/BusinessTypeRegistry.java`
- Test: `src/test/java/com/nextstep/domain/businesstype/BusinessTypeRegistryTest.java`
- Read (source of the 47-entry list to copy verbatim): `src/main/java/com/nextstep/domain/site/NoStorefrontSubCategories.java`

**Interfaces:**
- Consumes: `BusinessType`, `BusinessTypeKey`, `StandardBusinessType`, `LocationCertainty`, `RelatedTypeKeys` (Task 1, 3).
- Produces: `BusinessTypeRegistry.lookup(BusinessTypeKey): BusinessType`(미등록 키는 기본값 `StandardBusinessType(LOCATED, RelatedTypeKeys.none(), false)`), `BusinessTypeRegistry.lookup(String category, String subCategory): BusinessType`(null-safe, null이면 기본값).

**참고**: `NoStorefrontSubCategories`의 실제 47개 목록은 `src/main/java/com/nextstep/domain/site/NoStorefrontSubCategories.java`(현재 저장소에 있음)의 `NO_STOREFRONT` `Set` 내용을 그대로 복사한다 — 이 계획 문서에 그 47줄을 다시 옮겨적지 않고, "그 파일의 Set 리터럴을 그대로 복사해 각 항목을 `registerNoPhysicalStore(category, subCategory)` 호출로 바꾼다"고 명시한다(한 줄짜리 기계적 변환이라 실수 여지가 거의 없음, 옮기면서 오타가 없는지 각 항목을 원본과 diff로 대조할 것).

- [ ] **Step 1: Write the failing test**

```java
package com.nextstep.domain.businesstype;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class BusinessTypeRegistryTest {

    private final BusinessTypeRegistry registry = new BusinessTypeRegistry();

    @Test
    void 통신판매업은_매장없음으로_등록된다() {
        BusinessType type = registry.lookup("생활", "통신판매업");
        assertThat(type.locationCertainty()).isEqualTo(LocationCertainty.NO_PHYSICAL_STORE);
    }

    @Test
    void 담배소매업은_매장있음으로_등록된다() {
        // 84.5%로 90% 임계값 미만 — turbom-spec 의사결정-기록.md §9, NoStorefrontSubCategoriesTest 원본 근거
        BusinessType type = registry.lookup("기타", "담배소매업");
        assertThat(type.locationCertainty()).isEqualTo(LocationCertainty.LOCATED);
    }

    @Test
    void 담배소매업은_peerBased_신뢰도규칙을_쓴다() {
        BusinessType type = registry.lookup("기타", "담배소매업");
        LocationContext context = new LocationContext(java.util.List.of(
            new LocationContext.SiblingRecord("씨유 성남대왕판교로점", java.time.LocalDate.of(1999, 1, 15)),
            new LocationContext.SiblingRecord("다른가게", java.time.LocalDate.of(2020, 1, 1))));

        ReliabilitySignal signal = type.reliabilitySignal(
            "씨유 성남대왕판교로점", java.time.LocalDate.of(1999, 1, 15), context);

        assertThat(signal.level()).isEqualTo(ReliabilitySignal.Level.NEEDS_VERIFICATION);
    }

    @Test
    void 집단급식소와_위탁급식영업은_서로를_관련인허가로_가리킨다() {
        BusinessTypeKey 집단급식소 = new BusinessTypeKey("식품", "집단급식소");
        BusinessTypeKey 위탁급식영업 = new BusinessTypeKey("식품", "위탁급식영업");

        assertThat(registry.lookup(집단급식소.category(), 집단급식소.subCategory())
            .relatedTypeKeys().pairsWith(위탁급식영업)).isTrue();
        assertThat(registry.lookup(위탁급식영업.category(), 위탁급식영업.subCategory())
            .relatedTypeKeys().pairsWith(집단급식소)).isTrue();
    }

    @Test
    void 미등록_조합은_기본값(매장있음_페어없음_항상신뢰) () {
        BusinessType type = registry.lookup("없는카테고리", "없는소분류");
        assertThat(type.locationCertainty()).isEqualTo(LocationCertainty.LOCATED);
        assertThat(type.relatedTypeKeys().values()).isEmpty();
    }

    @Test
    void category나_subCategory가_null이면_기본값() {
        assertThat(registry.lookup(null, "통신판매업").locationCertainty()).isEqualTo(LocationCertainty.LOCATED);
        assertThat(registry.lookup("생활", null).locationCertainty()).isEqualTo(LocationCertainty.LOCATED);
    }

    @Test
    void 일반음식점은_매장있음이다() {
        assertThat(registry.lookup("식품", "일반음식점").locationCertainty()).isEqualTo(LocationCertainty.LOCATED);
    }
}
```

Java 메서드 이름에 공백/특수문자(`()`)가 들어간 `미등록_조합은_기본값(매장있음_페어없음_항상신뢰) ()`는 유효하지 않다 — 실제 파일에는 아래처럼 고쳐서 쓴다:

```java
    @Test
    void 미등록_조합은_기본값이다() {
        BusinessType type = registry.lookup("없는카테고리", "없는소분류");
        assertThat(type.locationCertainty()).isEqualTo(LocationCertainty.LOCATED);
        assertThat(type.relatedTypeKeys().values()).isEmpty();
    }
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd /home/ubuntu/app-build && mvn test -Dtest=BusinessTypeRegistryTest -q`
Expected: FAIL — `BusinessTypeRegistry` 없음.

- [ ] **Step 3: Implement BusinessTypeRegistry**

`src/main/java/com/nextstep/domain/businesstype/BusinessTypeRegistry.java` — `registerNoPhysicalStore`용 47개 카테고리 목록은 `NoStorefrontSubCategories.NO_STOREFRONT`의 내용을 그대로 옮긴다(아래는 그 중 대표 몇 개 + 전체를 옮기는 방법을 보여주는 골격, 실제 구현 시 원본 파일의 47줄 전부를 `registerNoPhysicalStore(...)` 호출로 1:1 변환):

```java
package com.nextstep.domain.businesstype;

import java.util.HashMap;
import java.util.Map;

public class BusinessTypeRegistry {

    private static final StandardBusinessType DEFAULT =
        new StandardBusinessType(LocationCertainty.LOCATED, RelatedTypeKeys.none(), false);

    private final Map<BusinessTypeKey, StandardBusinessType> byKey = new HashMap<>();

    public BusinessTypeRegistry() {
        registerNoPhysicalStoreCategories();
        registerPeerBasedReliability("기타", "담배소매업");
        registerPair(new BusinessTypeKey("식품", "집단급식소"), new BusinessTypeKey("식품", "위탁급식영업"));
    }

    public BusinessType lookup(String category, String subCategory) {
        if (category == null || subCategory == null) return DEFAULT;
        return byKey.getOrDefault(new BusinessTypeKey(category, subCategory), DEFAULT);
    }

    public BusinessType lookup(BusinessTypeKey key) {
        return byKey.getOrDefault(key, DEFAULT);
    }

    private void registerNoPhysicalStore(String category, String subCategory) {
        replace(new BusinessTypeKey(category, subCategory), existing ->
            new StandardBusinessType(LocationCertainty.NO_PHYSICAL_STORE, existing.relatedTypeKeys(),
                existing.peerBasedReliability()));
    }

    private void registerPeerBasedReliability(String category, String subCategory) {
        replace(new BusinessTypeKey(category, subCategory), existing ->
            new StandardBusinessType(existing.locationCertainty(), existing.relatedTypeKeys(), true));
    }

    private void registerPair(BusinessTypeKey a, BusinessTypeKey b) {
        replace(a, existing -> withRelated(existing, b));
        replace(b, existing -> withRelated(existing, a));
    }

    private StandardBusinessType withRelated(StandardBusinessType existing, BusinessTypeKey partner) {
        java.util.Set<BusinessTypeKey> merged = new java.util.HashSet<>(existing.relatedTypeKeys().values());
        merged.add(partner);
        return new StandardBusinessType(existing.locationCertainty(), new RelatedTypeKeys(merged),
            existing.peerBasedReliability());
    }

    private void replace(BusinessTypeKey key, java.util.function.UnaryOperator<StandardBusinessType> update) {
        StandardBusinessType current = byKey.getOrDefault(key, DEFAULT);
        byKey.put(key, update.apply(current));
    }

    private void registerNoPhysicalStoreCategories() {
        // NoStorefrontSubCategories.NO_STOREFRONT의 47개 항목을 그대로 이관 — 원본 파일과
        // 한 줄씩 대조해서 옮길 것(순서·값 무관, 존재 여부만 일치해야 함)
        registerNoPhysicalStore("건강", "의료기기판매(임대)업");
        registerNoPhysicalStore("기타", "물류창고업체");
        registerNoPhysicalStore("기타", "민방위급수시설");
        registerNoPhysicalStore("기타", "옥외광고업");
        registerNoPhysicalStore("기타", "인쇄사");
        registerNoPhysicalStore("기타", "출판사");
        registerNoPhysicalStore("동물", "동물미용업");
        registerNoPhysicalStore("동물", "동물생산업");
        registerNoPhysicalStore("동물", "동물용의료용구판매업");
        registerNoPhysicalStore("동물", "동물운송업");
        registerNoPhysicalStore("동물", "동물위탁관리업");
        registerNoPhysicalStore("동물", "동물전시업");
        registerNoPhysicalStore("동물", "동물판매업");
        registerNoPhysicalStore("문화", "게임물배급업");
        registerNoPhysicalStore("문화", "게임물제작업");
        registerNoPhysicalStore("문화", "대중문화예술기획업");
        registerNoPhysicalStore("문화", "박물관 및 미술관");
        registerNoPhysicalStore("문화", "비디오물감상실업");
        registerNoPhysicalStore("문화", "비디오물배급업");
        registerNoPhysicalStore("문화", "비디오물제작업");
        registerNoPhysicalStore("문화", "숙박업");
        registerNoPhysicalStore("문화", "영화배급업");
        registerNoPhysicalStore("문화", "영화수입업");
        registerNoPhysicalStore("문화", "영화제작업");
        registerNoPhysicalStore("문화", "온라인음악서비스제공업");
        registerNoPhysicalStore("문화", "음반및음악영상물배급업");
        registerNoPhysicalStore("문화", "음반및음악영상물제작업");
        registerNoPhysicalStore("문화", "일반야영장업");
        registerNoPhysicalStore("생활", "대규모점포");
        registerNoPhysicalStore("생활", "방문판매업");
        registerNoPhysicalStore("생활", "썰매장업");
        registerNoPhysicalStore("생활", "전화권유판매업");
        registerNoPhysicalStore("생활", "통신판매업");
        registerNoPhysicalStore("생활", "후원방문판매업체");
        registerNoPhysicalStore("식품", "건강기능식품유통전문판매업");
        registerNoPhysicalStore("식품", "건강기능식품일반판매업");
        registerNoPhysicalStore("식품", "식품운반업");
        registerNoPhysicalStore("식품", "용기냉동기특정설비");
        registerNoPhysicalStore("식품", "축산물운반업");
        registerNoPhysicalStore("자원환경", "가축분뇨수집운반업");
        registerNoPhysicalStore("자원환경", "고압가스업");
        registerNoPhysicalStore("자원환경", "대기오염물질배출시설설치사업장");
        registerNoPhysicalStore("자원환경", "목재수입유통업");
        registerNoPhysicalStore("자원환경", "배출가스전문정비사업자(확인검사대행자)");
        registerNoPhysicalStore("자원환경", "저수조청소업");
        registerNoPhysicalStore("자원환경", "제재업");
        registerNoPhysicalStore("자원환경", "특정고압가스업");
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `cd /home/ubuntu/app-build && mvn test -Dtest=BusinessTypeRegistryTest -q`
Expected: PASS, 7 tests.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/nextstep/domain/businesstype/BusinessTypeRegistry.java \
        src/test/java/com/nextstep/domain/businesstype/BusinessTypeRegistryTest.java
git commit -m "feat: add BusinessTypeRegistry migrating NoStorefrontSubCategories + register tobacco-retail reliability and 집단급식소/위탁급식영업 pairing"
```

---

## Task 5: LocationIdentity (레코드 단위 위치 판정, unitKey() 대체)

**Files:**
- Create: `src/main/java/com/nextstep/domain/site/LocationIdentity.java`
- Test: `src/test/java/com/nextstep/domain/site/LocationIdentityTest.java`
- Read: `src/main/java/com/nextstep/domain/site/AddressDetailParser.java` (CONFIDENCE_LOW/CONFIDENCE_HIGH 상수)

**Interfaces:**
- Consumes: `AddressDetailParser.CONFIDENCE_LOW`/`CONFIDENCE_HIGH` (기존).
- Produces: `LocationIdentity.key(String parseConfidence, String parsedBuildingName, String parsedFloor, String parsedUnitNo): String`, `LocationIdentity.isUnlocated(String key): boolean`.

- [ ] **Step 1: Write the failing test**

```java
package com.nextstep.domain.site;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class LocationIdentityTest {

    @Test
    void 호실번호가_있으면_UNIT_키() {
        String key = LocationIdentity.key(AddressDetailParser.CONFIDENCE_HIGH, "우성트램타워", null, "801");
        assertThat(key).isEqualTo("UNIT::801::우성트램타워");
    }

    @Test
    void 층만_있으면_FLOOR_키() {
        String key = LocationIdentity.key(AddressDetailParser.CONFIDENCE_HIGH, "우성트램타워", "8", null);
        assertThat(key).isEqualTo("FLOOR::8::우성트램타워");
    }

    @Test
    void 건물명만_있으면_정규화된_건물명() {
        String key = LocationIdentity.key(AddressDetailParser.CONFIDENCE_HIGH, "우성트램타워", null, null);
        assertThat(key).isEqualTo("우성트램타워");
    }

    @Test
    void LOW_신뢰도면_원본_문자열_정규화() {
        String key = LocationIdentity.key(AddressDetailParser.CONFIDENCE_LOW, "  B동  8층  801~804호  ", null, null);
        assertThat(key).isEqualTo("B동 8층 801~804호");
    }

    @Test
    void LOW_신뢰도인데_건물명도_없으면_위치미특정() {
        String key = LocationIdentity.key(AddressDetailParser.CONFIDENCE_LOW, null, null, null);
        assertThat(LocationIdentity.isUnlocated(key)).isTrue();
    }

    @Test
    void HIGH_신뢰도인데_전부_null이면_위치미특정() {
        // 담배소매업처럼 상세주소 자체가 원본에 없는 케이스(AddressDetailParser.Result.none())
        String key = LocationIdentity.key(AddressDetailParser.CONFIDENCE_HIGH, null, null, null);
        assertThat(LocationIdentity.isUnlocated(key)).isTrue();
    }

    @Test
    void 위치가_특정되면_isUnlocated는_false() {
        String key = LocationIdentity.key(AddressDetailParser.CONFIDENCE_HIGH, "우성트램타워", null, "801");
        assertThat(LocationIdentity.isUnlocated(key)).isFalse();
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd /home/ubuntu/app-build && mvn test -Dtest=LocationIdentityTest -q`
Expected: FAIL — `LocationIdentity` 없음.

- [ ] **Step 3: Implement LocationIdentity**

```java
package com.nextstep.domain.site;

public final class LocationIdentity {

    private static final String UNLOCATED_KEY = "__unlocated__";

    private LocationIdentity() {
    }

    public static String key(String parseConfidence, String parsedBuildingName,
                              String parsedFloor, String parsedUnitNo) {
        if (AddressDetailParser.CONFIDENCE_LOW.equals(parseConfidence)) {
            return parsedBuildingName != null ? normalize(parsedBuildingName) : UNLOCATED_KEY;
        }
        if (parsedUnitNo != null) {
            return "UNIT::" + parsedUnitNo + "::" + parsedBuildingName;
        }
        if (parsedFloor != null) {
            return "FLOOR::" + parsedFloor + "::" + parsedBuildingName;
        }
        if (parsedBuildingName != null) {
            return normalize(parsedBuildingName);
        }
        return UNLOCATED_KEY;
    }

    public static boolean isUnlocated(String key) {
        return UNLOCATED_KEY.equals(key);
    }

    private static String normalize(String value) {
        return value.trim().replaceAll("\\s+", " ");
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `cd /home/ubuntu/app-build && mvn test -Dtest=LocationIdentityTest -q`
Expected: PASS, 7 tests.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/nextstep/domain/site/LocationIdentity.java \
        src/test/java/com/nextstep/domain/site/LocationIdentityTest.java
git commit -m "feat: add LocationIdentity for record-level unit-key resolution with explicit UNLOCATED outcome"
```

**참고 — 이번 태스크에서 안 하는 것**: 좌표(x/y) 근접 기반 보조 판정은 설계 스펙의 "비목표" 섹션대로 이번엔 구현하지 않는다(구조화 파싱 → 원본 문자열 폴백 두 단계만). `LocationIdentity.key()`가 기존 `TenancyQueryService.unitKey()`와 동일한 파티션(같은 건물명/층/호는 여전히 같은 키)을 만들되, 전부 null인 경우만 새로 `UNLOCATED_KEY`로 분리한다 — 기존 그룹핑 결과는 이 degenerate 케이스를 빼면 안 바뀐다.

---

## Task 6: RelatedLicenseGroup / RelatedLicenseGroups (일급 컬렉션)

**Files:**
- Create: `src/main/java/com/nextstep/domain/unit/RelatedLicenseGroup.java`
- Create: `src/main/java/com/nextstep/domain/unit/RelatedLicenseGroups.java`
- Test: `src/test/java/com/nextstep/domain/unit/RelatedLicenseGroupsTest.java`

**Interfaces:**
- Consumes: `BusinessTypeKey` (Task 1), `Tenancy` (기존, `com.nextstep.domain.tenancy.Tenancy`).
- Produces: `RelatedLicenseGroup(List<BusinessTypeKey> businessTypeKeys, List<Tenancy> tenancies)`, `RelatedLicenseGroups(List<RelatedLicenseGroup> groups)` with `RelatedLicenseGroups.none()`, `groupContaining(Tenancy): Optional<RelatedLicenseGroup>`.

- [ ] **Step 1: Write the failing test**

```java
package com.nextstep.domain.unit;

import com.nextstep.domain.businesstype.BusinessTypeKey;
import com.nextstep.domain.tenancy.Tenancy;
import com.nextstep.domain.tenancy.TenancyPeriod;
import org.junit.jupiter.api.Test;
import java.time.LocalDate;
import java.util.List;
import static org.assertj.core.api.Assertions.assertThat;

class RelatedLicenseGroupsTest {

    private Tenancy tenancy(long id, String businessName, String category, String subCategory) {
        return new Tenancy(id, businessName, category, subCategory, null,
            new TenancyPeriod(LocalDate.of(2020, 1, 1), null), "영업/정상", "license_only",
            com.nextstep.domain.businesstype.ReliabilitySignal.confirmed());
    }

    @Test
    void groupContaining은_해당_tenancy가_속한_그룹을_찾는다() {
        Tenancy 집단급식소 = tenancy(1L, "행복유치원", "식품", "집단급식소");
        Tenancy 위탁급식영업 = tenancy(2L, "맛있는위탁업체", "식품", "위탁급식영업");
        RelatedLicenseGroup group = new RelatedLicenseGroup(
            List.of(new BusinessTypeKey("식품", "집단급식소"), new BusinessTypeKey("식품", "위탁급식영업")),
            List.of(집단급식소, 위탁급식영업));
        RelatedLicenseGroups groups = new RelatedLicenseGroups(List.of(group));

        assertThat(groups.groupContaining(집단급식소)).contains(group);
        assertThat(groups.groupContaining(위탁급식영업)).contains(group);
    }

    @Test
    void 속하지_않은_tenancy는_empty() {
        Tenancy 무관한업체 = tenancy(3L, "무관한업체", "건강", "의원");
        assertThat(RelatedLicenseGroups.none().groupContaining(무관한업체)).isEmpty();
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd /home/ubuntu/app-build && mvn test -Dtest=RelatedLicenseGroupsTest -q`
Expected: FAIL — `RelatedLicenseGroup(s)` 없음, `Tenancy` 생성자 인자 개수도 아직 8개(9번째 `reliabilitySignal` 미추가)라 이 단계에서는 컴파일 에러가 남— Task 7에서 `Tenancy`에 필드를 추가하기 전까지는 이 테스트가 계속 컴파일 실패 상태다. **그래서 이 태스크의 Step 3에서 `RelatedLicenseGroup`/`RelatedLicenseGroups`만 만들고, Step 4(테스트 통과 확인)는 Task 7 완료 후로 미룬다** — 아래 Step 순서 참고.

- [ ] **Step 3: Implement RelatedLicenseGroup + RelatedLicenseGroups**

```java
package com.nextstep.domain.unit;

import com.nextstep.domain.businesstype.BusinessTypeKey;
import com.nextstep.domain.tenancy.Tenancy;
import java.util.List;

public record RelatedLicenseGroup(List<BusinessTypeKey> businessTypeKeys, List<Tenancy> tenancies) {
}
```

```java
package com.nextstep.domain.unit;

import com.nextstep.domain.tenancy.Tenancy;
import java.util.List;
import java.util.Optional;

public record RelatedLicenseGroups(List<RelatedLicenseGroup> groups) {

    public static RelatedLicenseGroups none() {
        return new RelatedLicenseGroups(List.of());
    }

    public Optional<RelatedLicenseGroup> groupContaining(Tenancy tenancy) {
        return groups.stream().filter(g -> g.tenancies().contains(tenancy)).findFirst();
    }
}
```

- [ ] **Step 4: Add `reliabilitySignal` field to Tenancy now (pulled forward from Task 7) so this test compiles and passes**

Modify `src/main/java/com/nextstep/domain/tenancy/Tenancy.java` — add `ReliabilitySignal reliabilitySignal` as the 9th component:

```java
package com.nextstep.domain.tenancy;

import com.nextstep.domain.businesstype.ReliabilitySignal;

public record Tenancy(
    Long id,
    String businessName,
    String category,
    String subCategory,
    String industryDetail,
    TenancyPeriod period,
    String status,
    String enrichmentSource,
    ReliabilitySignal reliabilitySignal
) {
    private static final String ACTIVE_STATUS = "영업/정상";

    public Integer survivalMonths() {
        if (isClosed() && period.closedAt() == null) {
            return null;
        }
        return period.survivalMonths();
    }

    public boolean isActive() {
        return ACTIVE_STATUS.equals(status);
    }

    public boolean isClosed() {
        return !isActive();
    }

    public String displayStatus() {
        return isActive() ? "영업" : status;
    }

    public boolean closedAtEstimated() {
        return false;
    }
}
```

이 변경으로 `Tenancy`를 직접 생성하는 다른 세 곳도 같이 고쳐야 컴파일된다(전부 `ReliabilitySignal.confirmed()`를 9번째 인자로 추가):

Modify `src/test/java/com/nextstep/domain/statistics/UnitStatisticsTest.java` 3개 헬퍼 메서드:
```java
    private Tenancy closed(LocalDate start, LocalDate end) {
        return new Tenancy(1L, "가게", "음식", "일반음식점", null,
            new TenancyPeriod(start, end), "폐업", "license_only",
            com.nextstep.domain.businesstype.ReliabilitySignal.confirmed());
    }

    private Tenancy active(LocalDate start) {
        return new Tenancy(2L, "가게2", "음식", "일반음식점", null,
            new TenancyPeriod(start, null), "영업/정상", "license_only",
            com.nextstep.domain.businesstype.ReliabilitySignal.confirmed());
    }

    private Tenancy closedWithoutEndDate(LocalDate start) {
        return new Tenancy(3L, "가게3", "생활", "통신판매업", null,
            new TenancyPeriod(start, null), "취소/말소/만료/정지/중지", "license_only",
            com.nextstep.domain.businesstype.ReliabilitySignal.confirmed());
    }
```

Modify `src/test/java/com/nextstep/domain/tenancy/TenancyTest.java` 헬퍼 메서드:
```java
    private Tenancy withStatus(String status, LocalDate licensedAt, LocalDate closedAt) {
        return new Tenancy(1L, "가게", "생활", "통신판매업", null,
            new TenancyPeriod(licensedAt, closedAt), status, "license_only",
            com.nextstep.domain.businesstype.ReliabilitySignal.confirmed());
    }
```

Modify `src/main/java/com/nextstep/application/TenancyQueryService.java:200`의 `new Tenancy(...)` 호출 — 이 파일은 Task 9에서 통째로 재작성되므로, 여기서는 **컴파일만 되게** 9번째 인자만 추가:
```java
        return new Tenancy(
            representative.getId(),
            representative.getBusinessName(),
            representative.getCategory(),
            representative.getSubCategory(),
            null,
            new TenancyPeriod(licensedAt, closedAt),
            representative.getBusinessStatus(),
            "license_only",
            com.nextstep.domain.businesstype.ReliabilitySignal.confirmed()
        );
```

- [ ] **Step 5: Run all four affected test files to verify everything compiles and passes**

Run: `cd /home/ubuntu/app-build && mvn test -Dtest=RelatedLicenseGroupsTest,UnitStatisticsTest,TenancyTest -q`
Expected: PASS, 지금까지 이 세 파일에 있던 테스트 전부(9개 + 2개 신규 = 11개 근처) + 기존 테스트 그대로.

- [ ] **Step 6: Commit**

```bash
git add src/main/java/com/nextstep/domain/unit/RelatedLicenseGroup.java \
        src/main/java/com/nextstep/domain/unit/RelatedLicenseGroups.java \
        src/main/java/com/nextstep/domain/tenancy/Tenancy.java \
        src/main/java/com/nextstep/application/TenancyQueryService.java \
        src/test/java/com/nextstep/domain/unit/RelatedLicenseGroupsTest.java \
        src/test/java/com/nextstep/domain/statistics/UnitStatisticsTest.java \
        src/test/java/com/nextstep/domain/tenancy/TenancyTest.java
git commit -m "feat: add RelatedLicenseGroup(s) and thread ReliabilitySignal through Tenancy"
```

---

## Task 7: SitePartitioner (매장/무점포 2분류, BusinessTypeRegistry 사용)

**Files:**
- Create: `src/main/java/com/nextstep/application/SitePartitioner.java`
- Test: `src/test/java/com/nextstep/application/SitePartitionerTest.java`
- Read: `src/main/java/com/nextstep/infra/persistence/LicensedBusinessRecordEntity.java` (getter 목록)

**Interfaces:**
- Consumes: `BusinessTypeRegistry` (Task 4), `LicensedBusinessRecordEntity` (기존 infra).
- Produces: `SitePartitioner(BusinessTypeRegistry registry)`, `partition(List<LicensedBusinessRecordEntity> records): Partition` where `record Partition(List<LicensedBusinessRecordEntity> storefront, List<LicensedBusinessRecordEntity> noPhysicalStore)`. 판정 기준은 기존 `TenancyQueryService.partitionByStorefront`/`hasPhysicalSignal`과 완전히 동일(businessName 단위, 무점포 후보군인데 물리적 신호가 하나라도 있으면 전부 매장으로 취급).

- [ ] **Step 1: Write the failing test**

```java
package com.nextstep.application;

import com.nextstep.domain.businesstype.BusinessTypeRegistry;
import com.nextstep.infra.persistence.LicensedBusinessRecordEntity;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.assertj.core.api.Assertions.assertThat;

class SitePartitionerTest {

    private final SitePartitioner partitioner = new SitePartitioner(new BusinessTypeRegistry());

    @Test
    void 통신판매업만_있으면_무점포로_분류된다() {
        LicensedBusinessRecordEntity record = TestFixtures.record(
            1L, "생활", "통신판매업", "온라인셀러", null, null, null, "LOW");

        var partition = partitioner.partition(List.of(record));

        assertThat(partition.storefront()).isEmpty();
        assertThat(partition.noPhysicalStore()).containsExactly(record);
    }

    @Test
    void 같은_상호가_매장업종도_있으면_전부_매장으로_취급된다() {
        LicensedBusinessRecordEntity 동물병원 = TestFixtures.record(
            1L, "동물", "동물병원", "동물병원 더 하임", null, null, null, "HIGH");
        LicensedBusinessRecordEntity 동물미용업 = TestFixtures.record(
            2L, "동물", "동물미용업", "동물병원 더 하임", null, null, null, "HIGH");

        var partition = partitioner.partition(List.of(동물병원, 동물미용업));

        assertThat(partition.storefront()).containsExactlyInAnyOrder(동물병원, 동물미용업);
        assertThat(partition.noPhysicalStore()).isEmpty();
    }

    @Test
    void 일반음식점은_매장으로_분류된다() {
        LicensedBusinessRecordEntity record = TestFixtures.record(
            1L, "식품", "일반음식점", "국밥집", null, null, null, "HIGH");

        var partition = partitioner.partition(List.of(record));

        assertThat(partition.storefront()).containsExactly(record);
    }
}
```

이 테스트가 쓰는 `TestFixtures.record(...)` 헬퍼(JPA 엔티티는 protected 기본 생성자 + setter 없는 getter-only 구조라 리플렉션 없이는 테스트에서 직접 못 만든다)를 같이 만든다:

```java
package com.nextstep.application;

import com.nextstep.infra.persistence.LicensedBusinessRecordEntity;
import java.lang.reflect.Field;
import java.time.LocalDate;

final class TestFixtures {

    private TestFixtures() {
    }

    static LicensedBusinessRecordEntity record(long id, String category, String subCategory,
                                                String businessName, String parsedBuildingName,
                                                String parsedFloor, String parsedUnitNo,
                                                String parseConfidence) {
        LicensedBusinessRecordEntity entity = newInstance();
        set(entity, "id", id);
        set(entity, "pnu", "4113110800105090000");
        set(entity, "category", category);
        set(entity, "subCategory", subCategory);
        set(entity, "businessName", businessName);
        set(entity, "businessStatus", "영업/정상");
        set(entity, "licensedAt", LocalDate.of(2020, 1, 1));
        set(entity, "jibunAddress", "경기도 성남시 수정구 테스트동 1");
        set(entity, "addressSeparated", false);
        set(entity, "parsedBuildingName", parsedBuildingName);
        set(entity, "parsedFloor", parsedFloor);
        set(entity, "parsedUnitNo", parsedUnitNo);
        set(entity, "parseConfidence", parseConfidence);
        return entity;
    }

    private static LicensedBusinessRecordEntity newInstance() {
        try {
            var ctor = LicensedBusinessRecordEntity.class.getDeclaredConstructor();
            ctor.setAccessible(true);
            return ctor.newInstance();
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
    }

    private static void set(LicensedBusinessRecordEntity entity, String field, Object value) {
        try {
            Field f = LicensedBusinessRecordEntity.class.getDeclaredField(field);
            f.setAccessible(true);
            f.set(entity, value);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd /home/ubuntu/app-build && mvn test -Dtest=SitePartitionerTest -q`
Expected: FAIL — `SitePartitioner` 없음.

- [ ] **Step 3: Implement SitePartitioner**

```java
package com.nextstep.application;

import com.nextstep.domain.businesstype.BusinessTypeRegistry;
import com.nextstep.domain.businesstype.LocationCertainty;
import com.nextstep.infra.persistence.LicensedBusinessRecordEntity;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class SitePartitioner {

    private final BusinessTypeRegistry registry;

    public SitePartitioner(BusinessTypeRegistry registry) {
        this.registry = registry;
    }

    public record Partition(List<LicensedBusinessRecordEntity> storefront,
                             List<LicensedBusinessRecordEntity> noPhysicalStore) {
    }

    public Partition partition(List<LicensedBusinessRecordEntity> records) {
        Map<String, List<LicensedBusinessRecordEntity>> byBusinessName = records.stream()
            .collect(Collectors.groupingBy(LicensedBusinessRecordEntity::getBusinessName,
                LinkedHashMap::new, Collectors.toList()));

        List<LicensedBusinessRecordEntity> storefront = new ArrayList<>();
        List<LicensedBusinessRecordEntity> noPhysicalStore = new ArrayList<>();
        for (List<LicensedBusinessRecordEntity> group : byBusinessName.values()) {
            (hasPhysicalSignal(group) ? storefront : noPhysicalStore).addAll(group);
        }
        return new Partition(storefront, noPhysicalStore);
    }

    private boolean hasPhysicalSignal(List<LicensedBusinessRecordEntity> businessRecords) {
        return businessRecords.stream().anyMatch(r ->
            registry.lookup(r.getCategory(), r.getSubCategory()).locationCertainty() != LocationCertainty.NO_PHYSICAL_STORE
                || r.getParsedFloor() != null
                || r.getParsedUnitNo() != null);
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `cd /home/ubuntu/app-build && mvn test -Dtest=SitePartitionerTest -q`
Expected: PASS, 3 tests.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/nextstep/application/SitePartitioner.java \
        src/test/java/com/nextstep/application/SitePartitionerTest.java \
        src/test/java/com/nextstep/application/TestFixtures.java
git commit -m "feat: add SitePartitioner replacing TenancyQueryService.partitionByStorefront"
```

---

## Task 8: UnitGrouper (LocationIdentity로 Unit 그룹 형성, UNLOCATED 분리, 충돌감지/재분리 포함)

**2026-07-23 보정**: 이 계획 작성(7/20) 이후 배포된 `TenancyQueryService`의 충돌감지/재분리
기능(대형 상가·시장에서 동시영업 다른 상호가 한 Unit으로 뭉치는 버그 수정, `의사결정-기록.md`
§16)을 이 태스크로 이관한다. 그 기능이 만든 `OccupancySpan`(`domain.unit`, 이미 존재·커밋됨,
수정 없음)을 그대로 쓴다 — `resolveContention`/`isContended`/`occupancySpans`/`addressTextKey`
로직을 `TenancyQueryService`에서 여기로 옮기고, 대신 `LocationIdentity.key()`(이 태스크의 원래
목표, UNLOCATED 분리)를 기반으로 동작하게 합친다.

**Files:**
- Create: `src/main/java/com/nextstep/application/UnitGrouper.java`
- Test: `src/test/java/com/nextstep/application/UnitGrouperTest.java`

**Interfaces:**
- Consumes: `LocationIdentity` (Task 5), `LicensedBusinessRecordEntity`,
  `OccupancySpan(String ownerKey, LocalDate start, LocalDate endOrNull)` +
  `OccupancySpan.overlaps(OccupancySpan)` (기존, `com.nextstep.domain.unit`, 수정 없음).
- Produces: `UnitGrouper.group(String pnu, List<LicensedBusinessRecordEntity> storefrontRecords): Grouping` where `record Grouping(List<UnitGroup> unitGroups, List<LicensedBusinessRecordEntity> unlocated)`, `record UnitGroup(String unitId, List<LicensedBusinessRecordEntity> records)`. `unitId` 포맷은 기존과 동일(`{pnu}-U{seq}`).

- [ ] **Step 1: Write the failing test**

```java
package com.nextstep.application;

import org.junit.jupiter.api.Test;
import java.time.LocalDate;
import java.util.List;
import static org.assertj.core.api.Assertions.assertThat;

class UnitGrouperTest {

    private final UnitGrouper grouper = new UnitGrouper();

    @Test
    void 같은_호실은_하나의_유닛으로_묶인다() {
        var r1 = TestFixtures.record(1L, "건강", "병원", "가게A", "우성트램타워", null, "801", "HIGH");
        var r2 = TestFixtures.record(2L, "건강", "병원", "가게B", "우성트램타워", null, "801", "HIGH");

        var grouping = grouper.group("pnu-1", List.of(r1, r2));

        assertThat(grouping.unitGroups()).hasSize(1);
        assertThat(grouping.unitGroups().get(0).unitId()).isEqualTo("pnu-1-U1");
        assertThat(grouping.unitGroups().get(0).records()).containsExactly(r1, r2);
        assertThat(grouping.unlocated()).isEmpty();
    }

    @Test
    void 다른_호실은_다른_유닛으로_분리된다() {
        var r1 = TestFixtures.record(1L, "건강", "병원", "가게A", "우성트램타워", null, "801", "HIGH");
        var r2 = TestFixtures.record(2L, "건강", "병원", "가게B", "우성트램타워", null, "802", "HIGH");

        var grouping = grouper.group("pnu-1", List.of(r1, r2));

        assertThat(grouping.unitGroups()).hasSize(2);
    }

    @Test
    void 상세주소가_전부_없으면_유닛이_아니라_UNLOCATED로_빠진다() {
        var r1 = TestFixtures.record(1L, "기타", "담배소매업", "씨유매장", null, null, null, "HIGH");
        var r2 = TestFixtures.record(2L, "건강", "병원", "정상매장", "우성트램타워", null, "801", "HIGH");

        var grouping = grouper.group("pnu-1", List.of(r1, r2));

        assertThat(grouping.unitGroups()).hasSize(1);
        assertThat(grouping.unitGroups().get(0).records()).containsExactly(r2);
        assertThat(grouping.unlocated()).containsExactly(r1);
    }

    // 2026-07-23 보정 — 아래 3개는 배포된 충돌감지/재분리 기능(의사결정-기록.md §16)을 이관하며 추가.
    // TestFixtures.record()는 모든 레코드에 동일한 jibunAddress를 쓰고 closedAt/roadAddress를
    // 세팅할 수 없어서(§8 원안 헬퍼), 이 시나리오 전용으로 TestFixtures.recordForOverlap(...)을
    // 새로 추가한다(기존 record()/Task 9가 추가하는 recordWithDates()는 그대로 둠 — 시그니처 안 건드림).

    @Test
    void 층만_겹치고_지번주소가_다르면_지번주소로_재분리된다() {
        var r1 = TestFixtures.recordForOverlap(1L, "식품", "즉석판매제조가공업", "가락족발A",
            null, "B1", null, "HIGH", LocalDate.of(2020, 1, 1), null,
            "경기도 성남시 수정구 테스트동 94-1 지하1층", "경기도 성남시 수정구 테스트로 7, 지하1층 일부호 (테스트동)");
        var r2 = TestFixtures.recordForOverlap(2L, "식품", "즉석판매제조가공업", "가락생선B",
            null, "B1", null, "HIGH", LocalDate.of(2020, 6, 1), null,
            "경기도 성남시 수정구 테스트동 94-2 지하1층", "경기도 성남시 수정구 테스트로 7, 지하1층 일부호 (테스트동)");

        var grouping = grouper.group("pnu-2", List.of(r1, r2));

        assertThat(grouping.unitGroups()).hasSize(2);
        assertThat(grouping.unlocated()).isEmpty();
    }

    @Test
    void 지번주소까지_같으면_상호명으로_재분리된다() {
        var r1 = TestFixtures.recordForOverlap(1L, "식품", "식품소분업", "가락상회C",
            null, "B1", null, "HIGH", LocalDate.of(2020, 1, 1), null,
            "경기도 성남시 수정구 테스트동 93 지하1층", "경기도 성남시 수정구 테스트로 8, 지하1층 일부호 (테스트동)");
        var r2 = TestFixtures.recordForOverlap(2L, "식품", "식품소분업", "가락상회D",
            null, "B1", null, "HIGH", LocalDate.of(2020, 6, 1), null,
            "경기도 성남시 수정구 테스트동 93 지하1층", "경기도 성남시 수정구 테스트로 8, 지하1층 일부호 (테스트동)");

        var grouping = grouper.group("pnu-3", List.of(r1, r2));

        assertThat(grouping.unitGroups()).hasSize(2);
    }

    @Test
    void 구체적_호실번호가_있으면_겹쳐도_분리하지_않는다() {
        // UNIT:: 키 그룹은 충돌감지 대상 제외 — 실제 문제 사례 전부 호실번호 없는 경우였고,
        // 있는데 겹치는 건 폐업신고 누락일 가능성이 높음(회귀: TenancyQueryServiceTest의
        // 같은_Unit이라도_businessName이_다르면_병합하지_않는다 와 동일 전제)
        var r1 = TestFixtures.recordForOverlap(1L, "서비스", "미용", "가게D-1",
            null, "4", "104", "HIGH", LocalDate.of(2020, 1, 1), null,
            "경기도 성남시 수정구 테스트동 96 4층 104호", "경기도 성남시 수정구 테스트로 4, 4층 104호 (테스트동)");
        var r2 = TestFixtures.recordForOverlap(2L, "서비스", "세탁", "가게D-2",
            null, "4", "104", "HIGH", LocalDate.of(2020, 6, 1), null,
            "경기도 성남시 수정구 테스트동 96 4층 104호", "경기도 성남시 수정구 테스트로 4, 4층 104호 (테스트동)");

        var grouping = grouper.group("pnu-4", List.of(r1, r2));

        assertThat(grouping.unitGroups()).hasSize(1);
        assertThat(grouping.unitGroups().get(0).records()).containsExactly(r1, r2);
    }
}
```

`TestFixtures`에 이 시나리오 전용 오버로드를 추가한다(기존 `record(...)`는 그대로 두고 새 메서드만 추가):

```java
    static LicensedBusinessRecordEntity recordForOverlap(long id, String category, String subCategory,
                                                           String businessName, String parsedBuildingName,
                                                           String parsedFloor, String parsedUnitNo,
                                                           String parseConfidence, LocalDate licensedAt,
                                                           LocalDate closedAt, String jibunAddress,
                                                           String roadAddress) {
        LicensedBusinessRecordEntity entity = record(id, category, subCategory, businessName,
            parsedBuildingName, parsedFloor, parsedUnitNo, parseConfidence);
        set(entity, "licensedAt", licensedAt);
        set(entity, "closedAt", closedAt);
        set(entity, "jibunAddress", jibunAddress);
        set(entity, "roadAddress", roadAddress);
        return entity;
    }
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd /home/ubuntu/app-build && mvn test -Dtest=UnitGrouperTest -q`
Expected: FAIL — `UnitGrouper`/`TestFixtures.recordForOverlap` 없음.

- [ ] **Step 3: Implement UnitGrouper**

```java
package com.nextstep.application;

import com.nextstep.domain.site.LocationIdentity;
import com.nextstep.domain.unit.OccupancySpan;
import com.nextstep.infra.persistence.LicensedBusinessRecordEntity;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class UnitGrouper {

    private static final String UNIT_ID_SEPARATOR = "-U";
    // ponytail: 초기 추정값. 실사례로 오판(과병합/과분리) 나오면 조정(TenancyMerger와 값을 맞춘다)
    private static final int SAME_BUSINESS_MERGE_GAP_DAYS = 90;

    public record UnitGroup(String unitId, List<LicensedBusinessRecordEntity> records) {
    }

    public record Grouping(List<UnitGroup> unitGroups, List<LicensedBusinessRecordEntity> unlocated) {
    }

    public Grouping group(String pnu, List<LicensedBusinessRecordEntity> storefrontRecords) {
        List<LicensedBusinessRecordEntity> located = new ArrayList<>();
        List<LicensedBusinessRecordEntity> unlocated = new ArrayList<>();
        for (LicensedBusinessRecordEntity record : storefrontRecords) {
            String key = keyOf(record);
            (LocationIdentity.isUnlocated(key) ? unlocated : located).add(record);
        }

        List<List<LicensedBusinessRecordEntity>> primaryGroups = groupByKey(located, this::keyOf);
        List<List<LicensedBusinessRecordEntity>> resolvedGroups = primaryGroups.stream()
            .flatMap(this::resolveContention)
            .toList();

        List<UnitGroup> groups = new ArrayList<>();
        int index = 1;
        for (List<LicensedBusinessRecordEntity> records : resolvedGroups) {
            groups.add(new UnitGroup(pnu + UNIT_ID_SEPARATOR + index, records));
            index++;
        }
        return new Grouping(groups, unlocated);
    }

    private String keyOf(LicensedBusinessRecordEntity record) {
        return LocationIdentity.key(record.getParseConfidence(), record.getParsedBuildingName(),
            record.getParsedFloor(), record.getParsedUnitNo());
    }

    private Stream<List<LicensedBusinessRecordEntity>> resolveContention(List<LicensedBusinessRecordEntity> group) {
        // 구체적 호실번호(UNIT:: 키)가 있는 그룹은 겹쳐도 그대로 둔다 — 실측 결과 실제 문제
        // 사례(가락시장/AK플라자/백현동/롯데백화점)는 전부 호실번호 없는 케이스였고, 있는데
        // 겹치는 경우는 대부분 폐업신고 누락으로 보는 게 더 합리적(기존 회귀 테스트도 이 전제).
        if (keyOf(group.get(0)).startsWith("UNIT::") || !isContended(group)) {
            return Stream.of(group);
        }
        List<List<LicensedBusinessRecordEntity>> byAddressText = groupByKey(group, this::addressTextKey);
        return byAddressText.stream().flatMap(subGroup -> isContended(subGroup)
            ? groupByKey(subGroup, LicensedBusinessRecordEntity::getBusinessName).stream()
            : Stream.of(subGroup));
    }

    private List<List<LicensedBusinessRecordEntity>> groupByKey(
        List<LicensedBusinessRecordEntity> records, Function<LicensedBusinessRecordEntity, String> keyFn
    ) {
        Map<String, List<LicensedBusinessRecordEntity>> byKey = records.stream()
            .collect(Collectors.groupingBy(keyFn, LinkedHashMap::new, Collectors.toList()));
        return new ArrayList<>(byKey.values());
    }

    private boolean isContended(List<LicensedBusinessRecordEntity> records) {
        Map<String, List<LicensedBusinessRecordEntity>> byBusinessName = records.stream()
            .collect(Collectors.groupingBy(LicensedBusinessRecordEntity::getBusinessName, LinkedHashMap::new, Collectors.toList()));
        if (byBusinessName.size() < 2) return false;

        List<List<OccupancySpan>> spansByName = byBusinessName.entrySet().stream()
            .map(entry -> occupancySpans(entry.getKey(), entry.getValue()))
            .toList();

        for (int i = 0; i < spansByName.size(); i++) {
            for (int j = i + 1; j < spansByName.size(); j++) {
                for (OccupancySpan a : spansByName.get(i)) {
                    for (OccupancySpan b : spansByName.get(j)) {
                        if (a.overlaps(b)) return true;
                    }
                }
            }
        }
        return false;
    }

    private List<OccupancySpan> occupancySpans(String businessName, List<LicensedBusinessRecordEntity> records) {
        return mergeByGap(records).stream()
            .map(stint -> {
                LocalDate start = stint.stream()
                    .map(LicensedBusinessRecordEntity::getLicensedAt)
                    .min(LocalDate::compareTo)
                    .orElseThrow();
                boolean anyOpen = stint.stream().anyMatch(r -> r.getClosedAt() == null);
                LocalDate end = anyOpen ? null : stint.stream()
                    .map(LicensedBusinessRecordEntity::getClosedAt)
                    .max(LocalDate::compareTo)
                    .orElseThrow();
                return new OccupancySpan(businessName, start, end);
            })
            .toList();
    }

    private List<List<LicensedBusinessRecordEntity>> mergeByGap(List<LicensedBusinessRecordEntity> records) {
        List<LicensedBusinessRecordEntity> sorted = records.stream()
            .sorted((a, b) -> a.getLicensedAt().compareTo(b.getLicensedAt()))
            .toList();

        List<List<LicensedBusinessRecordEntity>> groups = new ArrayList<>();
        List<LicensedBusinessRecordEntity> current = new ArrayList<>();
        LocalDate currentEnd = null;
        boolean currentOpen = false;

        for (LicensedBusinessRecordEntity record : sorted) {
            boolean withinGap = current.isEmpty()
                || currentOpen
                || !record.getLicensedAt().isAfter(currentEnd.plusDays(SAME_BUSINESS_MERGE_GAP_DAYS));

            if (!withinGap) {
                groups.add(current);
                current = new ArrayList<>();
                currentOpen = false;
                currentEnd = null;
            }
            current.add(record);
            if (record.getClosedAt() == null) {
                currentOpen = true;
                currentEnd = null;
            } else if (!currentOpen && (currentEnd == null || record.getClosedAt().isAfter(currentEnd))) {
                currentEnd = record.getClosedAt();
            }
        }
        if (!current.isEmpty()) groups.add(current);
        return groups;
    }

    private String addressTextKey(LicensedBusinessRecordEntity record) {
        return normalize(record.getJibunAddress()) + "::" + normalize(record.getRoadAddress());
    }

    private String normalize(String value) {
        if (value == null) return "";
        return value.trim().replaceAll("\\s+", " ");
    }
}
```

**참고**: `mergeByGap`을 여기서도 private으로 다시 두는 이유 — `occupancySpans`(충돌 판정용, 상호별
"재직 구간" 계산)와 Task 9 `TenancyMerger.mergeByGap`(실제 Tenancy 병합용)은 같은 알고리즘이지만
서로 다른 목적의 서로 다른 클래스라 공유 유틸로 뽑지 않는다(원안 설계 그대로 — 지금도 두
메서드가 완전히 동일한 코드로 각자 존재, 중복이지만 각 클래스의 응집도를 위해 의도적으로 유지).

- [ ] **Step 4: Run test to verify it passes**

Run: `cd /home/ubuntu/app-build && mvn test -Dtest=UnitGrouperTest -q`
Expected: PASS, 7 tests(원안 3개 + 이번에 추가한 4개).

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/nextstep/application/UnitGrouper.java \
        src/test/java/com/nextstep/application/UnitGrouperTest.java \
        src/test/java/com/nextstep/application/TestFixtures.java
git commit -m "feat: add UnitGrouper replacing TenancyQueryService.unitGroups/unitKey, splitting out UNLOCATED records, with contention detection/re-split cascade"
```

---

## Task 9: TenancyMerger (mergeByGap/mergedTenancies/toTenancy 이관 + reliabilitySignal 계산)

**Files:**
- Create: `src/main/java/com/nextstep/application/TenancyMerger.java`
- Test: `src/test/java/com/nextstep/application/TenancyMergerTest.java`

**Interfaces:**
- Consumes: `BusinessTypeRegistry` (Task 4), `LocationContext` (Task 2), `Tenancy`/`TenancyPeriod` (Task 6로 필드 추가된 버전).
- Produces: `TenancyMerger(BusinessTypeRegistry registry)`, `merge(List<LicensedBusinessRecordEntity> allRecordsAtSamePnu, List<LicensedBusinessRecordEntity> group): List<Tenancy>`. 첫 인자는 `reliabilitySignal` 계산용 `LocationContext`를 만드는 재료(같은 PNU의 전체 레코드), 두번째 인자는 병합 대상(하나의 Unit 또는 noPhysicalStore 그룹).

- [ ] **Step 1: Write the failing test**

```java
package com.nextstep.application;

import com.nextstep.domain.businesstype.BusinessTypeRegistry;
import com.nextstep.domain.tenancy.Tenancy;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.assertj.core.api.Assertions.assertThat;

class TenancyMergerTest {

    private final TenancyMerger merger = new TenancyMerger(new BusinessTypeRegistry());

    @Test
    void 같은_상호는_하나의_테넌시로_병합된다() {
        var r1 = TestFixtures.record(1L, "식품", "일반음식점", "국밥집", null, null, null, "HIGH");
        var r2 = TestFixtures.record(2L, "식품", "일반음식점", "국밥집", null, null, null, "HIGH");

        List<Tenancy> tenancies = merger.merge(List.of(r1, r2), List.of(r1, r2));

        assertThat(tenancies).hasSize(1);
        assertThat(tenancies.get(0).businessName()).isEqualTo("국밥집");
    }

    @Test
    void 담배소매업은_같은_자리에_더_늦은_타상호_있으면_NEEDS_VERIFICATION() {
        var 씨유 = TestFixtures.recordWithDates(1L, "기타", "담배소매업", "씨유매장",
            java.time.LocalDate.of(1999, 1, 15), null);
        var 다른가게 = TestFixtures.recordWithDates(2L, "식품", "일반음식점", "다른가게",
            java.time.LocalDate.of(2020, 1, 1), null);

        List<Tenancy> tenancies = merger.merge(List.of(씨유, 다른가게), List.of(씨유));

        assertThat(tenancies).hasSize(1);
        assertThat(tenancies.get(0).reliabilitySignal().level())
            .isEqualTo(com.nextstep.domain.businesstype.ReliabilitySignal.Level.NEEDS_VERIFICATION);
    }

    @Test
    void 일반음식점은_항상_CONFIRMED다() {
        var r1 = TestFixtures.record(1L, "식품", "일반음식점", "국밥집", null, null, null, "HIGH");

        List<Tenancy> tenancies = merger.merge(List.of(r1), List.of(r1));

        assertThat(tenancies.get(0).reliabilitySignal().level())
            .isEqualTo(com.nextstep.domain.businesstype.ReliabilitySignal.Level.CONFIRMED);
    }
}
```

`TestFixtures`에 날짜를 지정할 수 있는 오버로드를 추가한다:

```java
    static LicensedBusinessRecordEntity recordWithDates(long id, String category, String subCategory,
                                                          String businessName, LocalDate licensedAt,
                                                          LocalDate closedAt) {
        LicensedBusinessRecordEntity entity = record(id, category, subCategory, businessName,
            null, null, null, "HIGH");
        set(entity, "licensedAt", licensedAt);
        set(entity, "closedAt", closedAt);
        return entity;
    }
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd /home/ubuntu/app-build && mvn test -Dtest=TenancyMergerTest -q`
Expected: FAIL — `TenancyMerger` 없음.

- [ ] **Step 3: Implement TenancyMerger**

```java
package com.nextstep.application;

import com.nextstep.domain.businesstype.BusinessTypeRegistry;
import com.nextstep.domain.businesstype.LocationContext;
import com.nextstep.domain.tenancy.Tenancy;
import com.nextstep.domain.tenancy.TenancyPeriod;
import com.nextstep.infra.persistence.LicensedBusinessRecordEntity;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class TenancyMerger {

    // ponytail: 초기 추정값. 실사례로 오판(과병합/과분리) 나오면 조정
    private static final int SAME_BUSINESS_MERGE_GAP_DAYS = 90;

    private final BusinessTypeRegistry registry;

    public TenancyMerger(BusinessTypeRegistry registry) {
        this.registry = registry;
    }

    public List<Tenancy> merge(List<LicensedBusinessRecordEntity> allRecordsAtSamePnu,
                                List<LicensedBusinessRecordEntity> group) {
        LocationContext context = toLocationContext(allRecordsAtSamePnu);

        Map<String, List<LicensedBusinessRecordEntity>> byBusinessName = group.stream()
            .collect(Collectors.groupingBy(LicensedBusinessRecordEntity::getBusinessName,
                LinkedHashMap::new, Collectors.toList()));

        return byBusinessName.values().stream()
            .flatMap(sameNameRecords -> mergeByGap(sameNameRecords).stream())
            .map(mergedGroup -> toTenancy(mergedGroup, context))
            .sorted(Comparator.comparing(t -> t.period().licensedAt()))
            .toList();
    }

    private LocationContext toLocationContext(List<LicensedBusinessRecordEntity> records) {
        return new LocationContext(records.stream()
            .map(r -> new LocationContext.SiblingRecord(r.getBusinessName(), r.getLicensedAt()))
            .toList());
    }

    private List<List<LicensedBusinessRecordEntity>> mergeByGap(List<LicensedBusinessRecordEntity> records) {
        List<LicensedBusinessRecordEntity> sorted = records.stream()
            .sorted(Comparator.comparing(LicensedBusinessRecordEntity::getLicensedAt))
            .toList();

        List<List<LicensedBusinessRecordEntity>> groups = new ArrayList<>();
        List<LicensedBusinessRecordEntity> current = new ArrayList<>();
        LocalDate currentEnd = null;
        boolean currentOpen = false;

        for (LicensedBusinessRecordEntity record : sorted) {
            boolean withinGap = current.isEmpty()
                || currentOpen
                || !record.getLicensedAt().isAfter(currentEnd.plusDays(SAME_BUSINESS_MERGE_GAP_DAYS));

            if (!withinGap) {
                groups.add(current);
                current = new ArrayList<>();
                currentOpen = false;
                currentEnd = null;
            }
            current.add(record);
            if (record.getClosedAt() == null) {
                currentOpen = true;
                currentEnd = null;
            } else if (!currentOpen && (currentEnd == null || record.getClosedAt().isAfter(currentEnd))) {
                currentEnd = record.getClosedAt();
            }
        }
        if (!current.isEmpty()) groups.add(current);
        return groups;
    }

    private Tenancy toTenancy(List<LicensedBusinessRecordEntity> group, LocationContext context) {
        LicensedBusinessRecordEntity representative = representativeOf(group);
        LocalDate licensedAt = group.stream()
            .map(LicensedBusinessRecordEntity::getLicensedAt)
            .min(LocalDate::compareTo)
            .orElseThrow();
        boolean anyOpen = group.stream().anyMatch(r -> r.getClosedAt() == null);
        LocalDate closedAt = anyOpen ? null : group.stream()
            .map(LicensedBusinessRecordEntity::getClosedAt)
            .max(LocalDate::compareTo)
            .orElseThrow();

        var reliabilitySignal = registry.lookup(representative.getCategory(), representative.getSubCategory())
            .reliabilitySignal(representative.getBusinessName(), licensedAt, context);

        return new Tenancy(
            representative.getId(),
            representative.getBusinessName(),
            representative.getCategory(),
            representative.getSubCategory(),
            null,
            new TenancyPeriod(licensedAt, closedAt),
            representative.getBusinessStatus(),
            "license_only",
            reliabilitySignal
        );
    }

    private LicensedBusinessRecordEntity representativeOf(List<LicensedBusinessRecordEntity> group) {
        return group.stream()
            .filter(r -> r.getClosedAt() == null)
            .max(Comparator.comparing(LicensedBusinessRecordEntity::getLicensedAt))
            .orElseGet(() -> group.stream()
                .max(Comparator.comparing(LicensedBusinessRecordEntity::getClosedAt))
                .orElseThrow());
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `cd /home/ubuntu/app-build && mvn test -Dtest=TenancyMergerTest -q`
Expected: PASS, 3 tests.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/nextstep/application/TenancyMerger.java \
        src/test/java/com/nextstep/application/TenancyMergerTest.java \
        src/test/java/com/nextstep/application/TestFixtures.java
git commit -m "feat: add TenancyMerger replacing mergeByGap/mergedTenancies, wiring reliabilitySignal computation"
```

---

## Task 10: RelatedLicenseLinker

**Files:**
- Create: `src/main/java/com/nextstep/application/RelatedLicenseLinker.java`
- Test: `src/test/java/com/nextstep/application/RelatedLicenseLinkerTest.java`

**Interfaces:**
- Consumes: `BusinessTypeRegistry` (Task 4), `Tenancy`, `RelatedLicenseGroup(s)` (Task 6).
- Produces: `RelatedLicenseLinker(BusinessTypeRegistry registry)`, `link(List<Tenancy> tenanciesInOneUnit): RelatedLicenseGroups`.

- [ ] **Step 1: Write the failing test**

```java
package com.nextstep.application;

import com.nextstep.domain.businesstype.BusinessTypeRegistry;
import com.nextstep.domain.tenancy.Tenancy;
import com.nextstep.domain.tenancy.TenancyPeriod;
import com.nextstep.domain.businesstype.ReliabilitySignal;
import org.junit.jupiter.api.Test;
import java.time.LocalDate;
import java.util.List;
import static org.assertj.core.api.Assertions.assertThat;

class RelatedLicenseLinkerTest {

    private final RelatedLicenseLinker linker = new RelatedLicenseLinker(new BusinessTypeRegistry());

    private Tenancy tenancy(long id, String businessName, String category, String subCategory) {
        return new Tenancy(id, businessName, category, subCategory, null,
            new TenancyPeriod(LocalDate.of(2020, 1, 1), null), "영업/정상", "license_only",
            ReliabilitySignal.confirmed());
    }

    @Test
    void 집단급식소와_위탁급식영업은_한_그룹으로_묶인다() {
        Tenancy 집단급식소 = tenancy(1L, "행복유치원", "식품", "집단급식소");
        Tenancy 위탁급식영업 = tenancy(2L, "맛있는위탁업체", "식품", "위탁급식영업");

        var groups = linker.link(List.of(집단급식소, 위탁급식영업));

        assertThat(groups.groups()).hasSize(1);
        assertThat(groups.groups().get(0).tenancies()).containsExactlyInAnyOrder(집단급식소, 위탁급식영업);
    }

    @Test
    void 관련없는_업종끼리는_그룹이_안된다() {
        Tenancy 국밥집 = tenancy(1L, "국밥집", "식품", "일반음식점");
        Tenancy 동물병원 = tenancy(2L, "동물병원", "건강", "의원");

        var groups = linker.link(List.of(국밥집, 동물병원));

        assertThat(groups.groups()).isEmpty();
    }

    @Test
    void 짝이_하나만_있으면_그룹이_안된다() {
        Tenancy 집단급식소 = tenancy(1L, "행복유치원", "식품", "집단급식소");

        var groups = linker.link(List.of(집단급식소));

        assertThat(groups.groups()).isEmpty();
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd /home/ubuntu/app-build && mvn test -Dtest=RelatedLicenseLinkerTest -q`
Expected: FAIL — `RelatedLicenseLinker` 없음.

- [ ] **Step 3: Implement RelatedLicenseLinker**

```java
package com.nextstep.application;

import com.nextstep.domain.businesstype.BusinessType;
import com.nextstep.domain.businesstype.BusinessTypeKey;
import com.nextstep.domain.businesstype.BusinessTypeRegistry;
import com.nextstep.domain.tenancy.Tenancy;
import com.nextstep.domain.unit.RelatedLicenseGroup;
import com.nextstep.domain.unit.RelatedLicenseGroups;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public class RelatedLicenseLinker {

    private final BusinessTypeRegistry registry;

    public RelatedLicenseLinker(BusinessTypeRegistry registry) {
        this.registry = registry;
    }

    public RelatedLicenseGroups link(List<Tenancy> tenancies) {
        Set<BusinessTypeKey> keysPresent = tenancies.stream()
            .map(this::keyOf)
            .collect(Collectors.toCollection(LinkedHashSet::new));

        List<RelatedLicenseGroup> groups = new ArrayList<>();
        Set<BusinessTypeKey> consumed = new LinkedHashSet<>();

        for (BusinessTypeKey key : keysPresent) {
            if (consumed.contains(key)) continue;
            BusinessType businessType = registry.lookup(key);
            Set<BusinessTypeKey> partners = businessType.relatedTypeKeys().values().stream()
                .filter(keysPresent::contains)
                .collect(Collectors.toCollection(LinkedHashSet::new));
            if (partners.isEmpty()) continue;

            Set<BusinessTypeKey> groupKeys = new LinkedHashSet<>(partners);
            groupKeys.add(key);
            List<Tenancy> groupTenancies = tenancies.stream()
                .filter(t -> groupKeys.contains(keyOf(t)))
                .toList();
            groups.add(new RelatedLicenseGroup(List.copyOf(groupKeys), groupTenancies));
            consumed.addAll(groupKeys);
        }

        return new RelatedLicenseGroups(groups);
    }

    private BusinessTypeKey keyOf(Tenancy tenancy) {
        return new BusinessTypeKey(tenancy.category(), tenancy.subCategory());
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `cd /home/ubuntu/app-build && mvn test -Dtest=RelatedLicenseLinkerTest -q`
Expected: PASS, 3 tests.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/nextstep/application/RelatedLicenseLinker.java \
        src/test/java/com/nextstep/application/RelatedLicenseLinkerTest.java
git commit -m "feat: add RelatedLicenseLinker grouping paired business-type tenancies within a Unit"
```

---

## Task 11: Site/Unit 레코드 필드 추가 (unlocatedRegistrations, relatedLicenseGroups)

**Files:**
- Modify: `src/main/java/com/nextstep/domain/site/Site.java`
- Modify: `src/main/java/com/nextstep/domain/unit/Unit.java`

**Interfaces:**
- Produces: `Site(Pnu pnu, String jibunAddress, String roadAddress, Coordinate coordinate, List<Unit> units, List<Tenancy> noStorefrontRegistrations, List<Tenancy> unlocatedRegistrations)`, `Unit(String unitId, String label, LocationSource locationSource, List<Tenancy> tenancies, String parsedFloor, String parsedUnitNo, String parseConfidence, RelatedLicenseGroups relatedLicenseGroups)`.

이 태스크는 순수 필드 추가라 자체 유닛 테스트는 안 만든다(record 자동 생성 접근자라 테스트할 새 로직이 없음) — 컴파일이 이 태스크의 검증 기준이고, 실제 값 채우기는 Task 12(TenancyQueryService 재작성)에서 확인된다.

- [ ] **Step 1: Modify Site.java**

```java
package com.nextstep.domain.site;

import com.nextstep.domain.tenancy.Tenancy;
import com.nextstep.domain.unit.Unit;
import java.util.List;

public record Site(Pnu pnu, String jibunAddress, String roadAddress, Coordinate coordinate,
                    List<Unit> units, List<Tenancy> noStorefrontRegistrations,
                    List<Tenancy> unlocatedRegistrations) {
}
```

- [ ] **Step 2: Modify Unit.java**

```java
package com.nextstep.domain.unit;

import com.nextstep.domain.statistics.UnitStatistics;
import com.nextstep.domain.tenancy.Tenancy;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

public record Unit(String unitId, String label, LocationSource locationSource, List<Tenancy> tenancies,
                    String parsedFloor, String parsedUnitNo, String parseConfidence,
                    RelatedLicenseGroups relatedLicenseGroups) {

    public UnitStatistics statistics() {
        return UnitStatistics.from(tenancies);
    }

    public Optional<Tenancy> currentTenancy() {
        return tenancies.stream()
            .filter(Tenancy::isActive)
            .max(Comparator.comparing(t -> t.period().licensedAt()));
    }
}
```

- [ ] **Step 3: Confirm the project still compiles (test run will show remaining call-site errors to fix in Task 12)**

Run: `cd /home/ubuntu/app-build && mvn compile -q 2>&1 | tail -30`
Expected: `TenancyQueryService.java`의 `new Site(...)`/`new Unit(...)` 호출들이 인자 개수 불일치로 컴파일 에러 — 이건 정상, Task 12에서 그 파일을 통째로 재작성하며 해결한다.

- [ ] **Step 4: Commit**

```bash
git add src/main/java/com/nextstep/domain/site/Site.java src/main/java/com/nextstep/domain/unit/Unit.java
git commit -m "feat: add unlocatedRegistrations to Site and relatedLicenseGroups to Unit (compile intentionally broken until Task 12)"
```

---

## Task 12: TenancyQueryService 재작성 (오케스트레이터로 축소) + NoStorefrontSubCategories 제거

**Files:**
- Modify: `src/main/java/com/nextstep/application/TenancyQueryService.java` (전체 재작성)
- Delete: `src/main/java/com/nextstep/domain/site/NoStorefrontSubCategories.java`
- Delete: `src/test/java/com/nextstep/domain/site/NoStorefrontSubCategoriesTest.java`

**Interfaces:**
- Consumes: `SitePartitioner`, `UnitGrouper`, `TenancyMerger`, `RelatedLicenseLinker`, `BusinessTypeRegistry` (Task 4, 7~10), `RelatedLicenseGroups`(Task 6), 새 `Site`/`Unit` 생성자(Task 11).
- Produces: 기존과 동일한 public API — `searchSites(String)`, `findSiteWithUnits(String)`, `findUnitWithTenancies(String)`(반환형 `Optional<UnitWithSite>` 그대로).

`NoStorefrontSubCategories`는 이제 아무 데서도 안 쓰인다(판정이 전부 `BusinessTypeRegistry`로 이관됨) — 지우고, 그 클래스만 테스트하던 `NoStorefrontSubCategoriesTest`도 같이 지운다. **단, 그 테스트가 검증하던 값(담배소매업=매장있음, 통신판매업=매장없음, null-safe 등)은 Task 4의 `BusinessTypeRegistryTest`가 이미 동등하게 검증하고 있다** — 커버리지 손실이 아니라 이동.

- [ ] **Step 1: Delete NoStorefrontSubCategories and its test**

```bash
git rm src/main/java/com/nextstep/domain/site/NoStorefrontSubCategories.java
git rm src/test/java/com/nextstep/domain/site/NoStorefrontSubCategoriesTest.java
```

- [ ] **Step 2: Rewrite TenancyQueryService.java**

```java
package com.nextstep.application;

import com.nextstep.domain.businesstype.BusinessTypeRegistry;
import com.nextstep.domain.site.AddressQuery;
import com.nextstep.domain.site.Pnu;
import com.nextstep.domain.site.Site;
import com.nextstep.domain.tenancy.Tenancy;
import com.nextstep.domain.unit.LocationSource;
import com.nextstep.domain.unit.RelatedLicenseGroups;
import com.nextstep.domain.unit.Unit;
import com.nextstep.infra.geo.KoreanTmCoordinateConverter;
import com.nextstep.infra.persistence.LicensedBusinessRecordEntity;
import com.nextstep.infra.persistence.LicensedBusinessRecordRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@Transactional(readOnly = true)
public class TenancyQueryService {

    private static final String UNIT_ID_SEPARATOR = "-U";

    private final LicensedBusinessRecordRepository recordRepository;
    private final SitePartitioner sitePartitioner;
    private final UnitGrouper unitGrouper;
    private final TenancyMerger tenancyMerger;
    private final RelatedLicenseLinker relatedLicenseLinker;

    public TenancyQueryService(LicensedBusinessRecordRepository recordRepository) {
        this.recordRepository = recordRepository;
        BusinessTypeRegistry registry = new BusinessTypeRegistry();
        this.sitePartitioner = new SitePartitioner(registry);
        this.unitGrouper = new UnitGrouper();
        this.tenancyMerger = new TenancyMerger(registry);
        this.relatedLicenseLinker = new RelatedLicenseLinker(registry);
    }

    public List<Site> searchSites(String query) {
        // 2026-07-23 보정: 이 계획(7/20) 작성 이후 배포된 토큰 AND 매칭 검색(의사결정-기록.md
        // §15) 이관 — 원안의 단순 substring 검색은 폐기.
        AddressQuery addressQuery = AddressQuery.of(query);
        List<LicensedBusinessRecordEntity> candidates = recordRepository.searchByAddress(addressQuery.anchorToken());
        String trimmedQuery = query.trim();
        List<LicensedBusinessRecordEntity> records = candidates.stream()
            .filter(r -> trimmedQuery.equals(r.getPnu()) || addressQuery.matchesAll(r.getJibunAddress(), r.getRoadAddress()))
            .toList();
        return assembleSites(records);
    }

    public Optional<Site> findSiteWithUnits(String pnu) {
        List<LicensedBusinessRecordEntity> records = recordRepository.findByPnuOrderByLicensedAtAscIdAsc(pnu);
        if (records.isEmpty()) return Optional.empty();
        return Optional.of(toSite(records));
    }

    public record UnitWithSite(Unit unit, Site site) {
    }

    public Optional<UnitWithSite> findUnitWithTenancies(String unitId) {
        Optional<UnitReference> unitReference = parseUnitId(unitId);
        if (unitReference.isEmpty()) return Optional.empty();

        List<LicensedBusinessRecordEntity> records =
            recordRepository.findByPnuOrderByLicensedAtAscIdAsc(unitReference.get().pnu());
        if (records.isEmpty()) return Optional.empty();

        List<LicensedBusinessRecordEntity> valid = validRecords(records);
        var partition = sitePartitioner.partition(valid);
        var grouping = unitGrouper.group(unitReference.get().pnu(), partition.storefront());
        int groupIndex = unitReference.get().index() - 1;
        if (groupIndex < 0 || groupIndex >= grouping.unitGroups().size()) return Optional.empty();

        UnitGrouper.UnitGroup group = grouping.unitGroups().get(groupIndex);
        Unit unit = toUnit(group, valid);
        Site siteWithoutUnits = toSiteWithoutUnits(group.records());
        return Optional.of(new UnitWithSite(unit, siteWithoutUnits));
    }

    private List<LicensedBusinessRecordEntity> validRecords(List<LicensedBusinessRecordEntity> records) {
        return records.stream().filter(r -> r.getLicensedAt() != null).toList();
    }

    private List<Site> assembleSites(List<LicensedBusinessRecordEntity> records) {
        if (records.isEmpty()) return List.of();

        Map<String, List<LicensedBusinessRecordEntity>> recordsByPnu = records.stream()
            .collect(Collectors.groupingBy(LicensedBusinessRecordEntity::getPnu, LinkedHashMap::new, Collectors.toList()));
        return recordsByPnu.values().stream()
            .map(this::toSite)
            .toList();
    }

    private Site toSite(List<LicensedBusinessRecordEntity> records) {
        List<LicensedBusinessRecordEntity> valid = validRecords(records);
        LicensedBusinessRecordEntity representative = valid.isEmpty() ? records.get(0) : valid.get(0);
        String pnu = representative.getPnu();

        var partition = sitePartitioner.partition(valid);
        var grouping = unitGrouper.group(pnu, partition.storefront());

        List<Unit> units = grouping.unitGroups().stream()
            .map(group -> toUnit(group, valid))
            .toList();
        List<Tenancy> noStorefrontRegistrations = tenancyMerger.merge(valid, partition.noPhysicalStore());
        List<Tenancy> unlocatedRegistrations = tenancyMerger.merge(valid, grouping.unlocated());

        return new Site(new Pnu(pnu), representative.getJibunAddress(), representative.getRoadAddress(),
            KoreanTmCoordinateConverter.fromEpsg5174(representative.getOriginalX(), representative.getOriginalY())
                .orElse(null),
            units, noStorefrontRegistrations, unlocatedRegistrations);
    }

    private Site toSiteWithoutUnits(List<LicensedBusinessRecordEntity> records) {
        List<LicensedBusinessRecordEntity> valid = validRecords(records);
        LicensedBusinessRecordEntity representative = valid.isEmpty() ? records.get(0) : valid.get(0);
        return new Site(new Pnu(representative.getPnu()), representative.getJibunAddress(),
            representative.getRoadAddress(), KoreanTmCoordinateConverter
                .fromEpsg5174(representative.getOriginalX(), representative.getOriginalY())
                .orElse(null), List.of(), List.of(), List.of());
    }

    private Unit toUnit(UnitGrouper.UnitGroup group, List<LicensedBusinessRecordEntity> allRecordsAtSamePnu) {
        List<Tenancy> tenancies = tenancyMerger.merge(allRecordsAtSamePnu, group.records());
        RelatedLicenseGroups relatedLicenseGroups = relatedLicenseLinker.link(tenancies);
        LicensedBusinessRecordEntity representative = group.records().get(0);
        return new Unit(group.unitId(), unitLabel(representative), LocationSource.LICENSE, tenancies,
            representative.getParsedFloor(), representative.getParsedUnitNo(), representative.getParseConfidence(),
            relatedLicenseGroups);
    }

    private Optional<UnitReference> parseUnitId(String unitId) {
        if (unitId == null) return Optional.empty();

        int separatorIndex = unitId.lastIndexOf(UNIT_ID_SEPARATOR);
        if (separatorIndex <= 0) return Optional.empty();

        String pnu = unitId.substring(0, separatorIndex);
        String rawIndex = unitId.substring(separatorIndex + UNIT_ID_SEPARATOR.length());
        try {
            int index = Integer.parseInt(rawIndex);
            if (index < 1) return Optional.empty();
            return Optional.of(new UnitReference(pnu, index));
        } catch (NumberFormatException e) {
            return Optional.empty();
        }
    }

    private String unitLabel(LicensedBusinessRecordEntity record) {
        if (!com.nextstep.domain.site.AddressDetailParser.CONFIDENCE_HIGH.equals(record.getParseConfidence())) {
            return "단일 점포";
        }

        String floor = record.getParsedFloor();
        String unitNo = record.getParsedUnitNo();
        String buildingName = record.getParsedBuildingName();

        if (unitNo != null) {
            return (floor != null ? floor + "층 " : "") + unitNo + "호";
        }
        if (floor != null) {
            return floor + "층";
        }
        if (buildingName != null) {
            return buildingName;
        }
        return "단일(상세주소불명)";
    }

    private record UnitReference(String pnu, int index) {
    }
}
```

- [ ] **Step 3: Run the full existing TenancyQueryServiceTest to check for regressions**

Run: `cd /home/ubuntu/app-build && mvn test -Dtest=TenancyQueryServiceTest -q 2>&1 | tail -60`
Expected: 이전과 동일한 개수로 PASS. 실패하면 실패한 테스트 메서드명과 실제 vs 기대값을 확인 — 대부분은 `Site`/`Unit` 레코드 필드 추가로 인한 단순 컴파일 문제일 것(테스트가 `new Site(...)`/`new Unit(...)`을 직접 호출하지는 않으므로 로직 회귀만 걱정하면 됨).

- [ ] **Step 4: Run full test suite**

Run: `cd /home/ubuntu/app-build && mvn clean test -q 2>&1 | tail -80`
Expected: 대부분 통과. `SiteControllerTest.csv_동일_pnu의_상세주소별_물건을_리스팅한다`가 실패할 가능성이 있음(아래 참고) — 이건 Task 14에서 다룬다. 다른 실패가 있으면 원인을 여기서 먼저 해결한다.

- [ ] **Step 5: Commit**

```bash
git add -A
git commit -m "refactor: rewrite TenancyQueryService as a thin orchestrator over SitePartitioner/UnitGrouper/TenancyMerger/RelatedLicenseLinker, remove NoStorefrontSubCategories"
```

---

## Task 13: DTO 노출 (reliabilitySignal, unlocatedRegistrations, relatedLicenseGroups)

**Files:**
- Modify: `src/main/java/com/nextstep/web/dto/ApiDtos.java`
- Modify: `src/main/java/com/nextstep/application/SiteQueryService.java`

**Interfaces:**
- Produces: `ApiDtos.SiteDetailResponse`에 `List<NoStorefrontRegistrationDto> unlocatedRegistrations` 추가, `ApiDtos.TenancyDto`에 `String reliabilitySignal, String reliabilitySignalReason` 추가, `ApiDtos.UnitDetailResponse`에 `List<RelatedLicenseGroupDto> relatedLicenseGroups` 추가, 신규 `ApiDtos.RelatedLicenseGroupDto(List<String> businessNames, List<TenancyDto> tenancies)`.

- [ ] **Step 1: Modify ApiDtos.java**

```java
package com.nextstep.web.dto;

import java.time.LocalDate;
import java.util.List;

public class ApiDtos {

    public record SearchResponse(List<SiteCandidateDto> candidates) {
    }

    public record SiteCandidateDto(String pnu, String jibunAddress, String roadAddress,
                                    Double latitude, Double longitude, int unitCount, int closedCount,
                                    String currentSubCategory) {
    }

    public record SiteDetailResponse(SiteDto site, List<UnitSummaryDto> units,
                                      List<NoStorefrontRegistrationDto> noStorefrontRegistrations,
                                      List<NoStorefrontRegistrationDto> unlocatedRegistrations,
                                      DisclaimerDto disclaimer) {
    }

    public record SiteDto(String pnu, String jibunAddress, String roadAddress, Double latitude, Double longitude) {
    }

    public record UnitSummaryDto(String unitId, String label, String currentBusinessName, String currentStatus,
                                  int totalTenancyCount, int closedCount, Integer averageSurvivalMonths,
                                  String industryDetail, String locationSource,
                                  String parsedFloor, String parsedUnitNo, String parseConfidence) {
    }

    public record NoStorefrontRegistrationDto(String businessName, String category, String subCategory,
                                               LocalDate licensedAt, LocalDate closedAt, String status) {
    }

    public record UnitDetailResponse(UnitDto unit, UnitStatisticsDto statistics, List<TenancyDto> timeline,
                                      List<RelatedLicenseGroupDto> relatedLicenseGroups,
                                      DisclaimerDto disclaimer) {
    }

    public record RelatedLicenseGroupDto(List<String> businessNames, List<TenancyDto> tenancies) {
    }

    public record UnitDto(String unitId, String label, String jibunAddress, String roadAddress,
                           String parsedFloor, String parsedUnitNo, String parseConfidence) {
    }

    public record UnitStatisticsDto(int totalTenancyCount, int closedCount, Integer averageSurvivalMonths,
                                     Integer longestSurvivalMonths, Integer shortestSurvivalMonths) {
    }

    public record TenancyDto(String tenancyId, String businessName, String category, String subCategory,
                              String industryDetail, LocalDate licensedAt, LocalDate closedAt, String status,
                              Integer survivalMonths, boolean closedAtEstimated, String enrichmentSource,
                              String reliabilitySignal, String reliabilitySignalReason,
                              MarketInfoDto marketInfo) {
    }

    public record MarketInfoDto(boolean isPlaceholder, Double leaseAreaSqm, Long depositKrw, Long monthlyRentKrw,
                                 Long keyMoneyKrw, Integer dailyFloatingPopulation, Integer sameCategoryNearbyCount,
                                 Double vacancyRatePercent, LocalDate asOf,
                                 Integer totalStoreCount, List<CategoryCountDto> categoryBreakdown) {
    }

    public record CategoryCountDto(String code, String name, int count, double ratio) {
    }

    public record DisclaimerDto(LocalDate dataAsOf, String note) {
    }

    public record ErrorResponse(String error, String message) {
    }
}
```

- [ ] **Step 2: Modify SiteQueryService.java**

`search`/`toCandidateDto`는 그대로. `getSiteDetail`에 `unlocatedRegistrations` 매핑 추가, `getUnitDetail`에 `relatedLicenseGroups` 매핑 추가, `toTenancyDto`에 `reliabilitySignal` 필드 채우기:

```java
    public SiteDetailResponse getSiteDetail(String pnu) {
        Site site = tenancyQueryService.findSiteWithUnits(pnu)
            .orElseThrow(() -> new SiteNotFoundException(pnu));

        Double lat = site.coordinate() == null ? null : site.coordinate().latitude();
        Double lon = site.coordinate() == null ? null : site.coordinate().longitude();
        Map<String, String> storeDetails = lookupStoreDetails(lon, lat);

        List<UnitSummaryDto> units = site.units().stream()
            .map(unit -> toUnitSummaryDto(unit, storeDetails))
            .sorted(Comparator.comparingInt(UnitSummaryDto::closedCount).reversed())
            .toList();

        List<NoStorefrontRegistrationDto> noStorefrontRegistrations = site.noStorefrontRegistrations().stream()
            .map(this::toNoStorefrontRegistrationDto)
            .toList();
        List<NoStorefrontRegistrationDto> unlocatedRegistrations = site.unlocatedRegistrations().stream()
            .map(this::toNoStorefrontRegistrationDto)
            .toList();

        return new SiteDetailResponse(toSiteDto(site), units, noStorefrontRegistrations,
            unlocatedRegistrations, disclaimer());
    }

    public UnitDetailResponse getUnitDetail(String unitId) {
        var unitWithSite = tenancyQueryService.findUnitWithTenancies(unitId)
            .orElseThrow(() -> new UnitNotFoundException(unitId));
        Unit unit = unitWithSite.unit();
        Site site = unitWithSite.site();

        String representativeSubCategory = unit.tenancies().stream()
            .max(Comparator.comparing(t -> t.period().licensedAt()))
            .map(Tenancy::subCategory)
            .orElse(null);
        Double lat = site.coordinate() == null ? null : site.coordinate().latitude();
        Double lon = site.coordinate() == null ? null : site.coordinate().longitude();

        var marketInfo = marketInfoService.fetch(site.pnu().value(), lon, lat, representativeSubCategory);
        Map<String, String> storeDetails = lookupStoreDetails(lon, lat);

        List<TenancyDto> timeline = unit.tenancies().stream()
            .map(t -> toTenancyDto(t, marketInfo, storeDetails))
            .toList();
        Map<Tenancy, TenancyDto> tenancyDtoByTenancy = new java.util.IdentityHashMap<>();
        for (int i = 0; i < unit.tenancies().size(); i++) {
            tenancyDtoByTenancy.put(unit.tenancies().get(i), timeline.get(i));
        }
        List<RelatedLicenseGroupDto> relatedLicenseGroups = unit.relatedLicenseGroups().groups().stream()
            .map(g -> new RelatedLicenseGroupDto(
                g.tenancies().stream().map(Tenancy::businessName).distinct().toList(),
                g.tenancies().stream().map(tenancyDtoByTenancy::get).toList()))
            .toList();

        UnitDto unitDto = new UnitDto(unit.unitId(), unit.label(), site.jibunAddress(), site.roadAddress(),
            unit.parsedFloor(), unit.parsedUnitNo(), unit.parseConfidence());
        UnitStatisticsDto statisticsDto = toStatisticsDto(unit);

        return new UnitDetailResponse(unitDto, statisticsDto, timeline, relatedLicenseGroups, disclaimer());
    }
```

`toTenancyDto`에 `reliabilitySignal` 필드 두 개 추가:

```java
    private TenancyDto toTenancyDto(Tenancy tenancy, MarketInfo marketInfo, Map<String, String> storeDetails) {
        List<CategoryCountDto> categoryBreakdown = marketInfo.categoryBreakdown().stream()
            .map(c -> new CategoryCountDto(c.code(), c.name(), c.count(), c.ratio()))
            .toList();
        MarketInfoDto marketInfoDto = new MarketInfoDto(marketInfo.isPlaceholder(), MarketInfo.LEASE_AREA_SQM,
            MarketInfo.DEPOSIT_KRW, MarketInfo.MONTHLY_RENT_KRW, MarketInfo.KEY_MONEY_KRW,
            MarketInfo.DAILY_FLOATING_POPULATION, marketInfo.sameCategoryNearbyCount(),
            MarketInfo.VACANCY_RATE_PERCENT, marketInfo.asOf(),
            marketInfo.totalStoreCount(), categoryBreakdown);

        String industryDetail = tenancy.isActive() ? lookupIndustryDetail(tenancy.businessName(), storeDetails) : null;
        String enrichmentSource = industryDetail != null ? "sangga_api" : tenancy.enrichmentSource();

        return new TenancyDto("t-" + tenancy.id(), tenancy.businessName(), tenancy.category(), tenancy.subCategory(),
            industryDetail, tenancy.period().licensedAt(), tenancy.period().closedAt(),
            tenancy.displayStatus(), tenancy.survivalMonths(), tenancy.closedAtEstimated(),
            enrichmentSource, tenancy.reliabilitySignal().level().name(), tenancy.reliabilitySignal().reason(),
            marketInfoDto);
    }
```

필요한 import 추가: `import com.nextstep.web.dto.ApiDtos.RelatedLicenseGroupDto;`는 이미 `import com.nextstep.web.dto.ApiDtos.*;`로 커버됨(기존 파일 확인).

- [ ] **Step 3: Run SiteQueryService-relevant tests**

Run: `cd /home/ubuntu/app-build && mvn test -Dtest=SiteControllerTest -q 2>&1 | tail -80`
Expected: 대부분 통과. `무점포업종은_units와_분리된_배열로_응답한다` 등 `noStorefrontRegistrations` 관련 테스트는 그대로 통과해야 함(그 DTO 자체는 안 바뀜, 새 필드가 옆에 추가됐을 뿐).

- [ ] **Step 4: Commit**

```bash
git add src/main/java/com/nextstep/web/dto/ApiDtos.java src/main/java/com/nextstep/application/SiteQueryService.java
git commit -m "feat: expose reliabilitySignal, unlocatedRegistrations, relatedLicenseGroups in API DTOs"
```

---

## Task 14: 실데이터 기반 통합 테스트 + 전체 회귀 확인

**Files:**
- Modify: `src/test/java/com/nextstep/web/SiteControllerTest.java` (신규 케이스 2개 추가 + 기존 카운트 실측 갱신)

**Interfaces:**
- 없음(순수 테스트 추가/보정 태스크).

- [ ] **Step 1: Add a 집단급식소/위탁급식영업 related-license fixture test**

`SiteControllerTest.java`에 상수와 테스트 메서드 추가:

```java
    private static final String RELATED_LICENSE_PNU = "4113110100100960003";
    private static final String DELETE_RELATED_LICENSE_PNU =
        "DELETE FROM licensed_business_record WHERE pnu = '" + RELATED_LICENSE_PNU + "'";
    private static final String INSERT_COLLECTIVE_KITCHEN =
        "INSERT INTO licensed_business_record "
            + "(id, pnu, category, sub_category, license_no, business_name, business_type, business_status, "
            + "status_detail_code, status_detail, licensed_at, closed_at, road_address, jibun_address, "
            + "address_separated, address_corrected, local_gov_code, original_x, original_y) "
            + "VALUES (9900000801, '4113110100100960003', '식품', '집단급식소', 'test-license-50', '행복유치원', "
            + "NULL, '영업/정상', '0000', '정상', '2020-01-01', NULL, "
            + "'경기도 성남시 수정구 테스트로 8 (테스트동)', '경기도 성남시 수정구 테스트동 98', "
            + "FALSE, TRUE, '3780000', 212818.475436898, 438579.588327304)";
    private static final String INSERT_CATERING_SERVICE =
        "INSERT INTO licensed_business_record "
            + "(id, pnu, category, sub_category, license_no, business_name, business_type, business_status, "
            + "status_detail_code, status_detail, licensed_at, closed_at, road_address, jibun_address, "
            + "address_separated, address_corrected, local_gov_code, original_x, original_y) "
            + "VALUES (9900000802, '4113110100100960003', '식품', '위탁급식영업', 'test-license-51', '맛있는위탁업체', "
            + "NULL, '영업/정상', '0000', '정상', '2020-01-01', NULL, "
            + "'경기도 성남시 수정구 테스트로 8 (테스트동)', '경기도 성남시 수정구 테스트동 98', "
            + "FALSE, TRUE, '3780000', 212818.475436898, 438579.588327304)";

    @Test
    @Sql(statements = {DELETE_RELATED_LICENSE_PNU, INSERT_COLLECTIVE_KITCHEN, INSERT_CATERING_SERVICE})
    @Sql(statements = DELETE_RELATED_LICENSE_PNU, executionPhase = Sql.ExecutionPhase.AFTER_TEST_METHOD)
    void 집단급식소와_위탁급식영업은_관련인허가로_묶인다() throws Exception {
        mockMvc.perform(get("/api/sites/" + RELATED_LICENSE_PNU))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.units", org.hamcrest.Matchers.hasSize(1)));

        mockMvc.perform(get("/api/units/" + RELATED_LICENSE_PNU + "-U1"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.timeline", org.hamcrest.Matchers.hasSize(2)))
            .andExpect(jsonPath("$.relatedLicenseGroups", org.hamcrest.Matchers.hasSize(1)))
            .andExpect(jsonPath("$.relatedLicenseGroups[0].businessNames",
                org.hamcrest.Matchers.containsInAnyOrder("행복유치원", "맛있는위탁업체")));
    }
```

- [ ] **Step 2: Add a 담배소매업 unlocated + reliability fixture test**

```java
    private static final String TOBACCO_PNU = "4113110100100960004";
    private static final String DELETE_TOBACCO_PNU =
        "DELETE FROM licensed_business_record WHERE pnu = '" + TOBACCO_PNU + "'";
    private static final String INSERT_TOBACCO_NO_ADDRESS =
        "INSERT INTO licensed_business_record "
            + "(id, pnu, category, sub_category, license_no, business_name, business_type, business_status, "
            + "status_detail_code, status_detail, licensed_at, closed_at, road_address, jibun_address, "
            + "address_separated, address_corrected, local_gov_code, original_x, original_y) "
            + "VALUES (9900000901, '4113110100100960004', '기타', '담배소매업', 'test-license-60', '씨유테스트점', "
            + "NULL, '영업/정상', '0000', '정상', '1999-01-15', NULL, "
            + "'경기도 성남시 수정구 테스트로 9 (테스트동)', '경기도 성남시 수정구 테스트동 96', "
            + "FALSE, TRUE, '3780000', 212818.475436898, 438579.588327304)";
    private static final String INSERT_LATER_OTHER_BUSINESS =
        "INSERT INTO licensed_business_record "
            + "(id, pnu, category, sub_category, license_no, business_name, business_type, business_status, "
            + "status_detail_code, status_detail, licensed_at, closed_at, road_address, jibun_address, "
            + "address_separated, address_corrected, local_gov_code, original_x, original_y) "
            + "VALUES (9900000902, '4113110100100960004', '식품', '일반음식점', 'test-license-61', '나중에온가게', "
            + "NULL, '영업/정상', '0000', '정상', '2020-01-01', NULL, "
            + "'경기도 성남시 수정구 테스트로 9-1 (테스트동)', '경기도 성남시 수정구 테스트동 96-1', "
            + "FALSE, TRUE, '3780000', 212818.475436898, 438579.588327304)";

    @Test
    @Sql(statements = {DELETE_TOBACCO_PNU, INSERT_TOBACCO_NO_ADDRESS, INSERT_LATER_OTHER_BUSINESS})
    @Sql(statements = DELETE_TOBACCO_PNU, executionPhase = Sql.ExecutionPhase.AFTER_TEST_METHOD)
    void 상세주소없는_담배소매업은_unlocatedRegistrations로_빠지고_확인필요_신호가_붙는다() throws Exception {
        mockMvc.perform(get("/api/sites/" + TOBACCO_PNU))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.units", org.hamcrest.Matchers.hasSize(1)))
            .andExpect(jsonPath("$.units[0].currentBusinessName").value("나중에온가게"))
            .andExpect(jsonPath("$.unlocatedRegistrations", org.hamcrest.Matchers.hasSize(1)))
            .andExpect(jsonPath("$.unlocatedRegistrations[0].businessName").value("씨유테스트점"));
    }
```

- [ ] **Step 3: Run the two new tests**

Run: `cd /home/ubuntu/app-build && mvn test -Dtest=SiteControllerTest#집단급식소와_위탁급식영업은_관련인허가로_묶인다+SiteControllerTest#상세주소없는_담배소매업은_unlocatedRegistrations로_빠지고_확인필요_신호가_붙는다 -q`
Expected: PASS, 2 tests. reliabilitySignal 자체는 `unlocatedRegistrations`가 `NoStorefrontRegistrationDto`(reliabilitySignal 필드 없음)로 노출되므로 이 통합 테스트에서는 "빠졌다"까지만 확인 — `reliabilitySignal` 값 검증은 이미 Task 9의 `TenancyMergerTest`가 단위 테스트로 커버함.

- [ ] **Step 4: Run the full suite and fix the one known count-drift test**

Run: `cd /home/ubuntu/app-build && mvn clean test -q 2>&1 | tail -100`

`csv_동일_pnu의_상세주소별_물건을_리스팅한다`가 실패하면(캐치올 Unit이 `unlocatedRegistrations`로 빠지면서 `units` 개수 또는 `totalTenancyCount` hasItem(5) 단언이 안 맞을 가능성) — 이 테스트의 기존 주석(`2026-07-18 businessName 단위 무점포업종 분리... 21 -> 5로 감소`)과 똑같은 방식으로 처리한다:

1. 실패 메시지에 찍힌 실제 `$.units` 크기와 `totalTenancyCount` 값들을 확인.
2. `hasSize(30)` → 실측된 새 값으로, `hasItem(5)` → 캐치올 레코드들이 `unlocatedRegistrations`로 빠졌다면 이 단언 자체를 제거(더 이상 캐치올 Unit이 없을 것이므로).
3. 테스트 위에 갱신 이유를 기존 관례대로 주석으로 남긴다: 예)
   ```java
   // 2026-07-20: LocationIdentity가 상세주소 전무 레코드를 UNLOCATED로 분리하며 캐치올 Unit이
   // 사라짐(29 -> 실측값). 해당 레코드들은 unlocatedRegistrations로 이동.
   ```
4. 다른 실패가 있으면(예상 밖의 것) 여기서 원인을 찾아 고친다 — 이 태스크를 완료로 표시하지 않는다.

- [ ] **Step 5: Final full regression run**

Run: `cd /home/ubuntu/app-build && mvn clean test -q 2>&1 | tail -20`
Expected: `BUILD SUCCESS`, 기존 79개 + 이번에 추가된 신규 테스트(약 30개) 전부 PASS.

- [ ] **Step 6: Commit**

```bash
git add src/test/java/com/nextstep/web/SiteControllerTest.java
git commit -m "test: add integration coverage for related-license pairing and unlocated tobacco-retail records, update csv fixture counts after UNLOCATED extraction"
```

- [ ] **Step 7: Push**

```bash
git push origin main
```

CI가 `mvn test`(테스트 job)를 돌리고, main이므로 deploy job도 트리거된다 — MySQL 스키마 변경이 없는 순수 애플리케이션 계층 리팩터링이라 별도 데이터 재적재는 불필요. 배포 후 `curl -G https://turbom.duckdns.org/api/sites/search --data-urlencode 'query=성남'`로 헬스체크.

---

## Task 15: turbom-spec 캐노니컬 문서를 구현 완료 상태로 갱신

**Files (turbom-spec 저장소, `/home/ubuntu/turbom-spec` — 없으면 `git clone https://github.com/nn98/turbom-spec.git`):**
- Modify: `backend-spec.md` §3.1(무점포업종 분리 서술)
- Modify: `의사결정-기록.md` §11(상태를 "구현 대기"→"완료"로)
- Modify: `CHANGELOG.md`(다음 번호 항목 추가)
- Create: `archive/YYYY-MM-DD/*.before-businesstype-impl`(수정 전 스냅샷, 저장소 관례)

**Interfaces:** 없음(문서만, 코드 변경 없음). Task 14 완료 후, 실제 커밋 해시가 나온 뒤 진행한다.

**왜 이 태스크가 필요한가**: Task 12에서 `NoStorefrontSubCategories`를 지우고 `BusinessTypeRegistry`로
대체하는데, `backend-spec.md` §3.1은 지금 `NoStorefrontSubCategories.isNoStorefront(category,
subCategory)`를 캐노니컬 도메인 모델로 서술하고 있다. 코드가 바뀐 뒤 이 문서를 안 고치면, 존재하지
않는 클래스를 캐노니컬이라고 가리키는 채로 방치되는 셈이다 — 2026-07-20 전체 스윕에서 바로 이런
종류의 방치(코드는 바뀌었는데 캐노니컬 문서는 안 바뀜)가 여러 건 발견돼 고친 직후라, 이번엔 같은
실수를 반복하지 않는다.

- [ ] **Step 1: 스냅샷**

```bash
cd /home/ubuntu/turbom-spec && git pull -q
mkdir -p archive/$(date +%F)
cp backend-spec.md archive/$(date +%F)/backend-spec.md.before-businesstype-impl
cp 의사결정-기록.md archive/$(date +%F)/의사결정-기록.md.before-businesstype-impl
cp CHANGELOG.md archive/$(date +%F)/CHANGELOG.md.before-businesstype-impl
```

- [ ] **Step 2: `backend-spec.md` §3.1의 "무점포업종 분리" 절을 실제 구현 기준으로 교체**

기존 절(`NoStorefrontSubCategories.isNoStorefront(category, subCategory)`가 47개 목록을 정적으로
관리한다는 서술)을 아래로 교체한다 — 47개 목록 자체와 의미는 안 바뀌었으니 그 설명은 유지하고,
클래스명과 "위치미특정"(신규 축, §9 근거) 부분만 추가:

```markdown
#### 업종 분류(`BusinessType`)와 위치미특정 (2026-07-20, `NoStorefrontSubCategories`에서 재설계)

인허가 원본은 (category, subCategory)별로 "층/호 정보 없음" 비율이 실측 기준 뚜렷하게 이분됨 —
통신판매업·방문판매업·전화권유판매업·의료기기판매(임대)업 등 47개 (category, subCategory) 조합은
90% 이상이 상세주소 없음(업종 특성상 자가/사무실 주소로 신고 가능, 물리적 점포 개념이 약함).
정상 매장업종(일반음식점 20.4%, 휴게음식점 17.5% 등)은 이 비율이 낮고 순수 개별 데이터 누락임.

`BusinessTypeRegistry.lookup(category, subCategory).locationCertainty()`가 이 47개 목록을 `LOCATED`/
`NO_PHYSICAL_STORE` 두 값으로 관리(구 `NoStorefrontSubCategories.isNoStorefront`와 값·의미 동일,
클래스만 이관). 판정은 **레코드 단위가 아니라 같은 PNU 내 같은 businessName 단위** — 한 상호명이
무점포 후보 업종과 정상 매장업종(또는 실제 층/호 정보가 있는 레코드) 라이선스를 동시에 갖고 있으면
그 상호명의 레코드 전부를 매장으로 취급한다(`SitePartitioner`). 상호명 전체가 무점포 후보 업종이면서
층/호 정보도 전혀 없을 때만 `Site.noStorefrontRegistrations`로 분류, Unit 그룹핑 자체를 안 거침.

**위치미특정(`unlocatedRegistrations`, 신규)**: 담배소매업(84.5%가 층/호 정보 없음, 90% 임계값
미달이라 위 무점포 목록엔 없음)처럼 카테고리 자체는 매장이 있는데 개별 레코드에 상세주소가 전혀
없는 경우가 있다 — 이건 업종 속성이 아니라 **레코드 단위 데이터 완비 여부**라, `LocationIdentity`가
매장/무점포 판정과 별개로 판단한다(`UnitGrouper`). 상세: `의사결정-기록.md` §9·§11,
`server/docs/superpowers/specs/2026-07-20-business-type-domain-design.md`.

**관련 인허가 페어링(신규)**: 집단급식소/위탁급식영업처럼 같은 물건에서 서로 다른 인허가로 뜨는
경우, `BusinessTypeRegistry`가 두 (category, subCategory)를 서로의 `relatedTypeKeys`로 등록해두면
같은 Unit 안에서 `RelatedLicenseLinker`가 자동으로 묶는다(`Unit.relatedLicenseGroups`).

**상태 신뢰도(신규)**: 담배소매업은 담배사업법상 거리제한(50~100m) 때문에 폐업신고를 미루는
"알박기"와, 세무서 폐업신고와 지자체 담배소매인 폐업신고 이원화로 인한 누락이 흔하고, 별개로
인허가 자체가 상호 변경과 무관하게 승계되는 경우도 있어(§9) `licensed_at`이 실제 개업일보다 훨씬
이를 수 있다. `BusinessType.reliabilitySignal(businessName, licensedAt, context)`가 같은 자리에
이 레코드보다 늦게 시작한 다른 상호가 있으면 `NEEDS_VERIFICATION`을 반환 — API 응답의
`timeline[].reliabilitySignal`/`reliabilitySignalReason`으로 노출(참고 신호, 정밀 판정 아님).
```

- [ ] **Step 3: `의사결정-기록.md` §11 상태 갱신**

§11("업종(BusinessType) 도메인 재설계 — 설계 확정, 구현 대기") 맨 끝의 **상태** 문단을 교체:

**2026-07-23 보정 — §16도 같이 갱신**: 이 계획 작성(7/20) 이후 배포된 대형 상가/시장 Unit
충돌감지/재분리 기능(`의사결정-기록.md` §16)이 "알려진 한계"로 남겨뒀던 항목 — 집단급식소/
위탁급식영업 관련인허가 페어링이 상호명 기준 재분리로 갈라지는 문제 — 이 태스크(Task 10
`RelatedLicenseLinker`)로 실제 해결된다. §16의 "알려진 한계" 문단에 "§11 구현으로 해결됨,
`RelatedLicenseLinker`가 처리" 한 줄을 추가해서 갱신한다(§16 자체를 삭제하거나 재작성하지
않음 — 왜 그 시점엔 한계였는지의 기록 가치는 남긴다).

```markdown
**상태**: 완료, 운영 반영됨. 커밋: `turbom-server` `<Task 14 Step 6/7 실제 커밋 해시로 교체>`.
구현 중 설계에서 벗어난 점이 있으면 여기 기록(예: `csv_동일_pnu의_상세주소별_물건을_리스팅한다`
테스트의 실측 카운트 갱신값 등). 상세: `backend-spec.md` §3.1,
`server/docs/superpowers/plans/2026-07-20-business-type-domain.md`.
```

- [ ] **Step 4: `CHANGELOG.md`에 항목 추가**

`## 이력` 맨 위(최신)에 다음 번호(21차 다음이면 22차)로 추가:

```markdown
### 2026-07-20 (22차) — 업종(BusinessType) 도메인 재설계 구현 완료 (`turbom-server`)

`NoStorefrontSubCategories`(정적 Set) + `TenancyQueryService`의 절차적 로직을 `BusinessType`
도메인 개념(`domain.businesstype`) + 협력자(`SitePartitioner`/`UnitGrouper`/`TenancyMerger`/
`RelatedLicenseLinker`)로 재편. 집단급식소/위탁급식영업 관련 인허가 페어링, 담배소매업처럼
매장은 있지만 상세주소가 없는 레코드의 위치미특정 분리(레코드 단위 판정, §9 반영),
businessStatus/licensed_at 신뢰도 신호(`reliabilitySignal`)를 신규 지원. 기존 79개 테스트 +
신규 테스트 전부 통과. 커밋: `turbom-server` `<실제 커밋 해시>`. 상세: `의사결정-기록.md` §11,
`backend-spec.md` §3.1.
```

- [ ] **Step 5: 커밋+push**

```bash
cd /home/ubuntu/turbom-spec
git add -A
git commit -m "docs: mark BusinessType domain redesign as implemented, sync backend-spec.md §3.1"
git push
```

---

## 스펙 대비 커버리지 체크

- 집단급식소/위탁급식영업 페어링 → Task 4(레지스트리 등록) + Task 10(RelatedLicenseLinker) + Task 14(통합 테스트). ✅
- 위치미특정 레코드(레코드 단위, §9 반영) → Task 5(LocationIdentity) + Task 8(UnitGrouper) + Task 14(통합 테스트, 상세주소 있는/없는 담배소매업 둘 다 커버는 Task 4의 `담배소매업은_매장있음으로_등록된다` + Task 8의 `상세주소가_전부_없으면...` 테스트로 분담). ✅
- 상태 신뢰도(폐업신고 누락 + 인허가 승계) → Task 3(StandardBusinessType.reliabilitySignal) + Task 4(담배소매업 peerBasedReliability 등록) + Task 9(TenancyMerger가 실제 계산해 Tenancy에 싣기) + Task 13(DTO 노출). ✅
- 일급 컬렉션(RelatedTypeKeys, LocationContext, RelatedLicenseGroups) → Task 1, 2, 6. ✅
- domain 순수성(JPA 비의존) → Task 2/5에서 명시적으로 엔티티 대신 원시값/도메인 값객체 사용. ✅
- NoStorefrontSubCategories 값 이관(행동 불변) → Task 4에서 47개 항목 그대로 복사 + 회귀 테스트. ✅
- 기존 79개 테스트 회귀 방지 → Task 12/13/14에서 단계별 확인, 알려진 드리프트(csv 픽스처 카운트)는 명시적 처리 절차 제공. ✅
- 캐노니컬 문서(turbom-spec) 동기화 → Task 15(구현 완료 후 `backend-spec.md`/`의사결정-기록.md`/`CHANGELOG.md` 갱신). 2026-07-20 전체 문서 스윕에서 나온 교훈 반영. ✅
- 비목표(좌표 기반 퍼지매칭, 3자 이상 그룹, 불필요 일급컬렉션화) → 전부 구현 안 함, Task 5/6 주석에 명시. ✅
