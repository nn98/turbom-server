package com.nextstep.infra.persistence;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.beans.factory.annotation.Autowired;
import java.util.List;
import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
class PersistenceSmokeTest {

    @Autowired SiteJpaRepository siteRepository;
    @Autowired UnitJpaRepository unitRepository;
    @Autowired TenancyJpaRepository tenancyRepository;

    @Test
    void 시드_데이터가_전부_로드된다() {
        assertThat(siteRepository.count()).isEqualTo(6);
        assertThat(unitRepository.count()).isEqualTo(6);
        assertThat(tenancyRepository.count()).isEqualTo(8);
    }

    @Test
    void 자리로_물건을_조회한다() {
        List<UnitEntity> units = unitRepository.findBySitePnu("4113110100100340000");
        assertThat(units).hasSize(1);
        assertThat(units.get(0).getUnitId()).isEqualTo("4113110100100340000-U1");
    }

    @Test
    void 물건으로_이력을_조회하면_두_건이_나온다() {
        List<TenancyEntity> tenancies = tenancyRepository.findByUnitId("4113110100100340000-U1");
        assertThat(tenancies).hasSize(2);
    }
}
