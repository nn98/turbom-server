from dedupe_pnu_ledger_conflicts import delete_rows, find_conflicts, resolve_conflicts


def _write_chunk(path, rows):
    """rows: list of (id, pnu, license_no) tuples. Fill remaining columns with fixed values."""
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
        (2, "4113110100200020000", "dup-001"),  # same license_no, different pnu
        (3, "4113110100300030000", "unique-001"),  # no conflict
    ])

    conflicts = find_conflicts(str(tmp_path))

    assert set(conflicts.keys()) == {"dup-001"}
    assert len(conflicts["dup-001"]) == 2


def test_resolve_conflicts_ledger_pnu와_다른_행만_삭제_대상(tmp_path):
    _write_chunk(tmp_path / "licensed-business-records-001.sql", [
        (1, "4113110100100010000", "dup-001"),  # differs from ledger pnu -> delete target
        (2, "4113110100200020000", "dup-001"),  # matches ledger pnu -> keep
        (3, "4113110100999990000", "unresolved-001"),
        (4, "4113110100888880000", "unresolved-001"),  # ledger does not cover -> defer
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
    assert "dup-001" in content  # id=2 should remain
    assert content.count("(1, '4113110100100010000'") == 0
    assert content.rstrip().endswith(";")  # final semicolon preserved
    assert ",\n(3," in content  # comma separation preserved


def test_delete_rows_청크의_모든_행이_삭제되면_파일_자체_삭제(tmp_path):
    chunk_path = tmp_path / "licensed-business-records-001.sql"
    _write_chunk(chunk_path, [
        (1, "4113110100100010000", "dup-001"),
    ])

    delete_rows(str(tmp_path), {1})

    assert not chunk_path.exists()
