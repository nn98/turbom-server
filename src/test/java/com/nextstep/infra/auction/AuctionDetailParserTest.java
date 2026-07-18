package com.nextstep.infra.auction;

import com.nextstep.domain.auction.AuctionCase;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

class AuctionDetailParserTest {

    private static final String CASE_51795_DETAIL_TEXT = """
        법원성남지원
        사건번호2025타경51795
        물건기본정보
        사건번호,물건번호,물건종류,감정평가액,최저매각가격 (매수신청보증금),입찰방법,매각기일,물건비고,담당 을(를) 나타낸 표
        사건번호
        2025타경51795전자
        물건번호
        1
        물건종류
        상가,오피스텔,근린시설

        감정평가액
        783,000,000원
        최저매각가격
        (매수신청보증금)
        131,599,000원
        (13,159,900원)
        입찰방법
        기일입찰

        예정매각기일
        2026.08.03 10:00 제3별관 1층 제5호법정

        물건비고

        목록1 소재지
        (근린생활시설) 경기도 성남시 수정구 고등동 616 대왕빌딩주건축물 1동 1층101호

        담당
        수원지방법원 성남지원| 경매4계
        사건접수,경매개시일,배당요구종기,청구금액 을(를) 나타낸 표
        사건접수
        2025.03.14
        경매개시일
        2025.03.15

        배당요구종기
        2025.05.19
        청구금액
        461,495,221원
        기일내역
        기일,기일종류,기일장소,최저매각가격,기일결과 을(를) 나타낸 표
        기일 기일종류 기일장소 최저매각가격 기일결과
        2026.02.09 (10:00) 매각기일 제3별관 1층 제5호법정 783,000,000원 유찰
        2026.03.16 (10:00) 매각기일 제3별관 1층 제5호법정 548,100,000원 유찰
        2026.04.20 (10:00) 매각기일 제3별관 1층 제5호법정 383,670,000원 유찰
        2026.05.22 (10:00) 매각기일 제3별관 1층 제5호법정 268,569,000원 유찰
        2026.06.29 (10:00) 매각기일 제3별관 1층 제5호법정 187,998,000원 유찰
        2026.08.03 (10:00) 매각기일 제3별관 1층 제5호법정 131,599,000원
        2026.08.10 (16:00) 매각결정기일 제3별관 1층 제5호법정
        감정평가요항표 요약
        1. 구분건물감정평가요항표 요약 - 철근콘크리트구조 건물 내 상업용 구분건물로 승강기 등 이용 편리함.
        인근매각물건사례
        유의사항
        """;

    @Test
    void 상세_화면에서_전체_필드를_추출한다() {
        AuctionDetailParser parser = new AuctionDetailParser();

        AuctionCase auctionCase = parser.parse("2025타경51795", 1, CASE_51795_DETAIL_TEXT);

        assertThat(auctionCase.caseNumber()).isEqualTo("2025타경51795");
        assertThat(auctionCase.itemNumber()).isEqualTo(1);
        assertThat(auctionCase.court()).isEqualTo("수원지방법원 성남지원");
        assertThat(auctionCase.divisionName()).isEqualTo("경매4계");
        assertThat(auctionCase.propertyType()).isEqualTo("상가,오피스텔,근린시설");
        assertThat(auctionCase.jibunAddress())
            .isEqualTo("(근린생활시설) 경기도 성남시 수정구 고등동 616 대왕빌딩주건축물 1동 1층101호");
        assertThat(auctionCase.appraisalValueKrw()).isEqualByComparingTo(new BigDecimal("783000000"));
        assertThat(auctionCase.minimumSalePriceKrw()).isEqualByComparingTo(new BigDecimal("131599000"));
        assertThat(auctionCase.bidDepositKrw()).isEqualByComparingTo(new BigDecimal("13159900"));
        assertThat(auctionCase.biddingMethod()).isEqualTo("기일입찰");
        assertThat(auctionCase.saleDate()).isEqualTo("2026.08.03 10:00 제3별관 1층 제5호법정");
        assertThat(auctionCase.filedDate()).isEqualTo("2025.03.14");
        assertThat(auctionCase.auctionStartDate()).isEqualTo("2025.03.15");
        assertThat(auctionCase.claimDeadline()).isEqualTo("2025.05.19");
        assertThat(auctionCase.claimAmountKrw()).isEqualByComparingTo(new BigDecimal("461495221"));
        assertThat(auctionCase.appraisalSummary()).startsWith("감정평가요항표 요약");
        assertThat(auctionCase.appraisalSummary()).doesNotContain("인근매각물건사례");

        assertThat(auctionCase.scheduleHistory())
            .extracting(e -> e.scheduleDate(), e -> e.scheduleType(), e -> e.result())
            .containsExactly(
                tuple("2026.02.09", "매각기일", "유찰"),
                tuple("2026.03.16", "매각기일", "유찰"),
                tuple("2026.04.20", "매각기일", "유찰"),
                tuple("2026.05.22", "매각기일", "유찰"),
                tuple("2026.06.29", "매각기일", "유찰"),
                tuple("2026.08.03", "매각기일", null)
            );
        assertThat(auctionCase.scheduleHistory().get(0).minimumPriceKrw())
            .isEqualByComparingTo(new BigDecimal("783000000"));
        assertThat(auctionCase.scheduleHistory().get(0).location())
            .isEqualTo("제3별관 1층 제5호법정");
        assertThat(auctionCase.scheduleHistory().get(5).minimumPriceKrw())
            .isEqualByComparingTo(new BigDecimal("131599000"));
    }
}
