package com.nextstep.application;

import com.nextstep.domain.businesstype.BusinessTypeRegistry;
import com.nextstep.infra.persistence.LicensedBusinessRecordEntity;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.assertj.core.api.Assertions.assertThat;

class SitePartitionerTest {

    private final SitePartitioner partitioner = new SitePartitioner(new BusinessTypeRegistry());

    @Test
    void 통신판매업만_있으면_무점포로_분류된다() {
        LicensedBusinessRecordEntity record = TestFixtures.record(
            1L, "생활", "통신판매업", "온라인셀러", null, null, null, "LOW");

        var partition = partitioner.partition(List.of(record));

        assertThat(partition.storefront()).isEmpty();
        assertThat(partition.noPhysicalStore()).containsExactly(record);
    }

    @Test
    void 같은_상호가_매장업종도_있으면_전부_매장으로_취급된다() {
        LicensedBusinessRecordEntity 동물병원 = TestFixtures.record(
            1L, "동물", "동물병원", "동물병원 더 하임", null, null, null, "HIGH");
        LicensedBusinessRecordEntity 동물미용업 = TestFixtures.record(
            2L, "동물", "동물미용업", "동물병원 더 하임", null, null, null, "HIGH");

        var partition = partitioner.partition(List.of(동물병원, 동물미용업));

        assertThat(partition.storefront()).containsExactlyInAnyOrder(동물병원, 동물미용업);
        assertThat(partition.noPhysicalStore()).isEmpty();
    }

    @Test
    void 일반음식점은_매장으로_분류된다() {
        LicensedBusinessRecordEntity record = TestFixtures.record(
            1L, "식품", "일반음식점", "국밥집", null, null, null, "HIGH");

        var partition = partitioner.partition(List.of(record));

        assertThat(partition.storefront()).containsExactly(record);
    }
}
