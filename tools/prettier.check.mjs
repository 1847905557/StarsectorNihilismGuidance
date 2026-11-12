import assert from 'node:assert/strict';
import { join } from 'node:path';
import { test } from 'node:test';
import { repositoryRoot } from './shared/files.mjs';

test('native Prettier discovers engineering files and preserves repository content exclusions', async () => {
  const { default: prettier } = await import('prettier');
  const options = { ignorePath: [join(repositoryRoot, '.gitignore'), join(repositoryRoot, '.prettierignore')] };
  for (const path of [
    'README.md',
    'docs/chapter1/README.md',
    '.zcode/workflow.md',
    'pnpm-lock.yaml',
    'tutorial-fixtures/units/C17-S3/src/main/resources/data/strings/strings.json',
    '.cache/browser-site/docs/README.md',
  ]) {
    assert.equal((await prettier.getFileInfo(join(repositoryRoot, path), options)).ignored, true, path);
  }
  for (const path of ['tools/restore-markdown.mjs', 'docs/.vuepress/plugin-dev-edit.ts', 'package.json', 'tsconfig.base.json']) {
    const info = await prettier.getFileInfo(join(repositoryRoot, path), options);
    assert.equal(info.ignored, false, path);
    assert.ok(info.inferredParser, path);
  }
});
