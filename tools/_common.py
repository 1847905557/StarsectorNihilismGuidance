from __future__ import annotations

import hashlib
import json
import os
import re
import stat
import tempfile
from pathlib import Path
from typing import Any, Callable, Iterable


TEXT_EXTENSIONS = frozenset(
    {
        ".bat",
        ".cmd",
        ".cjs",
        ".css",
        ".csv",
        ".frag",
        ".glsl",
        ".gradle",
        ".html",
        ".java",
        ".js",
        ".json",
        ".json5",
        ".jsonc",
        ".md",
        ".mjs",
        ".properties",
        ".ps1",
        ".py",
        ".scss",
        ".ship",
        ".skill",
        ".ts",
        ".txt",
        ".variant",
        ".vert",
        ".vue",
        ".xml",
        ".yaml",
        ".yml",
    }
)
TEXT_NAMES = frozenset({".gitattributes", ".gitignore", ".node-version", "LICENSE"})
EXCLUDED_DIRECTORIES = frozenset(
    {
        ".cache",
        ".git",
        ".gradle",
        ".pytest_cache",
        ".temp",
        ".tmp",
        "__pycache__",
        "build",
        "dist",
        "node_modules",
    }
)
UTF8_BOM = b"\xef\xbb\xbf"
FENCE_OPEN = re.compile(r"^ {0,3}(`{3,}|~{3,}).*$")


def opening_fence(line: str) -> tuple[str, int] | None:
    match = FENCE_OPEN.match(line)
    if match is None:
        return None
    marker = match.group(1)
    return marker[0], len(marker)


def closing_fence(line: str, marker: str, minimum: int) -> bool:
    return re.fullmatch(rf" {{0,3}}{re.escape(marker)}{{{minimum},}}[ \t]*", line) is not None


def repository_root() -> Path:
    return Path(__file__).resolve().parents[1]


def resolve_input_paths(raw_paths: Iterable[str]) -> list[Path]:
    values = list(raw_paths)
    if not values:
        return [repository_root()]
    return [Path(os.path.abspath(Path(value).expanduser())) for value in values]


def is_excluded_directory(path: Path) -> bool:
    return path.name.casefold() in EXCLUDED_DIRECTORIES


def is_supported_text(path: Path) -> bool:
    return path.name in TEXT_NAMES or path.suffix.casefold() in TEXT_EXTENSIONS


def collect_files(
    raw_paths: Iterable[str],
    *,
    predicate: Callable[[Path], bool] | None = None,
) -> tuple[list[Path], int, list[str]]:
    roots = resolve_input_paths(raw_paths)
    files: dict[str, Path] = {}
    skipped = 0
    errors: list[str] = []

    for root in roots:
        if any(path.is_symlink() for path in (root, *root.parents)) or (root.is_dir() and is_excluded_directory(root)):
            skipped += 1
            continue
        if not root.exists():
            errors.append(f"路径不存在：{display_path(root)}")
            continue
        if root.is_file():
            if predicate is None or predicate(root):
                files[os.path.normcase(str(root))] = root
            else:
                skipped += 1
            continue

        def traversal_error(error: OSError) -> None:
            errors.append(f"遍历失败：{error}")

        for current, directory_names, file_names in os.walk(root, followlinks=False, onerror=traversal_error):
            current_path = Path(current)
            retained_directories: list[str] = []
            for name in sorted(directory_names, key=str.casefold):
                child = current_path / name
                if is_excluded_directory(child) or child.is_symlink():
                    skipped += 1
                else:
                    retained_directories.append(name)
            directory_names[:] = retained_directories

            for name in sorted(file_names, key=str.casefold):
                path = current_path / name
                if path.is_symlink():
                    skipped += 1
                elif predicate is None or predicate(path):
                    files[os.path.normcase(str(path))] = path
                else:
                    skipped += 1

    return sorted(files.values(), key=lambda item: (display_path(item).casefold(), display_path(item))), skipped, errors


def collect_text_files(raw_paths: Iterable[str]) -> tuple[list[Path], int, list[str]]:
    return collect_files(raw_paths, predicate=is_supported_text)


def display_path(path: Path) -> str:
    resolved = path.resolve()
    root = repository_root()
    try:
        return resolved.relative_to(root).as_posix()
    except ValueError:
        return str(resolved)


def read_utf8(path: Path) -> tuple[bytes, str, bool]:
    data = path.read_bytes()
    has_bom = data.startswith(UTF8_BOM)
    payload = data[len(UTF8_BOM) :] if has_bom else data
    return data, payload.decode("utf-8", errors="strict"), has_bom


def atomic_write(path: Path, data: bytes) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    original_mode = stat.S_IMODE(path.stat().st_mode) if path.exists() else None
    descriptor, temporary_name = tempfile.mkstemp(
        prefix=f".{path.name}.", suffix=".tmp", dir=path.parent
    )
    temporary_path = Path(temporary_name)
    try:
        with os.fdopen(descriptor, "wb") as stream:
            stream.write(data)
            stream.flush()
            os.fsync(stream.fileno())
        if original_mode is not None:
            os.chmod(temporary_path, original_mode)
        os.replace(temporary_path, path)
    finally:
        if temporary_path.exists():
            temporary_path.unlink()


def sha256_bytes(data: bytes) -> str:
    return hashlib.sha256(data).hexdigest().upper()


def make_summary(*, scanned: int, skipped: int, bytes_scanned: int) -> dict[str, Any]:
    return {
        "scanned": scanned,
        "skipped": skipped,
        "problem_files": 0,
        "problems": 0,
        "changed": 0,
        "written": 0,
        "errors": [],
        "bytes": bytes_scanned,
        "files": [],
    }


def status_for(summary: dict[str, Any]) -> str:
    if summary["errors"]:
        return "error"
    if summary["problems"]:
        return "findings"
    return "pass"


def exit_code_for(summary: dict[str, Any]) -> int:
    if summary["errors"]:
        return 2
    if summary["problems"]:
        return 1
    return 0


def emit(
    summary: dict[str, Any], *, json_output: bool, verbose: bool
) -> None:
    payload = {"status": status_for(summary), **summary}
    if json_output:
        print(json.dumps(payload, ensure_ascii=False, indent=2, sort_keys=True))
        return

    print(
        "状态={status} 扫描={scanned} 跳过={skipped} 问题文件={problem_files} "
        "问题={problems} 待修改={changed} 已写入={written} 错误={error_count} "
        "字节={bytes}".format(error_count=len(summary["errors"]), **payload)
    )
    if verbose:
        for item in summary["files"]:
            details = ", ".join(item.get("issues", []))
            suffix = f"：{details}" if details else ""
            print(f"{item['path']}{suffix}")
    for error in summary["errors"]:
        print(f"错误：{error}")
