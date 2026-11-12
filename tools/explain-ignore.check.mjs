import assert from 'node:assert/strict';
import { spawnSync } from 'node:child_process';
import { mkdtemp, mkdir, writeFile, rm } from 'node:fs/promises';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import { test } from 'node:test';
import { explainIgnore } from './explain-ignore.mjs';
import { repositoryRoot } from './shared/files.mjs';

test('ignore interface reports nested patterns and includes negative exceptions', async (t) => {
  const root = await mkdtemp(join(tmpdir(), 'tutorial-ignore-'));
  t.after(() => rm(root, { recursive: true, force: true }));
  await mkdir(join(root, 'nested'));
  await writeFile(join(root, '.gitignore'), '*.log\n');
  await writeFile(join(root, 'nested/.gitignore'), '!keep.log\nsecret.txt\n');
  const result = await explainIgnore([join(root, 'one.log'), join(root, 'nested/keep.log'), join(root, 'nested/secret.txt')], { root });
  assert.deepEqual(result, {
    checked: 3,
    ignored: 2,
    files: [
      { path: 'one.log', ignored: true, source: '.gitignore', line: 1, pattern: '*.log' },
      { path: 'nested/keep.log', ignored: false },
      { path: 'nested/secret.txt', ignored: true, source: 'nested/.gitignore', line: 2, pattern: 'secret.txt' },
    ],
  });
});

test('ignore CLI emits readable and JSON results and rejects missing or unknown arguments', () => {
  const run = (/** @type {string[]} */ args) =>
    spawnSync(process.execPath, ['tools/explain-ignore.mjs', ...args], { cwd: repositoryRoot, encoding: 'utf8', windowsHide: true });
  const paths = ['.cache/browser-site/docs/README.md', 'package.json'];
  const readable = run(paths);
  assert.equal(readable.status, 0, readable.stderr);
  assert.match(readable.stdout, /查询=2 忽略=1 包含=1/);
  assert.match(readable.stdout, /\.gitignore:\d+ \/\.cache\//);
  const json = run([...paths, '--json']);
  assert.equal(json.status, 0, json.stderr);
  const summary = JSON.parse(json.stdout);
  assert.equal(summary.files[0].pattern, '/.cache/');
  assert.equal(summary.files[1].ignored, false);
  assert.equal(run(['--help']).status, 0);
  assert.equal(run([]).status, 2);
  assert.equal(run(['--unknown']).status, 2);
});
