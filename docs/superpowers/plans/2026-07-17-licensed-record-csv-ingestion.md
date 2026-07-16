# 원본 인허가 CSV → 서비스용 레코드 파싱 유틸 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** `data/기타_담배소매업_경기성남시.csv`(그리고 앞으로 추가될 같은 "LOCALDATA 표준" 포맷 원본)를 읽어 `licensed_business_record` 스키마에 맞는 SQL INSERT 청크로 변환하는 Python 유틸을 만든다. 원본에 PNU가 없으므로 지번주소에서 최초로 직접 계산한다.

**Architecture:** `server/scripts/`에 독립 Python 스크립트로 둔다(Java 앱 런타임과 무관, 오프라인 데이터 준비 도구). 핵심 로직(지번 파싱 + PNU 조립)을 순수 함수로 분리해 pytest로 검증하고, 오케스트레이션(CSV 읽기 → SQL 생성)은 그 위에 얇게 얹는다. 좌표는 원본 X/Y를 그대로 저장 — 변환은 기존처럼 Java `KoreanTmCoordinateConverter`가 조회 시점에 처리(재구현 안 함).

**Tech Stack:** Python 3, 표준 라이브러리 `csv`/`re`만 사용(외부 의존성 추가 안 함), pytest

## Global Constraints

- 새 레코드의 `id`는 기존 최대값(102088) 다음부터 이어붙인다 — 102089부터 시작
- 출력 SQL은 기존 `src/main/resources/data/licensed-business-records-NNN.sql`과 동일한 INSERT 컬럼 순서·포맷을 따른다: `(id, pnu, category, sub_category, license_no, business_name, business_type, business_status, status_detail_code, status_detail, licensed_at, closed_at, road_address, jibun_address, address_separated, address_corrected, parsed_building_name, parsed_floor, parsed_unit_no, parse_confidence, parse_method, local_gov_code, original_x, original_y)`
- `business_status`는 원본 CSV의 `영업상태명` 컬럼값을 그대로 쓴다 — 이미 5버킷(영업/정상, 폐업, 휴업, 취소/말소/만료/정지/중지, 제외/삭제/전출) 형식으로 채워져 있음이 확인됨(별도 매핑 테이블 불필요)
- PNU 유도에 실패한 레코드는 드롭하고 사유별 카운트를 로그로 남긴다 — 조용히 삼키지 않는다
- `parsed_building_name`/`parsed_floor`/`parsed_unit_no`/`parse_confidence`/`parse_method`/`address_separated`/`address_corrected`는 이 원본엔 상세주소 파싱 근거가 없으므로 전부 `NULL`/`FALSE` — 향후 `AddressDetailParser`(Java, 도로명주소 기반)가 별도로 채운다(이번 스코프 아님)

---

## File Map

| 파일 | 역할 |
|---|---|
| `server/scripts/legaldong_codes.csv` | **신규** — 전국 법정동코드 참조 테이블 |
| `server/scripts/jibun_pnu.py` | **신규** — 지번주소 파싱 + PNU 조립 순수 함수 |
| `server/scripts/test_jibun_pnu.py` | **신규** — 위 함수 pytest |
| `server/scripts/parse_licensed_records.py` | **신규** — CSV → SQL 청크 오케스트레이션 |
| `server/src/main/resources/data/licensed-business-records-086.sql` 이하 | **신규 생성물** — 실행 결과 |

---

### Task 1: 법정동코드 참조 테이블 + 지번주소 파서

**Files:**
- Create: `server/scripts/legaldong_codes.csv`
- Create: `server/scripts/jibun_pnu.py`
- Create: `server/scripts/test_jibun_pnu.py`

**Interfaces:**
- Produces: `jibun_pnu.load_legaldong_codes(csv_path: str) -> dict[str, str]` — 법정동명(전체 경로 문자열, 예: `"경기도 성남시 수정구 창곡동"`) → 10자리 법정동코드. `폐지` 상태 행은 제외.
- Produces: `jibun_pnu.parse_pnu(jibun_address: str, legaldong_codes: dict[str, str]) -> str | None` — 지번주소 문자열과 법정동코드 딕셔너리를 받아 19자리 PNU 문자열 또는 실패 시 `None` 반환.

- [x] **Step 1: 법정동코드 참조 파일 확보**

