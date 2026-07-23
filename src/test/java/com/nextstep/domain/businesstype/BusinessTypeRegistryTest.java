package com.nextstep.domain.businesstype;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class BusinessTypeRegistryTest {

    private final BusinessTypeRegistry registry = new BusinessTypeRegistry();

    @Test
    void 통신판매업은_매장없음으로_등록된다() {
        BusinessType type = registry.lookup("생활", "통신판매업");
        assertThat(type.locationCertainty()).isEqualTo(LocationCertainty.NO_PHYSICAL_STORE);
    }

    @Test
    void 담배소매업은_매장있음으로_등록된다() {
        // 84.5%로 90% 임계값 미만 — turbom-spec 의사결정-기록.md §9, NoStorefrontSubCategoriesTest 원본 근거
        BusinessType type = registry.lookup("기타", "담배소매업");
        assertThat(type.locationCertainty()).isEqualTo(LocationCertainty.LOCATED);
    }

    @Test
    void 담배소매업은_peerBased_신뢰도규칙을_쓴다() {
        BusinessType type = registry.lookup("기타", "담배소매업");
        LocationContext context = new LocationContext(java.util.List.of(
            new LocationContext.SiblingRecord("씨유 성남대왕판교로점", java.time.LocalDate.of(1999, 1, 15)),
            new LocationContext.SiblingRecord("다른가게", java.time.LocalDate.of(2020, 1, 1))));

        ReliabilitySignal signal = type.reliabilitySignal(
            "씨유 성남대왕판교로점", java.time.LocalDate.of(1999, 1, 15), context);

        assertThat(signal.level()).isEqualTo(ReliabilitySignal.Level.NEEDS_VERIFICATION);
    }

    @Test
    void 집단급식소와_위탁급식영업은_서로를_관련인허가로_가리킨다() {
        BusinessTypeKey 집단급식소 = new BusinessTypeKey("식품", "집단급식소");
        BusinessTypeKey 위탁급식영업 = new BusinessTypeKey("식품", "위탁급식영업");

        assertThat(registry.lookup(집단급식소.category(), 집단급식소.subCategory())
            .relatedTypeKeys().pairsWith(위탁급식영업)).isTrue();
        assertThat(registry.lookup(위탁급식영업.category(), 위탁급식영업.subCategory())
            .relatedTypeKeys().pairsWith(집단급식소)).isTrue();
    }

    @Test
    void 미등록_조합은_기본값이다() {
        BusinessType type = registry.lookup("없는카테고리", "없는소분류");
        assertThat(type.locationCertainty()).isEqualTo(LocationCertainty.LOCATED);
        assertThat(type.relatedTypeKeys().values()).isEmpty();
    }

    @Test
    void category나_subCategory가_null이면_기본값() {
        assertThat(registry.lookup(null, "통신판매업").locationCertainty()).isEqualTo(LocationCertainty.LOCATED);
        assertThat(registry.lookup("생활", null).locationCertainty()).isEqualTo(LocationCertainty.LOCATED);
    }

    @Test
    void 일반음식점은_매장있음이다() {
        assertThat(registry.lookup("식품", "일반음식점").locationCertainty()).isEqualTo(LocationCertainty.LOCATED);
    }
}
