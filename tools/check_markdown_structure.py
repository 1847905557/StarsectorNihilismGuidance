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
INLINE_CODE = re.compile(r"(`+)(?:(?!\1).)*\1")
INLINE_LINK = re.compile(
    r"!?\[[^\]]*\]\((<[^>]+>|[^)\s]+)(?:\s+(?:\"[^\"]*\"|'[^']*'|\([^)]*\)))?\)"
)
MARKDOWN_IMAGE = re.compile(
    r"!\[([^\]]*)\]\((<[^>]+>|[^)\s]+)"
    r"(?:\s+(?:\"([^\"]*)\"|'([^']*)'|\(([^)]*)\)))?\)"
)
IMAGE_FILENAME = re.compile(
    r"^[A-Za-z0-9][A-Za-z0-9._-]*\.(?:gif|jpe?g|png|svg|webp)$",
    re.IGNORECASE,
)
SECTION_REFERENCE = re.compile(r"C(\d+)-S(\d+)")
HEADING = re.compile(r"^(#{1,6})\s+(.+?)\s*$")
CHAPTER_BASELINE = re.compile(
    r"^本章技术基线：Starsector `[^`\s]+`"
    r"(?: 与 Java [1-9]\d*|，Java [1-9]\d*)?"
    r"(?:；(?:前置知识|依赖基线|可选依赖基线|前置依赖)：\S.+)?。$"
)
SECTION_LIST_PLACEHOLDER = "<!-- AUTO:SECTION-LIST -->"
ORDERED_HEADING = re.compile(
    r"^(?:第[一二三四五六七八九十百]+(?:步|部分|阶段|项|段)|"
    r"链路[一二三四五六七八九十百]+(?:：|:)|"
    r"(?:片段|步骤)\s*(?:\d+|[一二三四五六七八九十百]+)|"
    r"\d+[.、)]|[（(]\d+[）)]|[①②③④⑤⑥⑦⑧⑨⑩]|"
    r"[IVXLCDM]+[.、)])",
    re.IGNORECASE,
)
FIXED_HEADING_LEVELS = {
    "学习目标": {2},
    "前置知识": {2},
    "文件与 API 链路": {2},
    "故障分析": {2},
    "总结": {2},
    "建议参考项": {2},
    "验证方案": {2, 3},
}
FIXED_HEADING_ALIASES = {
    "梳理文件与 API 链路": "文件与 API 链路",
    "梳理调用关系": "文件与 API 链路",
    "检查完整成品": "完整最小例",
    "组合完整成品": "完整最小例",
    "组装完整成品": "完整最小例",
    "组装完整实现": "完整最小例",
}
COMPLETE_EXAMPLE = re.compile(r"^完整最小例(?:：.+)?$")
COMPLETE_DETAILS = re.compile(r"^:::\s+details\s+完整成品：\S.+$")
CONTAINER_FENCE = re.compile(r"^(:::+)(.*)$")
FAULT_INTRO = re.compile(
    r"(?:以下故障按|以下按您实际看到|下面按读者|下面每条都给出|"
    r"故障按症状组织|每项给出触发条件|每条给出触发条件)"
)
FAULT_POSITION_REFERENCE = re.compile(r"(?:故障分析|故障)\s*(?:第\s*)?[0-9一二三四五六七八九十百]+\s*条?")
FAULT_ORDERED_ITEM = re.compile(r"^\s*\d+[.、)]\s+\*\*")
FAULT_SUBHEADING = re.compile(r"^###\s+")
FAULT_PLAIN_ITEM = re.compile(r"^\s*[-*]\s+(?!\*\*)\S")
FAULT_LABEL_COLON = re.compile(r"^\s*[-*]\s+\*\*.*[：:]\*\*")
FAULT_BULLET = re.compile(r"^(\s*)[-*]\s+")
FAULT_SOLUTION_ITEM = re.compile(r"^(\s*)[-*]\s+\*\*解决方案\*\*[：:]")
REFERENCE_ITEM = re.compile(r"^- `[^`]+`：\S.*$")
VALIDATION_PREREQUISITE = re.compile(r"^前置数据：\S.*$")
VALIDATION_ORDERED_ITEM = re.compile(r"^\d+[.]\s+\S.*$")
VALIDATION_RESULT_LABEL = re.compile(r"^预期结果：$")
VALIDATION_RESULT_ITEM = re.compile(r"^[-*]\s+\S.*$")
FORBIDDEN_PROSE_CHAR = re.compile(r"[“”‘’（）]")
TABLE_ROW = re.compile(r"^\s*\|.*\|\s*$")
TABLE_SEPARATOR_CELL = re.compile(r"^:?-{3,}:?$")
TABLE_INTRO = re.compile(
    r"(?:下表|一张表|这张表|对照表|汇总表|交叉核对表|"
    r"收拢成表|整理成表|串成.{0,12}表)"
)
TABLE_BLOCK_PREFIX = re.compile(
    r"^(?:#{1,6}\s|[-*+]\s|\d+[.]\s|>|```|:::|<!--)"
)
STANDALONE_TABLE_PLACEHOLDER = re.compile(r"^\s*—\s*$")


