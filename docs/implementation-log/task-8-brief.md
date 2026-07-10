### Task 8: web DTO + SiteController(search, site 상세) + GlobalExceptionHandler — marketInfo는 항상 unavailable

**Files:**
- Create: `server/src/main/java/com/nextstep/web/dto/ApiDtos.java`
- Create: `server/src/main/java/com/nextstep/application/SiteQueryService.java`
- Create: `server/src/main/java/com/nextstep/web/SiteController.java`
- Create: `server/src/main/java/com/nextstep/web/GlobalExceptionHandler.java`
- Test: `server/src/test/java/com/nextstep/web/SiteControllerTest.java`

**Interfaces:**
- Consumes: `TenancyQueryService` (Task 6), `MarketInfo` (Task 7).
- Produces: `SiteQueryService.search/getSiteDetail/getUnitDetail`가 반환하는 DTO들은 이 태스크에서 `MarketInfoService` 없이 `MarketInfo.unavailable()`을 고정으로 채운다 — Task 9에서 실제 상가API 연동으로 교체되며 컨트롤러·DTO는 건드리지 않는다.
- `spec/api-spec.md`의 3개 엔드포인트 JSON 계약을 정확히 만족한다.

- [ ] **Step 1: 실패하는 테스트 작성**

```java
package com.nextstep.web;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class SiteControllerTest {

    @Autowired MockMvc mockMvc;

    @Test
    void query_없이_검색하면_400_INVALID_QUERY() throws Exception {
        mockMvc.perform(get("/api/sites/search"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error").value("INVALID_QUERY"));
    }

    @Test
    void 신흥동으로_검색하면_후보가_나온다() throws Exception {
        mockMvc.perform(get("/api/sites/search").param("query", "신흥동"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.candidates", org.hamcrest.Matchers.hasSize(2)))
            .andExpect(jsonPath("$.candidates[0].pnu").exists());
    }

    @Test
    void 존재하지_않는_pnu는_404_SITE_NOT_FOUND() throws Exception {
        mockMvc.perform(get("/api/sites/9999999999999999999"))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.error").value("SITE_NOT_FOUND"));
    }

    @Test
    void 자리상세는_폐업많은순으로_물건이_정렬된다() throws Exception {
        mockMvc.perform(get("/api/sites/4113110300100280001"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.units[0].closedCount").value(2))
            .andExpect(jsonPath("$.disclaimer.note").exists());
    }

    @Test
    void 존재하지_않는_unitId는_404_UNIT_NOT_FOUND() throws Exception {
        mockMvc.perform(get("/api/units/no-such-unit"))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.error").value("UNIT_NOT_FOUND"));
    }

    @Test
    void 물건상세는_타임라인과_marketInfo를_포함한다() throws Exception {
        mockMvc.perform(get("/api/units/4113110100100340000-U1"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.timeline", org.hamcrest.Matchers.hasSize(2)))
            .andExpect(jsonPath("$.timeline[0].marketInfo.isPlaceholder").value(true))
            .andExpect(jsonPath("$.statistics.totalTenancyCount").value(2));
    }
}
```

- [ ] **Step 2: 테스트 실행 → 실패 확인**

Run: `cd server && mvn -q test -Dtest=SiteControllerTest`
Expected: FAIL — 컨트롤러가 없어 컴파일 에러(또는 404 Not Found for all, 라우트 없음).

- [ ] **Step 3: DTO 작성 (record 모음, 한 파일)**

```java
package com.nextstep.web.dto;

import java.time.LocalDate;
import java.util.List;

public class ApiDtos {

    public record SearchResponse(List<SiteCandidateDto> candidates) {
    }

    public record SiteCandidateDto(String pnu, String jibunAddress, String roadAddress,
                                    Double latitude, Double longitude, int unitCount, int closedCount) {
    }

    public record SiteDetailResponse(SiteDto site, List<UnitSummaryDto> units, DisclaimerDto disclaimer) {
    }

    public record SiteDto(String pnu, String jibunAddress, String roadAddress, Double latitude, Double longitude) {
    }

    public record UnitSummaryDto(String unitId, String label, String currentBusinessName, String currentStatus,
                                  int totalTenancyCount, int closedCount, Integer averageSurvivalMonths,
                                  String industryDetail, String locationSource) {
    }

    public record UnitDetailResponse(UnitDto unit, UnitStatisticsDto statistics, List<TenancyDto> timeline,
                                      DisclaimerDto disclaimer) {
    }

    public record UnitDto(String unitId, String label, String jibunAddress, String roadAddress) {
    }

    public record UnitStatisticsDto(int totalTenancyCount, int closedCount, Integer averageSurvivalMonths,
                                     Integer longestSurvivalMonths, Integer shortestSurvivalMonths) {
    }

    public record TenancyDto(String tenancyId, String businessName, String category, String subCategory,
                              String industryDetail, LocalDate licensedAt, LocalDate closedAt, String status,
                              Integer survivalMonths, boolean closedAtEstimated, String enrichmentSource,
                              MarketInfoDto marketInfo) {
    }

    public record MarketInfoDto(boolean isPlaceholder, Double leaseAreaSqm, Long depositKrw, Long monthlyRentKrw,
                                 Long keyMoneyKrw, Integer dailyFloatingPopulation, Integer sameCategoryNearbyCount,
                                 Double vacancyRatePercent, LocalDate asOf) {
    }

    public record DisclaimerDto(LocalDate dataAsOf, String note) {
    }

    public record ErrorResponse(String error, String message) {
    }
}
```

