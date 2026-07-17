package com.nextstep.application;

import com.nextstep.domain.site.Site;
import com.nextstep.domain.tenancy.Tenancy;
import com.nextstep.domain.unit.Unit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.jdbc.Sql;
import java.util.List;
import java.util.Optional;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.offset;

@SpringBootTest
class TenancyQueryServiceTest {

    private static final String DUPLICATE_PNU = "4113110100100990000";
    private static final String DELETE_DUPLICATE_PNU =
        "DELETE FROM licensed_business_record WHERE pnu = '" + DUPLICATE_PNU + "'";
    private static final String INSERT_DUPLICATE_UNIT_1 =
        "INSERT INTO licensed_business_record "
            + "(id, pnu, category, sub_category, license_no, business_name, business_type, business_status, "
            + "status_detail_code, status_detail, licensed_at, closed_at, road_address, jibun_address, "
            + "address_separated, address_corrected, parsed_building_name, parsed_floor, parsed_unit_no, "
            + "parse_confidence, parse_method, local_gov_code, original_x, original_y) "
            + "VALUES (990001, '4113110100100990000', '동물', '동물미용업', 'test-license-1', '중복PNU 1층', "
            + "NULL, '영업/정상', '0000', '정상', '2020-01-01', NULL, "
            + "'경기도 성남시 수정구 테스트로 1, 1층 (테스트동)', '경기도 성남시 수정구 테스트동 99 1층', "
            + "FALSE, TRUE, NULL, '1', NULL, 'HIGH', 'REGEX', '3780000', 212818.475436898, 438579.588327304)";
    private static final String INSERT_DUPLICATE_UNIT_2 =
        "INSERT INTO licensed_business_record "
            + "(id, pnu, category, sub_category, license_no, business_name, business_type, business_status, "
            + "status_detail_code, status_detail, licensed_at, closed_at, road_address, jibun_address, "
            + "address_separated, address_corrected, parsed_building_name, parsed_floor, parsed_unit_no, "
            + "parse_confidence, parse_method, local_gov_code, original_x, original_y) "
            + "VALUES (990002, '4113110100100990000', '동물', '동물병원', 'test-license-2', '중복PNU 2층', "
            + "NULL, '폐업', '0002', '폐업', '2021-01-01', '2022-01-01', "
            + "'경기도 성남시 수정구 테스트로 1, 2층 (테스트동)', '경기도 성남시 수정구 테스트동 99 2층', "
            + "FALSE, TRUE, NULL, '2', NULL, 'HIGH', 'REGEX', '3780000', 212818.475436898, 438579.588327304)";
    private static final String FLOOR_OMITTED_PNU = "4113110100100970000";
    private static final String DELETE_FLOOR_OMITTED_PNU =
        "DELETE FROM licensed_business_record WHERE pnu = '" + FLOOR_OMITTED_PNU + "'";
    private static final String INSERT_FLOOR_OMITTED_WITH_FLOOR =
        "INSERT INTO licensed_business_record "
            + "(id, pnu, category, sub_category, license_no, business_name, business_type, business_status, "
            + "status_detail_code, status_detail, licensed_at, closed_at, road_address, jibun_address, "
            + "address_separated, address_corrected, parsed_building_name, parsed_floor, parsed_unit_no, "
            + "parse_confidence, parse_method, local_gov_code, original_x, original_y) "
            + "VALUES (990201, '4113110100100970000', '동물', '동물미용업', 'test-license-5', '층생략 1', "
            + "NULL, '영업/정상', '0000', '정상', '2020-01-01', NULL, "
            + "'경기도 성남시 수정구 테스트로 3, 1층 119호 (테스트동)', '경기도 성남시 수정구 테스트동 97 1층 119호', "
            + "FALSE, TRUE, NULL, '1', '119', 'HIGH', 'REGEX', '3780000', 212818.475436898, 438579.588327304)";
    private static final String INSERT_FLOOR_OMITTED_WITHOUT_FLOOR =
        "INSERT INTO licensed_business_record "
            + "(id, pnu, category, sub_category, license_no, business_name, business_type, business_status, "
            + "status_detail_code, status_detail, licensed_at, closed_at, road_address, jibun_address, "
            + "address_separated, address_corrected, parsed_building_name, parsed_floor, parsed_unit_no, "
            + "parse_confidence, parse_method, local_gov_code, original_x, original_y) "
            + "VALUES (990202, '4113110100100970000', '동물', '동물병원', 'test-license-6', '층생략 2', "
            + "NULL, '폐업', '0002', '폐업', '2021-01-01', '2022-01-01', "
            + "'경기도 성남시 수정구 테스트로 3, 119호 (테스트동)', '경기도 성남시 수정구 테스트동 97 119호', "
            + "FALSE, TRUE, NULL, NULL, '119', 'HIGH', 'REGEX', '3780000', 212818.475436898, 438579.588327304)";
    private static final String MULTI_CATEGORY_PNU = "4113110100100960000";
    private static final String DELETE_MULTI_CATEGORY_PNU =
        "DELETE FROM licensed_business_record WHERE pnu = '" + MULTI_CATEGORY_PNU + "'";
    // 시나리오1: gap 90일 이내 업종 전환 -> 병합
    private static final String INSERT_GAP_MERGE_A =
        "INSERT INTO licensed_business_record "
            + "(id, pnu, category, sub_category, license_no, business_name, business_type, business_status, "
            + "status_detail_code, status_detail, licensed_at, closed_at, road_address, jibun_address, "
            + "address_separated, address_corrected, parsed_building_name, parsed_floor, parsed_unit_no, "
            + "parse_confidence, parse_method, local_gov_code, original_x, original_y) "
            + "VALUES (990301, '4113110100100960000', '외식', '커피', 'test-license-10', '병합가게A', "
            + "NULL, '폐업', '0002', '폐업', '2020-01-01', '2022-01-01', "
            + "'경기도 성남시 수정구 테스트로 4, 1층 101호 (테스트동)', '경기도 성남시 수정구 테스트동 96 1층 101호', "
            + "FALSE, TRUE, NULL, '1', '101', 'HIGH', 'REGEX', '3780000', 212818.475436898, 438579.588327304)";
    private static final String INSERT_GAP_MERGE_B =
        "INSERT INTO licensed_business_record "
            + "(id, pnu, category, sub_category, license_no, business_name, business_type, business_status, "
            + "status_detail_code, status_detail, licensed_at, closed_at, road_address, jibun_address, "
            + "address_separated, address_corrected, parsed_building_name, parsed_floor, parsed_unit_no, "
            + "parse_confidence, parse_method, local_gov_code, original_x, original_y) "
            + "VALUES (990302, '4113110100100960000', '소매', '즉석판매', 'test-license-11', '병합가게A', "
            + "NULL, '영업/정상', '0000', '정상', '2022-02-01', NULL, "
            + "'경기도 성남시 수정구 테스트로 4, 1층 101호 (테스트동)', '경기도 성남시 수정구 테스트동 96 1층 101호', "
            + "FALSE, TRUE, NULL, '1', '101', 'HIGH', 'REGEX', '3780000', 212818.475436898, 438579.588327304)";
    // 시나리오2: gap 90일 초과 재입점 -> 분리 유지
    private static final String INSERT_GAP_SPLIT_C =
        "INSERT INTO licensed_business_record "
            + "(id, pnu, category, sub_category, license_no, business_name, business_type, business_status, "
            + "status_detail_code, status_detail, licensed_at, closed_at, road_address, jibun_address, "
            + "address_separated, address_corrected, parsed_building_name, parsed_floor, parsed_unit_no, "
            + "parse_confidence, parse_method, local_gov_code, original_x, original_y) "
            + "VALUES (990303, '4113110100100960000', '외식', '한식', 'test-license-12', '재입점가게B', "
            + "NULL, '폐업', '0002', '폐업', '2018-01-01', '2018-06-01', "
            + "'경기도 성남시 수정구 테스트로 4, 2층 102호 (테스트동)', '경기도 성남시 수정구 테스트동 96 2층 102호', "
            + "FALSE, TRUE, NULL, '2', '102', 'HIGH', 'REGEX', '3780000', 212818.475436898, 438579.588327304)";
    private static final String INSERT_GAP_SPLIT_D =
        "INSERT INTO licensed_business_record "
            + "(id, pnu, category, sub_category, license_no, business_name, business_type, business_status, "
            + "status_detail_code, status_detail, licensed_at, closed_at, road_address, jibun_address, "
            + "address_separated, address_corrected, parsed_building_name, parsed_floor, parsed_unit_no, "
            + "parse_confidence, parse_method, local_gov_code, original_x, original_y) "
            + "VALUES (990304, '4113110100100960000', '외식', '분식', 'test-license-13', '재입점가게B', "
            + "NULL, '영업/정상', '0000', '정상', '2020-01-01', NULL, "
            + "'경기도 성남시 수정구 테스트로 4, 2층 102호 (테스트동)', '경기도 성남시 수정구 테스트동 96 2층 102호', "
            + "FALSE, TRUE, NULL, '2', '102', 'HIGH', 'REGEX', '3780000', 212818.475436898, 438579.588327304)";
    // 시나리오3: 완전 겹침(업종 두 개 동시 보유) -> 병합
    private static final String INSERT_OVERLAP_E =
        "INSERT INTO licensed_business_record "
            + "(id, pnu, category, sub_category, license_no, business_name, business_type, business_status, "
            + "status_detail_code, status_detail, licensed_at, closed_at, road_address, jibun_address, "
            + "address_separated, address_corrected, parsed_building_name, parsed_floor, parsed_unit_no, "
            + "parse_confidence, parse_method, local_gov_code, original_x, original_y) "
            + "VALUES (990305, '4113110100100960000', '외식', '카페', 'test-license-14', '동시업종가게C', "
            + "NULL, '영업/정상', '0000', '정상', '2021-01-01', NULL, "
            + "'경기도 성남시 수정구 테스트로 4, 3층 103호 (테스트동)', '경기도 성남시 수정구 테스트동 96 3층 103호', "
            + "FALSE, TRUE, NULL, '3', '103', 'HIGH', 'REGEX', '3780000', 212818.475436898, 438579.588327304)";
    private static final String INSERT_OVERLAP_F =
        "INSERT INTO licensed_business_record "
            + "(id, pnu, category, sub_category, license_no, business_name, business_type, business_status, "
            + "status_detail_code, status_detail, licensed_at, closed_at, road_address, jibun_address, "
            + "address_separated, address_corrected, parsed_building_name, parsed_floor, parsed_unit_no, "
            + "parse_confidence, parse_method, local_gov_code, original_x, original_y) "
            + "VALUES (990306, '4113110100100960000', '소매', '베이커리', 'test-license-15', '동시업종가게C', "
            + "NULL, '영업/정상', '0000', '정상', '2021-06-01', NULL, "
            + "'경기도 성남시 수정구 테스트로 4, 3층 103호 (테스트동)', '경기도 성남시 수정구 테스트동 96 3층 103호', "
            + "FALSE, TRUE, NULL, '3', '103', 'HIGH', 'REGEX', '3780000', 212818.475436898, 438579.588327304)";
    // 시나리오4: 같은 Unit이지만 businessName 다름 -> 병합 안 함 (회귀)
    private static final String INSERT_DIFFERENT_NAME_G =
        "INSERT INTO licensed_business_record "
            + "(id, pnu, category, sub_category, license_no, business_name, business_type, business_status, "
            + "status_detail_code, status_detail, licensed_at, closed_at, road_address, jibun_address, "
            + "address_separated, address_corrected, parsed_building_name, parsed_floor, parsed_unit_no, "
            + "parse_confidence, parse_method, local_gov_code, original_x, original_y) "
            + "VALUES (990307, '4113110100100960000', '서비스', '미용', 'test-license-16', '가게D-1', "
            + "NULL, '영업/정상', '0000', '정상', '2020-01-01', NULL, "
            + "'경기도 성남시 수정구 테스트로 4, 4층 104호 (테스트동)', '경기도 성남시 수정구 테스트동 96 4층 104호', "
            + "FALSE, TRUE, NULL, '4', '104', 'HIGH', 'REGEX', '3780000', 212818.475436898, 438579.588327304)";
    private static final String INSERT_DIFFERENT_NAME_H =
        "INSERT INTO licensed_business_record "
            + "(id, pnu, category, sub_category, license_no, business_name, business_type, business_status, "
            + "status_detail_code, status_detail, licensed_at, closed_at, road_address, jibun_address, "
            + "address_separated, address_corrected, parsed_building_name, parsed_floor, parsed_unit_no, "
            + "parse_confidence, parse_method, local_gov_code, original_x, original_y) "
            + "VALUES (990308, '4113110100100960000', '서비스', '세탁', 'test-license-17', '가게D-2', "
            + "NULL, '영업/정상', '0000', '정상', '2020-06-01', NULL, "
            + "'경기도 성남시 수정구 테스트로 4, 4층 104호 (테스트동)', '경기도 성남시 수정구 테스트동 96 4층 104호', "
            + "FALSE, TRUE, NULL, '4', '104', 'HIGH', 'REGEX', '3780000', 212818.475436898, 438579.588327304)";
    private static final String NO_STOREFRONT_PNU = "4113110100100950000";
    private static final String DELETE_NO_STOREFRONT_PNU =
        "DELETE FROM licensed_business_record WHERE pnu = '" + NO_STOREFRONT_PNU + "'";
    private static final String INSERT_NO_STOREFRONT_STOREFRONT_RECORD =
        "INSERT INTO licensed_business_record "
            + "(id, pnu, category, sub_category, license_no, business_name, business_type, business_status, "
            + "status_detail_code, status_detail, licensed_at, closed_at, road_address, jibun_address, "
            + "address_separated, address_corrected, local_gov_code, original_x, original_y) "
            + "VALUES (990501, '4113110100100950000', '식품', '일반음식점', 'test-license-30', '일반음식점가게', "
            + "NULL, '영업/정상', '0000', '정상', '2020-01-01', NULL, "
            + "'경기도 성남시 수정구 테스트로 6, 1층 (테스트동)', '경기도 성남시 수정구 테스트동 96 1층', "
            + "FALSE, TRUE, '3780000', 212818.475436898, 438579.588327304)";
    private static final String INSERT_NO_STOREFRONT_ONLY_RECORD_1 =
        "INSERT INTO licensed_business_record "
            + "(id, pnu, category, sub_category, license_no, business_name, business_type, business_status, "
            + "status_detail_code, status_detail, licensed_at, closed_at, road_address, jibun_address, "
            + "address_separated, address_corrected, local_gov_code, original_x, original_y) "
            + "VALUES (990502, '4113110100100950000', '생활', '통신판매업', 'test-license-31', '통신판매업체A', "
            + "NULL, '영업/정상', '0000', '정상', '2021-01-01', NULL, "
            + "'경기도 성남시 수정구 테스트로 6 (테스트동)', '경기도 성남시 수정구 테스트동 96', "
            + "FALSE, TRUE, '3780000', 212818.475436898, 438579.588327304)";
    private static final String INSERT_NO_STOREFRONT_ONLY_RECORD_2 =
        "INSERT INTO licensed_business_record "
            + "(id, pnu, category, sub_category, license_no, business_name, business_type, business_status, "
            + "status_detail_code, status_detail, licensed_at, closed_at, road_address, jibun_address, "
            + "address_separated, address_corrected, local_gov_code, original_x, original_y) "
            + "VALUES (990503, '4113110100100950000', '생활', '방문판매업', 'test-license-32', '방문판매업체B', "
            + "NULL, '영업/정상', '0000', '정상', '2022-01-01', NULL, "
            + "'경기도 성남시 수정구 테스트로 6 (테스트동)', '경기도 성남시 수정구 테스트동 96', "
            + "FALSE, TRUE, '3780000', 212818.475436898, 438579.588327304)";
    // 같은 businessName("겸업사업자")이 매장업종(일반음식점) + 무점포후보업종(통신판매업)을
    // 동시에 보유 — 물리적 신호가 하나라도 있으니 둘 다 storefront로 취급돼야 함(동물병원 더 하임 사례 재현)
    private static final String INSERT_NO_STOREFRONT_MIXED_STOREFRONT =
        "INSERT INTO licensed_business_record "
            + "(id, pnu, category, sub_category, license_no, business_name, business_type, business_status, "
            + "status_detail_code, status_detail, licensed_at, closed_at, road_address, jibun_address, "
            + "address_separated, address_corrected, parsed_floor, local_gov_code, original_x, original_y) "
            + "VALUES (990504, '4113110100100950000', '식품', '일반음식점', 'test-license-33', '겸업사업자', "
            + "NULL, '영업/정상', '0000', '정상', '2023-01-01', NULL, "
            + "'경기도 성남시 수정구 테스트로 6, 2층 (테스트동)', '경기도 성남시 수정구 테스트동 96 2층', "
            + "FALSE, TRUE, '2', '3780000', 212818.475436898, 438579.588327304)";
    private static final String INSERT_NO_STOREFRONT_MIXED_NOSTOREFRONT =
        "INSERT INTO licensed_business_record "
            + "(id, pnu, category, sub_category, license_no, business_name, business_type, business_status, "
            + "status_detail_code, status_detail, licensed_at, closed_at, road_address, jibun_address, "
            + "address_separated, address_corrected, local_gov_code, original_x, original_y) "
            + "VALUES (990505, '4113110100100950000', '생활', '통신판매업', 'test-license-34', '겸업사업자', "
            + "NULL, '영업/정상', '0000', '정상', '2023-02-01', NULL, "
            + "'경기도 성남시 수정구 테스트로 6 (테스트동)', '경기도 성남시 수정구 테스트동 96', "
            + "FALSE, TRUE, '3780000', 212818.475436898, 438579.588327304)";
    private static final String SAME_JIBUN_PNU = "4113110100100980000";
    private static final String CSV_ADDRESS_UNIT_PNU = "4113110800105590004";
    private static final String DELETE_SAME_JIBUN_PNU =
        "DELETE FROM licensed_business_record WHERE pnu = '" + SAME_JIBUN_PNU + "'";
    private static final String INSERT_SAME_JIBUN_ROAD_1 =
        "INSERT INTO licensed_business_record "
            + "(id, pnu, category, sub_category, license_no, business_name, business_type, business_status, "
            + "status_detail_code, status_detail, licensed_at, closed_at, road_address, jibun_address, "
            + "address_separated, address_corrected, local_gov_code, original_x, original_y) "
            + "VALUES (990101, '4113110100100980000', '동물', '동물병원', 'test-license-3', '동일지번 도로명1', "
            + "NULL, '영업/정상', '0000', '정상', '2020-01-01', NULL, "
            + "'경기도 성남시 수정구 테스트로 2, 1층 (테스트동)', '경기도 성남시 수정구 테스트동 98', "
            + "FALSE, TRUE, '3780000', 212818.475436898, 438579.588327304)";
    private static final String INSERT_SAME_JIBUN_ROAD_2 =
        "INSERT INTO licensed_business_record "
            + "(id, pnu, category, sub_category, license_no, business_name, business_type, business_status, "
            + "status_detail_code, status_detail, licensed_at, closed_at, road_address, jibun_address, "
            + "address_separated, address_corrected, local_gov_code, original_x, original_y) "
            + "VALUES (990102, '4113110100100980000', '동물', '동물병원', 'test-license-4', '동일지번 도로명2', "
            + "NULL, '폐업', '0002', '폐업', '2021-01-01', '2022-01-01', "
            + "'경기도 성남시 수정구 테스트로 2, 2층 (테스트동)', '경기도 성남시 수정구 테스트동 98', "
            + "FALSE, TRUE, '3780000', 212818.475436898, 438579.588327304)";

