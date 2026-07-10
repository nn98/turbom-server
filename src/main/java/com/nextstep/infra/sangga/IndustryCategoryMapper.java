package com.nextstep.infra.sangga;

import java.util.Map;
import java.util.Optional;

/**
 * 인허가 소분류(우리 도메인) → 상가(상권)정보 API 대분류코드(indsLclsCd) 매핑.
 *
 * 두 체계는 서로 다른 분류(행안부 인허가 vs 소상공인시장진흥공단 상권업종)라 공식 매핑표가
 * 없다(브레인스토밍 확정 사항). 실 적재 데이터(server/src/main/resources/data.sql)에 실제
 * 존재하는 136개 소분류를 상가API 반경조회로 직접 수집한 대분류 10개
 * (G2 소매·I1 숙박·I2 음식·L1 부동산·M1 과학기술·N1 시설관리임대·P1 교육·Q1 보건의료·
 * R1 예술스포츠·S2 수리개인)에 손수 매핑했다.
 *
 * 도축업·폐기물처리·제조업·도매업 등 원래 "상가 상권" 개념이 없는 인허가/시설 라이선스 업종은
 * 매핑하지 않는다(Optional.empty()) — 이 경우 marketInfo.sameCategoryNearbyCount는 API를
 * 호출하지 않고 null로 응답한다(값을 못 구한 게 아니라 애초에 비교 대상 자체가 없다는 뜻).
 */
public final class IndustryCategoryMapper {

    private static final Map<String, String> SUB_CATEGORY_TO_SANGGA_CODE = Map.ofEntries(
        // 건강
        Map.entry("안전상비의약품 판매업소", "G2"),
        Map.entry("약국", "G2"),
        Map.entry("응급환자이송업", "Q1"),
        Map.entry("의료기기수리업", "S2"),
        Map.entry("의료기기판매(임대)업", "G2"),
        Map.entry("의료유사업", "Q1"),
        Map.entry("의원", "Q1"),
        Map.entry("치과기공소", "Q1"),
        Map.entry("안경업", "G2"),
        Map.entry("병원", "Q1"),
        Map.entry("부속의료기관", "Q1"),
        Map.entry("산후조리업", "Q1"),

        // 기타
        Map.entry("담배소매업", "G2"),
        Map.entry("무료직업소개소", "N1"),
        Map.entry("유료직업소개소", "N1"),
        Map.entry("옥외광고업", "M1"),
        Map.entry("요양보호사교육기관", "P1"),

        // 동물
        Map.entry("동물미용업", "S2"),
        Map.entry("동물병원", "M1"),
        Map.entry("동물약국", "G2"),
        Map.entry("동물용의료용구판매업", "G2"),
        Map.entry("동물위탁관리업", "S2"),
        Map.entry("동물판매업", "G2"),

        // 문화
        Map.entry("청소년게임제공업", "R1"),
        Map.entry("숙박업", "I1"),
        Map.entry("외국인관광도시민박업", "I1"),
        Map.entry("인터넷컴퓨터게임시설제공업", "R1"),
        Map.entry("국내여행업", "N1"),
        Map.entry("일반게임제공업", "R1"),
        Map.entry("국내외여행업", "N1"),
        Map.entry("국제회의기획업", "N1"),
        Map.entry("공연장", "R1"),
        Map.entry("관광숙박업", "I1"),
        Map.entry("노래연습장업", "R1"),
        Map.entry("박물관 및 미술관", "R1"),
        Map.entry("복합유통게임제공업", "R1"),
        Map.entry("비디오물감상실업", "R1"),
        Map.entry("영화상영관", "R1"),
        Map.entry("영화상영업", "R1"),
        Map.entry("일반야영장업", "R1"),
        Map.entry("일반테마파크업", "R1"),
        Map.entry("종합여행업", "N1"),
        Map.entry("테마파크업(기타)", "R1"),

        // 생활
        Map.entry("골프연습장업", "R1"),
        Map.entry("당구장업", "R1"),
        Map.entry("대규모점포", "G2"),
        Map.entry("목욕장업", "S2"),
        Map.entry("무도장업", "R1"),
        Map.entry("무도학원업", "P1"),
        Map.entry("미용업", "S2"),
        Map.entry("체력단련장업", "R1"),
        Map.entry("이용업", "S2"),
        Map.entry("체육도장업", "R1"),
        Map.entry("빙상장업", "R1"),
        Map.entry("세탁업", "S2"),
        Map.entry("수영장업", "R1"),
        Map.entry("썰매장업", "R1"),
        Map.entry("종합체육시설업", "R1"),

        // 식품
        Map.entry("건강기능식품유통전문판매업", "G2"),
        Map.entry("건강기능식품일반판매업", "G2"),
        Map.entry("단란주점영업", "I2"),
        Map.entry("유통전문판매업", "G2"),
        Map.entry("일반음식점", "I2"),
        Map.entry("식용얼음판매업", "G2"),
        Map.entry("유흥주점영업", "I2"),
        Map.entry("식품판매업(기타)", "G2"),
        Map.entry("위탁급식영업", "I2"),
        Map.entry("축산판매업", "G2"),
        Map.entry("즉석판매제조가공업", "G2"),
        Map.entry("휴게음식점", "I2"),
        Map.entry("제과점영업", "I2"),
        Map.entry("집단급식소", "I2"),
        Map.entry("집단급식소식품판매업", "I2"),

        // 자원환경 (대부분 매핑 불가 — 아래 목록만 예외적으로 상가 상권과 접점이 있음)
        Map.entry("석유판매업", "G2"),
        Map.entry("소독업", "N1"),
        Map.entry("건물위생관리업", "N1"),
        Map.entry("계량기수리업", "S2"),
        Map.entry("배출가스전문정비사업자(확인검사대행자)", "S2"),
        Map.entry("석유및석유대체연료판매업체", "G2"),
        Map.entry("쓰레기종량제봉투판매업", "G2"),
        Map.entry("저수조청소업", "N1"),
        Map.entry("환경컨설팅회사", "M1")

        // 나머지(도축업·제조업·도매업·창고업·처리업·조사기관 등)는 상가 상권 개념이 없어
        // 의도적으로 매핑하지 않는다 — 아래 default 분기에서 Optional.empty() 처리.
    );

    private IndustryCategoryMapper() {
    }

    public static Optional<String> toSanggaCategoryCode(String subCategory) {
        if (subCategory == null) return Optional.empty();
        return Optional.ofNullable(SUB_CATEGORY_TO_SANGGA_CODE.get(subCategory));
    }
}
