package com.nextstep.application.auction;

import com.nextstep.domain.auction.AuctionCase;
import com.nextstep.domain.auction.AuctionScheduleEntry;
import com.nextstep.infra.persistence.AuctionCaseEntity;
import com.nextstep.infra.persistence.AuctionCaseRepository;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
class AuctionCollectionServiceTest {

    @Autowired
    AuctionCaseRepository repository;

    @Test
    void 수집된_사건을_스케줄내역과_함께_저장한다() {
        AuctionCase collected = new AuctionCase(
            "2025타경51795", 1, "수원지방법원 성남지원", "경매4계",
            "상가,오피스텔,근린시설", "경기도 성남시 수정구 고등동 616",
            new BigDecimal("783000000"), new BigDecimal("131599000"), new BigDecimal("13159900"),
            "기일입찰", "2026.08.03", "2025.03.14", "2025.03.15", "2025.05.19",
            new BigDecimal("461495221"), "감정평가요항표 요약 ...",
            List.of(new AuctionScheduleEntry("2026.02.09", "10:00", "매각기일",
                "제3별관 1층 제5호법정", new BigDecimal("783000000"), "유찰"))
        );
        AuctionCollectionService service = new AuctionCollectionService(() -> List.of(collected), repository);

        service.collectAndPersist();

        List<AuctionCaseEntity> saved = repository.findAll();
        assertThat(saved).hasSize(1);
        assertThat(saved.get(0).getCaseNumber()).isEqualTo("2025타경51795");
        assertThat(saved.get(0).getScheduleHistory()).hasSize(1);
        assertThat(saved.get(0).getScheduleHistory().get(0).getResult()).isEqualTo("유찰");
    }
}
