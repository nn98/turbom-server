package com.nextstep.application.auction;

import com.nextstep.domain.auction.AuctionCase;
import com.nextstep.domain.auction.AuctionScheduleEntry;
import com.nextstep.infra.persistence.AuctionCaseEntity;
import com.nextstep.infra.persistence.AuctionCaseRepository;
import com.nextstep.infra.persistence.AuctionScheduleEntryEntity;
import java.util.List;
import java.util.function.Supplier;

/**
 * 개발용 스파이크 전용. 어떤 컨트롤러에도 연결하지 말 것 — 개발자가 직접 호출해야만 실행된다.
 */
public class AuctionCollectionService {

    private final Supplier<List<AuctionCase>> collector;
    private final AuctionCaseRepository repository;

    public AuctionCollectionService(Supplier<List<AuctionCase>> collector, AuctionCaseRepository repository) {
        this.collector = collector;
        this.repository = repository;
    }

    public void collectAndPersist() {
        for (AuctionCase auctionCase : collector.get()) {
            AuctionCaseEntity entity = new AuctionCaseEntity(
                auctionCase.caseNumber(), auctionCase.itemNumber(), auctionCase.court(),
                auctionCase.divisionName(), auctionCase.propertyType(), auctionCase.jibunAddress(),
                auctionCase.appraisalValueKrw(), auctionCase.minimumSalePriceKrw(), auctionCase.bidDepositKrw(),
                auctionCase.biddingMethod(), auctionCase.saleDate(), auctionCase.filedDate(),
                auctionCase.auctionStartDate(), auctionCase.claimDeadline(), auctionCase.claimAmountKrw(),
                auctionCase.appraisalSummary()
            );
            for (AuctionScheduleEntry entry : auctionCase.scheduleHistory()) {
                entity.addScheduleEntry(new AuctionScheduleEntryEntity(
                    entity, entry.scheduleDate(), entry.scheduleTime(), entry.scheduleType(),
                    entry.location(), entry.minimumPriceKrw(), entry.result()
                ));
            }
            repository.save(entity);
        }
    }
}
