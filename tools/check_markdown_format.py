from __future__ import annotations

import argparse
import re
import string
from pathlib import Path
from typing import Any, Sequence

from _common import atomic_write, closing_fence, collect_files, display_path, emit, exit_code_for, make_summary, opening_fence, read_utf8
from check_text_format import normalized_bytes


NEWLINE = re.compile(r"\r\n|\r|\n")
TILDE_FENCE = re.compile(r"^(?P<indent> {0,3})(?P<run>~{3,})(?P<rest>.*)$")
LONG_BACKTICK_FENCE = re.compile(r"^ {0,3}`{4,}")
INLINE_CODE = re.compile(r"(?<!\\)(`+)(?:(?!\1).)*\1")
LIST_BULLET = re.compile(r"^(?P<indent> *)(?P<quote>(?:>[ \t]*)*)\*(?P<space>[ \t]+)")
HORIZONTAL_RULE = re.compile(
    r"^ {0,3}(?:(?:\*[ \t]*){3,}|(?:_[ \t]*){3,}|(?:-[ \t]*){3,})$"
)
ORDERED_SPACING = re.compile(r"^( {0,3})(\d+\.)([ \t]{2,})")
EMPHASIS_UNDERSCORE = re.compile(r"(?<![A-Za-z0-9_\\])_(?!_|\s)([^_\n]+?)(?<!\s)_(?![A-Za-z0-9_])")
INDENTED_BLOCK = re.compile(r"^ {4,}")
CJK = r"\u3400-\u4dbf\u4e00-\u9fff\uf900-\ufaff"
CHINESE_PUNCTUATION = "，。；：？！、"
DASH_TIGHT_NEIGHBOR = rf"[^\s{CJK}]"
DASH_UNSPACED_NEIGHBOR = rf"[^\s{CJK}{CHINESE_PUNCTUATION}]"
ESCAPABLE_ASCII = frozenset(string.punctuation)


def markdown_file(path: Path) -> bool:
    return path.suffix.casefold() == ".md"


def normalize_fence_markers(text: str) -> tuple[str, int, int]:
    """围栏只允许三个反引号：波浪号围栏行改写为反引号，超长反引号围栏行只计数。

    围栏不嵌套，按围栏类型跟踪当前状态：反引号围栏内的波浪号行、
    波浪号围栏内的反引号行都是代码内容，不改写也不计入问题。
    """
    output: list[str] = []
    state: tuple[str, int] | None = None
    tilde_total = 0
    long_total = 0
    conflicting_backticks = False
    for line in text.splitlines(keepends=True):
        body = line
        newline = ""
        if line.endswith("\r\n"):
            body, newline = line[:-2], "\r\n"
        elif line.endswith(("\r", "\n")):
            body, newline = line[:-1], line[-1]
        if state is not None and state[0] == "~" and closing_fence(body, "`", 3):
            conflicting_backticks = True
        opener = opening_fence(body)
        boundary = False
        if state is None and opener:
            state = opener
            boundary = True
        elif state is not None and closing_fence(body, *state):
            boundary = True
            opener = state
            state = None
        if boundary and opener:
            if opener[0] == "~":
                tilde = TILDE_FENCE.match(body)
                body = tilde.group("indent") + "```" + tilde.group("rest")
                tilde_total += 1
            elif LONG_BACKTICK_FENCE.match(body):
                long_total += 1
        output.append(body + newline)
    if state is not None or conflicting_backticks:
        return text, tilde_total, long_total
    return "".join(output), tilde_total, long_total


def mask_inline_code(line: str) -> str:
    return INLINE_CODE.sub(lambda match: " " * len(match.group(0)), line)


def prose_lines(text: str) -> list[str]:
    lines: list[str] = []
    fence_marker: tuple[str, int] | None = None
    for line in text.splitlines():
        if fence_marker is not None:
            if closing_fence(line, *fence_marker):
                fence_marker = None
            continue
        opener = opening_fence(line)
        if opener:
            fence_marker = opener
            continue
        if fence_marker is None:
            lines.append(INLINE_CODE.sub("¤", line))
    return lines


