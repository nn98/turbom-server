package com.nextstep.application;

import com.nextstep.domain.site.LocationIdentity;
import com.nextstep.domain.unit.OccupancySpan;
import com.nextstep.infra.persistence.LicensedBusinessRecordEntity;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class UnitGrouper {

    private static final String UNIT_ID_SEPARATOR = "-U";
    // ponytail: 초기 추정값. 실사례로 오판(과병합/과분리) 나오면 조정(TenancyMerger와 값을 맞춘다)
    private static final int SAME_BUSINESS_MERGE_GAP_DAYS = 90;

    public record UnitGroup(String unitId, List<LicensedBusinessRecordEntity> records) {
    }

    public record Grouping(List<UnitGroup> unitGroups, List<LicensedBusinessRecordEntity> unlocated) {
    }

    public Grouping group(String pnu, List<LicensedBusinessRecordEntity> storefrontRecords) {
        List<LicensedBusinessRecordEntity> located = new ArrayList<>();
        List<LicensedBusinessRecordEntity> unlocated = new ArrayList<>();
        for (LicensedBusinessRecordEntity record : storefrontRecords) {
            String key = keyOf(record);
            (LocationIdentity.isUnlocated(key) ? unlocated : located).add(record);
        }

        List<List<LicensedBusinessRecordEntity>> primaryGroups = groupByKey(located, this::keyOf);
        List<List<LicensedBusinessRecordEntity>> resolvedGroups = primaryGroups.stream()
            .flatMap(this::resolveContention)
            .toList();

        List<UnitGroup> groups = new ArrayList<>();
        int index = 1;
        for (List<LicensedBusinessRecordEntity> records : resolvedGroups) {
            groups.add(new UnitGroup(pnu + UNIT_ID_SEPARATOR + index, records));
            index++;
        }
        return new Grouping(groups, unlocated);
    }

    private String keyOf(LicensedBusinessRecordEntity record) {
        return LocationIdentity.key(record.getParseConfidence(), record.getParsedBuildingName(),
            record.getParsedFloor(), record.getParsedUnitNo());
    }

    private Stream<List<LicensedBusinessRecordEntity>> resolveContention(List<LicensedBusinessRecordEntity> group) {
        // 구체적 호실번호(UNIT:: 키)가 있는 그룹은 겹쳐도 그대로 둔다 — 실측 결과 실제 문제
        // 사례(가락시장/AK플라자/백현동/롯데백화점)는 전부 호실번호 없는 케이스였고, 있는데
        // 겹치는 경우는 대부분 폐업신고 누락으로 보는 게 더 합리적(기존 회귀 테스트도 이 전제).
        if (keyOf(group.get(0)).startsWith("UNIT::") || !isContended(group)) {
            return Stream.of(group);
        }
        List<List<LicensedBusinessRecordEntity>> byAddressText = groupByKey(group, this::addressTextKey);
        return byAddressText.stream().flatMap(subGroup -> isContended(subGroup)
            ? groupByKey(subGroup, LicensedBusinessRecordEntity::getBusinessName).stream()
            : Stream.of(subGroup));
    }

    private List<List<LicensedBusinessRecordEntity>> groupByKey(
        List<LicensedBusinessRecordEntity> records, Function<LicensedBusinessRecordEntity, String> keyFn
    ) {
        Map<String, List<LicensedBusinessRecordEntity>> byKey = records.stream()
            .collect(Collectors.groupingBy(keyFn, LinkedHashMap::new, Collectors.toList()));
        return new ArrayList<>(byKey.values());
    }

    private boolean isContended(List<LicensedBusinessRecordEntity> records) {
        Map<String, List<LicensedBusinessRecordEntity>> byBusinessName = records.stream()
            .collect(Collectors.groupingBy(LicensedBusinessRecordEntity::getBusinessName, LinkedHashMap::new, Collectors.toList()));
        if (byBusinessName.size() < 2) return false;

        List<List<OccupancySpan>> spansByName = byBusinessName.entrySet().stream()
            .map(entry -> occupancySpans(entry.getKey(), entry.getValue()))
            .toList();

        for (int i = 0; i < spansByName.size(); i++) {
            for (int j = i + 1; j < spansByName.size(); j++) {
                for (OccupancySpan a : spansByName.get(i)) {
                    for (OccupancySpan b : spansByName.get(j)) {
                        if (a.overlaps(b)) return true;
                    }
                }
            }
        }
        return false;
    }

    private List<OccupancySpan> occupancySpans(String businessName, List<LicensedBusinessRecordEntity> records) {
        return mergeByGap(records).stream()
            .map(stint -> {
                LocalDate start = stint.stream()
                    .map(LicensedBusinessRecordEntity::getLicensedAt)
                    .min(LocalDate::compareTo)
                    .orElseThrow();
                boolean anyOpen = stint.stream().anyMatch(r -> r.getClosedAt() == null);
                LocalDate end = anyOpen ? null : stint.stream()
                    .map(LicensedBusinessRecordEntity::getClosedAt)
                    .max(LocalDate::compareTo)
                    .orElseThrow();
                return new OccupancySpan(businessName, start, end);
            })
            .toList();
    }

    private List<List<LicensedBusinessRecordEntity>> mergeByGap(List<LicensedBusinessRecordEntity> records) {
        List<LicensedBusinessRecordEntity> sorted = records.stream()
            .sorted((a, b) -> a.getLicensedAt().compareTo(b.getLicensedAt()))
            .toList();

        List<List<LicensedBusinessRecordEntity>> groups = new ArrayList<>();
        List<LicensedBusinessRecordEntity> current = new ArrayList<>();
        LocalDate currentEnd = null;
        boolean currentOpen = false;

        for (LicensedBusinessRecordEntity record : sorted) {
            boolean withinGap = current.isEmpty()
                || currentOpen
                || !record.getLicensedAt().isAfter(currentEnd.plusDays(SAME_BUSINESS_MERGE_GAP_DAYS));

            if (!withinGap) {
                groups.add(current);
                current = new ArrayList<>();
                currentOpen = false;
                currentEnd = null;
            }
            current.add(record);
            if (record.getClosedAt() == null) {
                currentOpen = true;
                currentEnd = null;
            } else if (!currentOpen && (currentEnd == null || record.getClosedAt().isAfter(currentEnd))) {
                currentEnd = record.getClosedAt();
            }
        }
        if (!current.isEmpty()) groups.add(current);
        return groups;
    }

    private String addressTextKey(LicensedBusinessRecordEntity record) {
        return normalize(record.getJibunAddress()) + "::" + normalize(record.getRoadAddress());
    }

    private String normalize(String value) {
        if (value == null) return "";
        return value.trim().replaceAll("\\s+", " ");
    }
}
