from __future__ import annotations

import argparse
import json
import re
import sys
from dataclasses import dataclass
from pathlib import Path
from typing import Any, Iterable

from _common import UTF8_BOM, collect_files, display_path, repository_root


UNIT_ID_PATTERN = re.compile(r"^C([1-9]\d*)-S([1-9]\d*)$")
PAGE_PATTERN = re.compile(
    r"^chapter([1-9]\d*)/section([1-9]\d*)/README\.md$"
)
FENCE_PATTERN = re.compile(
    r"(?ms)^(`{3,})([^\r\n]*)\r?\n(.*?)^\1[ \t]*\r?$"
)
PACKAGE_PATTERN = re.compile(r"(?m)^package\s+([A-Za-z_$][\w$]*(?:\.[A-Za-z_$][\w$]*)*)\s*;")
TYPE_PATTERN = re.compile(
    r"(?m)^(?:(?:public|protected|private|abstract|final|sealed|non-sealed|strictfp)\s+)*"
    r"(?:class|interface|enum|record)\s+([A-Za-z_$][\w$]*)\b"
)
LIST_PATTERN = re.compile(r"(?ms)^def\s+(javaUnitIds|shaderUnitIds)\s*=\s*\[(.*?)\]")
STRING_PATTERN = re.compile(r'["\']([^"\']+)["\']')


@dataclass(frozen=True)
class CodeBlock:
    language: str
    content: str
    line: int


@dataclass(frozen=True)
class JavaBlock:
    unit_id: str
    page: Path
    line: int
    package_name: str
    type_name: str
    content: str

    @property
    def relative_source(self) -> Path:
        return Path(*self.package_name.split(".")) / f"{self.type_name}.java"


def canonical_unit_id(chapter: str | int, section: str | int) -> str:
    return f"C{int(chapter)}-S{int(section)}"


def normalized_content(text: str) -> str:
    return text.replace("\r\n", "\n").replace("\r", "\n").rstrip("\n")


def read_text(path: Path) -> str:
    data = path.read_bytes()
    if data.startswith(UTF8_BOM):
        raise UnicodeError("UTF-8 BOM")
    return data.decode("utf-8", errors="strict")


def parse_blocks(text: str) -> list[CodeBlock]:
    blocks: list[CodeBlock] = []
    for match in FENCE_PATTERN.finditer(text):
        info = match.group(2).strip()
        language = info.split(maxsplit=1)[0].casefold() if info else ""
        blocks.append(
            CodeBlock(
                language=language,
                content=normalized_content(match.group(3)),
                line=text.count("\n", 0, match.start()) + 1,
            )
        )
    return blocks


def parse_unit_lists(text: str) -> tuple[dict[str, list[str]], list[dict[str, Any]]]:
    tokens = re.compile(r"\"(?:\\.|[^\"\\])*\"|'(?:\\.|[^'\\])*'|//[^\n]*|/\*[\s\S]*?\*/")
    text = tokens.sub(
        lambda match: re.sub(r"[^\n]", " ", match.group(0))
        if match.group(0).startswith(("//", "/*")) else match.group(0),
        text,
    )
    lists: dict[str, list[str]] = {}
    issues: list[dict[str, Any]] = []
    for match in LIST_PATTERN.finditer(text):
        name = match.group(1)
        values = STRING_PATTERN.findall(match.group(2))
        if name in lists:
            issues.append(
                issue("duplicate-build-list", "tutorial-fixtures/build.gradle", 1, f"重复定义 {name}")
            )
        lists[name] = values
    for name in ("javaUnitIds", "shaderUnitIds"):
        if name not in lists:
            issues.append(
                issue("missing-build-list", "tutorial-fixtures/build.gradle", 1, f"缺少 {name}")
            )
            lists[name] = []
    return lists, issues


def issue(code: str, path: str | Path, line: int, message: str) -> dict[str, Any]:
    return {"code": code, "path": str(path).replace("\\", "/"), "line": line, "message": message}