    @Autowired TenancyQueryService tenancyQueryService;

    @Test
    void 지번주소로_검색하면_일치하는_자리가_나온다() {
        List<Site> results = tenancyQueryService.searchSites("신흥동");
        assertThat(results).extracting(s -> s.pnu().value())
            .contains("4113110100100340000", "4113110100100300002");
        Site site = results.stream()
            .filter(result -> result.pnu().value().equals("4113110100100340000"))
            .findFirst()
            .orElseThrow();
        assertThat(site.coordinate()).isNotNull();
        assertThat(site.coordinate().latitude()).isCloseTo(37.449282365, offset(0.000001));
        assertThat(site.coordinate().longitude()).isCloseTo(127.145653550, offset(0.000001));
    }

    @Test
    void pnu로_자리상세를_조회하면_물건과_통계가_채워진다() {
        Optional<Site> site = tenancyQueryService.findSiteWithUnits("4113110300100280001");
        assertThat(site).isPresent();
        assertThat(site.get().coordinate().latitude()).isCloseTo(37.441429604, offset(0.000001));
        assertThat(site.get().coordinate().longitude()).isCloseTo(127.134860655, offset(0.000001));
        Unit unit = site.get().units().get(0);
        assertThat(unit.statistics().totalTenancyCount()).isGreaterThan(0);
        assertThat(unit.statistics().closedCount()).isGreaterThanOrEqualTo(0);
    }