def mask_inline_code(line: str) -> str:
    return INLINE_CODE.sub(lambda match: " " * len(match.group(0)), line)


def tutorial_page(path: Path, docs_root: Path) -> bool:
    try:
        path.resolve().relative_to(docs_root.resolve())
    except ValueError:
        return False
    return True


def split_table_row(line: str) -> list[str]:
    body = mask_inline_code(line).strip()[1:-1]
    cells: list[str] = []
    current: list[str] = []
    escaped = False
    for char in body:
        if char == "|" and not escaped:
            cells.append("".join(current).strip())
            current = []
            continue
        current.append(char)
        escaped = char == "\\" and not escaped
        if char != "\\":
            escaped = False
    cells.append("".join(current).strip())
    return cells


def flattened_cells_after(
    lines: list[str], start_index: int, end_index: int
) -> bool:
    cells = 0
    index = start_index
    while index < end_index and lines[index].strip() == "":
        index += 1
    while index < end_index:
        value = lines[index].strip()
        if not value or TABLE_BLOCK_PREFIX.match(value) or TABLE_ROW.match(value):
            break
        cells += 1
        index += 1
        if index >= end_index or lines[index].strip() != "":
            break
        while index < end_index and lines[index].strip() == "":
            index += 1
    return cells >= 6