def inspect_paired_quotes(lines: list[str]) -> tuple[int, int]:
    external_tight = 0
    internal_space = 0
    for line in lines:
        for quote in ('"', "'"):
            positions = [index for index, char in enumerate(line) if char == quote]
            for pair_start in range(0, len(positions) - 1, 2):
                opening = positions[pair_start]
                closing = positions[pair_start + 1]
                if opening > 0 and re.match(rf"[{CJK}]", line[opening - 1]):
                    external_tight += 1
                if closing + 1 < len(line) and re.match(rf"[{CJK}]", line[closing + 1]):
                    external_tight += 1
                content = line[opening + 1:closing]
                if content.strip(" \t"):
                    if opening + 1 < closing and line[opening + 1] in " \t":
                        internal_space += 1
                    if closing - 1 > opening and line[closing - 1] in " \t":
                        internal_space += 1
    return external_tight, internal_space


def invalid_backslash_count(text: str) -> int:
    count = 0
    position = 0
    while position < len(text):
        if text[position] != "\\":
            position += 1
            continue
        if position + 1 >= len(text) or text[position + 1] not in ESCAPABLE_ASCII:
            count += 1
            position += 1
            continue
        position += 2
    return count


def escape_invalid_backslashes(text: str) -> str:
    output: list[str] = []
    position = 0
    while position < len(text):
        char = text[position]
        if char != "\\":
            output.append(char)
            position += 1
            continue
        if position + 1 >= len(text) or text[position + 1] not in ESCAPABLE_ASCII:
            output.append("\\\\")
            position += 1
            continue
        output.append("\\" + text[position + 1])
        position += 2
    return "".join(output)


def inspect_markdown_symbols(text: str) -> list[str]:
    issues: list[str] = []
    inspection_text = re.sub(r"([，。；：？！、])[ \t]+(?=\*/)", r"\1*/", text)
    checks = (
        ("全角括号", re.compile(r"[（）]")),
        ("中文弯引号", re.compile(r"[“”‘’]")),
        ("中文省略号", re.compile(r"…")),
        ("连续四个及以上句点", re.compile(r"\.{4,}")),
        ("连续四个及以上冒号", re.compile(r":{4,}")),
        ("ASCII 左括号外侧紧邻中文", re.compile(rf"[{CJK}]\(")),
        ("ASCII 右括号外侧紧邻中文", re.compile(rf"\)[{CJK}]")),
        ("双破折号紧邻中文", re.compile(rf"(?:[{CJK}]——|——[{CJK}])")),
        (
            "中文标点前后存在空格",
            re.compile(rf"(?:[ \t]+[{CHINESE_PUNCTUATION}]|[{CHINESE_PUNCTUATION}][ \t]+)"),
        ),
    )
    for label, pattern in checks:
        count = len(pattern.findall(inspection_text))
        if count:
            issues.append(f"{label}={count}")
    prose = prose_lines(inspection_text)
    prose_text = "\n".join(prose)
    paren_internal = len(re.findall(r"(?:\([ \t]+|[ \t]+\))", prose_text))
    if paren_internal:
        issues.append(f"ASCII 括号内侧存在空格={paren_internal}")
    quote_external, quote_internal = inspect_paired_quotes(prose)
    if quote_external:
        issues.append(f"ASCII 引号外侧紧邻中文={quote_external}")
    if quote_internal:
        issues.append(f"ASCII 引号内侧存在空格={quote_internal}")
    backslash_count = invalid_backslash_count(prose_text)
    if backslash_count:
        issues.append(f"未作为转义符的反斜杠={backslash_count}")
    single_dash_count = 0
    for line in inspection_text.splitlines():
        line_without_inline_code = INLINE_CODE.sub("", line)
        for cell_or_text in line_without_inline_code.split("|"):
            if cell_or_text.strip() == "—":
                continue
            single_dash_count += len(re.findall(r"(?<!—)—(?!—)", cell_or_text))
    if single_dash_count:
        issues.append(f"表格空值占位以外的单破折号={single_dash_count}")
    unspaced_dash_count = 0
    for line in prose_lines(inspection_text):
        unspaced_dash_count += len(
            re.findall(rf"(?:{DASH_TIGHT_NEIGHBOR})——|——(?:{DASH_TIGHT_NEIGHBOR})", line)
        )
    if unspaced_dash_count:
        issues.append(f"双破折号未隔开={unspaced_dash_count}")
    return issues


