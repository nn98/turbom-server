package com.nextstep.domain.site;

public final class LocationIdentity {

    private static final String UNLOCATED_KEY = "__unlocated__";

    private LocationIdentity() {
    }

    public static String key(String parseConfidence, String parsedBuildingName,
                              String parsedFloor, String parsedUnitNo) {
        if (AddressDetailParser.CONFIDENCE_LOW.equals(parseConfidence)) {
            return parsedBuildingName != null ? normalize(parsedBuildingName) : UNLOCATED_KEY;
        }
        if (parsedUnitNo != null) {
            return "UNIT::" + parsedUnitNo + "::" + parsedBuildingName;
        }
        if (parsedFloor != null) {
            return "FLOOR::" + parsedFloor + "::" + parsedBuildingName;
        }
        if (parsedBuildingName != null) {
            return normalize(parsedBuildingName);
        }
        return UNLOCATED_KEY;
    }

    public static boolean isUnlocated(String key) {
        return UNLOCATED_KEY.equals(key);
    }

    private static String normalize(String value) {
        return value.trim().replaceAll("\\s+", " ");
    }
}
