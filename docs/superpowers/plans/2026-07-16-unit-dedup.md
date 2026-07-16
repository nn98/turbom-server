# Unit 중복 제거 — 상세주소 정규화 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 같은 PNU 안에서 상세주소 표현이 달라도 동일 층·호실이면 하나의 Unit으로 집계되게 하고, 불량 데이터로 인한 500 에러를 방어 처리한다.

**Architecture:** `TenancyQueryService.unitKey()`가 현재 raw jibunAddress 문자열을 키로 쓰는데, 이를 DB에 이미 파싱·저장된 `parsedFloor / parsedUnitNo / parsedBuildingName` 기준으로 교체한다. 부수적으로 `TenancyPeriod` 생성자의 null/역순 날짜 방어 처리와 `GlobalExceptionHandler` 로깅을 추가해 500 에러 원인을 추적 가능하게 만든다.

**Tech Stack:** Java 21, Spring Boot 3.x, JUnit 5 + Mockito (기존 테스트 스택 동일)

## Global Constraints

- 테스트: `mvn test` 전체 통과 상태 유지 (기존 테스트 깨지면 안 됨)
- API 계약 변경 없음 — 응답 JSON 필드 구조 동일, `label` 문자열 변경만 허용
- 커밋: 태스크 단위로 잦게 커밋
- 불량 데이터 처리: throw 대신 skip / 기본값으로 graceful 처리
- `AddressDetailParser`는 이미 DB에 결과가 저장돼 있으므로 런타임에 다시 호출하지 않음

---

## File Map

| 파일 | 역할 |
|---|---|
| `src/main/java/com/nextstep/application/TenancyQueryService.java` | **주 변경 대상** — `unitKey()`, `unitLabel()` |
| `src/main/java/com/nextstep/domain/tenancy/TenancyPeriod.java` | null/역순 날짜 방어 |
| `src/main/java/com/nextstep/web/GlobalExceptionHandler.java` | 예외 로깅 추가 |
| `src/test/java/com/nextstep/application/TenancyQueryServiceTest.java` | 신규 — unitKey 동작 검증 |

---

### Task 1: GlobalExceptionHandler에 로깅 추가 (방어 먼저)

현재 예상치 못한 예외를 스택트레이스 없이 삼키고 있어 500 원인 추적이 불가능하다.  
이후 태스크에서 불량 데이터로 인한 에러가 발생해도 즉시 확인할 수 있게 먼저 픽스한다.

**Files:**
- Modify: `src/main/java/com/nextstep/web/GlobalExceptionHandler.java`

- [x] **Step 1: 현재 파일 확인**

```java
// 현재 handleUnexpected — 스택트레이스가 로그에 찍히지 않는다
@ExceptionHandler(Exception.class)
public ResponseEntity<ErrorResponse> handleUnexpected(Exception e) {
    return ResponseEntity.internalServerError().body(new ErrorResponse("INTERNAL_ERROR", "서버 오류가 발생했습니다."));
}
```

- [x] **Step 2: 로깅 추가**

`GlobalExceptionHandler.java` 상단에 `import` 추가 후 핸들러 수정:

```java
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    // ... 기존 핸들러들 유지 ...

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleUnexpected(Exception e) {
        log.error("Unhandled exception", e);   // ← 이 줄만 추가
        return ResponseEntity.internalServerError().body(new ErrorResponse("INTERNAL_ERROR", "서버 오류가 발생했습니다."));
    }
}
```

- [x] **Step 3: 컴파일 확인**

```
mvn compile -q
```
Expected: BUILD SUCCESS

- [x] **Step 4: 커밋** — `0a9e07e fix: log unexpected exceptions in GlobalExceptionHandler`

---

### Task 2: TenancyPeriod 불량 데이터 방어 처리

`licensedAt=null` 또는 `closedAt < licensedAt` 레코드가 존재하면 현재 생성자가 throw해서 해당 PNU 전체가 500이 된다.  
불량 레코드를 skip하거나 날짜를 보정해 전체 조회가 중단되지 않게 한다.

**Files:**
- Modify: `src/main/java/com/nextstep/domain/tenancy/TenancyPeriod.java`
- Modify: `src/main/java/com/nextstep/application/TenancyQueryService.java` (toTenancy 내 방어)

- [x] **Step 1: TenancyPeriod — closedAt 역순 보정**

`licensedAt=null`이면 레코드 자체를 skip하므로 TenancyPeriod까지 오지 않는다 (Step 2에서 처리).  
`closedAt < licensedAt` 은 closedAt을 null로 대체해 "아직 영업중"으로 취급:

