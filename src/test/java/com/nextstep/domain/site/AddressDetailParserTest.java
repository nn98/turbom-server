package com.nextstep.domain.site;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AddressDetailParserTest {

    @Test
    void 건물명과_호수를_함께_추출한다() {
        String road = "경기도 성남시 분당구 성남대로 151, 분당엠코헤리츠 115-4호 (구미동)";

        var result = AddressDetailParser.parse(road);

        assertThat(result.buildingName()).isEqualTo("분당엠코헤리츠");
        assertThat(result.unitNo()).isEqualTo("115-4");
        assertThat(result.floor()).isNull();
        assertThat(result.confidence()).isEqualTo(AddressDetailParser.CONFIDENCE_HIGH);
        assertThat(result.method()).isEqualTo(AddressDetailParser.METHOD_REGEX);
    }

    @Test
    void 순수_호수만_있는_경우() {
        var result = AddressDetailParser.parse("경기도 성남시 수정구 위례광장로 21-10, 102호 (창곡동)");

        assertThat(result.unitNo()).isEqualTo("102");
        assertThat(result.buildingName()).isNull();
        assertThat(result.confidence()).isEqualTo(AddressDetailParser.CONFIDENCE_HIGH);
    }

    @Test
    void 층_일부호는_일부호_수식어를_제거하고_층만_남긴다() {
        var result = AddressDetailParser.parse("경기도 성남시 분당구 판교역로146번길 20, 1층 일부호 (백현동)");

        assertThat(result.floor()).isEqualTo("1");
        assertThat(result.unitNo()).isNull();
        assertThat(result.confidence()).isEqualTo(AddressDetailParser.CONFIDENCE_HIGH);
        assertThat(result.method()).isEqualTo(AddressDetailParser.METHOD_REGEX);
    }

    @Test
    void 괄호로_끼어든_일부_수식어가_있어도_층을_잃지_않는다() {
        var result = AddressDetailParser.parse("경기도 성남시 수정구 산성대로581번길 8, 1(일부)층 (양지동)");

        assertThat(result.floor()).isEqualTo("1");
        assertThat(result.confidence()).isEqualTo(AddressDetailParser.CONFIDENCE_HIGH);
    }

    @Test
    void 건물명과_지하층_일부호가_섞인_대형몰_케이스() {
        var result = AddressDetailParser.parse(
            "경기도 성남시 분당구 판교역로146번길 20, 현대백화점 판교점 지하1층 일부호 (백현동)");

        assertThat(result.buildingName()).isEqualTo("현대백화점 판교점");
        assertThat(result.floor()).isEqualTo("B1");
        assertThat(result.confidence()).isEqualTo(AddressDetailParser.CONFIDENCE_HIGH);
    }

    @Test
    void 대문자_B_지하_호수_표기를_정규화한다() {
        var result = AddressDetailParser.parse(
            "경기도 성남시 분당구 동판교로 91, B102호 (백현동, 백현마을4단지)");

        assertThat(result.unitNo()).isEqualTo("B102");
        assertThat(result.confidence()).isEqualTo(AddressDetailParser.CONFIDENCE_HIGH);
    }

    @Test
    void 소문자_b_지하_호수_표기도_정규화한다() {
        var result = AddressDetailParser.parse(
            "경기도 성남시 분당구 판교로319번길 13, 디테라스 b104호 (삼평동)");

        assertThat(result.buildingName()).isEqualTo("디테라스");
        assertThat(result.unitNo()).isEqualTo("B104");
    }

    @Test
    void 건물명만_있는_경우도_고신뢰도로_처리한다() {
        var result = AddressDetailParser.parse("경기도 성남시 분당구 백현로101번길 22, 성남농협 (수내동)");

        assertThat(result.buildingName()).isEqualTo("성남농협");
        assertThat(result.floor()).isNull();
        assertThat(result.unitNo()).isNull();
        assertThat(result.confidence()).isEqualTo(AddressDetailParser.CONFIDENCE_HIGH);
    }

    @Test
    void 상세주소가_없으면_NONE으로_표시한다() {
        var result = AddressDetailParser.parse("경기도 성남시 분당구 느티로 73 (정자동, 현대빌딩)");

        assertThat(result.buildingName()).isNull();
        assertThat(result.floor()).isNull();
        assertThat(result.unitNo()).isNull();
        assertThat(result.confidence()).isEqualTo(AddressDetailParser.CONFIDENCE_HIGH);
        assertThat(result.method()).isEqualTo(AddressDetailParser.METHOD_NONE);
    }

    @Test
    void road_address가_null이면_NONE으로_표시한다() {
        var result = AddressDetailParser.parse(null);

        assertThat(result.method()).isEqualTo(AddressDetailParser.METHOD_NONE);
    }

    @Test
    void 층과_호수가_동시에_있고_일부_수식어가_끼어도_둘_다_추출한다() {
        var result = AddressDetailParser.parse(
            "경기도 성남시 분당구 야탑로271번길 7, 목련마을자영빌라근린상가동 1층 101 일부호 (야탑동, 목련마을자영빌라)");

        assertThat(result.floor()).isEqualTo("1");
        assertThat(result.unitNo()).isEqualTo("101");
        assertThat(result.buildingName()).isEqualTo("목련마을자영빌라근린상가동");
        assertThat(result.confidence()).isEqualTo(AddressDetailParser.CONFIDENCE_HIGH);
    }

    @Test
    void 건물명_중간에_풀리지_않는_숫자가_남으면_저신뢰도로_원본을_보존한다() {
        var result = AddressDetailParser.parse(
            "경기도 성남시 분당구 판교로 10, 봇들마을9단지아파트상가 105호 (삼평동)");

        assertThat(result.confidence()).isEqualTo(AddressDetailParser.CONFIDENCE_LOW);
        assertThat(result.method()).isEqualTo(AddressDetailParser.METHOD_UNPARSED);
        assertThat(result.buildingName()).isEqualTo("봇들마을9단지아파트상가 105호");
    }
}
