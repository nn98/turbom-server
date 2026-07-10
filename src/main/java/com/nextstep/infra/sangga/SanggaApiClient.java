package com.nextstep.infra.sangga;

import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

@Component
public class SanggaApiClient {

    private static final int MAX_RADIUS_METERS = 2000;

    private final RestClient restClient;
    private final SanggaProperties properties;

    public SanggaApiClient(RestClient.Builder restClientBuilder, SanggaProperties properties) {
        this.restClient = restClientBuilder.baseUrl(properties.baseUrl()).build();
        this.properties = properties;
    }

    /**
     * 반경 내 특정 상가 대분류(indsLclsCd)에 속하는 업소 수. 서버 측 indsLclsCd 필터를
     * 써서 totalCount를 그대로 쓴다 — items를 받아 클라이언트에서 세지 않는다
     * (`상권조회-API-명세.md` §2.2 "설계 변경 시사점" 반영). numOfRows=1로 최소 페이로드만
     * 요청한다.
     */
    public int countInRadiusByCategory(double lon, double lat, int radiusMeters, String indsLclsCd) {
        if (radiusMeters > MAX_RADIUS_METERS) {
            throw new IllegalArgumentException("반경은 최대 " + MAX_RADIUS_METERS + "m까지입니다: " + radiusMeters);
        }

        // serviceKey는 base64 계열이라 +/=/를 포함한다. RestClient의 기본 URI 빌더는
        // RFC 3986 기준으로 +를 안전한 문자로 보고 인코딩하지 않지만, data.go.kr은
        // application/x-www-form-urlencoded 관례대로 +를 공백으로 해석해 키가
        // 손상된다(401 Unauthorized). URLEncoder로 직접 인코딩한 뒤 이미 인코딩된
        // URI로 요청해 RestClient가 다시 인코딩(이중 인코딩)하지 않게 한다.
        String encodedServiceKey = URLEncoder.encode(properties.serviceKey(), StandardCharsets.UTF_8);
        URI uri = URI.create(properties.baseUrl() + "/storeListInRadius"
            + "?serviceKey=" + encodedServiceKey
            + "&cx=" + lon
            + "&cy=" + lat
            + "&radius=" + radiusMeters
            + "&indsLclsCd=" + indsLclsCd
            + "&numOfRows=1"
            + "&pageNo=1"
            + "&type=json");

        SanggaStoreListResponse response = restClient.get()
            .uri(uri)
            .retrieve()
            .body(SanggaStoreListResponse.class);

        if (response == null || response.body() == null || response.body().totalCount() == null) {
            return 0;
        }
        return response.body().totalCount();
    }
}
