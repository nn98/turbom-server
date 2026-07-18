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
