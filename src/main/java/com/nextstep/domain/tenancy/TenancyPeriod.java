package com.nextstep.domain.tenancy;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

public record TenancyPeriod(LocalDate licensedAt, LocalDate closedAt) {
    public TenancyPeriod {
        if (licensedAt == null) {
            throw new IllegalArgumentException("licensedAt은 필수입니다.");
        }
        // ponytail: 역순 날짜는 데이터 오류 — throw 대신 null로 보정해 조회 중단 방지
        if (closedAt != null && closedAt.isBefore(licensedAt)) {
            closedAt = null;
        }
    }

    public int survivalMonths() {
        LocalDate end = closedAt != null ? closedAt : LocalDate.now();
        return (int) ChronoUnit.MONTHS.between(licensedAt, end);
    }
}