```bash
cd server/scripts
curl -s -m 30 -o /tmp/legaldong_raw.txt "https://gist.githubusercontent.com/FinanceData/4b0a6e1818cea9e77496e57b84bb4565/raw/"
python3 -c "
import csv
with open('/tmp/legaldong_raw.txt', encoding='utf-8') as fin, \
     open('legaldong_codes.csv', 'w', encoding='utf-8', newline='') as fout:
    reader = csv.reader(fin, delimiter='\t')
    writer = csv.writer(fout)
    for row in reader:
        writer.writerow(row)
"
wc -l legaldong_codes.csv
```
Expected: `legaldong_codes.csv` 생성, 약 45,958줄(헤더 포함). 헤더는 `법정동코드,법정동명,폐지여부`.

검증 — 성남시 창곡동 코드가 기존 실데이터와 일치하는지 확인:
```bash
grep "성남시 수정구 창곡동" legaldong_codes.csv
```
Expected: `4113110800,경기도 성남시 수정구 창곡동,존재` — 이 10자리(`4113110800`)가 기존 DB에 이미 적재된 PNU(예: `4113110800105090000`)의 앞 10자리와 일치해야 한다.

- [x] **Step 2: 실패하는 테스트 먼저**

`server/scripts/test_jibun_pnu.py`:

```python
import pytest
from jibun_pnu import load_legaldong_codes, parse_pnu

CODES = {
    "경기도 성남시 수정구 태평동": "4113110100",
    "경기도 성남시 수정구 수진동": "4113110300",
    "경기도 성남시 수정구 복정동": "4113110900",
    "경기도 성남시 분당구 정자동": "4113510500",
    "경기도 성남시 중원구 금광동": "4113310200",
}


def make_pnu(dong_code: str, san: bool, bon: int, bu: int) -> str:
    return dong_code + ("1" if san else "0") + f"{bon:04d}" + f"{bu:04d}"


def test_정상_지번_번지():
    pnu = parse_pnu("경기도 성남시수정구 태평동 2254", CODES)
    assert pnu == make_pnu("4113110100", False, 2254, 0)


def test_정상_지번_번지_본번_정확히():
    pnu = parse_pnu("경기도 성남시 수정구 수진동 4224번지", CODES)
    assert pnu == make_pnu("4113110300", False, 4224, 0)


def test_구식_지번_호_표기는_번지와_동일하게_파싱():
    pnu_ho = parse_pnu("경기도 성남시수정구 태평동 4253호   ", CODES)
    pnu_beonji = parse_pnu("경기도 성남시 수정구 태평동 4253번지", CODES)
    assert pnu_ho == pnu_beonji
    assert pnu_ho == make_pnu("4113110100", False, 4253, 0)


def test_본번_부번_조합():
    pnu = parse_pnu("경기도 성남시수정구 수진동 2679-20호   ", CODES)
    assert pnu == make_pnu("4113110300", False, 2679, 20)


def test_산_표기_인식():
    pnu = parse_pnu("경기도 성남시수정구 복정동 산65-1외35필지호", CODES)
    assert pnu == make_pnu("4113110900", True, 65, 1)


def test_번지_뒤_건물명_있어도_파싱():
    pnu = parse_pnu("경기도 성남시 중원구 금광동 3465번지 백산하이츠", CODES)
    assert pnu == make_pnu("4113310200", False, 3465, 0)


def test_동_이름_뒤_숫자_없으면_실패():
    assert parse_pnu("경기도 성남시수정구 복정동 남성대군인아파트호", CODES) is None


def test_동_이름_자체가_없으면_실패():
    assert parse_pnu("호", CODES) is None


def test_알수없는_동은_실패():
    assert parse_pnu("경기도 성남시 수정구 없는동 123", CODES) is None


def test_공백_주소는_실패():
    assert parse_pnu("", CODES) is None
    assert parse_pnu("   ", CODES) is None


def test_load_legaldong_codes_존재만_포함():
    codes = load_legaldong_codes("legaldong_codes.csv")
    assert codes["경기도 성남시 수정구 창곡동"] == "4113110800"
    # 폐지된 코드(구 성남시 창곡동, 구 조회)는 포함되지 않아야 함
    assert "경기도 성남시 창곡동" not in codes
```

- [x] **Step 3: 테스트 실행 — 실패 확인**

```bash
cd server/scripts
pip install pytest --quiet
pytest test_jibun_pnu.py -v
```
Expected: FAIL (`jibun_pnu` 모듈이 아직 없음 — `ModuleNotFoundError`)

- [x] **Step 4: 구현**

`server/scripts/jibun_pnu.py`:

