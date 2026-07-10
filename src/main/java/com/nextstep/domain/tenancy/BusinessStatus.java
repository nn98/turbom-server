package com.nextstep.domain.tenancy;

public enum BusinessStatus {
    ACTIVE("영업"),
    CLOSED("폐업");

    private final String display;

    BusinessStatus(String display) {
        this.display = display;
    }

    public String display() {
        return display;
    }

    public static BusinessStatus fromDb(String value) {
        if ("영업/정상".equals(value)) return ACTIVE;
        if ("제외/삭제/전출".equals(value) || "전출".equals(value)) return CLOSED;

        for (BusinessStatus status : values()) {
            if (status.display.equals(value)) return status;
        }
        throw new IllegalArgumentException("알 수 없는 영업상태: " + value);
    }
}
