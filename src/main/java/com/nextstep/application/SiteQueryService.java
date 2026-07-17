package com.nextstep.application;

import com.nextstep.domain.exception.InvalidQueryException;
import com.nextstep.domain.exception.SiteNotFoundException;
import com.nextstep.domain.exception.UnitNotFoundException;
import com.nextstep.domain.market.MarketInfo;
import com.nextstep.domain.site.Site;
import com.nextstep.domain.tenancy.Tenancy;
import com.nextstep.domain.unit.Unit;
import com.nextstep.infra.sangga.SanggaApiClient;
import com.nextstep.web.dto.ApiDtos.*;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

@Service
public class SiteQueryService {

    private static final String DISCLAIMER_NOTE = "인허가 신고 기준 데이터로 실제 영업 현황과 차이가 있을 수 있습니다.";
    private static final int ENRICHMENT_RADIUS_METERS = 300;

    private final TenancyQueryService tenancyQueryService;
    private final MarketInfoService marketInfoService;
    private final SanggaApiClient sanggaApiClient;

    public SiteQueryService(TenancyQueryService tenancyQueryService, MarketInfoService marketInfoService,
                             SanggaApiClient sanggaApiClient) {
        this.tenancyQueryService = tenancyQueryService;
        this.marketInfoService = marketInfoService;
        this.sanggaApiClient = sanggaApiClient;
    }

    public SearchResponse search(String query) {
        if (query == null || query.isBlank()) {
            throw new InvalidQueryException();
        }
        List<SiteCandidateDto> candidates = tenancyQueryService.searchSites(query).stream()
            .map(this::toCandidateDto)
            .toList();
        return new SearchResponse(candidates);
    }

    public SiteDetailResponse getSiteDetail(String pnu) {
        Site site = tenancyQueryService.findSiteWithUnits(pnu)
            .orElseThrow(() -> new SiteNotFoundException(pnu));

        Double lat = site.coordinate() == null ? null : site.coordinate().latitude();
        Double lon = site.coordinate() == null ? null : site.coordinate().longitude();
        Map<String, String> storeDetails = lookupStoreDetails(lon, lat);

        List<UnitSummaryDto> units = site.units().stream()
            .map(unit -> toUnitSummaryDto(unit, storeDetails))
            .sorted(Comparator.comparingInt(UnitSummaryDto::closedCount).reversed())
            .toList();

        List<NoStorefrontRegistrationDto> noStorefrontRegistrations = site.noStorefrontRegistrations().stream()
            .map(this::toNoStorefrontRegistrationDto)
            .toList();

        return new SiteDetailResponse(toSiteDto(site), units, noStorefrontRegistrations, disclaimer());
    }

    public UnitDetailResponse getUnitDetail(String unitId) {
        var unitWithSite = tenancyQueryService.findUnitWithTenancies(unitId)
            .orElseThrow(() -> new UnitNotFoundException(unitId));
        Unit unit = unitWithSite.unit();
        Site site = unitWithSite.site();

        String representativeSubCategory = unit.tenancies().stream()
            .max(Comparator.comparing(t -> t.period().licensedAt()))
            .map(Tenancy::subCategory)
            .orElse(null);
        Double lat = site.coordinate() == null ? null : site.coordinate().latitude();
        Double lon = site.coordinate() == null ? null : site.coordinate().longitude();

        var marketInfo = marketInfoService.fetch(site.pnu().value(), lon, lat, representativeSubCategory);
        Map<String, String> storeDetails = lookupStoreDetails(lon, lat);

        List<TenancyDto> timeline = unit.tenancies().stream()
            .map(t -> toTenancyDto(t, marketInfo, storeDetails))
            .toList();

        UnitDto unitDto = new UnitDto(unit.unitId(), unit.label(), site.jibunAddress(), site.roadAddress(),
            unit.parsedFloor(), unit.parsedUnitNo(), unit.parseConfidence());
        UnitStatisticsDto statisticsDto = toStatisticsDto(unit);

        return new UnitDetailResponse(unitDto, statisticsDto, timeline, disclaimer());
    }

    private Map<String, String> lookupStoreDetails(Double lon, Double lat) {
        if (lon == null || lat == null) return Map.of();
        return sanggaApiClient.lookupStoreDetails(lon, lat, ENRICHMENT_RADIUS_METERS);
    }

