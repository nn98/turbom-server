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
    private static final String INSERT_DUPLICATE_UNIT_1 =
        "INSERT INTO licensed_business_record "
            + "(id, pnu, category, sub_category, license_no, business_name, business_type, business_status, "
            + "status_detail_code, status_detail, licensed_at, closed_at, road_address, jibun_address, "
            + "address_separated, address_corrected, local_gov_code, original_x, original_y) "
            + "VALUES (990001, '4113110100100990000', '동물', '동물미용업', 'test-license-1', '중복PNU 1층', "
            + "NULL, '영업/정상', '0000', '정상', '2020-01-01', NULL, "
            + "'경기도 성남시 수정구 테스트로 1, 1층 (테스트동)', '경기도 성남시 수정구 테스트동 99 1층', "
            + "FALSE, TRUE, '3780000', 212818.475436898, 438579.588327304)";
    private static final String INSERT_DUPLICATE_UNIT_2 =
        "INSERT INTO licensed_business_record "
            + "(id, pnu, category, sub_category, license_no, business_name, business_type, business_status, "
            + "status_detail_code, status_detail, licensed_at, closed_at, road_address, jibun_address, "
            + "address_separated, address_corrected, local_gov_code, original_x, original_y) "
            + "VALUES (990002, '4113110100100990000', '동물', '동물병원', 'test-license-2', '중복PNU 2층', "
            + "NULL, '폐업', '0002', '폐업', '2021-01-01', '2022-01-01', "
            + "'경기도 성남시 수정구 테스트로 1, 2층 (테스트동)', '경기도 성남시 수정구 테스트동 99 2층', "
            + "FALSE, TRUE, '3780000', 212818.475436898, 438579.588327304)";
    private static final String INSERT_RAW_STATUS =
        "INSERT INTO licensed_business_record "
            + "(id, pnu, category, sub_category, license_no, business_name, business_type, business_status, "
            + "status_detail_code, status_detail, licensed_at, closed_at, road_address, jibun_address, "
            + "address_separated, address_corrected, local_gov_code, original_x, original_y) "
            + "VALUES (990201, '4113110100100970000', '건강', '의료기기판매(임대)업', 'test-license-5', "
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
            .andExpect(jsonPath("$.candidates", org.hamcrest.Matchers.hasSize(435)))
            .andExpect(jsonPath("$.candidates[*].pnu",
                org.hamcrest.Matchers.hasItems("4113110100100340000", "4113110100100300002")))
            .andExpect(jsonPath("$.candidates[0].pnu").exists())
            .andExpect(jsonPath("$.candidates[0].latitude").isNumber())
            .andExpect(jsonPath("$.candidates[0].longitude").isNumber());
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
            .andExpect(jsonPath("$.units[0].closedCount").value(4))
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
        mockMvc.perform(get("/api/units/4113110100100340000-U1"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.timeline", org.hamcrest.Matchers.hasSize(3)))
            .andExpect(jsonPath("$.timeline[0].status").value("영업"))
            .andExpect(jsonPath("$.timeline[0].marketInfo.isPlaceholder").value(true))
            .andExpect(jsonPath("$.statistics.totalTenancyCount").value(3));
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
    void csv_동일_pnu의_상세주소별_물건을_리스팅한다() throws Exception {
        mockMvc.perform(get("/api/sites/" + CSV_ADDRESS_UNIT_PNU))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.site.latitude").isNumber())
            .andExpect(jsonPath("$.site.longitude").isNumber())
            .andExpect(jsonPath("$.units", org.hamcrest.Matchers.hasSize(28)))
            .andExpect(jsonPath("$.units[*].totalTenancyCount", org.hamcrest.Matchers.hasItem(16)))
            .andExpect(jsonPath("$.units[*].currentStatus",
                org.hamcrest.Matchers.hasItems("영업", "공실")));
    }
}
