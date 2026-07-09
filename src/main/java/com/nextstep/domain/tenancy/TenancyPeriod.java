package com.nextstep.domain.tenancy;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

public record TenancyPeriod(LocalDate licensedAt, LocalDate closedAt) {
    public TenancyPeriod {
        if (licensedAt == null) {
            throw new IllegalArgumentException("licensedAt은 필수입니다.");
        }
        if (closedAt != null && closedAt.isBefore(licensedAt)) {
            throw new IllegalArgumentException("closedAt은 licensedAt보다 빠를 수 없습니다.");
        }
    }

    public int survivalMonths() {
        LocalDate end = closedAt != null ? closedAt : LocalDate.now();
        return (int) ChronoUnit.MONTHS.between(licensedAt, end);
    }
}
