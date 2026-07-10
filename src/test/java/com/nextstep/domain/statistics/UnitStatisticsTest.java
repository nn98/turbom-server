package com.nextstep.domain.statistics;

import com.nextstep.domain.tenancy.Tenancy;
import com.nextstep.domain.tenancy.TenancyPeriod;
import org.junit.jupiter.api.Test;
import java.time.LocalDate;
import java.util.List;
import static org.assertj.core.api.Assertions.assertThat;

class UnitStatisticsTest {

    private Tenancy closed(LocalDate start, LocalDate end) {
        return new Tenancy(1L, "가게", "음식", "일반음식점", null,
            new TenancyPeriod(start, end), "폐업", "license_only");
    }

    private Tenancy active(LocalDate start) {
        return new Tenancy(2L, "가게2", "음식", "일반음식점", null,
            new TenancyPeriod(start, null), "영업/정상", "license_only");
    }

    @Test
    void 폐업_이력만으로_평균_최장_최단_생존월을_계산한다() {
        List<Tenancy> tenancies = List.of(
            closed(LocalDate.of(2013, 5, 2), LocalDate.of(2017, 1, 10)),  // 44개월
            closed(LocalDate.of(2018, 1, 1), LocalDate.of(2018, 12, 1)),  // 11개월
            active(LocalDate.of(2023, 1, 15))
        );

        UnitStatistics stats = UnitStatistics.from(tenancies);

        assertThat(stats.totalTenancyCount()).isEqualTo(3);
        assertThat(stats.closedCount()).isEqualTo(2);
        assertThat(stats.longestSurvivalMonths()).isEqualTo(44);
        assertThat(stats.shortestSurvivalMonths()).isEqualTo(11);
        assertThat(stats.averageSurvivalMonths()).isEqualTo(28);
    }

    @Test
    void 폐업_이력이_없으면_평균_최장_최단이_null이다() {
        UnitStatistics stats = UnitStatistics.from(List.of(active(LocalDate.of(2023, 1, 1))));

        assertThat(stats.closedCount()).isZero();
        assertThat(stats.averageSurvivalMonths()).isNull();
        assertThat(stats.longestSurvivalMonths()).isNull();
        assertThat(stats.shortestSurvivalMonths()).isNull();
    }
}