    @Test
    void 존재하지_않는_pnu는_빈값() {
        assertThat(tenancyQueryService.findSiteWithUnits("9999999999999999999")).isEmpty();
    }

    @Test
    void unitId로_물건상세를_조회하면_csv_이력이_나온다() {
        var unitWithSite = tenancyQueryService.findUnitWithTenancies("4113110100100340000-U1");
        assertThat(unitWithSite).isPresent();
        Unit unit = unitWithSite.get().unit();
        // 레코드 10497/11709('동물병원 더 하임', 동일 category='동물', 7일 간격)가 gap 병합되어 4 -> 3
        assertThat(unit.tenancies()).hasSize(3);
        assertThat(unitWithSite.get().site().jibunAddress()).contains("신흥동");
    }

    @Test
    @Sql(statements = {DELETE_DUPLICATE_PNU, INSERT_DUPLICATE_UNIT_1, INSERT_DUPLICATE_UNIT_2})
    @Sql(statements = DELETE_DUPLICATE_PNU, executionPhase = Sql.ExecutionPhase.AFTER_TEST_METHOD)
    void 같은_pnu라도_지번주소가_다르면_물건을_분리한다() {
        Site site = tenancyQueryService.findSiteWithUnits(DUPLICATE_PNU).orElseThrow();

        assertThat(site.units()).hasSize(2);
        assertThat(site.units()).extracting(Unit::unitId)
            .containsExactly(DUPLICATE_PNU + "-U1", DUPLICATE_PNU + "-U2");
        assertThat(site.units()).extracting(Unit::label)
            .containsExactly("1층", "2층");
    }

