# PNU 권위 파일 조인 정정 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 성남시 관리번호(license_no)별로 이미 정확한 PNU를 갖고 있는 권위 파일
(`data_uncleaning/PNU(지번)기반_개폐업정보현황_성남시_10년.csv`)을 조인 정정 레이어로 붙여서,
① 우리 정규식 파서가 "산" 표기 누락으로 동일 업소를 다른 PNU로 중복 적재하는 버그를 막고,
② 정규식이 포기하던 지번 텍스트도 PNU를 복구해 커버리지를 늘리고,
③ 기존에 이미 적재된 1,630개 중복 쌍을 정리한다.

**Architecture:** `server/scripts/pnu_ledger.py`(신규)가 권위 CSV를 `license_no -> {pnu, ...}` 딕셔너리로
1회 로드한다. `parse_licensed_records.py`의 지번 정규식 PNU 계산 *앞*에 이 딕셔너리 조회를 끼워 넣어
적중 시 정규식을 건너뛴다(미스 시 기존 로직 100% 그대로 폴백). `batch_parse_licensed_records.py`는
이 딕셔너리를 한 번 로드해 모든 파일 처리에 공유한다(49MB 파일이라 파일당 재로드는 안 됨).
기존 데이터의 충돌은 별도 일회성 스크립트(`dedupe_pnu_ledger_conflicts.py`)로 정리한다.

**Tech Stack:** Python 3(표준 라이브러리 `csv`/`re`/`glob`만 사용, 신규 의존성 없음), pytest.

## Global Constraints

- 권위 파일 인코딩은 `cp949`(다른 모든 LOCALDATA 원본과 동일, 이미 확인됨).
- 권위 파일엔 `폐업일자` 컬럼이 없다 — 이 계획에서 `closed_at`을 이 파일에서 가져오는 코드는 절대
  작성하지 않는다(설계 문서 §문제 참고, 전면 대체 아님).
