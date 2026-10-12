from __future__ import annotations

import argparse
import base64
import json
import os
import re
import sys
from html.parser import HTMLParser
from pathlib import Path
from typing import Any, Callable, Iterable, Sequence
from urllib.parse import unquote, urlparse

from check_markdown_format import check_markdown_format_text
from check_markdown_structure import inspect_markdown
from _common import atomic_write, display_path, emit, exit_code_for, make_summary


HASH_SUFFIX = re.compile(r"-[A-Za-z0-9_-]{8}(?=\.[A-Za-z0-9]+$)")
ROUTE_LINK = re.compile(r"/chapter\d+/section\d+/")
DATA_IMAGE = re.compile(r"^data:(image/[A-Za-z0-9.+-]+);base64,(.+)$", re.S)
ALIGN_MAP = {"right": "---:", "left": ":---", "center": ":---:"}
DEFAULT_CONTAINER_TITLES = {
    "info": "相关信息",
    "tip": "提示",
    "warning": "注意",
    "caution": "警告",
    "important": "重要",
    "note": "注释",
}
IMAGE_EXTENSIONS = {
    "image/gif": {".gif"},
    "image/jpeg": {".jpg", ".jpeg"},
    "image/png": {".png"},
    "image/svg+xml": {".svg"},
    "image/webp": {".webp"},
}
IMAGE_FILENAME = re.compile(
    r"^[A-Za-z0-9][A-Za-z0-9._-]*\.(?:gif|jpe?g|png|svg|webp)$",
    re.IGNORECASE,
)
INLINE_MARKDOWN = frozenset("\\`*_[]<#~")
ENTITY_REFERENCE = re.compile(r"&(?:#[0-9]+|#x[0-9A-Fa-f]+|[A-Za-z][A-Za-z0-9]+);")
BLOCK_PREFIXES = (
    re.compile(r"#{1,6}(?:[ \t]|$)"),
    re.compile(r">(?:[ \t]|$)"),
    re.compile(r"[-+](?:[ \t]|$)"),
    re.compile(r"\d{1,9}[.)](?:[ \t]|$)"),
    re.compile(r"\|"),
    re.compile(r":{3,}(?:[ \t]|$)"),
    re.compile(r"(?:={3,}|-{3,}|~{3,})[ \t]*$"),
)
VOID_TAGS = frozenset(
    {
        "area",
        "base",
        "br",
        "col",
        "embed",
        "hr",
        "img",
        "input",
        "link",
        "meta",
        "source",
        "track",
        "wbr",
    }
)
INLINE_MARK_TAGS = {"strong": "strong", "em": "em", "s": "s", "del": "s", "strike": "s"}


class RestoreError(RuntimeError):
    """页面无法按项目结构恢复。"""


def _normalize_markdown(text: str) -> str:
    for _ in range(8):
        normalized, issues = check_markdown_format_text(text)
        if normalized == text:
            if issues:
                raise RestoreError("恢复结果不符合 Markdown 规范：" + "；".join(issues))
            return normalized
        text = normalized
    raise RestoreError("恢复结果无法收敛到 Markdown 格式规范固定点")


class Node:
    __slots__ = ("tag", "attrs", "children", "text")

    def __init__(self, tag: str | None, attrs: list[tuple[str, str | None]]):
        self.tag = tag
        self.attrs = {key: value or "" for key, value in attrs}
        self.children: list[Node] = []
        self.text = ""

    def itertext(self) -> Iterable[str]:
        for child in self.children:
            if child.tag is None:
                yield child.text
            else:
                yield from child.itertext()


class DOMBuilder(HTMLParser):
    def __init__(self) -> None:
        super().__init__(convert_charrefs=True)
        self.root = Node("root", [])
        self.stack: list[Node] = [self.root]

    def handle_starttag(self, tag: str, attrs: list[tuple[str, str | None]]) -> None:
        node = Node(tag, attrs)
        self.stack[-1].children.append(node)
        if tag not in VOID_TAGS:
            self.stack.append(node)

    def handle_startendtag(self, tag: str, attrs: list[tuple[str, str | None]]) -> None:
        self.stack[-1].children.append(Node(tag, attrs))

    def handle_endtag(self, tag: str) -> None:
        if tag in VOID_TAGS:
            return
        for index in range(len(self.stack) - 1, 0, -1):
            if self.stack[index].tag == tag:
                del self.stack[index:]
                return

    def handle_data(self, data: str) -> None:
        node = Node(None, [])
        node.text = data
        self.stack[-1].children.append(node)


