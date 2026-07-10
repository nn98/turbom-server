### Task 3: 도메인 값객체 — Pnu / Coordinate / BusinessStatus / TenancyPeriod

**Files:**
- Create: `server/src/main/java/com/nextstep/domain/site/Pnu.java`
- Create: `server/src/main/java/com/nextstep/domain/site/Coordinate.java`
- Create: `server/src/main/java/com/nextstep/domain/unit/LocationSource.java`
- Create: `server/src/main/java/com/nextstep/domain/tenancy/BusinessStatus.java`
- Create: `server/src/main/java/com/nextstep/domain/tenancy/TenancyPeriod.java`
- Test: `server/src/test/java/com/nextstep/domain/tenancy/TenancyPeriodTest.java`

**Interfaces:**
- Produces: `Pnu(String value)`, `Coordinate(double latitude, double longitude)`, `LocationSource{LICENSE,SANGGA_API,OVERLAP_INFERRED}` with `dbValue()`/`fromDb(String)`, `BusinessStatus{ACTIVE,CLOSED,SUSPENDED}` with `display()`/`fromDb(String)`, `TenancyPeriod(LocalDate licensedAt, LocalDate closedAt)` with `survivalMonths()`.

- [ ] **Step 1: 실패하는 테스트 작성**

```java
package com.nextstep.domain.tenancy;

import org.junit.jupiter.api.Test;
import java.time.LocalDate;
import static org.assertj.core.api.Assertions.assertThat;

class TenancyPeriodTest {

    @Test
    void 폐업이면_인허가일부터_폐업일까지_개월수를_계산한다() {
        TenancyPeriod period = new TenancyPeriod(LocalDate.of(2013, 5, 2), LocalDate.of(2017, 1, 10));
        assertThat(period.survivalMonths()).isEqualTo(44);
    }

    @Test
    void 영업중이면_폐업일이_null이다() {
        TenancyPeriod period = new TenancyPeriod(LocalDate.of(2023, 1, 15), null);
        assertThat(period.closedAt()).isNull();
    }

    @Test
    void 폐업일이_인허가일보다_빠르면_예외() {
        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class,
            () -> new TenancyPeriod(LocalDate.of(2020, 1, 1), LocalDate.of(2019, 1, 1)));
    }
}
```

- [ ] **Step 2: 테스트 실행 → 실패 확인**

Run: `cd server && mvn -q test -Dtest=TenancyPeriodTest`
Expected: FAIL — `TenancyPeriod` 클래스가 없어 컴파일 에러.

- [ ] **Step 3: Pnu 작성**

```java
package com.nextstep.domain.site;

public record Pnu(String value) {
    public Pnu {
        if (value == null || !value.matches("\\d{19}")) {
            throw new IllegalArgumentException("PNU는 19자리 숫자여야 합니다: " + value);
        }
    }

    public String legalDongCode() {
        return value.substring(0, 10);
    }
}
```

- [ ] **Step 4: Coordinate 작성**

```java
package com.nextstep.domain.site;

public record Coordinate(double latitude, double longitude) {
}
```

- [ ] **Step 5: LocationSource 작성**

```java
package com.nextstep.domain.unit;

public enum LocationSource {
    LICENSE("license"),
    SANGGA_API("sangga_api"),
    OVERLAP_INFERRED("overlap_inferred");

    private final String dbValue;

    LocationSource(String dbValue) {
        this.dbValue = dbValue;
    }

    public String dbValue() {
        return dbValue;
    }

    public static LocationSource fromDb(String value) {
        for (LocationSource source : values()) {
            if (source.dbValue.equals(value)) return source;
        }
        throw new IllegalArgumentException("알 수 없는 locationSource: " + value);
    }
}
```

- [ ] **Step 6: BusinessStatus 작성**

```java
package com.nextstep.domain.tenancy;

public enum BusinessStatus {
    ACTIVE("영업"),
    CLOSED("폐업"),
    SUSPENDED("휴업");

    private final String display;

    BusinessStatus(String display) {
        this.display = display;
    }

    public String display() {
        return display;
    }

    public static BusinessStatus fromDb(String value) {
        for (BusinessStatus status : values()) {
            if (status.display.equals(value)) return status;
        }
        throw new IllegalArgumentException("알 수 없는 영업상태: " + value);
    }
}
```

- [ ] **Step 7: TenancyPeriod 작성**

```java
package com.nextstep.domain.tenancy;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

public record TenancyPeriod(LocalDate licensedAt, LocalDate closedAt) {
    public TenancyPeriod {
        if (licensedAt == null) {
            throw new IllegalArgumentException("licensedAt은 필수입니다.");
        }
        if (closedAt != null && closedAt.isBefore(licensedAt)) {
            throw new IllegalArgumentException("closedAt은 licensedAt보다 빠를 수 없습니다.");
        }
    }

    public int survivalMonths() {
        LocalDate end = closedAt != null ? closedAt : LocalDate.now();
        return (int) ChronoUnit.MONTHS.between(licensedAt, end);
    }
}
```

- [ ] **Step 8: 테스트 재실행 → 통과 확인**

Run: `cd server && mvn -q test -Dtest=TenancyPeriodTest`
Expected: `Tests run: 3, Failures: 0, Errors: 0`

- [ ] **Step 9: 커밋**

```bash
git add src/main/java/com/nextstep/domain src/test/java/com/nextstep/domain
git commit -m "feat: add domain value objects (Pnu, Coordinate, BusinessStatus, TenancyPeriod)"
```

---

