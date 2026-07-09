package com.nextstep.application;

import com.nextstep.domain.market.MarketInfo;
import com.nextstep.infra.sangga.SanggaApiClient;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MarketInfoServiceTest {

    @Mock SanggaApiClient sanggaApiClient;

    @Test
    void 상가API가_성공하면_실값을_담는다() {
        when(sanggaApiClient.countSameCategoryInRadius(127.14, 37.44, 300, "동물미용업")).thenReturn(5);
        MarketInfoService service = new MarketInfoService(sanggaApiClient);

        MarketInfo result = service.fetch("pnu", 127.14, 37.44, "동물미용업");

        assertThat(result.sameCategoryNearbyCount()).isEqualTo(5);
        assertThat(result.isPlaceholder()).isTrue();
    }

    @Test
    void 상가API가_예외를_던지면_null로_대체한다() {
        when(sanggaApiClient.countSameCategoryInRadius(anyDouble(), anyDouble(), anyInt(), anyString()))
            .thenThrow(new RuntimeException("network blocked"));
        MarketInfoService service = new MarketInfoService(sanggaApiClient);

        MarketInfo result = service.fetch("pnu", 127.14, 37.44, "동물미용업");

        assertThat(result.sameCategoryNearbyCount()).isNull();
    }

    @Test
    void 좌표가_없으면_API를_호출하지_않고_null() {
        MarketInfoService service = new MarketInfoService(sanggaApiClient);

        MarketInfo result = service.fetch("pnu", null, null, "동물미용업");

        assertThat(result.sameCategoryNearbyCount()).isNull();
    }
}