    @Test
    @Sql(statements = {DELETE_DUPLICATE_PNU, INSERT_DUPLICATE_UNIT_1, INSERT_DUPLICATE_UNIT_2})
    @Sql(statements = DELETE_DUPLICATE_PNU, executionPhase = Sql.ExecutionPhase.AFTER_TEST_METHOD)
    void unitId로_조회하면_같은_pnu의_해당_지번주소_이력만_나온다() {
        var unitWithSite = tenancyQueryService.findUnitWithTenancies(DUPLICATE_PNU + "-U2").orElseThrow();

        assertThat(unitWithSite.unit().unitId()).isEqualTo(DUPLICATE_PNU + "-U2");
        assertThat(unitWithSite.unit().tenancies()).hasSize(1);
        assertThat(unitWithSite.unit().tenancies().get(0).businessName()).isEqualTo("중복PNU 2층");
        assertThat(unitWithSite.site().roadAddress()).contains("2층");
        assertThat(tenancyQueryService.findUnitWithTenancies(DUPLICATE_PNU + "-U3")).isEmpty();
    }

    @Test
    @Sql(statements = {DELETE_SAME_JIBUN_PNU, INSERT_SAME_JIBUN_ROAD_1, INSERT_SAME_JIBUN_ROAD_2})
    @Sql(statements = DELETE_SAME_JIBUN_PNU, executionPhase = Sql.ExecutionPhase.AFTER_TEST_METHOD)
    void 같은_pnu에서_도로명주소만_다르면_같은_물건으로_묶는다() {
        Site site = tenancyQueryService.findSiteWithUnits(SAME_JIBUN_PNU).orElseThrow();

        assertThat(site.units()).hasSize(1);
        assertThat(site.units().get(0).unitId()).isEqualTo(SAME_JIBUN_PNU + "-U1");
        assertThat(site.units().get(0).tenancies()).hasSize(2);
        assertThat(site.units().get(0).tenancies()).extracting("businessName")
            .containsExactly("동일지번 도로명1", "동일지번 도로명2");
        assertThat(tenancyQueryService.findUnitWithTenancies(SAME_JIBUN_PNU + "-U2")).isEmpty();
    }

