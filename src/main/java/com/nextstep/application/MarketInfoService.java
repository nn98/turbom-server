package com.nextstep.application;

import com.nextstep.domain.market.MarketInfo;
import com.nextstep.infra.sangga.SanggaApiClient;
import com.nextstep.infra.sangga.SanggaProperties;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

@Service
public class MarketInfoService {

    private static final int RADIUS_METERS = 300;

    private final SanggaApiClient sanggaApiClient;
    private final SanggaProperties sanggaProperties;

    public MarketInfoService(SanggaApiClient sanggaApiClient, SanggaProperties sanggaProperties) {
        this.sanggaApiClient = sanggaApiClient;
        this.sanggaProperties = sanggaProperties;
    }

    @Cacheable(value = "sameCategoryNearbyCount", key = "#pnu + ':' + #subCategory")
    public MarketInfo fetch(String pnu, Double lon, Double lat, String subCategory) {
        if (lon == null || lat == null || sanggaProperties.serviceKey() == null || sanggaProperties.serviceKey().isBlank()) {
            return MarketInfo.unavailable();
        }
        try {
            int count = sanggaApiClient.countSameCategoryInRadius(lon, lat, RADIUS_METERS, subCategory);
            return MarketInfo.of(count);
        } catch (Exception e) {
            return MarketInfo.unavailable();
        }
    }
}
