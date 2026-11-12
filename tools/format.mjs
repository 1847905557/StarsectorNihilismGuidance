import { extname, join } from 'node:path';
import prettier from 'prettier';
import { atomicWrite, collectFiles, displayPath, isText, readUtf8, repositoryRoot } from './shared/files.mjs';
import { command, diagnostic, result } from './shared/cli.mjs';
import { parseMarkdown } from './shared/markdown.mjs';
import { inspectText, transformTypography } from './check-text.mjs';

export async function formatMarkdown(text, path = 'document.md') {
  const options = await prettier.resolveConfig(join(repositoryRoot, '.prettierrc'));
  return prettier.format(repairFaultSpacing(transformTypography(text.replace(/\r\n?/g, '\n'), path)), {
    ...options,
    filepath: path,
    parser: 'markdown',
    tabWidth: 4,
    embeddedLanguageFormatting: 'off',
  });
}
export function repairFaultSpacing(text) {
  const { lines, hidden } = parseMarkdown(text);
  let active = false;
  const output = [];
  for (let index = 0; index < lines.length; index++) {
    const line = lines[index];
    if (!hidden.has(index) && /^##\s+/.test(line)) active = /^## 故障分析\s*$/.test(line);
    if (active && !hidden.has(index) && /^ {4}- \*\*解决方案\*\*/.test(line)) {
      while (output.length && !output.at(-1).trim()) output.pop();
    } else if (active && !hidden.has(index) && /^- \*\*/.test(line) && /^ {4}- \*\*解决方案\*\*/.test(output.at(-1) || '')) output.push('');
    output.push(line);
  }
  return output.join('\n');
}
export function formatTarget(path, root = repositoryRoot) {
  const rel = displayPath(path, root);
  return (
    extname(path) === '.md' ||
    /^tools\/.*\.mjs$/.test(rel) ||
    /^eslint(?:\.full)?\.config\.mjs$/.test(rel) ||
    /^tsconfig\.[^.]+\.json$/.test(rel) ||
    [
      'package.json',
      '.prettierrc',
      'pnpm-workspace.yaml',
      'docs/.vuepress/plugin-dev-edit.ts',
      'docs/.vuepress/plugin-dev-edit.test.ts',
    ].includes(rel)
  );
}
export async function formatFiles(paths = [], { root = repositoryRoot, write = false } = {}) {
  const scope = await collectFiles(paths, { root, include: isText });
  const files = [];
  let changed = 0,
    written = 0;
  for (const path of scope.files) {
    try {
      const source = await readUtf8(path);
      const options = await prettier.resolveConfig(path);
      const markdown = extname(path) === '.md';
      const normalized = transformTypography(source.text.replace(/\r\n?/g, '\n'), path).replace(/\n*$/, '\n');
      const desired = markdown
        ? await formatMarkdown(source.text, path)
        : formatTarget(path, root)
          ? await prettier.format(normalized, { ...options, filepath: path })
          : normalized;
      const differs = source.bom || desired !== source.text;
      const diagnostics = inspectText(desired, { path });
      if (differs) {
        changed++;
        if (write) {
          await atomicWrite(path, desired);
          written++;
        } else {
          let offset = 0;
          while (offset < source.text.length && source.text[offset] === desired[offset]) offset++;
          diagnostics.push(diagnostic(source.text, offset, 'format', '运行相同范围的 pnpm format 整理格式'));
        }
      }
      files.push({ path: displayPath(path, root), bytes: source.bytes.length, diagnostics });
    } catch (error) {
      scope.errors.push({ path: displayPath(path, root), message: error.message });
    }
  }
  return result(files, scope.errors, { skipped: scope.skipped, changed, written });
}
await command(
  import.meta.url,
  '使用项目文字规则与 Prettier 整理 Markdown、工具和相关配置。',
  (config) => formatFiles(config.paths, { write: config.write }),
  { write: { type: 'boolean' } },
);
