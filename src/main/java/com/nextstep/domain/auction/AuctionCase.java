package com.nextstep.domain.auction;

import java.math.BigDecimal;
import java.util.List;

public record AuctionCase(
    String caseNumber,
    int itemNumber,
    String court,
    String divisionName,
    String propertyType,
    String jibunAddress,
    BigDecimal appraisalValueKrw,
    BigDecimal minimumSalePriceKrw,
    BigDecimal bidDepositKrw,
    String biddingMethod,
    String saleDate,
    String filedDate,
    String auctionStartDate,
    String claimDeadline,
    BigDecimal claimAmountKrw,
    String appraisalSummary,
    List<AuctionScheduleEntry> scheduleHistory
) {
}
