import assert from 'node:assert/strict';
import { test } from 'node:test';
import { commentSpans, formatProse, inspectText, transformTypography } from './check-text.mjs';

for (const [input, expected] of [
  ['中文（说明）返回……', '中文 (说明) 返回...'],
  ['中文English和123数字', '中文 English 和 123 数字'],
  ['中文“引号”文字', '中文 "引号" 文字'],
  ['文字——说明。', '文字 —— 说明。'],
]) {
  test('prose typography: ' + input, () => {
    assert.equal(formatProse(input), expected);
    assert.equal(formatProse(expected), expected);
  });
}
test('inline code, destinations, images and API values retain exact characters', () => {
  const input = '# 文档\n\n正文 `中文（原样）` 与 [中文(说明)](https://example.com/a_(b))。\n\n![中文图](./file.png "file.png")\n';
  const output = transformTypography(input);
  assert.ok(output.includes('`中文（原样）`'));
  assert.ok(output.includes('https://example.com/a_(b)'));
  assert.ok(output.includes('![中文图](./file.png "file.png")'));
  assert.ok(output.includes('[中文 (说明)]'));
});
test('Java strings, text blocks, URLs and code symbols remain unchanged while comments format', () => {
  const input =
    '```java\nString label = "中文（原文）"; // 中文(说明)\nString value = """\n// 中文（原样）\n""";\nif (a || b) {} /* 中文(说明) */\n```\n';
  const output = transformTypography(input);
  assert.ok(output.includes('"中文（原文）"'));
  assert.ok(output.includes('// 中文（原样）'));
  assert.ok(output.includes('// 中文 (说明)'));
  assert.ok(output.includes('a || b'));
  assert.ok(output.includes('/* 中文 (说明) */'));
});
test('shell comments respect quoted hashes and shader directives', () => {
  const text = 'echo "中文#原文" # 中文(说明)\n';
  assert.equal(commentSpans(text, 'bash').length, 1);
  assert.equal(commentSpans('#version 110\nvoid main() {}\n', 'glsl').length, 0);
});
test('encoding diagnostics carry exact source positions', () => {
  const input = '# 标题\r\n\r\n正文 \t\r\n';
  const diagnostics = inspectText(input, { bom: true });
  assert.equal(diagnostics.filter((item) => item.rule === 'line-ending').length, 3);
  assert.ok(diagnostics.some((item) => item.rule === 'trailing-space' && item.line === 3 && item.column === 3));
  assert.ok(diagnostics.some((item) => item.rule === 'encoding-bom' && item.line === 1 && item.column === 1));
});

test('placeholder restoration preserves dollar sequences, tables and apostrophes', () => {
  const source = '# 页面\n\nMemory 使用 `$` 开头的键，`$&` 和 `$1` 保留。\n\n| 项目 | 值 |\n| --- | --- |\n| 状态 | 中文。   |\n';
  const output = transformTypography(source);
  assert.equal(output, source);
  assert.equal(formatProse("Don't change John's value."), "Don't change John's value.");
  assert.equal(inspectText('# 页面\n\n正文 — 说明。\n').filter((item) => item.rule === 'single-dash').length, 1);
});
