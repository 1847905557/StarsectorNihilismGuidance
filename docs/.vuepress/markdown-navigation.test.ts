// @vitest-environment node

import { mkdtempSync, mkdirSync, readFileSync, rmSync, writeFileSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import { createMarkdown } from 'vuepress/markdown';
import { watch } from 'chokidar';
import { afterEach, describe, expect, it } from 'vitest';
import { sectionListPlugin } from './markdown-sectionlist';
import { spLinkPlugin } from './markdown-splink';

const roots: string[] = [];
afterEach(() => {
  roots.splice(0).forEach((root) => rmSync(root, { recursive: true, force: true }));
});
const chapter = () => {
  const root = mkdtempSync(join(tmpdir(), 'chapter-navigation-'));
  roots.push(root);
  mkdirSync(join(root, 'chapter1/section1'), { recursive: true });
  mkdirSync(join(root, 'chapter1/section2'));
  const first = join(root, 'chapter1/section1/README.md');
  const second = join(root, 'chapter1/section2/README.md');
  writeFileSync(first, '# First title\n');
  writeFileSync(second, '# Second title\n');
  const md = createMarkdown({});
  md.use(sectionListPlugin, { docsDir: root, base: '/tutorial/' });
  return { md, first, second };
};
const source = '# Chapter\n\n## 本章各节\n\n<!-- AUTO:SECTION-LIST -->\n';
describe('chapter dependency updates', () => {
  it('matches real watcher paths to section dependencies on this platform', async () => {
    const { md, first } = chapter();
    const env = { filePathRelative: 'chapter1/README.md', importedFiles: [] as string[] };
    md.render(source, env);
    const watcher = watch(env.importedFiles, { ignoreInitial: true });
    try {
      await new Promise<void>((resolve, reject) => {
        watcher.once('ready', resolve);
        watcher.once('error', reject);
      });
      const change = new Promise<string>((resolve) => {
        watcher.once('change', resolve);
      });
      writeFileSync(first, '# Watched title\n');
      const path = await change;
      expect(env.importedFiles).toContain(path);
      expect(md.render(source, env)).toContain('Watched title');
    } finally {
      await watcher.close();
    }
  });

  it('registers section files and re-renders the dependent chapter after a valid title change', () => {
    const { md, first, second } = chapter();
    const env = { filePathRelative: 'chapter1/README.md', importedFiles: [] as string[] };
    const before = md.render(source, env);
    expect(env.importedFiles).toEqual([first, second]);
    writeFileSync(first, '# Updated title\n');
    const affected = env.importedFiles.includes(first);
    expect(affected).toBe(true);
    const after = affected ? md.render(source, env) : before;
    expect(after).toContain('Updated title');
    expect(after).not.toContain('First title');
    expect(after).toContain('/tutorial/chapter1/section1/');
    expect(env.importedFiles).toEqual([first, second]);
  });
  it('tracks an invalid section title so correcting it updates the chapter', () => {
    const { md, first, second } = chapter();
    writeFileSync(second, 'invalid heading\n');
    const env = { filePathRelative: 'chapter1/README.md', importedFiles: [] as string[] };
    expect(md.render(source, env)).not.toContain('Second title');
    expect(env.importedFiles).toContain(second);
    writeFileSync(second, '# Corrected title\n');
    expect(md.render(source, env)).toContain('Corrected title');
    expect(readFileSync(first, 'utf8')).toBe('# First title\n');
  });
  it('preserves dependencies registered by other plugins', () => {
    const { md, first, second } = chapter();
    const env = { filePathRelative: 'chapter1/README.md', importedFiles: ['other.md'] };
    md.render(source, env);
    expect(env.importedFiles).toEqual(['other.md', first, second]);
  });
});
describe('section reference tokens', () => {
  it('links plain references while retaining code, existing links and unknown targets', () => {
    const md = createMarkdown({});
    md.use(spLinkPlugin, { base: '/tutorial/' });
    const html = md.render('[C1-S1] `[C1-S1]` [already](https://example.com/) [C999-S999]\n');
    expect(html).toContain('/tutorial/chapter1/section1/');
    expect(html).toMatch(/<code(?:\s[^>]*)?>\[C1-S1\]<\/code>/);
    expect(html).toContain('href="https://example.com/"');
    expect(html).toContain('[C999-S999]');
  });
});
