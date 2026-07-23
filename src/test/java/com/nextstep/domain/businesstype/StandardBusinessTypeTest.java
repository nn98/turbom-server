package com.nextstep.domain.businesstype;

import org.junit.jupiter.api.Test;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import static org.assertj.core.api.Assertions.assertThat;

class StandardBusinessTypeTest {

    @Test
    void peerBasedReliability_꺼져있으면_항상_CONFIRMED() {
        StandardBusinessType type = new StandardBusinessType(
            LocationCertainty.LOCATED, RelatedTypeKeys.none(), false);
        LocationContext context = new LocationContext(List.of(
            new LocationContext.SiblingRecord("다른가게", LocalDate.of(2099, 1, 1))));

        ReliabilitySignal signal = type.reliabilitySignal("가게", LocalDate.of(2000, 1, 1), context);

        assertThat(signal.level()).isEqualTo(ReliabilitySignal.Level.CONFIRMED);
    }

    @Test
    void peerBasedReliability_켜져있고_더_늦은_타상호_있으면_NEEDS_VERIFICATION() {
        StandardBusinessType type = new StandardBusinessType(
            LocationCertainty.LOCATED, RelatedTypeKeys.none(), true);
        LocationContext context = new LocationContext(List.of(
            new LocationContext.SiblingRecord("씨유 성남대왕판교로점", LocalDate.of(1999, 1, 15)),
            new LocationContext.SiblingRecord("판교스터디카페", LocalDate.of(2020, 3, 1))));

        ReliabilitySignal signal = type.reliabilitySignal(
            "씨유 성남대왕판교로점", LocalDate.of(1999, 1, 15), context);

        assertThat(signal.level()).isEqualTo(ReliabilitySignal.Level.NEEDS_VERIFICATION);
        assertThat(signal.reason()).isNotBlank();
    }

    @Test
    void peerBasedReliability_켜져있어도_타상호가_없으면_CONFIRMED() {
        StandardBusinessType type = new StandardBusinessType(
            LocationCertainty.LOCATED, RelatedTypeKeys.none(), true);
        LocationContext context = new LocationContext(List.of(
            new LocationContext.SiblingRecord("씨유 성남대왕판교로점", LocalDate.of(1999, 1, 15))));

        ReliabilitySignal signal = type.reliabilitySignal(
            "씨유 성남대왕판교로점", LocalDate.of(1999, 1, 15), context);

        assertThat(signal.level()).isEqualTo(ReliabilitySignal.Level.CONFIRMED);
    }

    @Test
    void locationCertainty와_relatedTypeKeys는_생성자값_그대로_반환() {
        RelatedTypeKeys pair = new RelatedTypeKeys(Set.of(new BusinessTypeKey("식품", "위탁급식영업")));
        StandardBusinessType type = new StandardBusinessType(LocationCertainty.LOCATED, pair, false);

        assertThat(type.locationCertainty()).isEqualTo(LocationCertainty.LOCATED);
        assertThat(type.relatedTypeKeys()).isEqualTo(pair);
    }
}
