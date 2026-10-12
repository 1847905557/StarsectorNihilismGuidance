from __future__ import annotations

import argparse
import re
from pathlib import Path
from typing import Any, Sequence
from urllib.parse import unquote, urlsplit

from _common import (
    collect_files,
    display_path,
    emit,
    exit_code_for,
    make_summary,
    read_utf8,
    repository_root,
    resolve_input_paths,
)
from check_markdown_format import closing_fence, markdown_file, opening_fence


NEWLINE = re.compile(r"\r\n|\r|\n")
HEADING = re.compile(r"^(#{1,6})\s+(.+?)\s*$")
INLINE_CODE = re.compile(r"(`+)(?:(?!\1).)*\1")
INLINE_LINK = re.compile(
    r"!?\[[^\]]*\]\((<[^>]+>|[^)\s]+)(?:\s+(?:\"[^\"]*\"|'[^']*'|\([^)]*\)))?\)"
)
OWNER = re.compile(r"^\*\*Owner：\*\*\S.+$")
SCOPE = re.compile(r"^\*\*适用范围：\*\*\S.+$")
STANDARD_FILES = ("writing.md", "formatting.md", "technical.md")
MAX_CHARACTERS = 18000


def relative_target(source: Path, raw_target: str) -> Path | None:
    target = raw_target[1:-1] if raw_target.startswith("<") and raw_target.endswith(">") else raw_target
    target = unquote(target)
    parsed = urlsplit(target)
    if parsed.scheme or parsed.netloc or target.startswith("#") or parsed.path.startswith("/"):
        return None
    if not parsed.path:
        return None
    return (source.parent / Path(parsed.path)).resolve()


def visible_lines(text: str) -> tuple[list[str], list[bool], bool]:
    lines = NEWLINE.split(text)
    visible = [True] * len(lines)
    in_fence = False
    marker = ""
    length = 0
    for index, line in enumerate(lines):
        if in_fence:
            visible[index] = False
            if closing_fence(line, marker, length):
                in_fence = False
            continue
        opener = opening_fence(line)
        if opener:
            visible[index] = False
            marker, length = opener
            in_fence = True
    return lines, visible, in_fence


def inspect_general_standard(path: Path, text: str, *, standards_root: Path, validation_path: Path) -> list[str]:
    issues: list[str] = []
    lines, visible, unclosed = visible_lines(text)
    nonempty = [(index, line) for index, line in enumerate(lines) if visible[index] and line.strip()]
    headings: list[tuple[int, int, str]] = []
    for index, line in enumerate(lines):
        if not visible[index]:
            continue
        match = HEADING.match(line)
        if match:
            headings.append((index, len(match.group(1)), match.group(2).strip()))
        searchable = INLINE_CODE.sub(lambda match: " " * len(match.group(0)), line)
        for link in INLINE_LINK.finditer(searchable):
            target = relative_target(path, link.group(1))
            if target is not None and not target.exists():
                issues.append(f"第 {index + 1} 行相对目标不存在：{link.group(1)}")

    if unclosed:
        issues.append("代码围栏未闭合")
    h1 = [item for item in headings if item[1] == 1]
    if len(h1) != 1:
        issues.append(f"规范文件必须包含唯一 H1；当前 {len(h1)} 个")
    if nonempty and (not h1 or h1[0][0] != nonempty[0][0]):
        issues.append("规范文件首个非空行必须是 H1")
    if any(level > 3 for _, level, _ in headings):
        issues.append("规范文件只使用 H1、H2 和 H3")

    is_owner_file = path.parent.resolve() == standards_root.resolve()
    if is_owner_file:
        owner_count = sum(1 for index, line in enumerate(lines) if visible[index] and OWNER.match(line))
        scope_count = sum(1 for index, line in enumerate(lines) if visible[index] and SCOPE.match(line))
        if owner_count != 1:
            issues.append(f"Owner 声明必须恰好出现一次；当前 {owner_count} 次")
        if scope_count != 1:
            issues.append(f"适用范围声明必须恰好出现一次；当前 {scope_count} 次")
    elif path.resolve() != validation_path.resolve():
        issues.append("规范检查器只接受 standards Owner 文件和 validation.md")
    return issues


