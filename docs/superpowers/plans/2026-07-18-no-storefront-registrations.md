# 무점포/자가신고형 업종 분리 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 통신판매업 등 90% 이상 상세주소가 없는 47개 업종을 `Site.units`(물리적 자리)에서 분리해 `Site.noStorefrontRegistrations`로 노출한다. 판정은 레코드 단위가 아니라 같은 PNU 내 같은 businessName 단위 — 상호명 하나가 물리적 신호(무점포 목록 밖 업종이거나 실제 층/호 정보 보유) 있는 레코드를 하나라도 가지면 그 상호명의 레코드 전부를 매장으로 취급한다.

**Architecture:** 새 정적 분류 클래스(`NoStorefrontSubCategories`, `domain.site` 패키지, 기존 `IndustryCategoryMapper` 패턴과 동일)를 추가하고, `TenancyQueryService.toSite()`/`toSiteWithoutUnits()`에서 Unit 그룹핑 전에 businessName 단위로 storefront/noStorefront를 나눈다. `Site` 레코드에 필드 추가, API 계약(`SiteDetailResponse`)에 신규 배열 추가. 기존 `mergedTenancies()`(businessName+gap 90일 병합)를 noStorefront 쪽에도 그대로 재사용.

**Tech Stack:** Java 21, Spring Boot 3.x, JUnit 5 + Mockito/AssertJ, `@SpringBootTest`(H2 seed 데이터 기반, 기존 `TenancyQueryServiceTest.java`/`SiteControllerTest.java` 패턴과 동일)

## Global Constraints

- 분류 목록은 `spec/backend-spec.md` §3.1(2026-07-18 추가분)에 문서화된 47개 (category, subCategory) 쌍 — 90% 임계값 기계적 산출, 아래 Task 1에 전체 목록 포함
- 판정 단위는 **같은 PNU 내 같은 businessName** — 레코드 단위 아님(설계 문서 "데이터 흐름" 절 참고, 최초 레코드-단위 설계는 `동물병원 더 하임` 실사례로 기각됨)
- API 계약: `GET /api/sites/{pnu}` 응답에 `noStorefrontRegistrations[]` 신규 배열(`units[]`와 별도, 배타적). `unitId`/`marketInfo`/통계 없음
- `units[]`, `SiteCandidateDto`(검색결과 unitCount 등)는 코드 변경 없이 자동으로 무점포 업종이 빠진 값이 됨(이미 `site.units()` 기준으로 계산되므로)
- 테스트: `mvn test` 전체 스위트 통과 유지. **단, 이 변경으로 기존 하드코딩된 개수 assertion 다수가 legitimate하게 바뀔 것으로 예상됨** — 아래 Task 3 참고

---

## File Map

| 파일 | 역할 |
|---|---|
| `src/main/java/com/nextstep/domain/site/NoStorefrontSubCategories.java` | **신규** — 47개 분류 목록 + 판별 메서드 |
| `src/main/java/com/nextstep/domain/site/Site.java` | **수정** — `noStorefrontRegistrations` 필드 추가 |
| `src/main/java/com/nextstep/application/TenancyQueryService.java` | **수정** — businessName 단위 분리 로직 |
| `src/main/java/com/nextstep/web/dto/ApiDtos.java` | **수정** — `NoStorefrontRegistrationDto`, `SiteDetailResponse` 필드 추가 |
| `src/main/java/com/nextstep/application/SiteQueryService.java` | **수정** — DTO 변환 로직 |
| `src/test/java/com/nextstep/domain/site/NoStorefrontSubCategoriesTest.java` | **신규** |
| `src/test/java/com/nextstep/application/TenancyQueryServiceTest.java` | **수정** — 신규 테스트 + 기존 assertion 갱신 |
| `src/test/java/com/nextstep/web/SiteControllerTest.java` | **수정** — 신규 테스트 + 기존 assertion 갱신 |

---

### Task 1: 무점포 업종 분류 (`NoStorefrontSubCategories`)

**Files:**
- Create: `src/main/java/com/nextstep/domain/site/NoStorefrontSubCategories.java`
- Create: `src/test/java/com/nextstep/domain/site/NoStorefrontSubCategoriesTest.java`

**Interfaces:**
- Produces: `NoStorefrontSubCategories.isNoStorefront(String category, String subCategory) -> boolean`. Task 2가 이 메서드를 쓴다.

- [ ] **Step 1: 실패하는 테스트 먼저**

