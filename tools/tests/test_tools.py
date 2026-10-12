from __future__ import annotations

import base64
import os
import subprocess
import sys
import tempfile
import unittest
from pathlib import Path


TOOLS_DIR = Path(__file__).resolve().parents[1]
if str(TOOLS_DIR) not in sys.path:
    sys.path.insert(0, str(TOOLS_DIR))

import check_fixtures
import check_markdown_format
import check_markdown_structure
import check_standards
import check_text_format
import format_fault_analysis
import hash_scope
import restore_markdown
import text_maintenance
from _common import collect_text_files, is_supported_text


class ToolTests(unittest.TestCase):
    def make_root(self) -> tempfile.TemporaryDirectory[str]:
        return tempfile.TemporaryDirectory()

    def make_fixture_repo(
        self,
        root: Path,
        *,
        page_code: str | None = None,
        fixture_code: str | None = None,
        java_ids: str = '"C1-S1"',
        shader_ids: str = "",
        shader_page: str = "",
        shader_resources: dict[str, str] | None = None,
    ) -> None:
        page = root / "docs" / "chapter1" / "section1" / "README.md"
        page.parent.mkdir(parents=True)
        java_block = f"\n```java\n{page_code}\n```\n" if page_code is not None else "\n"
        page.write_text(f"# Test\n{java_block}{shader_page}", encoding="utf-8")
        unit = root / "tutorial-fixtures" / "units" / "C1-S1"
        unit.mkdir(parents=True)
        if fixture_code is not None:
            source = unit / "src" / "main" / "java" / "example" / "Sample.java"
            source.parent.mkdir(parents=True)
            source.write_text(fixture_code + "\n", encoding="utf-8")
        for name, content in (shader_resources or {}).items():
            resource = unit / "src" / "main" / "resources" / "data" / "shaders" / name
            resource.parent.mkdir(parents=True, exist_ok=True)
            resource.write_text(content + "\n", encoding="utf-8")
        build = root / "tutorial-fixtures" / "build.gradle"
        build.write_text(
            f"def javaUnitIds = [{java_ids}]\n"
            f"def shaderUnitIds = [{shader_ids}]\n",
            encoding="utf-8",
        )

    def test_01_text_whitelist(self) -> None:
        self.assertTrue(is_supported_text(Path("README.md")))
        self.assertTrue(is_supported_text(Path(".gitignore")))
        self.assertTrue(is_supported_text(Path("script.py")))
        self.assertFalse(is_supported_text(Path("image.png")))

    def test_02_excluded_directories(self) -> None:
        with self.make_root() as directory:
            root = Path(directory)
            (root / "keep.md").write_bytes(b"ok\r\n")
            (root / "node_modules").mkdir()
            (root / "node_modules" / "skip.md").write_bytes(b"skip\n")
            files, skipped, errors = collect_text_files([str(root)])
            self.assertEqual([path.name for path in files], ["keep.md"])
            self.assertGreaterEqual(skipped, 1)
            self.assertEqual(errors, [])

            explicit, explicit_skipped, explicit_errors = collect_text_files(
                [str(root / "node_modules")]
            )
            self.assertEqual(explicit, [])
            self.assertEqual(explicit_skipped, 1)
            self.assertEqual(explicit_errors, [])

    def test_03_symbolic_links_are_not_followed(self) -> None:
        with self.make_root() as directory:
            root = Path(directory)
            target = root / "target.md"
            target.write_bytes(b"ok\r\n")
            link = root / "link.md"
            try:
                os.symlink(target, link)
            except OSError as exc:
                self.skipTest(f"当前环境不能创建符号链接：{exc}")
            files, skipped, _ = collect_text_files([str(root)])
            self.assertEqual([path.name for path in files], ["target.md"])
            self.assertEqual(skipped, 1)

    def test_04_bom_detection(self) -> None:
        issues = check_text_format.inspect_text("text\n", True)
        self.assertIn("UTF-8 BOM", issues)

    def test_05_invalid_utf8_is_an_error(self) -> None:
        with self.make_root() as directory:
            path = Path(directory) / "bad.md"
            path.write_bytes(b"\xff\xfe")
            summary = check_text_format.run([str(path)], apply=True)
            self.assertEqual(len(summary["errors"]), 1)
            self.assertEqual(path.read_bytes(), b"\xff\xfe")

    def test_06_crlf_detection(self) -> None:
        issues = check_text_format.inspect_text("a\r\nb\r\n", False)
        self.assertTrue(any(issue.startswith("CRLF=") for issue in issues))

    def test_07_cr_only_detection(self) -> None:
        issues = check_text_format.inspect_text("a\rb\r", False)
        self.assertTrue(any(issue.startswith("CR-only=") for issue in issues))

    def test_08_mixed_newline_detection(self) -> None:
        issues = check_text_format.inspect_text("a\r\nb\nc\r", False)
        self.assertTrue(any(issue.startswith("CRLF=") for issue in issues))
        self.assertTrue(any(issue.startswith("CR-only=") for issue in issues))

    def test_09_trailing_whitespace_detection(self) -> None:
        issues = check_text_format.inspect_text("a  \r\n", False)
        self.assertIn("行尾空白=1", issues)

    def test_10_terminal_newline_detection(self) -> None:
        self.assertIn("缺少末尾行", check_text_format.inspect_text("a", False))
        self.assertIn(
            "末尾行多于一个", check_text_format.inspect_text("a\n\n", False)
        )

    def test_11_lf_normalization(self) -> None:
        desired = check_text_format.normalized_bytes("a\nb\r\nc\r\n\r\n")
        self.assertEqual(desired, b"a\nb\nc\n")

    def test_12_dry_run_does_not_write(self) -> None:
        with self.make_root() as directory:
            path = Path(directory) / "a.md"
            path.write_bytes(b"a\r\n")
            summary = check_text_format.run([str(path)], apply=False)
            self.assertEqual(summary["changed"], 1)
            self.assertEqual(summary["written"], 0)
            self.assertEqual(path.read_bytes(), b"a\r\n")

    def test_13_markdown_blank_cleanup(self) -> None:
        cleaned, issues = check_markdown_format.check_markdown_format_text("\n# H  \n\n\ntext\n\n")
        self.assertEqual(cleaned, "# H\n\ntext\n")
        self.assertTrue(issues)

    def test_13a_markdown_symbol_prohibitions_are_reported(self) -> None:
        text = (
            "模拟战(包括生涯内模拟战)返回。\r\n"
            "中文（全角）和“引号”以及……。\r\n"
            "省略....\r\n"
            "括号 ( 内侧 ) 有空格。\r\n"
            '中文"引号"中文。\r\n'
            "文字——说明。\r\n"
            "正文 — 说明。\r\n"
            "中文 ，标点。 \r\n"
        )
        _, issues = check_markdown_format.check_markdown_format_text(text)
        joined = "\n".join(issues)
        self.assertIn("ASCII 左括号外侧紧邻中文=1", joined)
        self.assertIn("ASCII 右括号外侧紧邻中文=1", joined)
        self.assertIn("全角括号=2", joined)
        self.assertIn("中文弯引号=2", joined)
        self.assertIn("中文省略号=2", joined)
        self.assertIn("连续四个及以上句点=1", joined)
        self.assertIn("ASCII 括号内侧存在空格=2", joined)
        self.assertIn("ASCII 引号外侧紧邻中文=2", joined)
        self.assertIn("双破折号紧邻中文=1", joined)
        self.assertIn("表格空值占位以外的单破折号=1", joined)
        self.assertIn("中文标点前后存在空格", joined)

    def test_13b_permitted_markdown_symbols_pass(self) -> None:
        text = (
            "模拟战 (包括生涯内模拟战) 返回。\r\n"
            '中文 "引号" 中文。\r\n'
            "文字 —— 说明。\r\n"
            "| 项目 | 值 |\r\n"
            "| --- | --- |\r\n"
            "| 状态 | — |\r\n"
        )
        cleaned, issues = check_markdown_format.check_markdown_format_text(text)
        self.assertEqual(cleaned, text)
        self.assertEqual(issues, [])

    def test_13ba_backslashes_must_escape_ascii_punctuation(self) -> None:
        text = (
            r"合法 \*强调字面量\*、\\ 和 \~。行内代码 `\n` 保留。"
            "\r\n"
            "非法 \\n、尾部 " + "\\"
            "\r\n"
        )
        _, issues = check_markdown_format.check_markdown_format_text(text)
        self.assertIn("未作为转义符的反斜杠=2", "\n".join(issues))

    def test_13bb_backslash_apply_doubles_only_invalid_prose_backslashes(self) -> None:
        text = "非法 \\n、尾部 \\\r\n合法 \\*字面量*、`\\n`\r\n"
        cleaned, issues = check_markdown_format.check_markdown_format_text(text)
        self.assertIn("未作为转义符的反斜杠=2", "\n".join(issues))
        self.assertEqual(cleaned, "非法 \\\\n、尾部 \\\\\r\n合法 \\*字面量*、`\\n`\r\n")

    def test_13bc_fenced_code_backslashes_are_preserved(self) -> None:
        text = "```java\r\nString path = \"C:\\\\data\";\r\n```\r\n"
        cleaned, issues = check_markdown_format.check_markdown_format_text(text)
        self.assertEqual(cleaned, text)
        self.assertNotIn("未作为转义符的反斜杠", "\n".join(issues))

    def test_13c_unambiguous_symbols_are_checked_inside_fences(self) -> None:
        text = "```text\r\n中文(错误)\r\n```\r\n"
        _, issues = check_markdown_format.check_markdown_format_text(text)
        self.assertTrue(any("ASCII 左括号外侧紧邻中文" in issue for issue in issues))

    def test_13d_markdown_symbol_apply_repairs_and_rechecks(self) -> None:
        with self.make_root() as directory:
            path = Path(directory) / "sample.md"
            path.write_bytes(
                "模拟战(包括生涯内模拟战)返回……\r\n文字——说明。\r\n".encode("utf-8")
            )
            applied = check_markdown_format.run([str(path)], apply=True)
            self.assertGreaterEqual(applied["written"], 1)
            checked = check_markdown_format.run([str(path)], apply=False)
            self.assertEqual(checked["problems"], 0)
            self.assertEqual(
                path.read_bytes().decode("utf-8"),
                "模拟战 (包括生涯内模拟战) 返回...\n文字 —— 说明。\n",
            )

    def test_13da_unspaced_double_dash_is_reported(self) -> None:
        text = (
            "`Addship` 接收装配 ID—— 游戏加载船体。\r\n"
            "差异日志器 ——open/black/storage 的税率。\r\n"
            "播放设定 ——：按顺序核对。\r\n"
        )
        _, issues = check_markdown_format.check_markdown_format_text(text)
        self.assertIn("双破折号未隔开=3", "\n".join(issues))

    def test_13db_spaced_double_dash_passes(self) -> None:
        text = "文字 —— 说明。\r\n`代码` —— open/black 的税率。\r\n"
        cleaned, issues = check_markdown_format.check_markdown_format_text(text)
        self.assertEqual(cleaned, text)
        self.assertEqual(issues, [])

    def test_13dc_fenced_unspaced_double_dash_is_ignored(self) -> None:
        text = "```java\r\n// value——comment\r\n```\r\n文字 —— 说明。\r\n"
        _, issues = check_markdown_format.check_markdown_format_text(text)
        self.assertNotIn("双破折号未隔开", "\n".join(issues))

    def test_13dd_markdown_symbol_apply_unspaced_dash(self) -> None:
        with self.make_root() as directory:
            path = Path(directory) / "sample.md"
            path.write_bytes(
                "`Addship` 接收装配 ID—— 游戏加载船体。\r\n".encode("utf-8")
            )
            applied = check_markdown_format.run([str(path)], apply=True)
            self.assertGreaterEqual(applied["written"], 1)
            checked = check_markdown_format.run([str(path)], apply=False)
            self.assertEqual(checked["problems"], 0)
            self.assertEqual(
                path.read_bytes().decode("utf-8"),
                "`Addship` 接收装配 ID —— 游戏加载船体。\n",
            )

    def test_13de_unspaced_dash_fixed_across_pipes(self) -> None:
        with self.make_root() as directory:
            path = Path(directory) / "sample.md"
            path.write_bytes(
                "2. 先查 `isDone()`—— 为真移除；否则查 `runWhilePaused() || !isPaused()`—— 为真才调。\r\n".encode("utf-8")
            )
            applied = check_markdown_format.run([str(path)], apply=True)
            self.assertGreaterEqual(applied["written"], 1)
            checked = check_markdown_format.run([str(path)], apply=False)
            self.assertEqual(checked["problems"], 0)
            self.assertEqual(
                path.read_bytes().decode("utf-8"),
                "2. 先查 `isDone()` —— 为真移除；否则查 `runWhilePaused() || !isPaused()` —— 为真才调。\n",
            )

    def test_13e_fenced_string_literal_is_byte_preserved(self) -> None:
        text = (
            "```java\r\n"
            'LOG.info("GearIn deployed " + side + ": aliveShips=" + aliveShips);\r\n'
            'LOG.info("GearIn shader plugin: runtime switch " + state);\r\n'
            'LOG.error("GearIn shader plugin: sprite not loaded: " + path);\r\n'
            'String padded = " leading and trailing ";\r\n'
            'String punctuation = "状态。 ";\r\n'
            "```\r\n"
        )
        cleaned, _ = check_markdown_format.check_markdown_format_text(text)
        self.assertEqual(cleaned, text)

    def test_13f_fenced_string_preserves_symbols_and_spaces(self) -> None:
        text = (
            "```java\r\n"
            'LOG.info("中文（测试） " + side);\r\n'
            "```\r\n"
        )
        cleaned, _ = check_markdown_format.check_markdown_format_text(text)
        self.assertEqual(cleaned, text)

    def test_13g_symbol_formatting_reaches_a_fixed_point(self) -> None:
        text = '正文 "第一处" 和 " 第二处"，以及 `inline`。\r\n'
        first, _ = check_markdown_format.check_markdown_format_text(text)
        second, issues = check_markdown_format.check_markdown_format_text(first)
        self.assertEqual(first, second)
        self.assertEqual(issues, [])

    def test_13h_inline_single_dash_literal_is_permitted(self) -> None:
        text = "单破折号写作 `—`。\r\n"
        cleaned, issues = check_markdown_format.check_markdown_format_text(text)
        self.assertEqual(cleaned, text)
        self.assertEqual(issues, [])

    def test_13i_shell_comment_obeys_symbol_rules(self) -> None:
        text = "```bash\r\ncommand # 中文(说明)\r\n```\r\n"
        cleaned, _ = check_markdown_format.check_markdown_format_text(text)
        self.assertIn("# 中文 (说明)", cleaned)

    def test_13j_fenced_pipes_do_not_split_code_or_comments(self) -> None:
        text = (
            "```java\r\n"
            'if (enabled || forced) { LOG.info("left|right "); } // 中文(说明)\r\n'
            "/** 中文(说明)。 */\r\n"
            "```\r\n"
        )
        cleaned, _ = check_markdown_format.check_markdown_format_text(text)
        self.assertIn('enabled || forced', cleaned)
        self.assertIn('"left|right "', cleaned)
        self.assertIn('// 中文 (说明)', cleaned)
        self.assertIn('/** 中文 (说明)。 */', cleaned)

    def test_13k_four_colon_runs_reported_inside_and_outside_fences(self) -> None:
        text = (
            "::: tip 外层标题\r\n\r\n内容\r\n\r\n::::\r\n\r\n"
            "```java\r\n// 注释 ::::\r\n```\r\n"
        )
        _, issues = check_markdown_format.check_markdown_format_text(text)
        self.assertIn("连续四个及以上冒号=2", "\n".join(issues))

    def test_13l_three_colon_container_passes(self) -> None:
        text = "::: tip 外层标题\r\n\r\n内容\r\n\r\n:::\r\n"
        cleaned, issues = check_markdown_format.check_markdown_format_text(text)
        self.assertEqual(cleaned, text)
        self.assertEqual(issues, [])

    def test_13m_four_colon_apply_collapses_to_container_fence(self) -> None:
        with self.make_root() as directory:
            path = Path(directory) / "sample.md"
            path.write_bytes(
                ":::: tip 外层标题\r\n\r\n内容\r\n\r\n::::\r\n".encode("utf-8")
            )
            applied = check_markdown_format.run([str(path)], apply=True)
            self.assertGreaterEqual(applied["written"], 1)
            checked = check_markdown_format.run([str(path)], apply=False)
            self.assertEqual(checked["problems"], 0)
            self.assertEqual(
                path.read_bytes().decode("utf-8"),
                "::: tip 外层标题\n\n内容\n\n:::\n",
            )

    def test_14_backtick_fence_is_preserved(self) -> None:
        source = "before\n\n```text\nline  \n\n\n```\n\nafter\n"
        cleaned, issues = check_markdown_format.check_markdown_format_text(source)
        self.assertIn("```text\nline  \n\n\n```", cleaned)
        self.assertIn("行尾空白=1", issues)

        mixed = "before\r\n```text\nline  \r\n\n```\r\nafter\r\n"
        mixed_cleaned, mixed_issues = check_markdown_format.check_markdown_format_text(mixed)
        self.assertIn("```text\nline  \r\n\n```\r\n", mixed_cleaned)
        self.assertIn("行尾空白=1", mixed_issues)

    def test_15_tilde_fence_is_converted_to_backticks(self) -> None:
        source = "~~~text\nline  \n\n\n~~~~\n"
        cleaned, issues = check_markdown_format.check_markdown_format_text(source)
        self.assertEqual(cleaned, "```text\nline  \n\n\n```\n")
        self.assertIn("行尾空白=1", issues)
        self.assertIn("波浪号围栏=2", "\n".join(issues))

    def test_15a_long_backtick_fence_is_reported_untouched(self) -> None:
        source = "````text\ncode\n````\n"
        cleaned, issues = check_markdown_format.check_markdown_format_text(source)
        self.assertEqual(cleaned, source)
        self.assertIn("超长反引号围栏=2", "\n".join(issues))

    def test_15b_tilde_lines_inside_backtick_fence_are_content(self) -> None:
        source = "```text\n~~~ kept\n```\n"
        cleaned, issues = check_markdown_format.check_markdown_format_text(source)
        self.assertEqual(cleaned, source)
        self.assertNotIn("波浪号围栏", "\n".join(issues))

    def test_16_unclosed_fence_refuses_cleanup(self) -> None:
        source = "```text\nvalue\n"
        cleaned, issues = check_markdown_format.check_markdown_format_text(source)
        self.assertEqual(cleaned, source)
        self.assertEqual(issues, ["代码围栏未闭合"])

    def test_17_relative_link_validation(self) -> None:
        with self.make_root() as directory:
            root = Path(directory)
            source = root / "README.md"
            (root / "target.md").write_bytes(b"ok\r\n")
            text = (
                "[ok](target.md) [web](https://example.com) [anchor](#x)\n"
                "[missing](missing.md) `[sample](ignored.md)`\n"
            )
            issues = check_markdown_structure.inspect_markdown(source, text)
            self.assertEqual(len(issues), 1)
            self.assertIn("missing.md", issues[0])

    def test_18_hash_is_stable_and_detects_change(self) -> None:
        with self.make_root() as directory:
            root = Path(directory)
            path = root / "a.bin"
            path.write_bytes(b"a")
            first = hash_scope.run([str(root)])
            second = hash_scope.run([str(root)])
            self.assertEqual(first["sha256"], second["sha256"])
            path.write_bytes(b"b")
            third = hash_scope.run([str(root)])
            self.assertNotEqual(first["sha256"], third["sha256"])

    def test_17a_image_contract_requires_alt_title_and_exact_filename(self) -> None:
        issues = check_markdown_structure.inspect_image_contract(
            "![](./ship.png) ![舰船](./ship.png \"other.png\")", 7
        )
        self.assertEqual(len(issues), 4)
        self.assertTrue(any("alt 必须为非空" in issue for issue in issues))
        self.assertTrue(any("title 必须写原始文件名" in issue for issue in issues))
        self.assertTrue(any("title 与目标文件名不一致" in issue for issue in issues))
        self.assertTrue(any("本地图片目标必须写成" in issue for issue in issues))

    def test_19_section_reference_validation(self) -> None:
        with self.make_root() as directory:
            root = Path(directory)
            docs = root / "docs"
            target = docs / "chapter1" / "section1" / "README.md"
            target.parent.mkdir(parents=True)
            target.write_bytes(b"# Target\r\n")
            source = docs / "README.md"
            text = (
                "# 页面\n\n[C1-S1]\n"
                "C1-S1 [C01-S01] [[C1-S1]]\n"
                "[C1-S1](/chapter1/section1/) [C2-S1]\n"
                "`C3-S1` `[C3-S1]`\n"
                "```text\nC4-S1 [C4-S1]\n```\n"
            )
            issues = check_markdown_structure.inspect_markdown(source, text, docs_root=docs)
            self.assertEqual(len(issues), 5)
            self.assertTrue(any("必须使用一层方括号" in issue for issue in issues))
            self.assertTrue(any("严禁前导零" in issue for issue in issues))
            self.assertTrue(any("双层方括号" in issue for issue in issues))
            self.assertTrue(any("严禁手写目标链接" in issue for issue in issues))
            self.assertTrue(any("目标不存在" in issue for issue in issues))

    def test_19a_fault_analysis_requires_solution_and_entry_spacing(self) -> None:
        source = Path("docs/chapter1/section1/README.md")
        valid = (
            "# 页面\r\n\r\n"
            "## 故障分析\r\n\r\n"
            "- **症状**：现象。\r\n"
            "    - **解决方案**：修正。\r\n\r\n"
            "## 总结\r\n"
        )
        self.assertEqual(
            check_markdown_structure.inspect_markdown(source, valid), []
        )

        missing_solution = valid.replace(
            "    - **解决方案**：修正。\r\n\r\n", "下一行不是解决方案。\r\n"
        )
        missing_issues = check_markdown_structure.inspect_markdown(source, missing_solution)
        self.assertTrue(any("必须紧跟以解决方案为开头" in issue for issue in missing_issues))

        loose_entry = valid.replace(
            "- **症状**：现象。\r\n    -", "- **症状**：现象。\r\n\r\n    -"
        )
        loose_issues = check_markdown_structure.inspect_markdown(source, loose_entry)
        self.assertTrue(any("解决方案必须紧跟问题行" in issue for issue in loose_issues))

        compact_entries = (
            "# 页面\r\n\r\n"
            "## 故障分析\r\n\r\n"
            "- **症状**：现象。\r\n"
            "    - **解决方案**：修正。\r\n"
            "- **第二症状**：现象。\r\n"
            "    - **解决方案**：修正。\r\n\r\n"
            "## 总结\r\n"
        )
        compact_issues = check_markdown_structure.inspect_markdown(source, compact_entries)
        self.assertTrue(any("条目之间至少需要 1 空行" in issue for issue in compact_issues))

        trailing_missing = valid.replace("修正。\r\n\r\n## 总结", "修正。\r\n## 总结")
        trailing_issues = check_markdown_structure.inspect_markdown(source, trailing_missing)
        self.assertTrue(any("条目之间至少需要 1 空行" in issue for issue in trailing_issues))

        same_level_solution = valid.replace(
            "    - **解决方案**：修正。", "- **解决方案**：修正。"
        )
        same_level_issues = check_markdown_structure.inspect_markdown(source, same_level_solution)
        self.assertTrue(any("解决方案必须嵌套" in issue for issue in same_level_issues))

        paragraph_fault = (
            "# 页面\r\n\r\n"
            "## 故障分析\r\n\r\n"
            "**症状**：现象。\r\n\r\n"
            "**解决方案**：修正。\r\n\r\n"
            "## 总结\r\n"
        )
        paragraph_issues = check_markdown_structure.inspect_markdown(source, paragraph_fault)
        self.assertTrue(any("必须使用列表格式" in issue for issue in paragraph_issues))

    def test_19b_validation_contract_requires_ordered_steps_and_compact_results(self) -> None:
        source = Path("docs/chapter1/section1/README.md")
        valid = (
            "# 页面\r\n\r\n"
            "## 验证方案\r\n\r\n"
            "前置数据：已有测试存档。\r\n\r\n"
            "1. 启动游戏。\r\n"
            "2. 查看日志。\r\n\r\n"
            "预期结果：\r\n\r\n"
            "- 日志出现目标行。\r\n"
            "- 界面显示目标状态。\r\n\r\n"
            "## 故障分析\r\n"
        )
        self.assertEqual(check_markdown_structure.inspect_markdown(source, valid), [])

        missing_prerequisite = valid.replace("前置数据：已有测试存档。\r\n\r\n", "")
        self.assertEqual(check_markdown_structure.inspect_markdown(source, missing_prerequisite), [])

        missing_steps = valid.replace("1. 启动游戏。\r\n2. 查看日志。\r\n\r\n", "")
        self.assertTrue(any("缺少数字步骤列表" in issue for issue in check_markdown_structure.inspect_markdown(source, missing_steps)))

        missing_label = valid.replace("预期结果：\r\n\r\n", "")
        self.assertTrue(any("缺少预期结果标签" in issue for issue in check_markdown_structure.inspect_markdown(source, missing_label)))

        missing_results = valid.replace("- 日志出现目标行。\r\n- 界面显示目标状态。\r\n", "")
        self.assertTrue(any("缺少预期结果列表" in issue for issue in check_markdown_structure.inspect_markdown(source, missing_results)))

        loose_steps = valid.replace("1. 启动游戏。\r\n2. 查看日志。", "1. 启动游戏。\r\n\r\n2. 查看日志。")
        self.assertTrue(any("数字步骤必须是紧凑列表" in issue for issue in check_markdown_structure.inspect_markdown(source, loose_steps)))

        loose_results = valid.replace("- 日志出现目标行。\r\n- 界面显示目标状态。", "- 日志出现目标行。\r\n\r\n- 界面显示目标状态。")
        self.assertTrue(any("预期结果必须是紧凑列表" in issue for issue in check_markdown_structure.inspect_markdown(source, loose_results)))

        reordered = valid.replace("前置数据：已有测试存档。\r\n\r\n", "预期结果：\r\n\r\n- 日志出现目标行。\r\n\r\n前置数据：已有测试存档。\r\n\r\n")
        self.assertTrue(any("结构顺序错误" in issue for issue in check_markdown_structure.inspect_markdown(source, reordered)))

    def test_19c_validation_contract_is_optional_for_concept_pages(self) -> None:
        source = Path("docs/chapter1/section1/README.md")
        text = "# 概念页\r\n\r\n## 总结\r\n\r\n只讲概念。\r\n"
        self.assertEqual(check_markdown_structure.inspect_markdown(source, text), [])

    def test_19d_fault_formatter_repairs_entry_boundaries(self) -> None:
        source = (
            "## 故障分析\r\n"
            "- **症状甲**：原因。\r\n"
            "\r\n"
            "    - **解决方案**：做法。\r\n"
            "- **症状乙**：原因。\r\n"
            "    - **解决方案**：做法。\r\n"
            "## 总结\r\n"
        )
        repaired, inserted, removed = format_fault_analysis.repair_fault_spacing(source)
        self.assertEqual(
            repaired,
            "## 故障分析\r\n"
            "- **症状甲**：原因。\r\n"
            "    - **解决方案**：做法。\r\n"
            "\r\n"
            "- **症状乙**：原因。\r\n"
            "    - **解决方案**：做法。\r\n"
            "\r\n"
            "## 总结\r\n",
        )
        self.assertEqual((inserted, removed), (2, 1))
        issues = check_markdown_structure.inspect_markdown(
            Path("docs/chapter1/section1/README.md"), "# 页面\n\n" + repaired
        )
        self.assertEqual(issues, [])

    def test_20_maintenance_apply_and_recheck(self) -> None:
        with self.make_root() as directory:
            path = Path(directory) / "a.md"
            path.write_bytes(b"\n# H  \n\n\ntext\n")
            applied = text_maintenance.run([str(path)], apply=True)
            checked = text_maintenance.run([str(path)], apply=False)
            self.assertGreaterEqual(applied["written"], 1)
            self.assertEqual(checked["problems"], 0)
            self.assertEqual(path.read_bytes(), b"# H\n\ntext\n")

    def test_21_public_scripts_show_help(self) -> None:
        scripts = (
            "format_fault_analysis.py",
            "check_fixtures.py",
            "check_text_format.py",
            "check_markdown_structure.py",
            "check_standards.py",
            "check_markdown_format.py",
            "hash_scope.py",
            "restore_markdown.py",
            "text_maintenance.py",
        )
        for script in scripts:
            completed = subprocess.run(
                [sys.executable, "-X", "utf8", "-B", str(TOOLS_DIR / script), "--help"],
                capture_output=True,
                check=False,
                text=True,
                encoding="utf-8",
            )
            self.assertEqual(completed.returncode, 0, msg=f"{script}: {completed.stderr}")
            self.assertTrue(completed.stdout.strip(), msg=script)

    def test_22_fixture_contract_passes(self) -> None:
        with self.make_root() as directory:
            root = Path(directory)
            code = "package example;\n\npublic class Sample {\n}"
            self.make_fixture_repo(root, page_code=code, fixture_code=code)
            summary = check_fixtures.run(root)
            self.assertEqual(summary["problems"], 0)
            self.assertEqual(summary["matched_java_blocks"], 1)

    def test_23_orphan_fixture_is_reported(self) -> None:
        with self.make_root() as directory:
            root = Path(directory)
            code = "package example;\n\npublic class Sample {\n}"
            self.make_fixture_repo(root, page_code=code, fixture_code=code)
            orphan = root / "tutorial-fixtures" / "units" / "C2-S1"
            orphan.mkdir()
            summary = check_fixtures.run(root)
            self.assertTrue(any(item["code"] == "orphan-fixture" for item in summary["issues"]))

    def test_24_complete_java_requires_fixture_and_list(self) -> None:
        with self.make_root() as directory:
            root = Path(directory)
            code = "package example;\n\npublic class Sample {\n}"
            self.make_fixture_repo(root, page_code=code, java_ids="")
            summary = check_fixtures.run(root)
            codes = {item["code"] for item in summary["issues"]}
            self.assertIn("page-java-not-listed", codes)
            self.assertIn("missing-fixture-java", codes)

    def test_25_fixture_java_requires_page_code(self) -> None:
        with self.make_root() as directory:
            root = Path(directory)
            code = "package example;\n\npublic class Sample {\n}"
            self.make_fixture_repo(root, fixture_code=code)
            summary = check_fixtures.run(root)
            self.assertTrue(any(item["code"] == "fixture-java-without-page-code" for item in summary["issues"]))

    def test_26_build_list_duplicate_missing_and_extra(self) -> None:
        with self.make_root() as directory:
            root = Path(directory)
            code = "package example;\n\npublic class Sample {\n}"
            self.make_fixture_repo(
                root,
                page_code=code,
                fixture_code=code,
                java_ids='"C1-S1", "C1-S1", "C2-S1"',
            )
            summary = check_fixtures.run(root)
            codes = {item["code"] for item in summary["issues"]}
            self.assertIn("duplicate-unit-id", codes)
            self.assertIn("listed-unit-missing-directory", codes)

    def test_27_java_content_difference_is_reported(self) -> None:
        with self.make_root() as directory:
            root = Path(directory)
            page_code = "package example;\n\npublic class Sample {\n}"
            fixture_code = "package example;\n\npublic class Sample {\n    int value;\n}"
            self.make_fixture_repo(root, page_code=page_code, fixture_code=fixture_code)
            summary = check_fixtures.run(root)
            self.assertTrue(any(item["code"] == "java-content-mismatch" for item in summary["issues"]))

    def test_28_shader_resource_matches_and_local_fragment_is_exempt(self) -> None:
        with self.make_root() as directory:
            root = Path(directory)
            code = "package example;\n\npublic class Sample {\n}"
            shader = "#version 110\nvoid main() {\n}"
            shader_page = (
                f"```glsl\n{shader}\n```\n"
                "```glsl\nvec4 localValue = vec4(1.0);\n```\n"
            )
            self.make_fixture_repo(
                root,
                page_code=code,
                fixture_code=code,
                shader_ids='"C1-S1"',
                shader_page=shader_page,
                shader_resources={"sample.vert": shader},
            )
            summary = check_fixtures.run(root)
            self.assertEqual(summary["problems"], 0)
            self.assertEqual(summary["matched_shader_blocks"], 1)

    def test_29_shader_difference_is_reported(self) -> None:
        with self.make_root() as directory:
            root = Path(directory)
            code = "package example;\n\npublic class Sample {\n}"
            shader_page = "```glsl\n#version 110\nvoid main() {\n}\n```\n"
            self.make_fixture_repo(
                root,
                page_code=code,
                fixture_code=code,
                shader_ids='"C1-S1"',
                shader_page=shader_page,
                shader_resources={"sample.vert": "#version 110\nvoid main() { gl_Position = vec4(0.0); }"},
            )
            summary = check_fixtures.run(root)
            codes = {item["code"] for item in summary["issues"]}
            self.assertIn("missing-or-mismatched-shader", codes)
            self.assertIn("fixture-shader-without-page-code", codes)

    def test_30_invalid_unit_id_and_utf8_are_reported(self) -> None:
        with self.make_root() as directory:
            root = Path(directory)
            code = "package example;\n\npublic class Sample {\n}"
            self.make_fixture_repo(root, page_code=code, fixture_code=code, java_ids='"C01-S1"')
            summary = check_fixtures.run(root)
            self.assertTrue(any(item["code"] == "invalid-unit-id" for item in summary["issues"]))
            page = root / "docs" / "chapter1" / "section1" / "README.md"
            page.write_bytes(b"\xff")
            invalid = check_fixtures.run(root)
            self.assertEqual(len(invalid["errors"]), 1)

    def test_31_fixture_json_cli(self) -> None:
        with self.make_root() as directory:
            root = Path(directory)
            code = "package example;\n\npublic class Sample {\n}"
            self.make_fixture_repo(root, page_code=code, fixture_code=code)
            completed = subprocess.run(
                [
                    sys.executable,
                    "-X",
                    "utf8",
                    "-B",
                    str(TOOLS_DIR / "check_fixtures.py"),
                    "--repo-root",
                    str(root),
                    "--json",
                    "--verbose",
                ],
                capture_output=True,
                check=False,
                text=True,
                encoding="utf-8",
            )
            self.assertEqual(completed.returncode, 0, msg=completed.stderr)
            payload = __import__("json").loads(completed.stdout)
            self.assertEqual(payload["status"], "pass")


    def test_32_markup_bullet_and_ordered_spacing(self) -> None:
        text = "*   item\r\n1.  item\r\n2.  second\r\n"
        fixed, issues = check_markdown_format.check_markdown_format_text(text)
        self.assertEqual(fixed, "- item\r\n1. item\r\n2. second\r\n")
        self.assertTrue(any("bullet 项目符号" in issue for issue in issues))
        self.assertTrue(any("bullet 多空格" in issue for issue in issues))
        self.assertTrue(any("有序列表多空格" in issue for issue in issues))

    def test_33_markup_underscore_emphasis_converted(self) -> None:
        text = "_本节预期阅读时间：~45 分钟。_\r\n引文 \"Does _not_ return 0\"\r\n"
        fixed, issues = check_markdown_format.check_markdown_format_text(text)
        self.assertEqual(fixed, "*本节预期阅读时间：~45 分钟。*\r\n引文 \"Does *not* return 0\"\r\n")
        self.assertTrue(any("_斜体=2" in issue for issue in issues))

    def test_34_markup_identifiers_escapes_and_code_preserved(self) -> None:
        text = (
            "标识符 GearIn_gunnery 与 SHIP_FIGHTERS 不动\r\n"
            "转义 \\_x_ 不动\r\n"
            "粗体 __bold__ 不动\r\n"
            "行内代码 `_keep_` 与 `gear_industry` 不动\r\n"
        )
        fixed, issues = check_markdown_format.check_markdown_format_text(text)
        self.assertEqual(fixed, text)
        self.assertEqual(issues, [])

    def test_35_markup_fence_and_indented_block_preserved(self) -> None:
        text = "```java\r\n*   @param keep\r\n```\r\n    *   keep\r\n"
        fixed, issues = check_markdown_format.check_markdown_format_text(text)
        self.assertEqual(fixed, text)
        self.assertEqual(issues, [])

    def test_35a_markup_quote_and_nested_bullets_convert(self) -> None:
        text = "> * item\r\n- parent\r\n    * child\r\n"
        fixed, issues = check_markdown_format.check_markdown_format_text(text)
        self.assertEqual(fixed, "> - item\r\n- parent\r\n    - child\r\n")
        self.assertTrue(any("bullet 项目符号=2" in issue for issue in issues))

    def test_35b_markup_horizontal_rule_normalized_and_inline_code_preserved(self) -> None:
        text = "* * *\r\n文本含 `* code` 与 *强调*。\r\n"
        fixed, issues = check_markdown_format.check_markdown_format_text(text)
        self.assertEqual(fixed, "---\r\n文本含 `* code` 与 *强调*。\r\n")
        self.assertTrue(any("水平线写法=1" in issue for issue in issues))

    def test_36_markup_unclosed_fence_refuses(self) -> None:
        text = "*   item\r\n```java\r\ncode\r\n"
        fixed, issues = check_markdown_format.check_markdown_format_text(text)
        self.assertEqual(fixed, text)
        self.assertEqual(issues, ["代码围栏未闭合"])

    def test_37_markup_apply_writes_and_recheck(self) -> None:
        with self.make_root() as directory:
            path = Path(directory) / "a.md"
            path.write_bytes("*   item\r\n1.  item\r\n_模板句。_\r\n".encode("utf-8"))
            applied = check_markdown_format.run([str(path)], apply=True)
            checked = check_markdown_format.run([str(path)], apply=False)
            self.assertGreaterEqual(applied["written"], 1)
            self.assertEqual(checked["problems"], 0)
            self.assertEqual(path.read_bytes(), "- item\n1. item\n*模板句。*\n".encode("utf-8"))


    def test_38_restore_basic_blocks(self) -> None:
        html_text = (
            "<main class=\"vp-page\"><div id=\"content\">"
            "<h1 id=\"t\"><a class=\"header-anchor\" href=\"#t\"><span>标题</span></a></h1>"
            "<p>段落含 <code>mod_info.json</code> 与 <em>斜体</em> 与 <strong>粗体</strong>。</p>"
            "<pre><code class=\"language-text\"><span class=\"line\">行一</span>\n"
            "<span class=\"line\"><span class=\"token\">行</span>二</span></code></pre>"
            "<table><thead><tr><th style=\"text-align:right;\">右</th><th>普通</th></tr></thead>"
            "<tbody><tr><td><code>a</code></td><td>b</td></tr></tbody></table>"
            "<ul><li>甲</li><li>乙</li></ul>"
            "<ol><li>一</li><li>二</li></ol>"
            "</div></main>"
        )
        restored = restore_markdown.restore_markdown_text(html_text)
        self.assertEqual(
            restored,
            "# 标题\n\n"
            "段落含 `mod_info.json` 与 *斜体* 与 **粗体**。\n\n"
            "```text\n行一\n行二\n```\n\n"
            "| 右 | 普通 |\n"
            "|---:|---|\n"
            "| `a` | b |\n\n"
            "- 甲\n- 乙\n\n"
            "1. 一\n2. 二\n",
        )

    def test_39_restore_containers_links_images(self) -> None:
        html_text = (
            "<main><div id=\"content\"><div class=\"hint-container caution\">"
            "<p class=\"hint-container-title\">必须 <code>id</code> 一致</p>"
            "<p>正文 <a class=\"route-link\" href=\"/StarsectorNihilismGuidance/chapter6/section2/\">[C6-S2]</a></p>"
            "</div>"
            "<details class=\"hint-container details\"><summary>完整成品：GearInX</summary>"
            "<p>内容</p></details>"
            "<p><img src=\"/StarsectorNihilismGuidance/assets/ship_making-DlQYjdBE.png\" "
            "alt=\"草图\" title=\"ship_making.png\"></p>"
            "<hr></div></main>"
        )
        restored = restore_markdown.restore_markdown_text(html_text)
        self.assertEqual(
            restored,
            "::: caution 必须 `id` 一致\n\n"
            "正文 [C6-S2]\n\n"
            ":::\n\n"
            "::: details 完整成品：GearInX\n\n"
            "内容\n\n"
            ":::\n\n"
            "![草图](./ship_making.png \"ship_making.png\")\n\n"
            "---\n",
        )

    def test_40_restore_fixed_dash_bullet(self) -> None:
        html_text = "<main><div id=\"content\"><ul><li>甲</li><li>乙</li></ul></div></main>"
        self.assertEqual(
            restore_markdown.restore_markdown_text(html_text),
            "- 甲\n- 乙\n",
        )

    def test_41_restore_run_dry_and_out(self) -> None:
        with self.make_root() as directory:
            root = Path(directory)
            dist = root / "docs/.vuepress/dist/chapter1/section1"
            dist.mkdir(parents=True)
            (root / "docs/.vuepress/dist/assets").mkdir(parents=True)
            (dist / "index.html").write_text(
                "<main><div id=\"content\"><h1>标题</h1><p>正文</p></div></main>",
                encoding="utf-8",
            )
            dry = restore_markdown.run(str(dist / "index.html"), verbose=True)
            self.assertEqual(dry["problem_files"], 0)
            out = root / "restored"
            written = restore_markdown.run(str(dist / "index.html"), out=str(out))
            self.assertEqual(written["written"], 1)
            restored = (out / "README.md").read_bytes()
            self.assertEqual(restored, "# 标题\n\n正文\n".encode("utf-8"))

    def test_41a_restore_highlights_and_code_entities(self) -> None:
        html_text = (
            "<main><div id=\"content\"><pre><code class=\"language-java\">"
            "<span class=\"line\">if (a &amp;gt; b) {</span>\n"
            "<span class=\"line highlighted\">    return;</span>\n"
            "<span class=\"line highlighted\"></span>"
            "</code></pre></div></main>"
        )
        self.assertEqual(
            restore_markdown.restore_markdown_text(html_text),
            "```java {2,3}\nif (a &gt; b) {\n    return;\n```\n",
        )

    def test_41aa_restore_preserves_compact_and_loose_lists(self) -> None:
        compact = (
            '<main><div id="content"><ul data-dev-list-tight="true">'
            '<li>A</li><li>B</li></ul></div></main>'
        )
        loose = (
            '<main><div id="content"><ul data-dev-list-tight="false">'
            '<li><p>A</p></li><li><p>B</p></li></ul></div></main>'
        )
        fallback = (
            '<main><div id="content"><ul>'
            '<li><p>A</p></li><li><p>B</p></li></ul></div></main>'
        )
        self.assertEqual(
            restore_markdown.restore_markdown_text(compact),
            "- A\n- B\n",
        )
        self.assertEqual(
            restore_markdown.restore_markdown_text(loose),
            "- A\n\n- B\n",
        )
        self.assertEqual(
            restore_markdown.restore_markdown_text(fallback),
            "- A\n\n- B\n",
        )

    def test_41ab_restore_preserves_loose_nested_list_boundary(self) -> None:
        html_text = (
            '<main><div id="content"><ul data-dev-list-tight="false">'
            '<li><p>问题 A</p><ul data-dev-list-tight="true">'
            '<li>解决方案 A</li></ul></li>'
            '<li><p>问题 B</p><ul data-dev-list-tight="true">'
            '<li>解决方案 B</li></ul></li>'
            '</ul></div></main>'
        )
        self.assertEqual(
            restore_markdown.restore_markdown_text(html_text),
            "- 问题 A\n\n    - 解决方案 A\n\n"
            "- 问题 B\n\n    - 解决方案 B\n",
        )

    def test_41b_restore_auto_section_list(self) -> None:
        html_text = (
            "<main><div id=\"content\"><h1>章节</h1><h2>本章各节</h2>"
            "<ul><li><a class=\"route-link\" href=\"/chapter1/section1/\">[C1-S1]</a></li></ul>"
            "</div></main>"
        )
        self.assertEqual(
            restore_markdown.restore_markdown_text(html_text),
            "# 章节\n\n## 本章各节\n\n<!-- AUTO:SECTION-LIST -->\n",
        )

    def test_41c_restore_data_image_to_hash_file(self) -> None:
        payload = base64.b64encode(b"png-data").decode("ascii")
        html_text = (
            "<main><div id=\"content\"><p><img alt=\"图\" title=\"source.png\" "
            "src=\"data:image/png;base64,"
            + payload
            + "\"></p></div></main>"
        )
        restored, assets = restore_markdown.restore_markdown_document(html_text)
        self.assertEqual(restored, "![图](./source.png \"source.png\")\n")
        self.assertEqual(assets, {"source.png": b"png-data"})

    def test_41d_restore_rejects_directory_input(self) -> None:
        with self.make_root() as directory:
            result = restore_markdown.run(directory)
            self.assertEqual(result["scanned"], 0)
            self.assertEqual(len(result["errors"]), 1)

    def test_41e_restore_copies_hashed_image_beside_markdown(self) -> None:
        with self.make_root() as directory:
            root = Path(directory)
            page = root / "dist/chapter1/section1/index.html"
            asset = root / "dist/assets/ship-DlQYjdBE.png"
            page.parent.mkdir(parents=True)
            asset.parent.mkdir(parents=True)
            page.write_text(
                "<main><div id=\"content\"><p><img alt=\"舰船\" title=\"ship.png\" "
                "src=\"/guide/assets/ship-DlQYjdBE.png\"></p></div></main>",
                encoding="utf-8",
            )
            asset.write_bytes(b"image-data")
            out = root / "restored"
            result = restore_markdown.run(str(page), out=str(out))
            self.assertEqual(result["written"], 2)
            self.assertEqual(
                (out / "README.md").read_bytes(),
                "![舰船](./ship.png \"ship.png\")\n".encode("utf-8"),
            )
            self.assertEqual((out / "ship.png").read_bytes(), b"image-data")

    def test_41f_restore_escapes_literal_inline_markdown(self) -> None:
        html_text = (
            "<main><div id=\"content\"><p>literal *star* _under_ [bracket] "
            "&lt;tag&gt; `tick` \\path &amp;copy;</p></div></main>"
        )
        self.assertEqual(
            restore_markdown.restore_markdown_text(html_text),
            "literal \\*star\\* \\_under\\_ \\[bracket\\] \\<tag> "
            "\\`tick\\` \\\\path \\&copy;\n",
        )

    def test_41g_restore_escapes_block_markers_in_text(self) -> None:
        html_text = (
            "<main><div id=\"content\"><p># heading\r\n- item\r\n1. item\r\n&gt; quote\r\n"
            "---\r\n~~~\r\n===</p></div></main>"
        )
        self.assertEqual(
            restore_markdown.restore_markdown_text(html_text),
            "\\# heading\n\\- item\n1\\. item\n\\> quote\n"
            "\\---\n\\~\\~\\~\n\\===\n",
        )

    def test_41h_restore_preserves_generated_inline_markup(self) -> None:
        html_text = (
            "<main><div id=\"content\"><p><em>a*b</em> <strong>c_d</strong> "
            "<code>[raw]</code> <a href=\"https://example.com/a_(b)\">[link]</a>"
            "</p></div></main>"
        )
        self.assertEqual(
            restore_markdown.restore_markdown_text(html_text),
            "*a\\*b* **c\\_d** `[raw]` "
            "[\\[link\\]](<https://example.com/a_(b)>)\n",
        )

    def test_41i_restore_escapes_image_alt_and_table_pipe(self) -> None:
        html_text = (
            "<main><div id=\"content\"><p><img alt=\"a[b]*c\" title=\"a.png\" "
            "src=\"https://example.com/a.png\"></p>"
            "<table><thead><tr><th>A|B</th><th>Code</th></tr></thead>"
            "<tbody><tr><td>x|y</td><td><code>a|b</code></td></tr></tbody></table>"
            "</div></main>"
        )
        self.assertEqual(
            restore_markdown.restore_markdown_text(html_text),
            "![a\\[b\\]\\*c](https://example.com/a.png \"a.png\")\n\n"
            "| A\\|B | Code |\n|---|---|\n| x\\|y | `a\\|b` |\n",
        )

    def test_41j_restore_escapes_heading_tail_and_extension_markers(self) -> None:
        html_text = (
            "<main><div id=\"content\"><h2>Title #</h2>"
            "<p>~~literal~~</p><p>::: warning</p><p>| cell |</p></div></main>"
        )
        self.assertEqual(
            restore_markdown.restore_markdown_text(html_text),
            "## Title \\#\n\n\\~\\~literal\\~\\~\n\n"
            "\\::: warning\n\n\\| cell |\n",
        )

    def test_41k_restore_normalizes_output_to_markdown_contract(self) -> None:
        html_text = (
            "<main><div id=\"content\"><p>中文 ，标点。 <br>下一行</p>"
            "<p>模拟战(包括生涯内模拟战)返回。</p></div></main>"
        )
        restored = restore_markdown.restore_markdown_text(html_text)
        self.assertEqual(
            restored,
            "中文，标点。<br>\n下一行\n\n"
            "模拟战 (包括生涯内模拟战) 返回。\n",
        )
        normalized, issues = check_markdown_format.check_markdown_format_text(restored)
        self.assertEqual(normalized, restored)
        self.assertEqual(issues, [])

    def test_41l_restore_rejects_unfixable_markdown_violation(self) -> None:
        html_text = "<main><div id=\"content\"><p>正文 — 说明。</p></div></main>"
        with self.assertRaisesRegex(restore_markdown.RestoreError, "单破折号"):
            restore_markdown.restore_markdown_text(html_text)

    def test_41m_restore_rejects_missing_or_mismatched_image_title(self) -> None:
        missing = "<main><div id=\"content\"><p><img alt=\"图\" src=\"/assets/ship.png\"></p></div></main>"
        with self.assertRaisesRegex(restore_markdown.RestoreError, "title 缺少"):
            restore_markdown.restore_markdown_text(missing)

        mismatched = (
            "<main><div id=\"content\"><p><img alt=\"图\" title=\"other.png\" "
            "src=\"/assets/ship-AbCd1234.png\"></p></div></main>"
        )
        with self.assertRaisesRegex(restore_markdown.RestoreError, "构建产物文件名不一致"):
            restore_markdown.restore_markdown_text(mismatched)

    def test_41n_restore_rejects_data_image_extension_mismatch(self) -> None:
        payload = base64.b64encode(b"png-data").decode("ascii")
        html_text = (
            "<main><div id=\"content\"><p><img alt=\"图\" title=\"source.jpg\" "
            f"src=\"data:image/png;base64,{payload}\"></p></div></main>"
        )
        with self.assertRaisesRegex(restore_markdown.RestoreError, "扩展名与 data URI 类型不一致"):
            restore_markdown.restore_markdown_text(html_text)

    def test_41o_restore_tiptap_code_language_and_highlights(self) -> None:
        html_text = (
            '<main><div id="content"><pre data-language="json" '
            'data-highlight-lines="2,4"><code class="language-json">'
            '{\n  "a": 1,\n  "b": 2\n}</code></pre></div></main>'
        )
        self.assertEqual(
            restore_markdown.restore_markdown_text(html_text),
            '```json {2,4}\n{\n  "a": 1,\n  "b": 2\n}\n```\n',
        )

    def test_41p_restore_tiptap_container_and_strike(self) -> None:
        html_text = (
            '<main><div id="content"><div data-vp-container="warning" '
            'data-vp-title="精确标题"><p>保留 <s>删除线</s>。</p></div></div></main>'
        )
        self.assertEqual(
            restore_markdown.restore_markdown_text(html_text),
            '::: warning 精确标题\n\n保留 ~~删除线~~。\n\n:::\n',
        )

    def test_41q_restore_tiptap_auto_section_list(self) -> None:
        html_text = (
            '<main><div id="content"><h1>章节</h1><h2>本章各节</h2>'
            '<div data-auto-section-list="true"></div></div></main>'
        )
        self.assertEqual(
            restore_markdown.restore_markdown_text(html_text),
            '# 章节\n\n## 本章各节\n\n<!-- AUTO:SECTION-LIST -->\n',
        )

    def test_41r_restore_stdin_payload_validates_document(self) -> None:
        with self.make_root() as directory:
            docs = Path(directory) / "docs"
            source = docs / "README.md"
            docs.mkdir()
            source.write_bytes(b"# Old\r\n")
            result = restore_markdown.restore_stdin_payload(
                {
                    "html": '<main><div id="content"><h1>新标题</h1><p>正文</p></div></main>',
                    "path": str(source),
                    "docsRoot": str(docs),
                }
            )
            self.assertTrue(result["ok"])
            self.assertEqual(result["markdown"], "# 新标题\n\n正文\n")
            self.assertEqual(result["assets"], [])

    def test_41s_restore_stdin_payload_returns_diagnostics(self) -> None:
        result = restore_markdown.restore_stdin_payload({"html": "", "path": "x", "docsRoot": "y"})
        self.assertFalse(result["ok"])
        self.assertEqual(result["diagnostics"], ["HTML 正文为空"])

    def test_42_speed_table_contract_accepts_owned_table(self) -> None:
        with self.make_root() as directory:
            docs = Path(directory) / "docs"
            source = docs / "chapter1" / "section1" / "README.md"
            source.parent.mkdir(parents=True)
            text = (
                "# 页面\r\n\r\n"
                "## 武器类型速查表\r\n\r\n"
                "按用途选择武器类型。\r\n\r\n"
                "| 类型 | 用途 |\r\n"
                "|---|---|\r\n"
                "| 弹道 | 持续输出 |\r\n"
                "| 能量 | 灵活配装 |\r\n"
            )
            self.assertEqual(
                check_markdown_structure.inspect_table_contract(source, text, docs_root=docs),
                [],
            )

    def test_43_speed_table_contract_reports_structure_and_owner(self) -> None:
        with self.make_root() as directory:
            docs = Path(directory) / "docs"
            source = docs / "chapter1" / "section1" / "README.md"
            source.parent.mkdir(parents=True)
            text = (
                "# 页面\r\n\r\n"
                "## 类型比较\r\n\r\n"
                "| 类型 | 用途 |\r\n"
                "|---|---|\r\n"
                "| 弹道 | 持续输出 |\r\n"
            )
            issues = check_markdown_structure.inspect_table_contract(source, text, docs_root=docs)
            self.assertTrue(any("两个数据行" in issue for issue in issues))
            self.assertTrue(any("必须由包含“速查表”" in issue for issue in issues))

    def test_44_speed_table_contract_reports_shared_heading(self) -> None:
        with self.make_root() as directory:
            docs = Path(directory) / "docs"
            source = docs / "chapter1" / "section1" / "README.md"
            source.parent.mkdir(parents=True)
            table = "| 类型 | 用途 |\r\n|---|---|\r\n| A | B |\r\n| C | D |\r\n"
            text = "# 页面\r\n\r\n## 类型速查表\r\n\r\n" + table + "\r\n" + table
            issues = check_markdown_structure.inspect_table_contract(source, text, docs_root=docs)
            self.assertTrue(any("只能管理一张表" in issue for issue in issues))

    def test_45_speed_table_damage_probes(self) -> None:
        with self.make_root() as directory:
            docs = Path(directory) / "docs"
            source = docs / "chapter1" / "section1" / "README.md"
            source.parent.mkdir(parents=True)
            text = (
                "# 页面\r\n\r\n"
                "## 类型比较\r\n\r\n"
                "下面整理成一张表：\r\n\r\n"
                "类型\r\n\r\n用途\r\n\r\nA\r\n\r\nB\r\n\r\nC\r\n\r\nD\r\n\r\n"
                "—\r\n"
            )
            issues = check_markdown_structure.inspect_table_contract(source, text, docs_root=docs)
            self.assertTrue(any("连续独立单元格" in issue for issue in issues))
            self.assertTrue(any("独立占位符" in issue for issue in issues))

    def test_46_speed_table_ignores_fenced_pipes(self) -> None:
        with self.make_root() as directory:
            docs = Path(directory) / "docs"
            source = docs / "chapter1" / "section1" / "README.md"
            source.parent.mkdir(parents=True)
            text = "# 页面\r\n\r\n```text\r\n| A | B |\r\n|---|---|\r\n```\r\n"
            self.assertEqual(
                check_markdown_structure.inspect_table_contract(source, text, docs_root=docs),
                [],
            )

    def test_47_tutorial_checker_rejects_non_docs_scope(self) -> None:
        with self.make_root() as directory:
            root = Path(directory)
            standards = root / ".zcode" / "standards"
            standards.mkdir(parents=True)
            source = standards / "sample.md"
            source.write_bytes(b"# Sample\r\n")
            summary = check_markdown_structure.run([str(source)], docs_root=root / "docs")
            self.assertEqual(summary["scanned"], 0)
            self.assertEqual(summary["problems"], 0)
            self.assertEqual(len(summary["errors"]), 1)
            self.assertIn("只检查 docs 目录", summary["errors"][0])

    def make_standard_set(self, root: Path) -> tuple[Path, Path]:
        standards = root / ".zcode" / "standards"
        standards.mkdir(parents=True)
        for name in check_standards.STANDARD_FILES:
            (standards / name).write_text(
                f"# {name}\n\n**Owner：**测试职责\n\n**适用范围：**测试范围\n\n## 当前规则\n\n- 必须按任务使用。\n",
                encoding="utf-8",
                newline="\n",
            )
        validation = root / ".zcode" / "validation.md"
        routes = "\n".join(f"- [{name}](./standards/{name})" for name in check_standards.STANDARD_FILES)
        validation.write_text(f"# 核验入口\n\n{routes}\n", encoding="utf-8", newline="\n")
        return standards, validation

    def test_48_standards_checker_accepts_current_manifest(self) -> None:
        with self.make_root() as directory:
            root = Path(directory)
            standards, validation = self.make_standard_set(root)
            summary = check_standards.run([str(standards), str(validation)], repo_root=root)
            self.assertEqual(summary["problems"], 0)
            self.assertEqual(summary["errors"], [])
            self.assertEqual(summary["scanned"], 4)
            self.assertLessEqual(summary["characters"], summary["character_limit"])

    def test_49_standards_checker_rejects_tutorial_scope(self) -> None:
        with self.make_root() as directory:
            root = Path(directory)
            source = root / "docs" / "README.md"
            source.parent.mkdir()
            source.write_bytes(b"# Tutorial\n")
            summary = check_standards.run([str(source)], repo_root=root)
            self.assertEqual(summary["scanned"], 0)
            self.assertEqual(len(summary["errors"]), 1)
            self.assertIn("只检查 .zcode/standards", summary["errors"][0])

    def test_50_standards_checker_reports_missing_and_extra_documents(self) -> None:
        with self.make_root() as directory:
            root = Path(directory)
            standards, validation = self.make_standard_set(root)
            (standards / "technical.md").unlink()
            (standards / "extra.md").write_text(
                "# 额外文件\n\n**Owner：**额外职责\n\n**适用范围：**测试\n",
                encoding="utf-8",
            )
            summary = check_standards.run([str(standards), str(validation)], repo_root=root)
            issues = " ".join(issue for item in summary["files"] for issue in item["issues"])
            self.assertIn("标准文件缺失", issues)
            self.assertIn("不在三份标准清单中", issues)

    def test_51_standards_checker_limits_unicode_characters(self) -> None:
        with self.make_root() as directory:
            root = Path(directory)
            standards, validation = self.make_standard_set(root)
            source = standards / "writing.md"
            with source.open("a", encoding="utf-8") as stream:
                stream.write("字" * check_standards.MAX_CHARACTERS)
            summary = check_standards.run([str(standards), str(validation)], repo_root=root)
            self.assertGreater(summary["characters"], check_standards.MAX_CHARACTERS)
            self.assertTrue(any("Unicode 字符" in issue for item in summary["files"] for issue in item["issues"]))

    def test_52_standards_checker_reports_declarations_links_and_routes(self) -> None:
        with self.make_root() as directory:
            root = Path(directory)
            standards, validation = self.make_standard_set(root)
            (standards / "writing.md").write_text("# Writing\n\n[missing](missing.md)\n", encoding="utf-8")
            validation.write_text("# 核验入口\n", encoding="utf-8")
            summary = check_standards.run([str(standards), str(validation)], repo_root=root)
            issues = " ".join(issue for item in summary["files"] for issue in item["issues"])
            self.assertIn("Owner 声明", issues)
            self.assertIn("适用范围声明", issues)
            self.assertIn("相对目标不存在", issues)
            self.assertIn("缺少 Owner 路由", issues)

    def test_53_validation_accepts_observation_images_and_task_sized_lists(self) -> None:
        with self.make_root() as directory:
            docs = Path(directory) / "docs"
            source = docs / "chapter1" / "section1" / "README.md"
            source.parent.mkdir(parents=True)
            (source.parent / "image.png").write_bytes(b"image")
            steps = "\n".join(f"{index}. 观察状态。" for index in range(1, 15))
            results = "\n".join(f"- 结果 {index}。" for index in range(1, 15))
            text = (
                "# 页面\n\n## 验证方案\n\n" + steps + "\n\n预期结果：\n\n" + results
                + '\n\n观察界面中的状态：\n\n![状态](./image.png "image.png")\n\n画面显示该状态。\n'
            )
            self.assertEqual(check_markdown_structure.inspect_markdown(source, text, docs_root=docs), [])
            misplaced = text.replace(
                '\n\n观察界面中的状态：\n\n![状态](./image.png "image.png")\n\n画面显示该状态。\n',
                "",
            ).replace("预期结果：", '![状态](./image.png "image.png")\n\n预期结果：')
            issues = check_markdown_structure.inspect_markdown(source, misplaced, docs_root=docs)
            self.assertTrue(any("验证图片必须位于预期结果列表之后" in issue for issue in issues))

    def test_54_validation_reports_duplicate_blocks_and_stray_result_prose(self) -> None:
        with self.make_root() as directory:
            docs = Path(directory) / "docs"
            source = docs / "chapter1" / "section1" / "README.md"
            text = (
                "# 页面\n\n## 验证方案\n\n前置数据：存档。\n前置数据：对象。\n\n"
                "1. 观察。\n\n预期结果：\n\n- 显示。\n\n预期结果：\n\n- 显示。\n\n额外正文。\n"
            )
            issues = check_markdown_structure.inspect_validation_contract(source, text, docs_root=docs)
            self.assertTrue(any("一个前置数据段" in issue for issue in issues))
            self.assertTrue(any("一个预期结果标签" in issue for issue in issues))
            self.assertTrue(any("只能附验证图片" in issue for issue in issues))

    def test_55_maintenance_routes_standards_to_its_checker(self) -> None:
        from unittest.mock import patch

        with self.make_root() as directory:
            root = Path(directory)
            standards, validation = self.make_standard_set(root)
            with patch("text_maintenance.repository_root", return_value=root), patch(
                "check_standards.repository_root", return_value=root
            ):
                summary = text_maintenance.run([str(standards), str(validation)], apply=False)
            self.assertEqual(summary["errors"], [])
            self.assertEqual(summary["problems"], 0)
            self.assertEqual(summary["components"]["standards"]["scanned"], 4)
            self.assertEqual(summary["components"]["markdown"]["scanned"], 0)

            (standards / "technical.md").unlink()
            with patch("text_maintenance.repository_root", return_value=root), patch(
                "check_standards.repository_root", return_value=root
            ):
                missing = text_maintenance.run([str(standards), str(validation)], apply=False)
            self.assertTrue(any("标准文件缺失" in issue for item in missing["files"] for issue in item["issues"]))

    def test_56_fault_analysis_uses_semantic_instruction_words(self) -> None:
        source = Path("docs/chapter1/section1/README.md")
        text = (
            "# 页面\n\n## 故障分析\n\n"
            "- **对象缺失**：修正顺序与注册位置对应。\n"
            "    - **解决方案**：确认修正路径指向目标文件。\n\n## 总结\n"
        )
        self.assertEqual(check_markdown_structure.inspect_markdown(source, text), [])


    def test_57_apply_rechecks_trailing_whitespace(self) -> None:
        from _common import exit_code_for

        with self.make_root() as directory:
            path = Path(directory) / "sample.txt"
            path.write_bytes(b"value \t\r\n\r\n")
            dry = check_text_format.run([str(path)], apply=False)
            self.assertEqual(exit_code_for(dry), 1)
            applied = check_text_format.run([str(path)], apply=True)
            self.assertEqual(path.read_bytes(), b"value\n")
            self.assertEqual(exit_code_for(applied), 0)
            self.assertEqual(applied["remaining_files"], [])
            self.assertEqual(check_text_format.run([str(path)], apply=True)["written"], 0)

    def test_58_apply_reports_remaining_literal_diagnostics(self) -> None:
        from _common import exit_code_for, status_for

        with self.make_root() as directory:
            path = Path(directory) / "sample.md"
            text = '```java\nString label = "保存……完成"; // 中文(说明)\n```\n'
            path.write_bytes(text.encode("utf-8"))
            result = check_markdown_format.run([str(path)], apply=True)
            self.assertIn('"保存……完成"', path.read_text(encoding="utf-8"))
            self.assertIn('// 中文 (说明)', path.read_text(encoding="utf-8"))
            self.assertEqual(exit_code_for(result), 1)
            self.assertEqual(status_for(result), "findings")
            self.assertTrue(result["remaining_files"])

    def test_59_unclosed_tilde_and_long_fence_boundaries(self) -> None:
        text = "~~~text\nbody\n"
        self.assertEqual(check_markdown_format.check_markdown_format_text(text), (text, ["代码围栏未闭合"]))
        text = "````text\n```\n*  code\n````\n"
        restored, issues = check_markdown_format.check_markdown_format_text(text)
        self.assertEqual(restored, text)
        self.assertTrue(any("超长" in issue for issue in issues))

    def test_60_inline_pipe_and_java_text_block_are_preserved(self) -> None:
        text = '正文 `中文(原样)|中文(原样)`。\n```java\nString text = """\n// 中文(原样)……\n""";\n```\n'
        self.assertEqual(check_markdown_format.check_markdown_format_text(text)[0], text)

    def test_61_structure_reports_heading_and_container_boundaries(self) -> None:
        source = Path("docs/README.md")
        for text, label in (
            ("# 页面\n\n# 重复\n", "唯一 H1"),
            ("# 页面\n\n#### 深标题\n", "H1、H2 和 H3"),
            ("# 页面\n\n::: tip 说明\n正文\n", "容器未闭合"),
            ("# 页面\n\n:::\n", "没有对应开始"),
        ):
            with self.subTest(label=label):
                self.assertTrue(any(label in issue for issue in check_markdown_structure.inspect_markdown(source, text)))

    def test_62_fixture_lists_ignore_line_and_block_comments(self) -> None:
        text = '/* def javaUnitIds = ["C9-S9"] */\ndef javaUnitIds = ["C1-S1", // "C2-S1"\n/* "C3-S1" ] */]\ndef shaderUnitIds = []\n'
        lists, issues = check_fixtures.parse_unit_lists(text)
        self.assertEqual(lists, {"javaUnitIds": ["C1-S1"], "shaderUnitIds": []})
        self.assertEqual(issues, [])

    def test_63_fixture_continues_after_unreadable_page(self) -> None:
        with self.make_root() as directory:
            root = Path(directory)
            code = "package example;\npublic class Sample {\n}"
            self.make_fixture_repo(root, page_code=code, fixture_code=code)
            bad = root / "docs/chapter2/section1/README.md"
            bad.parent.mkdir(parents=True)
            bad.write_bytes(b"\xff")
            result = check_fixtures.run(root)
            self.assertEqual(result["matched_java_blocks"], 1)
            self.assertEqual(result["pages"], 2)
            self.assertEqual(len(result["errors"]), 1)

    def test_64_fault_formatter_stops_at_next_section_and_preserves_code(self) -> None:
        text = "## 故障分析\n\n```text\n- a\n- b\n```\n\n## 其他内容\n\n- c\n- d\n"
        self.assertEqual(format_fault_analysis.repair_fault_spacing(text), (text, 0, 0))

    def test_65_restore_preserves_highlight_range_container_title_and_list_order(self) -> None:
        html = '<div id="content"><pre data-highlight-lines="2-4"><code>a\nb\nc\nd</code></pre><div class="extra warning hint-container"><p class="hint-container-title">注意</p><p>正文</p></div><ul><li><p>前文</p><ul><li>嵌套</li></ul><p>后文</p></li></ul></div>'
        text = restore_markdown.restore_markdown_text(html)
        self.assertIn("``` {2-4}", text)
        self.assertIn("::: warning 注意", text)
        self.assertLess(text.index("前文"), text.index("嵌套"))
        self.assertLess(text.index("嵌套"), text.index("后文"))

    def test_66_restore_output_is_idempotent(self) -> None:
        with self.make_root() as directory:
            root = Path(directory)
            source = root / "index.html"
            source.write_bytes(b'<div id="content"><h1>Title</h1></div>')
            target = root / "result.md"
            self.assertEqual(restore_markdown.run(str(source))["problems"], 0)
            self.assertEqual(restore_markdown.run(str(source), out=str(target))["written"], 1)
            self.assertEqual(restore_markdown.run(str(source), out=str(target))["written"], 0)

    def test_67_merge_counts_unique_diagnostics_and_keeps_verbose_files(self) -> None:
        from _common import make_summary

        result = make_summary(scanned=2, skipped=0, bytes_scanned=5)
        result["files"] = [{"path": "a", "issues": ["one"]}, {"path": "b", "issues": []}]
        result["problems"] = 1
        merged = text_maintenance.merge_findings(result, result)
        self.assertEqual(merged["problems"], 1)
        self.assertEqual(merged["problem_files"], 1)
        self.assertEqual(len(merged["files"]), 2)

    def test_68_explicit_symlink_is_skipped(self) -> None:
        with self.make_root() as directory:
            root = Path(directory)
            target = root / "target.md"
            target.write_bytes(b"text\n")
            link = root / "link.md"
            try:
                link.symlink_to(target)
            except OSError as exc:
                self.skipTest(f"当前环境不能创建符号链接：{exc}")
            files, skipped, errors = collect_text_files([str(link)])
            self.assertEqual((files, skipped, errors), ([], 1, []))

    def test_69a_text_preserves_multiline_literal_whitespace(self) -> None:
        from _common import exit_code_for

        with self.make_root() as directory:
            root = Path(directory)
            java = root / "Sample.java"
            content = 'String text = """\nvalue  \n""";\n'
            java.write_bytes(content.encode("utf-8"))
            result = check_text_format.run([str(java)], apply=True)
            self.assertEqual(java.read_bytes(), content.encode("utf-8"))
            self.assertEqual(exit_code_for(result), 1)
            markdown = root / "sample.md"
            source = '```java\n' + content + '```\n'
            markdown.write_bytes(source.encode("utf-8"))
            result = text_maintenance.run([str(markdown)], apply=True)
            self.assertEqual(markdown.read_bytes(), source.encode("utf-8"))
            self.assertEqual(exit_code_for(result), 1)

    def test_69b_tilde_conversion_preserves_embedded_backticks(self) -> None:
        text = "~~~text\n```\nbody\n~~~\n"
        restored, issues = check_markdown_format.check_markdown_format_text(text)
        self.assertEqual(restored, text)
        self.assertTrue(any("波浪号围栏" in issue for issue in issues))

    def test_69c_unreadable_file_is_reported_with_byte_count(self) -> None:
        from unittest.mock import patch

        with self.make_root() as directory:
            path = Path(directory) / "sample.md"
            path.write_bytes(b"# Title\n")
            for module in (check_text_format, check_markdown_format, check_markdown_structure, check_standards):
                with self.subTest(module=module.__name__), patch.object(module, "read_utf8", side_effect=OSError("read failure")):
                    if module is check_standards:
                        root = Path(directory) / "standards-test"
                        standards, validation = self.make_standard_set(root)
                        result = module.run([str(standards), str(validation)], repo_root=root)
                        count = 4
                    elif module is check_markdown_structure:
                        result = module.run([str(path)], docs_root=path.parent)
                        count = 1
                    else:
                        result = module.run([str(path)], apply=False)
                        count = 1
                    self.assertEqual(len(result["errors"]), count)
                    self.assertEqual(result["bytes"], 0)

    def test_69_real_cli_final_exit_codes(self) -> None:
        import json

        with self.make_root() as directory:
            root = Path(directory)
            path = root / "sample.md"
            path.write_bytes(b"text \t\r\n")
            for extra, expected in (([], 1), (["--apply"], 0), ([], 0)):
                completed = subprocess.run(
                    [sys.executable, "-X", "utf8", "-B", str(TOOLS_DIR / "check_text_format.py"), str(path), "--json", *extra],
                    cwd=root, capture_output=True, text=True, encoding="utf-8",
                )
                self.assertEqual(completed.returncode, expected, completed.stderr)
                self.assertEqual(json.loads(completed.stdout)["status"], "findings" if expected else "pass")
            path.write_bytes(b"````text\nbody\n````\n")
            completed = subprocess.run(
                [sys.executable, "-X", "utf8", "-B", str(TOOLS_DIR / "check_markdown_format.py"), str(path), "--apply"],
                cwd=root, capture_output=True, text=True, encoding="utf-8",
            )
            self.assertEqual(completed.returncode, 1, completed.stderr)
            completed = subprocess.run(
                [sys.executable, "-X", "utf8", "-B", str(TOOLS_DIR / "check_text_format.py"), str(root / "missing.md")],
                cwd=root, capture_output=True, text=True, encoding="utf-8",
            )
            self.assertEqual(completed.returncode, 2, completed.stderr)

    def test_70_atomic_write_preserves_mode_and_original_on_failure(self) -> None:
        import stat
        from unittest.mock import patch
        from _common import atomic_write

        with self.make_root() as directory:
            path = Path(directory) / "sample.txt"
            path.write_bytes(b"old\n")
            mode = stat.S_IMODE(path.stat().st_mode)
            with patch("_common.os.replace", side_effect=OSError("replace failure")):
                with self.assertRaisesRegex(OSError, "replace failure"):
                    atomic_write(path, b"new\n")
            self.assertEqual(path.read_bytes(), b"old\n")
            self.assertEqual(list(path.parent.glob("*.tmp")), [])
            atomic_write(path, b"new\n")
            self.assertEqual(stat.S_IMODE(path.stat().st_mode), mode)

    def test_71_platform_path_identity_and_repository_root(self) -> None:
        from _common import repository_root

        self.assertEqual(repository_root(), TOOLS_DIR.parent)
        with self.make_root() as directory:
            root = Path(directory)
            (root / "Sample.md").write_bytes(b"a\n")
            (root / "sample.md").write_bytes(b"b\n")
            files, _, errors = collect_text_files([str(root), str(root / "Sample.md")])
            self.assertEqual(errors, [])
            self.assertEqual(len(files), 1 if os.name == "nt" else 2)

    def test_72_restore_and_fault_cli_exit_codes(self) -> None:
        with self.make_root() as directory:
            root = Path(directory)
            html = root / "index.html"
            html.write_bytes(b'<div id="content"><h1>Title</h1></div>')
            fault = root / "fault.md"
            fault.write_bytes("# 页面\n\n## 故障分析\n- **症状**：原因。\n\n    - **解决方案**：修正。\n\n## 总结\n".encode("utf-8"))
            for script, args, expected in (
                ("restore_markdown.py", [str(html)], 0),
                ("restore_markdown.py", [str(html), "--out", str(root / "result.md")], 0),
                ("format_fault_analysis.py", [str(fault)], 1),
                ("format_fault_analysis.py", [str(fault), "--apply"], 0),
                ("format_fault_analysis.py", [str(fault)], 0),
            ):
                with self.subTest(script=script, args=args):
                    completed = subprocess.run(
                        [sys.executable, "-X", "utf8", "-B", str(TOOLS_DIR / script), *args],
                        cwd=root, capture_output=True, text=True, encoding="utf-8",
                    )
                    self.assertEqual(completed.returncode, expected, completed.stderr)

if __name__ == "__main__":
    unittest.main()
