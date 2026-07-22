# 동시 영업 중 다른 상호 뭉침 방지 (Unit 충돌 감지/재분리) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** `TenancyQueryService`가 같은 시기에 동시 영업 중이던 서로 다른 상호를 하나의 `Unit`으로
잘못 합치는 문제(가락시장·AK플라자·롯데백화점·백현동 등 실측 확인)를 고친다.

**Architecture:** 신규 순수 도메인 클래스 `OccupancySpan`(`domain.unit`)으로 겹침 판정을 캡슐화하고,
`TenancyQueryService.unitGroups()`에 3단계 재분리 캐스케이드(구조화된 층/호 → 지번/도로명 원문
텍스트 → 상호명)를 추가한다. 구체적 호실번호(`UNIT::` 키)가 있는 그룹은 대상에서 제외한다(실측:
실제 문제 사례 전부 호실번호 없는 케이스였고, 있는데 겹치는 경우는 폐업신고 누락일 가능성이
높음 — 기존 회귀 테스트도 이 전제로 작성돼 있음).

**Tech Stack:** Java 21, Spring Boot 3.3.4, JUnit 5 + AssertJ, H2(테스트)/MySQL(운영), Maven.

**설계 문서:** `docs/superpowers/specs/2026-07-22-unit-overlap-split-design.md` (커밋 `257813a`).

## Global Constraints

- `domain.*` 패키지는 JPA/Spring 비의존 순수 클래스만 (`backend-spec.md` §2). `OccupancySpan`은
  `LicensedBusinessRecordEntity`를 몰라야 한다 — `TenancyQueryService`가 엔티티→값 변환 후 넘긴다.
- 이 저장소는 로컬에 클론돼 있지 않다 — 모든 파일 작업은 원격 서버
  `ubuntu@132.226.231.92`(키 `C:\Users\soldesk\key_turbom_v0.key`)의 `/home/ubuntu/app-build`에서
  SSH로 수행한다. 파일을 새로 쓰거나 통째로 바꿀 때는 로컬 스크래치 경로에 작성 후
  `scp -i "C:\Users\soldesk\key_turbom_v0.key" <local> ubuntu@132.226.231.92:<remote>`로 올린다.
  테스트 실행은 `ssh -i "C:\Users\soldesk\key_turbom_v0.key" ubuntu@132.226.231.92 "cd /home/ubuntu/app-build && mvn -q -Dtest=<클래스명> test 2>&1 | tail -60"`.
- 기존 스타일 유지: 이 파일들은 이미 방대한 SQL INSERT 문자열 상수 패턴을 쓴다
  (`src/test/java/com/nextstep/application/TenancyQueryServiceTest.java` 참고) — 새 테스트도 그
  패턴을 그대로 따른다.
- 회귀 금지: 아래 기존 테스트 3개는 반드시 그대로 통과해야 한다 — 이번 변경의 존재 이유가 이
  테스트들을 깨지 않으면서 실제 문제를 잡는 것.
  - `TenancyQueryServiceTest.같은_Unit이라도_businessName이_다르면_병합하지_않는다`
  - `TenancyQueryServiceTest.층이_생략된_레코드는_같은_호실번호면_병합된다`
  - 그 외 `mvn clean test` 전체(현재 94개) 통과.

---

### Task 1: `OccupancySpan` 값 객체(겹침 판정)

**Files:**
- Create: `src/main/java/com/nextstep/domain/unit/OccupancySpan.java`
- Test: `src/test/java/com/nextstep/domain/unit/OccupancySpanTest.java`

**Interfaces:**
- Produces: `record OccupancySpan(String ownerKey, LocalDate start, LocalDate endOrNull)`,
  메서드 `boolean overlaps(OccupancySpan other)`. Task 2가 이 타입과 메서드를 그대로 사용한다.

- [ ] **Step 1: 로컬 스크래치에 실패하는 테스트 작성**

경로: `C:\Users\soldesk\AppData\Local\Temp\claude\<세션>\scratchpad\OccupancySpanTest.java`
(스크래치 디렉터리는 세션마다 다르므로 현재 세션의 스크래치 경로 사용)