def duplicate_values(values: Iterable[str]) -> set[str]:
    seen: set[str] = set()
    duplicates: set[str] = set()
    for value in values:
        if value in seen:
            duplicates.add(value)
        seen.add(value)
    return duplicates


def relative(path: Path, root: Path) -> str:
    try:
        return path.resolve().relative_to(root.resolve()).as_posix()
    except ValueError:
        return display_path(path)


def run(repo_root: Path | str | None = None, *, verbose: bool = False) -> dict[str, Any]:
    root = Path(repo_root).resolve() if repo_root is not None else repository_root()
    docs_root = root / "docs"
    units_root = root / "tutorial-fixtures" / "units"
    build_file = root / "tutorial-fixtures" / "build.gradle"
    summary: dict[str, Any] = {
        "pages": 0,
        "fixture_units": 0,
        "java_files": 0,
        "shader_files": 0,
        "complete_java_blocks": 0,
        "matched_java_blocks": 0,
        "complete_shader_blocks": 0,
        "matched_shader_blocks": 0,
        "problems": 0,
        "errors": [],
        "issues": [],
    }

    for required in (docs_root, units_root, build_file):
        if not required.exists():
            summary["errors"].append(f"路径不存在：{relative(required, root)}")
    if summary["errors"]:
        return summary

    issues: list[dict[str, Any]] = []
    page_paths: dict[str, Path] = {}
    java_blocks: list[JavaBlock] = []
    shader_blocks: dict[str, list[tuple[Path, CodeBlock]]] = {}

    try:
        for page in sorted(docs_root.glob("chapter*/section*/README.md")):
            if any(path.is_symlink() for path in (page, *page.parents) if path != root):
                continue
            rel = page.relative_to(docs_root).as_posix()
            match = PAGE_PATTERN.fullmatch(rel)
            if match is None:
                issues.append(issue("invalid-page-path", relative(page, root), 1, "section 页面路径不是正整数编号"))
                continue
            unit_id = canonical_unit_id(match.group(1), match.group(2))
            page_paths[unit_id] = page
            try:
                text = read_text(page)
            except (OSError, UnicodeError) as exc:
                summary["errors"].append(f"读取或解码失败：{relative(page, root)}：{exc}")
                continue
            blocks = parse_blocks(text)
            page_paths[unit_id] = page
            for block in blocks:
                if block.language == "java":
                    package_match = PACKAGE_PATTERN.search(block.content)
                    type_match = TYPE_PATTERN.search(block.content)
                    if package_match and type_match:
                        java_blocks.append(
                            JavaBlock(
                                unit_id=unit_id,
                                page=page,
                                line=block.line,
                                package_name=package_match.group(1),
                                type_name=type_match.group(1),
                                content=block.content,
                            )
                        )
                elif block.language in {"glsl", "vert", "frag"} and block.content.lstrip().startswith("#version"):
                    shader_blocks.setdefault(unit_id, []).append((page, block))

        build_text = read_text(build_file)
        unit_lists, list_issues = parse_unit_lists(build_text)
        issues.extend(list_issues)
    except (OSError, UnicodeError, UnicodeDecodeError) as exc:
        summary["errors"].append(f"读取或解码失败：{exc}")
        unit_lists = {"javaUnitIds": [], "shaderUnitIds": []}

    try:
        fixture_dirs = {path.name: path for path in units_root.iterdir() if path.is_dir() and not path.is_symlink()}
    except OSError as exc:
        summary["errors"].append(f"读取目录失败：{relative(units_root, root)}：{exc}")
        fixture_dirs = {}
    resources, _, scan_errors = collect_files([str(units_root)])
    summary["errors"].extend(scan_errors)
    java_files = [path for path in resources if path.suffix == ".java" and path.relative_to(units_root).parts[1:4] == ("src", "main", "java")]
    shader_files = [path for path in resources if path.suffix in {".vert", ".frag"} and path.relative_to(units_root).parts[1:4] == ("src", "main", "resources")]
    java_ids = unit_lists["javaUnitIds"]
    shader_ids = unit_lists["shaderUnitIds"]

    for source, values in (("javaUnitIds", java_ids), ("shaderUnitIds", shader_ids)):
        for duplicate in sorted(duplicate_values(values)):
            issues.append(issue("duplicate-unit-id", relative(build_file, root), 1, f"{source} 重复列出 {duplicate}"))
        for value in values:
            if UNIT_ID_PATTERN.fullmatch(value) is None:
                issues.append(issue("invalid-unit-id", relative(build_file, root), 1, f"{source} 包含非法编号 {value}"))
            if value not in fixture_dirs:
                issues.append(issue("listed-unit-missing-directory", relative(build_file, root), 1, f"{value} 没有 fixture 目录"))

    for unit_id, directory in sorted(fixture_dirs.items()):
        if UNIT_ID_PATTERN.fullmatch(unit_id) is None:
            issues.append(issue("invalid-fixture-id", relative(directory, root), 1, "fixture 目录不是正整数编号"))
        if unit_id not in page_paths:
            issues.append(issue("orphan-fixture", relative(directory, root), 1, f"{unit_id} 没有对应 section 页面"))

    page_java_ids = {block.unit_id for block in java_blocks}
    java_set = set(java_ids)
    fixture_set = set(fixture_dirs)
    for unit_id in sorted(page_java_ids - java_set):
        page = page_paths[unit_id]
        issues.append(issue("page-java-not-listed", relative(page, root), 1, f"{unit_id} 有完整 Java 示例但不在 javaUnitIds"))
    for unit_id in sorted(java_set - page_java_ids):
        issues.append(issue("listed-java-without-page-code", relative(build_file, root), 1, f"{unit_id} 在 javaUnitIds 中但页面没有完整 Java 示例"))
    for unit_id in sorted(fixture_set - java_set):
        issues.append(issue("fixture-not-listed", relative(fixture_dirs[unit_id], root), 1, f"{unit_id} fixture 不在 javaUnitIds"))

    expected_sources: dict[Path, JavaBlock] = {}
    matched_java = 0
    for block in java_blocks:
        source = units_root / block.unit_id / "src" / "main" / "java" / block.relative_source
        if source in expected_sources:
            issues.append(issue("duplicate-page-java", relative(block.page, root), block.line, f"完整类型 {block.package_name}.{block.type_name} 重复出现"))
            continue
        expected_sources[source] = block
        if not source.exists():
            issues.append(issue("missing-fixture-java", relative(block.page, root), block.line, f"缺少 {relative(source, root)}"))
            continue
        try:
            actual = normalized_content(read_text(source))
        except (OSError, UnicodeError, UnicodeDecodeError) as exc:
            summary["errors"].append(f"读取或解码失败：{relative(source, root)}：{exc}")
            continue
        if actual != block.content:
            issues.append(issue("java-content-mismatch", relative(source, root), 1, f"与 {relative(block.page, root)}:{block.line} 不一致"))
        else:
            matched_java += 1

    for source in java_files:
        if source not in expected_sources:
            unit_id = source.relative_to(units_root).parts[0]
            page = page_paths.get(unit_id)
            target = relative(page, root) if page else unit_id
            issues.append(issue("fixture-java-without-page-code", relative(source, root), 1, f"源码未出现在对应页面 {target}"))

    shader_resource_ids = {path.relative_to(units_root).parts[0] for path in shader_files}
    shader_page_ids = set(shader_blocks)
    shader_set = set(shader_ids)
    for unit_id in sorted(shader_page_ids - shader_set):
        page = page_paths[unit_id]
        issues.append(issue("page-shader-not-listed", relative(page, root), 1, f"{unit_id} 有完整 shader 但不在 shaderUnitIds"))
    for unit_id in sorted(shader_set - shader_page_ids):
        issues.append(issue("listed-shader-without-page-code", relative(build_file, root), 1, f"{unit_id} 在 shaderUnitIds 中但页面没有完整 shader"))
    for unit_id in sorted(shader_resource_ids - shader_set):
        issues.append(issue("shader-fixture-not-listed", relative(fixture_dirs[unit_id], root), 1, f"{unit_id} shader 资源不在 shaderUnitIds"))
    for unit_id in sorted(shader_set - shader_resource_ids):
        issues.append(issue("listed-shader-without-resource", relative(build_file, root), 1, f"{unit_id} 没有 .vert 或 .frag 资源"))

    matched_shader = 0
    for unit_id in sorted(shader_page_ids | shader_resource_ids):
        page_entries = shader_blocks.get(unit_id, [])
        resources = [path for path in shader_files if path.relative_to(units_root).parts[0] == unit_id]
        resource_contents: dict[str, list[Path]] = {}
        for resource in resources:
            try:
                content = normalized_content(read_text(resource))
            except (OSError, UnicodeError, UnicodeDecodeError) as exc:
                summary["errors"].append(f"读取或解码失败：{relative(resource, root)}：{exc}")
                continue
            resource_contents.setdefault(content, []).append(resource)
        used_resources: set[Path] = set()
        for page, block in page_entries:
            candidates = [path for path in resource_contents.get(block.content, []) if path not in used_resources]
            if not candidates:
                issues.append(issue("missing-or-mismatched-shader", relative(page, root), block.line, f"{unit_id} 的完整 shader 没有逐字一致的资源"))
                continue
            used_resources.add(candidates[0])
            matched_shader += 1
        for resource in resources:
            if resource not in used_resources:
                issues.append(issue("fixture-shader-without-page-code", relative(resource, root), 1, "shader 资源未在对应页面逐字出现"))

    summary.update(
        {
            "pages": len(page_paths),
            "fixture_units": len(fixture_dirs),
            "java_files": len(java_files),
            "shader_files": len(shader_files),
            "complete_java_blocks": len(java_blocks),
            "matched_java_blocks": matched_java,
            "complete_shader_blocks": sum(len(items) for items in shader_blocks.values()),
            "matched_shader_blocks": matched_shader,
            "problems": len(issues),
            "issues": issues,
        }
    )
    return summary