```java
public record TenancyPeriod(LocalDate licensedAt, LocalDate closedAt) {
    public TenancyPeriod {
        if (licensedAt == null) {
            throw new IllegalArgumentException("licensedAt은 필수입니다.");
        }
        // ponytail: 역순 날짜는 throw 대신 null 처리 — 원본 throw는 toTenancy에서 걸러진 후여야 의미있음
        if (closedAt != null && closedAt.isBefore(licensedAt)) {
            closedAt = null;
        }
    }

    public int survivalMonths() {
        LocalDate end = closedAt != null ? closedAt : LocalDate.now();
        return (int) ChronoUnit.MONTHS.between(licensedAt, end);
    }
}
```

- [x] **Step 2: TenancyQueryService.toTenancy() — licensedAt=null 레코드 skip**

`toTenancy()`에서 null 날짜 레코드를 필터링:

```java
// 기존 toUnits 내부
private List<Unit> toUnits(String pnu, List<LicensedBusinessRecordEntity> records) {
    return unitGroups(pnu, records).stream()
        .map(this::toUnit)
        .toList();
}

// unitGroups로 가기 전 records 필터링 — licensedAt null이면 데이터 무결성 문제로 skip
private List<LicensedBusinessRecordEntity> validRecords(List<LicensedBusinessRecordEntity> records) {
    return records.stream()
        .filter(r -> r.getLicensedAt() != null)
        .toList();
}
```

`toSite()`, `toSiteWithoutUnits()`, `findUnitWithTenancies()` 에서 records를 사용하기 전에 `validRecords()` 를 통과시킨다:

```java
private Site toSite(List<LicensedBusinessRecordEntity> records) {
    List<LicensedBusinessRecordEntity> valid = validRecords(records);
    LicensedBusinessRecordEntity representative = valid.isEmpty() ? records.get(0) : valid.get(0);
    return new Site(new Pnu(representative.getPnu()), representative.getJibunAddress(),
        representative.getRoadAddress(), KoreanTmCoordinateConverter
            .fromEpsg5174(representative.getOriginalX(), representative.getOriginalY())
            .orElse(null), toUnits(representative.getPnu(), valid));
}

private Site toSiteWithoutUnits(List<LicensedBusinessRecordEntity> records) {
    List<LicensedBusinessRecordEntity> valid = validRecords(records);
    LicensedBusinessRecordEntity representative = valid.isEmpty() ? records.get(0) : valid.get(0);
    return new Site(new Pnu(representative.getPnu()), representative.getJibunAddress(),
        representative.getRoadAddress(), KoreanTmCoordinateConverter
            .fromEpsg5174(representative.getOriginalX(), representative.getOriginalY())
            .orElse(null), List.of());
}
```

`findUnitWithTenancies()` 내 `groups` 생성 전:
```java
List<LicensedBusinessRecordEntity> records = recordRepository.findByPnuOrderByLicensedAtAscIdAsc(unitReference.get().pnu());
if (records.isEmpty()) return Optional.empty();

List<UnitGroup> groups = unitGroups(unitReference.get().pnu(), validRecords(records));  // ← validRecords 추가
```

- [x] **Step 3: 컴파일**

```
mvn compile -q
```
Expected: BUILD SUCCESS

- [x] **Step 4: 기존 테스트 통과 확인**

```
mvn test -q
```
Expected: BUILD SUCCESS (기존 MarketInfoServiceTest 포함 전체 통과)

- [x] **Step 5: 커밋** — Task 3과 함께 `4ca1e38 feat: normalize unit key by parsed floor/unit and guard against bad date data`로 병합 커밋됨(계획서상 별도 커밋 예정이었으나 실제로는 한 커밋)

---

### Task 3: unitKey 정규화 — 핵심 구현

`unitKey()`를 jibunAddress 문자열 대신 파싱된 위치 속성 기준으로 교체한다.

**Files:**
- Modify: `src/test/java/com/nextstep/application/TenancyQueryServiceTest.java` (신규 생성)
- Modify: `src/main/java/com/nextstep/application/TenancyQueryService.java`

#### 3-A: 실패하는 테스트 먼저

- [x] **Step 1: 테스트 파일 생성**

`src/test/java/com/nextstep/application/TenancyQueryServiceTest.java`:

