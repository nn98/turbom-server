package com.nextstep.domain.auction;

import java.math.BigDecimal;

public record AuctionScheduleEntry(
    String scheduleDate,
    String scheduleTime,
    String scheduleType,
    String location,
    BigDecimal minimumPriceKrw,
    String result
) {
}
