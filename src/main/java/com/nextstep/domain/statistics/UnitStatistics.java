package com.nextstep.domain.statistics;

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
        long closedCount = tenancies.stream().filter(Tenancy::isClosed).count();
        // 종료일자를 모르는 폐업 이력(취소/말소/휴업 등)은 closedCount엔 잡히지만
        // survivalMonths가 null이라 평균/최장/최단 계산에서는 제외한다.
        List<Integer> knownMonths = tenancies.stream()
            .filter(Tenancy::isClosed)
            .map(Tenancy::survivalMonths)
            .filter(java.util.Objects::nonNull)
            .toList();

        Integer average = knownMonths.isEmpty() ? null
            : (int) Math.round(knownMonths.stream().mapToInt(Integer::intValue).average().orElseThrow());
        Integer longest = knownMonths.isEmpty() ? null : java.util.Collections.max(knownMonths);
        Integer shortest = knownMonths.isEmpty() ? null : java.util.Collections.min(knownMonths);

        return new UnitStatistics(tenancies.size(), (int) closedCount, average, longest, shortest);
    }
}