```java
package com.nextstep.domain.unit;

import org.junit.jupiter.api.Test;
import java.time.LocalDate;
import static org.assertj.core.api.Assertions.assertThat;

class OccupancySpanTest {

    @Test
    void 기간이_겹치면_true() {
        OccupancySpan a = new OccupancySpan("A", LocalDate.of(2020, 1, 1), LocalDate.of(2021, 1, 1));
        OccupancySpan b = new OccupancySpan("B", LocalDate.of(2020, 6, 1), LocalDate.of(2022, 1, 1));

        assertThat(a.overlaps(b)).isTrue();
        assertThat(b.overlaps(a)).isTrue();
    }

    @Test
    void 기간이_전혀_안겹치면_false() {
        OccupancySpan a = new OccupancySpan("A", LocalDate.of(2020, 1, 1), LocalDate.of(2020, 6, 1));
        OccupancySpan b = new OccupancySpan("B", LocalDate.of(2021, 1, 1), LocalDate.of(2021, 6, 1));

        assertThat(a.overlaps(b)).isFalse();
    }

    @Test
    void 당일_인수인계는_겹침이_아니다() {
        // A의 종료일과 B의 시작일이 같은 날 — 흔한 정상 승계 패턴, 동시운영 아님
        OccupancySpan a = new OccupancySpan("A", LocalDate.of(2019, 1, 1), LocalDate.of(2020, 1, 1));
        OccupancySpan b = new OccupancySpan("B", LocalDate.of(2020, 1, 1), null);

        assertThat(a.overlaps(b)).isFalse();
        assertThat(b.overlaps(a)).isFalse();
    }

    @Test
    void 둘_다_계속_영업중이면_겹침() {
        OccupancySpan a = new OccupancySpan("A", LocalDate.of(2020, 1, 1), null);
        OccupancySpan b = new OccupancySpan("B", LocalDate.of(2020, 6, 1), null);

        assertThat(a.overlaps(b)).isTrue();
    }

    @Test
    void 한쪽만_계속_영업중이고_다른쪽_기간이_그_안에_있으면_겹침() {
        OccupancySpan a = new OccupancySpan("A", LocalDate.of(2020, 1, 1), null);
        OccupancySpan b = new OccupancySpan("B", LocalDate.of(2021, 1, 1), LocalDate.of(2021, 6, 1));

        assertThat(a.overlaps(b)).isTrue();
    }
}
```

- [ ] **Step 2: 서버에 업로드하고 실행해서 실패 확인**

```bash
scp -i "C:\Users\soldesk\key_turbom_v0.key" <로컬경로>\OccupancySpanTest.java ubuntu@132.226.231.92:/home/ubuntu/app-build/src/test/java/com/nextstep/domain/unit/OccupancySpanTest.java
ssh -i "C:\Users\soldesk\key_turbom_v0.key" ubuntu@132.226.231.92 "cd /home/ubuntu/app-build && mvn -q -Dtest=OccupancySpanTest test 2>&1 | tail -40"
```

기대 결과: 컴파일 실패(`OccupancySpan` 클래스 없음) — `cannot find symbol` 에러.

- [ ] **Step 3: `OccupancySpan` 구현**

```java
package com.nextstep.domain.unit;

import java.time.LocalDate;

public record OccupancySpan(String ownerKey, LocalDate start, LocalDate endOrNull) {

    public boolean overlaps(OccupancySpan other) {
        LocalDate thisEnd = endOrNull == null ? LocalDate.MAX : endOrNull;
        LocalDate otherEnd = other.endOrNull == null ? LocalDate.MAX : other.endOrNull;
        // 경계가 닿기만 하는 경우(당일 인수인계: 한쪽 종료일 == 다른쪽 시작일)는 겹침이 아니다 —
        // 실제 동시운영이 아니라 흔한 정상적 승계 패턴이라 엄격한 부등호(<)로 배제한다.
        return start.isBefore(otherEnd) && other.start.isBefore(thisEnd);
    }
}
```

- [ ] **Step 4: 업로드하고 실행해서 통과 확인**

```bash
scp -i "C:\Users\soldesk\key_turbom_v0.key" <로컬경로>\OccupancySpan.java ubuntu@132.226.231.92:/home/ubuntu/app-build/src/main/java/com/nextstep/domain/unit/OccupancySpan.java
ssh -i "C:\Users\soldesk\key_turbom_v0.key" ubuntu@132.226.231.92 "cd /home/ubuntu/app-build && mvn -q -Dtest=OccupancySpanTest test 2>&1 | tail -40 && cat target/surefire-reports/com.nextstep.domain.unit.OccupancySpanTest.txt"
```

