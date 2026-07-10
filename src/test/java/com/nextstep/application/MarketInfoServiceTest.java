package com.nextstep.application;

import com.nextstep.domain.market.MarketInfo;
import com.nextstep.infra.sangga.SanggaApiClient;
import com.nextstep.infra.sangga.SanggaProperties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MarketInfoServiceTest {

    @Mock SanggaApiClient sanggaApiClient;

    private static final SanggaProperties CONFIGURED_PROPERTIES = new SanggaProperties("https://example.com", "test-key");

    @Test
    void 상가API가_성공하면_실값을_담는다() {
        // "동물미용업" -> IndustryCategoryMapper가 "S2"(수리·개인)로 매핑
        when(sanggaApiClient.countInRadiusByCategory(127.14, 37.44, 300, "S2")).thenReturn(5);
        MarketInfoService service = new MarketInfoService(sanggaApiClient, CONFIGURED_PROPERTIES);

        MarketInfo result = service.fetch("pnu", 127.14, 37.44, "동물미용업");

        assertThat(result.sameCategoryNearbyCount()).isEqualTo(5);
        assertThat(result.isPlaceholder()).isTrue();
    }

    @Test
    void 상가API가_예외를_던지면_null로_대체한다() {
        when(sanggaApiClient.countInRadiusByCategory(anyDouble(), anyDouble(), anyInt(), anyString()))
            .thenThrow(new RuntimeException("network blocked"));
        MarketInfoService service = new MarketInfoService(sanggaApiClient, CONFIGURED_PROPERTIES);

        MarketInfo result = service.fetch("pnu", 127.14, 37.44, "동물미용업");

        assertThat(result.sameCategoryNearbyCount()).isNull();
    }

    @Test
    void 상가분류로_매핑되지_않는_소분류는_API를_호출하지_않고_null() {
        MarketInfoService service = new MarketInfoService(sanggaApiClient, CONFIGURED_PROPERTIES);

        MarketInfo result = service.fetch("pnu", 127.14, 37.44, "도축업");

        assertThat(result.sameCategoryNearbyCount()).isNull();
        verifyNoInteractions(sanggaApiClient);
    }

    @Test
    void 좌표가_없으면_API를_호출하지_않고_null() {
        MarketInfoService service = new MarketInfoService(sanggaApiClient, CONFIGURED_PROPERTIES);

        MarketInfo result = service.fetch("pnu", null, null, "동물미용업");

        assertThat(result.sameCategoryNearbyCount()).isNull();
    }

    @Test
    void 서비스키가_없으면_API를_호출하지_않고_null() {
        MarketInfoService service = new MarketInfoService(sanggaApiClient, new SanggaProperties("https://example.com", ""));

        MarketInfo result = service.fetch("pnu", 127.14, 37.44, "동물미용업");

        assertThat(result.sameCategoryNearbyCount()).isNull();
        verifyNoInteractions(sanggaApiClient);
    }
}
