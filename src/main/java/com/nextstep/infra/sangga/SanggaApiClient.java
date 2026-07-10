package com.nextstep.infra.sangga;

import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

@Component
public class SanggaApiClient {

    private static final int MAX_RADIUS_METERS = 2000;
    private static final int NUM_OF_ROWS = 500;

    private final RestClient restClient;
    private final SanggaProperties properties;

    public SanggaApiClient(RestClient.Builder restClientBuilder, SanggaProperties properties) {
        this.restClient = restClientBuilder.baseUrl(properties.baseUrl()).build();
        this.properties = properties;
    }

    public int countSameCategoryInRadius(double lon, double lat, int radiusMeters, String subCategory) {
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
            + "&numOfRows=" + NUM_OF_ROWS
            + "&pageNo=1"
            + "&type=json");

        SanggaStoreListResponse response = restClient.get()
            .uri(uri)
            .retrieve()
            .body(SanggaStoreListResponse.class);

        if (response == null || response.body() == null || response.body().items() == null) {
            return 0;
        }
        return (int) response.body().items().stream()
            .filter(item -> subCategory.equals(item.indsSclsNm()))
            .count();
    }
}
