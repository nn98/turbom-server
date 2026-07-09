package com.nextstep.application;

import com.nextstep.domain.market.MarketInfo;
import com.nextstep.infra.sangga.SanggaApiClient;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

@Service
public class MarketInfoService {

    private static final int RADIUS_METERS = 300;

    private final SanggaApiClient sanggaApiClient;

    public MarketInfoService(SanggaApiClient sanggaApiClient) {
        this.sanggaApiClient = sanggaApiClient;
    }

    @Cacheable(value = "sameCategoryNearbyCount", key = "#pnu + ':' + #subCategory")
    public MarketInfo fetch(String pnu, Double lon, Double lat, String subCategory) {
        if (lon == null || lat == null) {
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