def format_symbol_line(line: str, *, format_quotes: bool = True) -> str:
    translations = {"（": "(", "）": ")"}
    if format_quotes:
        translations.update({"“": '"', "”": '"', "‘": "'", "’": "'"})
    fixed = line.translate(str.maketrans(translations))
    fixed = re.sub(r"…+", "...", fixed)
    fixed = re.sub(r"\.{4,}", "...", fixed)
    fixed = re.sub(r":{4,}", ":::", fixed)
    fixed = re.sub(r"\([ \t]+", "(", fixed)
    fixed = re.sub(r"[ \t]+\)", ")", fixed)
    fixed = re.sub(rf"([{CJK}])\(", r"\1 (", fixed)
    fixed = re.sub(rf"\)([{CJK}])", r") \1", fixed)
    fixed = re.sub(rf"([{CJK}])——", r"\1 ——", fixed)
    fixed = re.sub(rf"——([{CJK}])", r"—— \1", fixed)
    fixed = re.sub(rf"[ \t]+([{CHINESE_PUNCTUATION}])", r"\1", fixed)
    fixed = re.sub(rf"([{CHINESE_PUNCTUATION}])[ \t]+", r"\1", fixed)

    if not format_quotes:
        return fixed
    for quote in ('"', "'"):
        positions = [index for index, char in enumerate(fixed) if char == quote]
        for pair_start in range(0, len(positions) - 1, 2):
            opening = positions[pair_start]
            closing = positions[pair_start + 1]
            quoted_content = fixed[opening + 1:closing]
            quoted = quoted_content.strip(" \t") if quoted_content.strip(" \t") else quoted_content
            fixed = fixed[:opening + 1] + quoted + fixed[closing:]
            positions = [index for index, char in enumerate(fixed) if char == quote]
            opening = positions[pair_start]
            closing = positions[pair_start + 1]
            if opening > 0 and re.match(rf"[{CJK}]", fixed[opening - 1]):
                fixed = fixed[:opening] + " " + fixed[opening:]
                positions = [index for index, char in enumerate(fixed) if char == quote]
                opening = positions[pair_start]
                closing = positions[pair_start + 1]
            if closing + 1 < len(fixed) and re.match(rf"[{CJK}]", fixed[closing + 1]):
                fixed = fixed[:closing + 1] + " " + fixed[closing + 1:]
    return fixed


def format_code_comments(
    line: str, in_block_comment: bool | str, *, hash_comments: bool = False
) -> tuple[str, bool | str]:
    output: list[str] = []
    position = 0
    while position < len(line):
        if isinstance(in_block_comment, str):
            closing = line.find(in_block_comment, position)
            if closing < 0:
                output.append(line[position:])
                return "".join(output), in_block_comment
            output.append(line[position:closing + len(in_block_comment)])
            position = closing + len(in_block_comment)
            in_block_comment = False
            continue
        if in_block_comment:
            closing = line.find("*/", position)
            if closing < 0:
                output.append(format_symbol_line(line[position:]))
                return "".join(output), True
            comment = line[position:closing]
            trailing = comment[len(comment.rstrip(" \t")):]
            core = comment[:len(comment) - len(trailing)] if trailing else comment
            output.append(format_symbol_line(core) + trailing + "*/")
            position = closing + 2
            in_block_comment = False
            continue
        char = line[position]
        if line.startswith('"""', position):
            output.append('"""')
            position += 3
            in_block_comment = '"""'
            continue
        if char in {'"', "'"}:
            closing = position + 1
            escaped = False
            while closing < len(line):
                current = line[closing]
                if escaped:
                    escaped = False
                elif current == "\\":
                    escaped = True
                elif current == char:
                    break
                closing += 1
            if closing >= len(line):
                output.append(line[position:])
                return "".join(output), in_block_comment
            output.append(line[position:closing + 1])
            position = closing + 1
            continue
        if line.startswith("//", position):
            output.append("//" + format_symbol_line(line[position + 2:]))
            return "".join(output), False
        if line.startswith("/*", position):
            output.append("/*")
            position += 2
            in_block_comment = True
            continue
        if hash_comments and char == "#" and not line.startswith("#version", position):
            output.append("#" + format_symbol_line(line[position + 1:]))
            return "".join(output), in_block_comment
        output.append(char)
        position += 1
    return "".join(output), in_block_comment


