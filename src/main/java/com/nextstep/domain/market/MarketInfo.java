package com.nextstep.domain.market;

import java.time.LocalDate;
import java.util.List;

public record MarketInfo(
    boolean isPlaceholder,
    Integer sameCategoryNearbyCount,
    Integer totalStoreCount,
    List<CategoryCount> categoryBreakdown,
    LocalDate asOf
) {

    public static final double LEASE_AREA_SQM = 42.6;
    public static final long DEPOSIT_KRW = 50_000_000L;
    public static final long MONTHLY_RENT_KRW = 2_800_000L;
    public static final long KEY_MONEY_KRW = 0L;
    public static final int DAILY_FLOATING_POPULATION = 21_400;
    public static final double VACANCY_RATE_PERCENT = 6.2;

    /** 반경 내 상가 대분류 하나의 점포 수·비중. 프론트가 업종을 골라 점포수·경쟁률을 보여줄 때 쓴다. */
    public record CategoryCount(String code, String name, int count, double ratio) {
    }

    public static MarketInfo unavailable() {
        return new MarketInfo(true, null, null, List.of(), LocalDate.now());
    }

    public static MarketInfo of(int sameCategoryNearbyCount, Integer totalStoreCount, List<CategoryCount> categoryBreakdown) {
        return new MarketInfo(true, sameCategoryNearbyCount, totalStoreCount, categoryBreakdown, LocalDate.now());
    }
}