```python
import csv
import re

_TAIL_PATTERN = re.compile(r'^\s*(산)?\s*(\d+)(-(\d+))?')


def load_legaldong_codes(csv_path: str) -> dict[str, str]:
    codes: dict[str, str] = {}
    with open(csv_path, encoding="utf-8") as f:
        reader = csv.DictReader(f)
        for row in reader:
            if row["폐지여부"] != "존재":
                continue
            codes[row["법정동명"]] = row["법정동코드"]
    return codes


def parse_pnu(jibun_address: str, legaldong_codes: dict[str, str]) -> str | None:
    address = jibun_address.strip()
    if not address:
        return None

    # 가장 긴 법정동명부터 검사 — "성남동"이 "금성남동" 같은 부분 문자열에
    # 잘못 매칭되는 걸 방지(법정동명은 항상 "시/도 시/군/구 동" 전체 경로라
    # 실제로 이런 충돌은 드물지만, 방어적으로 긴 것부터 검사한다).
    for dong_full_name in sorted(legaldong_codes, key=len, reverse=True):
        dong_short = dong_full_name.rsplit(" ", 1)[-1]
        idx = address.find(dong_short)
        if idx == -1:
            continue
        tail = address[idx + len(dong_short):]
        match = _TAIL_PATTERN.match(tail)
        if not match:
            continue

        is_san = match.group(1) is not None
        bon = int(match.group(2))
        bu = int(match.group(4)) if match.group(4) else 0

        return (
            legaldong_codes[dong_full_name]
            + ("1" if is_san else "0")
            + f"{bon:04d}"
            + f"{bu:04d}"
        )

    return None
```

- [x] **Step 5: 테스트 실행 — 통과 확인**

```bash
cd server/scripts
pytest test_jibun_pnu.py -v
```
Expected: 11개 테스트 전부 PASS

- [x] **Step 6: 커밋**

```bash
git add server/scripts/legaldong_codes.csv server/scripts/jibun_pnu.py server/scripts/test_jibun_pnu.py
git commit -m "feat: add legal-dong code table and jibun-address-to-PNU parser"
```

---

### Task 2: CSV → SQL 청크 오케스트레이션

**Files:**
- Create: `server/scripts/parse_licensed_records.py`

**Interfaces:**
- Consumes: `jibun_pnu.load_legaldong_codes()`, `jibun_pnu.parse_pnu()` (Task 1)
- Produces: 커맨드라인 스크립트. `python3 parse_licensed_records.py <csv_path> <output_dir> <start_id>` 형태로 실행하면 `<output_dir>`에 `licensed-business-records-NNN.sql` 청크를 생성하고, 표준출력에 스킵 사유별 카운트를 출력한다.

- [x] **Step 1: 구현**

`server/scripts/parse_licensed_records.py`:

```python
import csv
import os
import re
import sys
from collections import Counter

from jibun_pnu import load_legaldong_codes, parse_pnu

CHUNK_SIZE = 1000
FILENAME_PATTERN = re.compile(r"^(?P<category>[^_]+)_(?P<sub_category>[^_]+)_[^_]+\.csv$")


def derive_category(csv_path: str) -> tuple[str, str]:
    basename = os.path.basename(csv_path)
    match = FILENAME_PATTERN.match(basename)
    if not match:
        raise ValueError(
            f"파일명이 '대분류_소분류_지역.csv' 컨벤션과 안 맞음: {basename}"
        )
    return match.group("category"), match.group("sub_category")


def sql_string(value: str | None) -> str:
    if value is None or value == "":
        return "NULL"
    escaped = value.replace("'", "''")
    return f"'{escaped}'"


def sql_date(value: str | None) -> str:
    if not value:
        return "NULL"
    return f"'{value}'"


def sql_number(value: str | None) -> str:
    value = (value or "").strip()
    if not value:
        return "NULL"
    return value


def to_insert_row(record_id: int, pnu: str, row: dict, category: str, sub_category: str) -> str:
    return "(" + ", ".join([
        str(record_id),
        sql_string(pnu),
        sql_string(category),
        sql_string(sub_category),
        sql_string(row["관리번호"]),
        sql_string(row["사업장명"]),
        "NULL",  # business_type: 이 원본엔 없음
        sql_string(row["영업상태명"]),
        sql_string(row["상세영업상태코드"]),
        sql_string(row["상세영업상태명"]),
        sql_date(row["인허가일자"]),
        sql_date(row["폐업일자"]),
        sql_string(row["도로명주소"]),
        sql_string(row["지번주소"]),
        "FALSE",  # address_separated
        "NULL",  # address_corrected
        "NULL",  # parsed_building_name
        "NULL",  # parsed_floor
        "NULL",  # parsed_unit_no
        "NULL",  # parse_confidence
        "NULL",  # parse_method
        sql_string(row["개방자치단체코드"]),
        sql_number(row["좌표정보(X)"]),
        sql_number(row["좌표정보(Y)"]),
    ]) + ")"


def parse_file(csv_path: str, output_dir: str, start_id: int) -> None:
    category, sub_category = derive_category(csv_path)
    legaldong_codes = load_legaldong_codes(
        os.path.join(os.path.dirname(__file__), "legaldong_codes.csv")
    )

    skip_reasons: Counter = Counter()
    rows: list[str] = []
    record_id = start_id

    with open(csv_path, encoding="cp949") as f:
        reader = csv.DictReader(f)
        for row in reader:
            jibun = row["지번주소"].strip()
            if not jibun:
                skip_reasons["EMPTY"] += 1
                continue
            pnu = parse_pnu(jibun, legaldong_codes)
            if pnu is None:
                skip_reasons["UNPARSEABLE_OR_DONG_NOT_FOUND"] += 1
                continue
            if not row["인허가일자"].strip():
                skip_reasons["NO_LICENSED_AT"] += 1
                continue

            rows.append(to_insert_row(record_id, pnu, row, category, sub_category))
            record_id += 1

    os.makedirs(output_dir, exist_ok=True)
    existing = [f for f in os.listdir(output_dir) if f.startswith("licensed-business-records-")]
    next_chunk = len(existing) + 1

    columns = (
        "(id, pnu, category, sub_category, license_no, business_name, business_type, "
        "business_status, status_detail_code, status_detail, licensed_at, closed_at, "
        "road_address, jibun_address, address_separated, address_corrected, "
        "parsed_building_name, parsed_floor, parsed_unit_no, parse_confidence, "
        "parse_method, local_gov_code, original_x, original_y)"
    )

    for i in range(0, len(rows), CHUNK_SIZE):
        chunk_rows = rows[i:i + CHUNK_SIZE]
        chunk_path = os.path.join(
            output_dir, f"licensed-business-records-{next_chunk:03d}.sql"
        )
        with open(chunk_path, "w", encoding="utf-8") as out:
            out.write(f"INSERT INTO licensed_business_record {columns} VALUES\n")
            out.write(",\n".join(chunk_rows))
            out.write(";\n")
        next_chunk += 1

    print(f"생성된 레코드: {len(rows)}")
    print(f"다음 시작 id: {record_id}")
    print("스킵 사유별 카운트:")
    for reason, count in skip_reasons.most_common():
        print(f"  {reason}: {count}")


if __name__ == "__main__":
    if len(sys.argv) != 4:
        print("usage: parse_licensed_records.py <csv_path> <output_dir> <start_id>")
        sys.exit(1)
    parse_file(sys.argv[1], sys.argv[2], int(sys.argv[3]))
```

- [x] **Step 2: 컴파일 확인(문법 오류 없는지)**

```bash
cd server/scripts
python3 -c "import parse_licensed_records"
```
Expected: 에러 없이 종료

- [x] **Step 3: 커밋**

```bash
git add server/scripts/parse_licensed_records.py
git commit -m "feat: add CSV-to-SQL-chunk orchestration script for licensed records"
```

---

### Task 3: 실제 파일 실행 및 DB 반영

**Files:**
- Modify: `server/src/main/resources/data/` (신규 SQL 청크 추가)
- Test: 기존 `mvn test` 전체 스위트로 회귀 확인

- [x] **Step 1: 실행**

```bash
cd server/scripts
python3 parse_licensed_records.py \
  ../data/기타_담배소매업_경기성남시.csv \
  ../src/main/resources/data \
  102089
```
Expected: 표준출력에 생성된 레코드 수와 스킵 사유별 카운트가 출력됨. 전체 8547건 중 대다수(설계 문서 근거로 90% 이상 추정)가 생성되고, `UNPARSEABLE_OR_DONG_NOT_FOUND`가 소수(수백 건 이내)여야 한다 — 만약 스킵 비율이 30%를 넘으면 파서 로직을 재검토해야 하므로 STOP하고 보고한다.

- [x] **Step 2: 생성된 SQL 문법 확인**

```bash
head -5 ../src/main/resources/data/licensed-business-records-086.sql
tail -5 ../src/main/resources/data/licensed-business-records-086.sql
```
Expected: `INSERT INTO licensed_business_record (...) VALUES` 로 시작, `(102089, '4113...', ...)` 형태 행들, 마지막 행 뒤 `;`로 종료.

