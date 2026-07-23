package com.nextstep.application;

import com.nextstep.domain.businesstype.BusinessTypeRegistry;
import com.nextstep.domain.tenancy.Tenancy;
import com.nextstep.domain.tenancy.TenancyPeriod;
import com.nextstep.domain.businesstype.ReliabilitySignal;
import org.junit.jupiter.api.Test;
import java.time.LocalDate;
import java.util.List;
import static org.assertj.core.api.Assertions.assertThat;

class RelatedLicenseLinkerTest {

    private final RelatedLicenseLinker linker = new RelatedLicenseLinker(new BusinessTypeRegistry());

    private Tenancy tenancy(long id, String businessName, String category, String subCategory) {
        return new Tenancy(id, businessName, category, subCategory, null,
            new TenancyPeriod(LocalDate.of(2020, 1, 1), null), "영업/정상", "license_only",
            ReliabilitySignal.confirmed());
    }

    @Test
    void 집단급식소와_위탁급식영업은_한_그룹으로_묶인다() {
        Tenancy 집단급식소 = tenancy(1L, "행복유치원", "식품", "집단급식소");
        Tenancy 위탁급식영업 = tenancy(2L, "맛있는위탁업체", "식품", "위탁급식영업");

        var groups = linker.link(List.of(집단급식소, 위탁급식영업));

        assertThat(groups.groups()).hasSize(1);
        assertThat(groups.groups().get(0).tenancies()).containsExactlyInAnyOrder(집단급식소, 위탁급식영업);
    }

    @Test
    void 관련없는_업종끼리는_그룹이_안된다() {
        Tenancy 국밥집 = tenancy(1L, "국밥집", "식품", "일반음식점");
        Tenancy 동물병원 = tenancy(2L, "동물병원", "건강", "의원");

        var groups = linker.link(List.of(국밥집, 동물병원));

        assertThat(groups.groups()).isEmpty();
    }

    @Test
    void 짝이_하나만_있으면_그룹이_안된다() {
        Tenancy 집단급식소 = tenancy(1L, "행복유치원", "식품", "집단급식소");

        var groups = linker.link(List.of(집단급식소));

        assertThat(groups.groups()).isEmpty();
    }
}
