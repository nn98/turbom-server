package com.nextstep.domain.tenancy;

import org.junit.jupiter.api.Test;
import java.time.LocalDate;
import static org.assertj.core.api.Assertions.assertThat;

class TenancyPeriodTest {

    @Test
    void 폐업이면_인허가일부터_폐업일까지_개월수를_계산한다() {
        TenancyPeriod period = new TenancyPeriod(LocalDate.of(2013, 5, 2), LocalDate.of(2017, 1, 10));
        assertThat(period.survivalMonths()).isEqualTo(44);
    }

    @Test
    void 영업중이면_폐업일이_null이다() {
        TenancyPeriod period = new TenancyPeriod(LocalDate.of(2023, 1, 15), null);
        assertThat(period.closedAt()).isNull();
    }

    @Test
    void 폐업일이_인허가일보다_빠르면_예외() {
        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class,
            () -> new TenancyPeriod(LocalDate.of(2020, 1, 1), LocalDate.of(2019, 1, 1)));
    }
}
