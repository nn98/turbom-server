# 동시 영업 중 다른 상호 뭉침 방지 — Unit 그룹핑에 충돌 감지/재분리 추가

## 배경

`TenancyQueryService.unitKey()`는 각 인허가 레코드를 파싱된 층/호(`parsedFloor`/`parsedUnitNo`,
HIGH 신뢰도) 또는 원본 상세주소 텍스트(LOW 신뢰도)로만 그룹핑해서 하나의 `Unit`을 만든다. 이
키가 여러 레코드에서 같거나 비어있으면 전부 한 `Unit`으로 합쳐진다 — 정상적인 한 점포가 세월에
따라 임차인이 바뀌는(순차) 흔한 경우엔 정확히 맞는 동작이다.

문제는 **여러 개의 서로 다른 사업자가 같은 시기에 동시에 영업 중**인데도 이 키가 구분을 못 하는
경우다. 실사례를 배포 DB에서 직접 확인:

| PNU / 실제 성격 | 그룹 키 | 서로 다른 상호 수 | 현재 영업중 동시 개수 |
|---|---|---|---|
| 가락동 600(가락시장, 도매시장) | 층/호 없음 | 700+ | 690 |
| 성남 서현동 AK플라자 지하1층 "일부호" | B1/(unitNo 없음) | 430 | 31 |
| 잠실 롯데백화점 지층 | 층/호 없음 | 2,645 | 444 |
| 성남 백현동 541 지하1층 | B1/(unitNo 없음) | 1,221 | 84 |

한 물리적 공간에 두 사업자가 동시에 있을 수 없으므로, "같은 그룹 안에 다른 상호가 겹치는 기간
동안 둘 다 영업 중이었다"는 사실 자체가 "이 그룹은 실제로는 여러 개의 서로 다른 자리인데 주소
파싱 신호가 부족해서 잘못 합쳐졌다"는 확실한 증거다.

같은 상호가 재인허가를 낸 경우(같은 사업자가 다른 업종으로 추가 신고하는 경우 등)는 이미
`business_name` 정확 일치로 하나로 병합되고 있어(`mergedTenancies`) 문제가 없다 — 실측으로도
같은 사업자의 추가 인허가는 상호명이 그대로 재사용되는 게 일반적임을 확인했다(가락시장 샘플
확인). 그래서 "비슷하지만 완전히 같지는 않은 이름"을 동일 사업자로 판단하는 퍼지 유사도 매칭은
**만들지 않는다** — 지금은 근거 없는 추측이고, 잘못하면 실제로 다른 두 사업자를 억지로 합치는
새 버그를 만든다. 완전 일치가 아닌데 같은 사업자로 보이는 실사례가 나오면 그때 보강.

## 목표

- 같은 그룹 안에서 서로 다른 상호가 겹치는 기간 동안 동시에 영업 중이었던 경우를 감지하고,
  이를 별도 `Unit`으로 재분리한다.
- 정상 케이스(겹침 없음)는 지금과 완전히 동일하게 동작한다 — 회귀 없음.
- 재분리는 **가장 신뢰할 수 있는 신호부터 순서대로 시도**한다: 구조화된 층/호 → 원본
  주소 텍스트(지번/도로명) → 상호명(최후 수단). 상호명으로 바로 건너뛰지 않는 이유는
  가락시장 실측에서 지번/도로명 원문 자체는 레코드마다 상당히 다르다는 게 확인됐기 때문
  (해당 PNU 기준 지번주소 738종, 도로명주소 1029종 — 구조화 파싱만 실패했을 뿐 원문엔 구분
  정보가 남아있는 경우가 많음). 원문 텍스트로도 구분이 안 되는 완전히 동일한 경우(AK플라자
  "일부호" 반복 등)만 상호명까지 내려간다.

## 비목표

