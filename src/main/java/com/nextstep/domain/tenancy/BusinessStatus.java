package com.nextstep.domain.tenancy;

public enum BusinessStatus {
    ACTIVE("영업"),
    CLOSED("폐업"),
    SUSPENDED("휴업");

    private final String display;

    BusinessStatus(String display) {
        this.display = display;
    }

    public String display() {
        return display;
    }

    public static BusinessStatus fromDb(String value) {
        for (BusinessStatus status : values()) {
            if (status.display.equals(value)) return status;
        }
        throw new IllegalArgumentException("알 수 없는 영업상태: " + value);
    }
}
