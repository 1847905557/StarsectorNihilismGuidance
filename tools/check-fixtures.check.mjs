import assert from 'node:assert/strict';
import { mkdtemp, mkdir, writeFile, rm } from 'node:fs/promises';
import { tmpdir } from 'node:os';
import { dirname, join } from 'node:path';
import { test } from 'node:test';
import { checkFixtures, parseUnitLists } from './check-fixtures.mjs';

async function fixture(t) {
  const root = await mkdtemp(join(tmpdir(), 'tutorial-fixture-'));
  t.after(() => rm(root, { recursive: true, force: true }));
  const java = 'package example;\npublic class Sample {\n}\n',
    shader = '#version 110\nvoid main() {\n}\n';
  const paths = {
    'docs/chapter1/section1/README.md': '# 页面\n\n```java\n' + java + '```\n\n```glsl\n' + shader + '```\n',
    'tutorial-fixtures/build.gradle': 'def javaUnitIds = ["C1-S1"]\ndef shaderUnitIds = ["C1-S1"]\n',
    'tutorial-fixtures/units/C1-S1/src/main/java/example/Sample.java': java,
    'tutorial-fixtures/units/C1-S1/src/main/resources/data/shaders/sample.vert': shader,
  };
  for (const [path, text] of Object.entries(paths)) {
    await mkdir(dirname(join(root, path)), { recursive: true });
    await writeFile(join(root, path), text);
  }
  return root;
}
test('complete Java and shader examples match by package, type and exact content', async (t) => {
  const root = await fixture(t),
    summary = await checkFixtures({ root });
  assert.equal(summary.problems, 0);
  assert.deepEqual(summary.errors, []);
  assert.deepEqual(summary.statistics, {
    pages: 1,
    fixtureUnits: 1,
    javaFiles: 1,
    shaderFiles: 1,
    completeJavaBlocks: 1,
    matchedJavaBlocks: 1,
    completeShaderBlocks: 1,
    matchedShaderBlocks: 1,
  });
});
for (const [name, relative, replacement, rule] of [
  [
    'Java mismatch',
    'tutorial-fixtures/units/C1-S1/src/main/java/example/Sample.java',
    'package example;\npublic class Sample { int value; }\n',
    'java-content-mismatch',
  ],
  [
    'shader mismatch',
    'tutorial-fixtures/units/C1-S1/src/main/resources/data/shaders/sample.vert',
    '#version 110\nvoid main() { }\n',
    'missing-or-mismatched-shader',
  ],
  ['invalid unit', 'tutorial-fixtures/build.gradle', 'def javaUnitIds = ["C01-S1", "C01-S1"]\ndef shaderUnitIds = []\n', 'invalid-unit-id'],
  ['missing list', 'tutorial-fixtures/build.gradle', 'def javaUnitIds = []\n', 'missing-build-list'],
])
  test(name, async (t) => {
    const root = await fixture(t);
    await writeFile(join(root, relative), replacement);
    const summary = await checkFixtures({ root });
    assert.ok(
      summary.files.some((file) => file.diagnostics.some((item) => item.rule === rule)),
      JSON.stringify(summary),
    );
  });
test('Gradle comments preserve string identities and empty arrays', () => {
  const { lists, diagnostics } = parseUnitLists(
    '/* def javaUnitIds = ["C9-S9"] */\ndef javaUnitIds = ["C1-S1", // "C2-S2"\n]\ndef shaderUnitIds = []\n',
  );
  assert.deepEqual(lists.get('javaUnitIds'), ['C1-S1']);
  assert.deepEqual(lists.get('shaderUnitIds'), []);
  assert.deepEqual(diagnostics, []);
});
test('unreadable pages report errors while other content remains checked', async (t) => {
  const root = await fixture(t);
  await mkdir(join(root, 'docs/chapter2/section1'), { recursive: true });
  await writeFile(join(root, 'docs/chapter2/section1/README.md'), Buffer.from([0xff]));
  const summary = await checkFixtures({ root });
  assert.equal(summary.statistics.matchedJavaBlocks, 1);
  assert.equal(summary.errors.length, 1);
});
