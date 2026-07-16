# 동일 가게 다중업종 신고 병합 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 같은 Unit(물리적 위치) 안에서 businessName이 정확히 같은 인허가 레코드가 여러 업종으로 나뉘어 있을 때, 인허가일자 gap이 90일 이내면 하나의 Tenancy(재직)로 병합해 타임라인 왜곡과 통계 부정확성을 없앤다.

**Architecture:** `TenancyQueryService.toUnit()`이 `UnitGroup.records()`를 레코드 1:1로 `Tenancy`에 매핑하던 것을, businessName으로 재그룹 → gap 기준 병합 → 병합그룹 1:1 매핑으로 바꾼다. `unitGroups()`(물리적 위치 그룹핑)는 변경하지 않는다 — 이 작업은 그 안쪽 레이어에서만 동작한다.

**Tech Stack:** Java 21, Spring Boot 3.x, JUnit 5 + `@SpringBootTest`(H2 seed 데이터 기반, 기존 `TenancyQueryServiceTest.java` 패턴과 동일)

## Global Constraints

- 테스트: `mvn test` 전체 통과 상태 유지 (기존 테스트 깨지면 안 됨)
- API 계약 변경 없음 — `Tenancy`/`Unit` 필드 타입·구조 그대로, `spec/api-spec.md` 수정 불필요
- 같은 Unit 내 businessName이 정확히 같은 레코드만 병합 대상 (문자열 정규화 없음 — 상가API 조인 원칙과 동일하게 정확 일치)
- gap 임계값은 상수 `SAME_BUSINESS_MERGE_GAP_DAYS = 90` (일). 초기 추정값이므로 `ponytail:` 주석으로 표시
- 커밋: 태스크 단위로 커밋

---

## File Map

| 파일 | 역할 |
|---|---|
| `src/main/java/com/nextstep/application/TenancyQueryService.java` | **주 변경 대상** — `toUnit()`, `toTenancy()` 교체, `mergedTenancies()`/`mergeByGap()`/`representativeOf()` 신규 |
| `src/test/java/com/nextstep/application/TenancyQueryServiceTest.java` | 신규 테스트 4개 (gap 이내 병합 / gap 초과 분리 / 완전 겹침 병합 / 다른 businessName 미병합) |

---

### Task 1: businessName + gap 병합 로직

**Files:**
- Modify: `src/main/java/com/nextstep/application/TenancyQueryService.java:96-123` (`toUnits`, `toUnit`, `toTenancy`)
- Modify: `src/test/java/com/nextstep/application/TenancyQueryServiceTest.java` (새 테스트 4개 추가)

**Interfaces:**
- Consumes: `UnitGroup.records()` — `List<LicensedBusinessRecordEntity>` (이미 `unitKey()`로 같은 물리적 위치로 확정된 레코드들, `TenancyQueryService.java:201`)
- Produces: `toUnit(UnitGroup)` — 반환 `Unit`의 `tenancies()` 필드가 이제 businessName+gap 병합 결과를 담음. 이 메서드의 시그니처와 호출부(`toUnits`, `findUnitWithTenancies`)는 변경되지 않으므로 다른 코드에 영향 없음.

#### Step 1: 실패하는 테스트 먼저

`src/test/java/com/nextstep/application/TenancyQueryServiceTest.java`에 아래 상수와 SQL, 테스트 4개를 추가한다.

기존 `SAME_JIBUN_PNU` 상수 선언부 바로 위에 삽입:

