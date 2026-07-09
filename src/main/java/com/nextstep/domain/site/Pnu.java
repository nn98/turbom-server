package com.nextstep.domain.site;

public record Pnu(String value) {
    public Pnu {
        if (value == null || !value.matches("\\d{19}")) {
            throw new IllegalArgumentException("PNU는 19자리 숫자여야 합니다: " + value);
        }
    }

    public String legalDongCode() {
        return value.substring(0, 10);
    }
}
