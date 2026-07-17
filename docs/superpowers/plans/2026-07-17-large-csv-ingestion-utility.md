# 대용량 전국 CSV 처리 유틸 확장 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** `server/scripts/parse_licensed_records.py`가 지역 세그먼트 없는 전국판 파일명, 성남시 사전 필터, 관리번호 기준 중복 제거를 지원하도록 확장한다. 이번 스코프는 유틸 확장까지만 — 실제 `data/식품_일반음식점.csv` 실행은 포함하지 않는다.

**Architecture:** 기존 `parse_licensed_records.py`(이미 리뷰 통과, 운영 중)에 함수 2개(`load_existing_license_nos`)와 상수 1개(`SEONGNAM_GOV_CODE`)를 추가하고, `FILENAME_PATTERN`과 `parse_file()`의 스킵 체인만 수정한다. 새 파일은 테스트 파일 하나뿐.

**Tech Stack:** Python 3, 표준 라이브러리 `csv`/`re`/`os`만 사용, pytest(이미 `server/scripts/`에 설치돼 있음, Task 1 세션에서 확인됨)

## Global Constraints

- 성남시 판별은 `개방자치단체코드 == "3780000"`(원본 CSV 컬럼값 그대로 비교) — 문자열 매칭이 아니라 정확히 일치해야 함
- 파일명 규칙: `대분류_소분류.csv` 또는 `대분류_소분류_지역.csv` 둘 다 허용(지역 세그먼트 옵션)
- 중복 판정 키는 `관리번호`(license_no) — 기존에 적재된 SQL 청크(`output_dir` 안의 `licensed-business-records-*.sql`) 전체에서 이미 쓰인 값이면 스킵
- 스킵 사유는 전부 `skip_reasons` Counter에 로그로 남긴다 — 조용히 삼키지 않는다(기존 원칙 유지)
- 기존 3가지 스킵 사유(`EMPTY`, `UNPARSEABLE_OR_DONG_NOT_FOUND`, `NO_LICENSED_AT`)와 동작은 그대로 유지, 새 스킵 사유(`NOT_SEONGNAM`, `DUPLICATE_LICENSE_NO`)만 추가
- `mvn test`(Java)는 이번 변경과 무관 — 이 플랜은 순수 Python 유틸이라 Java 빌드 확인 불필요

---

## File Map

| 파일 | 역할 |
|---|---|
| `server/scripts/parse_licensed_records.py` | **수정 대상** — `FILENAME_PATTERN`, `SEONGNAM_GOV_CODE`, `load_existing_license_nos()`, `parse_file()` 스킵 체인 |
| `server/scripts/test_parse_licensed_records.py` | **신규** — 지금까지 이 파일엔 테스트가 없었음(Task 2에서 import 스모크체크만 함). 이번에 순수 로직이 늘어나므로 pytest 추가 |

---

### Task 1: 파일명 규칙 완화 + 성남시 사전 필터

**Files:**
- Modify: `server/scripts/parse_licensed_records.py:10` (`FILENAME_PATTERN`)
- Modify: `server/scripts/parse_licensed_records.py:13` 다음 줄 (새 상수 추가)
- Modify: `server/scripts/parse_licensed_records.py:92-103` (`parse_file()`의 for 루프)
- Create: `server/scripts/test_parse_licensed_records.py`

**Interfaces:**
- Consumes: 없음(기존 `derive_category`, `parse_file`을 수정)
- Produces: `parse_file()`이 이제 `SEONGNAM_GOV_CODE`(모듈 상수, 값 `"3780000"`)와 맞지 않는 행을 `NOT_SEONGNAM` 사유로 스킵한다. 이후 태스크는 이 스킵 체인 순서(성남시 필터 → 관리번호 중복 → 지번 파싱)를 그대로 이어받는다.

#### Step 1: 실패하는 테스트 먼저

`server/scripts/test_parse_licensed_records.py`:

```python
import csv

from parse_licensed_records import derive_category, parse_file


def test_지역_세그먼트_없어도_카테고리_유도():
    assert derive_category("식품_일반음식점.csv") == ("식품", "일반음식점")


def test_지역_세그먼트_있어도_카테고리_유도():
    assert derive_category("기타_담배소매업_경기성남시.csv") == ("기타", "담배소매업")


_FIELDNAMES = [
    "개방자치단체코드", "관리번호", "사업장명", "영업상태명",
    "상세영업상태코드", "상세영업상태명", "인허가일자", "폐업일자",
    "도로명주소", "지번주소", "좌표정보(X)", "좌표정보(Y)",
]


def _write_csv(path, rows):
    with open(path, "w", encoding="cp949", newline="") as f:
        writer = csv.DictWriter(f, fieldnames=_FIELDNAMES)
        writer.writeheader()
        for row in rows:
            writer.writerow(row)


def _make_row(gov_code, license_no, road_addr, jibun_addr):
    return {
        "개방자치단체코드": gov_code, "관리번호": license_no,
        "사업장명": "테스트가게", "영업상태명": "영업/정상",
        "상세영업상태코드": "01", "상세영업상태명": "영업",
        "인허가일자": "2020-01-01", "폐업일자": "",
        "도로명주소": road_addr, "지번주소": jibun_addr,
        "좌표정보(X)": "211488.6", "좌표정보(Y)": "438155.3",
    }


def test_성남시_아닌_행은_지번_파싱_없이_스킵됨(tmp_path, capsys):
    csv_path = tmp_path / "식품_일반음식점.csv"
    _write_csv(csv_path, [
        _make_row("1100000", "seoul-001", "서울특별시 종로구 1", "서울특별시 종로구 청운동 1"),
    ])
    output_dir = tmp_path / "out"
    parse_file(str(csv_path), str(output_dir), 1)

    out = capsys.readouterr().out
    assert "생성된 레코드: 0" in out
    assert "NOT_SEONGNAM: 1" in out
    assert not list(output_dir.glob("*.sql"))


def test_성남시_행은_정상_처리됨(tmp_path, capsys):
    csv_path = tmp_path / "식품_일반음식점.csv"
    _write_csv(csv_path, [
        _make_row("3780000", "seongnam-001", "경기도 성남시 수정구 태평동", "경기도 성남시 수정구 태평동 2254"),
    ])
    output_dir = tmp_path / "out"
    parse_file(str(csv_path), str(output_dir), 1)

    out = capsys.readouterr().out
    assert "생성된 레코드: 1" in out
    chunk_files = list(output_dir.glob("*.sql"))
    assert len(chunk_files) == 1
    assert "seongnam-001" in chunk_files[0].read_text(encoding="utf-8")
```

- [ ] **Step 2: 테스트 실행 — 실패 확인**

```bash
cd server/scripts
pytest test_parse_licensed_records.py -v
```
Expected: `test_지역_세그먼트_없어도_카테고리_유도`와 `test_성남시_아닌_행은_지번_파싱_없이_스킵됨` 등이 FAIL — `test_지역_세그먼트_없어도_카테고리_유도`는 현재 `FILENAME_PATTERN`이 3세그먼트를 요구해 `ValueError` 발생. `test_성남시_아닌_행은_지번_파싱_없이_스킵됨`은 현재 `NOT_SEONGNAM` 스킵 로직이 없어 서울 주소도 지번 파싱을 시도(법정동 못 찾아 `UNPARSEABLE_OR_DONG_NOT_FOUND`로 스킵되긴 하지만 사유가 다름 — 이 테스트는 `NOT_SEONGNAM` 사유를 명시적으로 검증하므로 실패해야 정상).

#### Step 3: 구현

`server/scripts/parse_licensed_records.py:10`의 `FILENAME_PATTERN`을 교체:

```python
FILENAME_PATTERN = re.compile(r"^(?P<category>[^_]+)_(?P<sub_category>[^_]+)(_[^_]+)?\.csv$")
```

`REGION_FILTER = "성남시"` 줄(현재 13번째 줄) 바로 다음에 상수 추가:

```python
# ponytail: 성남시 하드코딩, REGION_FILTER와 같은 이유(CLAUDE.md 데이터축).
# 지번주소 파싱을 시도하기 전에 거르는 성능용 사전 필터 — 실측 230만 행 중
# 99%가 이 필터 하나로 즉시 스킵됨(성남시는 1.3%뿐).
SEONGNAM_GOV_CODE = "3780000"
```

`parse_file()`의 for 루프(현재 92-103행)를 교체:

```python
        for row in reader:
            if row["개방자치단체코드"] != SEONGNAM_GOV_CODE:
                skip_reasons["NOT_SEONGNAM"] += 1
                continue
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
```

- [ ] **Step 4: 테스트 실행 — 통과 확인**

```bash
cd server/scripts
pytest test_parse_licensed_records.py -v
```
Expected: 이 파일의 4개 테스트 전부 PASS

- [ ] **Step 5: 기존 회귀 확인**

이 스크립트가 Task 1(`jibun_pnu.py`)에 의존하므로 그쪽 테스트도 같이 통과하는지 확인:

```bash
cd server/scripts
pytest -v
```
Expected: `test_jibun_pnu.py` 11개 + `test_parse_licensed_records.py` 4개, 총 15개 전부 PASS

- [ ] **Step 6: 커밋**

```bash
git add server/scripts/parse_licensed_records.py server/scripts/test_parse_licensed_records.py
git commit -m "feat: relax filename convention and add Seongnam pre-filter to CSV ingestion"
```

---

### Task 2: 관리번호 기준 중복 제거

**Files:**
- Modify: `server/scripts/parse_licensed_records.py` (새 함수 추가 + `parse_file()`에 통합)
- Modify: `server/scripts/test_parse_licensed_records.py` (테스트 추가)

**Interfaces:**
- Consumes: Task 1의 `SEONGNAM_GOV_CODE`, 수정된 `parse_file()` 스킵 체인
- Produces: `load_existing_license_nos(data_dir: str) -> set[str]` — 이후 다른 스크립트나 향후 확장이 "이미 적재된 관리번호 집합"이 필요하면 이 함수를 재사용한다.

#### Step 1: 실패하는 테스트 먼저

`server/scripts/test_parse_licensed_records.py` 파일 끝에 추가:

```python
from parse_licensed_records import load_existing_license_nos


def _write_existing_chunk(path, rows):
    """rows: list of (id, pnu, category, sub_category, license_no, business_name) 튜플"""
    lines = [
        "INSERT INTO licensed_business_record "
        "(id, pnu, category, sub_category, license_no, business_name) VALUES"
    ]
    row_texts = [
        f"({r[0]}, '{r[1]}', '{r[2]}', '{r[3]}', '{r[4]}', '{r[5]}')" for r in rows
    ]
    with open(path, "w", encoding="utf-8") as f:
        f.write(lines[0] + "\n")
        f.write(",\n".join(row_texts))
        f.write(";\n")


def test_load_existing_license_nos_기존_청크에서_추출(tmp_path):
    data_dir = tmp_path / "data"
    data_dir.mkdir()
    _write_existing_chunk(data_dir / "licensed-business-records-001.sql", [
        (1, "4113110200042530000", "기타", "담배소매업", "1974379000005600001", "태평코너"),
        (2, "4113110300043550000", "기타", "담배소매업", "1974379000005600002", "문방구"),
    ])

    license_nos = load_existing_license_nos(str(data_dir))

    assert license_nos == {"1974379000005600001", "1974379000005600002"}


def test_load_existing_license_nos_디렉토리_없으면_빈집합(tmp_path):
    assert load_existing_license_nos(str(tmp_path / "no-such-dir")) == set()


def test_이미_적재된_관리번호는_스킵됨(tmp_path, capsys):
    data_dir = tmp_path / "data"
    data_dir.mkdir()
    _write_existing_chunk(data_dir / "licensed-business-records-001.sql", [
        (1, "4113110200042530000", "기타", "담배소매업", "seongnam-001", "기존가게"),
    ])
    csv_path = tmp_path / "식품_일반음식점.csv"
    _write_csv(csv_path, [
        _make_row("3780000", "seongnam-001", "경기도 성남시 수정구 태평동", "경기도 성남시 수정구 태평동 2254"),
    ])

    parse_file(str(csv_path), str(data_dir), 100)

    out = capsys.readouterr().out
    assert "생성된 레코드: 0" in out
    assert "DUPLICATE_LICENSE_NO: 1" in out
```