```java
    private static final String MULTI_CATEGORY_PNU = "4113110100100960000";
    private static final String DELETE_MULTI_CATEGORY_PNU =
        "DELETE FROM licensed_business_record WHERE pnu = '" + MULTI_CATEGORY_PNU + "'";
    // 시나리오1: gap 90일 이내 업종 전환 -> 병합
    private static final String INSERT_GAP_MERGE_A =
        "INSERT INTO licensed_business_record "
            + "(id, pnu, category, sub_category, license_no, business_name, business_type, business_status, "
            + "status_detail_code, status_detail, licensed_at, closed_at, road_address, jibun_address, "
            + "address_separated, address_corrected, parsed_building_name, parsed_floor, parsed_unit_no, "
            + "parse_confidence, parse_method, local_gov_code, original_x, original_y) "
            + "VALUES (990301, '4113110100100960000', '외식', '커피', 'test-license-10', '병합가게A', "
            + "NULL, '폐업', '0002', '폐업', '2020-01-01', '2022-01-01', "
            + "'경기도 성남시 수정구 테스트로 4, 1층 101호 (테스트동)', '경기도 성남시 수정구 테스트동 96 1층 101호', "
            + "FALSE, TRUE, NULL, '1', '101', 'HIGH', 'REGEX', '3780000', 212818.475436898, 438579.588327304)";
    private static final String INSERT_GAP_MERGE_B =
        "INSERT INTO licensed_business_record "
            + "(id, pnu, category, sub_category, license_no, business_name, business_type, business_status, "
            + "status_detail_code, status_detail, licensed_at, closed_at, road_address, jibun_address, "
            + "address_separated, address_corrected, parsed_building_name, parsed_floor, parsed_unit_no, "
            + "parse_confidence, parse_method, local_gov_code, original_x, original_y) "
            + "VALUES (990302, '4113110100100960000', '소매', '즉석판매', 'test-license-11', '병합가게A', "
            + "NULL, '영업/정상', '0000', '정상', '2022-02-01', NULL, "
            + "'경기도 성남시 수정구 테스트로 4, 1층 101호 (테스트동)', '경기도 성남시 수정구 테스트동 96 1층 101호', "
            + "FALSE, TRUE, NULL, '1', '101', 'HIGH', 'REGEX', '3780000', 212818.475436898, 438579.588327304)";
    // 시나리오2: gap 90일 초과 재입점 -> 분리 유지
    private static final String INSERT_GAP_SPLIT_C =
        "INSERT INTO licensed_business_record "
            + "(id, pnu, category, sub_category, license_no, business_name, business_type, business_status, "
            + "status_detail_code, status_detail, licensed_at, closed_at, road_address, jibun_address, "
            + "address_separated, address_corrected, parsed_building_name, parsed_floor, parsed_unit_no, "
            + "parse_confidence, parse_method, local_gov_code, original_x, original_y) "
            + "VALUES (990303, '4113110100100960000', '외식', '한식', 'test-license-12', '재입점가게B', "
            + "NULL, '폐업', '0002', '폐업', '2018-01-01', '2018-06-01', "
            + "'경기도 성남시 수정구 테스트로 4, 2층 102호 (테스트동)', '경기도 성남시 수정구 테스트동 96 2층 102호', "
            + "FALSE, TRUE, NULL, '2', '102', 'HIGH', 'REGEX', '3780000', 212818.475436898, 438579.588327304)";
    private static final String INSERT_GAP_SPLIT_D =
        "INSERT INTO licensed_business_record "
            + "(id, pnu, category, sub_category, license_no, business_name, business_type, business_status, "
            + "status_detail_code, status_detail, licensed_at, closed_at, road_address, jibun_address, "
            + "address_separated, address_corrected, parsed_building_name, parsed_floor, parsed_unit_no, "
            + "parse_confidence, parse_method, local_gov_code, original_x, original_y) "
            + "VALUES (990304, '4113110100100960000', '외식', '분식', 'test-license-13', '재입점가게B', "
            + "NULL, '영업/정상', '0000', '정상', '2020-01-01', NULL, "
            + "'경기도 성남시 수정구 테스트로 4, 2층 102호 (테스트동)', '경기도 성남시 수정구 테스트동 96 2층 102호', "
            + "FALSE, TRUE, NULL, '2', '102', 'HIGH', 'REGEX', '3780000', 212818.475436898, 438579.588327304)";
    // 시나리오3: 완전 겹침(업종 두 개 동시 보유) -> 병합
    private static final String INSERT_OVERLAP_E =
        "INSERT INTO licensed_business_record "
            + "(id, pnu, category, sub_category, license_no, business_name, business_type, business_status, "
            + "status_detail_code, status_detail, licensed_at, closed_at, road_address, jibun_address, "
            + "address_separated, address_corrected, parsed_building_name, parsed_floor, parsed_unit_no, "
            + "parse_confidence, parse_method, local_gov_code, original_x, original_y) "
            + "VALUES (990305, '4113110100100960000', '외식', '카페', 'test-license-14', '동시업종가게C', "
            + "NULL, '영업/정상', '0000', '정상', '2021-01-01', NULL, "
            + "'경기도 성남시 수정구 테스트로 4, 3층 103호 (테스트동)', '경기도 성남시 수정구 테스트동 96 3층 103호', "
            + "FALSE, TRUE, NULL, '3', '103', 'HIGH', 'REGEX', '3780000', 212818.475436898, 438579.588327304)";
    private static final String INSERT_OVERLAP_F =
        "INSERT INTO licensed_business_record "
            + "(id, pnu, category, sub_category, license_no, business_name, business_type, business_status, "
            + "status_detail_code, status_detail, licensed_at, closed_at, road_address, jibun_address, "
            + "address_separated, address_corrected, parsed_building_name, parsed_floor, parsed_unit_no, "
            + "parse_confidence, parse_method, local_gov_code, original_x, original_y) "
            + "VALUES (990306, '4113110100100960000', '소매', '베이커리', 'test-license-15', '동시업종가게C', "
            + "NULL, '영업/정상', '0000', '정상', '2021-06-01', NULL, "
            + "'경기도 성남시 수정구 테스트로 4, 3층 103호 (테스트동)', '경기도 성남시 수정구 테스트동 96 3층 103호', "
            + "FALSE, TRUE, NULL, '3', '103', 'HIGH', 'REGEX', '3780000', 212818.475436898, 438579.588327304)";
    // 시나리오4: 같은 Unit이지만 businessName 다름 -> 병합 안 함 (회귀)
    private static final String INSERT_DIFFERENT_NAME_G =
        "INSERT INTO licensed_business_record "
            + "(id, pnu, category, sub_category, license_no, business_name, business_type, business_status, "
            + "status_detail_code, status_detail, licensed_at, closed_at, road_address, jibun_address, "
            + "address_separated, address_corrected, parsed_building_name, parsed_floor, parsed_unit_no, "
            + "parse_confidence, parse_method, local_gov_code, original_x, original_y) "
            + "VALUES (990307, '4113110100100960000', '서비스', '미용', 'test-license-16', '가게D-1', "
            + "NULL, '영업/정상', '0000', '정상', '2020-01-01', NULL, "
            + "'경기도 성남시 수정구 테스트로 4, 4층 104호 (테스트동)', '경기도 성남시 수정구 테스트동 96 4층 104호', "
            + "FALSE, TRUE, NULL, '4', '104', 'HIGH', 'REGEX', '3780000', 212818.475436898, 438579.588327304)";
    private static final String INSERT_DIFFERENT_NAME_H =
        "INSERT INTO licensed_business_record "
            + "(id, pnu, category, sub_category, license_no, business_name, business_type, business_status, "
            + "status_detail_code, status_detail, licensed_at, closed_at, road_address, jibun_address, "
            + "address_separated, address_corrected, parsed_building_name, parsed_floor, parsed_unit_no, "
            + "parse_confidence, parse_method, local_gov_code, original_x, original_y) "
            + "VALUES (990308, '4113110100100960000', '서비스', '세탁', 'test-license-17', '가게D-2', "
            + "NULL, '영업/정상', '0000', '정상', '2020-06-01', NULL, "
            + "'경기도 성남시 수정구 테스트로 4, 4층 104호 (테스트동)', '경기도 성남시 수정구 테스트동 96 4층 104호', "
            + "FALSE, TRUE, NULL, '4', '104', 'HIGH', 'REGEX', '3780000', 212818.475436898, 438579.588327304)";
```

