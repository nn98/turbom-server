### Task 10: MarketInfoService — try-catch 격리 + 캐시, SiteQueryService에 연결

**Files:**
- Create: `server/src/main/java/com/nextstep/application/MarketInfoService.java`
- Modify: `server/src/main/java/com/nextstep/application/SiteQueryService.java` (`getUnitDetail`에서 `MarketInfo.unavailable()` 고정값 대신 실제 서비스 호출)
- Test: `server/src/test/java/com/nextstep/application/MarketInfoServiceTest.java`

**Interfaces:**
- Consumes: `SanggaApiClient` (Task 9).
- Produces: `MarketInfoService.fetch(String pnu, Double lon, Double lat, String subCategory) -> MarketInfo`. 실패(예외 전부)·좌표 없음 시 `MarketInfo.unavailable()`.

- [ ] **Step 1: 실패하는 테스트 작성**

```java
package com.nextstep.application;

import com.nextstep.domain.market.MarketInfo;
import com.nextstep.infra.sangga.SanggaApiClient;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MarketInfoServiceTest {

    @Mock SanggaApiClient sanggaApiClient;

    @Test
    void 상가API가_성공하면_실값을_담는다() {
        when(sanggaApiClient.countSameCategoryInRadius(127.14, 37.44, 300, "동물미용업")).thenReturn(5);
        MarketInfoService service = new MarketInfoService(sanggaApiClient);

        MarketInfo result = service.fetch("pnu", 127.14, 37.44, "동물미용업");

        assertThat(result.sameCategoryNearbyCount()).isEqualTo(5);
        assertThat(result.isPlaceholder()).isTrue();
    }

    @Test
    void 상가API가_예외를_던지면_null로_대체한다() {
        when(sanggaApiClient.countSameCategoryInRadius(anyDouble(), anyDouble(), anyInt(), anyString()))
            .thenThrow(new RuntimeException("network blocked"));
        MarketInfoService service = new MarketInfoService(sanggaApiClient);

        MarketInfo result = service.fetch("pnu", 127.14, 37.44, "동물미용업");

        assertThat(result.sameCategoryNearbyCount()).isNull();
    }

    @Test
    void 좌표가_없으면_API를_호출하지_않고_null() {
        MarketInfoService service = new MarketInfoService(sanggaApiClient);

        MarketInfo result = service.fetch("pnu", null, null, "동물미용업");

        assertThat(result.sameCategoryNearbyCount()).isNull();
    }
}
```

- [ ] **Step 2: 테스트 실행 → 실패 확인**

Run: `cd server && mvn -q test -Dtest=MarketInfoServiceTest`
Expected: FAIL — `MarketInfoService` 없어 컴파일 에러. (Mockito는 `spring-boot-starter-test`에 포함되어 있어 추가 의존성 불필요.)

- [ ] **Step 3: MarketInfoService 작성**

```java
package com.nextstep.application;

import com.nextstep.domain.market.MarketInfo;
import com.nextstep.infra.sangga.SanggaApiClient;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

@Service
public class MarketInfoService {

    private static final int RADIUS_METERS = 300;

    private final SanggaApiClient sanggaApiClient;

    public MarketInfoService(SanggaApiClient sanggaApiClient) {
        this.sanggaApiClient = sanggaApiClient;
    }

    @Cacheable(value = "sameCategoryNearbyCount", key = "#pnu + ':' + #subCategory")
    public MarketInfo fetch(String pnu, Double lon, Double lat, String subCategory) {
        if (lon == null || lat == null) {
            return MarketInfo.unavailable();
        }
        try {
            int count = sanggaApiClient.countSameCategoryInRadius(lon, lat, RADIUS_METERS, subCategory);
            return MarketInfo.of(count);
        } catch (Exception e) {
            return MarketInfo.unavailable();
        }
    }
}
```

- [ ] **Step 4: 테스트 재실행 → 통과 확인**

Run: `cd server && mvn -q test -Dtest=MarketInfoServiceTest`
Expected: `Tests run: 3, Failures: 0, Errors: 0`

- [ ] **Step 5: SiteQueryService.getUnitDetail을 실제 MarketInfoService 호출로 교체**

`server/src/main/java/com/nextstep/application/SiteQueryService.java`에서 생성자와 `getUnitDetail`을 아래로 교체한다.

```java
    private final TenancyQueryService tenancyQueryService;
    private final MarketInfoService marketInfoService;

    public SiteQueryService(TenancyQueryService tenancyQueryService, MarketInfoService marketInfoService) {
        this.tenancyQueryService = tenancyQueryService;
        this.marketInfoService = marketInfoService;
    }
```

```java
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

        List<TenancyDto> timeline = unit.tenancies().stream()
            .map(t -> toTenancyDto(t, marketInfo))
            .toList();

        UnitDto unitDto = new UnitDto(unit.unitId(), unit.label(), site.jibunAddress(), site.roadAddress());
        UnitStatisticsDto statisticsDto = toStatisticsDto(unit);

        return new UnitDetailResponse(unitDto, statisticsDto, timeline, disclaimer());
    }
```

- [ ] **Step 6: 전체 테스트 재실행 (SiteControllerTest 포함, 회귀 확인)**

Run: `cd server && mvn -q test`
Expected: `BUILD SUCCESS`, 전체 테스트 통과(서비스키가 비어 있으므로 `SanggaApiClient` 실호출은 예외 → `MarketInfoService`가 `unavailable()`로 흡수 → `SiteControllerTest`의 `sameCategoryNearbyCount` 관련 단언은 값 존재 여부만 확인하므로 영향 없음).

- [ ] **Step 7: 커밋**

```bash
git add src/main/java/com/nextstep/application
git commit -m "feat: wire MarketInfoService into unit detail endpoint (sangga API real-time call)"
```

이 시점에서 `backend-spec.md` §10 전 6단계 완료.

---

