package com.nextstep.infra.auction;

import com.nextstep.domain.auction.AuctionCase;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CourtAuctionCollectorManualCheck {

    @Test
    void 성남시_수정구_상업용_경매사건을_실제로_수집한다() {
        CourtAuctionCollector collector = new CourtAuctionCollector();

        List<AuctionCase> cases = collector.collectSeongnamSujeongGu();

        assertThat(cases).isNotEmpty();
        cases.forEach(c -> System.out.println(c.caseNumber() + " " + c.itemNumber() + " " + c.jibunAddress()));
    }
}