    @Test
    @Sql(statements = {DELETE_FLOOR_OMITTED_PNU, INSERT_FLOOR_OMITTED_WITH_FLOOR, INSERT_FLOOR_OMITTED_WITHOUT_FLOOR})
    @Sql(statements = DELETE_FLOOR_OMITTED_PNU, executionPhase = Sql.ExecutionPhase.AFTER_TEST_METHOD)
    void 층이_생략된_레코드는_같은_호실번호면_병합된다() {
        Site site = tenancyQueryService.findSiteWithUnits(FLOOR_OMITTED_PNU).orElseThrow();

        assertThat(site.units()).hasSize(1);
        assertThat(site.units().get(0).tenancies()).hasSize(2);
        assertThat(site.units().get(0).label()).isEqualTo("1층 119호");
    }

    @Test
    @Sql(statements = {DELETE_MULTI_CATEGORY_PNU, INSERT_GAP_MERGE_A, INSERT_GAP_MERGE_B,
        INSERT_GAP_SPLIT_C, INSERT_GAP_SPLIT_D, INSERT_OVERLAP_E, INSERT_OVERLAP_F,
        INSERT_DIFFERENT_NAME_G, INSERT_DIFFERENT_NAME_H})
    @Sql(statements = DELETE_MULTI_CATEGORY_PNU, executionPhase = Sql.ExecutionPhase.AFTER_TEST_METHOD)
    void gap이_90일_이내인_같은_가게의_다른_업종_레코드는_하나의_재직으로_병합된다() {
        Site site = tenancyQueryService.findSiteWithUnits(MULTI_CATEGORY_PNU).orElseThrow();
        Unit unit = site.units().stream().filter(u -> u.label().equals("1층 101호")).findFirst().orElseThrow();

        assertThat(unit.tenancies()).hasSize(1);
        Tenancy merged = unit.tenancies().get(0);
        assertThat(merged.period().licensedAt()).isEqualTo(java.time.LocalDate.of(2020, 1, 1));
        assertThat(merged.period().closedAt()).isNull();
        assertThat(merged.category()).isEqualTo("소매");
        assertThat(merged.subCategory()).isEqualTo("즉석판매");
        assertThat(merged.status()).isEqualTo("영업/정상");
    }

