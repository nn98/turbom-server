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
