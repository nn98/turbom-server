import glob
import os
import re
import sys
import time
from collections import Counter

from parse_licensed_records import parse_file
from pnu_ledger import load_pnu_ledger

_CHUNK_ID_PATTERN = re.compile(r"^\((\d+),")


def next_start_id(output_dir: str) -> int:
    max_id = 0
    if not os.path.isdir(output_dir):
        return 1
    for filename in os.listdir(output_dir):
        if not filename.startswith("licensed-business-records-") or not filename.endswith(".sql"):
            continue
        with open(os.path.join(output_dir, filename), encoding="utf-8") as f:
            for line in f:
                match = _CHUNK_ID_PATTERN.match(line.strip())
                if match:
                    max_id = max(max_id, int(match.group(1)))
    return max_id + 1


def run_batch(output_dir: str, source_dirs: list[str], log_path: str,
              pnu_ledger: dict[str, dict] | None = None) -> None:
    csv_files = sorted(
        path for d in source_dirs for path in glob.glob(os.path.join(d, "*.csv"))
    )
    start_id = next_start_id(output_dir)
    total_generated = 0
    total_skip: Counter = Counter()
    file_errors: list[tuple[str, str]] = []
    started_at = time.time()

    with open(log_path, "w", encoding="utf-8") as log:
        for i, csv_path in enumerate(csv_files, start=1):
            basename = os.path.basename(csv_path)
            try:
                next_id, skip_reasons = parse_file(csv_path, output_dir, start_id, pnu_ledger)
            except Exception as e:  # noqa: BLE001 - 배치 전체가 한 파일 때문에 죽으면 안 됨
                file_errors.append((basename, f"{type(e).__name__}: {e}"))
                log.write(f"[{i}/{len(csv_files)}] ERROR {basename}: {type(e).__name__}: {e}\n")
                log.flush()
                continue

            generated = next_id - start_id
            total_generated += generated
            total_skip.update(skip_reasons)
            start_id = next_id
            log.write(f"[{i}/{len(csv_files)}] {basename}: 생성 {generated}, 스킵 {dict(skip_reasons)}\n")
            log.flush()

        elapsed = time.time() - started_at
        log.write("\n=== 배치 요약 ===\n")
        log.write(f"처리 파일 수: {len(csv_files)}\n")
        log.write(f"총 생성 레코드: {total_generated}\n")
        log.write(f"다음 시작 id: {start_id}\n")
        log.write(f"소요 시간: {elapsed:.1f}초\n")
        log.write("스킵 사유별 합계:\n")
        for reason, count in total_skip.most_common():
            log.write(f"  {reason}: {count}\n")
        if file_errors:
            log.write(f"\n파일 자체 오류로 처리 실패한 파일 ({len(file_errors)}개):\n")
            for basename, err in file_errors:
                log.write(f"  {basename}: {err}\n")

    print(f"완료. 요약 로그: {log_path}")
    print(f"총 생성 레코드: {total_generated} / 다음 시작 id: {start_id} / 실패 파일: {len(file_errors)}")


if __name__ == "__main__":
    args = sys.argv[1:]
    ledger_path = None
    if args and args[0] == "--ledger":
        ledger_path = args[1]
        args = args[2:]
    if len(args) < 2:
        print("usage: batch_parse_licensed_records.py [--ledger <path>] <output_dir> <source_dir> [<source_dir> ...]")
        sys.exit(1)
    pnu_ledger = load_pnu_ledger(ledger_path) if ledger_path else None
    run_batch(args[0], args[1:], os.path.join(args[1], "batch-run-summary.txt"), pnu_ledger)