같은 파일의 `@Test void csv_동일_pnu의_상세주소를_지번주소별_물건으로_묶는다()` 바로 위에 테스트 4개를 추가한다:

```java
    @Test
    @Sql(statements = {DELETE_MULTI_CATEGORY_PNU, INSERT_GAP_MERGE_A, INSERT_GAP_MERGE_B,
        INSERT_GAP_SPLIT_C, INSERT_GAP_SPLIT_D, INSERT_OVERLAP_E, INSERT_OVERLAP_F,
        INSERT_DIFFERENT_NAME_G, INSERT_DIFFERENT_NAME_H})
    @Sql(statements = DELETE_MULTI_CATEGORY_PNU, executionPhase = Sql.ExecutionPhase.AFTER_TEST_METHOD)
    void gap이_90일_이내인_같은_가게의_다른_업종_레코드는_하나의_재직으로_병합된다() {
        Site site = tenancyQueryService.findSiteWithUnits(MULTI_CATEGORY_PNU).orElseThrow();
        Unit unit = site.units().stream().filter(u -> u.label().equals("1층 101호")).findFirst().orElseThrow();

        assertThat(unit.tenancies()).hasSize(1);
        Tenancy merged = unit.tenancies().get(0);
        assertThat(merged.period().licensedAt()).isEqualTo(java.time.LocalDate.of(2020, 1, 1));
        assertThat(merged.period().closedAt()).isNull();
        assertThat(merged.category()).isEqualTo("소매");
        assertThat(merged.subCategory()).isEqualTo("즉석판매");
        assertThat(merged.status()).isEqualTo("영업/정상");
    }

    @Test
    @Sql(statements = {DELETE_MULTI_CATEGORY_PNU, INSERT_GAP_MERGE_A, INSERT_GAP_MERGE_B,
        INSERT_GAP_SPLIT_C, INSERT_GAP_SPLIT_D, INSERT_OVERLAP_E, INSERT_OVERLAP_F,
        INSERT_DIFFERENT_NAME_G, INSERT_DIFFERENT_NAME_H})
    @Sql(statements = DELETE_MULTI_CATEGORY_PNU, executionPhase = Sql.ExecutionPhase.AFTER_TEST_METHOD)
    void gap이_90일_초과인_같은_가게_레코드는_별도_재직으로_유지된다() {
        Site site = tenancyQueryService.findSiteWithUnits(MULTI_CATEGORY_PNU).orElseThrow();
        Unit unit = site.units().stream().filter(u -> u.label().equals("2층 102호")).findFirst().orElseThrow();

        assertThat(unit.tenancies()).hasSize(2);
        assertThat(unit.tenancies()).extracting(t -> t.period().licensedAt())
            .containsExactly(java.time.LocalDate.of(2018, 1, 1), java.time.LocalDate.of(2020, 1, 1));
    }

    @Test
    @Sql(statements = {DELETE_MULTI_CATEGORY_PNU, INSERT_GAP_MERGE_A, INSERT_GAP_MERGE_B,
        INSERT_GAP_SPLIT_C, INSERT_GAP_SPLIT_D, INSERT_OVERLAP_E, INSERT_OVERLAP_F,
        INSERT_DIFFERENT_NAME_G, INSERT_DIFFERENT_NAME_H})
    @Sql(statements = DELETE_MULTI_CATEGORY_PNU, executionPhase = Sql.ExecutionPhase.AFTER_TEST_METHOD)
    void 기간이_완전히_겹치는_동시업종_레코드는_하나의_재직으로_병합된다() {
        Site site = tenancyQueryService.findSiteWithUnits(MULTI_CATEGORY_PNU).orElseThrow();
        Unit unit = site.units().stream().filter(u -> u.label().equals("3층 103호")).findFirst().orElseThrow();

        assertThat(unit.tenancies()).hasSize(1);
        Tenancy merged = unit.tenancies().get(0);
        assertThat(merged.period().licensedAt()).isEqualTo(java.time.LocalDate.of(2021, 1, 1));
        assertThat(merged.period().closedAt()).isNull();
        assertThat(merged.category()).isEqualTo("소매");
    }

    @Test
    @Sql(statements = {DELETE_MULTI_CATEGORY_PNU, INSERT_GAP_MERGE_A, INSERT_GAP_MERGE_B,
        INSERT_GAP_SPLIT_C, INSERT_GAP_SPLIT_D, INSERT_OVERLAP_E, INSERT_OVERLAP_F,
        INSERT_DIFFERENT_NAME_G, INSERT_DIFFERENT_NAME_H})
    @Sql(statements = DELETE_MULTI_CATEGORY_PNU, executionPhase = Sql.ExecutionPhase.AFTER_TEST_METHOD)
    void 같은_Unit이라도_businessName이_다르면_병합하지_않는다() {
        Site site = tenancyQueryService.findSiteWithUnits(MULTI_CATEGORY_PNU).orElseThrow();
        Unit unit = site.units().stream().filter(u -> u.label().equals("4층 104호")).findFirst().orElseThrow();

        assertThat(unit.tenancies()).hasSize(2);
        assertThat(unit.tenancies()).extracting(Tenancy::businessName)
            .containsExactlyInAnyOrder("가게D-1", "가게D-2");
    }
```

