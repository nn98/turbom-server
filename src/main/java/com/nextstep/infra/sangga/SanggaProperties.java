package com.nextstep.infra.sangga;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "sangga.api")
public record SanggaProperties(String baseUrl, String serviceKey) {
}
