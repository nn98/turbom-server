### Task 9: SanggaApiClient — 상가(상권)정보 API 실시간 호출

**Files:**
- Create: `server/src/main/java/com/nextstep/infra/sangga/SanggaProperties.java`
- Create: `server/src/main/java/com/nextstep/infra/sangga/SanggaStoreListResponse.java`
- Create: `server/src/main/java/com/nextstep/infra/sangga/SanggaApiClient.java`
- Test: `server/src/test/java/com/nextstep/infra/sangga/SanggaApiClientTest.java`

**Interfaces:**
- Produces: `SanggaApiClient.countSameCategoryInRadius(double lon, double lat, int radiusMeters, String subCategory) -> int`. 반경 2000m 초과 시 `IllegalArgumentException`. 응답 파싱 실패·HTTP 오류는 이 클래스에서 그대로 예외로 던진다(무음 처리는 Task 10의 `MarketInfoService` 책임).

- [ ] **Step 1: 실패하는 테스트 작성 — MockRestServiceServer로 HTTP를 흉내낸다**

```java
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
```

- [ ] **Step 2: 테스트 실행 → 실패 확인**

Run: `cd server && mvn -q test -Dtest=SanggaApiClientTest`
Expected: FAIL — `SanggaApiClient`·`SanggaProperties`·`SanggaStoreListResponse` 없어 컴파일 에러.

- [ ] **Step 3: SanggaProperties 작성**

```java
package com.nextstep.infra.sangga;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "sangga.api")
public record SanggaProperties(String baseUrl, String serviceKey) {
}
```

- [ ] **Step 4: SanggaStoreListResponse 작성**

```java
package com.nextstep.infra.sangga;

import java.util.List;

public record SanggaStoreListResponse(SanggaHeader header, SanggaBody body) {

    public record SanggaHeader(String resultCode, String resultMsg) {
    }

    public record SanggaBody(List<SanggaStoreItem> items, int numOfRows, int pageNo, int totalCount) {
    }

    public record SanggaStoreItem(String indsSclsNm) {
    }
}
```

- [ ] **Step 5: SanggaApiClient 작성**

```java
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
```

이 클라이언트는 `spec/sangga_client.py`의 `fetch_radius_same_category_count`와 달리 `indsSclsCd` 서버측 필터를 쓰지 않는다 — `indsSclsNm`(이름) 클라이언트측 비교로 단순화했다(brainstorming 단계에서 코드 매핑표 부재로 확정). `RestClient.Builder`는 스프링 부트가 자동 구성한 빈을 그대로 주입받는다(직접 `.baseUrl()`을 호출하지 않고 프로퍼티로 조립).

- [ ] **Step 6: 테스트 재실행 → 통과 확인**

Run: `cd server && mvn -q test -Dtest=SanggaApiClientTest`
Expected: `Tests run: 2, Failures: 0, Errors: 0`

- [ ] **Step 7: 커밋**

```bash
git add src/main/java/com/nextstep/infra/sangga src/test/java/com/nextstep/infra/sangga
git commit -m "feat: add SanggaApiClient for storeListInRadius"
```

---

