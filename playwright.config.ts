import { defineConfig } from '@playwright/test';
import { collectFiles, displayPath } from './tools/shared/files.mjs';

const scope = await collectFiles(['tests/browser'], { include: (path) => path.endsWith('.spec.ts') });
if (scope.errors.length) throw new Error(scope.errors.map((item) => item.message).join('\n'));
export default defineConfig({
  testDir: '.',
  testMatch: scope.files.map((path) => displayPath(path)),
  testIgnore: [],
  workers: 1,
  timeout: 30_000,
  expect: { timeout: 10_000 },
  use: {
    baseURL: 'http://127.0.0.1:4173/StarsectorNihilismGuidance/',
    browserName: 'chromium',
    trace: 'retain-on-failure',
  },
  webServer: {
    command: 'node tests/browser/site.mjs && pnpm exec vuepress dev .cache/browser-site/docs --host 127.0.0.1 --port 4173',
    url: 'http://127.0.0.1:4173/StarsectorNihilismGuidance/',
    reuseExistingServer: false,
    timeout: 120_000,
  },
});
