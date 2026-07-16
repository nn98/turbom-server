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
