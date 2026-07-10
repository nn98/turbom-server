### Task 4: 도메인 애그리게이트 — Tenancy / UnitStatistics / Unit / Site

**Files:**
- Create: `server/src/main/java/com/nextstep/domain/tenancy/Tenancy.java`
- Create: `server/src/main/java/com/nextstep/domain/statistics/UnitStatistics.java`
- Create: `server/src/main/java/com/nextstep/domain/unit/Unit.java`
- Create: `server/src/main/java/com/nextstep/domain/site/Site.java`
- Test: `server/src/test/java/com/nextstep/domain/statistics/UnitStatisticsTest.java`

**Interfaces:**
- Consumes: `TenancyPeriod`, `BusinessStatus`, `LocationSource` (Task 3).
- Produces: `Tenancy(Long id, String businessName, String category, String subCategory, String industryDetail, TenancyPeriod period, BusinessStatus status, String enrichmentSource)` — `closedAtEstimated()`는 항상 `false` 반환(고정값, DB에 컬럼 없음). `UnitStatistics.from(List<Tenancy>)`. `Unit(String unitId, String label, LocationSource locationSource, List<Tenancy> tenancies)` — `statistics()`, `currentTenancy()`. `Site(Pnu pnu, String jibunAddress, String roadAddress, Coordinate coordinate, List<Unit> units)`.

- [ ] **Step 1: 실패하는 테스트 작성**

```java
package com.nextstep.domain.statistics;

import com.nextstep.domain.tenancy.BusinessStatus;
import com.nextstep.domain.tenancy.Tenancy;
import com.nextstep.domain.tenancy.TenancyPeriod;
import org.junit.jupiter.api.Test;
import java.time.LocalDate;
import java.util.List;
import static org.assertj.core.api.Assertions.assertThat;

class UnitStatisticsTest {

    private Tenancy closed(LocalDate start, LocalDate end) {
        return new Tenancy(1L, "가게", "음식", "일반음식점", null,
            new TenancyPeriod(start, end), BusinessStatus.CLOSED, "license_only");
    }

    private Tenancy active(LocalDate start) {
        return new Tenancy(2L, "가게2", "음식", "일반음식점", null,
            new TenancyPeriod(start, null), BusinessStatus.ACTIVE, "license_only");
    }

    @Test
    void 폐업_이력만으로_평균_최장_최단_생존월을_계산한다() {
        List<Tenancy> tenancies = List.of(
            closed(LocalDate.of(2013, 5, 2), LocalDate.of(2017, 1, 10)),  // 44개월
            closed(LocalDate.of(2018, 1, 1), LocalDate.of(2018, 12, 1)),  // 11개월
            active(LocalDate.of(2023, 1, 15))
        );

        UnitStatistics stats = UnitStatistics.from(tenancies);

        assertThat(stats.totalTenancyCount()).isEqualTo(3);
        assertThat(stats.closedCount()).isEqualTo(2);
        assertThat(stats.longestSurvivalMonths()).isEqualTo(44);
        assertThat(stats.shortestSurvivalMonths()).isEqualTo(11);
        assertThat(stats.averageSurvivalMonths()).isEqualTo(28);
    }

    @Test
    void 폐업_이력이_없으면_평균_최장_최단이_null이다() {
        UnitStatistics stats = UnitStatistics.from(List.of(active(LocalDate.of(2023, 1, 1))));

        assertThat(stats.closedCount()).isZero();
        assertThat(stats.averageSurvivalMonths()).isNull();
        assertThat(stats.longestSurvivalMonths()).isNull();
        assertThat(stats.shortestSurvivalMonths()).isNull();
    }
}
```

- [ ] **Step 2: 테스트 실행 → 실패 확인**

Run: `cd server && mvn -q test -Dtest=UnitStatisticsTest`
Expected: FAIL — 컴파일 에러(`Tenancy`, `UnitStatistics` 없음).

- [ ] **Step 3: Tenancy 작성**

```java
package com.nextstep.domain.tenancy;

public record Tenancy(
    Long id,
    String businessName,
    String category,
    String subCategory,
    String industryDetail,
    TenancyPeriod period,
    BusinessStatus status,
    String enrichmentSource
) {
    public int survivalMonths() {
        return period.survivalMonths();
    }

    public boolean closedAtEstimated() {
        return false;
    }
}
```

- [ ] **Step 4: UnitStatistics 작성**

```java
package com.nextstep.domain.statistics;

import com.nextstep.domain.tenancy.BusinessStatus;
import com.nextstep.domain.tenancy.Tenancy;
import java.util.List;

public record UnitStatistics(
    int totalTenancyCount,
    int closedCount,
    Integer averageSurvivalMonths,
    Integer longestSurvivalMonths,
    Integer shortestSurvivalMonths
) {
    public static UnitStatistics from(List<Tenancy> tenancies) {
        List<Integer> closedMonths = tenancies.stream()
            .filter(t -> t.status() == BusinessStatus.CLOSED)
            .map(Tenancy::survivalMonths)
            .toList();

        Integer average = closedMonths.isEmpty() ? null
            : (int) Math.round(closedMonths.stream().mapToInt(Integer::intValue).average().orElseThrow());
        Integer longest = closedMonths.isEmpty() ? null : java.util.Collections.max(closedMonths);
        Integer shortest = closedMonths.isEmpty() ? null : java.util.Collections.min(closedMonths);

        return new UnitStatistics(tenancies.size(), closedMonths.size(), average, longest, shortest);
    }
}
```

- [ ] **Step 5: Unit 작성**

```java
package com.nextstep.domain.unit;

import com.nextstep.domain.statistics.UnitStatistics;
import com.nextstep.domain.tenancy.BusinessStatus;
import com.nextstep.domain.tenancy.Tenancy;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

public record Unit(String unitId, String label, LocationSource locationSource, List<Tenancy> tenancies) {
    public UnitStatistics statistics() {
        return UnitStatistics.from(tenancies);
    }

    public Optional<Tenancy> currentTenancy() {
        return tenancies.stream()
            .filter(t -> t.status() == BusinessStatus.ACTIVE)
            .max(Comparator.comparing(t -> t.period().licensedAt()));
    }
}
```

- [ ] **Step 6: Site 작성**

```java
package com.nextstep.domain.site;

import com.nextstep.domain.unit.Unit;
import java.util.List;

public record Site(Pnu pnu, String jibunAddress, String roadAddress, Coordinate coordinate, List<Unit> units) {
}
```

- [ ] **Step 7: 테스트 재실행 → 통과 확인**

Run: `cd server && mvn -q test -Dtest=UnitStatisticsTest`
Expected: `Tests run: 2, Failures: 0, Errors: 0`

- [ ] **Step 8: 커밋**

```bash
git add src/main/java/com/nextstep/domain src/test/java/com/nextstep/domain
git commit -m "feat: add domain aggregates (Tenancy, UnitStatistics, Unit, Site)"
```

---