    @Test
    @Sql(statements = {DELETE_MULTI_CATEGORY_PNU, INSERT_GAP_MERGE_A, INSERT_GAP_MERGE_B,
        INSERT_GAP_SPLIT_C, INSERT_GAP_SPLIT_D, INSERT_OVERLAP_E, INSERT_OVERLAP_F,
        INSERT_DIFFERENT_NAME_G, INSERT_DIFFERENT_NAME_H})
    @Sql(statements = DELETE_MULTI_CATEGORY_PNU, executionPhase = Sql.ExecutionPhase.AFTER_TEST_METHOD)
    void gap이_90일_초과인_같은_가게_레코드는_별도_재직으로_유지된다() {
        Site site = tenancyQueryService.findSiteWithUnits(MULTI_CATEGORY_PNU).orElseThrow();
        Unit unit = site.units().stream().filter(u -> u.label().equals("2층 102호")).findFirst().orElseThrow();

        assertThat(unit.tenancies()).hasSize(2);
        assertThat(unit.tenancies()).extracting(t -> t.period().licensedAt())
            .containsExactly(java.time.LocalDate.of(2018, 1, 1), java.time.LocalDate.of(2020, 1, 1));
    }

    @Test
    @Sql(statements = {DELETE_MULTI_CATEGORY_PNU, INSERT_GAP_MERGE_A, INSERT_GAP_MERGE_B,
        INSERT_GAP_SPLIT_C, INSERT_GAP_SPLIT_D, INSERT_OVERLAP_E, INSERT_OVERLAP_F,
        INSERT_DIFFERENT_NAME_G, INSERT_DIFFERENT_NAME_H})
    @Sql(statements = DELETE_MULTI_CATEGORY_PNU, executionPhase = Sql.ExecutionPhase.AFTER_TEST_METHOD)
    void 기간이_완전히_겹치는_동시업종_레코드는_하나의_재직으로_병합된다() {
        Site site = tenancyQueryService.findSiteWithUnits(MULTI_CATEGORY_PNU).orElseThrow();
        Unit unit = site.units().stream().filter(u -> u.label().equals("3층 103호")).findFirst().orElseThrow();

        assertThat(unit.tenancies()).hasSize(1);
        Tenancy merged = unit.tenancies().get(0);
        assertThat(merged.period().licensedAt()).isEqualTo(java.time.LocalDate.of(2021, 1, 1));
        assertThat(merged.period().closedAt()).isNull();
        assertThat(merged.category()).isEqualTo("소매");
    }

