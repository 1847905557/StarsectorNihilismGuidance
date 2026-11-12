import assert from 'node:assert/strict';
import { mkdtemp, writeFile, rm } from 'node:fs/promises';
import { tmpdir } from 'node:os';
import { test } from 'node:test';
import { join } from 'node:path';
import { repositoryRoot } from './shared/files.mjs';
import { restoreMarkdown } from './restore-markdown.mjs';
const path = join(repositoryRoot, 'docs/README.md'),
  docs = join(repositoryRoot, 'docs');
test('restoration retains code text, language, highlights, aligned cells and inline marks', async () => {
  const source =
    '<h1>标题</h1><p><strong><code>Global</code> 入口</strong></p>' +
    '<pre data-language="java" data-highlight-lines="2-4"><code>one\ntwo\nthree\nfour</code></pre>' +
    '<h2>字段速查表</h2><table><tr><th style="text-align:right">列</th><th>值</th></tr><tr><td>A</td><td>B</td></tr><tr><td>C</td><td>D</td></tr></table>';
  const restored = await restoreMarkdown(source, path, docs);
  assert.equal(restored.ok, true, JSON.stringify(restored));
  assert.ok(restored.markdown.includes('**`Global` 入口**'));
  assert.ok(restored.markdown.includes('```java {2-4}\none\ntwo\nthree\nfour\n```'));
  assert.match(restored.markdown, /\| *-+: *\|/);
});
test('continuous marks across links remain one semantic mark', async () => {
  const restored = await restoreMarkdown(
    '<h1>标题</h1><p><strong>前文</strong><a href="https://example.com/"><strong>链接</strong></a><strong>后文</strong></p>',
    path,
    docs,
  );
  assert.equal(restored.ok, true);
  assert.ok(restored.markdown.includes('**前文[链接](https://example.com/) 后文**'));
});
test('literal Markdown characters and block prefixes remain literal', async () => {
  const restored = await restoreMarkdown(
    '<h1>标题</h1><p>literal *star* _under_ [bracket] &lt;tag&gt; `tick` \\path</p><p>1. literal</p>',
    path,
    docs,
  );
  assert.equal(restored.ok, true, JSON.stringify(restored));
  assert.ok(restored.markdown.includes('\\*star\\*'));
  assert.ok(restored.markdown.includes('\\[bracket\\]'));
  assert.ok(restored.markdown.includes('1\\. literal'));
});
test('containers and list tightness survive conversion', async () => {
  const restored = await restoreMarkdown(
    '<h1>标题</h1><div data-vp-container="tip" data-vp-title="说明"><p>正文</p></div>' +
      '<ul data-dev-list-tight="false"><li><p>甲</p><ul data-dev-list-tight="true"><li>子项</li></ul><p>后文</p></li><li><p>乙</p></li></ul>',
    path,
    docs,
  );
  assert.equal(restored.ok, true, JSON.stringify(restored));
  assert.ok(restored.markdown.includes('::: tip 说明'));
  assert.ok(restored.markdown.indexOf('甲') < restored.markdown.indexOf('子项'));
  assert.ok(restored.markdown.indexOf('子项') < restored.markdown.indexOf('后文'));
});
test('data images preserve exact payload and reject identity collisions', async (t) => {
  const work = await mkdtemp(join(tmpdir(), 'tutorial-assets-'));
  t.after(() => rm(work, { recursive: true, force: true }));
  const imagePath = join(work, 'README.md');
  await writeFile(join(work, 'image.png'), 'image-data');
  const image = Buffer.from('image-data').toString('base64');
  const restored = await restoreMarkdown(
    '<h1>标题</h1><p><img src="data:image/png;base64,' + image + '" alt="图" title="image.png"></p>',
    imagePath,
    work,
  );
  assert.equal(restored.ok, true, JSON.stringify(restored));
  assert.deepEqual(restored.assets, [{ name: 'image.png', size: 10, base64: image }]);
  const conflict = await restoreMarkdown(
    '<h1>标题</h1><p><img src="data:image/png;base64,' +
      image +
      '" alt="图" title="image.png">' +
      '<img src="data:image/png;base64,' +
      Buffer.from('other-data').toString('base64') +
      '" alt="图" title="image.png"></p>',
    imagePath,
    work,
  );
  assert.match(conflict.diagnostics[0], /文件名冲突/);
  const mismatch = await restoreMarkdown(
    '<h1>标题</h1><p><img src="data:image/png;base64,' + image + '" alt="图" title="image.jpg"></p>',
    path,
    docs,
  );
  assert.equal(mismatch.ok, false);
  assert.match(mismatch.diagnostics[0], /扩展名/);
  const invalid = await restoreMarkdown('<h1>标题</h1><p><img src="data:image/png;base64,?" alt="图" title="image.png"></p>', path, docs);
  assert.match(invalid.diagnostics[0], /Base64/);
});
test('empty content, incomplete containers and chapter contracts return structured diagnostics', async () => {
  assert.equal((await restoreMarkdown('', path, docs)).ok, false);
  const chapter = await restoreMarkdown(
    '<h1>章页</h1><p>介绍。</p><h2>本章各节</h2><div data-auto-section-list="true"></div>',
    join(docs, 'chapter1/README.md'),
    docs,
  );
  assert.equal(chapter.ok, false);
  assert.ok(chapter.diagnostics.some((message) => message.includes('本章技术基线')));
});
