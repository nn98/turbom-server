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


def test_스코프_밖_지역은_지번_파싱_없이_스킵됨(tmp_path, capsys):
    # 부산은 성남시도 서울도 아니라 스코프 밖 - REGION_FILTERS/ACCEPTED_GOV_CODES 둘 다 해당 없음.
    csv_path = tmp_path / "식품_일반음식점.csv"
    _write_csv(csv_path, [
        _make_row("2635000", "busan-001", "부산광역시 해운대구 1", "부산광역시 해운대구 우동 1"),
    ])
    output_dir = tmp_path / "out"
    parse_file(str(csv_path), str(output_dir), 1)

    out = capsys.readouterr().out
    assert "생성된 레코드: 0" in out
    assert "NOT_IN_SCOPE: 1" in out
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


def test_서울_구_코드_행도_정상_처리됨(tmp_path, capsys):
    # 서울 25개 구 코드 중 하나(3000000 - 개방자치단체코드는 행정표준코드와 다른 체계,
    # 2026-07-18 서울 195개 원본 파일 전수 스캔으로 확인한 실제 값) - 서울 스코프 확장.
    csv_path = tmp_path / "식품_일반음식점.csv"
    _write_csv(csv_path, [
        _make_row("3000000", "seoul-001", "서울특별시 종로구 청운동", "서울특별시 종로구 청운동 1"),
    ])
    output_dir = tmp_path / "out"
    parse_file(str(csv_path), str(output_dir), 1)

    out = capsys.readouterr().out
    assert "생성된 레코드: 1" in out
    chunk_files = list(output_dir.glob("*.sql"))
    assert len(chunk_files) == 1
    assert "seoul-001" in chunk_files[0].read_text(encoding="utf-8")


def test_서울시청_직접_코드_행도_정상_처리됨(tmp_path, capsys):
    # 후원방문판매업 등 구가 아닌 서울시 본청 발급 인허가 - 실측 코드 6110000(2026-07-18 발견).
    csv_path = tmp_path / "생활_후원방문판매업체.csv"
    _write_csv(csv_path, [
        _make_row("6110000", "seoul-city-001", "서울특별시 강남구 삼성동", "서울특별시 강남구 삼성동 1"),
    ])
    output_dir = tmp_path / "out"
    parse_file(str(csv_path), str(output_dir), 1)

    out = capsys.readouterr().out
    assert "생성된 레코드: 1" in out


def test_폐업일자가_존재하지_않는_달력날짜여도_행은_살리고_NULL_처리(tmp_path, capsys):
    # 실사례(2026-07-18 서울 스코프 확장 중 발견): 서대문구 인터넷컴퓨터게임시설제공업
    # 데이터의 폐업일자가 "20090229"(2009년은 윤년이 아니라 2/29 자체가 존재하지 않음,
    # 하이픈도 없음) - H2가 통째로 파싱 실패해서 시드 스크립트 전체가 죽었었다.
    # 인허가일자는 멀쩡하니 행을 버리지 않고 closed_at만 NULL로 처리한다.
    csv_path = tmp_path / "문화_인터넷컴퓨터게임시설제공업.csv"
    row = _make_row("3780000", "seongnam-baddate", "경기도 성남시 수정구 태평동", "경기도 성남시 수정구 태평동 2254")
    row["폐업일자"] = "20090229"
    _write_csv(csv_path, [row])
    output_dir = tmp_path / "out"

    parse_file(str(csv_path), str(output_dir), 1)

    out = capsys.readouterr().out
    assert "생성된 레코드: 1" in out
    chunk = list(output_dir.glob("*.sql"))[0].read_text(encoding="utf-8")
    assert "seongnam-baddate" in chunk
    row_text = [line for line in chunk.splitlines() if "seongnam-baddate" in line][0]
    assert "20090229" not in row_text


def test_인허가일자가_존재하지_않는_달력날짜면_행_자체를_스킵(tmp_path, capsys):
    csv_path = tmp_path / "식품_일반음식점.csv"
    row = _make_row("3780000", "seongnam-badlicense", "경기도 성남시 수정구 태평동", "경기도 성남시 수정구 태평동 2254")
    row["인허가일자"] = "20090229"
    _write_csv(csv_path, [row])
    output_dir = tmp_path / "out"

    parse_file(str(csv_path), str(output_dir), 1)

    out = capsys.readouterr().out
    assert "생성된 레코드: 0" in out
    assert "NO_LICENSED_AT: 1" in out


def test_하이픈_없는_YYYYMMDD_형식의_정상날짜는_그대로_인정(tmp_path, capsys):
    csv_path = tmp_path / "식품_일반음식점.csv"
    row = _make_row("3780000", "seongnam-yyyymmdd", "경기도 성남시 수정구 태평동", "경기도 성남시 수정구 태평동 2254")
    row["인허가일자"] = "20200101"
    _write_csv(csv_path, [row])
    output_dir = tmp_path / "out"

    parse_file(str(csv_path), str(output_dir), 1)

    out = capsys.readouterr().out
    assert "생성된 레코드: 1" in out


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


def test_폐업일자_컬럼_자체가_없는_원본도_처리됨(tmp_path, capsys):
    # 서울/경기 원목생산업 등 6개 파일은 폐업일자 컬럼이 원본에 아예 없음(2026-07-18 확인).
    fieldnames = [f for f in _FIELDNAMES if f != "폐업일자"]
    csv_path = tmp_path / "자원환경_원목생산업.csv"
    with open(csv_path, "w", encoding="cp949", newline="") as f:
        writer = csv.DictWriter(f, fieldnames=fieldnames)
        writer.writeheader()
        writer.writerow({
            "개방자치단체코드": "3780000", "관리번호": "seongnam-002",
            "사업장명": "테스트임업", "영업상태명": "영업/정상",
            "상세영업상태코드": "01", "상세영업상태명": "영업",
            "인허가일자": "2020-01-01",
            "도로명주소": "경기도 성남시 수정구 태평동", "지번주소": "경기도 성남시 수정구 태평동 2254",
            "좌표정보(X)": "211488.6", "좌표정보(Y)": "438155.3",
        })
    output_dir = tmp_path / "out"

    parse_file(str(csv_path), str(output_dir), 1)

    out = capsys.readouterr().out
    assert "생성된 레코드: 1" in out
    chunk = list(output_dir.glob("*.sql"))[0].read_text(encoding="utf-8")
    assert "seongnam-002" in chunk


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
