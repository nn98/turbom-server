package com.nextstep.application;

import com.nextstep.domain.site.Coordinate;
import com.nextstep.domain.site.Pnu;
import com.nextstep.domain.site.Site;
import com.nextstep.domain.tenancy.BusinessStatus;
import com.nextstep.domain.tenancy.Tenancy;
import com.nextstep.domain.tenancy.TenancyPeriod;
import com.nextstep.domain.unit.LocationSource;
import com.nextstep.domain.unit.Unit;
import com.nextstep.infra.persistence.SiteEntity;
import com.nextstep.infra.persistence.SiteJpaRepository;
import com.nextstep.infra.persistence.TenancyEntity;
import com.nextstep.infra.persistence.TenancyJpaRepository;
import com.nextstep.infra.persistence.UnitEntity;
import com.nextstep.infra.persistence.UnitJpaRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;

@Service
@Transactional(readOnly = true)
public class TenancyQueryService {

    private final SiteJpaRepository siteRepository;
    private final UnitJpaRepository unitRepository;
    private final TenancyJpaRepository tenancyRepository;

    public TenancyQueryService(SiteJpaRepository siteRepository, UnitJpaRepository unitRepository,
                                TenancyJpaRepository tenancyRepository) {
        this.siteRepository = siteRepository;
        this.unitRepository = unitRepository;
        this.tenancyRepository = tenancyRepository;
    }

    public List<Site> searchSites(String query) {
        return siteRepository.searchByAddress(query).stream()
            .map(this::assembleSite)
            .toList();
    }

    public Optional<Site> findSiteWithUnits(String pnu) {
        return siteRepository.findById(pnu).map(this::assembleSite);
    }

    public record UnitWithSite(Unit unit, Site site) {
    }

    public Optional<UnitWithSite> findUnitWithTenancies(String unitId) {
        return unitRepository.findById(unitId).flatMap(unitEntity ->
            siteRepository.findById(unitEntity.getSitePnu()).map(siteEntity -> {
                Unit unit = assembleUnit(unitEntity);
                Site site = new Site(new Pnu(siteEntity.getPnu()), siteEntity.getJibunAddress(),
                    siteEntity.getRoadAddress(), toCoordinate(siteEntity), List.of());
                return new UnitWithSite(unit, site);
            })
        );
    }

    private Site assembleSite(SiteEntity siteEntity) {
        List<UnitEntity> unitEntities = unitRepository.findBySitePnu(siteEntity.getPnu());
        List<Unit> units = unitEntities.stream().map(this::assembleUnit).toList();
        return new Site(new Pnu(siteEntity.getPnu()), siteEntity.getJibunAddress(),
            siteEntity.getRoadAddress(), toCoordinate(siteEntity), units);
    }

    private Unit assembleUnit(UnitEntity unitEntity) {
        List<Tenancy> tenancies = tenancyRepository.findByUnitId(unitEntity.getUnitId()).stream()
            .map(this::toTenancy)
            .sorted(Comparator.comparing(t -> t.period().licensedAt()))
            .toList();
        return new Unit(unitEntity.getUnitId(), unitEntity.getLabel(),
            LocationSource.fromDb(unitEntity.getLocationSource()), tenancies);
    }

    private Tenancy toTenancy(TenancyEntity entity) {
        return new Tenancy(
            entity.getId(),
            entity.getBusinessName(),
            entity.getCategory(),
            entity.getSubCategory(),
            null,
            new TenancyPeriod(entity.getLicensedAt(), entity.getClosedAt()),
            BusinessStatus.fromDb(entity.getStatus()),
            "license_only"
        );
    }

    private Coordinate toCoordinate(SiteEntity siteEntity) {
        if (siteEntity.getLatitude() == null || siteEntity.getLongitude() == null) return null;
        return new Coordinate(siteEntity.getLatitude().doubleValue(), siteEntity.getLongitude().doubleValue());
    }
}
