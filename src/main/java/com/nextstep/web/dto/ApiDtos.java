package com.nextstep.web.dto;

import java.time.LocalDate;
import java.util.List;

public class ApiDtos {

    public record SearchResponse(List<SiteCandidateDto> candidates) {
    }

    public record SiteCandidateDto(String pnu, String jibunAddress, String roadAddress,
                                    Double latitude, Double longitude, int unitCount, int closedCount) {
    }

    public record SiteDetailResponse(SiteDto site, List<UnitSummaryDto> units, DisclaimerDto disclaimer) {
    }

    public record SiteDto(String pnu, String jibunAddress, String roadAddress, Double latitude, Double longitude) {
    }

    public record UnitSummaryDto(String unitId, String label, String currentBusinessName, String currentStatus,
                                  int totalTenancyCount, int closedCount, Integer averageSurvivalMonths,
                                  String industryDetail, String locationSource) {
    }

    public record UnitDetailResponse(UnitDto unit, UnitStatisticsDto statistics, List<TenancyDto> timeline,
                                      DisclaimerDto disclaimer) {
    }

    public record UnitDto(String unitId, String label, String jibunAddress, String roadAddress) {
    }

    public record UnitStatisticsDto(int totalTenancyCount, int closedCount, Integer averageSurvivalMonths,
                                     Integer longestSurvivalMonths, Integer shortestSurvivalMonths) {
    }

    public record TenancyDto(String tenancyId, String businessName, String category, String subCategory,
                              String industryDetail, LocalDate licensedAt, LocalDate closedAt, String status,
                              Integer survivalMonths, boolean closedAtEstimated, String enrichmentSource,
                              MarketInfoDto marketInfo) {
    }

    public record MarketInfoDto(boolean isPlaceholder, Double leaseAreaSqm, Long depositKrw, Long monthlyRentKrw,
                                 Long keyMoneyKrw, Integer dailyFloatingPopulation, Integer sameCategoryNearbyCount,
                                 Double vacancyRatePercent, LocalDate asOf) {
    }

    public record DisclaimerDto(LocalDate dataAsOf, String note) {
    }

    public record ErrorResponse(String error, String message) {
    }
}
