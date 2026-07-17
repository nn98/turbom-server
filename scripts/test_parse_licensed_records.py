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