def inspect_validation_routes(text: str) -> list[str]:
    issues: list[str] = []
    for name in STANDARD_FILES:
        target = f"./standards/{name}"
        if text.count(target) < 1:
            issues.append(f"validation.md 缺少 Owner 路由：{target}")
    return issues


def allowed_standard_paths(paths: Sequence[str], *, root: Path) -> tuple[list[str], list[str]]:
    standards_root = (root / ".zcode" / "standards").resolve()
    validation_path = (root / ".zcode" / "validation.md").resolve()
    expected = {standards_root / name for name in STANDARD_FILES}
    requested = list(paths) or [str(standards_root), str(validation_path)]
    allowed: list[str] = []
    errors: list[str] = []
    for path in resolve_input_paths(requested):
        resolved = path.resolve()
        if resolved == standards_root or resolved in expected or resolved == validation_path:
            allowed.append(str(path))
        else:
            errors.append(f"check_standards.py 只检查 .zcode/standards 和 .zcode/validation.md：{display_path(path)}")
    return allowed, errors


def run(paths: Sequence[str], *, verbose: bool = False, repo_root: Path | None = None) -> dict[str, Any]:
    root = (repo_root or repository_root()).resolve()
    standards_root = (root / ".zcode" / "standards").resolve()
    validation_path = (root / ".zcode" / "validation.md").resolve()
    allowed, scope_errors = allowed_standard_paths(paths, root=root)
    files, skipped, errors = (
        collect_files(allowed, predicate=markdown_file) if allowed else ([], 0, [])
    )
    summary = make_summary(scanned=len(files), skipped=skipped, bytes_scanned=0)
    summary["errors"].extend(scope_errors)
    summary["errors"].extend(errors)
    expected = {standards_root / name for name in STANDARD_FILES}
    resolved_files = {path.resolve() for path in files}
    if any(Path(value).resolve() == standards_root for value in allowed):
        for path in sorted(expected - resolved_files):
            summary["problem_files"] += 1
            summary["problems"] += 1
            summary["files"].append({"path": display_path(path), "issues": ["标准文件缺失"]})
    texts: dict[Path, str] = {}
    for path in files:
        try:
            original, text, _ = read_utf8(path)
            summary["bytes"] += len(original)
            issues = inspect_general_standard(path, text, standards_root=standards_root, validation_path=validation_path)
            texts[path.resolve()] = text
            if path.parent.resolve() == standards_root and path.resolve() not in expected:
                issues.append("正式标准文件不在三份标准清单中")
            if path.resolve() == validation_path:
                issues.extend(inspect_validation_routes(text))
        except (OSError, UnicodeDecodeError) as exc:
            summary["errors"].append(f"{display_path(path)}：{exc}")
            continue
        if issues:
            summary["problem_files"] += 1
            summary["problems"] += len(issues)
            summary["files"].append({"path": display_path(path), "issues": issues})
        elif verbose:
            summary["files"].append({"path": display_path(path), "issues": []})
    complete_set = expected | {validation_path}
    if complete_set.issubset(texts):
        count = sum(len(NEWLINE.sub("\n", texts[path])) for path in complete_set)
        summary["characters"] = count
        summary["character_limit"] = MAX_CHARACTERS
        if count > MAX_CHARACTERS:
            path_label = display_path(validation_path)
            item = next((item for item in summary["files"] if item["path"] == path_label), None)
            if item is None:
                item = {"path": path_label, "issues": []}
                summary["files"].append(item)
            if not item["issues"]:
                summary["problem_files"] += 1
            summary["problems"] += 1
            item["issues"].append(f"正式规范共 {count} 个 Unicode 字符，超过 {MAX_CHARACTERS} 上限")
    return summary


def build_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(description="检查三份标准、核验路由、链接与规范总字符数。")
    parser.add_argument("paths", nargs="*", metavar="PATH", help="规范文件或目录；默认 standards 和 validation.md。")
    parser.add_argument("--json", action="store_true", help="输出 JSON。")
    parser.add_argument("--verbose", action="store_true", help="列出逐文件结果。")
    return parser


def main(argv: Sequence[str] | None = None) -> int:
    args = build_parser().parse_args(argv)
    summary = run(args.paths, verbose=args.verbose)
    emit(summary, json_output=args.json, verbose=args.verbose)
    return exit_code_for(summary)


if __name__ == "__main__":
    raise SystemExit(main())