- 권위 파일 경로는 `server/` 밖(`D:\Dev\_Woowahan-Techcourse\woowaTon\data_uncleaning\`)에 있다 —
  git 미관리 영역이라 하드코딩하지 않고 항상 CLI 인자로 받는다.
- `parse_file()`의 기존 3-positional-arg 호출부(기존 테스트 다수, CLI `__main__`)를 깨면 안 된다 —
  신규 파라미터는 반드시 기본값 `None`이 있는 4번째 파라미터로 추가한다.
- 청크 SQL 파일은 항상 "헤더 줄 1개 + 행마다 한 줄(`,`로 끝남, 마지막 행만 `;`로 끝남)" 형식을
  유지한다(기존 `parse_file()`의 쓰기 로직과 동일 — 다른 형식으로 쓰면 다음 배치 실행의
  `load_existing_license_nos()`/`next_start_id()` 정규식이 깨진다).

---

### Task 1: `pnu_ledger.py` 로더

**Files:**
- Create: `server/scripts/pnu_ledger.py`
- Test: `server/scripts/test_pnu_ledger.py`

**Interfaces:**
- Produces: `load_pnu_ledger(csv_path: str) -> dict[str, dict]` — 반환 딕셔너리의 값은
  `{"pnu": str, "address_corrected": bool, "road_masked": bool, "jibun_masked": bool}`.
  PNU가 빈 문자열인 원본 행은 결과에서 제외(정정할 값이 없으므로).

- [ ] **Step 1: 실패하는 테스트 작성**

`server/scripts/test_pnu_ledger.py`:

```python
import csv

from pnu_ledger import load_pnu_ledger

_FIELDNAMES = [
    "필지고유번호(PNU)", "관리번호", "주소보정여부",
    "원본도로명주소마스킹여부", "원본지번주소마스킹여부",
]


def _write_csv(path, rows):
    with open(path, "w", encoding="cp949", newline="") as f:
        writer = csv.DictWriter(f, fieldnames=_FIELDNAMES)
        writer.writeheader()
        for row in rows:
            writer.writerow(row)


def test_pnu와_플래그를_license_no_기준으로_로드(tmp_path):
    csv_path = tmp_path / "ledger.csv"
    _write_csv(csv_path, [
        {
            "필지고유번호(PNU)": "4113313200130010000",
            "관리번호": "2000380000005607203",
            "주소보정여부": "N",
            "원본도로명주소마스킹여부": "Y",
            "원본지번주소마스킹여부": "N",
        },
    ])

    ledger = load_pnu_ledger(str(csv_path))

    assert ledger["2000380000005607203"] == {
        "pnu": "4113313200130010000",
        "address_corrected": False,
        "road_masked": True,
        "jibun_masked": False,
    }


def test_pnu가_빈_행은_결과에서_제외(tmp_path):
    csv_path = tmp_path / "ledger.csv"
    _write_csv(csv_path, [
        {
            "필지고유번호(PNU)": "",
            "관리번호": "no-pnu-001",
            "주소보정여부": "N",
            "원본도로명주소마스킹여부": "N",
            "원본지번주소마스킹여부": "N",
        },
    ])

    ledger = load_pnu_ledger(str(csv_path))

    assert "no-pnu-001" not in ledger


def test_주소보정여부_Y도_정확히_불리언으로_변환(tmp_path):
    csv_path = tmp_path / "ledger.csv"
    _write_csv(csv_path, [
        {
            "필지고유번호(PNU)": "4113110100199990000",
            "관리번호": "corrected-001",
            "주소보정여부": "Y",
            "원본도로명주소마스킹여부": "N",
            "원본지번주소마스킹여부": "N",
        },
    ])

    ledger = load_pnu_ledger(str(csv_path))

    assert ledger["corrected-001"]["address_corrected"] is True
```

- [ ] **Step 2: 테스트가 실패하는지 확인**

Run: `cd server/scripts && python3 -m pytest -q test_pnu_ledger.py -v`
Expected: FAIL — `ModuleNotFoundError: No module named 'pnu_ledger'`

- [ ] **Step 3: 최소 구현 작성**

`server/scripts/pnu_ledger.py`:

```python
import csv


def load_pnu_ledger(csv_path: str) -> dict[str, dict]:
    """license_no -> {"pnu": str, "address_corrected": bool,
                       "road_masked": bool, "jibun_masked": bool}.

    PNU가 비어있는 원본 행은 정정할 값이 없으므로 결과에서 제외한다
    (원본 98,517건 중 54,078건이 이 경우 - 2026-07-18 실측,
    docs/superpowers/specs/2026-07-18-pnu-ledger-correction-design.md 참고).
    """
    ledger: dict[str, dict] = {}
    with open(csv_path, encoding="cp949", newline="") as f:
        reader = csv.DictReader(f)
        for row in reader:
            pnu = row["필지고유번호(PNU)"].strip()
            if not pnu:
                continue
            ledger[row["관리번호"]] = {
                "pnu": pnu,
                "address_corrected": row["주소보정여부"] == "Y",
                "road_masked": row["원본도로명주소마스킹여부"] == "Y",
                "jibun_masked": row["원본지번주소마스킹여부"] == "Y",
            }
    return ledger
```

- [ ] **Step 4: 테스트가 통과하는지 확인**

Run: `cd server/scripts && python3 -m pytest -q test_pnu_ledger.py -v`
Expected: `3 passed`

- [ ] **Step 5: 실제 권위 파일로 스모크 확인** (자동화된 테스트는 아니지만, 다음 태스크 전에
  반드시 눈으로 확인)

Run:
```bash
cd server/scripts
python3 -c "
from pnu_ledger import load_pnu_ledger
ledger = load_pnu_ledger('../../data_uncleaning/PNU(지번)기반_개폐업정보현황_성남시_10년.csv')
print('총 항목:', len(ledger))
print(ledger['2000380000005607203'])
"
```
Expected:
```
총 항목: 44439
{'pnu': '4113313200130010000', 'address_corrected': False, 'road_masked': True, 'jibun_masked': True}
```
(총 항목 수는 98,517 - 54,078(빈 PNU) = 44,439와 일치해야 함. 다르면 Step 3 구현을 재검토할 것.)

- [ ] **Step 6: 커밋**

```bash
cd server
git add scripts/pnu_ledger.py scripts/test_pnu_ledger.py
git commit -m "feat: add PNU authority-file loader for license_no-keyed correction"
```

---

### Task 2: `parse_licensed_records.py`에 ledger 조회 통합

**Files:**
- Modify: `server/scripts/parse_licensed_records.py:119-160` (`parse_file` 함수)
- Modify: `server/scripts/test_parse_licensed_records.py`

**Interfaces:**
- Consumes: Task 1의 `load_pnu_ledger()`가 만드는 딕셔너리 형태(`{license_no: {"pnu": str, ...}}`) —
  이 태스크는 이 딕셔너리를 **받기만** 한다(로드는 호출부 책임, 이 파일은 `pnu_ledger` 모듈을
  import하지 않는다 — 49MB 파일을 매 `parse_file()` 호출마다 재로드하면 안 되므로).
- Produces: `parse_file(csv_path: str, output_dir: str, start_id: int, pnu_ledger: dict[str, dict] | None = None) -> tuple[int, Counter]`
  (4번째 파라미터 추가, 나머지 시그니처·반환값 불변 — 기존 3-인자 호출부는 전부 그대로 동작).

- [ ] **Step 1: 실패하는 테스트 작성**

`server/scripts/test_parse_licensed_records.py`에 추가(파일 하단, 기존 임포트 블록 근처에
`from pnu_ledger import load_pnu_ledger`를 새로 추가할 필요는 없음 — 테스트에서 딕셔너리를
직접 리터럴로 만들어 넘긴다):

```python
def test_ledger에_license_no가_있으면_정규식_대신_ledger_pnu를_씀(tmp_path, capsys):
    csv_path = tmp_path / "식품_일반음식점.csv"
    # 정규식으로 파싱하면 "경기도 성남시 수정구 태평동 2254" -> 산여부=0인 PNU가 나옴.
    # ledger에는 일부러 다른(산여부=1) PNU를 넣어서, ledger가 이겼는지 검증한다.
    _write_csv(csv_path, [
        _make_row("3780000", "ledger-hit-001", "경기도 성남시 수정구 태평동",
                  "경기도 성남시 수정구 태평동 2254"),
    ])
    output_dir = tmp_path / "out"
    pnu_ledger = {"ledger-hit-001": {"pnu": "4113110100199990000", "address_corrected": False,
                                      "road_masked": False, "jibun_masked": False}}

    parse_file(str(csv_path), str(output_dir), 1, pnu_ledger)

    out = capsys.readouterr().out
    assert "생성된 레코드: 1" in out
    chunk = list(output_dir.glob("*.sql"))[0].read_text(encoding="utf-8")
    assert "4113110100199990000" in chunk


def test_ledger에_없는_license_no는_기존_정규식_로직_그대로(tmp_path, capsys):
    csv_path = tmp_path / "식품_일반음식점.csv"
    _write_csv(csv_path, [
        _make_row("3780000", "ledger-miss-001", "경기도 성남시 수정구 태평동",
                  "경기도 성남시 수정구 태평동 2254"),
    ])
    output_dir = tmp_path / "out"
    pnu_ledger = {"other-license": {"pnu": "9999999999999999999", "address_corrected": False,
                                     "road_masked": False, "jibun_masked": False}}

    parse_file(str(csv_path), str(output_dir), 1, pnu_ledger)

    out = capsys.readouterr().out
    assert "생성된 레코드: 1" in out
    chunk = list(output_dir.glob("*.sql"))[0].read_text(encoding="utf-8")
    assert "9999999999999999999" not in chunk  # ledger의 다른 항목을 잘못 쓰지 않았는지 확인


def test_ledger가_UNPARSEABLE_행을_구제(tmp_path, capsys):
    csv_path = tmp_path / "식품_일반음식점.csv"
    # 지번주소가 법정동명 매칭이 안 되는 텍스트 -> 정규식은 무조건 실패.
    _write_csv(csv_path, [
        _make_row("3780000", "rescue-001", "경기도 성남시 수정구 알수없는동네",
                  "경기도 성남시 수정구 알수없는동네 어딘가"),
    ])
    output_dir = tmp_path / "out"
    pnu_ledger = {"rescue-001": {"pnu": "4113110100188880000", "address_corrected": False,
                                  "road_masked": False, "jibun_masked": False}}

    parse_file(str(csv_path), str(output_dir), 1, pnu_ledger)

    out = capsys.readouterr().out
    assert "생성된 레코드: 1" in out
    assert "UNPARSEABLE_OR_DONG_NOT_FOUND" not in out


def test_ledger_인자_없이_호출해도_기존과_동일하게_동작(tmp_path, capsys):
    csv_path = tmp_path / "식품_일반음식점.csv"
    _write_csv(csv_path, [
        _make_row("3780000", "no-ledger-001", "경기도 성남시 수정구 태평동",
                  "경기도 성남시 수정구 태평동 2254"),
    ])
    output_dir = tmp_path / "out"

    parse_file(str(csv_path), str(output_dir), 1)  # 4번째 인자 생략 - 기존 호출부와 동일

    out = capsys.readouterr().out
    assert "생성된 레코드: 1" in out
```

- [ ] **Step 2: 테스트가 실패하는지 확인**

Run: `cd server/scripts && python3 -m pytest -q test_parse_licensed_records.py -k ledger -v`
Expected: FAIL — `TypeError: parse_file() takes 3 positional arguments but 4 were given`
(마지막 테스트 `test_ledger_인자_없이_호출해도...`는 이미 통과할 수 있음 — 4번째 인자를 안 넘기므로.
나머지 3개는 반드시 실패해야 함.)

- [ ] **Step 3: 최소 구현 작성**

`server/scripts/parse_licensed_records.py:119`의 함수 시그니처를 변경:

```python
def parse_file(csv_path: str, output_dir: str, start_id: int,
                pnu_ledger: dict[str, dict] | None = None) -> tuple[int, Counter]:
```

`server/scripts/parse_licensed_records.py:147-154`(현재 아래 블록)를:

```python
            jibun = row["지번주소"].strip()
            if not jibun:
                skip_reasons["EMPTY"] += 1
                continue
            pnu = parse_pnu(jibun, legaldong_codes)
            if pnu is None:
                skip_reasons["UNPARSEABLE_OR_DONG_NOT_FOUND"] += 1
                continue
```

다음으로 교체:

```python
            ledger_entry = pnu_ledger.get(row["관리번호"]) if pnu_ledger else None
            if ledger_entry is not None:
                pnu = ledger_entry["pnu"]
            else:
                jibun = row["지번주소"].strip()
                if not jibun:
                    skip_reasons["EMPTY"] += 1
                    continue
                pnu = parse_pnu(jibun, legaldong_codes)
                if pnu is None:
                    skip_reasons["UNPARSEABLE_OR_DONG_NOT_FOUND"] += 1
                    continue
```

- [ ] **Step 4: 테스트가 통과하는지 확인**

Run: `cd server/scripts && python3 -m pytest -q test_parse_licensed_records.py -v`
Expected: 이전 30개 + 신규 4개 = `34 passed`(전체 회귀 없음 확인 — Task 1 이후 `test_pnu_ledger.py` 3개까지
합치면 스크립트 전체는 `python3 -m pytest -q`로 `37 passed`).

- [ ] **Step 5: 커밋**

```bash
cd server
git add scripts/parse_licensed_records.py scripts/test_parse_licensed_records.py
git commit -m "feat: prefer PNU ledger over regex when license_no matches"
```

---

### Task 3: `batch_parse_licensed_records.py`에 ledger 배선

**Files:**
- Modify: `server/scripts/batch_parse_licensed_records.py`
- Modify: `server/scripts/test_batch_parse_licensed_records.py`
- Modify: `server/scripts/README.md`

**Interfaces:**
- Consumes: Task 1의 `load_pnu_ledger()`, Task 2의 `parse_file(..., pnu_ledger=...)`.
- Produces: `run_batch(output_dir: str, source_dirs: list[str], log_path: str, pnu_ledger: dict[str, dict] | None = None) -> None`
  (4번째 파라미터 추가). CLI: `batch_parse_licensed_records.py [--ledger <path>] <output_dir> <source_dir> [...]`.

- [ ] **Step 1: 실패하는 테스트 작성**

`server/scripts/test_batch_parse_licensed_records.py` 상단 import에 추가:

```python
from pnu_ledger import load_pnu_ledger
```

파일 끝에 추가:

```python
def test_run_batch에_ledger를_넘기면_parse_file에_전달됨(tmp_path):
    src = tmp_path / "경기도"
    src.mkdir()
    _write_csv(src / "식품_일반음식점.csv", [_row("ledger-e2e-001")])

    ledger_csv = tmp_path / "ledger.csv"
    with open(ledger_csv, "w", encoding="cp949", newline="") as f:
        writer = csv.DictWriter(f, fieldnames=[
            "필지고유번호(PNU)", "관리번호", "주소보정여부",
            "원본도로명주소마스킹여부", "원본지번주소마스킹여부",
        ])
        writer.writeheader()
        writer.writerow({
            "필지고유번호(PNU)": "4113110100177770000",
            "관리번호": "ledger-e2e-001",
            "주소보정여부": "N",
            "원본도로명주소마스킹여부": "N",
            "원본지번주소마스킹여부": "N",
        })
    pnu_ledger = load_pnu_ledger(str(ledger_csv))

    output_dir = tmp_path / "out"
    output_dir.mkdir()
    log_path = tmp_path / "summary.txt"

    run_batch(str(output_dir), [str(src)], str(log_path), pnu_ledger)

    content = list(output_dir.glob("*.sql"))[0].read_text(encoding="utf-8")
    assert "4113110100177770000" in content
```

`_row()` 헬퍼는 이 테스트 파일에 이미 있음(license_no를 첫 인자로 받음) — 새로 만들 필요 없음.

- [ ] **Step 2: 테스트가 실패하는지 확인**

Run: `cd server/scripts && python3 -m pytest -q test_batch_parse_licensed_records.py -k ledger -v`
Expected: FAIL — `TypeError: run_batch() takes 3 positional arguments but 4 were given`

- [ ] **Step 3: 최소 구현 작성**

`server/scripts/batch_parse_licensed_records.py:28`의 함수 시그니처 변경:

```python
def run_batch(output_dir: str, source_dirs: list[str], log_path: str,
              pnu_ledger: dict[str, dict] | None = None) -> None:
```

`server/scripts/batch_parse_licensed_records.py:42`(현재):

```python
                next_id, skip_reasons = parse_file(csv_path, output_dir, start_id)
```

다음으로 교체:

```python
                next_id, skip_reasons = parse_file(csv_path, output_dir, start_id, pnu_ledger)
```

`server/scripts/batch_parse_licensed_records.py` 상단 import에 추가:

```python
from pnu_ledger import load_pnu_ledger
```

`server/scripts/batch_parse_licensed_records.py:74-78`(현재 `__main__` 블록)을:

```python
if __name__ == "__main__":
    if len(sys.argv) < 3:
        print("usage: batch_parse_licensed_records.py [--ledger <path>] <output_dir> <source_dir> [<source_dir> ...]")
        sys.exit(1)
    run_batch(sys.argv[1], sys.argv[2:], os.path.join(sys.argv[2], "batch-run-summary.txt"))
```

다음으로 교체:

```python
if __name__ == "__main__":
    args = sys.argv[1:]
    ledger_path = None
    if args and args[0] == "--ledger":
        ledger_path = args[1]
        args = args[2:]
    if len(args) < 2:
        print("usage: batch_parse_licensed_records.py [--ledger <path>] <output_dir> <source_dir> [<source_dir> ...]")
        sys.exit(1)
    pnu_ledger = load_pnu_ledger(ledger_path) if ledger_path else None
    run_batch(args[0], args[1:], os.path.join(args[1], "batch-run-summary.txt"), pnu_ledger)
```

- [ ] **Step 4: 테스트가 통과하는지 확인**

Run: `cd server/scripts && python3 -m pytest -q -v`
Expected: 기존 37개 + 신규 1개 = `38 passed`

- [ ] **Step 5: README에 `--ledger` 옵션 문서화**

`server/scripts/README.md`의 "여러 파일 한꺼번에 돌리기" 섹션(현재 "python3 batch_parse_licensed_records.py <output_dir> <source_dir> [<source_dir> ...]" 예시가 있는 부분) 바로 아래에 추가:

```markdown
### 성남시 PNU 정정(`--ledger`)

`data/경기도`를 처리할 때 `--ledger` 옵션으로 권위 파일을 넘기면, `license_no`가 일치하는 행은
지번 정규식 대신 이 파일의 PNU를 그대로 쓴다(정규식이 "산" 표기 누락으로 산여부를 잘못 판정해
동일 업소가 다른 PNU로 중복 적재되는 걸 막음, 정규식이 포기하던 지번도 구제 —
`docs/superpowers/specs/2026-07-18-pnu-ledger-correction-design.md` 참고):

```bash
python3 batch_parse_licensed_records.py \
  --ledger "../../data_uncleaning/PNU(지번)기반_개폐업정보현황_성남시_10년.csv" \
  ../src/main/resources/data ../data/경기도
```

이 권위 파일은 성남시 전용이라 `data/서울특별시` 처리 시엔 `--ledger`를 안 넘겨도 되고(넘겨도
무해함 — 서울 `license_no`는 애초에 이 파일에 없어 전부 미스로 자연 폴백), 넘기려면 두 소스
디렉터리를 한 번에 처리하는 호출에 그냥 같이 붙이면 된다.
```

- [ ] **Step 6: 커밋**

```bash
cd server
git add scripts/batch_parse_licensed_records.py scripts/test_batch_parse_licensed_records.py scripts/README.md
git commit -m "feat: wire PNU ledger through batch runner via --ledger flag"
```

---

### Task 4: 기존 중복 정리 스크립트 (`dedupe_pnu_ledger_conflicts.py`)

**Files:**
- Create: `server/scripts/dedupe_pnu_ledger_conflicts.py`
- Create: `server/scripts/test_dedupe_pnu_ledger_conflicts.py`

**Interfaces:**
- Consumes: Task 1의 `load_pnu_ledger()` 반환 딕셔너리.
- Produces:
  - `find_conflicts(data_dir: str) -> dict[str, list[tuple[int, str, str]]]` —
    `license_no -> [(id, pnu, chunk_filename), ...]`, `pnu`가 2종류 이상인 `license_no`만 포함.
  - `resolve_conflicts(data_dir: str, pnu_ledger: dict[str, dict]) -> tuple[set[int], list[str]]` —
    반환은 `(삭제할 id 집합, ledger 미커버라 보류한 license_no 목록(정렬됨))`.
  - `delete_rows(data_dir: str, ids_to_delete: set[int]) -> int` — 실제로 삭제된 행 수를 반환.
    청크 파일은 "헤더 줄 + 행마다 한 줄" 불변식을 유지하며 다시 쓴다(Global Constraints 참고).
    한 청크의 모든 행이 삭제 대상이면 그 파일 자체를 삭제한다.

- [ ] **Step 1: 실패하는 테스트 작성**

`server/scripts/test_dedupe_pnu_ledger_conflicts.py`:

```python
from dedupe_pnu_ledger_conflicts import delete_rows, find_conflicts, resolve_conflicts


def _write_chunk(path, rows):
    """rows: list of (id, pnu, license_no) 튜플. 나머지 컬럼은 고정값으로 채운다."""
    lines = [
        "INSERT INTO licensed_business_record "
        "(id, pnu, category, sub_category, license_no, business_name) VALUES"
    ]
    row_texts = [
        f"({r[0]}, '{r[1]}', '기타', '담배소매업', '{r[2]}', '테스트가게')" for r in rows
    ]
    with open(path, "w", encoding="utf-8") as f:
        f.write(lines[0] + "\n")
        f.write(",\n".join(row_texts))
        f.write(";\n")


def test_find_conflicts_같은_license_no_다른_pnu만_잡음(tmp_path):
    _write_chunk(tmp_path / "licensed-business-records-001.sql", [
        (1, "4113110100100010000", "dup-001"),
        (2, "4113110100200020000", "dup-001"),  # 같은 license_no, 다른 pnu
        (3, "4113110100300030000", "unique-001"),  # 충돌 없음
    ])

    conflicts = find_conflicts(str(tmp_path))

    assert set(conflicts.keys()) == {"dup-001"}
    assert len(conflicts["dup-001"]) == 2


def test_resolve_conflicts_ledger_pnu와_다른_행만_삭제_대상(tmp_path):
    _write_chunk(tmp_path / "licensed-business-records-001.sql", [
        (1, "4113110100100010000", "dup-001"),  # 이게 ledger 값과 다름 -> 삭제 대상
        (2, "4113110100200020000", "dup-001"),  # ledger 값과 일치 -> 유지
        (3, "4113110100999990000", "unresolved-001"),
        (4, "4113110100888880000", "unresolved-001"),  # ledger 미커버 -> 보류
    ])
    pnu_ledger = {"dup-001": {"pnu": "4113110100200020000", "address_corrected": False,
                               "road_masked": False, "jibun_masked": False}}

    ids_to_delete, unresolved = resolve_conflicts(str(tmp_path), pnu_ledger)

    assert ids_to_delete == {1}
    assert unresolved == ["unresolved-001"]


def test_delete_rows_행_삭제_후_남은_행만_유효한_SQL로_재기록(tmp_path):
    chunk_path = tmp_path / "licensed-business-records-001.sql"
    _write_chunk(chunk_path, [
        (1, "4113110100100010000", "dup-001"),
        (2, "4113110100200020000", "dup-001"),
        (3, "4113110100300030000", "unique-001"),
    ])

    deleted = delete_rows(str(tmp_path), {1})

    assert deleted == 1
    content = chunk_path.read_text(encoding="utf-8")
    assert "dup-001" in content  # id=2는 남아있음
    assert content.count("(1, '4113110100100010000'") == 0
    assert content.rstrip().endswith(";")  # 마지막 행 세미콜론 정상 유지
    assert ",\n(3," in content  # 콤마 구분 정상 유지


def test_delete_rows_청크의_모든_행이_삭제되면_파일_자체_삭제(tmp_path):
    chunk_path = tmp_path / "licensed-business-records-001.sql"
    _write_chunk(chunk_path, [
        (1, "4113110100100010000", "dup-001"),
    ])

    delete_rows(str(tmp_path), {1})

    assert not chunk_path.exists()
```

- [ ] **Step 2: 테스트가 실패하는지 확인**

Run: `cd server/scripts && python3 -m pytest -q test_dedupe_pnu_ledger_conflicts.py -v`
Expected: FAIL — `ModuleNotFoundError: No module named 'dedupe_pnu_ledger_conflicts'`

- [ ] **Step 3: 최소 구현 작성**

`server/scripts/dedupe_pnu_ledger_conflicts.py`:

```python
import glob
import os
import re
import sys
from collections import defaultdict

from pnu_ledger import load_pnu_ledger

_ROW_PATTERN = re.compile(r"^\((\d+), '([^']*)', '[^']*', '[^']*', '([^']*)',")


def find_conflicts(data_dir: str) -> dict[str, list[tuple[int, str, str]]]:
    """license_no -> [(id, pnu, chunk_filename), ...], pnu가 2종류 이상인 것만."""
    by_license: dict[str, list[tuple[int, str, str]]] = defaultdict(list)
    for path in sorted(glob.glob(os.path.join(data_dir, "licensed-business-records-*.sql"))):
        filename = os.path.basename(path)
        with open(path, encoding="utf-8") as f:
            for line in f:
                m = _ROW_PATTERN.match(line.strip())
                if m:
                    record_id, pnu, license_no = m.groups()
                    by_license[license_no].append((int(record_id), pnu, filename))
    return {
        license_no: entries
        for license_no, entries in by_license.items()
        if len({pnu for _, pnu, _ in entries}) > 1
    }


def resolve_conflicts(data_dir: str, pnu_ledger: dict[str, dict]) -> tuple[set[int], list[str]]:
    """반환: (삭제할 id 집합, ledger 미커버라 보류한 license_no 목록(정렬됨))."""
    conflicts = find_conflicts(data_dir)
    ids_to_delete: set[int] = set()
    unresolved: list[str] = []
    for license_no, entries in conflicts.items():
        ledger_entry = pnu_ledger.get(license_no)
        if ledger_entry is None:
            unresolved.append(license_no)
            continue
        authoritative_pnu = ledger_entry["pnu"]
        for record_id, pnu, _ in entries:
            if pnu != authoritative_pnu:
                ids_to_delete.add(record_id)
    return ids_to_delete, sorted(unresolved)


def delete_rows(data_dir: str, ids_to_delete: set[int]) -> int:
    """청크 파일에서 ids_to_delete에 해당하는 행을 제거하고 다시 쓴다.
    반환: 실제로 삭제된 행 수."""
    if not ids_to_delete:
        return 0
    deleted_count = 0
    for path in sorted(glob.glob(os.path.join(data_dir, "licensed-business-records-*.sql"))):
        with open(path, encoding="utf-8") as f:
            lines = f.read().splitlines()
        header = lines[0]
        kept_rows: list[str] = []
        file_changed = False
        for line in lines[1:]:
            stripped = line.rstrip(",;")
            m = _ROW_PATTERN.match(stripped)
            if m and int(m.group(1)) in ids_to_delete:
                deleted_count += 1
                file_changed = True
                continue
            kept_rows.append(stripped)
        if not file_changed:
            continue
        if not kept_rows:
            os.remove(path)
            continue
        with open(path, "w", encoding="utf-8") as f:
            f.write(header + "\n")
            f.write(",\n".join(kept_rows))
            f.write(";\n")
    return deleted_count


if __name__ == "__main__":
    if len(sys.argv) != 3:
        print("usage: dedupe_pnu_ledger_conflicts.py <data_dir> <ledger_csv_path>")
        sys.exit(1)
    data_dir, ledger_path = sys.argv[1], sys.argv[2]
    pnu_ledger = load_pnu_ledger(ledger_path)
    ids_to_delete, unresolved = resolve_conflicts(data_dir, pnu_ledger)
    deleted = delete_rows(data_dir, ids_to_delete)
    print(f"삭제된 행: {deleted}")
    print(f"ledger 미커버로 보류된 license_no: {len(unresolved)}건")
    if unresolved:
        report_path = os.path.join(data_dir, "unresolved-pnu-conflicts.txt")
        with open(report_path, "w", encoding="utf-8") as f:
            f.write("\n".join(unresolved))
        print(f"보류 목록: {report_path}")
```

- [ ] **Step 4: 테스트가 통과하는지 확인**

Run: `cd server/scripts && python3 -m pytest -q -v`
Expected: 기존 38개 + 신규 5개 = `43 passed`

- [ ] **Step 5: 커밋**

```bash
cd server
git add scripts/dedupe_pnu_ledger_conflicts.py scripts/test_dedupe_pnu_ledger_conflicts.py
git commit -m "feat: add one-off cleanup for existing license_no/pnu conflicts"
```

---

### Task 5: 실제 데이터에 적용 — 정리 + 전체 재적재

**Files:**
- 코드 변경 없음(Task 1~4의 스크립트를 실행만 함). 데이터 파일(`server/src/main/resources/data/*.sql`)과
  테스트 카운트 어서션(`server/src/test/java/com/nextstep/infra/persistence/PersistenceSmokeTest.java`,
  `server/src/test/java/com/nextstep/web/SiteControllerTest.java`)이 바뀐다.

**Interfaces:**
- Consumes: Task 1~4의 모든 함수.

- [ ] **Step 1: 기존 충돌 정리 실행**

Run:
```bash
cd server/scripts
python3 dedupe_pnu_ledger_conflicts.py \
  ../src/main/resources/data \
  "../../data_uncleaning/PNU(지번)기반_개폐업정보현황_성남시_10년.csv"
```
Expected: `삭제된 행: <0보다 큰 정수>`가 출력됨(2026-07-18 브레인스토밍 시점 실측 1,630쌍 기준 —
이후 14차 배치로 데이터가 더 늘었으니 정확한 값은 다를 수 있음, 0이면 뭔가 잘못됐다는 신호이니
Task 1~4를 재검토).

- [ ] **Step 2: 성남시(`data/경기도`) + 서울(`data/서울특별시`) 전체 재적재**

**주의**: 이 스텝 전에 `server/src/main/resources/data/`에서 2026-07-18 14차 배치로 생성된 청크를
전부 지워야 한다(그 데이터는 이번 ledger 정정 *이전* 것이라 최종본이 아님 — 원래 있던 93개
청크는 남기고 그 이후 것만 지운다):

```bash
cd server
python3 -c "
import glob, os, re
files = glob.glob('src/main/resources/data/licensed-business-records-*.sql')
to_delete = [f for f in files if int(re.search(r'-(\d+)\.sql$', f).group(1)) > 93]
print('삭제 대상:', len(to_delete))
for f in to_delete:
    os.remove(f)
"
```

그다음 ledger를 붙여서 재실행(경기도 처리 시엔 ledger 적용, 서울은 이 파일이 안 커버하므로
같이 넘겨도 자동으로 무해하게 무시됨 — Task 3 Step 5 문서 참고):

```bash
cd server
python3 scripts/batch_parse_licensed_records.py \
  --ledger "../data_uncleaning/PNU(지번)기반_개폐업정보현황_성남시_10년.csv" \
  src/main/resources/data data/경기도 data/서울특별시
```

Expected: 약 13분 소요(2026-07-18 14차 배치 실측 기준). 완료 후
`data/경기도/batch-run-summary.txt`에 "총 생성 레코드"가 찍힘 — 정확한 숫자는 ledger가
UNPARSEABLE 행을 얼마나 구제했는지에 따라 14차의 1,514,685건과 다를 수 있음(더 많아야 정상).

이 커맨드는 오래 걸리므로 `run_in_background: true`로 실행하고 완료 알림을 기다릴 것 —
타임아웃으로 "moved to background"가 뜨는 상태에서 같은 명령을 또 실행하면 두 프로세스가
동시에 같은 출력 디렉터리에 쓰면서 PRIMARY KEY 충돌이 난다(2026-07-18 세션에서 실제로 겪음).
반드시 완료 알림을 받은 뒤에만 다음 스텝으로 진행할 것.

- [ ] **Step 3: PNU 20자 초과·잘못된 날짜 등 회귀 확인**

Run:
```bash
cd server
grep -lE "\([0-9]+, '[0-9]{20,}'" src/main/resources/data/*.sql | wc -l
```
Expected: `0`

- [ ] **Step 4: `mvn test` 실행 — 하드코딩된 카운트 어서션 갱신**

Run: `cd server && mvn -q test -Dsurefire.jvm.args="-Xmx4096m"`
Expected: 처음엔 FAIL — `PersistenceSmokeTest`의 `시드_데이터가_전부_로드된다()`(현재 155,364건
기대)와 `주소로_csv_원본행을_검색한다()`(현재 8,319건 기대), `SiteControllerTest`의
`신흥동으로_검색하면_후보가_나온다()`(현재 1741건 기대) — 실제 새 레코드 수로 값을 갱신해야 함
(2026-07-18 세션에서 이미 두 번 겪은 패턴, `PersistenceSmokeTest.java:16,31`,
`SiteControllerTest.java:104`).

실패 메시지에 찍히는 `but was: <실제값>`을 그대로 테스트 코드에 반영한 뒤 재실행:

Run: `cd server && mvn -q test -Dsurefire.jvm.args="-Xmx4096m"`
Expected: `BUILD SUCCESS`, 76개 테스트 전부 통과.

- [ ] **Step 5: 커밋**

```bash
cd server
git add src/main/resources/data/ src/test/java/com/nextstep/infra/persistence/PersistenceSmokeTest.java src/test/java/com/nextstep/web/SiteControllerTest.java
git commit -m "data: reingest with PNU ledger correction, dedupe existing conflicts"
```

---

### Task 6: 스펙·문서 최종 반영

**Files:**
- Modify: `D:\Dev\_Woowahan-Techcourse\woowaTon\spec\CHANGELOG.md`
- Modify: `D:\Dev\_Woowahan-Techcourse\woowaTon\ter-view\spec\CHANGELOG.md`(root와 동기화)

**Interfaces:** 없음(문서 전용 태스크).

- [ ] **Step 1: 15차 항목을 "구현 대기" → "완료"로 갱신**

`D:\Dev\_Woowahan-Techcourse\woowaTon\spec\CHANGELOG.md`의 15차 항목(`### 2026-07-18 (15차) — PNU 권위 파일 조인 정정 설계 승인 (구현 대기)`)에서:
- 제목의 `(구현 대기)`를 `(구현 완료)`로 변경.
- 마지막 문단("**구현은 아직 안 됨**...")을 Task 5에서 확인한 실제 삭제 행 수·재적재 후 총
  레코드 수로 교체.

- [ ] **Step 2: 루트 spec을 ter-view로 동기화**

Run:
```bash
cd "D:\Dev\_Woowahan-Techcourse\woowaTon"
cp spec/CHANGELOG.md ter-view/spec/
diff -q spec/CHANGELOG.md ter-view/spec/CHANGELOG.md && echo SYNCED
```
Expected: `SYNCED`

- [ ] **Step 3: 커밋** (server 레포 기준 — 루트 `spec/`은 git 미관리이므로 파일만 저장,
  server 레포에 걸친 변경은 없으므로 이 스텝은 생략 가능. `docs/superpowers/plans/`
  아래 이 계획 파일 자체를 커밋)

```bash
cd server
git add docs/superpowers/plans/2026-07-18-pnu-ledger-correction.md docs/superpowers/specs/2026-07-18-pnu-ledger-correction-design.md
git commit -m "docs: add PNU ledger correction design and implementation plan"
```

---

## Self-Review 체크리스트 (실행자 참고용)

- **스펙 커버리지**: 설계 문서의 "변경 파일" 표 6개 파일 전부 Task 1~4에 대응(`pnu_ledger.py`,
  `test_pnu_ledger.py` → Task 1; `parse_licensed_records.py`, `test_parse_licensed_records.py`
  → Task 2; `dedupe_pnu_ledger_conflicts.py`, `test_dedupe_pnu_ledger_conflicts.py` → Task 4).
  `batch_parse_licensed_records.py` 배선은 설계 문서에 암묵적으로 전제돼 있었고 Task 3에서 명시.
  "실행 순서" 3단계(정리 → 재적재 → mvn test)는 Task 5로 그대로 매핑.
- **플레이스홀더 없음**: 모든 스텝에 실제 코드/커맨드/기대출력 포함.
- **타입 일관성**: `pnu_ledger: dict[str, dict] | None` 시그니처가 Task 1(생산)→Task 2/3(소비)→
  Task 4(재사용)에서 동일하게 유지됨. `parse_file`/`run_batch` 반환값(`tuple[int, Counter]`/`None`)
  변경 없음.
