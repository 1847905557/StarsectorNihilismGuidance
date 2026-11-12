import assert from 'node:assert/strict';
import { mkdtemp, mkdir, writeFile, rm, unlink } from 'node:fs/promises';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import { test } from 'node:test';
import { checkDocs, guidanceFiles, inspectDocument } from './check-docs.mjs';

async function workspace(t) {
  const root = await mkdtemp(join(tmpdir(), 'tutorial-docs-'));
  t.after(() => rm(root, { recursive: true, force: true }));
  await mkdir(join(root, 'docs/chapter1/section1'), { recursive: true });
  return root;
}
const chapter = '# 章页\n\n本章技术基线：Starsector `0.98.5a-RC3`。\n\n章介绍。\n\n## 本章各节\n\n<!-- AUTO:SECTION-LIST -->\n';
test('chapter structure, baseline and automatic list are checked from the actual page role', async (t) => {
  const root = await workspace(t),
    path = join(root, 'docs/chapter1/README.md');
  assert.deepEqual(await inspectDocument(path, chapter, { root }), []);
  for (const [text, rule] of [
    [chapter.replace('本章技术基线：Starsector `0.98.5a-RC3`。\n\n', ''), 'chapter-baseline'],
    [chapter.replace('<!-- AUTO:SECTION-LIST -->', ''), 'chapter-catalog'],
    [chapter + '\n尾部。\n', 'chapter-catalog'],
    [chapter.replace('## 本章各节', '### 本章各节'), 'chapter-catalog'],
  ])
    assert.ok((await inspectDocument(path, text, { root })).some((item) => item.rule === rule));
  assert.deepEqual(await inspectDocument(join(root, 'docs/README.md'), '# 首页\n\n正文。\n', { root }), []);
});
test('generic Markdown checks preserve fenced literals and locate headings, links and containers', async (t) => {
  const root = await workspace(t),
    path = join(root, 'docs/README.md');
  const valid = '# 页面\n\n```java\nString label = "中文（原文）";\n```\n';
  assert.deepEqual(await inspectDocument(path, valid, { root }), []);
  const diagnostics = await inspectDocument(path, valid + '\n#### 深标题\n\n[目标](missing.md)\n\n::: tip 说明\n', { root });
  for (const rule of ['heading-level', 'relative-link', 'container-close']) assert.ok(diagnostics.some((item) => item.rule === rule));
  assert.ok((await inspectDocument(path, '# 页面\n\n```text\nbody\n', { root })).some((item) => item.rule === 'fence-close'));
});
test('section references and image identity use parsed Markdown and existing targets', async (t) => {
  const root = await workspace(t),
    path = join(root, 'docs/README.md');
  await writeFile(join(root, 'docs/chapter1/section1/README.md'), '# 目标\n');
  await writeFile(join(root, 'docs/image.png'), 'image');
  assert.deepEqual(await inspectDocument(path, '# 页面\n\n[C1-S1]\n\n![图](./image.png "image.png")\n', { root }), []);
  const diagnostics = await inspectDocument(path, '# 页面\n\n[C01-S1] C2-S1\n\n![](./image.png "other.png")\n', { root });
  for (const rule of ['section-reference', 'image-alt', 'image-title']) assert.ok(diagnostics.some((item) => item.rule === rule));
});
test('validation and fault lists retain their compact structure', async (t) => {
  const root = await workspace(t),
    path = join(root, 'docs/chapter1/section1/README.md');
  const valid =
    '# 页面\n\n## 验证方案\n\n1. 启动。\n2. 观察。\n\n预期结果：\n\n- 显示结果。\n\n## 故障分析\n\n- **症状**：原因。\n    - **解决方案**：修正。\n\n## 总结\n\n完成。\n';
  assert.deepEqual(await inspectDocument(path, valid, { root }), []);
  assert.ok(
    (await inspectDocument(path, valid.replace('1. 启动。\n2.', '1. 启动。\n\n2.'), { root })).some((item) => item.rule === 'validation'),
  );
  assert.ok(
    (await inspectDocument(path, valid.replace('    - **解决方案**', '- **解决方案**'), { root })).some(
      (item) => item.rule === 'fault-solution',
    ),
  );
});
test('formal document set, reading routes and single-file checks share the same implementation', async (t) => {
  const root = await workspace(t),
    directory = join(root, '.zcode');
  await mkdir(directory);
  for (const name of guidanceFiles) await writeFile(join(directory, name), '# 文档\n\n职责说明。\n');
  await writeFile(
    join(directory, 'workflow.md'),
    '# Workflow\n\n' +
      guidanceFiles
        .filter((name) => name !== 'workflow.md')
        .map((name) => '- 读取 `.zcode/' + name + '`。')
        .join('\n') +
      '\n',
  );
  assert.equal((await checkDocs([directory], { root })).problems, 0);
  const single = await checkDocs([join(directory, 'text-guidelines.md')], { root });
  assert.equal(single.scanned, 1);
  await unlink(join(directory, 'text-guidelines.md'));
  await writeFile(join(directory, 'extra.md'), '# Extra\n');
  const broken = await checkDocs([directory], { root });
  assert.equal(broken.problems, 2);
  await writeFile(join(directory, 'workflow.md'), '# Workflow\n');
  assert.ok((await checkDocs([directory], { root })).files.some((file) => file.diagnostics.some((item) => item.rule === 'guidance-route')));
});

test('table checks use source column counts and a semantic heading', async (t) => {
  const root = await workspace(t),
    path = join(root, 'docs/README.md');
  const source = '# 页面\n\n## 字段速查表\n\n| 字段 | 说明 |\n| --- | --- |\n| A | 甲 |\n| B | 乙 |\n';
  assert.deepEqual(await inspectDocument(path, source, { root }), []);
  const broken = await inspectDocument(path, source.replace('| A | 甲 |', '| A | 甲 | 多列 |'), { root });
  assert.ok(broken.some((item) => item.rule === 'tutorial-table' && item.message.includes('列数')));
});