`src/test/java/com/nextstep/domain/site/NoStorefrontSubCategoriesTest.java`:

```java
package com.nextstep.domain.site;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class NoStorefrontSubCategoriesTest {

    @Test
    void 통신판매업은_무점포로_분류된다() {
        assertThat(NoStorefrontSubCategories.isNoStorefront("생활", "통신판매업")).isTrue();
    }

    @Test
    void 방문판매업은_무점포로_분류된다() {
        assertThat(NoStorefrontSubCategories.isNoStorefront("생활", "방문판매업")).isTrue();
    }

    @Test
    void 일반음식점은_무점포가_아니다() {
        assertThat(NoStorefrontSubCategories.isNoStorefront("식품", "일반음식점")).isFalse();
    }

    @Test
    void 담배소매업은_무점포가_아니다() {
        // 84.5%로 90% 임계값 미만 — 편의점 등에 붙는 부가허가라 실제 매장 있음(기존 도메인 지식과 일치)
        assertThat(NoStorefrontSubCategories.isNoStorefront("기타", "담배소매업")).isFalse();
    }

    @Test
    void 동물병원은_무점포가_아니다() {
        // 동물병원(위생/시설 실사 필요)과 동물미용업/동물위탁관리업(무점포 후보)은 다른 소분류
        assertThat(NoStorefrontSubCategories.isNoStorefront("동물", "동물병원")).isFalse();
    }

    @Test
    void 알수없는_조합은_무점포가_아니다() {
        assertThat(NoStorefrontSubCategories.isNoStorefront("없는카테고리", "없는소분류")).isFalse();
    }

    @Test
    void category나_subCategory가_null이면_무점포가_아니다() {
        assertThat(NoStorefrontSubCategories.isNoStorefront(null, "통신판매업")).isFalse();
        assertThat(NoStorefrontSubCategories.isNoStorefront("생활", null)).isFalse();
    }
}
```

- [ ] **Step 2: 테스트 실행 — 실패 확인**

```
mvn test -Dtest=NoStorefrontSubCategoriesTest -q
```
Expected: FAIL (`NoStorefrontSubCategories` 클래스가 아직 없음 — 컴파일 에러)

- [ ] **Step 3: 구현**

`src/main/java/com/nextstep/domain/site/NoStorefrontSubCategories.java`:

```java
package com.nextstep.domain.site;

import java.util.Set;

/**
 * (category, subCategory)별 "층/호 정보 없음" 비율이 90% 이상인 업종 — 통신판매업 등 물리적
 * 점포 없이 신고 가능한 업종. 90% 임계값을 기계적으로 적용한 결과(2026-07-18, 실측 91,772건
 * 기준), 수작업 큐레이션 아님. 표본이 작은 항목(n≤5건: 물류창고업체·박물관 및 미술관·
 * 비디오물감상실업·비디오물배급업·온라인음악서비스제공업·일반야영장업·대규모점포·썰매장업·
 * 용기냉동기특정설비·가축분뇨수집운반업·배출가스전문정비사업자(확인검사대행자)·제재업)은
 * 통계적으로 약한 신호 — 향후 실사례로 오분류가 확인되면 개별 조정.
 *
 * 판정은 이 클래스 단독으로 끝나지 않는다 — 호출부(TenancyQueryService)가 같은 businessName의
 * 다른 레코드에 물리적 신호(무점포 목록 밖 업종이거나 실제 층/호 정보)가 있으면 이 클래스의
 * 판정을 무시하고 매장으로 취급한다(동물병원 더 하임처럼 한 상호가 매장업종+무점포업종 라이선스를
 * 동시에 보유하는 경우 대응).
 */
public final class NoStorefrontSubCategories {

    private record CategoryPair(String category, String subCategory) {
    }

    private static final Set<CategoryPair> NO_STOREFRONT = Set.of(
        new CategoryPair("건강", "의료기기판매(임대)업"),
        new CategoryPair("기타", "물류창고업체"),
        new CategoryPair("기타", "민방위급수시설"),
        new CategoryPair("기타", "옥외광고업"),
        new CategoryPair("기타", "인쇄사"),
        new CategoryPair("기타", "출판사"),
        new CategoryPair("동물", "동물미용업"),
        new CategoryPair("동물", "동물생산업"),
        new CategoryPair("동물", "동물용의료용구판매업"),
        new CategoryPair("동물", "동물운송업"),
        new CategoryPair("동물", "동물위탁관리업"),
        new CategoryPair("동물", "동물전시업"),
        new CategoryPair("동물", "동물판매업"),
        new CategoryPair("문화", "게임물배급업"),
        new CategoryPair("문화", "게임물제작업"),
        new CategoryPair("문화", "대중문화예술기획업"),
        new CategoryPair("문화", "박물관 및 미술관"),
        new CategoryPair("문화", "비디오물감상실업"),
        new CategoryPair("문화", "비디오물배급업"),
        new CategoryPair("문화", "비디오물제작업"),
        new CategoryPair("문화", "숙박업"),
        new CategoryPair("문화", "영화배급업"),
        new CategoryPair("문화", "영화수입업"),
        new CategoryPair("문화", "영화제작업"),
        new CategoryPair("문화", "온라인음악서비스제공업"),
        new CategoryPair("문화", "음반및음악영상물배급업"),
        new CategoryPair("문화", "음반및음악영상물제작업"),
        new CategoryPair("문화", "일반야영장업"),
        new CategoryPair("생활", "대규모점포"),
        new CategoryPair("생활", "방문판매업"),
        new CategoryPair("생활", "썰매장업"),
        new CategoryPair("생활", "전화권유판매업"),
        new CategoryPair("생활", "통신판매업"),
        new CategoryPair("생활", "후원방문판매업체"),
        new CategoryPair("식품", "건강기능식품유통전문판매업"),
        new CategoryPair("식품", "건강기능식품일반판매업"),
        new CategoryPair("식품", "식품운반업"),
        new CategoryPair("식품", "용기냉동기특정설비"),
        new CategoryPair("식품", "축산물운반업"),
        new CategoryPair("자원환경", "가축분뇨수집운반업"),
        new CategoryPair("자원환경", "고압가스업"),
        new CategoryPair("자원환경", "대기오염물질배출시설설치사업장"),
        new CategoryPair("자원환경", "목재수입유통업"),
        new CategoryPair("자원환경", "배출가스전문정비사업자(확인검사대행자)"),
        new CategoryPair("자원환경", "저수조청소업"),
        new CategoryPair("자원환경", "제재업"),
        new CategoryPair("자원환경", "특정고압가스업")
    );

    private NoStorefrontSubCategories() {
    }

    public static boolean isNoStorefront(String category, String subCategory) {
        if (category == null || subCategory == null) return false;
        return NO_STOREFRONT.contains(new CategoryPair(category, subCategory));
    }
}
```

- [ ] **Step 4: 테스트 실행 — 통과 확인**

```
mvn test -Dtest=NoStorefrontSubCategoriesTest -q
```
Expected: 8개 테스트 전부 PASS

- [ ] **Step 5: 커밋**

```bash
git add src/main/java/com/nextstep/domain/site/NoStorefrontSubCategories.java \
        src/test/java/com/nextstep/domain/site/NoStorefrontSubCategoriesTest.java
git commit -m "feat: add classification for no-storefront/self-reported subcategories"
```

---

### Task 2: Site 도메인 모델 + TenancyQueryService businessName 단위 분리

**Files:**
- Modify: `src/main/java/com/nextstep/domain/site/Site.java`
- Modify: `src/main/java/com/nextstep/application/TenancyQueryService.java`
- Modify: `src/test/java/com/nextstep/application/TenancyQueryServiceTest.java`

**Interfaces:**
- Consumes: Task 1의 `NoStorefrontSubCategories.isNoStorefront(String, String)`
- Produces: `Site.noStorefrontRegistrations() -> List<Tenancy>`. Task 3의 `SiteQueryService`가 이 값을 읽어 DTO로 변환한다.

#### Step 1: 실패하는 테스트 먼저

`src/test/java/com/nextstep/application/TenancyQueryServiceTest.java`에서, 기존 `SAME_JIBUN_PNU` 상수 선언부 바로 위(또는 파일 내 다른 PNU 상수들 근처)에 아래 상수를 추가:

```java
    private static final String NO_STOREFRONT_PNU = "4113110100100950000";
    private static final String DELETE_NO_STOREFRONT_PNU =
        "DELETE FROM licensed_business_record WHERE pnu = '" + NO_STOREFRONT_PNU + "'";
    private static final String INSERT_NO_STOREFRONT_STOREFRONT_RECORD =
        "INSERT INTO licensed_business_record "
            + "(id, pnu, category, sub_category, license_no, business_name, business_type, business_status, "
            + "status_detail_code, status_detail, licensed_at, closed_at, road_address, jibun_address, "
            + "address_separated, address_corrected, local_gov_code, original_x, original_y) "
            + "VALUES (990501, '4113110100100950000', '식품', '일반음식점', 'test-license-30', '일반음식점가게', "
            + "NULL, '영업/정상', '0000', '정상', '2020-01-01', NULL, "
            + "'경기도 성남시 수정구 테스트로 6, 1층 (테스트동)', '경기도 성남시 수정구 테스트동 96 1층', "
            + "FALSE, TRUE, '3780000', 212818.475436898, 438579.588327304)";
    private static final String INSERT_NO_STOREFRONT_ONLY_RECORD_1 =
        "INSERT INTO licensed_business_record "
            + "(id, pnu, category, sub_category, license_no, business_name, business_type, business_status, "
            + "status_detail_code, status_detail, licensed_at, closed_at, road_address, jibun_address, "
            + "address_separated, address_corrected, local_gov_code, original_x, original_y) "
            + "VALUES (990502, '4113110100100950000', '생활', '통신판매업', 'test-license-31', '통신판매업체A', "
            + "NULL, '영업/정상', '0000', '정상', '2021-01-01', NULL, "
            + "'경기도 성남시 수정구 테스트로 6 (테스트동)', '경기도 성남시 수정구 테스트동 96', "
            + "FALSE, TRUE, '3780000', 212818.475436898, 438579.588327304)";
    private static final String INSERT_NO_STOREFRONT_ONLY_RECORD_2 =
        "INSERT INTO licensed_business_record "
            + "(id, pnu, category, sub_category, license_no, business_name, business_type, business_status, "
            + "status_detail_code, status_detail, licensed_at, closed_at, road_address, jibun_address, "
            + "address_separated, address_corrected, local_gov_code, original_x, original_y) "
            + "VALUES (990503, '4113110100100950000', '생활', '방문판매업', 'test-license-32', '방문판매업체B', "
            + "NULL, '영업/정상', '0000', '정상', '2022-01-01', NULL, "
            + "'경기도 성남시 수정구 테스트로 6 (테스트동)', '경기도 성남시 수정구 테스트동 96', "
            + "FALSE, TRUE, '3780000', 212818.475436898, 438579.588327304)";
    // 같은 businessName("겸업사업자")이 매장업종(일반음식점) + 무점포후보업종(통신판매업)을
    // 동시에 보유 — 물리적 신호가 하나라도 있으니 둘 다 storefront로 취급돼야 함(동물병원 더 하임 사례 재현)
    private static final String INSERT_NO_STOREFRONT_MIXED_STOREFRONT =
        "INSERT INTO licensed_business_record "
            + "(id, pnu, category, sub_category, license_no, business_name, business_type, business_status, "
            + "status_detail_code, status_detail, licensed_at, closed_at, road_address, jibun_address, "
            + "address_separated, address_corrected, parsed_floor, local_gov_code, original_x, original_y) "
            + "VALUES (990504, '4113110100100950000', '식품', '일반음식점', 'test-license-33', '겸업사업자', "
            + "NULL, '영업/정상', '0000', '정상', '2023-01-01', NULL, "
            + "'경기도 성남시 수정구 테스트로 6, 2층 (테스트동)', '경기도 성남시 수정구 테스트동 96 2층', "
            + "FALSE, TRUE, '2', '3780000', 212818.475436898, 438579.588327304)";
    private static final String INSERT_NO_STOREFRONT_MIXED_NOSTOREFRONT =
        "INSERT INTO licensed_business_record "
            + "(id, pnu, category, sub_category, license_no, business_name, business_type, business_status, "
            + "status_detail_code, status_detail, licensed_at, closed_at, road_address, jibun_address, "
            + "address_separated, address_corrected, local_gov_code, original_x, original_y) "
            + "VALUES (990505, '4113110100100950000', '생활', '통신판매업', 'test-license-34', '겸업사업자', "
            + "NULL, '영업/정상', '0000', '정상', '2023-02-01', NULL, "
            + "'경기도 성남시 수정구 테스트로 6 (테스트동)', '경기도 성남시 수정구 테스트동 96', "
            + "FALSE, TRUE, '3780000', 212818.475436898, 438579.588327304)";
```

같은 파일의 `@Test void csv_동일_pnu의_상세주소를_지번주소별_물건으로_묶는다()` 바로 위에 테스트 3개를 추가한다:

```java
    @Test
    @Sql(statements = {DELETE_NO_STOREFRONT_PNU, INSERT_NO_STOREFRONT_STOREFRONT_RECORD,
        INSERT_NO_STOREFRONT_ONLY_RECORD_1, INSERT_NO_STOREFRONT_ONLY_RECORD_2})
    @Sql(statements = DELETE_NO_STOREFRONT_PNU, executionPhase = Sql.ExecutionPhase.AFTER_TEST_METHOD)
    void 무점포업종만_있는_상호는_noStorefrontRegistrations로_분리된다() {
        Site site = tenancyQueryService.findSiteWithUnits(NO_STOREFRONT_PNU).orElseThrow();

        assertThat(site.units()).hasSize(1);
        assertThat(site.units().get(0).tenancies()).extracting(Tenancy::businessName)
            .containsExactly("일반음식점가게");

        assertThat(site.noStorefrontRegistrations()).hasSize(2);
        assertThat(site.noStorefrontRegistrations()).extracting(Tenancy::businessName)
            .containsExactlyInAnyOrder("통신판매업체A", "방문판매업체B");
    }

    @Test
    @Sql(statements = {DELETE_NO_STOREFRONT_PNU, INSERT_NO_STOREFRONT_MIXED_STOREFRONT,
        INSERT_NO_STOREFRONT_MIXED_NOSTOREFRONT})
    @Sql(statements = DELETE_NO_STOREFRONT_PNU, executionPhase = Sql.ExecutionPhase.AFTER_TEST_METHOD)
    void 같은_상호가_매장업종과_무점포업종을_겸하면_전부_storefront로_취급된다() {
        Site site = tenancyQueryService.findSiteWithUnits(NO_STOREFRONT_PNU).orElseThrow();

        assertThat(site.noStorefrontRegistrations()).isEmpty();
        int totalTenancies = site.units().stream().mapToInt(u -> u.tenancies().size()).sum();
        assertThat(totalTenancies).isEqualTo(2);
        assertThat(site.units().stream().flatMap(u -> u.tenancies().stream()))
            .extracting(Tenancy::businessName)
            .containsOnly("겸업사업자");
    }

    @Test
    @Sql(statements = {DELETE_NO_STOREFRONT_PNU, INSERT_NO_STOREFRONT_ONLY_RECORD_1,
        INSERT_NO_STOREFRONT_ONLY_RECORD_2})
    @Sql(statements = DELETE_NO_STOREFRONT_PNU, executionPhase = Sql.ExecutionPhase.AFTER_TEST_METHOD)
    void 전부_무점포업종이면_units는_빈배열이다() {
        Site site = tenancyQueryService.findSiteWithUnits(NO_STOREFRONT_PNU).orElseThrow();

        assertThat(site.units()).isEmpty();
        assertThat(site.noStorefrontRegistrations()).hasSize(2);
    }
```

- [ ] **Step 2: 테스트 실행 — 실패 확인**

```
mvn test -Dtest=TenancyQueryServiceTest -q
```
Expected: 컴파일 에러(`Site.noStorefrontRegistrations()` 메서드 없음)

#### Step 3: 구현

`src/main/java/com/nextstep/domain/site/Site.java` 전체 교체:

```java
package com.nextstep.domain.site;

import com.nextstep.domain.tenancy.Tenancy;
import com.nextstep.domain.unit.Unit;
import java.util.List;

public record Site(Pnu pnu, String jibunAddress, String roadAddress, Coordinate coordinate,
                    List<Unit> units, List<Tenancy> noStorefrontRegistrations) {
}
```

`src/main/java/com/nextstep/application/TenancyQueryService.java` 수정:

import 블록에 추가(`com.nextstep.domain.site.Pnu` 임포트 다음 줄):
```java
import com.nextstep.domain.site.NoStorefrontSubCategories;
```

`toSite()`(현재 81-88행)를 교체:

```java
    private Site toSite(List<LicensedBusinessRecordEntity> records) {
        List<LicensedBusinessRecordEntity> valid = validRecords(records);
        LicensedBusinessRecordEntity representative = valid.isEmpty() ? records.get(0) : valid.get(0);
        Map<Boolean, List<LicensedBusinessRecordEntity>> partitioned = partitionByStorefront(valid);
        return new Site(new Pnu(representative.getPnu()), representative.getJibunAddress(),
            representative.getRoadAddress(), KoreanTmCoordinateConverter
                .fromEpsg5174(representative.getOriginalX(), representative.getOriginalY())
                .orElse(null), toUnits(representative.getPnu(), partitioned.get(true)),
            mergedTenancies(partitioned.get(false)));
    }
```