def inspect_table_contract(path: Path, text: str, *, docs_root: Path) -> list[str]:
    if not tutorial_page(path, docs_root):
        return []

    issues: list[str] = []
    lines = NEWLINE.split(text)
    visible = [True] * len(lines)
    headings: list[tuple[int, int, str]] = []
    in_fence = False
    fence_marker = ""
    fence_length = 0

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
        heading = HEADING.match(mask_inline_code(line))
        if heading and len(heading.group(1)) in (2, 3):
            headings.append((index, len(heading.group(1)), heading.group(2).strip()))

    table_blocks: list[tuple[int, int]] = []
    table_lines: set[int] = set()
    index = 0
    while index < len(lines):
        if visible[index] and TABLE_ROW.match(lines[index]):
            start = index
            while index < len(lines) and visible[index] and TABLE_ROW.match(lines[index]):
                table_lines.add(index)
                index += 1
            table_blocks.append((start, index))
            continue
        index += 1

    heading_owners: dict[int, list[int]] = {}
    for start, end in table_blocks:
        rows = [split_table_row(lines[row]) for row in range(start, end)]
        column_count = len(rows[0]) if rows else 0
        if len(rows) < 4:
            issues.append(
                f"第 {start + 1} 行速查表至少需要表头、分隔行和两个数据行"
            )
        if len(rows) < 2 or not all(
            TABLE_SEPARATOR_CELL.fullmatch(cell) for cell in rows[1]
        ):
            issues.append(f"第 {start + 1} 行速查表缺少合法分隔行")
        if column_count < 2:
            issues.append(f"第 {start + 1} 行速查表至少需要两列")
        if any(len(row) != column_count for row in rows[1:]):
            issues.append(f"第 {start + 1} 行速查表各行列数必须一致")

        owner = max((item for item in headings if item[0] < start), default=None)
        if owner is None or "速查表" not in owner[2]:
            issues.append(
                f"第 {start + 1} 行表格必须由包含“速查表”的 H2 或 H3 标题管理"
            )
            continue

        heading_owners.setdefault(owner[0], []).append(start)
        between = [
            lines[row].strip()
            for row in range(owner[0] + 1, start)
            if lines[row].strip()
        ]
        if len(between) > 1 or (
            between and TABLE_BLOCK_PREFIX.match(between[0]) is not None
        ):
            issues.append(
                f"第 {start + 1} 行速查表标题后只允许零段或一段普通引言"
            )

    for heading_line, starts in heading_owners.items():
        if len(starts) > 1:
            issues.append(
                f"第 {heading_line + 1} 行速查表标题只能管理一张表"
            )

    heading_indexes = [item[0] for item in headings]
    table_starts = [item[0] for item in table_blocks]
    for index, line in enumerate(lines):
        if not visible[index] or index in table_lines:
            continue
        searchable = mask_inline_code(line)
        if STANDALONE_TABLE_PLACEHOLDER.fullmatch(searchable):
            issues.append(f"第 {index + 1} 行表格外严禁使用独立占位符：—")
        if not TABLE_INTRO.search(searchable):
            continue
        next_heading = min(
            (item for item in heading_indexes if item > index),
            default=len(lines),
        )
        if any(index < start < next_heading for start in table_starts):
            continue
        detail = "，且后续内容呈现连续独立单元格" if flattened_cells_after(
            lines, index + 1, next_heading
        ) else ""
        issues.append(
            f"第 {index + 1} 行表格导语后缺少 Markdown 表格{detail}"
        )
    return issues


def relative_target(source: Path, raw_target: str) -> Path | None:
    target = raw_target[1:-1] if raw_target.startswith("<") and raw_target.endswith(">") else raw_target
    target = unquote(target)
    parsed = urlsplit(target)
    if parsed.scheme or parsed.netloc or target.startswith("#") or parsed.path.startswith("/"):
        return None
    if not parsed.path:
        return None
    return (source.parent / Path(parsed.path)).resolve()


def inspect_image_contract(line: str, line_number: int) -> list[str]:
    issues: list[str] = []
    for match in MARKDOWN_IMAGE.finditer(line):
        alt = match.group(1).strip()
        raw_target = match.group(2)
        title = next((value for value in match.groups()[2:] if value is not None), None)
        target = raw_target[1:-1] if raw_target.startswith("<") and raw_target.endswith(">") else raw_target
        parsed = urlsplit(target)
        basename = unquote(Path(parsed.path).name)

        if not alt:
            issues.append(f"第 {line_number} 行图片 alt 必须为非空语义描述：{raw_target}")
        if title is None or not title:
            issues.append(f"第 {line_number} 行图片 title 必须写原始文件名：{raw_target}")
            continue
        if not IMAGE_FILENAME.fullmatch(title) or ".." in title:
            issues.append(f"第 {line_number} 行图片 title 不是合法文件名：{title}")
            continue
        if basename != title:
            issues.append(
                f"第 {line_number} 行图片 title 与目标文件名不一致：{title} != {basename}"
            )
        if not parsed.scheme and not parsed.netloc and target != f"./{title}":
            issues.append(
                f"第 {line_number} 行本地图片目标必须写成 ./{title}：{raw_target}"
            )
    return issues