- `BusinessType` 도메인 재설계(15-task 계획, `docs/superpowers/plans/2026-07-20-business-type-domain.md`)와의
  통합. 이번 수정은 `TenancyQueryService`의 그룹핑 알고리즘만 독립적으로 고친다. 통합은 필요해지면
  나중에.
- 상호명 유사도(퍼지 매칭)로 "다른 이름·같은 사업자"를 추정하는 기능. 근거 없이 만들지 않음(위
  배경 참고).
- `NoStorefrontSubCategories`(무점포 업종 사전 제외) 로직 변경. 이미 잘 동작 중 — 통신판매업 등은
  이 수정이 다루는 `unitGroups()`에 도달하기 전에 이미 걸러진다.
- 층/호 파싱 자체(`AddressDetailParser`)의 정교화. 이번 수정은 파싱 결과를 있는 그대로 쓰고,
  파싱이 실패해도 원문 텍스트 fallback으로 대응한다.
- 과거 배치 데이터 재정제(예: `parse_confidence`가 NULL인 일부 서울 레코드 — 조사 중 발견했지만
  이번 스코프 밖, 별도 이슈로 기록만).

## 설계

### 겹침 판정 — `OccupancySpan` (신규, `domain.unit`, JPA 비의존)

```java
package com.nextstep.domain.unit;

import java.time.LocalDate;

public record OccupancySpan(String ownerKey, LocalDate start, LocalDate endOrNull) {

    public boolean overlaps(OccupancySpan other) {
        LocalDate thisEnd = endOrNull == null ? LocalDate.MAX : endOrNull;
        LocalDate otherEnd = other.endOrNull == null ? LocalDate.MAX : other.endOrNull;
        // 경계가 닿기만 하는 경우(당일 인수인계: A 폐업일 == B 개업일)는 겹침이 아니다 — 실제
        // 동시 운영이 아니라 흔한 정상적 승계 패턴이라 엄격한 부등호(<)로 배제한다.
        return start.isBefore(otherEnd) && other.start.isBefore(thisEnd);
    }
}
```

`ownerKey`는 상호명(business_name)을 담아, 겹침이 "다른 상호끼리"인지 판정할 때 쓴다(같은
상호의 두 span은 겹쳐도 문제 아님 — 예: 같은 사업자가 겹치는 기간에 업종 두 개를 신고한 경우).

### 그룹 충돌 판정 + 재분리 — `TenancyQueryService.unitGroups()` 확장

현재:
```java
private List<UnitGroup> unitGroups(String pnu, List<LicensedBusinessRecordEntity> records) {
    Map<String, List<LicensedBusinessRecordEntity>> recordsByAddress = records.stream()
        .collect(Collectors.groupingBy(this::unitKey, LinkedHashMap::new, Collectors.toList()));
    // ... UnitGroup 생성
}
```

변경 후 흐름 (그룹 하나당):

1. `unitKey()`로 1차 그룹핑(변경 없음).
2. 각 1차 그룹에 대해, 상호명별로 `OccupancySpan`을 만든다 — 같은 상호의 레코드는 기존
   `mergeByGap()`으로 이미 만드는 "재직 구간"을 그대로 재사용(중복 계산 없음), 그 구간의
   시작/종료로 span 하나씩.
3. 그룹 내 span들을 상호명이 다른 것끼리만 pairwise로 겹침 검사. 하나라도 겹치면 그 그룹은
   "충돌"로 표시.
4. 충돌 그룹만 2차 키로 재그룹핑: `normalize(jibunAddress) + "::" + normalize(roadAddress)`.
   충돌이 없던 그룹은 전혀 손대지 않는다(회귀 위험 최소화).
5. 2차 재그룹핑 결과 각각에 대해 3번 겹침 검사를 다시 수행. 여전히 충돌인 서브그룹만 3차 키
   (`business_name`)로 최종 재그룹핑 — 상호명이 곧 키이므로 이 단계는 항상 충돌이 해소된다
   (같은 상호끼리는 다시 하나로 모이고, 다른 상호는 무조건 분리됨).