def emit(summary: dict[str, Any], *, json_output: bool, verbose: bool) -> None:
    status = "error" if summary["errors"] else "findings" if summary["problems"] else "pass"
    payload = {"status": status, **summary}
    if json_output:
        print(json.dumps(payload, ensure_ascii=False, indent=2, sort_keys=True))
        return
    print(
        "状态={status} 页面={pages} fixture单元={fixture_units} Java文件={java_files} "
        "shader文件={shader_files} Java匹配={matched_java_blocks}/{complete_java_blocks} "
        "shader匹配={matched_shader_blocks}/{complete_shader_blocks} 问题={problems} 错误={error_count}".format(
            status=status, error_count=len(summary["errors"]), **summary
        )
    )
    if verbose or summary["problems"]:
        for item in summary["issues"]:
            print(f"{item['path']}:{item['line']}: [{item['code']}] {item['message']}")
    for error in summary["errors"]:
        print(f"错误：{error}")


def build_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(description="检查教程页面与 fixture 的双向契约。")
    parser.add_argument("--repo-root", type=Path, help="仓库根目录；默认使用工具所在仓库。")
    parser.add_argument("--json", action="store_true", help="输出 JSON。")
    parser.add_argument("--verbose", action="store_true", help="列出全部问题。")
    return parser


def main() -> int:
    args = build_parser().parse_args()
    summary = run(args.repo_root, verbose=args.verbose)
    emit(summary, json_output=args.json, verbose=args.verbose)
    if summary["errors"]:
        return 2
    return 1 if summary["problems"] else 0


if __name__ == "__main__":
    sys.exit(main())
