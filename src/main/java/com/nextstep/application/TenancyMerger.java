package com.nextstep.application;

import com.nextstep.domain.businesstype.BusinessTypeRegistry;
import com.nextstep.domain.businesstype.LocationContext;
import com.nextstep.domain.tenancy.Tenancy;
import com.nextstep.domain.tenancy.TenancyPeriod;
import com.nextstep.infra.persistence.LicensedBusinessRecordEntity;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class TenancyMerger {

    // ponytail: 초기 추정값. 실사례로 오판(과병합/과분리) 나오면 조정
    private static final int SAME_BUSINESS_MERGE_GAP_DAYS = 90;

    private final BusinessTypeRegistry registry;

    public TenancyMerger(BusinessTypeRegistry registry) {
        this.registry = registry;
    }

    public List<Tenancy> merge(List<LicensedBusinessRecordEntity> allRecordsAtSamePnu,
                                List<LicensedBusinessRecordEntity> group) {
        LocationContext context = toLocationContext(allRecordsAtSamePnu);

        Map<String, List<LicensedBusinessRecordEntity>> byBusinessName = group.stream()
            .collect(Collectors.groupingBy(LicensedBusinessRecordEntity::getBusinessName,
                LinkedHashMap::new, Collectors.toList()));

        return byBusinessName.values().stream()
            .flatMap(sameNameRecords -> mergeByGap(sameNameRecords).stream())
            .map(mergedGroup -> toTenancy(mergedGroup, context))
            .sorted(Comparator.comparing(t -> t.period().licensedAt()))
            .toList();
    }

    private LocationContext toLocationContext(List<LicensedBusinessRecordEntity> records) {
        return new LocationContext(records.stream()
            .map(r -> new LocationContext.SiblingRecord(r.getBusinessName(), r.getLicensedAt()))
            .toList());
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

    private Tenancy toTenancy(List<LicensedBusinessRecordEntity> group, LocationContext context) {
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

        var reliabilitySignal = registry.lookup(representative.getCategory(), representative.getSubCategory())
            .reliabilitySignal(representative.getBusinessName(), licensedAt, context);

        return new Tenancy(
            representative.getId(),
            representative.getBusinessName(),
            representative.getCategory(),
            representative.getSubCategory(),
            null,
            new TenancyPeriod(licensedAt, closedAt),
            representative.getBusinessStatus(),
            "license_only",
            reliabilitySignal
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
}
