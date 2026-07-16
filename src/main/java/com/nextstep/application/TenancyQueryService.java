package com.nextstep.application;

import com.nextstep.domain.site.AddressDetailParser;
import com.nextstep.domain.site.Pnu;
import com.nextstep.domain.site.Site;
import com.nextstep.domain.tenancy.Tenancy;
import com.nextstep.domain.tenancy.TenancyPeriod;
import com.nextstep.domain.unit.LocationSource;
import com.nextstep.domain.unit.Unit;
import com.nextstep.infra.geo.KoreanTmCoordinateConverter;
import com.nextstep.infra.persistence.LicensedBusinessRecordEntity;
import com.nextstep.infra.persistence.LicensedBusinessRecordRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
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

    public TenancyQueryService(LicensedBusinessRecordRepository recordRepository) {
        this.recordRepository = recordRepository;
    }

    public List<Site> searchSites(String query) {
        return assembleSites(recordRepository.searchByAddress(query));
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

        List<LicensedBusinessRecordEntity> records = recordRepository.findByPnuOrderByLicensedAtAscIdAsc(unitReference.get().pnu());
        if (records.isEmpty()) return Optional.empty();

        List<UnitGroup> groups = unitGroups(unitReference.get().pnu(), validRecords(records));
        int groupIndex = unitReference.get().index() - 1;
        if (groupIndex < 0 || groupIndex >= groups.size()) return Optional.empty();

        UnitGroup group = groups.get(groupIndex);
        return Optional.of(new UnitWithSite(toUnit(group), toSiteWithoutUnits(group.records())));
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
        return new Site(new Pnu(representative.getPnu()), representative.getJibunAddress(),
            representative.getRoadAddress(), KoreanTmCoordinateConverter
                .fromEpsg5174(representative.getOriginalX(), representative.getOriginalY())
                .orElse(null), toUnits(representative.getPnu(), valid));
    }

    private Site toSiteWithoutUnits(List<LicensedBusinessRecordEntity> records) {
        List<LicensedBusinessRecordEntity> valid = validRecords(records);
        LicensedBusinessRecordEntity representative = valid.isEmpty() ? records.get(0) : valid.get(0);
        return new Site(new Pnu(representative.getPnu()), representative.getJibunAddress(),
            representative.getRoadAddress(), KoreanTmCoordinateConverter
                .fromEpsg5174(representative.getOriginalX(), representative.getOriginalY())
                .orElse(null), List.of());
    }

    private List<Unit> toUnits(String pnu, List<LicensedBusinessRecordEntity> records) {
        return unitGroups(pnu, records).stream()
            .map(this::toUnit)
            .toList();
    }

    private Unit toUnit(UnitGroup group) {
        List<Tenancy> tenancies = group.records().stream()
            .map(this::toTenancy)
            .sorted(Comparator.comparing(t -> t.period().licensedAt()))
            .toList();
        LicensedBusinessRecordEntity representative = group.records().get(0);
        return new Unit(group.unitId(), unitLabel(representative), LocationSource.LICENSE, tenancies,
            representative.getParsedFloor(), representative.getParsedUnitNo(), representative.getParseConfidence());
    }

    private Tenancy toTenancy(LicensedBusinessRecordEntity entity) {
        return new Tenancy(
            entity.getId(),
            entity.getBusinessName(),
            entity.getCategory(),
            entity.getSubCategory(),
            null,
            new TenancyPeriod(entity.getLicensedAt(), entity.getClosedAt()),
            entity.getBusinessStatus(),
            "license_only"
        );
    }

    private List<UnitGroup> unitGroups(String pnu, List<LicensedBusinessRecordEntity> records) {
        Map<String, List<LicensedBusinessRecordEntity>> recordsByAddress = records.stream()
            .collect(Collectors.groupingBy(this::unitKey, LinkedHashMap::new, Collectors.toList()));

        List<UnitGroup> groups = new ArrayList<>();
        int index = 1;
        for (List<LicensedBusinessRecordEntity> addressRecords : recordsByAddress.values()) {
            groups.add(new UnitGroup(unitId(pnu, index), addressRecords));
            index++;
        }
        return groups;
    }

    private String unitKey(LicensedBusinessRecordEntity record) {
        if (AddressDetailParser.CONFIDENCE_LOW.equals(record.getParseConfidence())) {
            // UNPARSED: parsedBuildingName에 원본 상세 문자열이 저장됨
            String detail = record.getParsedBuildingName();
            return detail != null ? normalize(detail) : "__unknown__";
        }
        // HIGH (NONE / REGEX): 구조화된 위치 속성 — jibunAddress 표현 무관하게 동일 층·호 병합
        return record.getParsedFloor() + "::" + record.getParsedUnitNo() + "::" + record.getParsedBuildingName();
    }

    private String normalize(String value) {
        if (value == null) return "";
        return value.trim().replaceAll("\\s+", " ");
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

    private String unitId(String pnu, int index) {
        return pnu + UNIT_ID_SEPARATOR + index;
    }

    private String unitLabel(LicensedBusinessRecordEntity record) {
        if (!AddressDetailParser.CONFIDENCE_HIGH.equals(record.getParseConfidence())) {
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

    private record UnitGroup(String unitId, List<LicensedBusinessRecordEntity> records) {
    }

    private record UnitReference(String pnu, int index) {
    }
}