def _classes(node: Node) -> set[str]:
    return set(node.attrs.get("class", "").split())


def _children(node: Node, tag: str) -> list[Node]:
    return [child for child in node.children if child.tag == tag]


def _descendants(node: Node, tag: str | None = None) -> Iterable[Node]:
    for child in node.children:
        if tag is None or child.tag == tag:
            yield child
        yield from _descendants(child, tag)


def _find_content(root: Node) -> Node:
    for node in _descendants(root, "div"):
        if node.attrs.get("id") == "content":
            return node
    raise RestoreError("HTML 中缺少 id=content 的教程正文")


def _outer_mark(node: Node) -> str | None:
    direct = INLINE_MARK_TAGS.get(node.tag or "")
    if direct is not None:
        return direct
    if node.tag != "a" or len(node.children) != 1:
        return None
    return INLINE_MARK_TAGS.get(node.children[0].tag or "")


def _without_outer_mark(node: Node, mark: str) -> list[Node]:
    if INLINE_MARK_TAGS.get(node.tag or "") == mark:
        return node.children
    link = Node("a", list(node.attrs.items()))
    link.children = node.children[0].children
    return [link]


def _merge_inline_mark_runs(node: Node) -> None:
    for child in node.children:
        _merge_inline_mark_runs(child)

    merged: list[Node] = []
    index = 0
    while index < len(node.children):
        mark = _outer_mark(node.children[index])
        if mark is None:
            merged.append(node.children[index])
            index += 1
            continue
        end = index + 1
        while end < len(node.children) and _outer_mark(node.children[end]) == mark:
            end += 1
        if end == index + 1:
            merged.append(node.children[index])
            index = end
            continue
        wrapper = Node(mark, [])
        for child in node.children[index:end]:
            wrapper.children.extend(_without_outer_mark(child, mark))
        merged.append(wrapper)
        index = end
    node.children = merged


def _inline_code(value: str) -> str:
    longest = max((len(item) for item in re.findall(r"`+", value)), default=0)
    marker = "`" * (longest + 1)
    padding = " " if value.startswith("`") or value.endswith("`") else ""
    return f"{marker}{padding}{value}{padding}{marker}"


def _escape_block_prefix(line: str) -> str:
    indentation = len(line) - len(line.lstrip(" "))
    if indentation > 3:
        return line
    content = line[indentation:]
    for pattern in BLOCK_PREFIXES:
        match = pattern.match(content)
        if not match:
            continue
        if content[0].isdigit():
            punctuation = re.search(r"[.)]", content)
            if punctuation is None:
                return line
            index = indentation + punctuation.start()
        else:
            index = indentation
        return line[:index] + "\\" + line[index:]
    return line


def _escape_markdown_text(value: str) -> str:
    value = value.replace("\r\n", "\n").replace("\r", "\n")
    output: list[str] = []
    position = 0
    while position < len(value):
        char = value[position]
        if char in INLINE_MARKDOWN:
            output.append("\\" + char)
        elif char == "&" and ENTITY_REFERENCE.match(value, position):
            output.append("\\&")
        else:
            output.append(char)
        position += 1
    return "\n".join(_escape_block_prefix(line) for line in "".join(output).split("\n"))


def _link_destination(value: str) -> str:
    escaped = value.replace("\\", "\\\\").replace("<", "%3C").replace(">", "%3E")
    if re.search(r"[\s()]", escaped):
        return f"<{escaped}>"
    return escaped


def _format_highlight_lines(lines: list[int]) -> str:
    if not lines:
        return ""
    groups: list[str] = []
    start = previous = lines[0]
    for line in lines[1:] + [-1]:
        if line == previous + 1:
            previous = line
            continue
        if previous - start >= 2:
            groups.append(f"{start}-{previous}")
        else:
            groups.extend(str(item) for item in range(start, previous + 1))
        start = previous = line
    return " {" + ",".join(groups) + "}"


