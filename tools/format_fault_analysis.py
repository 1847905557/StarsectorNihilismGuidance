from __future__ import annotations

import argparse
import json
import re
from pathlib import Path
from typing import Any, Sequence

from _common import atomic_write, closing_fence, collect_files, display_path, opening_fence, read_utf8, repository_root
from check_text_format import normalized_bytes


NEWLINE = re.compile(r"\r\n|\r|\n")
HEADING = re.compile(r"^(#{1,3})\s+(.+?)\s*$")
LIST_BULLET = re.compile(r"^\s*[-*]\s+")
SOLUTION_ITEM = re.compile(r"^\s*[-*]\s+\*\*解决方案\*\*[：:]")


def repair_fault_spacing(text: str) -> tuple[str, int, int]:
    newline_match = NEWLINE.search(text)
    newline = newline_match.group(0) if newline_match else "\n"
    lines = NEWLINE.split(text)
    fault_start: int | None = None
    fault_end: int | None = None
    in_fence = False
    fence_marker = ""
    fence_length = 0
    visible = [True] * len(lines)

    for index, line in enumerate(lines):
        if in_fence:
            visible[index] = False
            if closing_fence(line, fence_marker, fence_length):
                in_fence = False
            continue
        opener = opening_fence(line)
        if opener:
            visible[index] = False
            fence_marker, fence_length = opener
            in_fence = True
            continue
        heading = HEADING.match(line)
        if not heading:
            continue
        level = len(heading.group(1))
        title = heading.group(2).strip()
        if level == 2 and title == "故障分析":
            fault_start = index
        elif level <= 2 and fault_start is not None:
            fault_end = index
            break

    if fault_start is None:
        return text, 0, 0
    end = fault_end if fault_end is not None else len(lines)
    bullets = [
        (index, bool(SOLUTION_ITEM.match(lines[index])))
        for index in range(fault_start + 1, end)
        if visible[index] and LIST_BULLET.match(lines[index])
    ]
    delete_indexes: set[int] = set()
    insert_after: list[int] = []
    for position, (index, is_solution) in enumerate(bullets):
        next_index = index + 1
        while next_index < end and not lines[next_index].strip():
            next_index += 1
        following = bullets[position + 1] if position + 1 < len(bullets) else None
        if (
            following is not None
            and not is_solution
            and following[1]
            and next_index == following[0]
        ):
            delete_indexes.update(range(index + 1, next_index))
            continue
        if next_index >= end:
            if (
                next_index == index + 1
                and next_index < len(lines)
                and HEADING.match(lines[next_index])
            ):
                insert_after.append(index)
            continue
        if LIST_BULLET.match(lines[next_index]) or HEADING.match(lines[next_index]):
            if next_index == index + 1:
                insert_after.append(index)

    output: list[str] = []
    for index, line in enumerate(lines):
        if index in delete_indexes:
            continue
        output.append(line)
        if index in insert_after:
            output.append("")
    return newline.join(output), len(insert_after), len(delete_indexes)


def run(paths: Sequence[str], *, apply: bool) -> dict[str, Any]:
    files, skipped, errors = collect_files(paths, predicate=lambda path: path.suffix.casefold() == ".md")
    result: dict[str, Any] = {
        "status": "pass",
        "scanned": len(files),
        "skipped": skipped,
        "changed": 0,
        "inserted_blank_lines": 0,
        "removed_blank_lines": 0,
        "written": 0,
        "bytes_before": 0,
        "bytes_after": 0,
        "errors": list(errors),
        "files": [],
    }
    for path in files:
        try:
            original, text, has_bom = read_utf8(path)
            repaired, inserted, removed = repair_fault_spacing(text)
            desired = normalized_bytes(repaired, protect_fences=True)
        except (OSError, UnicodeDecodeError) as exc:
            result["errors"].append(f"{display_path(path)}：{exc}")
            continue
        result["bytes_before"] += len(original)
        result["bytes_after"] += len(desired)
        if desired == original:
            continue
        result["changed"] += 1
        result["inserted_blank_lines"] += inserted
        result["removed_blank_lines"] += removed
        item = {
            "path": display_path(path),
            "inserted_blank_lines": inserted,
            "removed_blank_lines": removed,
            "written": False,
        }
        result["files"].append(item)
        if apply:
            try:
                atomic_write(path, desired)
                result["written"] += 1
                item["written"] = True
            except OSError as exc:
                result["errors"].append(f"{display_path(path)}：{exc}")
    result["problems"] = result["changed"]
    if apply:
        checked = run(paths, apply=False)
        result["problems"] = checked["problems"]
        result["remaining_files"] = checked["files"]
        result["errors"] = list(dict.fromkeys(result["errors"] + checked["errors"]))
    result["status"] = "error" if result["errors"] else "findings" if result["problems"] else "pass"
    return result


def main(argv: Sequence[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description="修复 docs 故障分析列表的空行边界。")
    parser.add_argument("paths", nargs="*", help="docs 内的文件或目录；默认 docs。")
    parser.add_argument("--apply", action="store_true", help="原子写入修复结果；默认 dry-run。")
    parser.add_argument("--json", action="store_true", help="输出 JSON。")
    args = parser.parse_args(argv)
    result = run(args.paths or [str(repository_root() / "docs")], apply=args.apply)
    if args.json:
        print(json.dumps(result, ensure_ascii=False, indent=2, sort_keys=True))
    else:
        display = {**result, "errors": len(result["errors"])}
        print(
            "状态={status} 扫描={scanned} 修改文件={changed} 插入空行={inserted_blank_lines} "
            "删除空行={removed_blank_lines} 已写入={written} 错误={errors}".format(**display)
        )
    return 2 if result["errors"] else 1 if result["problems"] else 0


if __name__ == "__main__":
    raise SystemExit(main())
