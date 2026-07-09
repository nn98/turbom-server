package com.nextstep.domain.market;

import java.time.LocalDate;

public record MarketInfo(boolean isPlaceholder, Integer sameCategoryNearbyCount, LocalDate asOf) {

    public static final double LEASE_AREA_SQM = 42.6;
    public static final long DEPOSIT_KRW = 50_000_000L;
    public static final long MONTHLY_RENT_KRW = 2_800_000L;
    public static final long KEY_MONEY_KRW = 0L;
    public static final int DAILY_FLOATING_POPULATION = 21_400;
    public static final double VACANCY_RATE_PERCENT = 6.2;

    public static MarketInfo unavailable() {
        return new MarketInfo(true, null, LocalDate.now());
    }

    public static MarketInfo of(int sameCategoryNearbyCount) {
        return new MarketInfo(true, sameCategoryNearbyCount, LocalDate.now());
    }
}