기대 결과: `Tests run: 5, Failures: 0, Errors: 0`

- [ ] **Step 5: 커밋**

```bash
ssh -i "C:\Users\soldesk\key_turbom_v0.key" ubuntu@132.226.231.92 "cd /home/ubuntu/app-build && git add src/main/java/com/nextstep/domain/unit/OccupancySpan.java src/test/java/com/nextstep/domain/unit/OccupancySpanTest.java && git commit -m 'feat: add OccupancySpan for detecting concurrent-occupancy overlap'"
```

(원격 push는 Task 3 완료 후 한 번에 확인받고 진행 — 아래 Task 3 참고)

---

### Task 2: `TenancyQueryService` 충돌 감지 + 3단계 재분리 캐스케이드

**Files:**
- Modify: `src/main/java/com/nextstep/application/TenancyQueryService.java`
- Modify: `src/test/java/com/nextstep/application/TenancyQueryServiceTest.java`

**Interfaces:**
- Consumes: `OccupancySpan(String ownerKey, LocalDate start, LocalDate endOrNull)`,
  `OccupancySpan.overlaps(OccupancySpan)` (Task 1).
- 기존 `unitKey(LicensedBusinessRecordEntity)`, `normalize(String)`, `mergeByGap(List<...>)`는
  그대로 재사용(수정 없음).

이 태스크는 하나의 알고리즘을 단계적으로 완성하는 흐름이라 세부 스텝을 나눠 진행한다(부분
구현 상태로 커밋하지 않음 — 마지막 Step에서만 커밋).

- [ ] **Step 1: 2차 키(원문 주소텍스트)로 해소되는 시나리오 — 실패하는 테스트 작성**

`TenancyQueryServiceTest.java`의 필드 선언부(기존 상수들 바로 아래, `@Autowired` 줄 위)에 아래
상수 3세트를 추가한다:

```java
    private static final String OVERLAP_TIER2_PNU = "4113110100100940000";
    private static final String DELETE_OVERLAP_TIER2_PNU =
        "DELETE FROM licensed_business_record WHERE pnu = '" + OVERLAP_TIER2_PNU + "'";
    // 시나리오: 층만 파싱되고(B1) 호실번호 없음 -> 1차 키로는 뭉침. 지번주소 텍스트가 서로
    // 달라서(94-1 vs 94-2) 2차 키로 해소돼야 함(AK플라자 지하1층 "일부호" 반복 사례 재현)
    private static final String INSERT_OVERLAP_TIER2_A =
        "INSERT INTO licensed_business_record "
            + "(id, pnu, category, sub_category, license_no, business_name, business_type, business_status, "
            + "status_detail_code, status_detail, licensed_at, closed_at, road_address, jibun_address, "
            + "address_separated, address_corrected, parsed_building_name, parsed_floor, parsed_unit_no, "
            + "parse_confidence, parse_method, local_gov_code, original_x, original_y) "
            + "VALUES (9900000401, '4113110100100940000', '식품', '즉석판매제조가공업', 'test-license-40', '가락족발A', "
            + "NULL, '영업/정상', '0000', '정상', '2020-01-01', NULL, "
            + "'경기도 성남시 수정구 테스트로 7, 지하1층 일부호 (테스트동)', '경기도 성남시 수정구 테스트동 94-1 지하1층', "
            + "FALSE, TRUE, NULL, 'B1', NULL, 'HIGH', 'REGEX', '3780000', 212818.475436898, 438579.588327304)";
    private static final String INSERT_OVERLAP_TIER2_B =
        "INSERT INTO licensed_business_record "
            + "(id, pnu, category, sub_category, license_no, business_name, business_type, business_status, "
            + "status_detail_code, status_detail, licensed_at, closed_at, road_address, jibun_address, "
            + "address_separated, address_corrected, parsed_building_name, parsed_floor, parsed_unit_no, "
            + "parse_confidence, parse_method, local_gov_code, original_x, original_y) "
            + "VALUES (9900000402, '4113110100100940000', '식품', '즉석판매제조가공업', 'test-license-41', '가락생선B', "
            + "NULL, '영업/정상', '0000', '정상', '2020-06-01', NULL, "
            + "'경기도 성남시 수정구 테스트로 7, 지하1층 일부호 (테스트동)', '경기도 성남시 수정구 테스트동 94-2 지하1층', "
            + "FALSE, TRUE, NULL, 'B1', NULL, 'HIGH', 'REGEX', '3780000', 212818.475436898, 438579.588327304)";
```

