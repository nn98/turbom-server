### Task 7: MarketInfo 도메인 + 예외 클래스

**Files:**
- Create: `server/src/main/java/com/nextstep/domain/market/MarketInfo.java`
- Create: `server/src/main/java/com/nextstep/domain/exception/InvalidQueryException.java`
- Create: `server/src/main/java/com/nextstep/domain/exception/SiteNotFoundException.java`
- Create: `server/src/main/java/com/nextstep/domain/exception/UnitNotFoundException.java`

**Interfaces:**
- Produces: `MarketInfo(boolean isPlaceholder, Integer sameCategoryNearbyCount, LocalDate asOf)` + `public static final` 목업 상수 6개 + `unavailable()`/`of(Integer count)` 팩토리.

- [ ] **Step 1: MarketInfo 작성**

```java
package com.nextstep.domain.market;

import java.time.LocalDate;

public record MarketInfo(boolean isPlaceholder, Integer sameCategoryNearbyCount, LocalDate asOf) {

    public static final double LEASE_AREA_SQM = 42.6;
    public static final long DEPOSIT_KRW = 50_000_000L;
    public static final long MONTHLY_RENT_KRW = 2_800_000L;
    public static final long KEY_MONEY_KRW = 0L;
    public static final int DAILY_FLOATING_POPULATION = 21_400;
    public static final double VACANCY_RATE_PERCENT = 6.2;

    public static MarketInfo unavailable() {
        return new MarketInfo(true, null, LocalDate.now());
    }

    public static MarketInfo of(int sameCategoryNearbyCount) {
        return new MarketInfo(true, sameCategoryNearbyCount, LocalDate.now());
    }
}
```

- [ ] **Step 2: 예외 클래스 3종 작성**

```java
package com.nextstep.domain.exception;

public class InvalidQueryException extends RuntimeException {
    public InvalidQueryException() {
        super("query 파라미터가 필요합니다.");
    }
}
```

```java
package com.nextstep.domain.exception;

public class SiteNotFoundException extends RuntimeException {
    public SiteNotFoundException(String pnu) {
        super("해당 자리를 찾을 수 없습니다: " + pnu);
    }
}
```

```java
package com.nextstep.domain.exception;

public class UnitNotFoundException extends RuntimeException {
    public UnitNotFoundException(String unitId) {
        super("해당 물건을 찾을 수 없습니다: " + unitId);
    }
}
```

- [ ] **Step 3: 빌드 확인**

Run: `cd server && mvn -q compile`
Expected: `BUILD SUCCESS`.

- [ ] **Step 4: 커밋**

```bash
git add src/main/java/com/nextstep/domain/market src/main/java/com/nextstep/domain/exception
git commit -m "feat: add MarketInfo value object and domain exceptions"
```

---