class MarkdownRestorer:
    def __init__(self, asset_loader: Callable[[str], bytes] | None = None) -> None:
        self.assets: dict[str, bytes] = {}
        self.asset_loader = asset_loader

    def inline(self, node: Node) -> str:
        tag = node.tag
        if tag is None:
            return _escape_markdown_text(node.text)
        if tag == "em":
            return "*" + "".join(self.inline(child) for child in node.children) + "*"
        if tag == "strong":
            return "**" + "".join(self.inline(child) for child in node.children) + "**"
        if tag in {"s", "del", "strike"}:
            return "~~" + "".join(self.inline(child) for child in node.children) + "~~"
        if tag == "code":
            return _inline_code("".join(node.itertext()))
        if tag == "a":
            text = "".join(self.inline(child) for child in node.children)
            classes = _classes(node)
            if "header-anchor" in classes:
                return text
            href = node.attrs.get("href", "")
            if "route-link" in classes and ROUTE_LINK.search(href):
                return "".join(node.itertext())
            return f"[{text}]({_link_destination(href)})"
        if tag == "img":
            return self.image(node)
        if tag == "br":
            return "<br>\n"
        return "".join(self.inline(child) for child in node.children)

    def image(self, node: Node) -> str:
        source = node.attrs.get("src", "")
        alt = _escape_markdown_text(node.attrs.get("alt", ""))
        title = node.attrs.get("title", "")
        if not title:
            raise RestoreError("图片 title 缺少原始文件名")
        if not IMAGE_FILENAME.fullmatch(title) or ".." in title:
            raise RestoreError(f"图片 title 不是合法文件名：{title}")
        match = DATA_IMAGE.match(source)
        if match:
            mime_type, payload = match.groups()
            try:
                data = base64.b64decode(payload, validate=True)
            except ValueError as exc:
                raise RestoreError("data URI 图片不是有效 Base64") from exc
            extensions = IMAGE_EXTENSIONS.get(mime_type)
            if extensions is None:
                raise RestoreError(f"不支持的 data URI 图片类型：{mime_type}")
            if Path(title).suffix.casefold() not in extensions:
                raise RestoreError(
                    f"图片 title 扩展名与 data URI 类型不一致：{title} != {mime_type}"
                )
            previous = self.assets.get(title)
            if previous is not None and previous != data:
                raise RestoreError(f"图片文件名冲突：{title}")
            self.assets[title] = data
            return f'![{alt}](./{title} "{title}")'

        parsed = urlparse(source)
        if parsed.scheme in {"http", "https"}:
            source_name = unquote(os.path.basename(parsed.path))
            if source_name != title:
                raise RestoreError(
                    f"图片 title 与远程文件名不一致：{title} != {source_name}"
                )
            return f'![{alt}]({_link_destination(source)} "{title}")'
        name = unquote(os.path.basename(parsed.path))
        if not name:
            raise RestoreError("图片缺少可恢复的文件名")
        restored_name = HASH_SUFFIX.sub("", name)
        if restored_name != title:
            raise RestoreError(
                f"图片 title 与构建产物文件名不一致：{title} != {restored_name}"
            )
        if self.asset_loader is not None:
            data = self.asset_loader(source)
            previous = self.assets.get(title)
            if previous is not None and previous != data:
                raise RestoreError(f"图片文件名冲突：{title}")
            self.assets[title] = data
        return f'![{alt}](./{title} "{title}")'

    def heading(self, node: Node) -> str:
        value = "".join(self.inline(child) for child in node.children).strip()
        return f"{'#' * int(node.tag[1])} {value}"

    def code_fence(self, node: Node) -> str:
        code_nodes = _children(node, "code")
        if not code_nodes:
            raise RestoreError("pre 元素缺少 code 子元素")
        code = code_nodes[0]
        language_match = re.search(r"(?:^|\s)language-([\w-]+)", code.attrs.get("class", ""))
        language = (
            code.attrs.get("data-language", "")
            or node.attrs.get("data-language", "")
            or (language_match.group(1) if language_match else "")
        )
        line_nodes = [
            child for child in code.children
            if child.tag == "span" and "line" in _classes(child)
        ]
        highlighted = [
            index for index, line in enumerate(line_nodes, 1)
            if "highlighted" in _classes(line)
        ]
        highlight_attr = (
            code.attrs.get("data-highlight-lines", "")
            or node.attrs.get("data-highlight-lines", "")
        )
        if highlight_attr:
            highlighted = sorted(
                {
                    line
                    for start, end in re.findall(r"(\d+)(?:\s*-\s*(\d+))?", highlight_attr)
                    for line in range(int(start), int(end or start) + 1)
                    if line > 0
                }
            )
        if line_nodes:
            if not "".join(line_nodes[-1].itertext()):
                line_nodes.pop()
            lines = ["".join(line.itertext()) for line in line_nodes]
        else:
            raw = "".join(code.itertext()).replace("\r\n", "\n").replace("\r", "\n")
            if raw.endswith("\n"):
                raw = raw[:-1]
            lines = raw.split("\n") if raw else []
        info = language + _format_highlight_lines(highlighted)
        return f"```{info}\n" + "\n".join(lines) + "\n```"

    def table(self, node: Node) -> str:
        rows: list[list[str]] = []
        for row in _descendants(node, "tr"):
            cells = []
            for cell in row.children:
                if cell.tag in {"th", "td"}:
                    value = "".join(self.inline(child) for child in cell.children).strip()
                    cells.append(value.replace("|", r"\|"))
            if cells:
                rows.append(cells)
        if not rows:
            raise RestoreError("table 元素没有数据行")

        headings = list(_descendants(node, "th"))
        aligns: list[str] = []
        for heading in headings[: len(rows[0])]:
            style = heading.attrs.get("style", "")
            found = re.search(r"text-align:\s*(\w+)", style)
            aligns.append(ALIGN_MAP.get(found.group(1) if found else "", "---"))
        if len(aligns) != len(rows[0]):
            aligns = ["---"] * len(rows[0])

        output = [
            "| " + " | ".join(rows[0]) + " |",
            "|" + "|".join(aligns) + "|",
        ]
        output.extend("| " + " | ".join(row) + " |" for row in rows[1:])
        return "\n".join(output)

    def list_block(self, node: Node, depth: int = 0) -> str:
        lines: list[str] = []
        start = int(node.attrs.get("start", "1") or "1")
        items = _children(node, "li")
        tight_attribute = node.attrs.get("data-dev-list-tight")
        if tight_attribute:
            tight = tight_attribute != "false"
        else:
            tight = not any(_children(item, "p") for item in items)
        for offset, item in enumerate(items):
            content: list[str | Node] = []
            inline_parts: list[str] = []

            def flush_inline() -> None:
                value = "".join(inline_parts).strip()
                if value:
                    content.append(value)
                inline_parts.clear()

            for child in item.children:
                if child.tag in {"ul", "ol"}:
                    flush_inline()
                    content.append(child)
                elif child.tag == "p":
                    flush_inline()
                    value = "".join(self.inline(grandchild) for grandchild in child.children).strip()
                    if value:
                        content.append(value)
                elif child.tag is None:
                    inline_parts.append(child.text)
                elif child.tag in {"a", "br", "code", "em", "img", "span", "strong"}:
                    inline_parts.append(self.inline(child))
                else:
                    flush_inline()
                    blocks = self.block(child)
                    content.extend(blocks)
            flush_inline()
            prefix = "    " * depth
            marker = f"{start + offset}. " if node.tag == "ol" else "- "
            continuation = prefix + " " * len(marker)
            started = False
            for part in content:
                if isinstance(part, Node):
                    if not started:
                        lines.append(prefix + marker.rstrip())
                        started = True
                    if not tight:
                        lines.append("")
                    lines.extend(self.list_block(part, depth + 1).splitlines())
                    continue
                value_lines = part.splitlines() or [""]
                if not started:
                    lines.append(prefix + marker + value_lines[0])
                    started = True
                    lines.extend(continuation + line if line else "" for line in value_lines[1:])
                else:
                    lines.append("")
                    lines.extend(continuation + line if line else "" for line in value_lines)
            if not started:
                lines.append(prefix + marker.rstrip())
            if not tight and offset < len(items) - 1:
                lines.append("")
        return "\n".join(lines)

    def container(self, node: Node) -> str:
        classes = _classes(node)
        container_type = node.attrs.get("data-vp-container", "") or next(
            (name for name in (*DEFAULT_CONTAINER_TITLES, "details") if name in classes),
            "details" if node.tag == "details" else "info",
        )
        title = node.attrs.get("data-vp-title", "")
        body_nodes: list[Node] = []
        for child in node.children:
            if child.tag == "summary":
                title = "".join(self.inline(grandchild) for grandchild in child.children).strip()
            elif child.tag == "p" and "hint-container-title" in _classes(child):
                title = "".join(self.inline(grandchild) for grandchild in child.children).strip()
            else:
                body_nodes.append(child)
        opening = f"::: {container_type}" + (f" {title}" if title else "")
        body: list[str] = []
        for child in body_nodes:
            body.extend(self.block(child))
        if not body:
            return opening + "\n:::\n"
        return opening + "\n\n" + "\n\n".join(body) + "\n\n:::"

    def block(self, node: Node) -> list[str]:
        tag = node.tag
        if tag is None:
            return []
        if tag in {"h1", "h2", "h3", "h4", "h5", "h6"}:
            return [self.heading(node)]
        if tag == "p":
            value = "".join(self.inline(child) for child in node.children).strip()
            return [value] if value else []
        if tag == "pre":
            return [self.code_fence(node)]
        if tag == "table":
            return [self.table(node)]
        if tag in {"ul", "ol"}:
            return [self.list_block(node)]
        if tag == "blockquote":
            body: list[str] = []
            for child in node.children:
                body.extend(self.block(child))
            lines: list[str] = []
            for index, value in enumerate(body):
                if index:
                    lines.append(">")
                lines.extend("> " + line if line else ">" for line in value.splitlines())
            return ["\n".join(lines)] if lines else []
        if tag == "hr":
            return ["---"]
        if node.attrs.get("data-auto-section-list") == "true":
            return ["<!-- AUTO:SECTION-LIST -->"]
        if (
            tag == "details"
            or (tag == "div" and "hint-container" in _classes(node))
            or node.attrs.get("data-vp-container")
        ):
            return [self.container(node)]
        blocks: list[str] = []
        for child in node.children:
            blocks.extend(self.block(child))
        return blocks

    def restore(self, html_text: str) -> str:
        builder = DOMBuilder()
        builder.feed(html_text)
        _merge_inline_mark_runs(builder.root)
        content = _find_content(builder.root)
        blocks: list[str] = []
        replace_section_list = False
        for child in content.children:
            if child.tag is None:
                continue
            if replace_section_list and (
                child.tag == "ul"
                or child.attrs.get("data-auto-section-list") == "true"
            ):
                blocks.append("<!-- AUTO:SECTION-LIST -->")
                replace_section_list = False
                continue
            rendered = self.block(child)
            blocks.extend(rendered)
            if child.tag == "h2" and "".join(child.itertext()).strip() == "本章各节":
                replace_section_list = True
        if replace_section_list:
            raise RestoreError("本章各节标题后缺少自动生成列表")
        if not blocks:
            raise RestoreError("教程正文为空")
        text = "\n\n".join(block.rstrip("\n") for block in blocks)
        return _normalize_markdown(text + "\n")