의사코드:
```java
private List<UnitGroup> unitGroups(String pnu, List<LicensedBusinessRecordEntity> records) {
    List<UnitGroup> primary = groupBy(records, this::unitKey, pnu);
    return primary.stream()
        .flatMap(group -> isContended(group.records())
            ? resplit(pnu, group.records())
            : Stream.of(group))
        .toList(); // unitId는 최종 목록 순서로 다시 부여
}

private Stream<UnitGroup> resplit(String pnu, List<LicensedBusinessRecordEntity> records) {
    List<UnitGroup> byAddressText = groupBy(records, this::addressTextKey, pnu);
    return byAddressText.stream().flatMap(g -> isContended(g.records())
        ? groupBy(g.records(), LicensedBusinessRecordEntity::getBusinessName, pnu).stream()
        : Stream.of(g));
}
```

(정확한 unitId 재부여·기존 헬퍼와의 통합은 구현 단계에서 확정)

### 영향 범위

- `TenancyQueryService.unitGroups()`/`unitKey()` 주변만 수정. `Unit`/`Tenancy` 도메인 record,
  API DTO, 컨트롤러는 변경 없음 — Unit 개수만 늘어나고(충돌 그룹에서만) 각 Unit의 필드 구조는
  그대로.
- `unitId` 부여 순서가 바뀔 수 있어(충돌 그룹이 여러 개로 늘어남) 기존에 발급된 `unitId` 값이
  일부 재배치될 수 있다 — 프론트가 `unitId`를 영구 식별자로 캐싱하지 않는다는 전제 확인 필요
  (기존에도 인허가 데이터가 갱신될 때마다 순서가 바뀔 수 있는 구조라 새로운 제약은 아님).

## 검증 계획

`TenancyQueryServiceTest`에 시나리오 추가:

1. **회귀**: 겹침 없는 정상 순차 임차(기존 픽스처) → Unit 개수·구성 변화 없음.
2. **2차 키로 해소**: 같은 PNU, 같은 층("B1")·unitNo 없음, 서로 다른 jibunAddress 텍스트를 가진
   두 상호가 겹치는 기간 영업 → 2개 Unit으로 분리, 각각 원문 주소 기준으로 묶임.
3. **3차 키까지 내려감**: 위와 동일하지만 jibunAddress/roadAddress까지 완전히 동일한 두 상호가
   겹치는 기간 영업(가락시장/AK플라자 재현) → 2개 Unit으로 분리, 각각 상호명 기준.
4. **당일 인수인계는 겹침 아님**: A 폐업일 == B 개업일인 두 상호 → 겹침 미판정, 기존처럼 1개
   그룹으로 유지(2차/3차로 안 내려감).
5. **같은 상호의 겹치는 두 업종신고는 겹침 아님**: 같은 business_name의 두 레코드가 겹치는 기간
   존재 → 애초에 pairwise 비교 대상이 아니므로(같은 상호끼리는 비교 안 함) 문제 없음.

`OccupancySpan`은 순수 로직이라 별도 단위 테스트(`OccupancySpanTest`)로 겹침/경계 케이스만
빠르게 검증.

## 문서화 계획 (구현 완료 후)

- `backend-spec.md`: `TenancyQueryService`의 Unit 그룹핑 절에 충돌 감지/재분리 단계 추가.
- `의사결정-기록.md`: 새 절 추가 — 이 설계의 배경(가락시장/AK플라자/롯데백화점 실측)과 "퍼지
  유사도 매칭은 근거 부족으로 보류" 판단 근거 기록.
- `CHANGELOG.md`: 번호 매겨 추가.
- `api-spec.md`: 응답 스키마 자체는 안 바뀌지만(Unit 필드 구조 동일), 대형 상가/시장 PNU의
  `unitCount`가 이전보다 크게 늘어날 수 있다는 점을 후보 응답 설명에 한 줄 추가할지는 구현 후
  실제 변화량 보고 결정.
