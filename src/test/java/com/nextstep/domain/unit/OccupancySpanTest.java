package com.nextstep.domain.unit;

import org.junit.jupiter.api.Test;
import java.time.LocalDate;
import static org.assertj.core.api.Assertions.assertThat;

class OccupancySpanTest {

    @Test
    void 기간이_겹치면_true() {
        OccupancySpan a = new OccupancySpan("A", LocalDate.of(2020, 1, 1), LocalDate.of(2021, 1, 1));
        OccupancySpan b = new OccupancySpan("B", LocalDate.of(2020, 6, 1), LocalDate.of(2022, 1, 1));

        assertThat(a.overlaps(b)).isTrue();
        assertThat(b.overlaps(a)).isTrue();
    }

    @Test
    void 기간이_전혀_안겹치면_false() {
        OccupancySpan a = new OccupancySpan("A", LocalDate.of(2020, 1, 1), LocalDate.of(2020, 6, 1));
        OccupancySpan b = new OccupancySpan("B", LocalDate.of(2021, 1, 1), LocalDate.of(2021, 6, 1));

        assertThat(a.overlaps(b)).isFalse();
    }

    @Test
    void 당일_인수인계는_겹침이_아니다() {
        OccupancySpan a = new OccupancySpan("A", LocalDate.of(2019, 1, 1), LocalDate.of(2020, 1, 1));
        OccupancySpan b = new OccupancySpan("B", LocalDate.of(2020, 1, 1), null);

        assertThat(a.overlaps(b)).isFalse();
        assertThat(b.overlaps(a)).isFalse();
    }

    @Test
    void 둘_다_계속_영업중이면_겹침() {
        OccupancySpan a = new OccupancySpan("A", LocalDate.of(2020, 1, 1), null);
        OccupancySpan b = new OccupancySpan("B", LocalDate.of(2020, 6, 1), null);

        assertThat(a.overlaps(b)).isTrue();
    }

    @Test
    void 한쪽만_계속_영업중이고_다른쪽_기간이_그_안에_있으면_겹침() {
        OccupancySpan a = new OccupancySpan("A", LocalDate.of(2020, 1, 1), null);
        OccupancySpan b = new OccupancySpan("B", LocalDate.of(2021, 1, 1), LocalDate.of(2021, 6, 1));

        assertThat(a.overlaps(b)).isTrue();
    }
}
