package com.nextstep.domain.site;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class LocationIdentityTest {

    @Test
    void 호실번호가_있으면_UNIT_키() {
        String key = LocationIdentity.key(AddressDetailParser.CONFIDENCE_HIGH, "우성트램타워", null, "801");
        assertThat(key).isEqualTo("UNIT::801::우성트램타워");
    }

    @Test
    void 층만_있으면_FLOOR_키() {
        String key = LocationIdentity.key(AddressDetailParser.CONFIDENCE_HIGH, "우성트램타워", "8", null);
        assertThat(key).isEqualTo("FLOOR::8::우성트램타워");
    }

    @Test
    void 건물명만_있으면_정규화된_건물명() {
        String key = LocationIdentity.key(AddressDetailParser.CONFIDENCE_HIGH, "우성트램타워", null, null);
        assertThat(key).isEqualTo("우성트램타워");
    }

    @Test
    void LOW_신뢰도면_원본_문자열_정규화() {
        String key = LocationIdentity.key(AddressDetailParser.CONFIDENCE_LOW, "  B동  8층  801~804호  ", null, null);
        assertThat(key).isEqualTo("B동 8층 801~804호");
    }

    @Test
    void LOW_신뢰도인데_건물명도_없으면_위치미특정() {
        String key = LocationIdentity.key(AddressDetailParser.CONFIDENCE_LOW, null, null, null);
        assertThat(LocationIdentity.isUnlocated(key)).isTrue();
    }

    @Test
    void HIGH_신뢰도인데_전부_null이면_위치미특정() {
        // 담배소매업처럼 상세주소 자체가 원본에 없는 케이스(AddressDetailParser.Result.none())
        String key = LocationIdentity.key(AddressDetailParser.CONFIDENCE_HIGH, null, null, null);
        assertThat(LocationIdentity.isUnlocated(key)).isTrue();
    }

    @Test
    void 위치가_특정되면_isUnlocated는_false() {
        String key = LocationIdentity.key(AddressDetailParser.CONFIDENCE_HIGH, "우성트램타워", null, "801");
        assertThat(LocationIdentity.isUnlocated(key)).isFalse();
    }
}
