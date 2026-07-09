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