- [ ] **Step 4: SiteQueryService 작성 (marketInfo는 항상 unavailable 고정)**

```java
package com.nextstep.application;

import com.nextstep.domain.exception.InvalidQueryException;
import com.nextstep.domain.exception.SiteNotFoundException;
import com.nextstep.domain.exception.UnitNotFoundException;
import com.nextstep.domain.market.MarketInfo;
import com.nextstep.domain.site.Site;
import com.nextstep.domain.tenancy.BusinessStatus;
import com.nextstep.domain.tenancy.Tenancy;
import com.nextstep.domain.unit.Unit;
import com.nextstep.web.dto.ApiDtos.*;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;

@Service
public class SiteQueryService {

    private static final String DISCLAIMER_NOTE = "인허가 신고 기준 데이터로 실제 영업 현황과 차이가 있을 수 있습니다.";

    private final TenancyQueryService tenancyQueryService;

    public SiteQueryService(TenancyQueryService tenancyQueryService) {
        this.tenancyQueryService = tenancyQueryService;
    }

    public SearchResponse search(String query) {
        if (query == null || query.isBlank()) {
            throw new InvalidQueryException();
        }
        List<SiteCandidateDto> candidates = tenancyQueryService.searchSites(query).stream()
            .map(this::toCandidateDto)
            .toList();
        return new SearchResponse(candidates);
    }

    public SiteDetailResponse getSiteDetail(String pnu) {
        Site site = tenancyQueryService.findSiteWithUnits(pnu)
            .orElseThrow(() -> new SiteNotFoundException(pnu));

        List<UnitSummaryDto> units = site.units().stream()
            .map(this::toUnitSummaryDto)
            .sorted(Comparator.comparingInt(UnitSummaryDto::closedCount).reversed())
            .toList();

        return new SiteDetailResponse(toSiteDto(site), units, disclaimer());
    }

    public UnitDetailResponse getUnitDetail(String unitId) {
        var unitWithSite = tenancyQueryService.findUnitWithTenancies(unitId)
            .orElseThrow(() -> new UnitNotFoundException(unitId));
        Unit unit = unitWithSite.unit();
        Site site = unitWithSite.site();

        MarketInfo marketInfo = MarketInfo.unavailable();

        List<TenancyDto> timeline = unit.tenancies().stream()
            .map(t -> toTenancyDto(t, marketInfo))
            .toList();

        UnitDto unitDto = new UnitDto(unit.unitId(), unit.label(), site.jibunAddress(), site.roadAddress());
        UnitStatisticsDto statisticsDto = toStatisticsDto(unit);

        return new UnitDetailResponse(unitDto, statisticsDto, timeline, disclaimer());
    }

    private SiteCandidateDto toCandidateDto(Site site) {
        int closedCount = site.units().stream()
            .flatMap(u -> u.tenancies().stream())
            .filter(t -> t.status() == BusinessStatus.CLOSED)
            .toList().size();
        Double lat = site.coordinate() == null ? null : site.coordinate().latitude();
        Double lon = site.coordinate() == null ? null : site.coordinate().longitude();
        return new SiteCandidateDto(site.pnu().value(), site.jibunAddress(), site.roadAddress(),
            lat, lon, site.units().size(), closedCount);
    }

    private SiteDto toSiteDto(Site site) {
        Double lat = site.coordinate() == null ? null : site.coordinate().latitude();
        Double lon = site.coordinate() == null ? null : site.coordinate().longitude();
        return new SiteDto(site.pnu().value(), site.jibunAddress(), site.roadAddress(), lat, lon);
    }

    private UnitSummaryDto toUnitSummaryDto(Unit unit) {
        var stats = unit.statistics();
        String currentBusinessName = unit.currentTenancy().map(Tenancy::businessName).orElse(null);
        String currentStatus = unit.currentTenancy().isPresent() ? "영업" : "공실";
        String industryDetail = unit.currentTenancy().map(Tenancy::industryDetail).orElse(null);
        return new UnitSummaryDto(unit.unitId(), unit.label(), currentBusinessName, currentStatus,
            stats.totalTenancyCount(), stats.closedCount(), stats.averageSurvivalMonths(),
            industryDetail, unit.locationSource().dbValue());
    }

    private UnitStatisticsDto toStatisticsDto(Unit unit) {
        var stats = unit.statistics();
        return new UnitStatisticsDto(stats.totalTenancyCount(), stats.closedCount(), stats.averageSurvivalMonths(),
            stats.longestSurvivalMonths(), stats.shortestSurvivalMonths());
    }

    private TenancyDto toTenancyDto(Tenancy tenancy, MarketInfo marketInfo) {
        MarketInfoDto marketInfoDto = new MarketInfoDto(marketInfo.isPlaceholder(), MarketInfo.LEASE_AREA_SQM,
            MarketInfo.DEPOSIT_KRW, MarketInfo.MONTHLY_RENT_KRW, MarketInfo.KEY_MONEY_KRW,
            MarketInfo.DAILY_FLOATING_POPULATION, marketInfo.sameCategoryNearbyCount(),
            MarketInfo.VACANCY_RATE_PERCENT, marketInfo.asOf());

        return new TenancyDto("t-" + tenancy.id(), tenancy.businessName(), tenancy.category(), tenancy.subCategory(),
            tenancy.industryDetail(), tenancy.period().licensedAt(), tenancy.period().closedAt(),
            tenancy.status().display(), tenancy.survivalMonths(), tenancy.closedAtEstimated(),
            tenancy.enrichmentSource(), marketInfoDto);
    }

    private DisclaimerDto disclaimer() {
        return new DisclaimerDto(LocalDate.now(), DISCLAIMER_NOTE);
    }
}
```

