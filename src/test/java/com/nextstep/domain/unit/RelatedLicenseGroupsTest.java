package com.nextstep.domain.unit;

import com.nextstep.domain.businesstype.BusinessTypeKey;
import com.nextstep.domain.tenancy.Tenancy;
import com.nextstep.domain.tenancy.TenancyPeriod;
import org.junit.jupiter.api.Test;
import java.time.LocalDate;
import java.util.List;
import static org.assertj.core.api.Assertions.assertThat;

class RelatedLicenseGroupsTest {

    private Tenancy tenancy(long id, String businessName, String category, String subCategory) {
        return new Tenancy(id, businessName, category, subCategory, null,
            new TenancyPeriod(LocalDate.of(2020, 1, 1), null), "영업/정상", "license_only",
            com.nextstep.domain.businesstype.ReliabilitySignal.confirmed());
    }

    @Test
    void groupContaining은_해당_tenancy가_속한_그룹을_찾는다() {
        Tenancy 집단급식소 = tenancy(1L, "행복유치원", "식품", "집단급식소");
        Tenancy 위탁급식영업 = tenancy(2L, "맛있는위탁업체", "식품", "위탁급식영업");
        RelatedLicenseGroup group = new RelatedLicenseGroup(
            List.of(new BusinessTypeKey("식품", "집단급식소"), new BusinessTypeKey("식품", "위탁급식영업")),
            List.of(집단급식소, 위탁급식영업));
        RelatedLicenseGroups groups = new RelatedLicenseGroups(List.of(group));

        assertThat(groups.groupContaining(집단급식소)).contains(group);
        assertThat(groups.groupContaining(위탁급식영업)).contains(group);
    }

    @Test
    void 속하지_않은_tenancy는_empty() {
        Tenancy 무관한업체 = tenancy(3L, "무관한업체", "건강", "의원");
        assertThat(RelatedLicenseGroups.none().groupContaining(무관한업체)).isEmpty();
    }
}
