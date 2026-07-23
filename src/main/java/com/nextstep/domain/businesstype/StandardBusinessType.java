package com.nextstep.domain.businesstype;

import java.time.LocalDate;

public record StandardBusinessType(
    LocationCertainty locationCertainty,
    RelatedTypeKeys relatedTypeKeys,
    boolean peerBasedReliability
) implements BusinessType {

    @Override
    public ReliabilitySignal reliabilitySignal(String businessName, LocalDate licensedAt, LocationContext context) {
        if (!peerBasedReliability) {
            return ReliabilitySignal.confirmed();
        }
        if (context.hasLaterOtherBusinessName(businessName, licensedAt)) {
            return ReliabilitySignal.needsVerification(
                "같은 자리에 이 인허가보다 더 늦게 시작한 다른 상호가 있음 — 폐업신고 누락 또는 "
                    + "인허가 승계로 licensedAt이 실제 개업일보다 이를 수 있음(turbom-spec 의사결정-기록.md §9)");
        }
        return ReliabilitySignal.confirmed();
    }
}