- [x] **Step 3: 전체 Java 테스트 회귀 확인**

```bash
cd ..
mvn test
```
Expected: BUILD SUCCESS, 기존 57개 테스트 전부 통과(새 레코드가 추가돼도 기존 PNU/유닛 개수를 검증하는 테스트는 영향받지 않아야 함 — 다른 PNU 대역이므로).

- [x] **Step 4: 실제 데이터 확인 — 샘플 조회**

새로 적재된 PNU 하나를 골라 애플리케이션이 실제로 조회 가능한지 수동 확인:
```bash
grep -m1 "INSERT" ../src/main/resources/data/licensed-business-records-086.sql
```
그 다음 줄에서 PNU 하나(예: `4113110100...`)를 추출해 기록해둔다 — 이후 로컬 서버 기동 테스트(별도 스코프) 때 `GET /api/sites/{pnu}`로 확인 가능.

- [x] **Step 5: 커밋**

```bash
git add src/main/resources/data/licensed-business-records-0*.sql
git commit -m "feat: ingest 기타_담배소매업_경기성남시 dataset into seed data"
```
(수백 개의 신규 INSERT 문이라 diff가 크다 — 정상)

---

## 실행 중 발견된 버그와 수정

Task 3 최초 실행(구현자 서브에이전트) 결과에서 컨트롤러가 표본 PNU를 검증하다가 **동명 충돌 버그**를 발견했다: `parse_pnu()`(Task 1, 리뷰 통과된 코드)가 법정동명을 "시/도"까지 포함해 비교하지 않고 짧은 동 이름(`rsplit(" ", 1)[-1]`)만으로 전국 법정동코드 테이블에서 찾다 보니, 동명이 겹치는 다른 도시로 잘못 매칭되는 사례가 나왔다 — 예: "태평동" 레코드가 성남시가 아니라 전라북도 전주시 완산구로 배정됨. 성남시 48개 동 중 18개(37.5%, 정확히는 리프 레벨 동 44개 기준 18개=40.9%)가 전국 어딘가와 이름이 겹침이 확인됨.

**수정**: Task 1의 `jibun_pnu.py`(범용 PNU 파서)는 건드리지 않고, Task 2의 `parse_licensed_records.py`에 `REGION_FILTER = "성남시"` 상수를 추가해 오케스트레이션 레이어에서 법정동코드 후보를 성남시로 좁혔다(성남시 내부엔 동명 충돌 0건 확인됨 — 완전히 안전한 필터). "이 프로젝트는 성남시 한정"이라는 정책은 파서가 아니라 오케스트레이션이 아는 게 맞다는 판단.

**재검증**: 수정 후 재실행한 7,518건 전체의 PNU 앞 10자리를 `legaldong_codes.csv`와 전수 대조해 전부 실제 성남시 법정동코드임을 확인했고, 이후 독립 리뷰어가 다시 한번 처음부터 전부 재도출해 검증함(구 로직으로 성공했다가 새 로직에서 탈락한 170건을 전부 역추적해 전부 오배정이었음을 확인 등). 커밋: `83c66c7`(수정), `d1479b3`(재적재).

## 한계 및 후속 작업

| 항목 | 상태 |
|---|---|
| `parsed_floor`/`parsed_unit_no` 등 상세주소 파싱 | 이번 스코프 아님 — 기존 `AddressDetailParser`(Java, 도로명주소 기반)가 조회 시점 전에 별도 배치로 채워야 함 |
| 스킵된 레코드(UNPARSEABLE_OR_DONG_NOT_FOUND) | 완전히 드롭됨, 별도 보관 안 함 — 필요 시 재작업 |
| `parse_licensed_records.py`를 다른 LOCALDATA 포맷 파일에 재사용 | 컬럼 구조·상태값 형식이 파일마다 다를 수 있어 실행 전 헤더 diff 확인 필수 — 자동 스키마 감지는 안 함(YAGNI) |
| `REGION_FILTER = "성남시"` 하드코딩 | 성남시 외 지역 파일이 추가되면 파일명의 "지역" 세그먼트에서 유도하도록 확장 필요 |
| DB 제약조건(NOT NULL/CHECK) 사전 검증 없음 | 스크립트가 SQL을 생성하기 전에 미리 검사하지 않음 — 위반 시 `mvn test`의 Spring 컨텍스트 로딩 실패로 뒤늦게 드러남(이번 실행에선 위반 없었음). 향후 다른 파일 적재 시 유의 |
