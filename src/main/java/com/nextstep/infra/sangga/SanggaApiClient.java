package com.nextstep.infra.sangga;

import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

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

        SanggaStoreListResponse response = restClient.get()
            .uri(uriBuilder -> uriBuilder.path("/storeListInRadius")
                .queryParam("serviceKey", properties.serviceKey())
                .queryParam("cx", lon)
                .queryParam("cy", lat)
                .queryParam("radius", radiusMeters)
                .queryParam("numOfRows", NUM_OF_ROWS)
                .queryParam("pageNo", 1)
                .queryParam("type", "json")
                .build())
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
