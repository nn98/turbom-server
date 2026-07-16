package com.nextstep.web.dto;

import java.time.LocalDate;
import java.util.List;

public class ApiDtos {

    public record SearchResponse(List<SiteCandidateDto> candidates) {
    }

    // currentSubCategory: 현재 영업 중인 업종 소분류. 전체 공실이면 null.
    public record SiteCandidateDto(String pnu, String jibunAddress, String roadAddress,
                                    Double latitude, Double longitude, int unitCount, int closedCount,
                                    String currentSubCategory) {
    }

    public record SiteDetailResponse(SiteDto site, List<UnitSummaryDto> units, DisclaimerDto disclaimer) {
    }

    public record SiteDto(String pnu, String jibunAddress, String roadAddress, Double latitude, Double longitude) {
    }

    public record UnitSummaryDto(String unitId, String label, String currentBusinessName, String currentStatus,
                                  int totalTenancyCount, int closedCount, Integer averageSurvivalMonths,
                                  String industryDetail, String locationSource,
                                  String parsedFloor, String parsedUnitNo, String parseConfidence) {
    }

    public record UnitDetailResponse(UnitDto unit, UnitStatisticsDto statistics, List<TenancyDto> timeline,
                                      DisclaimerDto disclaimer) {
    }

    public record UnitDto(String unitId, String label, String jibunAddress, String roadAddress,
                           String parsedFloor, String parsedUnitNo, String parseConfidence) {
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
                                 Double vacancyRatePercent, LocalDate asOf,
                                 Integer totalStoreCount, List<CategoryCountDto> categoryBreakdown) {
    }

    // 반경 내 상가 대분류 하나의 점포수·비중(0~1). 프론트가 업종을 선택하면 이 목록에서
    // 골라 "그 업종 점포수 / 경쟁률(ratio)"을 보여줄 수 있다.
    public record CategoryCountDto(String code, String name, int count, double ratio) {
    }

    public record DisclaimerDto(LocalDate dataAsOf, String note) {
    }

    public record ErrorResponse(String error, String message) {
    }
}
