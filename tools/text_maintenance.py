from __future__ import annotations

import argparse
from pathlib import Path
from typing import Any, Sequence

import check_standards
import check_markdown_format
import check_markdown_structure
import check_text_format
from _common import collect_files, emit, exit_code_for, make_summary, repository_root


def merge_findings(*payloads: dict[str, Any]) -> dict[str, Any]:
    summary = make_summary(scanned=0, skipped=0, bytes_scanned=0)
    file_issues: dict[str, list[str]] = {}
    for payload in payloads:
        summary["scanned"] = max(summary["scanned"], payload["scanned"])
        summary["skipped"] = max(summary["skipped"], payload["skipped"])
        summary["bytes"] = max(summary["bytes"], payload["bytes"])
        summary["errors"].extend(payload["errors"])
        for item in payload["files"]:
            issues = file_issues.setdefault(item["path"], [])
            issues.extend(issue for issue in item["issues"] if issue not in issues)
    summary["files"] = [
        {"path": path, "issues": issues} for path, issues in sorted(file_issues.items())
    ]
    summary["problem_files"] = sum(bool(issues) for issues in file_issues.values())
    summary["problems"] = sum(len(issues) for issues in file_issues.values())
    return summary


def run(paths: Sequence[str], *, apply: bool, verbose: bool = False) -> dict[str, Any]:
    normalized = check_text_format.run(paths, apply=apply, verbose=verbose)
    formatted = check_markdown_format.run(paths, apply=apply, verbose=verbose)
    changed_paths = {
        item["path"]
        for payload in (normalized, formatted)
        for item in payload["files"]
        if item.get("changed")
    }
    written_paths = {
        item["path"]
        for payload in (normalized, formatted)
        for item in payload["files"]
        if item.get("written")
    }

    text_check = check_text_format.run(paths, apply=False, verbose=verbose) if apply else normalized
    format_check = check_markdown_format.run(paths, apply=False, verbose=verbose) if apply else formatted
    root = repository_root().resolve()
    docs_root = root / "docs"
    standards_root = root / ".zcode" / "standards"
    validation_path = root / ".zcode" / "validation.md"
    markdown_files, _, route_errors = collect_files(
        paths, predicate=check_markdown_format.markdown_file
    )
    tutorial_paths: list[str] = []
    standard_paths: list[str] = []
    for path in markdown_files:
        resolved = path.resolve()
        if resolved.is_relative_to(docs_root):
            tutorial_paths.append(str(path))
        elif resolved.is_relative_to(standards_root) or resolved == validation_path:
            standard_paths.append(str(path))
    empty = make_summary(scanned=0, skipped=0, bytes_scanned=0)
    markdown_check = (
        check_markdown_structure.run(tutorial_paths, verbose=verbose) if tutorial_paths else empty
    )
    input_roots = [Path(value).expanduser().resolve() for value in paths] or [root]
    full_standards = any(standards_root.is_relative_to(path) for path in input_roots)
    if full_standards:
        standard_paths = [str(standards_root)] + [
            path for path in standard_paths if Path(path).resolve() == validation_path
        ]
    standards_check = (
        check_standards.run(standard_paths, verbose=verbose) if standard_paths else empty
    )
    summary = merge_findings(text_check, format_check, markdown_check, standards_check)
    summary["changed"] = len(changed_paths)
    summary["written"] = len(written_paths)
    summary["errors"] = list(
        dict.fromkeys(normalized["errors"] + formatted["errors"] + route_errors + summary["errors"])
    )
    summary["components"] = {
        "text": {
            key: text_check[key]
            for key in ("scanned", "problem_files", "problems", "changed", "written")
        },
        "format": {
            key: format_check[key]
            for key in ("scanned", "problem_files", "problems", "changed", "written")
        },
        "markdown": {key: markdown_check[key] for key in ("scanned", "problem_files", "problems")},
        "standards": {key: standards_check[key] for key in ("scanned", "problem_files", "problems")},
    }
    return summary


def build_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(description="组合执行仓库文本与 Markdown 维护。")
    parser.add_argument("paths", nargs="*", metavar="PATH", help="文件或目录；默认仓库根目录。")
    parser.add_argument("--apply", action="store_true", help="执行文本规范化与 Markdown 格式修复后复检。")
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
