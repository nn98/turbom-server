package com.nextstep.domain.unit;

public enum LocationSource {
    LICENSE("license"),
    SANGGA_API("sangga_api"),
    OVERLAP_INFERRED("overlap_inferred");

    private final String dbValue;

    LocationSource(String dbValue) {
        this.dbValue = dbValue;
    }

    public String dbValue() {
        return dbValue;
    }

    public static LocationSource fromDb(String value) {
        for (LocationSource source : values()) {
            if (source.dbValue.equals(value)) return source;
        }
        throw new IllegalArgumentException("알 수 없는 locationSource: " + value);
    }
}
