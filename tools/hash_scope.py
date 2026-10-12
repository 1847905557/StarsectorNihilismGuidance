from __future__ import annotations

import argparse
import hashlib
import json
from pathlib import Path
from typing import Any, Sequence

from _common import collect_files, display_path, repository_root, sha256_bytes


def run(paths: Sequence[str], *, verbose: bool = False) -> dict[str, Any]:
    files, skipped, errors = collect_files(paths)
    records: list[dict[str, Any]] = []
    total_bytes = 0

    for path in files:
        try:
            data = path.read_bytes()
        except OSError as exc:
            errors.append(f"{display_path(path)}：{exc}")
            continue
        total_bytes += len(data)
        records.append(
            {"path": display_path(path), "bytes": len(data), "sha256": sha256_bytes(data)}
        )

    aggregate_input = "".join(
        f"{item['path']}\0{item['bytes']}\0{item['sha256']}\n" for item in records
    ).encode("utf-8")
    return {
        "status": "error" if errors else "pass",
        "root": repository_root().as_posix(),
        "files": records if verbose else [],
        "file_count": len(records),
        "skipped": skipped,
        "bytes": total_bytes,
        "sha256": hashlib.sha256(aggregate_input).hexdigest().upper(),
        "errors": errors,
    }


def build_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(description="生成指定范围的稳定 SHA-256 清单。")
    parser.add_argument("paths", nargs="*", metavar="PATH", help="文件或目录；默认仓库根目录。")
    parser.add_argument("--json", action="store_true", help="输出 JSON；配合 --verbose 包含逐文件清单。")
    parser.add_argument("--verbose", action="store_true", help="列出逐文件哈希。")
    return parser


def main(argv: Sequence[str] | None = None) -> int:
    args = build_parser().parse_args(argv)
    payload = run(args.paths, verbose=args.verbose)
    if args.json:
        print(json.dumps(payload, ensure_ascii=False, indent=2, sort_keys=True))
    else:
        print(
            f"状态={payload['status']} 文件={payload['file_count']} 跳过={payload['skipped']} "
            f"字节={payload['bytes']} SHA256={payload['sha256']}"
        )
        if args.verbose:
            for item in payload["files"]:
                print(f"{item['sha256']} {item['bytes']} {item['path']}")
        for error in payload["errors"]:
            print(f"错误：{error}")
    return 2 if payload["errors"] else 0


if __name__ == "__main__":
    raise SystemExit(main())