def format_unspaced_dashes(line: str) -> str:
    protected: list[str] = []

    def protect(match: re.Match[str]) -> str:
        protected.append(match.group(0))
        return f"\ufff4{len(protected) - 1}\ufff5"

    masked = INLINE_CODE.sub(protect, line)
    masked = re.sub(rf"({DASH_UNSPACED_NEIGHBOR})——", r"\1 ——", masked)
    masked = re.sub(rf"——({DASH_UNSPACED_NEIGHBOR})", r"—— \1", masked)
    for index, content in enumerate(protected):
        masked = masked.replace(f"\ufff4{index}\ufff5", content)
    return masked


def format_prose_line(line: str) -> str:
    protected: list[str] = []

    def protect(match: re.Match[str]) -> str:
        protected.append(match.group(0))
        return f"\ufff0{len(protected) - 1}\ufff1"

    masked = INLINE_CODE.sub(protect, line)
    fixed = format_symbol_line(masked)
    for index, content in enumerate(protected):
        fixed = fixed.replace(f"\ufff0{index}\ufff1", content)
    return fixed


def format_prose_backslashes(line: str) -> str:
    protected: list[str] = []

    def protect(match: re.Match[str]) -> str:
        protected.append(match.group(0))
        return f"\ufff2{len(protected) - 1}\ufff3"

    fixed = escape_invalid_backslashes(INLINE_CODE.sub(protect, line))
    for index, content in enumerate(protected):
        fixed = fixed.replace(f"\ufff2{index}\ufff3", content)
    return fixed


def format_symbol_text(text: str) -> str:
    output: list[str] = []
    in_fence = False
    fence_marker = ""
    fence_length = 0
    fence_language = ""
    block_comment = False
    for line in text.splitlines(keepends=True):
        newline = ""
        body = line
        if line.endswith("\r\n"):
            body, newline = line[:-2], "\r\n"
        elif line.endswith(("\r", "\n")):
            body, newline = line[:-1], line[-1]
        opener = opening_fence(body)
        if opener and not in_fence:
            in_fence = True
            fence_marker, fence_length = opener
            stripped = body.lstrip()
            info = stripped[fence_length:].strip().split(maxsplit=1)
            fence_language = info[0].casefold() if info else ""
            block_comment = False
            output.append(body + newline)
            continue
        is_closing = in_fence and closing_fence(body, fence_marker, fence_length)
        if in_fence and fence_language in {
            "java", "javascript", "js", "typescript", "ts", "glsl", "vert", "frag",
            "c", "cpp", "jsonc", "json5", "bash", "sh", "shell", "powershell",
            "ps1", "properties", "yaml", "yml",
        }:
            fixed_body, block_comment = format_code_comments(
                body,
                block_comment,
                hash_comments=fence_language
                in {"bash", "sh", "shell", "powershell", "ps1", "properties", "yaml", "yml", "json", "json5"},
            )
        elif in_fence:
            fixed_body = body
        elif INDENTED_BLOCK.match(body) and not re.match(r"^ +(?:[-*]|\d+\.)[ \t]+", body):
            fixed_body = body
        else:
            body = format_prose_backslashes(body)
            body = format_unspaced_dashes(body)
            # 分隔符位置从等长屏蔽文本取得，行内代码按原字节参与恢复。
            boundaries = [match.start() for match in re.finditer(r"(?<!\\)\|", mask_inline_code(body))]
            starts = [0] + [index + 1 for index in boundaries]
            ends = boundaries + [len(body)]
            parts = [body[start:end] for start, end in zip(starts, ends)]
            for index, part in enumerate(parts):
                if part.strip() != "—":
                    parts[index] = format_prose_line(part)
            fixed_body = "|".join(parts)
        output.append(fixed_body + newline)
        if is_closing:
            in_fence = False
            fence_language = ""
    return "".join(output)