```java
package com.nextstep.application;

import com.nextstep.infra.persistence.LicensedBusinessRecordEntity;
import com.nextstep.infra.persistence.LicensedBusinessRecordRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TenancyQueryServiceTest {

    @Mock
    LicensedBusinessRecordRepository repository;

    @InjectMocks
    TenancyQueryService service;

    @Test
    void 동일_층호_다른_jibunAddress는_하나의_Unit으로_집계된다() {
        // 창곡동 509 실제 패턴: "703호"가 jibunAddress 표현만 달리해서 두 레코드
        var record1 = makeRecord(1L, "4113110800105090000",
            "위례광장로 300, 위례중앙타워 7층 703호 (창곡동)",
            "창곡동 509 위례중앙역 중앙타워 703호",
            null, "703", null, "HIGH", "REGEX");
        var record2 = makeRecord(2L, "4113110800105090000",
            "위례광장로 300, 위례중앙타워 7층 703호 (창곡동)",
            "창곡동 509번지 위례중앙역 중앙타워 703호",  // 번지 표기 차이
            null, "703", null, "HIGH", "REGEX");

        when(repository.findByPnuOrderByLicensedAtAscIdAsc("4113110800105090000"))
            .thenReturn(List.of(record1, record2));

        var site = service.findSiteWithUnits("4113110800105090000").orElseThrow();

        assertThat(site.units()).hasSize(1);
        assertThat(site.units().get(0).tenancies()).hasSize(2);
    }

    @Test
    void 상세주소_없는_레코드_여럿은_단일_상세주소불명_Unit으로_합산된다() {
        var record1 = makeRecord(1L, "PNU001",
            "위례광장로 300 (창곡동)", "창곡동 509",
            null, null, null, "HIGH", "NONE");
        var record2 = makeRecord(2L, "PNU001",
            "위례광장로 300 (창곡동)", "창곡동 509 ",  // trailing space
            null, null, null, "HIGH", "NONE");

        when(repository.findByPnuOrderByLicensedAtAscIdAsc("PNU001"))
            .thenReturn(List.of(record1, record2));

        var site = service.findSiteWithUnits("PNU001").orElseThrow();

        assertThat(site.units()).hasSize(1);
        assertThat(site.units().get(0).label()).isEqualTo("단일(상세주소불명)");
        assertThat(site.units().get(0).tenancies()).hasSize(2);
    }

    @Test
    void 다른_층호는_별도_Unit으로_유지된다() {
        var floor1 = makeRecord(1L, "PNU001",
            "어딘가로 1, 1층 101호 (동)", "창곡동 1 1층 101호",
            "1", "101", null, "HIGH", "REGEX");
        var floor2 = makeRecord(2L, "PNU001",
            "어딘가로 1, 2층 201호 (동)", "창곡동 1 2층 201호",
            "2", "201", null, "HIGH", "REGEX");

        when(repository.findByPnuOrderByLicensedAtAscIdAsc("PNU001"))
            .thenReturn(List.of(floor1, floor2));

        var site = service.findSiteWithUnits("PNU001").orElseThrow();

        assertThat(site.units()).hasSize(2);
    }

    @Test
    void UNPARSED_레코드는_상세문자열_기준으로_그룹핑된다() {
        // 동일한 UNPARSED 상세 문자열 → 같은 Unit
        var r1 = makeRecord(1L, "PNU001",
            "어딘가로 1, 위례중앙타워 4층 410~416호 (동)", "창곡동 509 4층",
            "위례중앙타워 4층 410~416호", null, null, "LOW", "UNPARSED");
        var r2 = makeRecord(2L, "PNU001",
            "어딘가로 1, 위례중앙타워 4층 410~416호 (동)", "창곡동 509번지 4층",
            "위례중앙타워 4층 410~416호", null, null, "LOW", "UNPARSED");
        // 다른 UNPARSED → 다른 Unit
        var r3 = makeRecord(3L, "PNU001",
            "어딘가로 1, 위례중앙타워 5층 510호 (동)", "창곡동 509 5층",
            "위례중앙타워 5층 510호", null, null, "LOW", "UNPARSED");

        when(repository.findByPnuOrderByLicensedAtAscIdAsc("PNU001"))
            .thenReturn(List.of(r1, r2, r3));

        var site = service.findSiteWithUnits("PNU001").orElseThrow();

        assertThat(site.units()).hasSize(2);
    }

    // ── 헬퍼 ──────────────────────────────────────────────────────────────

    private LicensedBusinessRecordEntity makeRecord(
        Long id, String pnu, String roadAddress, String jibunAddress,
        String parsedFloor, String parsedUnitNo, String parsedBuildingName,
        String parseConfidence, String parseMethod
    ) {
        // LicensedBusinessRecordEntity는 protected 기본 생성자만 있으므로
        // reflection으로 필드를 설정한다.
        try {
            var entity = new LicensedBusinessRecordEntity() {};  // 익명 서브클래스로 접근
            setField(entity, "id", id);
            setField(entity, "pnu", pnu);
            setField(entity, "roadAddress", roadAddress);
            setField(entity, "jibunAddress", jibunAddress);
            setField(entity, "parsedFloor", parsedFloor);
            setField(entity, "parsedUnitNo", parsedUnitNo);
            setField(entity, "parsedBuildingName", parsedBuildingName);
            setField(entity, "parseConfidence", parseConfidence);
            setField(entity, "parseMethod", parseMethod);
            setField(entity, "businessName", "테스트업체" + id);
            setField(entity, "businessStatus", "영업/정상");
            setField(entity, "licensedAt", LocalDate.of(2020, 1, 1));
            setField(entity, "locationSource", "LICENSE");
            return entity;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private void setField(Object obj, String fieldName, Object value) throws Exception {
        // 부모 클래스 필드까지 탐색
        Class<?> clazz = obj.getClass();
        while (clazz != null) {
            try {
                var field = clazz.getDeclaredField(fieldName);
                field.setAccessible(true);
                field.set(obj, value);
                return;
            } catch (NoSuchFieldException e) {
                clazz = clazz.getSuperclass();
            }
        }
        throw new NoSuchFieldException(fieldName + " not found in " + obj.getClass());
    }
}
```

