package com.nextstep.infra.sangga;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestToUriTemplate;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.springframework.http.HttpMethod.GET;

class SanggaApiClientTest {

    private MockRestServiceServer server;
    private SanggaApiClient client;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder().baseUrl("https://apis.data.go.kr/B553077/api/open/sdsc2");
        server = MockRestServiceServer.bindTo(builder).build();
        client = new SanggaApiClient(builder, new SanggaProperties("https://apis.data.go.kr/B553077/api/open/sdsc2", "test-key"));
    }

    @Test
    void 반경조회_응답의_totalCount를_그대로_쓴다() {
        String body = """
            {"header":{"resultCode":"00","resultMsg":"NORMAL SERVICE"},
             "body":{"totalCount":7}}
            """;
        server.expect(requestToUriTemplate(
                "https://apis.data.go.kr/B553077/api/open/sdsc2/storeListInRadius?serviceKey=test-key&cx={cx}&cy={cy}&radius={radius}&indsLclsCd={indsLclsCd}&numOfRows={numOfRows}&pageNo={pageNo}&type={type}",
                127.1456208, 37.4492216, 300, "M1", 1, 1, "json"))
            .andExpect(method(GET))
            .andRespond(withSuccess(body, MediaType.APPLICATION_JSON));

        int count = client.countInRadiusByCategory(127.1456208, 37.4492216, 300, "M1");

        assertThat(count).isEqualTo(7);
    }

    @Test
    void NODATA_ERROR_응답은_0으로_처리한다() {
        String body = """
            {"header":{"resultCode":"03","resultMsg":"NODATA_ERROR"},"body":{}}
            """;
        server.expect(requestToUriTemplate(
                "https://apis.data.go.kr/B553077/api/open/sdsc2/storeListInRadius?serviceKey=test-key&cx={cx}&cy={cy}&radius={radius}&indsLclsCd={indsLclsCd}&numOfRows={numOfRows}&pageNo={pageNo}&type={type}",
                127.0, 37.0, 300, "Q1", 1, 1, "json"))
            .andExpect(method(GET))
            .andRespond(withSuccess(body, MediaType.APPLICATION_JSON));

        int count = client.countInRadiusByCategory(127.0, 37.0, 300, "Q1");

        assertThat(count).isEqualTo(0);
    }

    @Test
    void 반경이_2000m를_넘으면_예외() {
        assertThatThrownBy(() -> client.countInRadiusByCategory(127.0, 37.0, 2001, "I2"))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void 전체점포수_요약은_업종필터없이_500건까지_조회한다() {
        String body = """
            {"header":{"resultCode":"00","resultMsg":"NORMAL SERVICE"},
             "body":{"totalCount":2,"items":[{"indsLclsCd":"I2","indsLclsNm":"음식"},{"indsLclsCd":"G2","indsLclsNm":"소매"}]}}
            """;
        server.expect(requestToUriTemplate(
                "https://apis.data.go.kr/B553077/api/open/sdsc2/storeListInRadius?serviceKey=test-key&cx={cx}&cy={cy}&radius={radius}&numOfRows={numOfRows}&pageNo={pageNo}&type={type}",
                127.1456208, 37.4492216, 300, 500, 1, "json"))
            .andExpect(method(GET))
            .andRespond(withSuccess(body, MediaType.APPLICATION_JSON));

        SanggaStoreListResponse.SanggaBody summary = client.fetchRadiusSummary(127.1456208, 37.4492216, 300);

        assertThat(summary.totalCount()).isEqualTo(2);
        assertThat(summary.items()).hasSize(2);
    }

    @Test
    void 전체점포수_요약_NODATA_ERROR는_빈_요약으로_처리한다() {
        String body = """
            {"header":{"resultCode":"03","resultMsg":"NODATA_ERROR"},"body":{}}
            """;
        server.expect(requestToUriTemplate(
                "https://apis.data.go.kr/B553077/api/open/sdsc2/storeListInRadius?serviceKey=test-key&cx={cx}&cy={cy}&radius={radius}&numOfRows={numOfRows}&pageNo={pageNo}&type={type}",
                127.0, 37.0, 300, 500, 1, "json"))
            .andExpect(method(GET))
            .andRespond(withSuccess(body, MediaType.APPLICATION_JSON));

        SanggaStoreListResponse.SanggaBody summary = client.fetchRadiusSummary(127.0, 37.0, 300);

        assertThat(summary.totalCount()).isEqualTo(0);
        assertThat(summary.items()).isEmpty();
    }
}