def inspect_section_references(
    line: str, line_number: int, *, docs_root: Path
) -> list[str]:
    issues: list[str] = []
    for match in SECTION_REFERENCE.finditer(line):
        chapter_text, section_text = match.groups()
        source = match.group(0)
        before = line[: match.start()]
        after = line[match.end() :]
        double_bracketed = before.endswith("[[") and after.startswith("]]" )
        bracketed = before.endswith("[") and after.startswith("]")
        explicit_link = bracketed and after.startswith("](")

        if double_bracketed:
            issues.append(f"第 {line_number} 行 Section 编号使用双层方括号：{source}")
            continue
        if explicit_link:
            issues.append(f"第 {line_number} 行 Section 编号严禁手写目标链接：{source}")
            continue
        if not bracketed:
            issues.append(f"第 {line_number} 行 Section 编号必须使用一层方括号：{source}")
            continue
        if chapter_text.startswith("0") or section_text.startswith("0"):
            issues.append(f"第 {line_number} 行 Section 编号严禁前导零：{source}")
            continue

        chapter = int(chapter_text)
        section = int(section_text)
        target = docs_root / f"chapter{chapter}" / f"section{section}" / "README.md"
        if not target.is_file():
            issues.append(f"第 {line_number} 行 Section 编号目标不存在：{source}")
    return issues


def inspect_validation_contract(path: Path, text: str, *, docs_root: Path) -> list[str]:
    if not tutorial_page(path, docs_root):
        return []
    if re.search(r"chapter\d+[/\\]section\d+[/\\]README\.md$", str(path)) is None:
        return []

    issues: list[str] = []
    lines = NEWLINE.split(text)
    visible = [True] * len(lines)
    headings: list[tuple[int, int, str]] = []
    in_fence = False
    fence_marker = ""
    fence_length = 0
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
        heading = HEADING.match(mask_inline_code(line))
        if heading:
            headings.append((index, len(heading.group(1)), heading.group(2).strip()))

    for heading_index, level, title in headings:
        if title != "验证方案" or level not in (2, 3):
            continue
        end = next(
            (index for index, later_level, _ in headings if index > heading_index and later_level <= level),
            len(lines),
        )
        content = [
            index for index in range(heading_index + 1, end)
            if visible[index] and lines[index].strip()
        ]
        prerequisites = [index for index in content if VALIDATION_PREREQUISITE.match(lines[index].strip())]
        ordered = [index for index in content if VALIDATION_ORDERED_ITEM.match(lines[index].strip())]
        labels = [index for index in content if VALIDATION_RESULT_LABEL.match(lines[index].strip())]
        result_label = labels[0] if labels else None
        result_items = [
            index for index in content
            if VALIDATION_RESULT_ITEM.match(lines[index].strip())
            and (result_label is None or index > result_label)
        ]
        images = [index for index in content if MARKDOWN_IMAGE.search(lines[index])]
        start_line = heading_index + 1
        if not ordered:
            issues.append(f"第 {start_line} 行验证方案缺少数字步骤列表")
        if result_label is None:
            issues.append(f"第 {start_line} 行验证方案缺少预期结果标签")
        if len(labels) > 1:
            issues.append(f"第 {start_line} 行验证方案只能有一个预期结果标签")
        if not result_items:
            issues.append(f"第 {start_line} 行验证方案缺少预期结果列表")
        if len(prerequisites) > 1:
            issues.append(f"第 {start_line} 行验证方案只能有一个前置数据段")
        prerequisite = prerequisites[0] if prerequisites else None
        first_ordered = ordered[0] if ordered else None
        first_result = result_items[0] if result_items else None
        present = [value for value in (prerequisite, first_ordered, result_label, first_result) if value is not None]
        if present != sorted(present) or (
            result_label is not None and any(index > result_label for index in ordered)
        ):
            issues.append(f"第 {start_line} 行验证方案结构顺序错误")
        if prerequisite is not None and content and content[0] != prerequisite:
            issues.append(f"第 {prerequisite + 1} 行前置数据必须是验证方案的第一个内容块")
        for indexes, label in ((ordered, "数字步骤"), (result_items, "预期结果")):
            for earlier, later in zip(indexes, indexes[1:]):
                if any(not lines[index].strip() for index in range(earlier + 1, later)):
                    issues.append(f"第 {earlier + 1} 行验证方案{label}必须是紧凑列表：列表项之间不允许空行")
        if result_label is not None and first_result is not None:
            if any(result_label < index < first_result for index in content):
                issues.append(f"第 {result_label + 1} 行预期结果标签后必须紧跟结果列表")
        if ordered and result_label is not None:
            for index in content:
                if ordered[0] <= index < result_label and index not in ordered:
                    if not lines[index].startswith("    "):
                        issues.append(f"第 {index + 1} 行验证动作只能包含步骤及其嵌套内容")
        if result_items:
            last_result = result_items[-1]
            for index in images:
                if index <= last_result:
                    issues.append(f"第 {index + 1} 行验证图片必须位于预期结果列表之后")
            trailing = [index for index in content if index > last_result]
            if trailing and not any(index > last_result for index in images):
                issues.append(f"第 {trailing[0] + 1} 行预期结果之后只能附验证图片与观察说明")

    return issues


