package com.nextstep.application;

import com.nextstep.domain.site.Pnu;
import com.nextstep.domain.site.Site;
import com.nextstep.domain.tenancy.BusinessStatus;
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

        List<UnitGroup> groups = unitGroups(unitReference.get().pnu(), records);
        int groupIndex = unitReference.get().index() - 1;
        if (groupIndex < 0 || groupIndex >= groups.size()) return Optional.empty();

        UnitGroup group = groups.get(groupIndex);
        return Optional.of(new UnitWithSite(toUnit(group), toSiteWithoutUnits(group.records())));
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
        LicensedBusinessRecordEntity representative = records.get(0);
        return new Site(new Pnu(representative.getPnu()), representative.getJibunAddress(),
            representative.getRoadAddress(), KoreanTmCoordinateConverter
                .fromEpsg5174(representative.getOriginalX(), representative.getOriginalY())
                .orElse(null), toUnits(representative.getPnu(), records));
    }

    private Site toSiteWithoutUnits(List<LicensedBusinessRecordEntity> records) {
        LicensedBusinessRecordEntity representative = records.get(0);
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
        return new Unit(group.unitId(), unitLabel(group.records().get(0).getRoadAddress()), LocationSource.LICENSE, tenancies);
    }

    private Tenancy toTenancy(LicensedBusinessRecordEntity entity) {
        return new Tenancy(
            entity.getId(),
            entity.getBusinessName(),
            entity.getCategory(),
            entity.getSubCategory(),
            null,
            new TenancyPeriod(entity.getLicensedAt(), entity.getClosedAt()),
            BusinessStatus.fromDb(entity.getBusinessStatus()),
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
        return normalize(record.getJibunAddress());
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

    private String unitLabel(String roadAddress) {
        if (roadAddress == null) return "단일 점포";

        List<String> patterns = List.of(
            "([A-Z가-힣]+동\\s*\\d+호)",
            "(\\d+층\\s*\\d+호)",
            "(\\d{4}(?:,\\d{4})*호)",
            "(\\d+호)",
            "(\\d+층)"
        );
        String target = roadAddress.split("\\(")[0].trim();
        for (String pattern : patterns) {
            java.util.regex.Matcher matcher = java.util.regex.Pattern.compile(pattern).matcher(target);
            if (matcher.find()) return matcher.group(1).replaceAll("\\s+", " ");
        }
        return "단일 점포";
    }

    private record UnitGroup(String unitId, List<LicensedBusinessRecordEntity> records) {
    }

    private record UnitReference(String pnu, int index) {
    }
}
