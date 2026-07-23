package com.nextstep.application;

import com.nextstep.domain.businesstype.BusinessTypeKey;
import com.nextstep.domain.businesstype.BusinessTypeRegistry;
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

    private final BusinessTypeRegistry registry;

    public UnitGrouper() {
        this(new BusinessTypeRegistry());
    }

    public UnitGrouper(BusinessTypeRegistry registry) {
        this.registry = registry;
    }

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
        return separateByMustSeparateEdges(group).stream();
    }

    // 3자 이상이 겹칠 때 관련업종쌍(예: 집단급식소/위탁급식영업)만 예외 처리하던 isContended가
    // 그룹 전체를 all-or-nothing으로 재분리 대상 삼던 문제 수정 — 서로 충돌(겹침 AND 비관련쌍)
    // 하는 상호명끼리만 분리하고, 충돌 없는 상호명(관련쌍 포함)은 하나로 유지한다.
    // Union-Find: 충돌 없는 이름끼리 묶고, 충돌 있는 이름은 각자 다른 그룹으로 남긴다.
    private List<List<LicensedBusinessRecordEntity>> separateByMustSeparateEdges(
        List<LicensedBusinessRecordEntity> group
    ) {
        Map<String, List<LicensedBusinessRecordEntity>> byName = group.stream()
            .collect(Collectors.groupingBy(LicensedBusinessRecordEntity::getBusinessName, LinkedHashMap::new, Collectors.toList()));
        List<String> names = new ArrayList<>(byName.keySet());

        int[] parent = new int[names.size()];
        for (int i = 0; i < parent.length; i++) parent[i] = i;
        for (int i = 0; i < names.size(); i++) {
            for (int j = i + 1; j < names.size(); j++) {
                List<LicensedBusinessRecordEntity> pair = new ArrayList<>(byName.get(names.get(i)));
                pair.addAll(byName.get(names.get(j)));
                if (!isContended(pair)) union(parent, i, j);
            }
        }

        Map<Integer, List<String>> namesByComponent = new LinkedHashMap<>();
        for (int i = 0; i < names.size(); i++) {
            namesByComponent.computeIfAbsent(find(parent, i), k -> new ArrayList<>()).add(names.get(i));
        }

        List<List<LicensedBusinessRecordEntity>> result = new ArrayList<>();
        for (List<String> componentNames : namesByComponent.values()) {
            List<LicensedBusinessRecordEntity> componentRecords = group.stream()
                .filter(r -> componentNames.contains(r.getBusinessName()))
                .toList();
            if (isContended(componentRecords)) {
                // 이행적 병합의 부작용(A-B 무충돌, B-C 무충돌인데 A-C는 충돌)으로 한 컴포넌트에
                // 충돌이 남은 드문 경우만 기존 tier2(주소텍스트)/tier3(상호명) 캐스케이드로 처리
                result.addAll(resolveViaAddressAndNameCascade(componentRecords));
            } else {
                result.add(componentRecords);
            }
        }
        return result;
    }

    private List<List<LicensedBusinessRecordEntity>> resolveViaAddressAndNameCascade(
        List<LicensedBusinessRecordEntity> group
    ) {
        List<List<LicensedBusinessRecordEntity>> byAddressText = groupByKey(group, this::addressTextKey);
        return byAddressText.stream()
            .flatMap(subGroup -> isContended(subGroup)
                ? groupByKey(subGroup, LicensedBusinessRecordEntity::getBusinessName).stream()
                : Stream.of(subGroup))
            .toList();
    }

    private int find(int[] parent, int i) {
        while (parent[i] != i) {
            parent[i] = parent[parent[i]];
            i = parent[i];
        }
        return i;
    }

    private void union(int[] parent, int a, int b) {
        int rootA = find(parent, a);
        int rootB = find(parent, b);
        if (rootA != rootB) parent[rootA] = rootB;
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

        List<List<LicensedBusinessRecordEntity>> recordsByName = new ArrayList<>(byBusinessName.values());
        List<List<OccupancySpan>> spansByName = byBusinessName.entrySet().stream()
            .map(entry -> occupancySpans(entry.getKey(), entry.getValue()))
            .toList();

        for (int i = 0; i < spansByName.size(); i++) {
            for (int j = i + 1; j < spansByName.size(); j++) {
                // 집단급식소/위탁급식영업처럼 등록된 관련 업종쌍은 같은 물건에서 서로 다른
                // 상호명으로 겹쳐도 충돌이 아니라 정상적인 페어링 — RelatedLicenseLinker가
                // 같은 Unit 안에서 묶을 수 있도록 재분리 대상에서 제외
                if (areRelatedTypes(recordsByName.get(i).get(0), recordsByName.get(j).get(0))) continue;
                for (OccupancySpan a : spansByName.get(i)) {
                    for (OccupancySpan b : spansByName.get(j)) {
                        if (a.overlaps(b)) return true;
                    }
                }
            }
        }
        return false;
    }

    private boolean areRelatedTypes(LicensedBusinessRecordEntity a, LicensedBusinessRecordEntity b) {
        BusinessTypeKey keyB = new BusinessTypeKey(b.getCategory(), b.getSubCategory());
        return registry.lookup(a.getCategory(), a.getSubCategory()).relatedTypeKeys().pairsWith(keyB);
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
