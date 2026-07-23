package com.nextstep.application;

import com.nextstep.domain.businesstype.BusinessTypeRegistry;
import com.nextstep.domain.site.AddressQuery;
import com.nextstep.domain.site.Pnu;
import com.nextstep.domain.site.Site;
import com.nextstep.domain.tenancy.Tenancy;
import com.nextstep.domain.unit.LocationSource;
import com.nextstep.domain.unit.RelatedLicenseGroups;
import com.nextstep.domain.unit.Unit;
import com.nextstep.infra.geo.KoreanTmCoordinateConverter;
import com.nextstep.infra.persistence.LicensedBusinessRecordEntity;
import com.nextstep.infra.persistence.LicensedBusinessRecordRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@Transactional(readOnly = true)
public class TenancyQueryService {

    private static final String UNIT_ID_SEPARATOR = "-U";

    private final LicensedBusinessRecordRepository recordRepository;
    private final SitePartitioner sitePartitioner;
    private final UnitGrouper unitGrouper;
    private final TenancyMerger tenancyMerger;
    private final RelatedLicenseLinker relatedLicenseLinker;

    public TenancyQueryService(LicensedBusinessRecordRepository recordRepository) {
        this.recordRepository = recordRepository;
        BusinessTypeRegistry registry = new BusinessTypeRegistry();
        this.sitePartitioner = new SitePartitioner(registry);
        this.unitGrouper = new UnitGrouper();
        this.tenancyMerger = new TenancyMerger(registry);
        this.relatedLicenseLinker = new RelatedLicenseLinker(registry);
    }

    public List<Site> searchSites(String query) {
        // 2026-07-23 보정: 이 계획(7/20) 작성 이후 배포된 토큰 AND 매칭 검색(의사결정-기록.md
        // §15) 이관 — 원안의 단순 substring 검색은 폐기.
        AddressQuery addressQuery = AddressQuery.of(query);
        List<LicensedBusinessRecordEntity> candidates = recordRepository.searchByAddress(addressQuery.anchorToken());
        String trimmedQuery = query.trim();
        List<LicensedBusinessRecordEntity> records = candidates.stream()
            .filter(r -> trimmedQuery.equals(r.getPnu()) || addressQuery.matchesAll(r.getJibunAddress(), r.getRoadAddress()))
            .toList();
        return assembleSites(records);
    }

    public Optional<Site> findSiteWithUnits(String pnu) {
        List<LicensedBusinessRecordEntity> records = recordRepository.findByPnuOrderByLicensedAtAscIdAsc(pnu);
        if (records.isEmpty()) return Optional.empty();
        return Optional.of(toSite(records));
    }

    public record UnitWithSite(Unit unit, Site site) {
    }

    public Optional<UnitWithSite> findUnitWithTenancies(String unitId) {
        Optional<UnitReference> unitReference = parseUnitId(unitId);
        if (unitReference.isEmpty()) return Optional.empty();

        List<LicensedBusinessRecordEntity> records =
            recordRepository.findByPnuOrderByLicensedAtAscIdAsc(unitReference.get().pnu());
        if (records.isEmpty()) return Optional.empty();

        List<LicensedBusinessRecordEntity> valid = validRecords(records);
        var partition = sitePartitioner.partition(valid);
        var grouping = unitGrouper.group(unitReference.get().pnu(), partition.storefront());
        int groupIndex = unitReference.get().index() - 1;
        if (groupIndex < 0 || groupIndex >= grouping.unitGroups().size()) return Optional.empty();

        UnitGrouper.UnitGroup group = grouping.unitGroups().get(groupIndex);
        Unit unit = toUnit(group, valid);
        Site siteWithoutUnits = toSiteWithoutUnits(group.records());
        return Optional.of(new UnitWithSite(unit, siteWithoutUnits));
    }

    private List<LicensedBusinessRecordEntity> validRecords(List<LicensedBusinessRecordEntity> records) {
        return records.stream().filter(r -> r.getLicensedAt() != null).toList();
    }