- [x] **Step 2: 테스트 실행 — 실패 확인**

```
mvn test -pl . -Dtest=TenancyQueryServiceTest -q
```
Expected: FAIL (현재 unitKey가 jibunAddress 기준이므로 Unit 수가 예상보다 많음)

#### 3-B: 구현

- [x] **Step 3: TenancyQueryService.unitKey() 교체**

`src/main/java/com/nextstep/application/TenancyQueryService.java` 내 `unitKey()` 메서드 교체:

```java
private String unitKey(LicensedBusinessRecordEntity record) {
    if (AddressDetailParser.CONFIDENCE_LOW.equals(record.getParseConfidence())) {
        // UNPARSED: 파싱 불가한 상세 문자열(parsedBuildingName에 저장됨)을 키로 사용
        String detail = record.getParsedBuildingName();
        return detail != null ? normalize(detail) : "__unknown__";
    }
    // HIGH (NONE 또는 REGEX): 구조화된 위치 속성 기준 — jibunAddress 표현 무관
    return record.getParsedFloor() + "::" + record.getParsedUnitNo() + "::" + record.getParsedBuildingName();
}
```

이미 `AddressDetailParser` import가 있으므로 추가 import 불필요.

- [x] **Step 4: unitLabel() 텍스트 수정**

`unitLabel()` 내 "단일 점포" → "단일(상세주소불명)" (parseMethod == NONE 케이스):

```java
private String unitLabel(LicensedBusinessRecordEntity record) {
    if (!AddressDetailParser.CONFIDENCE_HIGH.equals(record.getParseConfidence())) {
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
    return "단일(상세주소불명)";  // HIGH + NONE: 파싱 성공했지만 상세 없음
}
```

- [x] **Step 5: 테스트 실행 — 통과 확인** — 실측 8개 테스트 PASS (계획 시점 4개 예상보다 늘어남, TenancyPeriodTest 등 포함 총 11개 전체 통과)

- [x] **Step 6: 전체 테스트 통과 확인** — `mvn test -Dtest=TenancyQueryServiceTest,TenancyPeriodTest` 재확인: `Tests run: 11, Failures: 0, Errors: 0`

- [x] **Step 7: 커밋** — `4ca1e38`

---

### Task 4: 배포 확인

- [x] **Step 1: 푸시** — origin/main = `4ca1e38` (이미 반영됨)

- [x] **Step 2: CI 통과 확인** — GitHub Actions run 29486095059, `4ca1e38` 커밋, **success** (test + deploy 잡 모두 통과, self-hosted 러너가 systemctl로 서비스까지 재시작)

- [x] **Step 3: 창곡동 509 상세 조회 확인** — 실제 엔드포인트는 `/detail` 접미사 없이 `GET /api/sites/{pnu}` (계획서의 URL 오기 수정). 재확인 결과:
  ```bash
  curl -s https://turbom.duckdns.org/api/sites/4113110800105090000
  ```
  HTTP 200 (500 에러 사라짐). raw 레코드 470건 → Unit 281개로 병합(21개 그룹이 실제 다건 병합, 최대 병합은 "단일(상세주소불명)" 버킷 144건). 계획서에 적힌 "249"는 사전 추정치였을 뿐 실측 기준선이 아니었음 — 병합 로직 자체는 의도대로 동작 확인됨.

---

## 한계 및 후속 작업

| 케이스 | 현재 결과 | 이유 |
|---|---|---|
| `"1층 119호"` vs `"119호"` (층 생략) | 여전히 별도 Unit | parsedFloor 값 다름, 보정 불가 |
| UNPARSED 범위 표기 `"410~416호"` vs `"410~421호"` | 별도 Unit | 같은 공간이지만 면적 변경 이력, 의도적 분리 |
| 좌표 없는 일부 Site cross-PNU 중복 | 미처리 | 별도 이슈로 분리 |