def inspect_chapter_contract(path: Path, text: str, *, docs_root: Path) -> list[str]:
    if not tutorial_page(path, docs_root):
        return []
    relative = path.resolve().relative_to(docs_root.resolve()).as_posix()
    if re.fullmatch(r"chapter[1-9]\d*/README\.md", relative) is None:
        return []

    issues: list[str] = []
    content: list[tuple[int, str]] = []
    lines = NEWLINE.split(text)
    in_fence = False
    marker = ""
    length = 0
    for index, line in enumerate(lines):
        if in_fence:
            if closing_fence(line, marker, length):
                in_fence = False
            continue
        opener = opening_fence(line)
        if opener:
            content.append((index + 1, line))
            marker, length = opener
            in_fence = True
            continue
        if line.strip():
            content.append((index + 1, line))

    baselines = [(number, line) for number, line in content if line.startswith("本章技术基线")]
    if len(baselines) != 1:
        issues.append(f"章页必须包含一个本章技术基线段落；当前 {len(baselines)} 个")
    else:
        number, baseline = baselines[0]
        if CHAPTER_BASELINE.fullmatch(baseline) is None:
            issues.append(f"第 {number} 行本章技术基线格式错误")
        if content.index(baselines[0]) != 1:
            issues.append(f"第 {number} 行本章技术基线必须紧接 H1")
        if (number > 1 and lines[number - 2].strip()) or (
            number < len(lines) and lines[number].strip()
        ):
            issues.append(f"第 {number} 行本章技术基线必须独占普通段落")

    headings = [
        (number, match)
        for number, line in content
        if (match := HEADING.match(mask_inline_code(line))) is not None
    ]
    catalogs = [(number, match) for number, match in headings if match.group(2) == "本章各节"]
    if len(catalogs) != 1:
        issues.append(f"章页必须包含一个本章各节标题；当前 {len(catalogs)} 个")
    elif len(catalogs[0][1].group(1)) != 2:
        issues.append(f"第 {catalogs[0][0]} 行本章各节必须使用 H2")
    if any(len(match.group(1)) > 1 and match.group(2) != "本章各节" for _, match in headings):
        issues.append("章页只允许以本章各节作为正文分区标题")

    placeholders = [(number, line) for number, line in content if line == SECTION_LIST_PLACEHOLDER]
    if len(placeholders) != 1:
        issues.append(f"章页必须包含一个自动列表占位符；当前 {len(placeholders)} 个")
    if len(catalogs) == 1 and len(placeholders) == 1:
        if content[-2:] != [(catalogs[0][0], "## 本章各节"), placeholders[0]]:
            issues.append("章页必须以本章各节和独立自动列表占位符结束")
        if len(baselines) == 1 and not any(
            baselines[0][0] < number < catalogs[0][0] for number, _ in content
        ):
            issues.append("章页必须在本章技术基线与本章各节之间提供章介绍")
    return issues