    private List<Site> assembleSites(List<LicensedBusinessRecordEntity> records) {
        if (records.isEmpty()) return List.of();

        Map<String, List<LicensedBusinessRecordEntity>> recordsByPnu = records.stream()
            .collect(Collectors.groupingBy(LicensedBusinessRecordEntity::getPnu, LinkedHashMap::new, Collectors.toList()));
        return recordsByPnu.values().stream()
            .map(this::toSite)
            .toList();
    }

    private Site toSite(List<LicensedBusinessRecordEntity> records) {
        List<LicensedBusinessRecordEntity> valid = validRecords(records);
        LicensedBusinessRecordEntity representative = valid.isEmpty() ? records.get(0) : valid.get(0);
        String pnu = representative.getPnu();

        var partition = sitePartitioner.partition(valid);
        var grouping = unitGrouper.group(pnu, partition.storefront());

        List<Unit> units = grouping.unitGroups().stream()
            .map(group -> toUnit(group, valid))
            .toList();
        List<Tenancy> noStorefrontRegistrations = tenancyMerger.merge(valid, partition.noPhysicalStore());
        List<Tenancy> unlocatedRegistrations = tenancyMerger.merge(valid, grouping.unlocated());

        return new Site(new Pnu(pnu), representative.getJibunAddress(), representative.getRoadAddress(),
            KoreanTmCoordinateConverter.fromEpsg5174(representative.getOriginalX(), representative.getOriginalY())
                .orElse(null),
            units, noStorefrontRegistrations, unlocatedRegistrations);
    }

    private Site toSiteWithoutUnits(List<LicensedBusinessRecordEntity> records) {
        List<LicensedBusinessRecordEntity> valid = validRecords(records);
        LicensedBusinessRecordEntity representative = valid.isEmpty() ? records.get(0) : valid.get(0);
        return new Site(new Pnu(representative.getPnu()), representative.getJibunAddress(),
            representative.getRoadAddress(), KoreanTmCoordinateConverter
                .fromEpsg5174(representative.getOriginalX(), representative.getOriginalY())
                .orElse(null), List.of(), List.of(), List.of());
    }

    private Unit toUnit(UnitGrouper.UnitGroup group, List<LicensedBusinessRecordEntity> allRecordsAtSamePnu) {
        List<Tenancy> tenancies = tenancyMerger.merge(allRecordsAtSamePnu, group.records());
        RelatedLicenseGroups relatedLicenseGroups = relatedLicenseLinker.link(tenancies);
        LicensedBusinessRecordEntity representative = group.records().get(0);
        return new Unit(group.unitId(), unitLabel(representative), LocationSource.LICENSE, tenancies,
            representative.getParsedFloor(), representative.getParsedUnitNo(), representative.getParseConfidence(),
            relatedLicenseGroups);
    }

    private Optional<UnitReference> parseUnitId(String unitId) {
        if (unitId == null) return Optional.empty();

        int separatorIndex = unitId.lastIndexOf(UNIT_ID_SEPARATOR);
        if (separatorIndex <= 0) return Optional.empty();

        String pnu = unitId.substring(0, separatorIndex);
        String rawIndex = unitId.substring(separatorIndex + UNIT_ID_SEPARATOR.length());
        try {
            int index = Integer.parseInt(rawIndex);
            if (index < 1) return Optional.empty();
            return Optional.of(new UnitReference(pnu, index));
        } catch (NumberFormatException e) {
            return Optional.empty();
        }
    }

    private String unitLabel(LicensedBusinessRecordEntity record) {
        if (!com.nextstep.domain.site.AddressDetailParser.CONFIDENCE_HIGH.equals(record.getParseConfidence())) {
            return "단일 점포";
        }

        String floor = record.getParsedFloor();
        String unitNo = record.getParsedUnitNo();
        String buildingName = record.getParsedBuildingName();

        if (unitNo != null) {
            return (floor != null ? floor + "층 " : "") + unitNo + "호";
        }
        if (floor != null) {
            return floor + "층";
        }
        if (buildingName != null) {
            return buildingName;
        }
        return "단일(상세주소불명)";
    }

    private record UnitReference(String pnu, int index) {
    }
}
