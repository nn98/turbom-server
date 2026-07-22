"""
자체 정합성 감사: 저장된 pnu의 산여부 자리가, 그 행 자신의 jibun_address를 현재
jibun_pnu 파싱 규칙으로 재파싱했을 때 나오는 값과 일치하는지 확인한다.
외부 권위 파일(ledger CSV) 없이, 같은 텍스트를 같은 규칙으로 다시 돌려서
자기모순만 잡아내는 감사 — "산" 글자가 텍스트에 없는데 pnu는 산여부=1인 행을 찾는다.
2026-07-22 세션에서 8,121개 필지 충돌(운중동 1034 등, `의사결정-기록.md` §14) 재발을
조사하며 작성.

jibun_pnu.parse_pnu()는 legaldong_codes를 매 호출마다 다시 정렬해서(20,544개 키를
행 158만 건마다 재정렬) 대량 배치엔 너무 느리다(158만 행 기준 20분+) — 정렬을 한 번만
하고, 이 프로젝트 적재 스코프(성남시+서울 25구, parse_licensed_records.py의
ACCEPTED_GOV_CODES와 동일)로 법정동 후보를 541개로 좁혀 77.6초까지 줄였다(판정 로직
자체는 원본과 동일 — 3,000행 샘플로 결과 일치 확인).

입력 준비:
  mysql --defaults-extra-file=~/.my.cnf turbom -N \
    -e "SELECT id, pnu, jibun_address FROM licensed_business_record" \
    > /tmp/pnu_audit_source.tsv
실행: python3 scripts/audit_san_conflicts.py
출력: 요약 통계 + 불일치 샘플을 표준출력에 인쇄, 전체 불일치 목록은 /tmp/pnu_san_conflicts.csv로 저장.
"""
import csv
import os
import re
import sys
import time

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from jibun_pnu import load_legaldong_codes  # noqa: E402

_SCRIPT_DIR = os.path.dirname(os.path.abspath(__file__))
LEGALDONG_CSV = os.path.join(_SCRIPT_DIR, "legaldong_codes.csv")
SOURCE_TSV = "/tmp/pnu_audit_source.tsv"
OUTPUT_CSV = "/tmp/pnu_san_conflicts.csv"

_TAIL_PATTERN = re.compile(r'^\s*(산)?\s*(\d+)(-(\d+))?')


# 이 프로젝트의 적재 스코프(scripts/parse_licensed_records.py의 ACCEPTED_GOV_CODES)와 동일 —
# 성남시 전체 + 서울 25개 구. 우리 데이터의 모든 jibun_address는 이미 이 범위 안에서만
# 나오므로(적재 시점에 이미 필터링됨), 후보를 이 범위로 좁혀도 감사 결과는 안 바뀌고
# 법정동 20,544개 → 541개로 줄어 스캔이 훨씬 빨라진다(3000행 샘플로 전체 스캔 버전과
# 결과 동일함을 확인함).
_SCOPE_PREFIXES = ("경기도 성남시", "서울특별시")


def build_dong_index(legaldong_codes: dict[str, str]) -> list[tuple[str, str, str | None]]:
    """(dong_short, code, gu_or_si) 튜플을 dong_full_name 길이 내림차순으로 한 번만 정렬."""
    scoped = {k: v for k, v in legaldong_codes.items() if k.startswith(_SCOPE_PREFIXES)}
    index = []
    for dong_full_name in sorted(scoped, key=len, reverse=True):
        tokens = dong_full_name.split(" ")
        dong_short = tokens[-1]
        gu_or_si = tokens[-2] if len(tokens) >= 2 else None
        index.append((dong_short, legaldong_codes[dong_full_name], gu_or_si))
    return index


def parse_pnu_fast(address: str, dong_index: list[tuple[str, str, str | None]]) -> str | None:
    address = address.strip()
    if not address:
        return None
    for dong_short, code, gu_or_si in dong_index:
        idx = address.find(dong_short)
        if idx == -1:
            continue
        if gu_or_si and gu_or_si not in address:
            continue
        tail = address[idx + len(dong_short):]
        match = _TAIL_PATTERN.match(tail)
        if not match:
            continue
        is_san = match.group(1) is not None
        bon = int(match.group(2))
        bu = int(match.group(4)) if match.group(4) else 0
        if bon > 9999 or bu > 9999:
            return None
        return code + ("1" if is_san else "0") + f"{bon:04d}" + f"{bu:04d}"
    return None


def main() -> None:
    start = time.time()
    legaldong_codes = load_legaldong_codes(LEGALDONG_CSV)
    dong_index = build_dong_index(legaldong_codes)
    print(f"법정동 인덱스 {len(dong_index)}개 준비 완료 ({time.time() - start:.1f}s)")

    total = 0
    reparse_failed = 0
    matches = 0
    san_only_conflict = 0
    other_conflict = 0

    conflicts: list[tuple[str, str, str, str]] = []  # id, stored_pnu, derived_pnu, jibun_address

    with open(SOURCE_TSV, encoding="utf-8", newline="") as f:
        reader = csv.reader(f, delimiter="\t")
        for row in reader:
            if len(row) != 3:
                continue
            record_id, stored_pnu, jibun_address = row
            total += 1
            if total % 200000 == 0:
                print(f"  ...{total}행 처리, {time.time() - start:.0f}s 경과")

            derived_pnu = parse_pnu_fast(jibun_address, dong_index)
            if derived_pnu is None:
                reparse_failed += 1
                continue

            if derived_pnu == stored_pnu:
                matches += 1
                continue

            same_lot = (stored_pnu[:10] + stored_pnu[11:] == derived_pnu[:10] + derived_pnu[11:])
            if same_lot and stored_pnu[10] != derived_pnu[10]:
                san_only_conflict += 1
                conflicts.append((record_id, stored_pnu, derived_pnu, jibun_address))
            else:
                other_conflict += 1

    elapsed = time.time() - start
    print(f"\n총 처리 시간: {elapsed:.1f}s")
    print(f"전체 행: {total}")
    print(f"재파싱 실패(UNPARSEABLE 등, 감사 대상 아님): {reparse_failed}")
    print(f"저장값과 재파싱값 일치: {matches}")
    print(f"산여부만 불일치(자기모순, 수정 대상 후보): {san_only_conflict}")
    print(f"그 외 불일치(법정동/본번/부번 자체가 다름 — 별개 문제, 이번엔 안 건드림): {other_conflict}")

    with open(OUTPUT_CSV, "w", encoding="utf-8", newline="") as f:
        writer = csv.writer(f)
        writer.writerow(["id", "stored_pnu", "derived_pnu", "jibun_address"])
        writer.writerows(conflicts)
    print(f"\n산여부 불일치 전체 목록: {OUTPUT_CSV} ({len(conflicts)}건)")

    print("\n샘플 10건:")
    for record_id, stored_pnu, derived_pnu, jibun_address in conflicts[:10]:
        print(f"  id={record_id} stored={stored_pnu} derived={derived_pnu} addr={jibun_address!r}")


if __name__ == "__main__":
    main()
