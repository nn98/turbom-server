# 물건(Unit) 중복 제거 — 상세주소 정규화 설계

**Date:** 2026-07-16  
**Status:** Approved

---

## 문제

같은 PNU(필지) 안에서 동일한 물리적 공간이 상세주소 표현 방식 차이로 복수의 Unit으로 나타난다.

예시 (동일 PNU, 동일 공간):
- `"창곡동 543"` (상세 미입력)
- `"창곡동 543 1층 101호"` (상세 입력)
- `"창곡동 543 1 층 101 호"` (띄어쓰기 변형)

현재 `unitKey = normalize(jibunAddress)` 기준이므로 위 세 레코드가 각각 별도 Unit으로 집계된다.

---

## 범위

| 항목 | 포함 여부 |
|---|---|
| Unit 수준 상세주소 정규화 | ✅ |
| Site 수준 cross-PNU 중복 (좌표 없는 일부) | ❌ 별도 이슈 |
| Repository / API 계약 변경 | ❌ |

---

## 핵심 개념

### 건물(Site) 기준
**PNU = 본번 + 부번** — 변경 없음. 지번이 다르면 다른 건물.

### 물건(Unit) 기준
**파싱된 위치 속성 (층 + 호 + 건물명)** — 도로명주소에서 추출한 구조화 값 기준.  
jibunAddress 문자열 표현이 달라도 같은 위치면 하나의 Unit.

### 상세주소 불명 처리
층/호/건물명이 전혀 추출되지 않는 레코드 → **"단일(상세주소불명)"** Unit으로 표시.  
한 필지 안에서 상세 미입력 레코드가 여럿 있으면 모두 이 Unit 하나로 합산.

---

## 설계

### Unit 키 (`unitKey`) 변경

```
기존: normalize(jibunAddress)
변경: parseConfidence 기준 분기
```

**HIGH confidence (NONE / REGEX 메서드):**
```
key = parsedFloor + "::" + parsedUnitNo + "::" + parsedBuildingName
```
- 상세 없음 (NONE): `"null::null::null"` → 단일(상세주소불명) Unit
- 상세 파싱 성공 (REGEX): `"1::101::null"`, `"B::null::현대빌딩"` 등

**LOW confidence (UNPARSED 메서드):**
```
key = normalize(extractDetail(jibunAddress))
```
- 파싱 불가한 상세 문자열만 추출해 키로 사용 (lot 번지 제외)
- 동일 문자열이면 같은 Unit으로 합산

### Unit 라벨 (`unitLabel`) 변경

| 케이스 | 현재 | 변경 후 |
|---|---|---|
| parsedFloor + parsedUnitNo 있음 | "1층 101호" | 동일 |
| parsedFloor만 | "1층" | 동일 |
| parsedBuildingName만 | buildingName | 동일 |
| 전부 null (HIGH, NONE) | "단일 점포" | **"단일(상세주소불명)"** |
| LOW confidence | "단일 점포" | 동일 |

---

## 변경 파일

| 파일 | 변경 내용 |
|---|---|
| `TenancyQueryService.java` | `unitKey()` 로직 교체, `unitLabel()` 텍스트 수정 |
| `TenancyQueryServiceTest.java` (신규) | 상세주소 변형 레코드 → 1 Unit 집계 검증 |

---

## 테스트 시나리오

1. **정규화 합산**: 동일 PNU에 `"창곡동 543"`, `"창곡동 543 1층 101호"` 두 레코드 → Unit 수 = 2 (`null::null::null` 하나, `"1::101::null"` 하나)
2. **표현 변형 합산**: `"1층 101호"` / `"1 층 101 호"` / `"1층101호"` → 동일 parsedFloor/parsedUnitNo이면 Unit 수 = 1
3. **상세주소불명 합산**: 상세 없는 레코드 3개 → Unit 1개, 라벨 `"단일(상세주소불명)"`
4. **구분 유지**: `"1층 101호"`와 `"2층 201호"` → Unit 2개
5. **LOW confidence**: UNPARSED 레코드 두 개가 동일 상세 문자열 → Unit 1개

---

## 프론트엔드 영향

API 응답 구조 변경 없음. `label` 필드 문자열이 `"단일 점포"` → `"단일(상세주소불명)"` 으로 바뀌는 것만 확인 필요.