def restore_markdown_document(
    html_text: str,
    *,
    asset_loader: Callable[[str], bytes] | None = None,
) -> tuple[str, dict[str, bytes]]:
    restorer = MarkdownRestorer(asset_loader)
    return restorer.restore(html_text), restorer.assets


def restore_markdown_text(html_text: str) -> str:
    text, _ = restore_markdown_document(html_text)
    return text


def restore_stdin_payload(payload: dict[str, Any]) -> dict[str, Any]:
    html_text = payload.get("html")
    html_base64 = payload.get("htmlBase64")
    source_path = payload.get("path")
    docs_root = payload.get("docsRoot")
    if isinstance(html_base64, str):
        try:
            html_text = base64.b64decode(html_base64, validate=True).decode("utf-8")
        except (ValueError, UnicodeDecodeError) as exc:
            return {"ok": False, "diagnostics": [f"HTML Base64 不是有效 UTF-8：{exc}"]}
    if not isinstance(html_text, str) or not html_text.strip():
        return {"ok": False, "diagnostics": ["HTML 正文为空"]}
    if not isinstance(source_path, str) or not source_path:
        return {"ok": False, "diagnostics": ["缺少 Markdown 源文件路径"]}
    if not isinstance(docs_root, str) or not docs_root:
        return {"ok": False, "diagnostics": ["缺少 docs 根目录路径"]}

    try:
        markdown, assets = restore_markdown_document(html_text)
        issues = inspect_markdown(
            Path(source_path).resolve(),
            markdown,
            docs_root=Path(docs_root).resolve(),
        )
    except (OSError, UnicodeDecodeError, RestoreError, ValueError) as exc:
        return {"ok": False, "diagnostics": [str(exc)]}
    if issues:
        return {"ok": False, "diagnostics": issues}
    return {
        "ok": True,
        "markdown": markdown,
        "assets": [
            {
                "name": name,
                "size": len(data),
                "base64": base64.b64encode(data).decode("ascii"),
            }
            for name, data in sorted(assets.items())
        ],
    }


