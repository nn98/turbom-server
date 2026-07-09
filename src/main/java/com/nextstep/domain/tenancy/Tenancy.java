package com.nextstep.domain.tenancy;

public record Tenancy(
    Long id,
    String businessName,
    String category,
    String subCategory,
    String industryDetail,
    TenancyPeriod period,
    BusinessStatus status,
    String enrichmentSource
) {
    public int survivalMonths() {
        return period.survivalMonths();
    }

    public boolean closedAtEstimated() {
        return false;
    }
}