같은 파일 맨 아래(마지막 `@Test` 메서드 뒤, 클래스 닫는 `}` 앞)에 테스트 추가:

```java
    @Test
    @Sql(statements = {DELETE_OVERLAP_TIER2_PNU, INSERT_OVERLAP_TIER2_A, INSERT_OVERLAP_TIER2_B})
    @Sql(statements = DELETE_OVERLAP_TIER2_PNU, executionPhase = Sql.ExecutionPhase.AFTER_TEST_METHOD)
    void 층만_겹치고_지번주소가_다르면_지번주소로_재분리된다() {
        Site site = tenancyQueryService.findSiteWithUnits(OVERLAP_TIER2_PNU).orElseThrow();

        assertThat(site.units()).hasSize(2);
        assertThat(site.units()).flatExtracting(Unit::tenancies)
            .extracting(Tenancy::businessName)
            .containsExactlyInAnyOrder("가락족발A", "가락생선B");
        assertThat(site.units()).allSatisfy(unit -> assertThat(unit.tenancies()).hasSize(1));
    }
```

이 테스트는 현재 코드로는 실패한다(층/호 키가 같아서 1개 Unit·2개 Tenancy로 나옴, 2개 Unit
기대와 불일치) — 업로드해서 확인:

```bash
scp -i "C:\Users\soldesk\key_turbom_v0.key" <로컬경로>\TenancyQueryServiceTest.java ubuntu@132.226.231.92:/home/ubuntu/app-build/src/test/java/com/nextstep/application/TenancyQueryServiceTest.java
ssh -i "C:\Users\soldesk\key_turbom_v0.key" ubuntu@132.226.231.92 "cd /home/ubuntu/app-build && mvn -q -Dtest=TenancyQueryServiceTest#층만_겹치고_지번주소가_다르면_지번주소로_재분리된다 test 2>&1 | tail -60"
```

기대 결과: FAIL (`expected size:<2> but was:<1>` 류)

- [ ] **Step 2: `TenancyQueryService.java`에 충돌 감지 + 2단계까지 구현**

기존 `import` 블록(파일 최상단)에 두 줄 추가:

```java
import com.nextstep.domain.unit.OccupancySpan;
```
(`com.nextstep.domain.unit.LocationSource` 임포트 바로 위나 아래, 알파벳 순서 유지)

```java
import java.util.function.Function;
import java.util.stream.Stream;
```
(`java.util.stream.Collectors` 임포트 근처, 알파벳 순서 유지)

기존 `private List<UnitGroup> unitGroups(...)` 메서드 전체를 아래로 교체:

```java
    private List<UnitGroup> unitGroups(String pnu, List<LicensedBusinessRecordEntity> records) {
        List<List<LicensedBusinessRecordEntity>> primaryGroups = groupByKey(records, this::unitKey);
        List<List<LicensedBusinessRecordEntity>> resolvedGroups = primaryGroups.stream()
            .flatMap(this::resolveContention)
            .toList();

        List<UnitGroup> groups = new ArrayList<>();
        int index = 1;
        for (List<LicensedBusinessRecordEntity> groupRecords : resolvedGroups) {
            groups.add(new UnitGroup(unitId(pnu, index), groupRecords));
            index++;
        }
        return groups;
    }

    private Stream<List<LicensedBusinessRecordEntity>> resolveContention(List<LicensedBusinessRecordEntity> group) {
        // 구체적 호실번호(UNIT:: 키)가 있는 그룹은 겹쳐도 그대로 둔다 — 실측 결과 실제 문제
        // 사례(가락시장/AK플라자/백현동/롯데백화점)는 전부 호실번호 없는 케이스였고, 있는데
        // 겹치는 경우는 대부분 폐업신고 누락으로 보는 게 더 합리적(기존 회귀 테스트도 이 전제).
        if (unitKey(group.get(0)).startsWith("UNIT::") || !isContended(group)) {
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

    private String addressTextKey(LicensedBusinessRecordEntity record) {
        return normalize(record.getJibunAddress()) + "::" + normalize(record.getRoadAddress());
    }
```

