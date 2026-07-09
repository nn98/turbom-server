package com.nextstep.infra.sangga;

import java.time.Duration;
import org.springframework.boot.web.client.ClientHttpRequestFactories;
import org.springframework.boot.web.client.ClientHttpRequestFactorySettings;
import org.springframework.boot.web.client.RestClientCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.ClientHttpRequestFactory;

/**
 * SanggaApiClient가 주입받는 RestClient.Builder에 커넥트/리드 타임아웃을 건다.
 * SanggaApiClient 생성자에서 직접 requestFactory를 덮어쓰지 않는 이유: Spring Boot의
 * 자동구성 RestClient.Builder는 prototype 빈이라 컴포넌트별로 독립적이지만, 테스트에서
 * MockRestServiceServer.bindTo(builder)도 동일한 방식(builder.requestFactory(...))으로
 * 동작해서 생성자 안에서 다시 requestFactory를 호출하면 mock이 덮여써진다(순서상 나중이
 * 이기므로). RestClientCustomizer는 자동구성 파이프라인에서만 적용되고, 테스트가 수동으로
 * 만든 RestClient.Builder는 이 customizer를 타지 않으므로 mock 바인딩이 그대로 유지된다.
 */
@Configuration
public class SanggaRestClientConfig {

    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(2);
    private static final Duration READ_TIMEOUT = Duration.ofSeconds(3);

    @Bean
    public RestClientCustomizer sanggaTimeoutCustomizer() {
        ClientHttpRequestFactorySettings settings = ClientHttpRequestFactorySettings.DEFAULTS
            .withConnectTimeout(CONNECT_TIMEOUT)
            .withReadTimeout(READ_TIMEOUT);
        ClientHttpRequestFactory requestFactory = ClientHttpRequestFactories.get(settings);
        return builder -> builder.requestFactory(requestFactory);
    }
}