def _target_path(out: str) -> Path:
    target = Path(out).resolve()
    return target if target.suffix.casefold() == ".md" else target / "README.md"


def _load_page_asset(page: Path, source: str) -> bytes:
    parsed = urlparse(source)
    name = unquote(os.path.basename(parsed.path))
    if not name:
        raise RestoreError("图片缺少构建产物文件名")

    candidates = [page.parent / unquote(parsed.path)]
    candidates.extend(ancestor / "assets" / name for ancestor in page.parents)
    for candidate in candidates:
        if candidate.is_file():
            try:
                return candidate.read_bytes()
            except OSError as exc:
                raise RestoreError(f"无法读取图片构建产物：{candidate}") from exc
    raise RestoreError(f"找不到图片构建产物：{name}")


def run(
    html_path: str,
    *,
    out: str | None = None,
    verbose: bool = False,
) -> dict[str, Any]:
    page = Path(html_path).resolve()
    summary = make_summary(scanned=0, skipped=0, bytes_scanned=0)
    if not page.is_file():
        summary["errors"].append(f"{display_path(page)}：不是现有 HTML 文件")
        return summary
    if page.suffix.casefold() != ".html":
        summary["errors"].append(f"{display_path(page)}：输入必须是单个 HTML 文件")
        return summary

    try:
        html_bytes = page.read_bytes()
        html_text = html_bytes.decode("utf-8")
        restored, assets = restore_markdown_document(
            html_text,
            asset_loader=lambda source: _load_page_asset(page, source),
        )
    except (OSError, UnicodeDecodeError, RestoreError) as exc:
        summary["errors"].append(f"{display_path(page)}：{exc}")
        return summary

    summary["scanned"] = 1
    summary["bytes"] = len(html_bytes)
    summary["assets"] = len(assets)
    item = {
        "path": display_path(page),
        "issues": [],
        "changed": True,
        "written": False,
        "assets": len(assets),
    }
    summary["files"].append(item)
    if out is None:
        summary["changed"] = 1
        return summary

    target = _target_path(out)
    try:
        outputs = {target: restored.encode("utf-8")}
        for name, data in assets.items():
            outputs[target.parent / name] = data
        for output, data in outputs.items():
            if not output.exists() or output.read_bytes() != data:
                summary["changed"] += 1
                atomic_write(output, data)
                summary["written"] += 1
    except OSError as exc:
        summary["errors"].append(f"{display_path(target)}：{exc}")
        return summary
    item["written"] = bool(summary["written"])
    item["changed"] = bool(summary["changed"])
    return summary