(`unitKey()`, `normalize()`, `mergeByGap()`는 기존 그대로 — 손대지 않는다)

업로드하고 방금 작성한 테스트로 확인:

```bash
scp -i "C:\Users\soldesk\key_turbom_v0.key" <로컬경로>\TenancyQueryService.java ubuntu@132.226.231.92:/home/ubuntu/app-build/src/main/java/com/nextstep/application/TenancyQueryService.java
ssh -i "C:\Users\soldesk\key_turbom_v0.key" ubuntu@132.226.231.92 "cd /home/ubuntu/app-build && mvn -q -Dtest=TenancyQueryServiceTest#층만_겹치고_지번주소가_다르면_지번주소로_재분리된다 test 2>&1 | tail -60"
```

기대 결과: PASS

- [ ] **Step 3: 3차 키(상호명)까지 내려가는 시나리오 — 실패하는 테스트 작성**

`TenancyQueryServiceTest.java`에 상수 추가:

```java
    private static final String OVERLAP_TIER3_PNU = "4113110100100930000";
    private static final String DELETE_OVERLAP_TIER3_PNU =
        "DELETE FROM licensed_business_record WHERE pnu = '" + OVERLAP_TIER3_PNU + "'";
    // 시나리오: 층/호도 같고(B1/없음) 지번·도로명 원문도 완전히 동일 -> 2차 키로도 해소 안 됨,
    // 3차(상호명)까지 내려가야 함(가락시장처럼 상세주소 자체에 구분 정보가 전혀 없는 경우)
    private static final String INSERT_OVERLAP_TIER3_C =
        "INSERT INTO licensed_business_record "
            + "(id, pnu, category, sub_category, license_no, business_name, business_type, business_status, "
            + "status_detail_code, status_detail, licensed_at, closed_at, road_address, jibun_address, "
            + "address_separated, address_corrected, parsed_building_name, parsed_floor, parsed_unit_no, "
            + "parse_confidence, parse_method, local_gov_code, original_x, original_y) "
            + "VALUES (9900000501, '4113110100100930000', '식품', '식품소분업', 'test-license-50', '가락상회C', "
            + "NULL, '영업/정상', '0000', '정상', '2020-01-01', NULL, "
            + "'경기도 성남시 수정구 테스트로 8, 지하1층 일부호 (테스트동)', '경기도 성남시 수정구 테스트동 93 지하1층', "
            + "FALSE, TRUE, NULL, 'B1', NULL, 'HIGH', 'REGEX', '3780000', 212818.475436898, 438579.588327304)";
    private static final String INSERT_OVERLAP_TIER3_D =
        "INSERT INTO licensed_business_record "
            + "(id, pnu, category, sub_category, license_no, business_name, business_type, business_status, "
            + "status_detail_code, status_detail, licensed_at, closed_at, road_address, jibun_address, "
            + "address_separated, address_corrected, parsed_building_name, parsed_floor, parsed_unit_no, "
            + "parse_confidence, parse_method, local_gov_code, original_x, original_y) "
            + "VALUES (9900000502, '4113110100100930000', '식품', '식품소분업', 'test-license-51', '가락상회D', "
            + "NULL, '영업/정상', '0000', '정상', '2020-06-01', NULL, "
            + "'경기도 성남시 수정구 테스트로 8, 지하1층 일부호 (테스트동)', '경기도 성남시 수정구 테스트동 93 지하1층', "
            + "FALSE, TRUE, NULL, 'B1', NULL, 'HIGH', 'REGEX', '3780000', 212818.475436898, 438579.588327304)";
```

테스트 추가:

```java
    @Test
    @Sql(statements = {DELETE_OVERLAP_TIER3_PNU, INSERT_OVERLAP_TIER3_C, INSERT_OVERLAP_TIER3_D})
    @Sql(statements = DELETE_OVERLAP_TIER3_PNU, executionPhase = Sql.ExecutionPhase.AFTER_TEST_METHOD)
    void 지번주소까지_같으면_상호명으로_재분리된다() {
        Site site = tenancyQueryService.findSiteWithUnits(OVERLAP_TIER3_PNU).orElseThrow();

        assertThat(site.units()).hasSize(2);
        assertThat(site.units()).flatExtracting(Unit::tenancies)
            .extracting(Tenancy::businessName)
            .containsExactlyInAnyOrder("가락상회C", "가락상회D");
        assertThat(site.units()).allSatisfy(unit -> assertThat(unit.tenancies()).hasSize(1));
    }
```