    @Test
    @Sql(statements = {DELETE_MULTI_CATEGORY_PNU, INSERT_GAP_MERGE_A, INSERT_GAP_MERGE_B,
        INSERT_GAP_SPLIT_C, INSERT_GAP_SPLIT_D, INSERT_OVERLAP_E, INSERT_OVERLAP_F,
        INSERT_DIFFERENT_NAME_G, INSERT_DIFFERENT_NAME_H})
    @Sql(statements = DELETE_MULTI_CATEGORY_PNU, executionPhase = Sql.ExecutionPhase.AFTER_TEST_METHOD)
    void 같은_Unit이라도_businessName이_다르면_병합하지_않는다() {
        Site site = tenancyQueryService.findSiteWithUnits(MULTI_CATEGORY_PNU).orElseThrow();
        Unit unit = site.units().stream().filter(u -> u.label().equals("4층 104호")).findFirst().orElseThrow();

        assertThat(unit.tenancies()).hasSize(2);
        assertThat(unit.tenancies()).extracting(Tenancy::businessName)
            .containsExactlyInAnyOrder("가게D-1", "가게D-2");
    }

    @Test
    @Sql(statements = {DELETE_NO_STOREFRONT_PNU, INSERT_NO_STOREFRONT_STOREFRONT_RECORD,
        INSERT_NO_STOREFRONT_ONLY_RECORD_1, INSERT_NO_STOREFRONT_ONLY_RECORD_2})
    @Sql(statements = DELETE_NO_STOREFRONT_PNU, executionPhase = Sql.ExecutionPhase.AFTER_TEST_METHOD)
    void 무점포업종만_있는_상호는_noStorefrontRegistrations로_분리된다() {
        Site site = tenancyQueryService.findSiteWithUnits(NO_STOREFRONT_PNU).orElseThrow();

        assertThat(site.units()).hasSize(1);
        assertThat(site.units().get(0).tenancies()).extracting(Tenancy::businessName)
            .containsExactly("일반음식점가게");

        assertThat(site.noStorefrontRegistrations()).hasSize(2);
        assertThat(site.noStorefrontRegistrations()).extracting(Tenancy::businessName)
            .containsExactlyInAnyOrder("통신판매업체A", "방문판매업체B");
    }

