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

    public Integer survivalMonths() {
        if (isClosed() && period.closedAt() == null) {
            // status가 "종료됐다"고 말하는데 원본에 종료일자가 없는 케이스(취소/말소/휴업 등) —
            // licensedAt~오늘로 계산하면 "아직 영업중"처럼 보이는 왜곡이 생겨 null로 정직하게 표현.
            return null;
        }
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