    private NoStorefrontRegistrationDto toNoStorefrontRegistrationDto(Tenancy tenancy) {
        return new NoStorefrontRegistrationDto(tenancy.businessName(), tenancy.category(), tenancy.subCategory(),
            tenancy.period().licensedAt(), tenancy.period().closedAt(), tenancy.displayStatus());
    }

    private String lookupIndustryDetail(String businessName, Map<String, String> storeDetails) {
        if (businessName == null || storeDetails.isEmpty()) return null;
        return storeDetails.get(businessName.trim().toLowerCase());
    }

    private SiteCandidateDto toCandidateDto(Site site) {
        int closedCount = site.units().stream()
            .flatMap(u -> u.tenancies().stream())
            .filter(Tenancy::isClosed)
            .toList().size();
        Double lat = site.coordinate() == null ? null : site.coordinate().latitude();
        Double lon = site.coordinate() == null ? null : site.coordinate().longitude();
        String currentSubCategory = site.units().stream()
            .flatMap(u -> u.currentTenancy().stream())
            .map(Tenancy::subCategory)
            .findFirst()
            .orElse(null);
        return new SiteCandidateDto(site.pnu().value(), site.jibunAddress(), site.roadAddress(),
            lat, lon, site.units().size(), closedCount, currentSubCategory);
    }

    private SiteDto toSiteDto(Site site) {
        Double lat = site.coordinate() == null ? null : site.coordinate().latitude();
        Double lon = site.coordinate() == null ? null : site.coordinate().longitude();
        return new SiteDto(site.pnu().value(), site.jibunAddress(), site.roadAddress(), lat, lon);
    }

    private UnitSummaryDto toUnitSummaryDto(Unit unit, Map<String, String> storeDetails) {
        var stats = unit.statistics();
        String currentBusinessName = unit.currentTenancy().map(Tenancy::businessName).orElse(null);
        String currentStatus = unit.currentTenancy().isPresent() ? "영업" : "공실";
        String industryDetail = unit.currentTenancy()
            .map(t -> lookupIndustryDetail(t.businessName(), storeDetails))
            .orElse(null);
        return new UnitSummaryDto(unit.unitId(), unit.label(), currentBusinessName, currentStatus,
            stats.totalTenancyCount(), stats.closedCount(), stats.averageSurvivalMonths(),
            industryDetail, unit.locationSource().dbValue(),
            unit.parsedFloor(), unit.parsedUnitNo(), unit.parseConfidence());
    }

    private UnitStatisticsDto toStatisticsDto(Unit unit) {
        var stats = unit.statistics();
        return new UnitStatisticsDto(stats.totalTenancyCount(), stats.closedCount(), stats.averageSurvivalMonths(),
            stats.longestSurvivalMonths(), stats.shortestSurvivalMonths());
    }

    private TenancyDto toTenancyDto(Tenancy tenancy, MarketInfo marketInfo, Map<String, String> storeDetails) {
        List<CategoryCountDto> categoryBreakdown = marketInfo.categoryBreakdown().stream()
            .map(c -> new CategoryCountDto(c.code(), c.name(), c.count(), c.ratio()))
            .toList();
        MarketInfoDto marketInfoDto = new MarketInfoDto(marketInfo.isPlaceholder(), MarketInfo.LEASE_AREA_SQM,
            MarketInfo.DEPOSIT_KRW, MarketInfo.MONTHLY_RENT_KRW, MarketInfo.KEY_MONEY_KRW,
            MarketInfo.DAILY_FLOATING_POPULATION, marketInfo.sameCategoryNearbyCount(),
            MarketInfo.VACANCY_RATE_PERCENT, marketInfo.asOf(),
            marketInfo.totalStoreCount(), categoryBreakdown);

        // 폐업 이력은 Sangga API에 없으므로 원천적으로 null; 영업 중이면 이름 매칭 시도
        String industryDetail = tenancy.isActive() ? lookupIndustryDetail(tenancy.businessName(), storeDetails) : null;
        String enrichmentSource = industryDetail != null ? "sangga_api" : tenancy.enrichmentSource();

        return new TenancyDto("t-" + tenancy.id(), tenancy.businessName(), tenancy.category(), tenancy.subCategory(),
            industryDetail, tenancy.period().licensedAt(), tenancy.period().closedAt(),
            tenancy.displayStatus(), tenancy.survivalMonths(), tenancy.closedAtEstimated(),
            enrichmentSource, marketInfoDto);
    }

    private DisclaimerDto disclaimer() {
        return new DisclaimerDto(LocalDate.now(), DISCLAIMER_NOTE);
    }
}
