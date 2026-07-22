package com.nextstep.domain.site;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class AddressQueryTest {

    @Test
    void 토큰_순서가_바뀌어도_전부_매칭되면_true() {
        AddressQuery query = AddressQuery.of("신흥동 수정구");

        assertThat(query.matchesAll("경기도 성남시 수정구 신흥동 4124번지", null)).isTrue();
    }

    @Test
    void 토큰_하나가_빠지면_false() {
        AddressQuery query = AddressQuery.of("신흥동 중원구");

        assertThat(query.matchesAll("경기도 성남시 수정구 신흥동 4124번지", null)).isFalse();
    }

    @Test
    void jibunAddress에_없어도_roadAddress에_있으면_매칭() {
        AddressQuery query = AddressQuery.of("판교대장로");

        assertThat(query.matchesAll("경기도 성남시 분당구 대장동 202-4",
            "경기도 성남시 분당구 판교대장로6길 10")).isTrue();
    }

    @Test
    void 숫자_토큰은_다른_숫자에_붙어있으면_매칭_안함() {
        AddressQuery query = AddressQuery.of("534");

        assertThat(query.matchesAll("경기도 성남시 수정구 어딘가동 1534", null)).isFalse();
        assertThat(query.matchesAll("경기도 성남시 수정구 어딘가동 5340", null)).isFalse();
    }

    @Test
    void 숫자_토큰은_경계가_있으면_매칭() {
        AddressQuery query = AddressQuery.of("534");

        assertThat(query.matchesAll("경기도 성남시 수정구 어딘가동 534-1", null)).isTrue();
        assertThat(query.matchesAll("경기도 성남시 수정구 어딘가동 534번지 3층", null)).isTrue();
    }

    @Test
    void 문자_토큰은_대소문자_무관() {
        AddressQuery query = AddressQuery.of("gs25");

        assertThat(query.matchesAll("경기도 성남시 수정구 GS25 위든타워점", null)).isTrue();
    }

    @Test
    void anchorToken은_가장_긴_토큰() {
        AddressQuery query = AddressQuery.of("성남시 수정구청사거리");

        assertThat(query.anchorToken()).isEqualTo("수정구청사거리");
    }

    @Test
    void 단일_토큰이면_anchorToken이_그_토큰() {
        AddressQuery query = AddressQuery.of("  성남  ");

        assertThat(query.anchorToken()).isEqualTo("성남");
    }
}