def count_and_fix(line: str, *, allow_indented_bullet: bool = False) -> tuple[str, int, int, int, int]:
    """修复行内标记格式；返回 (修复后行, bullet 数, bullet 多空格数, 有序列表多空格数, _ 斜体数)。

    只做行首项目符号替换：无序列表统一为 ``- ``，有序列表后的多余空格收敛为一个；
    _ 斜体标记替换为 * 斜体标记。行内代码经等长占位屏蔽后，
    匹配区间映射回原行，仅替换两个分隔符位置的字符。
    """
    fixed = line
    bullet = 0
    bullet_spacing = 0
    if not HORIZONTAL_RULE.match(line):
        match = LIST_BULLET.match(line)
        if match and (len(match.group("indent")) < 4 or allow_indented_bullet):
            fixed = (
                line[: match.start()]
                + match.group("indent")
                + match.group("quote")
                + "- "
                + line[match.end() :]
            )
            bullet = 1
            bullet_spacing = int(len(match.group("space")) > 1)
    fixed, ordered = ORDERED_SPACING.subn(lambda match: match.group(1) + match.group(2) + " ", fixed)

    masked = mask_inline_code(fixed)
    matches = list(EMPHASIS_UNDERSCORE.finditer(masked))
    emphasis = 0
    if matches:
        chars = list(fixed)
        for match in matches:
            start, end = match.span()
            if chars[start] == "_":
                chars[start] = "*"
            if chars[end - 1] == "_":
                chars[end - 1] = "*"
            emphasis += 1
        fixed = "".join(chars)
    return fixed, bullet, bullet_spacing, ordered, emphasis