- [x] **Step 1: 위 상수·테스트 4개를 `TenancyQueryServiceTest.java`에 추가**

- [x] **Step 2: 실행 — 실패 확인**

```
mvn test -Dtest=TenancyQueryServiceTest -q
```
Expected: FAIL (`gap이_90일_이내인...`, `기간이_완전히_겹치는...` 두 개는 현재 로직이 레코드별로 별도 Tenancy를 만들기 때문에 `hasSize(1)` 단언이 깨짐)

#### Step 3: 구현

`src/main/java/com/nextstep/application/TenancyQueryService.java`에서 `UNIT_ID_SEPARATOR` 선언 바로 아래에 상수 추가:

```java
    private static final String UNIT_ID_SEPARATOR = "-U";
    // ponytail: 초기 추정값. 실사례로 오판(과병합/과분리) 나오면 조정
    private static final int SAME_BUSINESS_MERGE_GAP_DAYS = 90;
```

`toUnit()` 메서드(현재 96-110행)를 다음으로 교체:

```java
    private List<Unit> toUnits(String pnu, List<LicensedBusinessRecordEntity> records) {
        return unitGroups(pnu, records).stream()
            .map(this::toUnit)
            .toList();
    }

    private Unit toUnit(UnitGroup group) {
        List<Tenancy> tenancies = mergedTenancies(group.records());
        LicensedBusinessRecordEntity representative = group.records().get(0);
        return new Unit(group.unitId(), unitLabel(representative), LocationSource.LICENSE, tenancies,
            representative.getParsedFloor(), representative.getParsedUnitNo(), representative.getParseConfidence());
    }

    private List<Tenancy> mergedTenancies(List<LicensedBusinessRecordEntity> records) {
        Map<String, List<LicensedBusinessRecordEntity>> byBusinessName = records.stream()
            .collect(Collectors.groupingBy(LicensedBusinessRecordEntity::getBusinessName, LinkedHashMap::new, Collectors.toList()));

        return byBusinessName.values().stream()
            .flatMap(sameNameRecords -> mergeByGap(sameNameRecords).stream())
            .map(this::toTenancy)
            .sorted(Comparator.comparing(t -> t.period().licensedAt()))
            .toList();
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

    private Tenancy toTenancy(List<LicensedBusinessRecordEntity> group) {
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

        return new Tenancy(
            representative.getId(),
            representative.getBusinessName(),
            representative.getCategory(),
            representative.getSubCategory(),
            null,
            new TenancyPeriod(licensedAt, closedAt),
            representative.getBusinessStatus(),
            "license_only"
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
```

