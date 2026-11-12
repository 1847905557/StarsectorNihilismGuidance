import assert from 'node:assert/strict';
import { mkdtemp, mkdir, writeFile, readFile, rm, symlink, readdir, stat } from 'node:fs/promises';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import { test } from 'node:test';
import { atomicWrite, collectFiles, inside, readUtf8 } from './files.mjs';

async function workspace(t) {
  const root = await mkdtemp(join(tmpdir(), 'tutorial-files-'));
  t.after(() => rm(root, { recursive: true, force: true }));
  return root;
}
test('collection follows nested ignore rules, explicit files, duplicates and path boundaries', async (t) => {
  const root = await workspace(t);
  await mkdir(join(root, 'nested'));
  await writeFile(join(root, '.gitignore'), '*.log\n!keep.log\ncache/\n');
  await writeFile(join(root, 'nested/.gitignore'), '!take.log\n');
  for (const name of ['a.md', 'skip.log', 'keep.log', 'nested/take.log']) await writeFile(join(root, name), 'value\n');
  const found = await collectFiles([], { root });
  assert.deepEqual(found.files.map((path) => path.slice(root.length + 1).replaceAll('\\', '/')).sort(), [
    '.gitignore',
    'a.md',
    'keep.log',
    'nested/.gitignore',
    'nested/take.log',
  ]);
  assert.equal((await collectFiles([join(root, 'a.md'), join(root, 'a.md')], { root })).files.length, 1);
  assert.equal((await collectFiles([join(root, 'skip.log')], { root })).files.length, 0);
  assert.equal((await collectFiles([join(root, 'missing')], { root })).errors.length, 1);
  assert.equal(inside(root, root + '-other/file'), false);
  assert.equal(inside(root, join(root, 'nested')), true);
});
test('directory junctions and files below linked ancestors stay outside collection', async (t) => {
  const root = await workspace(t);
  const target = join(root, 'target'),
    link = join(root, 'link');
  await mkdir(target);
  await writeFile(join(target, 'page.md'), '# Page\n');
  await symlink(target, link, process.platform === 'win32' ? 'junction' : 'dir');
  const direct = await collectFiles([link, join(link, 'page.md')], { root });
  assert.equal(direct.files.length, 0);
  assert.equal(direct.skipped, 2);
});
test('UTF-8 reader reports BOM and rejects invalid byte sequences', async (t) => {
  const root = await workspace(t),
    path = join(root, 'text.md');
  await writeFile(path, Buffer.from([0xef, 0xbb, 0xbf, 0x61]));
  assert.deepEqual((await readUtf8(path)).text, 'a');
  assert.equal((await readUtf8(path)).bom, true);
  await writeFile(path, Buffer.from([0xff]));
  await assert.rejects(readUtf8(path), /encoded data/);
});
test('atomic writes preserve mode and release temporary files on success and rename failure', async (t) => {
  const root = await workspace(t),
    path = join(root, 'page.md');
  await writeFile(path, 'old\n');
  const mode = (await stat(path)).mode;
  await atomicWrite(path, 'new\n');
  assert.equal(await readFile(path, 'utf8'), 'new\n');
  assert.equal((await stat(path)).mode, mode);
  const directory = join(root, 'directory');
  await mkdir(directory);
  await assert.rejects(atomicWrite(directory, 'data'));
  assert.deepEqual((await readdir(root)).sort(), ['directory', 'page.md']);
});
