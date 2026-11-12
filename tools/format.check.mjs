import assert from 'node:assert/strict';
import { mkdtemp, readFile, rm, writeFile } from 'node:fs/promises';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import { spawnSync } from 'node:child_process';
import { test } from 'node:test';
import { formatFiles, formatMarkdown } from './format.mjs';
import { repositoryRoot } from './shared/files.mjs';

test('formatting is a fixed point and preserves code fences, containers and tight fault lists', async () => {
  const code = 'package example;\npublic class Value { String label = "中文（原文）"; }\n';
  const input =
    '# 页面\n\n*阅读时间：\\~15 分钟。*\n\n::: details 完整成品：Value\n\n```java\n' +
    code +
    '```\n\n:::\n\n## 常见问题\n\n- **症状**：原因。\n    - **解决方案**：修正。\n\n- **另一个症状**：原因。\n    - **解决方案**：修正。\n';
  const output = await formatMarkdown(input);
  assert.ok(output.includes(code));
  assert.ok(output.includes('_阅读时间'));
  assert.ok(output.includes('::: details 完整成品：Value'));
  assert.equal(await formatMarkdown(output), output);
});
test('dry-run, write and repeat execution use the same scope and expose final results', async (t) => {
  const root = await mkdtemp(join(tmpdir(), 'tutorial-format-'));
  t.after(() => rm(root, { recursive: true, force: true }));
  const path = join(root, 'page.md');
  const original = '# 标题\r\n\r\n中文(说明)\r\n';
  await writeFile(path, original);
  const preview = await formatFiles([path], { root });
  assert.equal(preview.changed, 1);
  assert.equal(preview.written, 0);
  assert.equal(await readFile(path, 'utf8'), original);
  const written = await formatFiles([path], { root, write: true });
  assert.equal(written.written, 1);
  assert.equal(written.problems, 0);
  assert.equal((await formatFiles([path], { root })).changed, 0);
});
test('real CLI returns findings and execution errors', async (t) => {
  const root = await mkdtemp(join(tmpdir(), 'tutorial-cli-'));
  t.after(() => rm(root, { recursive: true, force: true }));
  const path = join(root, 'page.md');
  await writeFile(path, '# 标题\r\n');
  const script = join(repositoryRoot, 'tools/format.mjs');
  const preview = spawnSync(process.execPath, [script, path, '--json'], { encoding: 'utf8', windowsHide: true });
  assert.equal(preview.status, 1, preview.stderr);
  assert.equal(JSON.parse(preview.stdout).changed, 1);
  const missing = spawnSync(process.execPath, [script, join(root, 'missing.md'), '--json'], { encoding: 'utf8', windowsHide: true });
  assert.equal(missing.status, 2);
  const unknown = spawnSync(process.execPath, [script, '--unknown'], { encoding: 'utf8', windowsHide: true });
  assert.equal(unknown.status, 2);
});

test('fault spacing fixes only the corresponding section and retains fenced examples', async () => {
  const source =
    '# 页面\n\n## 常见问题\n\n- **症状**：原因。\n\n    - **解决方案**：修正。\n- **其他症状**：原因。\n    - **解决方案**：修正。\n\n## 示例\n\n```text\n- a\n- b\n```\n';
  const output = await formatMarkdown(source);
  assert.ok(output.includes('- **症状**：原因。\n    - **解决方案**：修正。\n\n- **其他症状**'));
  assert.ok(output.includes('```text\n- a\n- b\n```'));
});
