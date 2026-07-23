package com.nextstep.application;

import org.junit.jupiter.api.Test;
import java.time.LocalDate;
import java.util.List;
import static org.assertj.core.api.Assertions.assertThat;

class UnitGrouperTest {

    private final UnitGrouper grouper = new UnitGrouper();

    @Test
    void 같은_호실은_하나의_유닛으로_묶인다() {
        var r1 = TestFixtures.record(1L, "건강", "병원", "가게A", "우성트램타워", null, "801", "HIGH");
        var r2 = TestFixtures.record(2L, "건강", "병원", "가게B", "우성트램타워", null, "801", "HIGH");

        var grouping = grouper.group("pnu-1", List.of(r1, r2));

        assertThat(grouping.unitGroups()).hasSize(1);
        assertThat(grouping.unitGroups().get(0).unitId()).isEqualTo("pnu-1-U1");
        assertThat(grouping.unitGroups().get(0).records()).containsExactly(r1, r2);
        assertThat(grouping.unlocated()).isEmpty();
    }

    @Test
    void 다른_호실은_다른_유닛으로_분리된다() {
        var r1 = TestFixtures.record(1L, "건강", "병원", "가게A", "우성트램타워", null, "801", "HIGH");
        var r2 = TestFixtures.record(2L, "건강", "병원", "가게B", "우성트램타워", null, "802", "HIGH");

        var grouping = grouper.group("pnu-1", List.of(r1, r2));

        assertThat(grouping.unitGroups()).hasSize(2);
    }

    @Test
    void 상세주소가_전부_없으면_유닛이_아니라_UNLOCATED로_빠진다() {
        var r1 = TestFixtures.record(1L, "기타", "담배소매업", "씨유매장", null, null, null, "HIGH");
        var r2 = TestFixtures.record(2L, "건강", "병원", "정상매장", "우성트램타워", null, "801", "HIGH");

        var grouping = grouper.group("pnu-1", List.of(r1, r2));

        assertThat(grouping.unitGroups()).hasSize(1);
        assertThat(grouping.unitGroups().get(0).records()).containsExactly(r2);
        assertThat(grouping.unlocated()).containsExactly(r1);
    }

    // 2026-07-23 보정 — 아래 3개는 배포된 충돌감지/재분리 기능(의사결정-기록.md §16)을 이관하며 추가.
    // TestFixtures.record()는 모든 레코드에 동일한 jibunAddress를 쓰고 closedAt/roadAddress를
    // 세팅할 수 없어서(§8 원안 헬퍼), 이 시나리오 전용으로 TestFixtures.recordForOverlap(...)을
    // 새로 추가한다(기존 record()/Task 9가 추가하는 recordWithDates()는 그대로 둠 — 시그니처 안 건드림).

    @Test
    void 층만_겹치고_지번주소가_다르면_지번주소로_재분리된다() {
        var r1 = TestFixtures.recordForOverlap(1L, "식품", "즉석판매제조가공업", "가락족발A",
            null, "B1", null, "HIGH", LocalDate.of(2020, 1, 1), null,
            "경기도 성남시 수정구 테스트동 94-1 지하1층", "경기도 성남시 수정구 테스트로 7, 지하1층 일부호 (테스트동)");
        var r2 = TestFixtures.recordForOverlap(2L, "식품", "즉석판매제조가공업", "가락생선B",
            null, "B1", null, "HIGH", LocalDate.of(2020, 6, 1), null,
            "경기도 성남시 수정구 테스트동 94-2 지하1층", "경기도 성남시 수정구 테스트로 7, 지하1층 일부호 (테스트동)");

        var grouping = grouper.group("pnu-2", List.of(r1, r2));

        assertThat(grouping.unitGroups()).hasSize(2);
        assertThat(grouping.unlocated()).isEmpty();
    }

    @Test
    void 지번주소까지_같으면_상호명으로_재분리된다() {
        var r1 = TestFixtures.recordForOverlap(1L, "식품", "식품소분업", "가락상회C",
            null, "B1", null, "HIGH", LocalDate.of(2020, 1, 1), null,
            "경기도 성남시 수정구 테스트동 93 지하1층", "경기도 성남시 수정구 테스트로 8, 지하1층 일부호 (테스트동)");
        var r2 = TestFixtures.recordForOverlap(2L, "식품", "식품소분업", "가락상회D",
            null, "B1", null, "HIGH", LocalDate.of(2020, 6, 1), null,
            "경기도 성남시 수정구 테스트동 93 지하1층", "경기도 성남시 수정구 테스트로 8, 지하1층 일부호 (테스트동)");

        var grouping = grouper.group("pnu-3", List.of(r1, r2));

        assertThat(grouping.unitGroups()).hasSize(2);
    }

    @Test
    void 구체적_호실번호가_있으면_겹쳐도_분리하지_않는다() {
        // UNIT:: 키 그룹은 충돌감지 대상 제외 — 실제 문제 사례 전부 호실번호 없는 경우였고,
        // 있는데 겹치는 건 폐업신고 누락일 가능성이 높음(회귀: TenancyQueryServiceTest의
        // 같은_Unit이라도_businessName이_다르면_병합하지_않는다 와 동일 전제)
        var r1 = TestFixtures.recordForOverlap(1L, "서비스", "미용", "가게D-1",
            null, "4", "104", "HIGH", LocalDate.of(2020, 1, 1), null,
            "경기도 성남시 수정구 테스트동 96 4층 104호", "경기도 성남시 수정구 테스트로 4, 4층 104호 (테스트동)");
        var r2 = TestFixtures.recordForOverlap(2L, "서비스", "세탁", "가게D-2",
            null, "4", "104", "HIGH", LocalDate.of(2020, 6, 1), null,
            "경기도 성남시 수정구 테스트동 96 4층 104호", "경기도 성남시 수정구 테스트로 4, 4층 104호 (테스트동)");

        var grouping = grouper.group("pnu-4", List.of(r1, r2));

        assertThat(grouping.unitGroups()).hasSize(1);
        assertThat(grouping.unitGroups().get(0).records()).containsExactly(r1, r2);
    }
}
