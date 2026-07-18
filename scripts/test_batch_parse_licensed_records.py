import csv

from batch_parse_licensed_records import next_start_id, run_batch
from pnu_ledger import load_pnu_ledger

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


def _row(license_no, gov_code="3780000"):
    return {
        "개방자치단체코드": gov_code, "관리번호": license_no,
        "사업장명": "테스트가게", "영업상태명": "영업/정상",
        "상세영업상태코드": "01", "상세영업상태명": "영업",
        "인허가일자": "2020-01-01", "폐업일자": "",
        "도로명주소": "경기도 성남시 수정구 태평동", "지번주소": "경기도 성남시 수정구 태평동 2254",
        "좌표정보(X)": "211488.6", "좌표정보(Y)": "438155.3",
    }


def test_next_start_id_출력_디렉토리_없으면_1(tmp_path):
    assert next_start_id(str(tmp_path / "no-such-dir")) == 1


def test_next_start_id_기존_청크_최대id_다음값(tmp_path):
    chunk = tmp_path / "licensed-business-records-001.sql"
    chunk.write_text(
        "INSERT INTO licensed_business_record (id, pnu) VALUES\n(41, 'x'),\n(42, 'y');\n",
        encoding="utf-8",
    )
    assert next_start_id(str(tmp_path)) == 43


def test_run_batch_여러_파일에_걸쳐_id를_이어받고_요약로그를_남김(tmp_path):
    src_a = tmp_path / "경기도"
    src_a.mkdir()
    _write_csv(src_a / "식품_일반음식점.csv", [_row("a-001"), _row("a-002")])

    src_b = tmp_path / "부산광역시"
    src_b.mkdir()
    _write_csv(src_b / "식품_일반음식점.csv", [_row("b-001", gov_code="2635000")])  # 스코프 밖(NOT_IN_SCOPE)

    output_dir = tmp_path / "out"
    output_dir.mkdir()
    log_path = tmp_path / "summary.txt"

    run_batch(str(output_dir), [str(src_a), str(src_b)], str(log_path))

    chunk_files = list(output_dir.glob("*.sql"))
    assert len(chunk_files) == 1
    content = chunk_files[0].read_text(encoding="utf-8")
    assert "a-001" in content and "a-002" in content

    summary = log_path.read_text(encoding="utf-8")
    assert "총 생성 레코드: 2" in summary
    assert "NOT_IN_SCOPE: 1" in summary


def test_run_batch_한_파일이_깨져도_나머지는_계속_처리됨(tmp_path):
    src = tmp_path / "경기도"
    src.mkdir()
    # 필수 컬럼(사업장명 등)이 아예 없는 깨진 CSV -> parse_file 내부에서 KeyError 발생
    (src / "이상한_카테고리.csv").write_text("어떤,헤더\n1,2\n", encoding="cp949")
    _write_csv(src / "식품_일반음식점.csv", [_row("ok-001")])

    output_dir = tmp_path / "out"
    output_dir.mkdir()
    log_path = tmp_path / "summary.txt"

    run_batch(str(output_dir), [str(src)], str(log_path))

    chunk_files = list(output_dir.glob("*.sql"))
    assert len(chunk_files) == 1
    assert "ok-001" in chunk_files[0].read_text(encoding="utf-8")

    summary = log_path.read_text(encoding="utf-8")
    assert "이상한_카테고리.csv" in summary
    assert "파일 자체 오류로 처리 실패한 파일 (1개)" in summary


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