`toSiteWithoutUnits()`(현재 90-97행)를 교체:

```java
    private Site toSiteWithoutUnits(List<LicensedBusinessRecordEntity> records) {
        List<LicensedBusinessRecordEntity> valid = validRecords(records);
        LicensedBusinessRecordEntity representative = valid.isEmpty() ? records.get(0) : valid.get(0);
        return new Site(new Pnu(representative.getPnu()), representative.getJibunAddress(),
            representative.getRoadAddress(), KoreanTmCoordinateConverter
                .fromEpsg5174(representative.getOriginalX(), representative.getOriginalY())
                .orElse(null), List.of(), List.of());
    }
```

`toUnits()` 메서드(현재 99-103행) 바로 다음에 새 메서드 추가:

```java
    private Map<Boolean, List<LicensedBusinessRecordEntity>> partitionByStorefront(
        List<LicensedBusinessRecordEntity> records
    ) {
        Map<String, List<LicensedBusinessRecordEntity>> byBusinessName = records.stream()
            .collect(Collectors.groupingBy(LicensedBusinessRecordEntity::getBusinessName, LinkedHashMap::new, Collectors.toList()));

        List<LicensedBusinessRecordEntity> storefront = new ArrayList<>();
        List<LicensedBusinessRecordEntity> noStorefront = new ArrayList<>();
        for (List<LicensedBusinessRecordEntity> group : byBusinessName.values()) {
            (hasPhysicalSignal(group) ? storefront : noStorefront).addAll(group);
        }

        Map<Boolean, List<LicensedBusinessRecordEntity>> result = new LinkedHashMap<>();
        result.put(true, storefront);
        result.put(false, noStorefront);
        return result;
    }

    private boolean hasPhysicalSignal(List<LicensedBusinessRecordEntity> businessRecords) {
        return businessRecords.stream().anyMatch(r ->
            !NoStorefrontSubCategories.isNoStorefront(r.getCategory(), r.getSubCategory())
                || r.getParsedFloor() != null
                || r.getParsedUnitNo() != null
        );
    }
```

- [ ] **Step 4: 테스트 실행 — 통과 확인**

```
mvn test -Dtest=TenancyQueryServiceTest -q
```
Expected: BUILD SUCCESS. 신규 3개 포함, 기존 테스트도 전부 PASS — **단, `csv_동일_pnu의_상세주소를_지번주소별_물건으로_묶는다`(PNU `4113110800105590004` 사용)는 실패할 가능성이 매우 높다**(사전 조사로 이 PNU에 통신판매업·의료기기판매(임대)업·건강기능식품일반판매업·동물미용업 등 무점포 후보 레코드가 다수 확인됨). 이 실패는 예상된 것 — Step 4-A로 넘어간다.

- [ ] **Step 4-A: 예상되는 회귀 처리**

`csv_동일_pnu의_상세주소를_지번주소별_물건으로_묶는다` 테스트가 실패하면:
1. 실패 메시지의 실제값(`site.units()` 크기, tenancy 합계, `anySatisfy(hasSize(21))` 등)을 읽는다.
2. **감소 방향인지 확인** — 무점포 업종이 `units[]`에서 빠지므로 Unit 수와 tenancy 합계는 기존 값(30, 59, 21)보다 **작아지거나 같아야** 한다. 만약 늘어났다면 파티션 로직에 버그가 있다는 뜻이니 STOP하고 보고한다.
3. 감소가 맞다면 테스트의 하드코딩된 숫자를 실제 관측값으로 갱신하고, 이 PNU에서 `site.noStorefrontRegistrations()`가 비어있지 않음을 확인하는 assertion을 하나 추가한다(정확한 개수는 실제 관측값 사용):
   ```java
   assertThat(site.noStorefrontRegistrations()).isNotEmpty();
   ```
4. 주석도 갱신: 왜 숫자가 바뀌었는지 한 줄 남긴다(예: `// 2026-07-18: 통신판매업 등 무점포 업종이 noStorefrontRegistrations로 분리되며 감소`).

- [ ] **Step 5: 커밋**

```bash
git add src/main/java/com/nextstep/domain/site/Site.java \
        src/main/java/com/nextstep/application/TenancyQueryService.java \
        src/test/java/com/nextstep/application/TenancyQueryServiceTest.java
git commit -m "feat: partition licensed records into storefront/no-storefront by businessName"
```