def check_markdown_format_text(text: str) -> tuple[str, list[str]]:
    """清理文档空白并修复围栏外的 Markdown 标记格式。

    返回 (修复后文本, issues)。围栏内保留代码结构，只清理行尾空白；
    4 空格缩进块继续原样保留（同样清理行尾空白）。围栏未闭合时保持文件不变。
    """
    newline_match = NEWLINE.search(text)
    newline = newline_match.group(0) if newline_match else "\n"
    original_text = text
    text, tilde_total, long_total = normalize_fence_markers(text)
    parts = NEWLINE.split(text)
    separators = NEWLINE.findall(text)
    lines = [
        (parts[index], separators[index] if index < len(separators) else "")
        for index in range(len(parts))
    ]
    if lines and lines[-1] == ("", ""):
        lines.pop()

    output: list[str] = []
    in_fence = False
    fence_marker = ""
    fence_length = 0
    pending_blank = False
    removed_leading = 0
    collapsed_blank = 0
    trimmed_lines = 0
    bullet_total = 0
    ordered_total = 0
    emphasis_total = 0
    bullet_spacing_total = 0
    horizontal_rule_total = 0
    previous_nonblank = ""

    for line, original_newline in lines:
        if in_fence:
            if line.rstrip(" \t") != line:
                trimmed_lines += 1
            output.append(line + original_newline)
            if closing_fence(line, fence_marker, fence_length):
                in_fence = False
            continue

        opener = opening_fence(line)
        if opener:
            if pending_blank and output:
                output.append(newline)
            pending_blank = False
            output.append(line + original_newline)
            fence_marker, fence_length = opener
            in_fence = True
            continue

        cleaned = line.rstrip(" \t")
        if cleaned != line:
            trimmed_lines += 1
        indented_list = False
        if INDENTED_BLOCK.match(cleaned):
            indent = len(cleaned) - len(cleaned.lstrip(" "))
            prior_indent = len(previous_nonblank) - len(previous_nonblank.lstrip(" "))
            prior_is_list = bool(re.match(r"^ *(?:[-*]|\d+\.)[ \t]+", previous_nonblank))
            indented_list = indent >= 4 and prior_is_list and prior_indent <= indent
        if INDENTED_BLOCK.match(cleaned) and not indented_list:
            if cleaned == "":
                if not output:
                    removed_leading += 1
                elif pending_blank:
                    collapsed_blank += 1
                else:
                    pending_blank = True
                continue
            if pending_blank and output:
                output.append(newline)
            pending_blank = False
            output.append(line + original_newline)
            continue

        fixed, bullet, bullet_spacing, ordered, emphasis = count_and_fix(
            cleaned, allow_indented_bullet=indented_list
        )
        if HORIZONTAL_RULE.fullmatch(cleaned) and cleaned != "---":
            fixed = "---"
            horizontal_rule_total += 1
        if fixed == "":
            if not output:
                removed_leading += 1
            elif pending_blank:
                collapsed_blank += 1
            else:
                pending_blank = True
            continue

        bullet_total += bullet
        bullet_spacing_total += bullet_spacing
        ordered_total += ordered
        emphasis_total += emphasis
        if pending_blank and output:
            output.append(newline)
        pending_blank = False
        output.append(fixed + newline)
        previous_nonblank = cleaned

    issues: list[str] = []
    if in_fence:
        issues.append("代码围栏未闭合")
        return original_text, issues
    if removed_leading:
        issues.append(f"开头空行={removed_leading}")
    if collapsed_blank:
        issues.append(f"多余空行={collapsed_blank}")
    if pending_blank:
        issues.append("末尾空行")
    if trimmed_lines:
        issues.append(f"行尾空白={trimmed_lines}")
    if tilde_total:
        issues.append(f"波浪号围栏={tilde_total}")
    if long_total:
        issues.append(f"超长反引号围栏={long_total}")
    details = []
    if bullet_total:
        details.append(f"bullet 项目符号={bullet_total}")
    if bullet_spacing_total:
        details.append(f"bullet 多空格={bullet_spacing_total}")
    if ordered_total:
        details.append(f"有序列表多空格={ordered_total}")
    if emphasis_total:
        details.append(f"_斜体={emphasis_total}")
    if horizontal_rule_total:
        details.append(f"水平线写法={horizontal_rule_total}")
    if details:
        issues.append("标记格式：" + "、".join(details))
    issues.extend(inspect_markdown_symbols(text))

    cleaned_text = "".join(output)
    for _ in range(4):
        formatted_text = format_symbol_text(cleaned_text)
        if formatted_text == cleaned_text:
            break
        cleaned_text = formatted_text
    if not cleaned_text.endswith(("\r\n", "\r", "\n")):
        cleaned_text += newline
    return cleaned_text, issues


def run(paths: Sequence[str], *, apply: bool, verbose: bool = False) -> dict[str, Any]:
    files, skipped, errors = collect_files(paths, predicate=markdown_file)
    summary = make_summary(
        scanned=len(files), skipped=skipped, bytes_scanned=0
    )
    summary["errors"].extend(errors)

    for path in files:
        try:
            original, text, has_bom = read_utf8(path)
            summary["bytes"] += len(original)
            cleaned, issues = check_markdown_format_text(text)
            desired = normalized_bytes(cleaned, protect_fences=True)
        except (OSError, UnicodeDecodeError) as exc:
            summary["errors"].append(f"{display_path(path)}：{exc}")
            continue

        if issues and issues == ["代码围栏未闭合"]:
            summary["problem_files"] += 1
            summary["problems"] += 1
            summary["files"].append({"path": display_path(path), "issues": issues})
            continue
        if desired == original and not issues:
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
            "issues": issues or ["需要格式规范化"],
            "changed": is_changed,
            "written": False,
        }
        summary["files"].append(item)
        if apply and is_changed:
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
    parser = argparse.ArgumentParser(description="清理并修复 Markdown 格式：空白、无序列表、有序列表和 _ 斜体。")
    parser.add_argument("paths", nargs="*", metavar="PATH", help="文件或目录；默认仓库根目录。")
    parser.add_argument("--apply", action="store_true", help="写入修改；默认只报告。")
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
