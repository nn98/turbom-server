package com.nextstep.web;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class SiteControllerTest {

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
            .andExpect(jsonPath("$.candidates", org.hamcrest.Matchers.hasSize(2)))
            .andExpect(jsonPath("$.candidates[0].pnu").exists());
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
            .andExpect(jsonPath("$.units[0].closedCount").value(2))
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
            .andExpect(jsonPath("$.timeline", org.hamcrest.Matchers.hasSize(2)))
            .andExpect(jsonPath("$.timeline[0].marketInfo.isPlaceholder").value(true))
            .andExpect(jsonPath("$.statistics.totalTenancyCount").value(2));
    }
}