---

### Task 3: API 계약 + SiteController 반영

**Files:**
- Modify: `src/main/java/com/nextstep/web/dto/ApiDtos.java`
- Modify: `src/main/java/com/nextstep/application/SiteQueryService.java`
- Modify: `src/test/java/com/nextstep/web/SiteControllerTest.java`

**Interfaces:**
- Consumes: Task 2의 `Site.noStorefrontRegistrations() -> List<Tenancy>`
- Produces: `GET /api/sites/{pnu}` 응답에 `noStorefrontRegistrations[]` 필드 추가(`spec/api-spec.md` §② 참고)

#### Step 1: 실패하는 테스트 먼저

`src/test/java/com/nextstep/web/SiteControllerTest.java`에서, 기존 `RAW_STATUS_PNU` 관련 상수 선언부 근처에 추가:

```java
    private static final String NO_STOREFRONT_PNU = "4113110100100960001";
    private static final String DELETE_NO_STOREFRONT_PNU2 =
        "DELETE FROM licensed_business_record WHERE pnu = '" + NO_STOREFRONT_PNU + "'";
    private static final String INSERT_NO_STOREFRONT_STOREFRONT =
        "INSERT INTO licensed_business_record "
            + "(id, pnu, category, sub_category, license_no, business_name, business_type, business_status, "
            + "status_detail_code, status_detail, licensed_at, closed_at, road_address, jibun_address, "
            + "address_separated, address_corrected, local_gov_code, original_x, original_y) "
            + "VALUES (990601, '4113110100100960001', '식품', '일반음식점', 'test-license-40', '진짜매장', "
            + "NULL, '영업/정상', '0000', '정상', '2020-01-01', NULL, "
            + "'경기도 성남시 수정구 테스트로 7 (테스트동)', '경기도 성남시 수정구 테스트동 97', "
            + "FALSE, TRUE, '3780000', 212818.475436898, 438579.588327304)";
    private static final String INSERT_NO_STOREFRONT_ONLY = 
        "INSERT INTO licensed_business_record "
            + "(id, pnu, category, sub_category, license_no, business_name, business_type, business_status, "
            + "status_detail_code, status_detail, licensed_at, closed_at, road_address, jibun_address, "
            + "address_separated, address_corrected, local_gov_code, original_x, original_y) "
            + "VALUES (990602, '4113110100100960001', '생활', '통신판매업', 'test-license-41', '온라인셀러', "
            + "NULL, '영업/정상', '0000', '정상', '2021-01-01', NULL, "
            + "'경기도 성남시 수정구 테스트로 7 (테스트동)', '경기도 성남시 수정구 테스트동 97', "
            + "FALSE, TRUE, '3780000', 212818.475436898, 438579.588327304)";
```

같은 파일의 `csv_동일_pnu의_상세주소별_물건을_리스팅한다` 테스트 바로 위에 추가:

```java
    @Test
    @Sql(statements = {DELETE_NO_STOREFRONT_PNU2, INSERT_NO_STOREFRONT_STOREFRONT, INSERT_NO_STOREFRONT_ONLY})
    @Sql(statements = DELETE_NO_STOREFRONT_PNU2, executionPhase = Sql.ExecutionPhase.AFTER_TEST_METHOD)
    void 무점포업종은_units와_분리된_배열로_응답한다() throws Exception {
        mockMvc.perform(get("/api/sites/" + NO_STOREFRONT_PNU))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.units", org.hamcrest.Matchers.hasSize(1)))
            .andExpect(jsonPath("$.units[0].currentBusinessName").value("진짜매장"))
            .andExpect(jsonPath("$.noStorefrontRegistrations", org.hamcrest.Matchers.hasSize(1)))
            .andExpect(jsonPath("$.noStorefrontRegistrations[0].businessName").value("온라인셀러"))
            .andExpect(jsonPath("$.noStorefrontRegistrations[0].category").value("생활"))
            .andExpect(jsonPath("$.noStorefrontRegistrations[0].subCategory").value("통신판매업"));
    }
```

- [ ] **Step 2: 테스트 실행 — 실패 확인**

```
mvn test -Dtest=SiteControllerTest -q
```
Expected: FAIL — `$.noStorefrontRegistrations` 경로가 응답에 없음

#### Step 3: 구현