def inspect_markdown(path: Path, text: str, *, docs_root: Path | None = None) -> list[str]:
    issues: list[str] = []
    resolved_docs_root = docs_root or repository_root() / "docs"
    section_match = re.search(r"chapter(\d+)[/\\]section\d+[/\\]README\.md$", str(path))
    is_section_page = section_match is not None
    in_fence = False
    fence_marker = ""
    fence_length = 0
    complete_example_lines: list[int] = []
    complete_details_lines: list[int] = []
    container_stack: list[int] = []
    h1_lines: list[int] = []
    fault_start: int | None = None
    fault_end: int | None = None
    pending_fault_symptom: tuple[int, int] | None = None
    fault_bullet_lines: list[tuple[int, int, bool]] = []
    reference_start: int | None = None
    reference_end: int | None = None
    reference_content_lines: list[int] = []

    lines = NEWLINE.split(text)
    for line_index, line in enumerate(lines):
        line_number = line_index + 1
        character_searchable = line
        for match in FORBIDDEN_PROSE_CHAR.finditer(character_searchable):
            issues.append(
                f"第 {line_number} 行使用禁用字符：{match.group(0)}"
            )
        if in_fence:
            if closing_fence(line, fence_marker, fence_length):
                in_fence = False
            continue
        opener = opening_fence(line)
        if opener:
            fence_marker, fence_length = opener
            in_fence = True
            continue

        container = CONTAINER_FENCE.match(line)
        if container:
            colons = len(container.group(1))
            if not container.group(2).strip():
                if container_stack and colons >= container_stack[-1]:
                    container_stack.pop()
                else:
                    issues.append(f"第 {line_number} 行 VuePress 容器结束标记没有对应开始标记")
            else:
                container_stack.append(colons)
                if len(container.group(2).split()) < 2:
                    issues.append(
                        f"第 {line_number} 行 VuePress 容器缺少标题：{line.strip()}"
                    )
        searchable = mask_inline_code(line)
        heading = HEADING.match(searchable)
        if heading:
            level = len(heading.group(1))
            title = heading.group(2).strip()
            if level == 1:
                h1_lines.append(line_number)
            if level > 3:
                issues.append(f"第 {line_number} 行标题只使用 H1、H2 和 H3")
            if ORDERED_HEADING.match(title):
                issues.append(f"第 {line_number} 行标题严禁使用顺序编号：{title}")
            expected_levels = FIXED_HEADING_LEVELS.get(title) if is_section_page else None
            if expected_levels is not None and level not in expected_levels:
                expected = " 或 ".join(f"H{item}" for item in sorted(expected_levels))
                issues.append(
                    f"第 {line_number} 行固定标题层级错误：{title} 应使用 {expected}"
                )
            alias_target = FIXED_HEADING_ALIASES.get(title) if is_section_page else None
            if alias_target is not None:
                issues.append(
                    f"第 {line_number} 行固定标题使用了同义改名：{title}；应为 {alias_target}"
                )
            if is_section_page and COMPLETE_EXAMPLE.match(title):
                complete_example_lines.append(line_number)
                if level != 2:
                    issues.append(
                        f"第 {line_number} 行固定标题层级错误：{title} 应使用 H2"
                    )
            if is_section_page and level == 2 and title == "故障分析":
                fault_start = line_number
            elif is_section_page and level <= 2 and fault_start is not None and fault_end is None:
                fault_end = line_number
            if is_section_page and level == 2 and title == "建议参考项":
                reference_start = line_number
            elif is_section_page and reference_start is not None and reference_end is None:
                reference_end = line_number
        if COMPLETE_DETAILS.match(line):
            complete_details_lines.append(line_number)
        if is_section_page and fault_start is not None and fault_end is None:
            solution = FAULT_SOLUTION_ITEM.match(searchable)
            bullet = FAULT_BULLET.match(searchable)
            if searchable.strip() and bullet is None and not HEADING.match(searchable):
                issues.append(
                    f"第 {line_number} 行故障分析条目必须使用列表格式：{line.strip()}"
                )
            if pending_fault_symptom is not None:
                if not searchable.strip():
                    pass
                elif solution is None:
                    issues.append(
                        f"第 {pending_fault_symptom[0]} 行故障分析问题后必须紧跟以解决方案为开头的行"
                    )
                    pending_fault_symptom = None
                else:
                    solution_indent = len(solution.group(1))
                    if solution_indent <= pending_fault_symptom[1]:
                        issues.append(
                            f"第 {line_number} 行故障分析解决方案必须嵌套在对应问题下"
                        )
                    pending_fault_symptom = None
            elif solution is not None:
                issues.append(
                    f"第 {line_number} 行故障分析解决方案前必须有对应的问题行"
                )

            if FAULT_INTRO.search(searchable):
                issues.append(f"第 {line_number} 行故障分析禁止模板化导语或抽象排错词：{line.strip()}")
            if FAULT_POSITION_REFERENCE.search(searchable):
                issues.append(f"第 {line_number} 行故障分析禁止位置编号引用：{line.strip()}")
            if FAULT_ORDERED_ITEM.match(searchable):
                issues.append(f"第 {line_number} 行故障分析条目禁止顺序编号：{line.strip()}")
            if FAULT_SUBHEADING.match(searchable):
                issues.append(f"第 {line_number} 行故障分析症状严禁使用 H3：{line.strip()}")
            if FAULT_PLAIN_ITEM.match(searchable):
                issues.append(f"第 {line_number} 行故障分析条目必须以加粗症状起句：{line.strip()}")
            if FAULT_LABEL_COLON.match(searchable):
                issues.append(f"第 {line_number} 行故障分析症状后的冒号必须位于加粗范围外：{line.strip()}")
            if bullet is not None:
                bullet_indent = len(bullet.group(1))
                is_solution = solution is not None
                fault_bullet_lines.append((line_number, bullet_indent, is_solution))
                if not is_solution:
                    pending_fault_symptom = (line_number, bullet_indent)
        if (
            is_section_page
            and reference_start is not None
            and reference_end is None
            and HEADING.match(searchable) is None
            and line.strip()
        ):
            reference_content_lines.append(line_number)
            if REFERENCE_ITEM.match(line) is None:
                issues.append(
                    f"第 {line_number} 行建议参考项条目必须使用 “- `对象`：说明” 的形式：{line.strip()}"
                )
            for reference in SECTION_REFERENCE.finditer(searchable):
                issues.append(
                    f"第 {line_number} 行建议参考项严禁章节引用：[{reference.group(0)}]"
                )
        issues.extend(
            inspect_section_references(
                searchable, line_number, docs_root=resolved_docs_root
            )
        )
        issues.extend(inspect_image_contract(searchable, line_number))
        for match in INLINE_LINK.finditer(searchable):
            raw_target = match.group(1)
            target = relative_target(path, raw_target)
            if target is not None and not target.exists():
                issues.append(f"第 {line_number} 行相对目标不存在：{raw_target}")

    if in_fence:
        issues.append("代码围栏未闭合")
    if tutorial_page(path, resolved_docs_root):
        if len(h1_lines) != 1:
            issues.append(f"教程页面必须包含唯一 H1；当前 {len(h1_lines)} 个")
        first_content = next((index + 1 for index, line in enumerate(lines) if line.strip()), None)
        if h1_lines and h1_lines[0] != first_content:
            issues.append("教程页面首个非空行必须是 H1")
    if container_stack:
        issues.append(f"VuePress 容器未闭合={len(container_stack)}")

    if pending_fault_symptom is not None:
        issues.append(
            f"第 {pending_fault_symptom[0]} 行故障分析问题后必须紧跟以解决方案为开头的行"
        )

    if fault_bullet_lines:
        section_end = fault_end or len(lines) + 1
        boundaries = []
        for index in range(len(fault_bullet_lines) - 1):
            current, following = fault_bullet_lines[index], fault_bullet_lines[index + 1]
            problem_to_solution = not current[2] and following[2]
            boundaries.append((current[0], following[0], problem_to_solution))
        boundaries.append((fault_bullet_lines[-1][0], section_end, False))
        for line_number, next_item, problem_to_solution in boundaries:
            between = lines[line_number:next_item - 1]
            if problem_to_solution:
                if any(not value.strip() for value in between):
                    issues.append(
                        f"第 {line_number} 行故障分析解决方案必须紧跟问题行：两者之间不允许空行"
                    )
            elif not any(not value.strip() for value in between):
                issues.append(
                    f"第 {line_number} 行故障分析条目之间至少需要 1 空行"
                )

    if reference_start is not None and not reference_content_lines:
        issues.append(
            f"第 {reference_start} 行建议参考项至少包含一个参考项"
        )
    for earlier, later in zip(reference_content_lines, reference_content_lines[1:]):
        between = lines[earlier:later - 1]
        if any(not value.strip() for value in between):
            issues.append(
                f"第 {earlier} 行建议参考项列表必须是紧凑列表：列表项之间不允许空行"
            )

    has_complete_java = re.search(r"```java[\s\S]*?\bpackage\s+", text) is not None
    if (
        section_match is not None
        and int(section_match.group(1)) >= 6
        and complete_example_lines
        and has_complete_java
        and not complete_details_lines
    ):
        issues.append("进阶页的完整最小例缺少“完整成品：<类名>”details 容器")
    issues.extend(inspect_validation_contract(path, text, docs_root=resolved_docs_root))
    issues.extend(inspect_table_contract(path, text, docs_root=resolved_docs_root))
    issues.extend(inspect_chapter_contract(path, text, docs_root=resolved_docs_root))
    return issues


