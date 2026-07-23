package com.nextstep.domain.businesstype;

import org.junit.jupiter.api.Test;
import java.time.LocalDate;
import java.util.List;
import static org.assertj.core.api.Assertions.assertThat;

class LocationContextTest {

    @Test
    void 다른_상호가_더_늦게_시작했으면_true() {
        LocationContext context = new LocationContext(List.of(
            new LocationContext.SiblingRecord("씨유 성남대왕판교로점", LocalDate.of(1999, 1, 15)),
            new LocationContext.SiblingRecord("판교스터디카페", LocalDate.of(2020, 3, 1))
        ));

        assertThat(context.hasLaterOtherBusinessName("씨유 성남대왕판교로점", LocalDate.of(1999, 1, 15)))
            .isTrue();
    }

    @Test
    void 같은_상호만_있으면_false() {
        LocationContext context = new LocationContext(List.of(
            new LocationContext.SiblingRecord("씨유 성남대왕판교로점", LocalDate.of(1999, 1, 15))
        ));

        assertThat(context.hasLaterOtherBusinessName("씨유 성남대왕판교로점", LocalDate.of(1999, 1, 15)))
            .isFalse();
    }

    @Test
    void 다른_상호가_있어도_더_이르면_false() {
        LocationContext context = new LocationContext(List.of(
            new LocationContext.SiblingRecord("씨유 성남대왕판교로점", LocalDate.of(2010, 1, 1)),
            new LocationContext.SiblingRecord("옛날가게", LocalDate.of(1990, 1, 1))
        ));

        assertThat(context.hasLaterOtherBusinessName("씨유 성남대왕판교로점", LocalDate.of(2010, 1, 1)))
            .isFalse();
    }
}
