package com.nextstep.application;

import com.nextstep.domain.businesstype.BusinessType;
import com.nextstep.domain.businesstype.BusinessTypeKey;
import com.nextstep.domain.businesstype.BusinessTypeRegistry;
import com.nextstep.domain.tenancy.Tenancy;
import com.nextstep.domain.unit.RelatedLicenseGroup;
import com.nextstep.domain.unit.RelatedLicenseGroups;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public class RelatedLicenseLinker {

    private final BusinessTypeRegistry registry;

    public RelatedLicenseLinker(BusinessTypeRegistry registry) {
        this.registry = registry;
    }

    public RelatedLicenseGroups link(List<Tenancy> tenancies) {
        Set<BusinessTypeKey> keysPresent = tenancies.stream()
            .map(this::keyOf)
            .collect(Collectors.toCollection(LinkedHashSet::new));

        List<RelatedLicenseGroup> groups = new ArrayList<>();
        Set<BusinessTypeKey> consumed = new LinkedHashSet<>();

        for (BusinessTypeKey key : keysPresent) {
            if (consumed.contains(key)) continue;
            BusinessType businessType = registry.lookup(key);
            Set<BusinessTypeKey> partners = businessType.relatedTypeKeys().values().stream()
                .filter(keysPresent::contains)
                .collect(Collectors.toCollection(LinkedHashSet::new));
            if (partners.isEmpty()) continue;

            Set<BusinessTypeKey> groupKeys = new LinkedHashSet<>(partners);
            groupKeys.add(key);
            List<Tenancy> groupTenancies = tenancies.stream()
                .filter(t -> groupKeys.contains(keyOf(t)))
                .toList();
            groups.add(new RelatedLicenseGroup(List.copyOf(groupKeys), groupTenancies));
            consumed.addAll(groupKeys);
        }

        return new RelatedLicenseGroups(groups);
    }

    private BusinessTypeKey keyOf(Tenancy tenancy) {
        return new BusinessTypeKey(tenancy.category(), tenancy.subCategory());
    }
}
