package com.nextstep.domain.statistics;

import com.nextstep.domain.tenancy.BusinessStatus;
import com.nextstep.domain.tenancy.Tenancy;
import java.util.List;

public record UnitStatistics(
    int totalTenancyCount,
    int closedCount,
    Integer averageSurvivalMonths,
    Integer longestSurvivalMonths,
    Integer shortestSurvivalMonths
) {
    public static UnitStatistics from(List<Tenancy> tenancies) {
        List<Integer> closedMonths = tenancies.stream()
            .filter(t -> t.status() == BusinessStatus.CLOSED)
            .map(Tenancy::survivalMonths)
            .toList();

        Integer average = closedMonths.isEmpty() ? null
            : (int) Math.round(closedMonths.stream().mapToInt(Integer::intValue).average().orElseThrow());
        Integer longest = closedMonths.isEmpty() ? null : java.util.Collections.max(closedMonths);
        Integer shortest = closedMonths.isEmpty() ? null : java.util.Collections.min(closedMonths);

        return new UnitStatistics(tenancies.size(), closedMonths.size(), average, longest, shortest);
    }
}
