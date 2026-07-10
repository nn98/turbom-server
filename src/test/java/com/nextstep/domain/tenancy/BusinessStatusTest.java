package com.nextstep.domain.tenancy;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class BusinessStatusTest {

    @Test
    void 영업정상_원본값은_영업으로_정규화한다() {
        assertThat(BusinessStatus.fromDb("영업/정상")).isEqualTo(BusinessStatus.ACTIVE);
    }

    @Test
    void 폐업_원본값은_폐업으로_정규화한다() {
        assertThat(BusinessStatus.fromDb("폐업")).isEqualTo(BusinessStatus.CLOSED);
    }

    @Test
    void 제외삭제전출_원본값은_폐업으로_정규화한다() {
        assertThat(BusinessStatus.fromDb("제외/삭제/전출")).isEqualTo(BusinessStatus.CLOSED);
        assertThat(BusinessStatus.fromDb("전출")).isEqualTo(BusinessStatus.CLOSED);
    }
}
