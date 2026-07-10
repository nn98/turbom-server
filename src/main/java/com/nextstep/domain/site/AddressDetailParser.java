package com.nextstep.domain.site;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class AddressDetailParser {

    public static final String CONFIDENCE_HIGH = "HIGH";
    public static final String CONFIDENCE_LOW = "LOW";

    public static final String METHOD_REGEX = "REGEX";
    public static final String METHOD_UNPARSED = "UNPARSED";
    public static final String METHOD_NONE = "NONE";

    private static final Pattern FLOOR_PATTERN =
        Pattern.compile("(지하)?\\s*(\\d+)\\s*(?:\\(?일부\\)?\\s*)?층");
    private static final Pattern UNIT_PATTERN =
        Pattern.compile("(?i)([Bb])?\\s*(\\d+(?:-\\d+)?)\\s*(?:\\(?일부\\)?\\s*)?호");
    private static final Pattern QUALIFIER_NOISE_PATTERN =
        Pattern.compile("(\\(일부\\)|일부)\\s*[호층]?");
    private static final Pattern STRAY_PUNCTUATION_PATTERN = Pattern.compile("[(),\\-]");
    private static final Pattern DIGIT_PATTERN = Pattern.compile("\\d");

    private AddressDetailParser() {
    }

    public record Result(String buildingName, String floor, String unitNo, String confidence, String method) {

        public static Result none() {
            return new Result(null, null, null, CONFIDENCE_HIGH, METHOD_NONE);
        }

        public static Result unparsed(String rawDetail) {
            return new Result(rawDetail, null, null, CONFIDENCE_LOW, METHOD_UNPARSED);
        }
    }

    public static Result parse(String roadAddress) {
        String detail = extractDetail(roadAddress);
        if (detail.isBlank()) {
            return Result.none();
        }

        String working = detail;

        String floor = null;
        Matcher floorMatcher = FLOOR_PATTERN.matcher(working);
        if (floorMatcher.find()) {
            boolean basement = floorMatcher.group(1) != null;
            floor = (basement ? "B" : "") + floorMatcher.group(2);
            working = working.substring(0, floorMatcher.start()) + " " + working.substring(floorMatcher.end());
        }

        String unitNo = null;
        Matcher unitMatcher = UNIT_PATTERN.matcher(working);
        if (unitMatcher.find()) {
            boolean basement = unitMatcher.group(1) != null;
            unitNo = (basement ? "B" : "") + unitMatcher.group(2);
            working = working.substring(0, unitMatcher.start()) + " " + working.substring(unitMatcher.end());
        }

        working = QUALIFIER_NOISE_PATTERN.matcher(working).replaceAll(" ");
        working = STRAY_PUNCTUATION_PATTERN.matcher(working).replaceAll(" ");
        working = working.trim().replaceAll("\\s+", " ");

        if (DIGIT_PATTERN.matcher(working).find()) {
            return Result.unparsed(detail);
        }

        String buildingName = working.isBlank() ? null : working;
        if (floor == null && unitNo == null && buildingName == null) {
            return Result.unparsed(detail);
        }
        return new Result(buildingName, floor, unitNo, CONFIDENCE_HIGH, METHOD_REGEX);
    }

    static String extractDetail(String roadAddress) {
        if (roadAddress == null) return "";
        // The trailing "(...)" holds the legal-dong name and may itself contain a comma
        // (e.g. "(정자동, 현대빌딩)"), so only a comma before that parenthesis counts as
        // the road-address / detail-address separator.
        int lastParen = roadAddress.lastIndexOf('(');
        String beforeParen = lastParen >= 0 ? roadAddress.substring(0, lastParen) : roadAddress;
        int commaIndex = beforeParen.indexOf(',');
        if (commaIndex < 0) return "";
        return beforeParen.substring(commaIndex + 1).trim();
    }
}