    @Test
    @Sql(statements = {DELETE_NO_STOREFRONT_PNU, INSERT_NO_STOREFRONT_MIXED_STOREFRONT,
        INSERT_NO_STOREFRONT_MIXED_NOSTOREFRONT})
    @Sql(statements = DELETE_NO_STOREFRONT_PNU, executionPhase = Sql.ExecutionPhase.AFTER_TEST_METHOD)
    void 같은_상호가_매장업종과_무점포업종을_겸하면_전부_storefront로_취급된다() {
        Site site = tenancyQueryService.findSiteWithUnits(NO_STOREFRONT_PNU).orElseThrow();

        assertThat(site.noStorefrontRegistrations()).isEmpty();
        int totalTenancies = site.units().stream().mapToInt(u -> u.tenancies().size()).sum();
        assertThat(totalTenancies).isEqualTo(2);
        assertThat(site.units().stream().flatMap(u -> u.tenancies().stream()))
            .extracting(Tenancy::businessName)
            .containsOnly("겸업사업자");
    }

    @Test
    @Sql(statements = {DELETE_NO_STOREFRONT_PNU, INSERT_NO_STOREFRONT_ONLY_RECORD_1,
        INSERT_NO_STOREFRONT_ONLY_RECORD_2})
    @Sql(statements = DELETE_NO_STOREFRONT_PNU, executionPhase = Sql.ExecutionPhase.AFTER_TEST_METHOD)
    void 전부_무점포업종이면_units는_빈배열이다() {
        Site site = tenancyQueryService.findSiteWithUnits(NO_STOREFRONT_PNU).orElseThrow();

        assertThat(site.units()).isEmpty();
        assertThat(site.noStorefrontRegistrations()).hasSize(2);
    }

    @Test
    void csv_동일_pnu의_상세주소를_지번주소별_물건으로_묶는다() {
        Site site = tenancyQueryService.findSiteWithUnits(CSV_ADDRESS_UNIT_PNU).orElseThrow();

        assertThat(site.coordinate()).isNotNull();
        // unitKey가 parsedUnitNo(있으면) 또는 parsedFloor 기준으로 바뀌어
        // 동일 jibunAddress이지만 다른 층/호실이 올바르게 분리되고, 호실번호가 같으면 층 표기 생략 차이는 병합된다
        // businessName + gap(<=90일) 병합으로 인해 tenancy 수가 감소 (카테고리 동일 여부 무관, 79 -> 59)
        // 2026-07-18: businessName 단위 무점포업종 분리(Task 2) 도입으로 59 -> 39, 아래 21 -> 5로 추가 감소.
        // 캐치올 Unit("단일(상세주소불명)")은 애초에 parsedFloor/parsedUnitNo가 없는 레코드들이라
        // 무점포 후보 subCategory와 함께 물리적 신호가 없는 businessName 비율이 높아 감소폭이 가장 큼.
        // 39 - 5 = noStorefrontRegistrations(20)로 옮겨간 레코드 수와 정합(59-39=20).
        assertThat(site.units()).hasSize(30);
        assertThat(site.units().stream().mapToInt(unit -> unit.tenancies().size()).sum()).isEqualTo(39);
        // 5는 "단일(상세주소불명)" 캐치올 Unit 몫 — 다른 Unit의 개수가 늘어난 게 아니라
        // 무점포업종만 있던 businessName들이 noStorefrontRegistrations로 옮겨가며 캐치올 Unit만 크게 줄었다
        assertThat(site.units()).anySatisfy(unit -> assertThat(unit.tenancies()).hasSize(5));
        assertThat(site.noStorefrontRegistrations()).isNotEmpty();
        assertThat(site.units().stream()
            .flatMap(unit -> unit.tenancies().stream())
            .map(tenancy -> tenancy.status())
            .distinct())
            .contains("영업/정상", "폐업", "제외/삭제/전출");
    }
}
