import csv
import datetime
import os
import re
import sys
from collections import Counter

from jibun_pnu import load_legaldong_codes, parse_pnu

CHUNK_SIZE = 1000
FILENAME_PATTERN = re.compile(r"^(?P<category>[^_]+)_(?P<sub_category>[^_]+)(_[^_]+)?\.csv$")
# 이 프로젝트의 데이터축(CLAUDE.md §1): 성남시 수정구+분당 일부, 2026-07-18부로 서울 전체 추가.
# 다른 지역이 더 필요해지면 여기 문자열만 추가(legaldong_codes.csv엔 전국 데이터가 이미 있음).
REGION_FILTERS = ["성남시", "서울특별시"]
SEONGNAM_GOV_CODE = "3780000"
# 서울 25개 구 코드(3000000~3240000, 만 단위 증가) + 구가 아닌 서울시 본청 직접발급 코드
# (6110000 — 후원방문판매업 등). 2026-07-18 서울 195개 원본 파일 전수 스캔으로 확인한 값.
SEOUL_GOV_CODES = {str(3_000_000 + i * 10_000) for i in range(25)} | {"6110000"}
# 지번주소 파싱을 시도하기 전에 거르는 성능용 사전 필터 — 실측 230만 행 중 99%가
# 이 필터 하나로 즉시 스킵됨(스코프 안 비중이 원래 작음).
ACCEPTED_GOV_CODES = {SEONGNAM_GOV_CODE} | SEOUL_GOV_CODES


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


def _valid_date_or_none(value: str | None) -> str | None:
    value = (value or "").strip()
    if not value:
        return None
    # 일부 원본(주로 서울 취소/말소 이력)은 하이픈 없는 YYYYMMDD로 옴 - 둘 다 받는다.
    # 존재하지 않는 달력 날짜(예: "20090229" - 2009년은 윤년이 아님)는 걸러낸다 -
    # 그대로 SQL에 넣으면 H2가 그 청크 전체를, 결국 시드 스크립트 전체를 실패시킨다
    # (2026-07-18 서울 스코프 확장 중 실제로 겪음).
    for fmt in ("%Y-%m-%d", "%Y%m%d"):
        try:
            datetime.datetime.strptime(value, fmt)
            return value
        except ValueError:
            continue
    return None


def sql_date(value: str | None) -> str:
    valid = _valid_date_or_none(value)
    if valid is None:
        return "NULL"
    return f"'{valid}'"


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
        sql_date(row.get("폐업일자")),
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


def parse_file(csv_path: str, output_dir: str, start_id: int,
                pnu_ledger: dict[str, dict] | None = None) -> tuple[int, Counter]:
    category, sub_category = derive_category(csv_path)
    all_legaldong_codes = load_legaldong_codes(
        os.path.join(os.path.dirname(__file__), "legaldong_codes.csv")
    )
    # 동 이름은 전국적으로 겹치는 경우가 흔하다(예: "태평동"이 성남시 외 4개 도시에도 존재,
    # "갈현동"이 성남시 중원구와 서울 은평구에 둘 다 존재). 지역으로 후보를 좁히지 않으면
    # 엉뚱한 도시의 PNU가 나올 수 있어 반드시 스코프를 좁힌다(구/시 단위 교차 검증은
    # jibun_pnu.parse_pnu가 담당).
    legaldong_codes = {
        name: code for name, code in all_legaldong_codes.items()
        if any(region in name for region in REGION_FILTERS)
    }
    existing_license_nos = load_existing_license_nos(output_dir)

    skip_reasons: Counter = Counter()
    rows: list[str] = []
    record_id = start_id

    with open(csv_path, encoding="cp949") as f:
        reader = csv.DictReader(f)
        for row in reader:
            if row["개방자치단체코드"] not in ACCEPTED_GOV_CODES:
                skip_reasons["NOT_IN_SCOPE"] += 1
                continue
            if row["관리번호"] in existing_license_nos:
                skip_reasons["DUPLICATE_LICENSE_NO"] += 1
                continue
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
            if _valid_date_or_none(row["인허가일자"]) is None:
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

    return record_id, skip_reasons


if __name__ == "__main__":
    if len(sys.argv) != 4:
        print("usage: parse_licensed_records.py <csv_path> <output_dir> <start_id>")
        sys.exit(1)
    parse_file(sys.argv[1], sys.argv[2], int(sys.argv[3]))