def allowed_tutorial_paths(
    paths: Sequence[str], *, docs_root: Path
) -> tuple[list[str], list[str]]:
    requested = list(paths) or [str(docs_root)]
    allowed: list[str] = []
    errors: list[str] = []
    resolved_root = docs_root.resolve()
    for path in resolve_input_paths(requested):
        try:
            path.resolve().relative_to(resolved_root)
        except ValueError:
            errors.append(
                f"check_markdown_structure.py 只检查 docs 目录中的教程文档：{display_path(path)}"
            )
            continue
        allowed.append(str(path))
    return allowed, errors


def run(
    paths: Sequence[str],
    *,
    verbose: bool = False,
    docs_root: Path | None = None,
) -> dict[str, Any]:
    resolved_docs_root = (docs_root or repository_root() / "docs").resolve()
    allowed, scope_errors = allowed_tutorial_paths(paths, docs_root=resolved_docs_root)
    files, skipped, errors = (
        collect_files(allowed, predicate=markdown_file) if allowed else ([], 0, [])
    )
    summary = make_summary(
        scanned=len(files), skipped=skipped, bytes_scanned=0
    )
    summary["errors"].extend(scope_errors)
    summary["errors"].extend(errors)

    for path in files:
        try:
            original, text, _ = read_utf8(path)
            summary["bytes"] += len(original)
            issues = inspect_markdown(path, text, docs_root=resolved_docs_root)
        except (OSError, UnicodeDecodeError) as exc:
            summary["errors"].append(f"{display_path(path)}：{exc}")
            continue
        if issues:
            summary["problem_files"] += 1
            summary["problems"] += len(issues)
            summary["files"].append({"path": display_path(path), "issues": issues})
        elif verbose:
            summary["files"].append({"path": display_path(path), "issues": []})
    return summary


def build_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(
        description="检查 docs 的章页外框、节页结构、标题、围栏、容器、速查表、引用与图片。"
    )
    parser.add_argument("paths", nargs="*", metavar="PATH", help="docs 内的文件或目录；默认 docs。")
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
