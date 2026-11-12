import assert from 'node:assert/strict';
import { test } from 'node:test';
import { options, result, exitCode } from './cli.mjs';

test('CLI options preserve multiple scopes, structured output and explicit writes', () => {
  assert.deepEqual(options(['docs/chapter1', '.zcode', '--json', '--write'], { write: { type: 'boolean' } }), {
    paths: ['docs/chapter1', '.zcode'],
    json: true,
    write: true,
  });
  assert.throws(() => options(['--unknown']), /Unknown option/);
});
test('summary counts diagnostics, byte totals and execution errors consistently', () => {
  const files = [
    {
      path: 'a.md',
      bytes: 10,
      diagnostics: [
        { line: 1, column: 1, rule: 'one', message: 'one' },
        { line: 2, column: 1, rule: 'two', message: 'two' },
      ],
    },
    { path: 'b.md', bytes: 4, diagnostics: [] },
  ];
  const findings = result(files);
  assert.equal(findings.bytes, 14);
  assert.equal(findings.problems, 2);
  assert.equal(findings.problemFiles, 1);
  assert.equal(exitCode(findings), 1);
  assert.equal(exitCode(result([], [{ path: 'missing.md', message: 'missing' }])), 2);
  assert.equal(exitCode(result()), 0);
});