- [ ] **Step 2: 테스트 실행 — 실패 확인**

```bash
cd server/scripts
pytest test_parse_licensed_records.py -v
```
Expected: 새로 추가한 3개 테스트가 FAIL — `load_existing_license_nos`가 아직 없어 `ImportError`.

#### Step 3: 구현

`server/scripts/parse_licensed_records.py`에서 `to_insert_row` 함수와 `parse_file` 함수 사이에 추가:

```python
_LICENSE_NO_PATTERN = re.compile(r"^\((?:\d+), '[^']*', '[^']*', '[^']*', '([^']*)',")


def load_existing_license_nos(data_dir: str) -> set[str]:
    license_nos: set[str] = set()
    if not os.path.isdir(data_dir):
        return license_nos
    for filename in os.listdir(data_dir):
        if not filename.startswith("licensed-business-records-") or not filename.endswith(".sql"):
            continue
        with open(os.path.join(data_dir, filename), encoding="utf-8") as f:
            for line in f:
                match = _LICENSE_NO_PATTERN.match(line.strip())
                if match:
                    license_nos.add(match.group(1))
    return license_nos
```

`parse_file()` 안, `legaldong_codes = {...}` 대입 다음 줄에 추가:

```python
    existing_license_nos = load_existing_license_nos(output_dir)
```

`parse_file()`의 for 루프에서 `if row["개방자치단체코드"] != SEONGNAM_GOV_CODE:` 블록 바로 다음에 추가:

```python
            if row["관리번호"] in existing_license_nos:
                skip_reasons["DUPLICATE_LICENSE_NO"] += 1
                continue
```

즉 루프 전체가 다음과 같아진다:

```python
        for row in reader:
            if row["개방자치단체코드"] != SEONGNAM_GOV_CODE:
                skip_reasons["NOT_SEONGNAM"] += 1
                continue
            if row["관리번호"] in existing_license_nos:
                skip_reasons["DUPLICATE_LICENSE_NO"] += 1
                continue
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
```

- [ ] **Step 4: 테스트 실행 — 통과 확인**

```bash
cd server/scripts
pytest test_parse_licensed_records.py -v
```
Expected: 이 파일의 7개 테스트 전부 PASS

- [ ] **Step 5: 전체 회귀 확인**

```bash
cd server/scripts
pytest -v
```
Expected: `test_jibun_pnu.py` 11개 + `test_parse_licensed_records.py` 7개, 총 18개 전부 PASS

- [ ] **Step 6: 커밋**

```bash
git add server/scripts/parse_licensed_records.py server/scripts/test_parse_licensed_records.py
git commit -m "feat: dedupe licensed records by license_no against already-loaded chunks"
```

---

## 한계 및 후속 작업

| 항목 | 상태 |
|---|---|
| 실제 `data/식품_일반음식점.csv` 적재 실행 | 이번 스코프 아님 — 유틸 완성 후 별도 요청 시 실행(230만 행 스캔은 실측 16초, 안전하게 돌릴 수 있음이 이미 확인됨) |
| 관리번호 정규식(`_LICENSE_NO_PATTERN`)이 컬럼 순서 변경에 취약 | `to_insert_row`가 만드는 컬럼 순서(id, pnu, category, sub_category, license_no, ...)가 바뀌면 이 정규식도 같이 고쳐야 함 — 두 곳이 암묵적으로 결합돼 있음 |
| `load_existing_license_nos`가 매 실행마다 전체 청크 디렉토리를 다시 스캔 | 청크가 수백 개로 늘어나면(현재 93개) 텍스트 스캔 비용이 커질 수 있음 — 지금은 문제없음(정규식 스캔은 가벼움), 필요시 캐시 고려 |