기존 단일 레코드용 `private Tenancy toTenancy(LicensedBusinessRecordEntity entity) { ... }` 메서드(현재 112-123행)는 **삭제**한다 — 위 `toTenancy(List<...>)`가 대체한다.

파일 상단 import 블록(`java.util.ArrayList` 다음 줄)에 추가:

```java
import java.time.LocalDate;
```

- [x] **Step 4: 컴파일 확인** — BUILD SUCCESS

- [x] **Step 5: 새 테스트 통과 확인** — 신규 4개 포함 전체 PASS

- [x] **Step 6: 전체 테스트 회귀 확인** — 최종 57/57 PASS. 구현 중 1차 리뷰에서 구현자가 brief에 없는 "카테고리 1개뿐이면 gap 병합 제외" 게이트를 임의로 추가한 결함이 발견돼(실seed데이터 "타코본" 0일-gap 재입점 케이스가 병합 안 됨) 별도 fix 커밋(`b9fd0de`)으로 제거함. 그 여파로 CSV 회귀 기대값이 79→59로, 다른 4개 assertion도 함께 갱신됨(모두 감소 방향, 증가는 없음 — 병합 조건이 느슨해질수록 Unit별 tenancy 수는 단조감소). 재검토 결과 Approved.

- [x] **Step 7: 커밋** — `8490938`(구현) + `b9fd0de`(카테고리 게이트 결함 수정) + `69e0add`(회귀값 설명 주석)

---

## 한계 및 후속 작업

| 케이스 | 현재 결과 | 이유 |
|---|---|---|
| 상호명 리브랜딩(간판만 바뀜) | 별도 재직 유지 | businessName 정확 일치만 신뢰, 감지 신호 없음 |
| gap 임계값 90일의 정확성 | 미검증 추정값 | 실사례 데이터로 오판(과병합/과분리) 확인 후 조정 필요 |
| 여러 업종을 프론트에 리스트로 노출 | 대표 업종 하나만 노출 | API 계약 변경(Tenancy.category → 배열)이 필요해 이번 스코프 밖 |
