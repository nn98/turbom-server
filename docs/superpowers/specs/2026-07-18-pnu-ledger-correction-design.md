# PNU 권위 파일 조인 정정 설계

**Date:** 2026-07-18
**Status:** Approved

---

## 문제

2026-07-18 서울/경기 전체 스코프 확장(195개 카테고리 파일 × 2지역, `data/경기도`+`data/서울특별시`) 작업 중, 검색 결과에 동일 자리가 서로 다른 `pnu`로 중복 노출되는 문제를 프론트(`turbom-client`) 세션이 실사례(금토동 534-8)로 리포트했다(`server/spec/CHANGELOG.md`에 미커밋 교차기록, `ter-view/docs/superpowers/specs/2026-07-18-site-entity-similarity-design.md` 참고).

원인을 실측한 결과, "유사 항목 매칭"이 아니라 **진짜 중복 행**이었다:

- `license_no='2000380000005607203'`가 **완전히 동일한 `jibun_address` 텍스트**("산" 표기 없음)로 두 번 적재됨 — `pnu` 끝에서 두 번째 자리(산여부)만 `0`/`1`로 다름.
- 우리 PNU 파서(`jibun_pnu.py`)는 주소 텍스트에 "산"이 **문자 그대로** 있어야만 산여부=1을 만든다 — 텍스트가 같은데 값이 갈렸다는 건, 두 행이 서로 다른 시점/로직으로 각각 한 번씩 중복 적재됐다는 뜻.
- 기존 93개 청크(9만1천여 건)만 스캔해도 **동일 `license_no`가 서로 다른 `pnu`를 가진 쌍이 1,630개** 확인됨. 금토동 534-8은 빙산의 일각.
- 근본 원인 추적: `data_uncleaning/PNU(지번)기반_개폐업정보현황_성남시_10년.csv`(49MB, 98,517행, `data_uncleaning/` 안에 있었지만 실제 적재 소스로 쓰인 적 없음)에 **관리번호별 PNU가 이미 정확히 계산돼** 있고, 이 파일의 PNU가 우리 legacy(사전 dedup 도입 전) 청크의 값과 일치한다 — 즉 이 권위 파일이 과거 한 번 쓰였다가 이후 세션에서 잊힌 것으로 보인다.
- 이 권위 파일은 우리 정규식(`jibun_pnu.parse_pnu`)이 `UNPARSEABLE_OR_DONG_NOT_FOUND`로 포기하는 케이스도 PNU를 갖고 있어(정규식과 무관하게 사전 계산됨), **커버리지 개선** 효과도 있다.
- 단, 이 권위 파일엔 **`폐업일자` 컬럼이 없다**(36개 컬럼 전수 확인). 개업–폐업 이력이 이 앱의 존재 이유(CLAUDE.md 한 줄 정의)라 이 파일로 전면 대체는 불가 — `license_no`로 조인해 PNU만 가져오고 `closed_at`은 계속 LOCALDATA(`data/경기도`)에서 온다.

## 범위

| 항목 | 포함 여부 |
|---|---|
| `license_no → PNU` 권위 조인 레이어 신규 구현 | ✅ |
| 정규식 실패(`UNPARSEABLE_OR_DONG_NOT_FOUND`) 행 중 권위 파일이 커버하는 만큼 복구 | ✅ |
| 기존 적재분의 `license_no` 중복(다른 `pnu`) 쌍 정리(권위 PNU로 병합) | ✅ |
| `closed_at`을 권위 파일에서 가져오기 | ❌ 컬럼 자체 없음, 계속 LOCALDATA 소스 |
| 서울/성남 외 경기도 지역에 권위 파일 적용 | ❌ 이 파일은 성남시 전용 — 다른 지역은 기존 정규식 로직 그대로 |
| `호실분리여부` 등 권위 파일의 나머지 컬럼 활용 | ❌ 98,517건 중 4건만 `Y`라 사실상 무의미, 이번 스코프 제외 |
| 이번에 이미 완료한 서울/경기 스코프 확장 배치(1,514,685건) 재실행 | ✅ (이 설계 적용 후 필요 — 아래 "실행 순서" 참고) |

---

## 설계

### 1. 권위 파일 로더 (`server/scripts/pnu_ledger.py`, 신규)

```python
def load_pnu_ledger(csv_path: str) -> dict[str, dict]:
    """license_no -> {"pnu": str, "address_corrected": bool,
                       "road_masked": bool, "jibun_masked": bool}"""
```

- `data_uncleaning/PNU(지번)기반_개폐업정보현황_성남시_10년.csv`를 cp949로 1회 읽어 딕셔너리로 로드.
- 필요한 컬럼만 추출: `필지고유번호(PNU)`(idx 0), `관리번호`(idx 11), `주소보정여부`(idx 21, Y/N),
  `원본도로명주소마스킹여부`(idx 25), `원본지번주소마스킹여부`(idx 26).
- PNU가 빈 문자열인 행(전체 98,517건 중 54,078건 — 원본 자체에 PNU 미기재)은 딕셔너리에서 제외
  (정정할 값이 없으므로 lookup miss와 동일하게 취급, 기존 정규식 로직으로 자연스럽게 폴백).
- `jibun_pnu.load_legaldong_codes`와 동일한 패턴(순수 함수, 캐시는 호출부 책임).

### 2. `parse_licensed_records.py` 통합

현재 순서(스코프 필터 → 중복 관리번호 → 지번 파싱 → PNU 계산 → 인허가일자 검증)에서, **지번 정규식 PNU 계산 단계 앞에** ledger lookup을 끼워 넣는다:

```python
ledger_entry = pnu_ledger.get(row["관리번호"])
if ledger_entry is not None:
    pnu = ledger_entry["pnu"]
else:
    pnu = parse_pnu(jibun, legaldong_codes)  # 기존 로직 그대로
    if pnu is None:
        skip_reasons["UNPARSEABLE_OR_DONG_NOT_FOUND"] += 1
        continue
```

- `pnu_ledger`는 `data/경기도` 처리 시에만 로드해서 넘긴다(서울 등 다른 지역 처리 시엔 빈 딕셔너리 또는 미전달 —
  이 권위 파일이 애초에 성남시 전용이라 다른 지역 `license_no`는 항상 lookup miss로 자연 폴백되지만,
  불필요한 메모리 로딩을 피하려면 호출부에서 지역별로 분기).
- `address_corrected`/마스킹 플래그는 이번 스코프에서는 **정보 확인용으로만** 활용(로그 카운트에 반영,
  아직 `ingestion_exclusion_log` 자동 판단에는 안 씀 — 그건 별도 스코프, §6 마스킹 처리 로직과 조율 필요).

### 3. 기존 중복 정리 (일회성 스크립트)

`server/scripts/dedupe_pnu_ledger_conflicts.py`(신규, 일회성 실행 도구):

1. `src/main/resources/data/*.sql` 전체를 스캔해 `license_no`별로 `pnu` 집합을 만든다(브레인스토밍 중 검증 완료 —
   93개 청크에서 1,630쌍 확인).
2. 각 충돌 쌍에 대해:
   - 권위 파일이 해당 `license_no`를 커버하면 → 권위 PNU와 일치하는 행만 남기고 나머지 삭제.
   - 권위 파일이 커버하지 않으면 → 이번 스코프에서는 보류, 목록만 로그로 남김(수동 검토 대상).
3. 삭제는 SQL 청크 파일을 다시 써서 반영(직접 DB 조작이 아니라 파일 정정 — 스키마 원칙상 SQL 파일이
   유일한 소스).

## 변경 파일

| 파일 | 변경 내용 |
|---|---|
| `server/scripts/pnu_ledger.py` | 신규 — 권위 파일 로더 |
| `server/scripts/test_pnu_ledger.py` | 신규 — 로더 단위 테스트(빈 PNU 제외, 컬럼 매핑 정확성) |
| `server/scripts/parse_licensed_records.py` | ledger lookup을 정규식 PNU 계산보다 우선 적용하도록 통합 |
| `server/scripts/test_parse_licensed_records.py` | ledger 적중 시 정규식 스킵 검증, ledger 미스 시 기존 폴백 검증 |
| `server/scripts/dedupe_pnu_ledger_conflicts.py` | 신규 — 기존 1,630쌍 정리용 일회성 스크립트 |
| `server/scripts/test_dedupe_pnu_ledger_conflicts.py` | 신규 |

## 실행 순서 (구현 완료 후)

1. `dedupe_pnu_ledger_conflicts.py`로 기존 93개 청크(+ 이번에 이미 생성한 1,686개 청크)의 충돌 정리.
2. `batch_parse_licensed_records.py`를 **`data/경기도` + `data/서울특별시` 전체에 대해 재실행** —
   이번 설계 적용 *이전*에 생성된 1,514,685건(2026-07-18 배치)은 ledger 정정을 안 거쳤으므로 최종본이 아님.
   재실행 시 `license_no` 중복 스킵이 이미 대부분 걸러주지만, ledger가 커버하는 행은 PNU가 달라질 수 있어
   완전 재적재가 필요.
3. `mvn test` — 레코드 수·PNU 값이 바뀌므로 하드코딩된 카운트 어서션 재조정 필요(세션 반복 패턴).

## 테스트 시나리오

1. **ledger 적중**: `license_no`가 ledger에 있으면 지번 텍스트와 무관하게 ledger PNU를 그대로 씀(정규식 미실행).
2. **ledger 미스**: `license_no`가 ledger에 없으면 기존 정규식 로직 그대로(회귀 없음).
3. **ledger가 UNPARSEABLE 행을 구제**: 지번 텍스트가 정규식으로 못 뚫는 형식이어도, `license_no`가 ledger에
   있으면 더 이상 스킵되지 않고 PNU가 채워짐.
4. **ledger의 빈 PNU는 미스로 취급**: 원본 자체에 PNU가 비어있는 ledger 행(54,078건)은 lookup에 안 들어가고,
   해당 `license_no`가 들어오면 기존 정규식 폴백.
5. **충돌 쌍 정리**: 동일 `license_no`, 다른 `pnu` 두 행이 존재하고 ledger가 커버할 때 → 권위 PNU와
   일치하는 행만 남고 나머지 삭제.
6. **충돌 쌍이지만 ledger 미커버**: 삭제하지 않고 보류 목록에 기록.

## 한계 및 후속 작업

| 항목 | 상태 |
|---|---|
| 서울/성남 외 경기도의 동일한 산여부 오류 가능성 | 권위 파일이 없어 이번 스코프에서 해결 불가 — 잔존 위험으로 문서화만 |
| 마스킹 플래그(`원본도로명주소마스킹여부`/`원본지번주소마스킹여부`)를 `ingestion_exclusion_log`에 실제 반영 | 이번 스코프 제외, 후속 작업으로 남김 |
| 보류된 충돌 쌍(ledger 미커버) 처리 | 목록만 남기고 이번엔 미해결 — 건수 확인 후 별도 판단 |
| `data_uncleaning/`의 다른 원본들도 유사한 미활용 권위 데이터를 갖고 있을 가능성 | 이번 조사 범위 밖, 후속 세션에서 재검토 권장 |
