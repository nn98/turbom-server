package com.nextstep.application;

import com.nextstep.domain.businesstype.BusinessTypeRegistry;
import com.nextstep.domain.businesstype.LocationCertainty;
import com.nextstep.infra.persistence.LicensedBusinessRecordEntity;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class SitePartitioner {

    private final BusinessTypeRegistry registry;

    public SitePartitioner(BusinessTypeRegistry registry) {
        this.registry = registry;
    }

    public record Partition(List<LicensedBusinessRecordEntity> storefront,
                             List<LicensedBusinessRecordEntity> noPhysicalStore) {
    }

    public Partition partition(List<LicensedBusinessRecordEntity> records) {
        Map<String, List<LicensedBusinessRecordEntity>> byBusinessName = records.stream()
            .collect(Collectors.groupingBy(LicensedBusinessRecordEntity::getBusinessName,
                LinkedHashMap::new, Collectors.toList()));

        List<LicensedBusinessRecordEntity> storefront = new ArrayList<>();
        List<LicensedBusinessRecordEntity> noPhysicalStore = new ArrayList<>();
        for (List<LicensedBusinessRecordEntity> group : byBusinessName.values()) {
            (hasPhysicalSignal(group) ? storefront : noPhysicalStore).addAll(group);
        }
        return new Partition(storefront, noPhysicalStore);
    }

    private boolean hasPhysicalSignal(List<LicensedBusinessRecordEntity> businessRecords) {
        return businessRecords.stream().anyMatch(r ->
            registry.lookup(r.getCategory(), r.getSubCategory()).locationCertainty() != LocationCertainty.NO_PHYSICAL_STORE
                || r.getParsedFloor() != null
                || r.getParsedUnitNo() != null);
    }
}
