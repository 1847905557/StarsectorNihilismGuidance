import { readdir } from 'node:fs/promises';
import { join, resolve } from 'node:path';
import { collectFiles, displayPath, readUtf8, repositoryRoot } from './shared/files.mjs';
import { command, result } from './shared/cli.mjs';
import { codeContent, parseMarkdown } from './shared/markdown.mjs';

const unitId = /^C([1-9]\d*)-S([1-9]\d*)$/;
const pagePath = /^docs\/chapter([1-9]\d*)\/section([1-9]\d*)\/README\.md$/;
const packageName = /^package\s+([A-Za-z_$][\w$]*(?:\.[A-Za-z_$][\w$]*)*)\s*;/m;
const typeName =
  /^(?:(?:public|protected|private|abstract|final|sealed|non-sealed|strictfp)\s+)*(?:class|interface|enum|record)\s+([A-Za-z_$][\w$]*)\b/m;

export function parseUnitLists(text) {
  const visible = text.replace(/"(?:\\.|[^"\\])*"|'(?:\\.|[^'\\])*'|\/\/[^\n]*|\/\*[\s\S]*?\*\//g, (token) =>
    /^(?:\/\/|\/\*)/.test(token) ? token.replace(/[^\n]/g, ' ') : token,
  );
  const lists = new Map();
  const diagnostics = [];
  for (const match of visible.matchAll(/^def\s+(javaUnitIds|shaderUnitIds)\s*=\s*\[([\s\S]*?)\]/gm)) {
    if (lists.has(match[1])) diagnostics.push({ rule: 'duplicate-build-list', message: '重复定义 ' + match[1] });
    lists.set(
      match[1],
      [...match[2].matchAll(/["']([^"']+)["']/g)].map((item) => item[1]),
    );
  }
  for (const name of ['javaUnitIds', 'shaderUnitIds']) {
    if (!lists.has(name)) {
      diagnostics.push({ rule: 'missing-build-list', message: '缺少 ' + name });
      lists.set(name, []);
    }
  }
  return { lists, diagnostics };
}

