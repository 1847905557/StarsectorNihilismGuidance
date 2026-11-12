// @vitest-environment node

import { createHash } from 'node:crypto';
import { EventEmitter } from 'node:events';
import { mkdtempSync, mkdirSync, readFileSync, readdirSync, rmSync, writeFileSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { join } from 'node:path';

import { afterEach, describe, expect, it, vi } from 'vitest';
import * as restorer from '../../tools/restore-markdown.mjs';

import {
  encode,
  devEditVitePlugin,
  resolveEditTarget,
  runMarkdownRestore,
  sourceRevision,
  validateRestoredAssets,
} from './plugin-dev-edit';

const temporaryRoots: string[] = [];

afterEach(() => {
  vi.useRealTimers();
  temporaryRoots.splice(0).forEach((root) => rmSync(root, { recursive: true, force: true }));
});

const fixture = (relativeDir = 'chapter1/section1') => {
  const root = mkdtempSync(join(tmpdir(), 'dev-edit-'));
  temporaryRoots.push(root);
  const docs = join(root, 'docs');
  const pageDir = join(docs, relativeDir);
  mkdirSync(pageDir, { recursive: true });
  const page = join(pageDir, 'README.md');
  writeFileSync(page, '# 原标题\r\n', 'utf-8');
  return { docs, page };
};

describe('dev edit source binding', () => {
  it('writes LF with a single terminal newline', () => {
    expect(encode('# 标题\r\n正文\r\n\r\n')).toBe('# 标题\n正文\n');
    expect(encode('# 标题')).toBe('# 标题\n');
  });
  it('uses the exact source bytes for SHA-256 revisions', () => {
    const bytes = Buffer.from('# 标题\r\n', 'utf-8');
    expect(sourceRevision(bytes)).toBe(createHash('sha256').update(bytes).digest('hex'));
    expect(sourceRevision(Buffer.from('# 标题\n', 'utf-8'))).not.toBe(sourceRevision(bytes));
  });

  it('allows existing Markdown below docs and rejects traversal or other extensions', () => {
    const { docs, page } = fixture();
    expect(resolveEditTarget(docs, 'chapter1/section1/README.md')).toBe(page);
    expect(resolveEditTarget(docs, '../README.md')).toBeNull();
    expect(resolveEditTarget(docs, 'chapter1/section1/missing.md')).toBeNull();
    expect(resolveEditTarget(docs, 'chapter1/section1/image.png')).toBeNull();
  });
});

describe('restore function boundary', () => {
  it('restores Tiptap HTML through the shared Node function', async () => {
    const { docs, page } = fixture('');
    const result = await runMarkdownRestore(
      '<h1>新标题</h1><pre data-language="java"><code class="language-java">int value = 1;</code></pre>',
      page,
      docs,
    );

    expect(result.ok).toBe(true);
    expect(result.markdown).toContain('# 新标题');
    expect(result.markdown).toContain('```java');
  });

  it('preserves complex Unicode Tiptap HTML', async () => {
    const { docs, page } = fixture('');
    const result = await runMarkdownRestore(
      '<h1>虚无主义教程 (Nihilism Guidance)</h1>' +
        '<p>调整“此处”这个字，并保留 "双引号"。</p>' +
        '<div data-vp-container="tip" data-vp-title="技能要求？">' +
        '<p><strong>代码</strong>：保留中文属性与标点。</p></div>' +
        '<pre data-language="json"><code class="language-json">{"key": "值"}</code></pre>',
      page,
      docs,
    );

    expect(result.ok).toBe(true);
    expect(result.markdown).toContain('调整 "此处" 这个字');
    expect(result.markdown).toContain('::: tip 技能要求？');
    expect(result.markdown).toContain('{"key": "值"}');
  });

  it('merges continuous marks split by a Tiptap link boundary', async () => {
    const { docs, page } = fixture('');
    const result = await runMarkdownRestore(
      '<h1>标题</h1>' +
        '<p><strong>前文</strong><a href="https://example.com/"><strong>链接</strong></a>' +
        '<strong>后文</strong></p>' +
        '<p><em>斜体前</em><a href="https://example.com/"><em>斜体链接</em></a>' +
        '<em>斜体后</em></p>' +
        '<p><s>删除前</s><a href="https://example.com/"><s>删除链接</s></a>' +
        '<s>删除后</s></p>',
      page,
      docs,
    );

    expect(result.ok).toBe(true);
    expect(result.markdown).toContain('**前文[链接](https://example.com/) 后文**');
    expect(result.markdown).toContain('_斜体前[斜体链接](https://example.com/) 斜体后_');
    expect(result.markdown).toContain('~~删除前[删除链接](https://example.com/) 删除后~~');
  });

  it('returns structured diagnostics for an empty document', async () => {
    const { docs, page } = fixture();
    const result = await runMarkdownRestore('', page, docs);
    expect(result.ok).toBe(false);
    expect(result.diagnostics?.length).toBeGreaterThan(0);
  });

  it('restores a chapter baseline, introduction, and automatic section list', async () => {
    const { docs, page } = fixture('chapter1');
    const result = await runMarkdownRestore(
      '<h1>章页</h1><p>本章技术基线：Starsector <code>0.98a-RC8</code>。</p>' +
        '<p>共同主题。</p><p>实际用途。</p><p>理解重点。</p>' +
        '<h2>本章各节</h2><div data-auto-section-list="true"></div>',
      page,
      docs,
    );

    expect(result.ok).toBe(true);
    expect(result.markdown).toBe(
      '# 章页\n\n本章技术基线：Starsector `0.98a-RC8`。\n\n' +
        '共同主题。\n\n实际用途。\n\n理解重点。\n\n' +
        '## 本章各节\n\n<!-- AUTO:SECTION-LIST -->\n',
    );
  });

  it('returns chapter diagnostics when the baseline is malformed', async () => {
    const { docs, page } = fixture('chapter1');
    const result = await runMarkdownRestore(
      '<h1>章页</h1><p>本章技术基线：格式错误</p><p>章介绍。</p>' + '<h2>本章各节</h2><div data-auto-section-list="true"></div>',
      page,
      docs,
    );

    expect(result.ok).toBe(false);
    expect(result.diagnostics).toContain('本章技术基线格式错误');
  });
});

describe('page write middleware', () => {
  const session = () => {
    const { docs, page } = fixture('');
    const previous = process.cwd();
    let handler: (request: unknown, response: unknown) => void = () => {};
    try {
      process.chdir(join(docs, '..'));
      devEditVitePlugin().configureServer({
        config: { root: docs },
        middlewares: {
          use: (value) => {
            handler = value;
          },
        },
      });
    } finally {
      process.chdir(previous);
    }
    const send = async (payload: Record<string, unknown>) => {
      const req = new EventEmitter() as EventEmitter & { url: string; method: string };
      req.url = '/__api/edit-page';
      req.method = 'POST';
      const completed = new Promise<{ status: number; body: Record<string, unknown> }>((resolve) => {
        handler(req, {
          statusCode: 0,
          setHeader: () => {},
          end(this: { statusCode: number }, body: string) {
            resolve({ status: this.statusCode, body: JSON.parse(body) });
          },
        });
      });
      req.emit('data', Buffer.from(JSON.stringify({ path: 'README.md', revision: sourceRevision(readFileSync(page)), ...payload })));
      req.emit('end');
      return { ...(await completed), page, docs };
    };
    return { send, page, docs };
  };
  const request = (payload: Record<string, unknown>) => session().send(payload);
  it('writes restored Markdown and returns the actual new source revision', async () => {
    const result = await request({ html: '<h1>更新标题</h1><p>中文(说明)</p>' });
    expect(result.status).toBe(200);
    const saved = readFileSync(result.page, 'utf8');
    expect(saved).toContain('中文 (说明)');
    expect(result.body.revision).toBe(sourceRevision(Buffer.from(saved)));
    expect(readdirSync(join(result.docs, '..', '.zcode/tutorial-work/backups/edit-writes'))).toHaveLength(1);
  });
  it('returns a version conflict while preserving current source content', async () => {
    const result = await request({ revision: 'different-version', html: '<h1>新内容</h1>' });
    expect(result.status).toBe(409);
    expect(result.body.code).toBe('revision-conflict');
    expect(readFileSync(result.page, 'utf8')).toBe('# 原标题\r\n');
  });
  it('returns validation diagnostics while preserving current source content', async () => {
    const result = await request({ html: '' });
    expect(result.status).toBe(422);
    expect(result.body.code).toBe('validation-failed');
    expect(readFileSync(result.page, 'utf8')).toBe('# 原标题\r\n');
  });
  it('accepts exactly one concurrent save from the same source version', async () => {
    const work = session();
    const results = await Promise.all([work.send({ html: '<h1>第一份修改</h1>' }), work.send({ html: '<h1>第二份修改</h1>' })]);
    expect(results.map((result) => result.status).sort()).toEqual([200, 409]);
    const accepted = results.find((result) => result.status === 200)!;
    expect(sourceRevision(readFileSync(work.page))).toBe(accepted.body.revision);
    expect(readdirSync(join(work.docs, '..', '.zcode/tutorial-work/backups/edit-writes'))).toHaveLength(1);
  });
  it('preserves external edits that arrive during Markdown restoration', async () => {
    const work = session();
    const restore = restorer.restoreMarkdown;
    const spy = vi.spyOn(restorer, 'restoreMarkdown').mockImplementation(async (...args) => {
      const result = await restore(...args);
      writeFileSync(work.page, '# 外部修改\n', 'utf8');
      return result;
    });
    try {
      const result = await work.send({ html: '<h1>编辑器修改</h1>' });
      expect(result.status).toBe(409);
      expect(readFileSync(work.page, 'utf8')).toBe('# 外部修改\n');
    } finally {
      spy.mockRestore();
    }
  });
  it('keeps both source backups for two saves in the same second', async () => {
    vi.useFakeTimers({ toFake: ['Date'] });
    vi.setSystemTime(new Date('2026-11-12T12:00:00Z'));
    const work = session();
    const first = await work.send({ html: '<h1>第一次修改</h1>' });
    const second = await work.send({ html: '<h1>第二次修改</h1>' });
    expect([first.status, second.status]).toEqual([200, 200]);
    const directory = join(work.docs, '..', '.zcode/tutorial-work/backups/edit-writes');
    const names = readdirSync(directory);
    expect(names).toHaveLength(2);
    expect(names.map((name) => readFileSync(join(directory, name), 'utf8')).sort()).toEqual(['# 原标题\r\n', '# 第一次修改\n'].sort());
  });
  it('processes the next save after a failed restoration', async () => {
    const work = session();
    expect((await work.send({ html: '' })).status).toBe(422);
    expect((await work.send({ html: '<h1>继续保存</h1>' })).status).toBe(200);
    expect(readFileSync(work.page, 'utf8')).toBe('# 继续保存\n');
  });
});

describe('data URI asset contract', () => {
  it('accepts only an existing byte-identical asset with a local filename', () => {
    const { page } = fixture();
    const bytes = Buffer.from('image-bytes');
    writeFileSync(join(page, '..', 'ship.png'), bytes);
    const asset = { name: 'ship.png', size: bytes.length, base64: bytes.toString('base64') };

    expect(validateRestoredAssets(page, [asset])).toEqual([]);
    expect(validateRestoredAssets(page, [{ ...asset, name: '../ship.png' }])).toHaveLength(1);
    expect(validateRestoredAssets(page, [{ ...asset, name: 'missing.png' }])).toHaveLength(1);
    expect(validateRestoredAssets(page, [{ ...asset, base64: Buffer.from('changed').toString('base64') }])).toHaveLength(1);
    expect(readFileSync(join(page, '..', 'ship.png'))).toEqual(bytes);
  });
});
