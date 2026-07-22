package com.nextstep.domain.site;

import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Pattern;

/**
 * 검색어를 공백 기준으로 토큰화해서, 순서·인접 여부와 무관하게 모든 토큰이 주소 어딘가에
 * 나타나는지(AND) 판정한다. 기존에는 검색어 전체를 하나의 substring으로만 비교해서 토큰
 * 하나만 빠지거나 순서가 바뀌어도 매칭이 실패했다(2026-07-22 프론트 리포트).
 */
public final class AddressQuery {

    // ponytail: 한국 지번주소는 보통 5토큰 이내(시/도·시/군/구·동·번지·건물명). 상한을 안 둬도
    // AND 조건 추가일 뿐이라 토큰이 많아져도 정확성·성능 문제 없음.
    private static final Pattern WHITESPACE = Pattern.compile("\\s+");
    private static final Pattern DIGITS_ONLY = Pattern.compile("\\d+");

    private final List<String> tokens;

    private AddressQuery(List<String> tokens) {
        this.tokens = tokens;
    }

    public static AddressQuery of(String rawQuery) {
        List<String> tokens = Arrays.stream(WHITESPACE.split(rawQuery.trim()))
            .filter(t -> !t.isBlank())
            .toList();
        return new AddressQuery(tokens);
    }

    /**
     * DB 사전 필터용 — 토큰 중 가장 긴 것. 전체 토큰이 AND로 매칭되는 행이라면 이 토큰도
     * 반드시 매칭되므로(필요조건), 이 토큰만으로 후보를 좁혀도 진짜 결과가 빠지지 않는다.
     */
    public String anchorToken() {
        return tokens.stream().max(Comparator.comparingInt(String::length)).orElse("");
    }

    /**
     * 모든 토큰이 jibunAddress 또는 roadAddress 어딘가에 나타나는지(순서 무관, AND).
     * 숫자만인 토큰은 앞뒤에 다른 숫자가 붙어있지 않아야 매칭(예: "534" 검색 시 "534-1"의
     * "534"는 매칭하되 "1534"·"5340"은 제외) — 층/호수 등 무관한 숫자에 걸리는 걸 줄인다.
     */
    public boolean matchesAll(String jibunAddress, String roadAddress) {
        String jibun = jibunAddress == null ? "" : jibunAddress;
        String road = roadAddress == null ? "" : roadAddress;
        for (String token : tokens) {
            if (!matches(token, jibun) && !matches(token, road)) {
                return false;
            }
        }
        return true;
    }

    private boolean matches(String token, String text) {
        if (DIGITS_ONLY.matcher(token).matches()) {
            return Pattern.compile("(?<!\\d)" + Pattern.quote(token) + "(?!\\d)").matcher(text).find();
        }
        return text.toLowerCase().contains(token.toLowerCase());
    }
}
