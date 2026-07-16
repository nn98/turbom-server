import csv
import os
import re
import sys
from collections import Counter

from jibun_pnu import load_legaldong_codes, parse_pnu

CHUNK_SIZE = 1000
FILENAME_PATTERN = re.compile(r"^(?P<category>[^_]+)_(?P<sub_category>[^_]+)_[^_]+\.csv$")
# ponytail: 이 프로젝트는 성남시 한정(CLAUDE.md 데이터축)이라 하드코딩. 다른 지역 파일이
# 추가되면 파일명의 "지역" 세그먼트에서 유도하도록 확장.
REGION_FILTER = "성남시"


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


def sql_date(value: str | None) -> str:
    if not value:
        return "NULL"
    return f"'{value}'"


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
        sql_date(row["폐업일자"]),
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


def parse_file(csv_path: str, output_dir: str, start_id: int) -> None:
    category, sub_category = derive_category(csv_path)
    all_legaldong_codes = load_legaldong_codes(
        os.path.join(os.path.dirname(__file__), "legaldong_codes.csv")
    )
    # 동 이름은 전국적으로 겹치는 경우가 흔하다(예: "태평동"이 성남시 외 4개 도시에도 존재).
    # 지역으로 후보를 좁히지 않으면 엉뚱한 도시의 PNU가 나올 수 있어 반드시 스코프를 좁힌다.
    legaldong_codes = {
        name: code for name, code in all_legaldong_codes.items() if REGION_FILTER in name
    }

    skip_reasons: Counter = Counter()
    rows: list[str] = []
    record_id = start_id

    with open(csv_path, encoding="cp949") as f:
        reader = csv.DictReader(f)
        for row in reader:
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


if __name__ == "__main__":
    if len(sys.argv) != 4:
        print("usage: parse_licensed_records.py <csv_path> <output_dir> <start_id>")
        sys.exit(1)
    parse_file(sys.argv[1], sys.argv[2], int(sys.argv[3]))
