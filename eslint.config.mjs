import globals from 'globals';
import js from '@eslint/js';
import pluginVue from 'eslint-plugin-vue';
import tseslint from 'typescript-eslint';
import { gitIgnored } from './tools/shared/files.mjs';

const fastConfig = [
  {
    ignores: [await gitIgnored()],
  },
  js.configs.recommended,
  ...tseslint.configs.recommended,
  ...pluginVue.configs['flat/recommended'],
  {
    files: ['**/*.vue', '**/*.ts'],
    languageOptions: { parserOptions: { parser: tseslint.parser } },
    rules: {
      'vue/html-self-closing': 'off',
      'vue/html-closing-bracket-newline': 'off',
      'vue/html-indent': 'off',
      'vue/max-attributes-per-line': 'off',
      'vue/multiline-html-element-content-newline': 'off',
      'vue/multi-word-component-names': 'off',
      'vue/singleline-html-element-content-newline': 'off',
      '@typescript-eslint/no-unused-expressions': ['error', { allowShortCircuit: true, allowTernary: true }],
      'vue/attributes-order': 'off',
      'vue/html-quotes': 'off',
      'vue/mustache-interpolation-spacing': 'off',
      'vue/no-multi-spaces': 'off',
    },
  },
  {
    files: ['docs/.vuepress/client/**/*.ts', '**/*.vue'],
    languageOptions: { globals: globals.browser },
  },
  {
    files: ['docs/.vuepress/*.ts', 'tools/**/*.mjs', 'tests/**/*.mjs', 'tests/**/*.ts', '*.config.ts', 'eslint*.config.mjs'],
    languageOptions: { globals: globals.node },
  },
  {
    files: ['docs/.vuepress/**/*.test.ts'],
    languageOptions: { globals: { ...globals.browser, ...globals.node } },
    rules: { 'vue/one-component-per-file': 'off' },
  },
];

export const fullConfig = [
  ...fastConfig,
  {
    files: ['**/*.ts', '**/*.vue', '**/*.mjs'],
    languageOptions: {
      parserOptions: {
        project: ['./tsconfig.client.json', './tsconfig.node.json', './tsconfig.tools.json', './tsconfig.test.json'],
        tsconfigRootDir: import.meta.dirname,
        extraFileExtensions: ['.vue'],
      },
    },
    rules: {
      '@typescript-eslint/no-floating-promises': [
        'error',
        { allowForKnownSafeCalls: [{ from: 'package', package: '@types/node', name: 'test' }] },
      ],
      '@typescript-eslint/no-misused-promises': 'error',
      '@typescript-eslint/no-unnecessary-condition': ['error', { allowConstantLoopConditions: 'only-allowed-literals' }],
      '@typescript-eslint/no-unnecessary-type-assertion': 'error',
      '@typescript-eslint/await-thenable': ['error', { allowMixedPromiseArrays: true }],
      '@typescript-eslint/return-await': ['error', 'error-handling-correctness-only'],
      '@typescript-eslint/switch-exhaustiveness-check': ['error', { considerDefaultExhaustiveForUnions: true }],
    },
  },
];
export default fastConfig;