`src/main/java/com/nextstep/web/dto/ApiDtos.java`에서 `SiteDetailResponse` 레코드를 교체:

```java
    public record SiteDetailResponse(SiteDto site, List<UnitSummaryDto> units,
                                      List<NoStorefrontRegistrationDto> noStorefrontRegistrations,
                                      DisclaimerDto disclaimer) {
    }
```

`UnitSummaryDto` 레코드 바로 다음에 신규 레코드 추가:

```java
    public record NoStorefrontRegistrationDto(String businessName, String category, String subCategory,
                                               LocalDate licensedAt, LocalDate closedAt, String status) {
    }
```

`src/main/java/com/nextstep/application/SiteQueryService.java`의 `getSiteDetail()` 메서드를 교체:

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

        return new SiteDetailResponse(toSiteDto(site), units, noStorefrontRegistrations, disclaimer());
    }
```

같은 클래스에 private 헬퍼 메서드 추가(`lookupStoreDetails` 메서드 근처):

```java
    private NoStorefrontRegistrationDto toNoStorefrontRegistrationDto(Tenancy tenancy) {
        return new NoStorefrontRegistrationDto(tenancy.businessName(), tenancy.category(), tenancy.subCategory(),
            tenancy.period().licensedAt(), tenancy.period().closedAt(), tenancy.displayStatus());
    }
```

- [ ] **Step 4: 테스트 실행 — 통과 확인**

```
mvn test -Dtest=SiteControllerTest -q
```
Expected: 신규 테스트 포함 전부 PASS — **단, 기존 `csv_동일_pnu의_상세주소별_물건을_리스팅한다`(PNU `4113110800105590004`, `hasSize(30)` 검증)도 Task 2와 같은 이유로 실패할 가능성이 높다.** Task 2의 Step 4-A와 동일한 절차로 처리: 실제값 확인 → 감소 방향인지 검증 → 갱신.

- [ ] **Step 5: 전체 회귀 확인**

```
mvn test -q
```
Expected: BUILD SUCCESS, 전체 스위트 통과. Task 2/3에서 갱신한 숫자 외에 예상 못 한 실패가 있으면 STOP하고 원인을 보고한다(특히 `4113110100100340000` PNU를 쓰는 `SiteControllerTest.물건상세는_타임라인과_marketInfo를_포함한다` — 이 테스트는 unit `4113110100100340000-U1`이 현재 프로덕션에서 "스웨터메이커스"+"그랑핏 아름다운자세"(둘 다 순수 통신판매업)+"동물병원 더 하임"의 동물위탁관리업 레코드 하나가 뒤섞인 "단일(상세주소불명)" 버킷이라는 게 사전 확인됨 — 이 fix 이후 스웨터메이커스/그랑핏은 빠지고 동물병원 더 하임 관련 레코드만 남을 것으로 예상되므로, 이 테스트도 실패해서 갱신이 필요할 가능성이 크다. 실패하면 Task 2 Step 4-A와 같은 절차로 처리하되, "동물병원 더 하임"의 레코드가 사라지지 않고(noStorefrontRegistrations로도 안 빠지고) 여전히 어딘가의 `units[]`에 존재하는지 별도로 확인 — 사라졌다면 businessName 파티션 로직 버그이니 STOP).

- [ ] **Step 6: 커밋**

```bash
git add src/main/java/com/nextstep/web/dto/ApiDtos.java \
        src/main/java/com/nextstep/application/SiteQueryService.java \
        src/test/java/com/nextstep/web/SiteControllerTest.java
git commit -m "feat: expose noStorefrontRegistrations in GET /api/sites/{pnu}"
```

---

## 한계 및 후속 작업

| 항목 | 상태 |
|---|---|
| 47개 목록은 90% 임계값 기계적 산출 | 표본 작은 항목(n≤5) 위주로 향후 재검토 여지 |
| `noStorefrontRegistrations`도 `mergedTenancies` 재사용(businessName+gap 90일) | 무점포 업종엔 "같은 자리" 개념이 없어 이 재사용이 완벽히 맞는 전제는 아님 — 실사용 후 이상하면 재설계 |
| "동물병원 더 하임" 같은 겸업 사업자의 레코드가 층/호 정보 불일치로 여러 Unit에 분산되는 문제 | 이번 스코프 아님 — 기존 Unit 분리 규칙의 별개 한계(businessName 파티션은 "매장이냐 아니냐"만 결정, 어느 Unit인지는 기존 unitKey 로직 그대로) |
