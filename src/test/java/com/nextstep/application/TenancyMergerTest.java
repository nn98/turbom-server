package com.nextstep.application;

import com.nextstep.domain.businesstype.BusinessTypeRegistry;
import com.nextstep.domain.tenancy.Tenancy;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.assertj.core.api.Assertions.assertThat;

class TenancyMergerTest {

    private final TenancyMerger merger = new TenancyMerger(new BusinessTypeRegistry());

    @Test
    void 같은_상호는_하나의_테넌시로_병합된다() {
        var r1 = TestFixtures.record(1L, "식품", "일반음식점", "국밥집", null, null, null, "HIGH");
        var r2 = TestFixtures.record(2L, "식품", "일반음식점", "국밥집", null, null, null, "HIGH");

        List<Tenancy> tenancies = merger.merge(List.of(r1, r2), List.of(r1, r2));

        assertThat(tenancies).hasSize(1);
        assertThat(tenancies.get(0).businessName()).isEqualTo("국밥집");
    }

    @Test
    void 담배소매업은_같은_자리에_더_늦은_타상호_있으면_NEEDS_VERIFICATION() {
        var 씨유 = TestFixtures.recordWithDates(1L, "기타", "담배소매업", "씨유매장",
            java.time.LocalDate.of(1999, 1, 15), null);
        var 다른가게 = TestFixtures.recordWithDates(2L, "식품", "일반음식점", "다른가게",
            java.time.LocalDate.of(2020, 1, 1), null);

        List<Tenancy> tenancies = merger.merge(List.of(씨유, 다른가게), List.of(씨유));

        assertThat(tenancies).hasSize(1);
        assertThat(tenancies.get(0).reliabilitySignal().level())
            .isEqualTo(com.nextstep.domain.businesstype.ReliabilitySignal.Level.NEEDS_VERIFICATION);
    }

    @Test
    void 일반음식점은_항상_CONFIRMED다() {
        var r1 = TestFixtures.record(1L, "식품", "일반음식점", "국밥집", null, null, null, "HIGH");

        List<Tenancy> tenancies = merger.merge(List.of(r1), List.of(r1));

        assertThat(tenancies.get(0).reliabilitySignal().level())
            .isEqualTo(com.nextstep.domain.businesstype.ReliabilitySignal.Level.CONFIRMED);
    }
}
