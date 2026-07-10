package com.nextstep.domain.tenancy;

public record Tenancy(
    Long id,
    String businessName,
    String category,
    String subCategory,
    String industryDetail,
    TenancyPeriod period,
    String status,
    String enrichmentSource
) {
    private static final String ACTIVE_STATUS = "영업/정상";

    public int survivalMonths() {
        return period.survivalMonths();
    }

    public boolean isActive() {
        return ACTIVE_STATUS.equals(status);
    }

    public boolean isClosed() {
        return !isActive();
    }

    public String displayStatus() {
        return isActive() ? "영업" : status;
    }

    public boolean closedAtEstimated() {
        return false;
    }
}