- [ ] **Step 5: SiteController 작성**

```java
package com.nextstep.web;

import com.nextstep.application.SiteQueryService;
import com.nextstep.web.dto.ApiDtos.*;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
public class SiteController {

    private final SiteQueryService siteQueryService;

    public SiteController(SiteQueryService siteQueryService) {
        this.siteQueryService = siteQueryService;
    }

    @GetMapping("/sites/search")
    public SearchResponse search(@RequestParam(required = false) String query) {
        return siteQueryService.search(query);
    }

    @GetMapping("/sites/{pnu}")
    public SiteDetailResponse siteDetail(@PathVariable String pnu) {
        return siteQueryService.getSiteDetail(pnu);
    }

    @GetMapping("/units/{unitId}")
    public UnitDetailResponse unitDetail(@PathVariable String unitId) {
        return siteQueryService.getUnitDetail(unitId);
    }
}
```

- [ ] **Step 6: GlobalExceptionHandler 작성**

```java
package com.nextstep.web;

import com.nextstep.domain.exception.InvalidQueryException;
import com.nextstep.domain.exception.SiteNotFoundException;
import com.nextstep.domain.exception.UnitNotFoundException;
import com.nextstep.web.dto.ApiDtos.ErrorResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(InvalidQueryException.class)
    public ResponseEntity<ErrorResponse> handleInvalidQuery(InvalidQueryException e) {
        return ResponseEntity.badRequest().body(new ErrorResponse("INVALID_QUERY", e.getMessage()));
    }

    @ExceptionHandler(SiteNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleSiteNotFound(SiteNotFoundException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new ErrorResponse("SITE_NOT_FOUND", e.getMessage()));
    }

    @ExceptionHandler(UnitNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleUnitNotFound(UnitNotFoundException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new ErrorResponse("UNIT_NOT_FOUND", e.getMessage()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleUnexpected(Exception e) {
        return ResponseEntity.internalServerError().body(new ErrorResponse("INTERNAL_ERROR", "서버 오류가 발생했습니다."));
    }
}
```

- [ ] **Step 7: 테스트 재실행 → 통과 확인**

Run: `cd server && mvn -q test -Dtest=SiteControllerTest`
Expected: `Tests run: 6, Failures: 0, Errors: 0`

- [ ] **Step 8: 커밋**

```bash
git add src/main/java/com/nextstep/web src/main/java/com/nextstep/application/SiteQueryService.java src/test/java/com/nextstep/web
git commit -m "feat: add 3 API endpoints with DB-only pipeline (marketInfo stubbed unavailable)"
```

이 시점에서 `backend-spec.md` §10 1~4단계 완료 — 상가API 없이 전체 서비스가 동작한다.

---