업로드 후 실행해서 실패 확인(2차 키까지만 구현된 상태라 여전히 1개 그룹으로 뭉쳐 있어야 함):

```bash
scp -i "C:\Users\soldesk\key_turbom_v0.key" <로컬경로>\TenancyQueryServiceTest.java ubuntu@132.226.231.92:/home/ubuntu/app-build/src/test/java/com/nextstep/application/TenancyQueryServiceTest.java
ssh -i "C:\Users\soldesk\key_turbom_v0.key" ubuntu@132.226.231.92 "cd /home/ubuntu/app-build && mvn -q -Dtest=TenancyQueryServiceTest#지번주소까지_같으면_상호명으로_재분리된다 test 2>&1 | tail -60"
```

기대 결과: FAIL — 잠깐, `resolveContention`은 이미 Step 2에서 3차(상호명) 에스컬레이션까지
구현돼 있다(`groupByKey(subGroup, LicensedBusinessRecordEntity::getBusinessName)` 부분). 이
시나리오는 이미 Step 2 구현으로 통과할 가능성이 높다 — 그렇다면 이 Step은 "테스트 작성 → 이미
통과함을 확인"으로 진행(구현이 이미 완전해서 실패하는 테스트를 못 만드는 경우, 통과 확인만 하고
다음으로 넘어간다). 만약 실제로 FAIL한다면 `resolveContention`의 3차 에스컬레이션 부분을
다시 점검한다.

```bash
ssh -i "C:\Users\soldesk\key_turbom_v0.key" ubuntu@132.226.231.92 "cd /home/ubuntu/app-build && mvn -q -Dtest=TenancyQueryServiceTest#지번주소까지_같으면_상호명으로_재분리된다 test 2>&1 | tail -60"
```

기대 결과: PASS (Step 2의 구현이 이미 3단계 캐스케이드 전체를 포함하고 있어서)

- [ ] **Step 4: 당일 인수인계는 분리되지 않음 — 회귀 잠금 테스트**

상수 추가:

```java
    private static final String SAME_DAY_HANDOVER_PNU = "4113110100100920000";
    private static final String DELETE_SAME_DAY_HANDOVER_PNU =
        "DELETE FROM licensed_business_record WHERE pnu = '" + SAME_DAY_HANDOVER_PNU + "'";
    // 시나리오: 다른 상호지만 A 폐업일 == B 개업일(당일 인수인계) -> 겹침 아님, 분리되면 안 됨
    private static final String INSERT_SAME_DAY_HANDOVER_A =
        "INSERT INTO licensed_business_record "
            + "(id, pnu, category, sub_category, license_no, business_name, business_type, business_status, "
            + "status_detail_code, status_detail, licensed_at, closed_at, road_address, jibun_address, "
            + "address_separated, address_corrected, parsed_building_name, parsed_floor, parsed_unit_no, "
            + "parse_confidence, parse_method, local_gov_code, original_x, original_y) "
            + "VALUES (9900000601, '4113110100100920000', '식품', '식품소분업', 'test-license-60', '인수인계전', "
            + "NULL, '폐업', '0002', '폐업', '2019-01-01', '2020-01-01', "
            + "'경기도 성남시 수정구 테스트로 9, 지하1층 일부호 (테스트동)', '경기도 성남시 수정구 테스트동 92 지하1층', "
            + "FALSE, TRUE, NULL, 'B1', NULL, 'HIGH', 'REGEX', '3780000', 212818.475436898, 438579.588327304)";
    private static final String INSERT_SAME_DAY_HANDOVER_B =
        "INSERT INTO licensed_business_record "
            + "(id, pnu, category, sub_category, license_no, business_name, business_type, business_status, "
            + "status_detail_code, status_detail, licensed_at, closed_at, road_address, jibun_address, "
            + "address_separated, address_corrected, parsed_building_name, parsed_floor, parsed_unit_no, "
            + "parse_confidence, parse_method, local_gov_code, original_x, original_y) "
            + "VALUES (9900000602, '4113110100100920000', '식품', '식품소분업', 'test-license-61', '인수인계후', "
            + "NULL, '영업/정상', '0000', '정상', '2020-01-01', NULL, "
            + "'경기도 성남시 수정구 테스트로 9, 지하1층 일부호 (테스트동)', '경기도 성남시 수정구 테스트동 92 지하1층', "
            + "FALSE, TRUE, NULL, 'B1', NULL, 'HIGH', 'REGEX', '3780000', 212818.475436898, 438579.588327304)";
```

