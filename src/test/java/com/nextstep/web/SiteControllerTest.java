package com.nextstep.web;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.context.jdbc.Sql;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class SiteControllerTest {

    private static final String DUPLICATE_PNU = "4113110100100990000";
    private static final String CSV_ADDRESS_UNIT_PNU = "4113110800105590004";
    private static final String RAW_STATUS_PNU = "4113110100100970000";
    private static final String DELETE_DUPLICATE_PNU =
        "DELETE FROM licensed_business_record WHERE pnu = '" + DUPLICATE_PNU + "'";
    private static final String DELETE_RAW_STATUS_PNU =
        "DELETE FROM licensed_business_record WHERE pnu = '" + RAW_STATUS_PNU + "'";
    private static final String NO_STOREFRONT_PNU = "4113110100100960001";
    private static final String DELETE_NO_STOREFRONT_PNU2 =
        "DELETE FROM licensed_business_record WHERE pnu = '" + NO_STOREFRONT_PNU + "'";
    private static final String INSERT_NO_STOREFRONT_STOREFRONT =
        "INSERT INTO licensed_business_record "
            + "(id, pnu, category, sub_category, license_no, business_name, business_type, business_status, "
            + "status_detail_code, status_detail, licensed_at, closed_at, road_address, jibun_address, "
            + "address_separated, address_corrected, local_gov_code, original_x, original_y) "
            + "VALUES (9900000601, '4113110100100960001', '식품', '일반음식점', 'test-license-40', '진짜매장', "
            + "NULL, '영업/정상', '0000', '정상', '2020-01-01', NULL, "
            + "'경기도 성남시 수정구 테스트로 7 (테스트동)', '경기도 성남시 수정구 테스트동 97', "
            + "FALSE, TRUE, '3780000', 212818.475436898, 438579.588327304)";
    private static final String INSERT_NO_STOREFRONT_ONLY =
        "INSERT INTO licensed_business_record "
            + "(id, pnu, category, sub_category, license_no, business_name, business_type, business_status, "
            + "status_detail_code, status_detail, licensed_at, closed_at, road_address, jibun_address, "
            + "address_separated, address_corrected, local_gov_code, original_x, original_y) "
            + "VALUES (9900000602, '4113110100100960001', '생활', '통신판매업', 'test-license-41', '온라인셀러', "
            + "NULL, '영업/정상', '0000', '정상', '2021-01-01', NULL, "
            + "'경기도 성남시 수정구 테스트로 7 (테스트동)', '경기도 성남시 수정구 테스트동 97', "
            + "FALSE, TRUE, '3780000', 212818.475436898, 438579.588327304)";
    private static final String PURE_NO_STOREFRONT_PNU = "4113110100100960002";
    private static final String DELETE_PURE_NO_STOREFRONT_PNU =
        "DELETE FROM licensed_business_record WHERE pnu = '" + PURE_NO_STOREFRONT_PNU + "'";
    private static final String INSERT_PURE_NO_STOREFRONT =
        "INSERT INTO licensed_business_record "
            + "(id, pnu, category, sub_category, license_no, business_name, business_type, business_status, "
            + "status_detail_code, status_detail, licensed_at, closed_at, road_address, jibun_address, "
            + "address_separated, address_corrected, local_gov_code, original_x, original_y) "
            + "VALUES (9900000701, '4113110100100960002', '생활', '통신판매업', 'test-license-42', '순수온라인셀러', "
            + "NULL, '영업/정상', '0000', '정상', '2021-01-01', NULL, "
            + "'경기도 성남시 수정구 검색전용테스트로 (검색전용테스트동)', '경기도 성남시 수정구 검색전용테스트동', "
            + "FALSE, TRUE, '3780000', 212818.475436898, 438579.588327304)";
    private static final String INSERT_DUPLICATE_UNIT_1 =
        "INSERT INTO licensed_business_record "
            + "(id, pnu, category, sub_category, license_no, business_name, business_type, business_status, "
            + "status_detail_code, status_detail, licensed_at, closed_at, road_address, jibun_address, "
            + "address_separated, address_corrected, parsed_building_name, parsed_floor, parsed_unit_no, "
            + "parse_confidence, parse_method, local_gov_code, original_x, original_y) "
            + "VALUES (9900000001, '4113110100100990000', '동물', '동물미용업', 'test-license-1', '중복PNU 1층', "
            + "NULL, '영업/정상', '0000', '정상', '2020-01-01', NULL, "
            + "'경기도 성남시 수정구 테스트로 1, 1층 (테스트동)', '경기도 성남시 수정구 테스트동 99 1층', "
            + "FALSE, TRUE, NULL, '1', NULL, 'HIGH', 'REGEX', '3780000', 212818.475436898, 438579.588327304)";
    private static final String INSERT_DUPLICATE_UNIT_2 =
        "INSERT INTO licensed_business_record "
            + "(id, pnu, category, sub_category, license_no, business_name, business_type, business_status, "
            + "status_detail_code, status_detail, licensed_at, closed_at, road_address, jibun_address, "
            + "address_separated, address_corrected, parsed_building_name, parsed_floor, parsed_unit_no, "
            + "parse_confidence, parse_method, local_gov_code, original_x, original_y) "
            + "VALUES (9900000002, '4113110100100990000', '동물', '동물병원', 'test-license-2', '중복PNU 2층', "
            + "NULL, '폐업', '0002', '폐업', '2021-01-01', '2022-01-01', "
            + "'경기도 성남시 수정구 테스트로 1, 2층 (테스트동)', '경기도 성남시 수정구 테스트동 99 2층', "
            + "FALSE, TRUE, NULL, '2', NULL, 'HIGH', 'REGEX', '3780000', 212818.475436898, 438579.588327304)";
    // 2026-07-18: sub_category가 원래 '의료기기판매(임대)업'이었으나 무점포업종 목록에 편입되며
    // (호실정보 없는 단독 레코드라 물리적 신호 전무) noStorefrontRegistrations로 빠져 unit 자체가
    // 사라져 404가 나던 걸 '의원'으로 교체 — 이 테스트 목적(상세영업상태 없을 때 원본값 그대로
    // 응답)과는 무관한 업종이라 무해함
    private static final String INSERT_RAW_STATUS =
        "INSERT INTO licensed_business_record "
            + "(id, pnu, category, sub_category, license_no, business_name, business_type, business_status, "
            + "status_detail_code, status_detail, licensed_at, closed_at, road_address, jibun_address, "
            + "address_separated, address_corrected, local_gov_code, original_x, original_y) "
            + "VALUES (9900000201, '4113110100100970000', '건강', '의원', 'test-license-5', "
            + "'원본상태 테스트', NULL, '휴업', NULL, NULL, '2024-01-01', NULL, "
            + "'경기도 성남시 수정구 테스트로 3 (테스트동)', '경기도 성남시 수정구 테스트동 97', "
            + "FALSE, TRUE, '3780000', 212818.475436898, 438579.588327304)";

    @Autowired MockMvc mockMvc;

    @Test
    void query_없이_검색하면_400_INVALID_QUERY() throws Exception {
        mockMvc.perform(get("/api/sites/search"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error").value("INVALID_QUERY"));
    }

    @Test
    void 신흥동으로_검색하면_후보가_나온다() throws Exception {
        mockMvc.perform(get("/api/sites/search").param("query", "신흥동"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.candidates", org.hamcrest.Matchers.hasSize(1588)))
            .andExpect(jsonPath("$.candidates[*].pnu",
                org.hamcrest.Matchers.hasItems("4113110100100340000", "4113110100100300002")))
            .andExpect(jsonPath("$.candidates[0].pnu").exists())
            .andExpect(jsonPath("$.candidates[0].latitude").isNumber())
            .andExpect(jsonPath("$.candidates[0].longitude").isNumber());
    }

    @Test
    @Sql(statements = {DELETE_PURE_NO_STOREFRONT_PNU, INSERT_PURE_NO_STOREFRONT})
    @Sql(statements = DELETE_PURE_NO_STOREFRONT_PNU, executionPhase = Sql.ExecutionPhase.AFTER_TEST_METHOD)
    void 무점포업종만_있는_자리는_검색결과에서_빠진다() throws Exception {
        // 2026-07-18: units가 0개인 PNU가 검색/지도핀에는 그대로 노출되던 버그 — 실사례
        // (금토동 390-11/436-3/517-7, 전부 고압가스업·통신판매업만 있는 자리)로 발견됨
        mockMvc.perform(get("/api/sites/search").param("query", "검색전용테스트동"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.candidates", org.hamcrest.Matchers.hasSize(0)));
    }

    @Test
    void 존재하지_않는_pnu는_404_SITE_NOT_FOUND() throws Exception {
        mockMvc.perform(get("/api/sites/9999999999999999999"))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.error").value("SITE_NOT_FOUND"));
    }

    @Test
    void 자리상세는_폐업많은순으로_물건이_정렬된다() throws Exception {
        mockMvc.perform(get("/api/sites/4113110300100280001"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.site.latitude").value(org.hamcrest.Matchers.closeTo(37.441429604, 0.000001)))
            .andExpect(jsonPath("$.site.longitude").value(org.hamcrest.Matchers.closeTo(127.134860655, 0.000001)))
            .andExpect(jsonPath("$.units[0].closedCount").value(6))
            .andExpect(jsonPath("$.disclaimer.note").exists());
    }

    @Test
    void 존재하지_않는_unitId는_404_UNIT_NOT_FOUND() throws Exception {
        mockMvc.perform(get("/api/units/no-such-unit"))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.error").value("UNIT_NOT_FOUND"));
    }

    @Test
    void 물건상세는_타임라인과_marketInfo를_포함한다() throws Exception {
        // 2026-07-18: 무점포업종 분리 이전엔 스웨터메이커스/그랑핏 아름다운자세(둘 다 통신판매업,
        // 무관한 사업자)가 동물병원 더 하임과 뒤섞여 hasSize(3)이었음 — 그 두 업체가
        // noStorefrontRegistrations로 빠지면서 이 unit엔 동물병원 더 하임(동물병원 레코드, 영업중)만 남음
        mockMvc.perform(get("/api/units/4113110100100340000-U1"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.timeline", org.hamcrest.Matchers.hasSize(1)))
            .andExpect(jsonPath("$.timeline[0].businessName").value("동물병원 더 하임"))
            .andExpect(jsonPath("$.timeline[0].status").value("영업"))
            .andExpect(jsonPath("$.timeline[0].marketInfo.isPlaceholder").value(true))
            .andExpect(jsonPath("$.statistics.totalTenancyCount").value(1));
    }

    @Test
    @Sql(statements = {DELETE_DUPLICATE_PNU, INSERT_DUPLICATE_UNIT_1, INSERT_DUPLICATE_UNIT_2})
    @Sql(statements = DELETE_DUPLICATE_PNU, executionPhase = Sql.ExecutionPhase.AFTER_TEST_METHOD)
    void 같은_pnu의_지번주소별_물건을_리스팅한다() throws Exception {
        mockMvc.perform(get("/api/sites/" + DUPLICATE_PNU))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.units", org.hamcrest.Matchers.hasSize(2)))
            .andExpect(jsonPath("$.units[0].unitId").value(DUPLICATE_PNU + "-U2"))
            .andExpect(jsonPath("$.units[1].unitId").value(DUPLICATE_PNU + "-U1"));

        mockMvc.perform(get("/api/units/" + DUPLICATE_PNU + "-U2"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.unit.roadAddress").value(org.hamcrest.Matchers.containsString("2층")))
            .andExpect(jsonPath("$.timeline", org.hamcrest.Matchers.hasSize(1)))
            .andExpect(jsonPath("$.timeline[0].businessName").value("중복PNU 2층"))
            .andExpect(jsonPath("$.timeline[0].status").value("폐업"));
    }

    @Test
    @Sql(statements = {DELETE_DUPLICATE_PNU, INSERT_DUPLICATE_UNIT_1, INSERT_DUPLICATE_UNIT_2})
    @Sql(statements = DELETE_DUPLICATE_PNU, executionPhase = Sql.ExecutionPhase.AFTER_TEST_METHOD)
    void 검색결과_candidate에도_유닛별_층호_파싱정보가_내려간다() throws Exception {
        // 2026-07-21: 프론트가 건물별로 묶은 뒤 층/호로 재분리하려면 상세 API를 자리마다
        // 추가 호출할 필요 없이 검색 결과 자체에 유닛별 파싱 결과가 있어야 한다는 요청 반영
        mockMvc.perform(get("/api/sites/search").param("query", "테스트동 99"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.candidates", org.hamcrest.Matchers.hasSize(1)))
            .andExpect(jsonPath("$.candidates[0].units", org.hamcrest.Matchers.hasSize(2)))
            .andExpect(jsonPath("$.candidates[0].units[*].unitId",
                org.hamcrest.Matchers.containsInAnyOrder(DUPLICATE_PNU + "-U1", DUPLICATE_PNU + "-U2")))
            .andExpect(jsonPath("$.candidates[0].units[*].parsedFloor",
                org.hamcrest.Matchers.containsInAnyOrder("1", "2")))
            .andExpect(jsonPath("$.candidates[0].units[*].parseConfidence",
                org.hamcrest.Matchers.everyItem(org.hamcrest.Matchers.is("HIGH"))));
    }

    @Test
    @Sql(statements = {DELETE_RAW_STATUS_PNU, INSERT_RAW_STATUS})
    @Sql(statements = DELETE_RAW_STATUS_PNU, executionPhase = Sql.ExecutionPhase.AFTER_TEST_METHOD)
    void 상세영업상태가_없어도_영업상태_원본값을_그대로_응답한다() throws Exception {
        mockMvc.perform(get("/api/units/" + RAW_STATUS_PNU + "-U1"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.timeline", org.hamcrest.Matchers.hasSize(1)))
            .andExpect(jsonPath("$.timeline[0].businessName").value("원본상태 테스트"))
            .andExpect(jsonPath("$.timeline[0].status").value("휴업"));
    }

    @Test
    @Sql(statements = {DELETE_NO_STOREFRONT_PNU2, INSERT_NO_STOREFRONT_STOREFRONT, INSERT_NO_STOREFRONT_ONLY})
    @Sql(statements = DELETE_NO_STOREFRONT_PNU2, executionPhase = Sql.ExecutionPhase.AFTER_TEST_METHOD)
    void 무점포업종은_units와_분리된_배열로_응답한다() throws Exception {
        mockMvc.perform(get("/api/sites/" + NO_STOREFRONT_PNU))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.units", org.hamcrest.Matchers.hasSize(1)))
            .andExpect(jsonPath("$.units[0].currentBusinessName").value("진짜매장"))
            .andExpect(jsonPath("$.noStorefrontRegistrations", org.hamcrest.Matchers.hasSize(1)))
            .andExpect(jsonPath("$.noStorefrontRegistrations[0].businessName").value("온라인셀러"))
            .andExpect(jsonPath("$.noStorefrontRegistrations[0].category").value("생활"))
            .andExpect(jsonPath("$.noStorefrontRegistrations[0].subCategory").value("통신판매업"));
    }

    @Test
    void csv_동일_pnu의_상세주소별_물건을_리스팅한다() throws Exception {
        mockMvc.perform(get("/api/sites/" + CSV_ADDRESS_UNIT_PNU))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.site.latitude").isNumber())
            .andExpect(jsonPath("$.site.longitude").isNumber())
            .andExpect(jsonPath("$.units", org.hamcrest.Matchers.hasSize(30)))
            // 5 = 캐치올 Unit의 병합 후 개수. 2026-07-18: businessName 단위 무점포업종 분리(Task 2)로
            // 20건이 noStorefrontRegistrations로 이동하며 21 -> 5로 감소 (다른 Unit이 늘어난 게 아님)
            .andExpect(jsonPath("$.units[*].totalTenancyCount", org.hamcrest.Matchers.hasItem(5)))
            .andExpect(jsonPath("$.units[*].currentStatus",
                org.hamcrest.Matchers.hasItems("영업", "공실")));
    }
}
