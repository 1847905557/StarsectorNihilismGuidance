import { defineConfig } from 'vitest/config';
import { collectFiles, displayPath } from './tools/shared/files.mjs';

const scope = await collectFiles(['docs/.vuepress'], { include: (path) => path.endsWith('.test.ts') });
if (scope.errors.length) throw new Error(scope.errors.map((item) => item.message).join('\n'));

export default defineConfig({
  test: {
    include: scope.files.map((path) => displayPath(path)),
    exclude: [],
    environment: 'node',
    testTimeout: 10_000,
    hookTimeout: 10_000,
    clearMocks: true,
    restoreMocks: true,
  },
});