테스트 추가:

```java
    @Test
    @Sql(statements = {DELETE_SAME_DAY_HANDOVER_PNU, INSERT_SAME_DAY_HANDOVER_A, INSERT_SAME_DAY_HANDOVER_B})
    @Sql(statements = DELETE_SAME_DAY_HANDOVER_PNU, executionPhase = Sql.ExecutionPhase.AFTER_TEST_METHOD)
    void 당일_인수인계는_겹침이_아니라_분리되지_않는다() {
        Site site = tenancyQueryService.findSiteWithUnits(SAME_DAY_HANDOVER_PNU).orElseThrow();

        assertThat(site.units()).hasSize(1);
        assertThat(site.units().get(0).tenancies()).hasSize(2);
        assertThat(site.units().get(0).tenancies())
            .extracting(Tenancy::businessName)
            .containsExactlyInAnyOrder("인수인계전", "인수인계후");
    }
```

업로드 후 실행:

```bash
scp -i "C:\Users\soldesk\key_turbom_v0.key" <로컬경로>\TenancyQueryServiceTest.java ubuntu@132.226.231.92:/home/ubuntu/app-build/src/test/java/com/nextstep/application/TenancyQueryServiceTest.java
ssh -i "C:\Users\soldesk\key_turbom_v0.key" ubuntu@132.226.231.92 "cd /home/ubuntu/app-build && mvn -q -Dtest=TenancyQueryServiceTest#당일_인수인계는_겹침이_아니라_분리되지_않는다 test 2>&1 | tail -60"
```

기대 결과: PASS (Step 2에서 만든 `OccupancySpan.overlaps()`의 엄격한 부등호 덕분에 이미 맞게
동작해야 함 — 만약 FAIL이면 `isContended`/`occupancySpans`의 날짜 경계 처리를 재검토)

- [ ] **Step 5: 전체 테스트 스위트로 회귀 확인**

```bash
ssh -i "C:\Users\soldesk\key_turbom_v0.key" ubuntu@132.226.231.92 "cd /home/ubuntu/app-build && mvn -q clean test 2>&1 | tail -30; echo '---SUMMARY---'; grep 'Tests run' target/surefire-reports/*.txt"
```

기대 결과: 모든 클래스 `Failures: 0, Errors: 0`, 특히
`TenancyQueryServiceTest`(기존 16개 + 신규 3개 = 19개)와
`SiteControllerTest`(14개, 그대로) 확인. 하나라도 실패하면 — 특히
`같은_Unit이라도_businessName이_다르면_병합하지_않는다`나
`층이_생략된_레코드는_같은_호실번호면_병합된다`가 실패하면 — `resolveContention`의 `UNIT::`
제외 조건이 제대로 동작하는지부터 확인한다(원인 없이 임의로 조건을 바꾸지 않는다).

- [ ] **Step 6: 커밋**

```bash
ssh -i "C:\Users\soldesk\key_turbom_v0.key" ubuntu@132.226.231.92 "cd /home/ubuntu/app-build && git add src/main/java/com/nextstep/application/TenancyQueryService.java src/test/java/com/nextstep/application/TenancyQueryServiceTest.java && git commit -m 'feat: split Units when different businesses occupied the same address key concurrently

FLOOR::/raw-text-keyed groups (no specific unit number) that show two
different business names open at overlapping times are re-split, first by
raw jibun/road address text, then by business name if that still collides
(가락시장/AK플라자/백현동/롯데백화점 실측 사례). UNIT:: keyed groups (specific
parsed unit number) are left untouched -- real collapse cases never had a
unit number, and overlapping different names there are more likely stale
unreported closures than genuine second physical spots.'"
```

---

### Task 3: 배포 확인 + 문서 동기화

**Files:**
- Modify (turbom-spec repo, `/home/ubuntu/turbom-spec`): `backend-spec.md`, `CHANGELOG.md`,
  `의사결정-기록.md`

**Interfaces:** 없음(코드 변경 없음, 검증·문서화만)

- [ ] **Step 1: 사용자에게 push 확인 받기**

