package com.nextstep.infra.sangga;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Component
public class SanggaApiClient {

    private static final Logger log = LoggerFactory.getLogger(SanggaApiClient.class);
    private static final int MAX_RADIUS_METERS = 2000;
    private static final int BREAKDOWN_NUM_OF_ROWS = 500; // API 1회 최대치. 그 이상 밀집 지역은 근사치.

    private final RestClient restClient;
    private final SanggaProperties properties;

    public SanggaApiClient(RestClient.Builder restClientBuilder, SanggaProperties properties) {
        this.restClient = restClientBuilder.baseUrl(properties.baseUrl()).build();
        this.properties = properties;
        if (properties.serviceKey() == null || properties.serviceKey().isBlank()) {
            log.warn("SANGGA_SERVICE_KEY is not set — marketInfo enrichment will return unavailable/empty for every request");
        }
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

    /**
     * 반경 내 업소를 가져와 bizesNm(소문자 트림) → indsSclsNm 룩업 맵을 반환한다.
     * 서비스키 없거나 API 실패 시 빈 맵 반환 — 호출 측은 null 없음을 보장받는다.
     */
    public Map<String, String> lookupStoreDetails(double lon, double lat, int radiusMeters) {
        if (properties.serviceKey() == null || properties.serviceKey().isBlank()) {
            return Map.of();
        }
        try {
            var body = fetchRadiusSummary(lon, lat, radiusMeters);
            if (body.items() == null || body.items().isEmpty()) return Map.of();
            return body.items().stream()
                .filter(item -> item.bizesNm() != null && item.indsSclsNm() != null)
                .collect(Collectors.toMap(
                    item -> item.bizesNm().trim().toLowerCase(),
                    SanggaStoreListResponse.SanggaStoreItem::indsSclsNm,
                    (a, b) -> a
                ));
        } catch (Exception e) {
            return Map.of();
        }
    }

    /**
     * 반경 내 전체 업소 수(totalCount, 업종 필터 없음)와 업종 대분류 구성용 표본(items,
     * 최대 500건)을 함께 가져온다. totalCount는 항상 정확하지만 items는 500건을 넘는
     * 초밀집 반경에서는 근사치다 — 이 프로젝트의 반경(300m)에서는 실측상 그런 사례가
     * 없었다.
     */
    public SanggaStoreListResponse.SanggaBody fetchRadiusSummary(double lon, double lat, int radiusMeters) {
        if (radiusMeters > MAX_RADIUS_METERS) {
            throw new IllegalArgumentException("반경은 최대 " + MAX_RADIUS_METERS + "m까지입니다: " + radiusMeters);
        }

        String encodedServiceKey = URLEncoder.encode(properties.serviceKey(), StandardCharsets.UTF_8);
        URI uri = URI.create(properties.baseUrl() + "/storeListInRadius"
            + "?serviceKey=" + encodedServiceKey
            + "&cx=" + lon
            + "&cy=" + lat
            + "&radius=" + radiusMeters
            + "&numOfRows=" + BREAKDOWN_NUM_OF_ROWS
            + "&pageNo=1"
            + "&type=json");

        SanggaStoreListResponse response = restClient.get()
            .uri(uri)
            .retrieve()
            .body(SanggaStoreListResponse.class);

        if (response == null || response.body() == null) {
            return new SanggaStoreListResponse.SanggaBody(0, List.of());
        }
        int totalCount = response.body().totalCount() == null ? 0 : response.body().totalCount();
        List<SanggaStoreListResponse.SanggaStoreItem> items = response.body().items() == null
            ? List.of() : response.body().items();
        return new SanggaStoreListResponse.SanggaBody(totalCount, items);
    }
}