export async function checkFixtures({ root = repositoryRoot } = {}) {
  root = resolve(root);
  const scope = await collectFiles(
    [join(root, 'docs'), join(root, 'tutorial-fixtures', 'units'), join(root, 'tutorial-fixtures', 'build.gradle')],
    {
      root,
      include: (path) => /(?:README\.md|\.java|\.vert|\.frag|build\.gradle)$/.test(path),
    },
  );
  const records = new Map();
  const sourceText = new Map();
  for (const path of scope.files) {
    try {
      const source = await readUtf8(path);
      const rel = displayPath(path, root);
      sourceText.set(rel, source.text);
      records.set(rel, {
        path: rel,
        bytes: source.bytes.length,
        diagnostics: source.bom ? [{ line: 1, column: 1, rule: 'encoding-bom', message: '源码使用 UTF-8 无 BOM' }] : [],
      });
    } catch (error) {
      scope.errors.push({ path: displayPath(path, root), message: error.message });
    }
  }
  function add(path, rule, message, line = 1) {
    if (!records.has(path)) records.set(path, { path, bytes: 0, diagnostics: [] });
    records.get(path).diagnostics.push({ line, column: 1, rule, message });
  }
  const build = 'tutorial-fixtures/build.gradle';
  const { lists, diagnostics } = parseUnitLists(sourceText.get(build) || '');
  for (const item of diagnostics) add(build, item.rule, item.message);
  const dirs = await readdir(join(root, 'tutorial-fixtures', 'units'), { withFileTypes: true }).catch((error) => {
    scope.errors.push({ path: 'tutorial-fixtures/units', message: error.message });
    return [];
  });
  const units = new Set(dirs.filter((entry) => entry.isDirectory() && !entry.isSymbolicLink()).map((entry) => entry.name));
  const pages = new Map(),
    javaBlocks = [],
    shaderBlocks = new Map();
  for (const [path, text] of sourceText) {
    const match = pagePath.exec(path);
    if (!match) continue;
    const id = 'C' + Number(match[1]) + '-S' + Number(match[2]);
    pages.set(id, path);
    for (const block of parseMarkdown(text).fences) {
      const language = block.info?.split(/\s/)[0].toLowerCase();
      const content = codeContent(block.content);
      if (language === 'java') {
        const pkg = packageName.exec(content),
          type = typeName.exec(content);
        if (pkg && type)
          javaBlocks.push({
            id,
            page: path,
            line: block.map[0] + 1,
            content,
            source: 'tutorial-fixtures/units/' + id + '/src/main/java/' + pkg[1].replaceAll('.', '/') + '/' + type[1] + '.java',
          });
      } else if (['glsl', 'vert', 'frag'].includes(language) && content.trimStart().startsWith('#version')) {
        if (!shaderBlocks.has(id)) shaderBlocks.set(id, []);
        shaderBlocks.get(id).push({ page: path, content, line: block.map[0] + 1 });
      }
    }
  }
  const javaIds = new Set(lists.get('javaUnitIds')),
    shaderIds = new Set(lists.get('shaderUnitIds'));
  for (const [name, values] of lists) {
    const seen = new Set();
    for (const id of values) {
      if (seen.has(id)) add(build, 'duplicate-unit-id', name + ' 重复列出 ' + id);
      seen.add(id);
      if (!unitId.test(id)) add(build, 'invalid-unit-id', name + ' 包含非法编号 ' + id);
      if (!units.has(id)) add(build, 'listed-unit-missing-directory', id + ' 没有 fixture 目录');
    }
  }
  for (const id of units) {
    const path = 'tutorial-fixtures/units/' + id;
    if (!unitId.test(id)) add(path, 'invalid-fixture-id', 'fixture 目录不是正整数编号');
    if (!pages.has(id)) add(path, 'orphan-fixture', id + ' 没有对应 section 页面');
    if (!javaIds.has(id)) add(path, 'fixture-not-listed', id + ' fixture 不在 javaUnitIds');
  }
  const pageJavaIds = new Set(javaBlocks.map((block) => block.id));
  for (const id of pageJavaIds)
    if (!javaIds.has(id)) add(pages.get(id), 'page-java-not-listed', id + ' 有完整 Java 示例但不在 javaUnitIds');
  for (const id of javaIds)
    if (!pageJavaIds.has(id)) add(build, 'listed-java-without-page-code', id + ' 在 javaUnitIds 中但页面没有完整 Java 示例');
  const expected = new Set();
  let matchedJava = 0,
    matchedShader = 0;
  for (const block of javaBlocks) {
    if (expected.has(block.source)) {
      add(block.page, 'duplicate-page-java', '完整类型重复出现', block.line);
      continue;
    }
    expected.add(block.source);
    if (!sourceText.has(block.source)) add(block.page, 'missing-fixture-java', '缺少 ' + block.source, block.line);
    else if (codeContent(sourceText.get(block.source)) !== block.content)
      add(block.source, 'java-content-mismatch', '与 ' + block.page + ':' + block.line + ' 不一致');
    else matchedJava++;
  }
  const javaFiles = [...sourceText.keys()].filter((path) => /^tutorial-fixtures\/units\/[^/]+\/src\/main\/java\/.*\.java$/.test(path));
  const shaderFiles = [...sourceText.keys()].filter((path) =>
    /^tutorial-fixtures\/units\/[^/]+\/src\/main\/resources\/.*\.(vert|frag)$/.test(path),
  );
  for (const path of javaFiles) if (!expected.has(path)) add(path, 'fixture-java-without-page-code', '源码未出现在对应页面');
  const resourceIds = new Set(shaderFiles.map((path) => path.split('/')[2]));
  for (const id of shaderBlocks.keys())
    if (!shaderIds.has(id)) add(pages.get(id), 'page-shader-not-listed', id + ' 有完整 shader 但不在 shaderUnitIds');
  for (const id of shaderIds) {
    if (!shaderBlocks.has(id)) add(build, 'listed-shader-without-page-code', id + ' 在 shaderUnitIds 中但页面没有完整 shader');
    if (!resourceIds.has(id)) add(build, 'listed-shader-without-resource', id + ' 没有 .vert 或 .frag 资源');
  }
  for (const id of resourceIds)
    if (!shaderIds.has(id)) add('tutorial-fixtures/units/' + id, 'shader-fixture-not-listed', id + ' shader 资源不在 shaderUnitIds');
  const used = new Set();
  for (const [id, blocks] of shaderBlocks)
    for (const block of blocks) {
      const candidate = shaderFiles.find(
        (path) => path.split('/')[2] === id && !used.has(path) && codeContent(sourceText.get(path)) === block.content,
      );
      if (!candidate) add(block.page, 'missing-or-mismatched-shader', id + ' 的完整 shader 没有逐字一致的资源', block.line);
      else {
        used.add(candidate);
        matchedShader++;
      }
    }
  for (const path of shaderFiles) if (!used.has(path)) add(path, 'fixture-shader-without-page-code', 'shader 资源未在对应页面逐字出现');
  const statistics = {
    pages: pages.size,
    fixtureUnits: units.size,
    javaFiles: javaFiles.length,
    shaderFiles: shaderFiles.length,
    completeJavaBlocks: javaBlocks.length,
    matchedJavaBlocks: matchedJava,
    completeShaderBlocks: [...shaderBlocks.values()].reduce((sum, blocks) => sum + blocks.length, 0),
    matchedShaderBlocks: matchedShader,
  };
  return result(
    [...records.values()].sort((a, b) => a.path.localeCompare(b.path)),
    scope.errors,
    { skipped: scope.skipped, statistics },
  );
}
await command(
  import.meta.url,
  '检查教程完整源码、fixture 与 Gradle 清单的双向对应。',
  (config) => checkFixtures({ root: config['repo-root'] }),
  { 'repo-root': { type: 'string' } },
);
