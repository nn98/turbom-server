package com.nextstep.application;

import com.nextstep.domain.market.MarketInfo;
import com.nextstep.infra.sangga.IndustryCategoryMapper;
import com.nextstep.infra.sangga.SanggaApiClient;
import com.nextstep.infra.sangga.SanggaProperties;
import com.nextstep.infra.sangga.SanggaStoreListResponse;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

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
        var sanggaCategoryCode = IndustryCategoryMapper.toSanggaCategoryCode(subCategory);
        if (sanggaCategoryCode.isEmpty()) {
            return MarketInfo.unavailable();
        }

        int sameCategoryCount;
        try {
            sameCategoryCount = sanggaApiClient.countInRadiusByCategory(lon, lat, RADIUS_METERS, sanggaCategoryCode.get());
        } catch (Exception e) {
            return MarketInfo.unavailable();
        }

        // 전체점포수/업종구성은 별도 호출이라 독립적으로 격리한다 - 이게 실패해도
        // 위에서 이미 구한 sameCategoryCount(핵심 필드)는 그대로 내려간다.
        Integer totalStoreCount = null;
        List<MarketInfo.CategoryCount> categoryBreakdown = List.of();
        try {
            var summary = sanggaApiClient.fetchRadiusSummary(lon, lat, RADIUS_METERS);
            totalStoreCount = summary.totalCount();
            categoryBreakdown = toCategoryBreakdown(summary);
        } catch (Exception e) {
            // totalStoreCount/categoryBreakdown만 비워진 채 내려간다.
        }

        return MarketInfo.of(sameCategoryCount, totalStoreCount, categoryBreakdown);
    }

    private List<MarketInfo.CategoryCount> toCategoryBreakdown(SanggaStoreListResponse.SanggaBody summary) {
        if (summary.items() == null || summary.items().isEmpty()) {
            return List.of();
        }
        Map<String, String> nameByCode = new LinkedHashMap<>();
        Map<String, Integer> countByCode = new LinkedHashMap<>();
        for (var item : summary.items()) {
            if (item.indsLclsCd() == null) continue;
            nameByCode.putIfAbsent(item.indsLclsCd(), item.indsLclsNm());
            countByCode.merge(item.indsLclsCd(), 1, Integer::sum);
        }
        int total = summary.totalCount() == null || summary.totalCount() == 0
            ? summary.items().size() : summary.totalCount();

        return countByCode.entrySet().stream()
            .map(e -> new MarketInfo.CategoryCount(e.getKey(), nameByCode.get(e.getKey()), e.getValue(),
                total == 0 ? 0.0 : (double) e.getValue() / total))
            .sorted(Comparator.comparingInt(MarketInfo.CategoryCount::count).reversed())
            .toList();
    }
}
