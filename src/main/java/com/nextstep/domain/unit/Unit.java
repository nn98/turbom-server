package com.nextstep.domain.unit;

import com.nextstep.domain.statistics.UnitStatistics;
import com.nextstep.domain.tenancy.BusinessStatus;
import com.nextstep.domain.tenancy.Tenancy;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

public record Unit(String unitId, String label, LocationSource locationSource, List<Tenancy> tenancies) {
    public UnitStatistics statistics() {
        return UnitStatistics.from(tenancies);
    }

    public Optional<Tenancy> currentTenancy() {
        return tenancies.stream()
            .filter(t -> t.status() == BusinessStatus.ACTIVE)
            .max(Comparator.comparing(t -> t.period().licensedAt()));
    }
}
