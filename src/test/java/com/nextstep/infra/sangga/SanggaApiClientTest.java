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
    void 반경조회_응답에서_동일업종만_카운트한다() {
        String body = """
            {"header":{"resultCode":"00","resultMsg":"NORMAL SERVICE"},
             "body":{"items":[{"indsSclsNm":"동물미용업"},{"indsSclsNm":"동물미용업"},{"indsSclsNm":"동물병원"}],
                     "numOfRows":3,"pageNo":1,"totalCount":3}}
            """;
        server.expect(requestToUriTemplate(
                "https://apis.data.go.kr/B553077/api/open/sdsc2/storeListInRadius?serviceKey=test-key&cx={cx}&cy={cy}&radius={radius}&numOfRows={numOfRows}&pageNo={pageNo}&type={type}",
                127.1456208, 37.4492216, 300, 500, 1, "json"))
            .andExpect(method(GET))
            .andRespond(withSuccess(body, MediaType.APPLICATION_JSON));

        int count = client.countSameCategoryInRadius(127.1456208, 37.4492216, 300, "동물미용업");

        assertThat(count).isEqualTo(2);
    }

    @Test
    void 반경이_2000m를_넘으면_예외() {
        assertThatThrownBy(() -> client.countSameCategoryInRadius(127.0, 37.0, 2001, "동물미용업"))
            .isInstanceOf(IllegalArgumentException.class);
    }
}