def build_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(
        description="按项目 Markdown 规范从单个 VuePress HTML 页面恢复 Markdown。"
    )
    parser.add_argument("html", nargs="?", metavar="INDEX_HTML", help="单个 VuePress index.html 文件。")
    parser.add_argument(
        "--out",
        metavar="PATH",
        help="输出 Markdown 文件或目标目录 (目录内写入 README.md)；省略时只检查可恢复性。",
    )
    parser.add_argument("--json", action="store_true", help="输出 JSON。")
    parser.add_argument("--verbose", action="store_true", help="列出页面与解码图片数量。")
    parser.add_argument(
        "--stdin-json",
        action="store_true",
        help="从 stdin 读取整页恢复请求并向 stdout 输出 JSON。",
    )
    return parser


def main(argv: Sequence[str] | None = None) -> int:
    args = build_parser().parse_args(argv)
    if args.stdin_json:
        for stream in (sys.stdin, sys.stdout, sys.stderr):
            reconfigure = getattr(stream, "reconfigure", None)
            if reconfigure is not None:
                reconfigure(encoding="utf-8")
        try:
            payload = json.loads(sys.stdin.read())
            if not isinstance(payload, dict):
                raise ValueError("stdin JSON 顶层必须是对象")
            result = restore_stdin_payload(payload)
        except (json.JSONDecodeError, UnicodeDecodeError, ValueError) as exc:
            result = {"ok": False, "diagnostics": [str(exc)]}
        sys.stdout.write(json.dumps(result, ensure_ascii=False))
        return 0 if result.get("ok") else 1
    if not args.html:
        build_parser().error("必须提供 INDEX_HTML，或使用 --stdin-json")
    summary = run(args.html, out=args.out, verbose=args.verbose)
    emit(summary, json_output=args.json, verbose=args.verbose)
    return exit_code_for(summary)


if __name__ == "__main__":
    raise SystemExit(main())
