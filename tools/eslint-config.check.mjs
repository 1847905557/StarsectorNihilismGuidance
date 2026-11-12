import assert from 'node:assert/strict';
import { join, resolve } from 'node:path';
import { test } from 'node:test';
import { ESLint } from 'eslint';
import ts from 'typescript';
import { repositoryRoot } from './shared/files.mjs';

const fullRules = [
  '@typescript-eslint/no-floating-promises',
  '@typescript-eslint/no-misused-promises',
  '@typescript-eslint/no-unnecessary-condition',
  '@typescript-eslint/no-unnecessary-type-assertion',
  '@typescript-eslint/await-thenable',
  '@typescript-eslint/return-await',
  '@typescript-eslint/switch-exhaustiveness-check',
];
const fast = new ESLint({ cwd: repositoryRoot });
const full = new ESLint({ cwd: repositoryRoot, overrideConfigFile: join(repositoryRoot, 'eslint.full.config.mjs') });

test('both ESLint tiers cover browser, Node, tools, configuration and tests with actual environments', async () => {
  for (const path of [
    'docs/.vuepress/client/dev-edit-client.ts',
    'docs/.vuepress/config.ts',
    'docs/.vuepress/plugin-dev-edit.test.ts',
    'tools/check-text.mjs',
    'eslint.config.mjs',
  ]) {
    const file = join(repositoryRoot, path);
    const ordinary = await fast.calculateConfigForFile(file);
    const complete = await full.calculateConfigForFile(file);
    assert.equal(ordinary.rules['@typescript-eslint/no-explicit-any'][0], 2);
    for (const rule of fullRules) assert.equal(complete.rules[rule][0], 2);
    assert.ok(complete.languageOptions.parserOptions.project.length);
  }
  const client = await fast.calculateConfigForFile(join(repositoryRoot, 'docs/.vuepress/client/dev-edit-client.ts'));
  const node = await fast.calculateConfigForFile(join(repositoryRoot, 'tools/check-text.mjs'));
  assert.ok(Object.hasOwn(client.languageOptions.globals, 'window'));
  assert.equal(Object.hasOwn(client.languageOptions.globals, 'process'), false);
  assert.ok(Object.hasOwn(node.languageOptions.globals, 'process'));
  assert.equal(Object.hasOwn(node.languageOptions.globals, 'window'), false);
  assert.equal(await full.isPathIgnored(join(repositoryRoot, '.zcode/plans/example.ts')), true);
  assert.equal(await full.isPathIgnored(join(repositoryRoot, 'docs/.vuepress/.temp/example.ts')), true);
  assert.equal(await full.isPathIgnored(join(repositoryRoot, 'docs/.vuepress/config.ts.12345678.mjs')), true);
});

const cases = [
  ['redundant fallback', 'const value: string = "text";\nconsole.log(value ?? "fallback");', '@typescript-eslint/no-unnecessary-condition'],
  [
    'redundant assertion',
    'const value: string = "text";\nconsole.log(value as string);',
    '@typescript-eslint/no-unnecessary-type-assertion',
  ],
  ['unhandled Promise', 'async function load() { return 1; }\nload();', '@typescript-eslint/no-floating-promises'],
  [
    'async callback in a synchronous API',
    'function consume(callback: () => void) { callback(); }\nconsume(async () => { await Promise.resolve(); });',
    '@typescript-eslint/no-misused-promises',
  ],
  ['await on an ordinary value', 'async function load() { await 1; }\nvoid load();', '@typescript-eslint/await-thenable'],
  [
    'return through a try block',
    'async function load() { try { return Promise.resolve(1); } catch { return 0; } }\nvoid load();',
    '@typescript-eslint/return-await',
  ],
  [
    'missing union branch',
    'function select(value: "one" | "two") { switch (value) { case "one": return 1; } }\nselect("one");',
    '@typescript-eslint/switch-exhaustiveness-check',
  ],
];
for (const [name, source, rule] of cases) {
  test('full ESLint diagnoses ' + name + ' with type information', async () => {
    const path = join(repositoryRoot, 'docs/.vuepress/client/eslint-probe.ts');
    const compilerOptions = {
      target: ts.ScriptTarget.ES2023,
      module: ts.ModuleKind.ESNext,
      moduleResolution: ts.ModuleResolutionKind.Bundler,
      strict: true,
      noEmit: true,
      types: ['node'],
    };
    const host = ts.createCompilerHost(compilerOptions);
    const original = host.getSourceFile.bind(host);
    host.getSourceFile = (file, ...args) =>
      resolve(file) === resolve(path)
        ? ts.createSourceFile(path, /** @type {string} */ (source), ts.ScriptTarget.ES2023, true)
        : original(file, ...args);
    const program = ts.createProgram([path], compilerOptions, host);
    const checker = new ESLint({
      cwd: repositoryRoot,
      overrideConfigFile: join(repositoryRoot, 'eslint.full.config.mjs'),
      overrideConfig: { files: ['**/*.ts'], languageOptions: { parserOptions: { project: false, programs: [program] } } },
    });
    const ordinary = await fast.lintText(/** @type {string} */ (source), { filePath: path });
    const complete = await checker.lintText(/** @type {string} */ (source), { filePath: path });
    assert.equal(ordinary.flatMap((result) => result.messages).filter((message) => message.ruleId === rule).length, 0);
    assert.equal(
      complete.flatMap((result) => result.messages).filter((message) => message.ruleId === rule).length,
      1,
      JSON.stringify(complete.map((result) => result.messages)),
    );
  });
}