이 변경은 검색 API의 `unitCount`/`units[]` 응답을 바꾼다(대형 상가·시장 PNU에서 Unit 수가
크게 늘어날 수 있음). 이전 세션 패턴대로 push 전 사용자에게 확인:
"Unit 충돌감지/재분리 변경사항을 원격에 push할까요? (대형 상가/시장 PNU의 unitCount가 늘어날
수 있습니다)" — AskUserQuestion으로 "지금 push" / "커밋만 유지" 선택받기.

- [ ] **Step 2: push 및 CI/CD 배포 확인**

```bash
ssh -i "C:\Users\soldesk\key_turbom_v0.key" ubuntu@132.226.231.92 "cd /home/ubuntu/app-build && git push"
```

푸시 후 GitHub Actions 실행을 확인(`gh run watch <run-id> --repo nn98/turbom-server --exit-status`
또는 `gh run list --repo nn98/turbom-server --limit 3`로 run id 확인).

- [ ] **Step 3: 실사례로 배포 검증**

```bash
ssh -i "C:\Users\soldesk\key_turbom_v0.key" ubuntu@132.226.231.92 "curl -s 'http://localhost:8080/api/sites/1171010700006000000' | python3 -c 'import json,sys; d=json.load(sys.stdin); print(\"unitCount:\", len(d.get(\"units\", [])))'"
```

가락시장(`1171010700006000000`)의 `unitCount`가 이전(1개)보다 크게 늘어났는지 확인. 참고:
운영 데이터에는 테스트에서 다루지 않은 추가 변형(예: 지번주소도 부분적으로만 다른 경우 등)이
있을 수 있어 정확히 "700개"가 될 거라 장담할 수 없음 — 1보다 훨씬 커졌다는 것과 API가 정상
응답한다는 것만 확인하면 충분.

- [ ] **Step 4: turbom-spec 문서 동기화**

`backend-spec.md`의 Unit 그룹핑 관련 서술(`unitKey`/`unitGroups` 언급 부분)에 충돌감지/재분리
단계 한 단락 추가, `CHANGELOG.md`에 번호 매겨 항목 추가(이번 세션 24차 다음 번호),
`의사결정-기록.md`에 새 절 추가(배경: 가락시장/AK플라자/백현동/롯데백화점 실측, 퍼지 유사도
매칭 보류 판단, `UNIT::` 제외 스코프 판단 근거). 정확한 문구는 실제 구현/배포 결과(Step 3의
실측값)를 반영해서 작성 — 이 계획 문서의 사전 추측 문구를 그대로 복사하지 않는다.

```bash
ssh -i "C:\Users\soldesk\key_turbom_v0.key" ubuntu@132.226.231.92 "cd /home/ubuntu/turbom-spec && git pull --ff-only"
```
(이후 이전 세션과 동일한 패턴: archive 스냅샷 → 수정 → commit → push)

- [ ] **Step 5: 커밋 및 push**

```bash
ssh -i "C:\Users\soldesk\key_turbom_v0.key" ubuntu@132.226.231.92 "cd /home/ubuntu/turbom-spec && git add -A && git commit -m '<실제 배포 결과 반영한 커밋메시지>' && git push"
```

---

## Self-Review 체크리스트 (계획 작성자가 직접 확인함)

- **스펙 커버리지**: 설계문서의 겹침판정(`OccupancySpan`, Task 1) / 충돌감지+3단계 캐스케이드
  (Task 2) / `UNIT::` 제외 스코프(Task 2 Step 2) / 문서화 계획(Task 3) 전부 태스크로 매핑됨.
- **placeholder 스캔**: "TBD"/"나중에" 없음. Task 3 Step 4의 정확한 문구만 실측 결과 대기 —
  이건 아직 실행 전이라 결과를 알 수 없어서가 아니라(플레이스홀더), 오히려 사전에 추측해서
  박아넣는 게 거짓 기록이 되는 걸 막기 위한 의도적 지연이라 문제 아님.
  (다른 세션들도 이 저장소에서 항상 "실제 배포 결과 확인 후 문서화" 순서를 지켰음)
- **타입 일관성**: `OccupancySpan(String ownerKey, LocalDate start, LocalDate endOrNull)` —
  Task 1 정의와 Task 2의 `occupancySpans()` 사용부 시그니처 일치 확인함.
