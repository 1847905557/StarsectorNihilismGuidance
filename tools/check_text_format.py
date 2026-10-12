from __future__ import annotations

import argparse
import re
from pathlib import Path
from typing import Any, Sequence

from _common import atomic_write, closing_fence, collect_text_files, display_path, emit, exit_code_for, make_summary, opening_fence, read_utf8


BARE_CR = re.compile(r"\r(?!\n)")
TRAILING_WHITESPACE = re.compile(r"[ \t]+(?=\r?$)", re.MULTILINE)
NEWLINE = re.compile(r"\r\n|\r|\n")


def inspect_text(text: str, has_bom: bool) -> list[str]:
    issues: list[str] = []
    if has_bom:
        issues.append("UTF-8 BOM")
    crlf = text.count("\r\n")
    bare_cr = len(BARE_CR.findall(text))
    if crlf:
        issues.append(f"CRLF={crlf}")
    if bare_cr:
        issues.append(f"CR-only={bare_cr}")
    trailing = len(TRAILING_WHITESPACE.findall(text))
    if trailing:
        issues.append(f"行尾空白={trailing}")
    if not text.endswith("\n"):
        issues.append("缺少末尾行")
    elif text.endswith("\n\n"):
        issues.append("末尾行多于一个")
    return issues


def normalized_bytes(text: str, *, trim_trailing: bool = True, protect_fences: bool = False) -> bytes:
    """规范化为 UTF-8 无 BOM、LF、无行尾空白和单个末尾行。"""
    body = NEWLINE.sub("\n", text)
    if trim_trailing:
        lines: list[str] = []
        fence: tuple[str, int] | None = None
        for line in body.split("\n"):
            if protect_fences and fence is not None:
                lines.append(line)
                if closing_fence(line, *fence):
                    fence = None
                continue
            lines.append(line if protect_fences and line.startswith("    ") else line.rstrip(" \t"))
            if protect_fences:
                fence = opening_fence(line)
        body = "\n".join(lines)
    body = body.rstrip("\n")
    return (body + "\n").encode("utf-8")


def run(paths: Sequence[str], *, apply: bool, verbose: bool = False) -> dict[str, Any]:
    files, skipped, errors = collect_text_files(paths)
    summary = make_summary(
        scanned=len(files), skipped=skipped, bytes_scanned=0
    )
    summary["errors"].extend(errors)

    for path in files:
        try:
            original, text, has_bom = read_utf8(path)
            summary["bytes"] += len(original)
            issues = inspect_text(text, has_bom)
            desired = normalized_bytes(
                text,
                trim_trailing=path.suffix.casefold() in {".md", ".txt"} or not path.suffix,
                protect_fences=path.suffix.casefold() == ".md",
            )
        except (OSError, UnicodeDecodeError) as exc:
            summary["errors"].append(f"{display_path(path)}：{exc}")
            continue
        if not issues and desired == original:
            if verbose:
                summary["files"].append({"path": display_path(path), "issues": []})
            continue

        summary["problem_files"] += 1
        summary["problems"] += max(1, len(issues))
        is_changed = desired != original
        if is_changed:
            summary["changed"] += 1
        item = {
            "path": display_path(path),
            "issues": issues or ["需要 LF 规范化"],
            "changed": is_changed,
            "written": False,
        }
        summary["files"].append(item)
        if apply and desired != original:
            try:
                atomic_write(path, desired)
                summary["written"] += 1
                item["written"] = True
            except OSError as exc:
                summary["errors"].append(f"{display_path(path)}：{exc}")
    if apply:
        checked = run(paths, apply=False, verbose=verbose)
        for key in ("problems", "problem_files"):
            summary[key] = checked[key]
        summary["errors"] = list(dict.fromkeys(summary["errors"] + checked["errors"]))
        summary["remaining_files"] = checked["files"]
    return summary


def build_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(description="检查并规范化仓库文本编码、换行和行尾格式。")
    parser.add_argument("paths", nargs="*", metavar="PATH", help="文件或目录；默认仓库根目录。")
    parser.add_argument("--apply", action="store_true", help="写入修改（UTF-8 无 BOM、LF、末尾单换行）；默认只报告。")
    parser.add_argument("--json", action="store_true", help="输出 JSON。")
    parser.add_argument("--verbose", action="store_true", help="列出逐文件结果。")
    return parser


def main(argv: Sequence[str] | None = None) -> int:
    args = build_parser().parse_args(argv)
    summary = run(args.paths, apply=args.apply, verbose=args.verbose)
    emit(summary, json_output=args.json, verbose=args.verbose)
    return exit_code_for(summary)


if __name__ == "__main__":
    raise SystemExit(main())
