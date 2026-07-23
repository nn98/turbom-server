package com.nextstep.domain.unit;

import com.nextstep.domain.tenancy.Tenancy;
import java.util.List;
import java.util.Optional;

public record RelatedLicenseGroups(List<RelatedLicenseGroup> groups) {

    public static RelatedLicenseGroups none() {
        return new RelatedLicenseGroups(List.of());
    }

    public Optional<RelatedLicenseGroup> groupContaining(Tenancy tenancy) {
        return groups.stream().filter(g -> g.tenancies().contains(tenancy)).findFirst();
    }
}
