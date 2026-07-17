package com.nextstep.application;

import com.nextstep.domain.site.AddressDetailParser;
import com.nextstep.domain.site.NoStorefrontSubCategories;
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

import java.time.LocalDate;
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
    // ponytail: 초기 추정값. 실사례로 오판(과병합/과분리) 나오면 조정
    private static final int SAME_BUSINESS_MERGE_GAP_DAYS = 90;

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
        Map<Boolean, List<LicensedBusinessRecordEntity>> partitioned = partitionByStorefront(valid);
        return new Site(new Pnu(representative.getPnu()), representative.getJibunAddress(),
            representative.getRoadAddress(), KoreanTmCoordinateConverter
                .fromEpsg5174(representative.getOriginalX(), representative.getOriginalY())
                .orElse(null), toUnits(representative.getPnu(), partitioned.get(true)),
            mergedTenancies(partitioned.get(false)));
    }

    private Site toSiteWithoutUnits(List<LicensedBusinessRecordEntity> records) {
        List<LicensedBusinessRecordEntity> valid = validRecords(records);
        LicensedBusinessRecordEntity representative = valid.isEmpty() ? records.get(0) : valid.get(0);
        return new Site(new Pnu(representative.getPnu()), representative.getJibunAddress(),
            representative.getRoadAddress(), KoreanTmCoordinateConverter
                .fromEpsg5174(representative.getOriginalX(), representative.getOriginalY())
                .orElse(null), List.of(), List.of());
    }

    private List<Unit> toUnits(String pnu, List<LicensedBusinessRecordEntity> records) {
        return unitGroups(pnu, records).stream()
            .map(this::toUnit)
            .toList();
    }

    private Map<Boolean, List<LicensedBusinessRecordEntity>> partitionByStorefront(
        List<LicensedBusinessRecordEntity> records
    ) {
        Map<String, List<LicensedBusinessRecordEntity>> byBusinessName = records.stream()
            .collect(Collectors.groupingBy(LicensedBusinessRecordEntity::getBusinessName, LinkedHashMap::new, Collectors.toList()));

        List<LicensedBusinessRecordEntity> storefront = new ArrayList<>();
        List<LicensedBusinessRecordEntity> noStorefront = new ArrayList<>();
        for (List<LicensedBusinessRecordEntity> group : byBusinessName.values()) {
            (hasPhysicalSignal(group) ? storefront : noStorefront).addAll(group);
        }

        Map<Boolean, List<LicensedBusinessRecordEntity>> result = new LinkedHashMap<>();
        result.put(true, storefront);
        result.put(false, noStorefront);
        return result;
    }

    private boolean hasPhysicalSignal(List<LicensedBusinessRecordEntity> businessRecords) {
        return businessRecords.stream().anyMatch(r ->
            !NoStorefrontSubCategories.isNoStorefront(r.getCategory(), r.getSubCategory())
                || r.getParsedFloor() != null
                || r.getParsedUnitNo() != null
        );
    }

    private Unit toUnit(UnitGroup group) {
        List<Tenancy> tenancies = mergedTenancies(group.records());
        LicensedBusinessRecordEntity representative = group.records().get(0);
        return new Unit(group.unitId(), unitLabel(representative), LocationSource.LICENSE, tenancies,
            representative.getParsedFloor(), representative.getParsedUnitNo(), representative.getParseConfidence());
    }

    private List<Tenancy> mergedTenancies(List<LicensedBusinessRecordEntity> records) {
        Map<String, List<LicensedBusinessRecordEntity>> byBusinessName = records.stream()
            .collect(Collectors.groupingBy(LicensedBusinessRecordEntity::getBusinessName, LinkedHashMap::new, Collectors.toList()));

        return byBusinessName.values().stream()
            .flatMap(sameNameRecords -> mergeByGap(sameNameRecords).stream())
            .map(this::toTenancy)
            .sorted(Comparator.comparing(t -> t.period().licensedAt()))
            .toList();
    }

    private List<List<LicensedBusinessRecordEntity>> mergeByGap(List<LicensedBusinessRecordEntity> records) {
        List<LicensedBusinessRecordEntity> sorted = records.stream()
            .sorted(Comparator.comparing(LicensedBusinessRecordEntity::getLicensedAt))
            .toList();

        List<List<LicensedBusinessRecordEntity>> groups = new ArrayList<>();
        List<LicensedBusinessRecordEntity> current = new ArrayList<>();
        LocalDate currentEnd = null;
        boolean currentOpen = false;

        for (LicensedBusinessRecordEntity record : sorted) {
            boolean withinGap = current.isEmpty()
                || currentOpen
                || !record.getLicensedAt().isAfter(currentEnd.plusDays(SAME_BUSINESS_MERGE_GAP_DAYS));

            if (!withinGap) {
                groups.add(current);
                current = new ArrayList<>();
                currentOpen = false;
                currentEnd = null;
            }
            current.add(record);
            if (record.getClosedAt() == null) {
                currentOpen = true;
                currentEnd = null;
            } else if (!currentOpen && (currentEnd == null || record.getClosedAt().isAfter(currentEnd))) {
                currentEnd = record.getClosedAt();
            }
        }
        if (!current.isEmpty()) groups.add(current);
        return groups;
    }

    private Tenancy toTenancy(List<LicensedBusinessRecordEntity> group) {
        LicensedBusinessRecordEntity representative = representativeOf(group);
        LocalDate licensedAt = group.stream()
            .map(LicensedBusinessRecordEntity::getLicensedAt)
            .min(LocalDate::compareTo)
            .orElseThrow();
        boolean anyOpen = group.stream().anyMatch(r -> r.getClosedAt() == null);
        LocalDate closedAt = anyOpen ? null : group.stream()
            .map(LicensedBusinessRecordEntity::getClosedAt)
            .max(LocalDate::compareTo)
            .orElseThrow();

        return new Tenancy(
            representative.getId(),
            representative.getBusinessName(),
            representative.getCategory(),
            representative.getSubCategory(),
            null,
            new TenancyPeriod(licensedAt, closedAt),
            representative.getBusinessStatus(),
            "license_only"
        );
    }

    private LicensedBusinessRecordEntity representativeOf(List<LicensedBusinessRecordEntity> group) {
        return group.stream()
            .filter(r -> r.getClosedAt() == null)
            .max(Comparator.comparing(LicensedBusinessRecordEntity::getLicensedAt))
            .orElseGet(() -> group.stream()
                .max(Comparator.comparing(LicensedBusinessRecordEntity::getClosedAt))
                .orElseThrow());
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
        String unitNo = record.getParsedUnitNo();
        if (unitNo != null) {
            // ponytail: 호실번호가 있으면 층 표기 생략 차이는 무시(호실번호가 층을 함의). 다른 건물에서
            // 같은 호실번호가 우연히 겹치면 오탐 가능 — 실사례 발견 시 buildingName 비중 높여 보정
            return "UNIT::" + unitNo + "::" + record.getParsedBuildingName();
        }
        return "FLOOR::" + record.getParsedFloor() + "::" + record.getParsedBuildingName();
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
