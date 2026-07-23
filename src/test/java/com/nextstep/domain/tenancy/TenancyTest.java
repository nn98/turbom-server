package com.nextstep.domain.tenancy;

import org.junit.jupiter.api.Test;
import java.time.LocalDate;
import static org.assertj.core.api.Assertions.assertThat;

class TenancyTest {

    private Tenancy withStatus(String status, LocalDate licensedAt, LocalDate closedAt) {
        return new Tenancy(1L, "가게", "생활", "통신판매업", null,
            new TenancyPeriod(licensedAt, closedAt), status, "license_only",
            com.nextstep.domain.businesstype.ReliabilitySignal.confirmed());
    }

    @Test
    void 영업중이고_closedAt_없으면_오늘까지로_계산한다() {
        Tenancy tenancy = withStatus("영업/정상", LocalDate.now().minusMonths(5), null);
        assertThat(tenancy.survivalMonths()).isEqualTo(5);
    }

    @Test
    void 폐업이고_closedAt_있으면_정상_계산한다() {
        Tenancy tenancy = withStatus("폐업", LocalDate.of(2020, 1, 1), LocalDate.of(2020, 6, 1));
        assertThat(tenancy.survivalMonths()).isEqualTo(5);
    }

    @Test
    void 취소말소상태인데_closedAt_없으면_survivalMonths는_null이다() {
        Tenancy tenancy = withStatus("취소/말소/만료/정지/중지", LocalDate.of(2017, 5, 15), null);
        assertThat(tenancy.survivalMonths()).isNull();
    }

    @Test
    void 제외삭제전출상태인데_closedAt_없으면_survivalMonths는_null이다() {
        Tenancy tenancy = withStatus("제외/삭제/전출", LocalDate.of(2017, 5, 15), null);
        assertThat(tenancy.survivalMonths()).isNull();
    }

    @Test
    void 휴업상태인데_closedAt_없으면_survivalMonths는_null이다() {
        Tenancy tenancy = withStatus("휴업", LocalDate.of(2017, 5, 15), null);
        assertThat(tenancy.survivalMonths()).isNull();
    }

    @Test
    void 폐업인데_closedAt_없으면_survivalMonths는_null이다() {
        Tenancy tenancy = withStatus("폐업", LocalDate.of(2017, 5, 15), null);
        assertThat(tenancy.survivalMonths()).isNull();
    }
}
