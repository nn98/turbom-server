import glob
import os
import re
import sys
from collections import defaultdict

from pnu_ledger import load_pnu_ledger

_ROW_PATTERN = re.compile(r"^\((\d+), '([^']*)', '[^']*', '[^']*', '([^']*)',")


def find_conflicts(data_dir: str) -> dict[str, list[tuple[int, str, str]]]:
    """license_no -> [(id, pnu, chunk_filename), ...], pnu가 2종류 이상인 것만."""
    by_license: dict[str, list[tuple[int, str, str]]] = defaultdict(list)
    for path in sorted(glob.glob(os.path.join(data_dir, "licensed-business-records-*.sql"))):
        filename = os.path.basename(path)
        with open(path, encoding="utf-8") as f:
            for line in f:
                m = _ROW_PATTERN.match(line.strip())
                if m:
                    record_id, pnu, license_no = m.groups()
                    by_license[license_no].append((int(record_id), pnu, filename))
    return {
        license_no: entries
        for license_no, entries in by_license.items()
        if len({pnu for _, pnu, _ in entries}) > 1
    }


def resolve_conflicts(data_dir: str, pnu_ledger: dict[str, dict]) -> tuple[set[int], list[str]]:
    """반환: (삭제할 id 집합, ledger 미커버라 보류한 license_no 목록(정렬됨))."""
    conflicts = find_conflicts(data_dir)
    ids_to_delete: set[int] = set()
    unresolved: list[str] = []
    for license_no, entries in conflicts.items():
        ledger_entry = pnu_ledger.get(license_no)
        if ledger_entry is None:
            unresolved.append(license_no)
            continue
        authoritative_pnu = ledger_entry["pnu"]
        for record_id, pnu, _ in entries:
            if pnu != authoritative_pnu:
                ids_to_delete.add(record_id)
    return ids_to_delete, sorted(unresolved)


def delete_rows(data_dir: str, ids_to_delete: set[int]) -> int:
    """청크 파일에서 ids_to_delete에 해당하는 행을 제거하고 다시 쓴다.
    반환: 실제로 삭제된 행 수."""
    if not ids_to_delete:
        return 0
    deleted_count = 0
    for path in sorted(glob.glob(os.path.join(data_dir, "licensed-business-records-*.sql"))):
        with open(path, encoding="utf-8") as f:
            lines = f.read().splitlines()
        header = lines[0]
        kept_rows: list[str] = []
        file_changed = False
        for line in lines[1:]:
            stripped = line.rstrip(",;")
            m = _ROW_PATTERN.match(stripped)
            if m and int(m.group(1)) in ids_to_delete:
                deleted_count += 1
                file_changed = True
                continue
            kept_rows.append(stripped)
        if not file_changed:
            continue
        if not kept_rows:
            os.remove(path)
            continue
        with open(path, "w", encoding="utf-8") as f:
            f.write(header + "\n")
            f.write(",\n".join(kept_rows))
            f.write(";\n")
    return deleted_count


if __name__ == "__main__":
    if len(sys.argv) != 3:
        print("usage: dedupe_pnu_ledger_conflicts.py <data_dir> <ledger_csv_path>")
        sys.exit(1)
    data_dir, ledger_path = sys.argv[1], sys.argv[2]
    pnu_ledger = load_pnu_ledger(ledger_path)
    ids_to_delete, unresolved = resolve_conflicts(data_dir, pnu_ledger)
    deleted = delete_rows(data_dir, ids_to_delete)
    print(f"삭제된 행: {deleted}")
    print(f"ledger 미커버로 보류된 license_no: {len(unresolved)}건")
    if unresolved:
        report_path = os.path.join(data_dir, "unresolved-pnu-conflicts.txt")
        with open(report_path, "w", encoding="utf-8") as f:
            f.write("\n".join(unresolved))
        print(f"보류 목록: {report_path}")
