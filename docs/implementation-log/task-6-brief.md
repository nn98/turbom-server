### Task 6: TenancyQueryService — 개폐업 DB 조회 파이프라인

**Files:**
- Create: `server/src/main/java/com/nextstep/application/TenancyQueryService.java`
- Test: `server/src/test/java/com/nextstep/application/TenancyQueryServiceTest.java`

**Interfaces:**
- Consumes: `SiteJpaRepository`, `UnitJpaRepository`, `TenancyJpaRepository` (Task 5); `Site`, `Unit`, `Tenancy`, `Pnu`, `Coordinate`, `LocationSource`, `BusinessStatus`, `TenancyPeriod` (Task 3·4).
- Produces: `TenancyQueryService.searchSites(String query) -> List<Site>`, `findSiteWithUnits(String pnu) -> Optional<Site>`, `findUnitWithTenancies(String unitId) -> Optional<Unit>` (반환하는 `Unit`에 소속 `Site`를 알 수 있도록 별도 `UnitWithSite` 레코드도 함께 제공 — 물건 상세 화면이 `jibunAddress`/`roadAddress`를 필요로 하기 때문).

- [ ] **Step 1: 실패하는 테스트 작성**

```java
package com.nextstep.application;

import com.nextstep.domain.site.Site;
import com.nextstep.domain.unit.Unit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import java.util.List;
import java.util.Optional;
import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class TenancyQueryServiceTest {

    @Autowired TenancyQueryService tenancyQueryService;

    @Test
    void 지번주소로_검색하면_일치하는_자리가_나온다() {
        List<Site> results = tenancyQueryService.searchSites("신흥동");
        assertThat(results).extracting(s -> s.pnu().value())
            .contains("4113110100100340000", "4113110100100300002");
    }

    @Test
    void pnu로_자리상세를_조회하면_물건과_통계가_채워진다() {
        Optional<Site> site = tenancyQueryService.findSiteWithUnits("4113110300100280001");
        assertThat(site).isPresent();
        Unit unit = site.get().units().get(0);
        assertThat(unit.statistics().totalTenancyCount()).isEqualTo(2);
        assertThat(unit.statistics().closedCount()).isEqualTo(2);
    }

    @Test
    void 존재하지_않는_pnu는_빈값() {
        assertThat(tenancyQueryService.findSiteWithUnits("9999999999999999999")).isEmpty();
    }

    @Test
    void unitId로_물건상세를_조회하면_이력_두_건이_시간순으로_나온다() {
        var unitWithSite = tenancyQueryService.findUnitWithTenancies("4113110100100340000-U1");
        assertThat(unitWithSite).isPresent();
        Unit unit = unitWithSite.get().unit();
        assertThat(unit.tenancies()).hasSize(2);
        assertThat(unit.tenancies().get(0).period().licensedAt())
            .isBefore(unit.tenancies().get(1).period().licensedAt());
        assertThat(unitWithSite.get().site().jibunAddress()).contains("신흥동");
    }
}
```

- [ ] **Step 2: 테스트 실행 → 실패 확인**

Run: `cd server && mvn -q test -Dtest=TenancyQueryServiceTest`
Expected: FAIL — `TenancyQueryService` 클래스가 없어 컴파일 에러.

- [ ] **Step 3: TenancyQueryService 작성**

```java
package com.nextstep.application;

import com.nextstep.domain.site.Coordinate;
import com.nextstep.domain.site.Pnu;
import com.nextstep.domain.site.Site;
import com.nextstep.domain.tenancy.BusinessStatus;
import com.nextstep.domain.tenancy.Tenancy;
import com.nextstep.domain.tenancy.TenancyPeriod;
import com.nextstep.domain.unit.LocationSource;
import com.nextstep.domain.unit.Unit;
import com.nextstep.infra.persistence.SiteEntity;
import com.nextstep.infra.persistence.SiteJpaRepository;
import com.nextstep.infra.persistence.TenancyEntity;
import com.nextstep.infra.persistence.TenancyJpaRepository;
import com.nextstep.infra.persistence.UnitEntity;
import com.nextstep.infra.persistence.UnitJpaRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;

@Service
@Transactional(readOnly = true)
public class TenancyQueryService {

    private final SiteJpaRepository siteRepository;
    private final UnitJpaRepository unitRepository;
    private final TenancyJpaRepository tenancyRepository;

    public TenancyQueryService(SiteJpaRepository siteRepository, UnitJpaRepository unitRepository,
                                TenancyJpaRepository tenancyRepository) {
        this.siteRepository = siteRepository;
        this.unitRepository = unitRepository;
        this.tenancyRepository = tenancyRepository;
    }

    public List<Site> searchSites(String query) {
        return siteRepository.searchByAddress(query).stream()
            .map(this::assembleSite)
            .toList();
    }

    public Optional<Site> findSiteWithUnits(String pnu) {
        return siteRepository.findById(pnu).map(this::assembleSite);
    }

    public record UnitWithSite(Unit unit, Site site) {
    }

    public Optional<UnitWithSite> findUnitWithTenancies(String unitId) {
        return unitRepository.findById(unitId).flatMap(unitEntity ->
            siteRepository.findById(unitEntity.getSitePnu()).map(siteEntity -> {
                Unit unit = assembleUnit(unitEntity);
                Site site = new Site(new Pnu(siteEntity.getPnu()), siteEntity.getJibunAddress(),
                    siteEntity.getRoadAddress(), toCoordinate(siteEntity), List.of());
                return new UnitWithSite(unit, site);
            })
        );
    }

    private Site assembleSite(SiteEntity siteEntity) {
        List<UnitEntity> unitEntities = unitRepository.findBySitePnu(siteEntity.getPnu());
        List<Unit> units = unitEntities.stream().map(this::assembleUnit).toList();
        return new Site(new Pnu(siteEntity.getPnu()), siteEntity.getJibunAddress(),
            siteEntity.getRoadAddress(), toCoordinate(siteEntity), units);
    }

    private Unit assembleUnit(UnitEntity unitEntity) {
        List<Tenancy> tenancies = tenancyRepository.findByUnitId(unitEntity.getUnitId()).stream()
            .map(this::toTenancy)
            .sorted(Comparator.comparing(t -> t.period().licensedAt()))
            .toList();
        return new Unit(unitEntity.getUnitId(), unitEntity.getLabel(),
            LocationSource.fromDb(unitEntity.getLocationSource()), tenancies);
    }

    private Tenancy toTenancy(TenancyEntity entity) {
        return new Tenancy(
            entity.getId(),
            entity.getBusinessName(),
            entity.getCategory(),
            entity.getSubCategory(),
            null,
            new TenancyPeriod(entity.getLicensedAt(), entity.getClosedAt()),
            BusinessStatus.fromDb(entity.getStatus()),
            "license_only"
        );
    }

    private Coordinate toCoordinate(SiteEntity siteEntity) {
        if (siteEntity.getLatitude() == null || siteEntity.getLongitude() == null) return null;
        return new Coordinate(siteEntity.getLatitude().doubleValue(), siteEntity.getLongitude().doubleValue());
    }
}
```

- [ ] **Step 4: 테스트 재실행 → 통과 확인**

Run: `cd server && mvn -q test -Dtest=TenancyQueryServiceTest`
Expected: `Tests run: 4, Failures: 0, Errors: 0`

- [ ] **Step 5: 커밋**

```bash
git add src/main/java/com/nextstep/application src/test/java/com/nextstep/application
git commit -m "feat: add TenancyQueryService (DB-only query pipeline)"
```

---

