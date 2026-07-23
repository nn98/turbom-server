package com.nextstep.domain.businesstype;

import org.junit.jupiter.api.Test;
import java.util.Set;
import static org.assertj.core.api.Assertions.assertThat;

class RelatedTypeKeysTest {

    @Test
    void 등록된_키와는_페어다() {
        BusinessTypeKey 위탁급식영업 = new BusinessTypeKey("식품", "위탁급식영업");
        RelatedTypeKeys keys = new RelatedTypeKeys(Set.of(위탁급식영업));

        assertThat(keys.pairsWith(위탁급식영업)).isTrue();
    }

    @Test
    void 등록안된_키와는_페어가_아니다() {
        RelatedTypeKeys keys = new RelatedTypeKeys(Set.of(new BusinessTypeKey("식품", "위탁급식영업")));

        assertThat(keys.pairsWith(new BusinessTypeKey("기타", "담배소매업"))).isFalse();
    }

    @Test
    void none은_아무것과도_페어가_아니다() {
        assertThat(RelatedTypeKeys.none().pairsWith(new BusinessTypeKey("식품", "집단급식소"))).isFalse();
    }
}
