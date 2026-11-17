import { basename, dirname, extname, join, resolve } from 'node:path';
import { access, readdir } from 'node:fs/promises';
import { collectFiles, displayPath, inside, readUtf8, repositoryRoot } from './shared/files.mjs';
import { command, result } from './shared/cli.mjs';
import { headings, inlineTokens, parseMarkdown } from './shared/markdown.mjs';

export const guidanceFiles = [
  'overview.md',
  'workflow.md',
  'writing-guidelines.md',
  'text-guidelines.md',
  'technical-guidelines.md',
  'frontend-guidelines.md',
  'css-guidelines.md',
  'terminology.md',
];
const fixedHeadings = ['前置知识', '常见问题', '建议参考项'];
const removedHeadings = ['学习目标', '总结'];
const timeLines = [
  ['阅读时间', /^_本节预期阅读时间：\\~\d+ 分钟。_$/],
  ['操作时间', /^_本节预期操作时间：\\~\d+ 分钟。_$/],
];
const baseline =
  /^本章技术基线：Starsector `[^`\s]+`(?: 与 Java [1-9]\d*|，Java [1-9]\d*)?(?:；(?:前置知识|依赖基线|可选依赖基线|前置依赖)：\S.+)?。$/;
const imageName = /^[A-Za-z0-9][A-Za-z0-9._-]*\.(?:gif|jpe?g|png|svg|webp)$/i;
const orderedHeading =
  /^(?:第[一二三四五六七八九十百]+(?:步|部分|阶段|项|段)|链路[一二三四五六七八九十百]+[：:]|(?:片段|步骤)\s*(?:\d+|[一二三四五六七八九十百]+)|\d+[.、)]|[（(]\d+[）)]|[①②③④⑤⑥⑦⑧⑨⑩]|[IVXLCDM]+[.、)])/i;
const placeholder = '<!-- AUTO:SECTION-LIST -->';
/** @param {string} path */
async function exists(path) {
  try {
    await access(path);
    return true;
  } catch (error) {
    if (/** @type {NodeJS.ErrnoException} */ (error).code === 'ENOENT') return false;
    throw error;
  }
}
/** @param {string} path @param {string} value */
function relativeLink(path, value) {
  if (/^(?:[a-z][\w+.-]*:|\/|#)/i.test(value)) return null;
  return resolve(dirname(path), decodeURIComponent(value.split(/[?#]/)[0] ?? ''));
}
/** @param {{line: number, level: number}[]} items @param {{line: number, level: number}} current @param {number} count */
function sectionEnd(items, current, count) {
  return items.find((heading) => heading.line > current.line && heading.level <= current.level)?.line ?? count;
}
/** @param {string} value */
function stripInlineCode(value) {
  return value.replace(/(`+)[\s\S]*?\1/g, (match) => ' '.repeat(match.length));
}

/** @param {string} path @param {string} text @param {{root?: string}} config @returns {Promise<import('./shared/cli.mjs').Diagnostic[]>} */
export async function inspectDocument(path, text, { root = repositoryRoot } = {}) {
  const document = parseMarkdown(text);
  const { lines, tokens, hidden, fences } = document;
  const titles = headings(tokens);
  /** @type {import('./shared/cli.mjs').Diagnostic[]} */
  const diagnostics = [];
  /** @param {number} line @param {string} rule @param {string} message @param {number} column */
  const add = (line, rule, message, column = 1) => diagnostics.push({ line: line + 1, column, rule, message });
  const h1 = titles.filter((heading) => heading.level === 1);
  if (h1.length !== 1) add(0, 'document-title', '文档必须包含唯一 H1；当前 ' + h1.length + ' 个');
  const first = lines.findIndex((line) => line.trim());
  if (h1.length && /** @type {{ line: number; level: number; title: string; }} */ (h1[0]).line !== first)
    add(/** @type {{ line: number; level: number; title: string; }} */ (h1[0]).line, 'document-title', '文档首个非空行必须是 H1');
  for (const heading of titles) if (heading.level > 3) add(heading.line, 'heading-level', '标题只使用 H1、H2 和 H3');
  for (const token of fences.filter((item) => item.type === 'fence')) {
    const last = /** @type {string} */ (lines[/** @type {[number, number]} */ (token.map)[1] - 1]);
    if (!new RegExp('^\\s*' + token.markup[0] + '{' + token.markup.length + ',}\\s*$').test(last)) {
      add(/** @type {[number, number]} */ (token.map)[0], 'fence-close', '代码围栏未闭合');
    }
  }

  const tutorial = inside(join(root, 'docs'), path);
  const rel = displayPath(path, join(root, 'docs'));
  const chapter = tutorial && /^chapter[1-9]\d*\/README\.md$/.test(rel);
  const section = tutorial && /^chapter[1-9]\d*\/section[1-9]\d*\/README\.md$/.test(rel);
  const containers = [];
  for (let line = 0; line < lines.length; line++) {
    if (hidden.has(line)) continue;
    const value = /** @type {string} */ (lines[line]).trim();
    const container = /^(:::+)(.*)$/.exec(value);
    if (container) {
      if (/** @type {string} */ (container[2]).trim()) {
        containers.push(line);
        if (/** @type {string} */ (container[2]).trim().split(/\s+/).length < 2) add(line, 'container-title', 'VuePress 容器缺少标题');
      } else if (containers.length) containers.pop();
      else add(line, 'container-close', 'VuePress 容器结束标记没有对应开始标记');
    }
    if (tutorial) {
      const searchable = stripInlineCode(value);
      for (const match of searchable.matchAll(/C(\d+)-S(\d+)/g)) {
        const before = searchable.slice(0, match.index),
          after = searchable.slice(match.index + match[0].length);
        if (!before.endsWith('[') || !after.startsWith(']') || before.endsWith('[[') || after.startsWith(']]')) {
          add(line, 'section-reference', 'Section 编号必须使用一层方括号：' + match[0]);
        } else if (after.startsWith('](')) add(line, 'section-reference', 'Section 编号由插件生成目标链接：' + match[0]);
        else if (/^0/.test(/** @type {string} */ (match[1])) || /^0/.test(/** @type {string} */ (match[2])))
          add(line, 'section-reference', 'Section 编号使用无前导零的正整数');
        else if (!(await exists(join(root, 'docs', 'chapter' + match[1], 'section' + match[2], 'README.md')))) {
          add(line, 'section-reference', 'Section 编号目标不存在：' + match[0]);
        }
      }
    }
  }
  for (const line of containers) add(line, 'container-close', 'VuePress 容器未闭合');
  for (const parent of tokens.filter((token) => token.type === 'inline')) {
    for (const token of inlineTokens(parent)) {
      const target = token.type === 'image' ? token.attrGet('src') : token.type === 'link_open' ? token.attrGet('href') : null;
      if (!target) continue;
      const local = relativeLink(path, String(target));
      if (local && !(await exists(local)))
        add(/** @type {[number, number]} */ (parent.map)[0], 'relative-link', '相对目标不存在：' + target);
      if (tutorial && token.type === 'image') {
        const title = String(token.attrGet('title') || '');
        const name = decodeURIComponent(
          /** @type {string} */ (new URL(String(target), 'https://local.invalid/').pathname.split('/').at(-1)),
        );
        if (!token.content.trim()) add(/** @type {[number, number]} */ (parent.map)[0], 'image-alt', '图片 alt 必须为非空语义描述');
        if (!imageName.test(title) || title.includes('..'))
          add(/** @type {[number, number]} */ (parent.map)[0], 'image-title', '图片 title 必须写合法的原始文件名');
        else {
          if (name !== title) add(/** @type {[number, number]} */ (parent.map)[0], 'image-title', '图片 title 与目标文件名不一致');
          if (local && target !== './' + title)
            add(/** @type {[number, number]} */ (parent.map)[0], 'image-target', '本地图片目标必须写成 ./' + title);
        }
      }
    }
  }

  if (chapter) {
    const content = lines.map((value, line) => ({ value, line })).filter(({ value, line }) => value.trim() && !hidden.has(line));
    const baselines = content.filter(({ value }) => value.startsWith('本章技术基线'));
    if (baselines.length > 1) add(0, 'chapter-baseline', '章页最多包含一个本章技术基线段落；当前 ' + baselines.length + ' 个');
    else if (baselines.length === 1) {
      const item = /** @type {{ value: string; line: number; }} */ (baselines[0]);
      if (!baseline.test(item.value)) add(item.line, 'chapter-baseline', '本章技术基线格式错误');
      if (content[1] !== item) add(item.line, 'chapter-baseline', '本章技术基线必须紧接 H1');
      if (lines[item.line - 1]?.trim() || lines[item.line + 1]?.trim()) add(item.line, 'chapter-baseline', '本章技术基线必须独占普通段落');
    }
    const catalogs = titles.filter((item) => item.title === '本章各节');
    const markers = content.filter(({ value }) => value === placeholder);
    if (catalogs.length !== 1 || /** @type {{ line: number; level: number; title: string; }} */ (catalogs[0]).level !== 2)
      add(0, 'chapter-catalog', '章页必须包含一个 H2 本章各节标题');
    if (markers.length !== 1) add(0, 'chapter-catalog', '章页必须包含一个自动列表占位符');
    if (catalogs.length === 1 && markers.length === 1) {
      if (content.at(-1) !== markers[0] || content.at(-2)?.value !== '## 本章各节')
        add(
          /** @type {{ value: string; line: number; }} */ (markers[0]).line,
          'chapter-catalog',
          '章页必须以本章各节和独立自动列表占位符结束',
        );
      const start = baselines.length === 1 ? /** @type {{ value: string; line: number; }} */ (baselines[0]).line : (h1[0]?.line ?? 0);
      if (
        !content.some(
          (item) => item.line > start && item.line < /** @type {{ line: number; level: number; title: string; }} */ (catalogs[0]).line,
        )
      )
        add(0, 'chapter-introduction', '章页必须提供章介绍');
    }
  }
  if (tutorial) {
    for (const heading of titles) {
      if (orderedHeading.test(heading.title)) add(heading.line, 'heading-number', '标题使用主题描述');
      if (section && fixedHeadings.includes(heading.title) && heading.level !== 2) {
        add(heading.line, 'section-heading', '固定标题使用 H2：' + heading.title);
      }
      if (section && removedHeadings.includes(heading.title)) add(heading.line, 'section-heading', '节页不再使用该标题：' + heading.title);
    }
    for (let index = 0; index < tokens.length; index++) {
      const table = /** @type {import('markdown-it').Token} */ (tokens[index]);
      if (table.type !== 'table_open') continue;
      const end = tokens.findIndex((item, after) => after > index && item.type === 'table_close');
      const content = tokens.slice(index, end);
      const rows = content.filter((item) => item.type === 'tr_open');
      const columns = content.filter((item) => item.type === 'th_open').length;
      const sourceRows = lines
        .slice(/** @type {[number, number]} */ (table.map)[0], /** @type {[number, number]} */ (table.map)[1])
        .filter((line) => line.trim());
      const widths = sourceRows.map(
        (line) =>
          stripInlineCode(line)
            .trim()
            .replace(/^\||\|$/g, '')
            .split(/(?<!\\)\|/).length,
      );
      if (widths.some((width) => width !== columns))
        add(/** @type {[number, number]} */ (table.map)[0], 'tutorial-table', '表格各行列数必须一致');
      if (rows.length < 3 || columns < 2) add(/** @type {[number, number]} */ (table.map)[0], 'tutorial-table', '表格至少两列和两个数据行');
    }
  }
  if (section) {
    for (const heading of titles) {
      const end = sectionEnd(titles, heading, lines.length);
      const content = lines
        .map((value, line) => ({ value, line }))
        .filter((item) => item.line > heading.line && item.line < end && item.value.trim() && !hidden.has(item.line));
      if (heading.title === '常见问题') {
        let previousSolution;
        for (let index = 0; index < content.length; index += 2) {
          const symptom = /** @type {{ value: string; line: number; }} */ (content[index]),
            solution = content[index + 1];
          if (!/^- \*\*.+?\*\*[：:]\S/.test(symptom.value)) add(symptom.line, 'fault-symptom', '常见问题条目以加粗症状与原因起句');
          if (!solution || !/^ {4}- \*\*解决方案\*\*[：:]\S/.test(solution.value))
            add(symptom.line, 'fault-solution', '常见问题条目必须包含解决方案子项');
          if (previousSolution && symptom.line === previousSolution.line + 1) add(symptom.line, 'fault-spacing', '故障条目之间保留空行');
          previousSolution = solution;
        }
      }
      if (heading.title === '建议参考项') {
        if (titles.at(-1) !== heading) add(heading.line, 'references', '建议参考项是最后一个标题');
        if (!content.length) add(heading.line, 'references', '建议参考项至少包含一个条目');
        for (const item of content)
          if (!/^- `[^`]+`：\S/.test(item.value) || /C\d+-S\d+/.test(stripInlineCode(item.value)))
            add(item.line, 'references', '参考项采用行内代码对象与冒号说明');
      }
    }
    const content = lines.map((value, line) => ({ value, line })).filter(({ value, line }) => value.trim() && !hidden.has(line));
    const rule = content.findIndex(({ value }) => value.trim() === '---');
    if (rule < 2 || content.slice(1, rule).some(({ value }) => /^#{1,6}\s/.test(value)))
      add(content[1]?.line ?? 0, 'section-lead', '节页 H1 后先写导语，再以独立的分割线 --- 与时间段落分隔');
    else if (lines[/** @type {{ value: string; line: number; }} */ (content[rule]).line - 1]?.trim())
      add(/** @type {{ value: string; line: number; }} */ (content[rule]).line, 'section-lead', '分割线前必须保留空行');
    const start = rule < 2 ? 0 : rule;
    timeLines.forEach(([name, pattern], index) => {
      const item = content[start + index + 1];
      if (!item || !(/** @type {RegExp} */ (pattern).test(item.value.trim())))
        add(item?.line ?? 0, 'section-time', '节页分割线后依次写阅读时间与操作时间：缺少' + name);
    });
    const body = content[start + timeLines.length + 1];
    if (rule >= 2 && body && !/^## \S/.test(body.value)) add(body.line, 'section-time', '时间段落后必须紧跟 H2 标题');
    if (Number(/** @type {RegExpExecArray} */ (/chapter(\d+)/.exec(rel))[1]) >= 6 && !titles.some((item) => item.title === '前置知识'))
      add(0, 'section-heading', '第 6 章起节页必须包含前置知识');
  }
  if (dirname(path) === resolve(root, '.zcode') && basename(path) === 'workflow.md') {
    const visible = lines.filter((_, line) => !hidden.has(line)).join('\n');
    for (const name of guidanceFiles.filter((name) => name !== 'workflow.md')) {
      if (!visible.includes('.zcode/' + name)) add(0, 'guidance-route', '工作流程缺少阅读入口：.zcode/' + name);
    }
  }
  return diagnostics;
}

/** @param {string[]} paths @param {{root?: string}} config */
export async function checkDocs(paths = [], { root = repositoryRoot } = {}) {
  root = resolve(root);
  const scope = await collectFiles(paths, { root, include: (path) => extname(path).toLowerCase() === '.md' });
  const files = [];
  for (const path of scope.files) {
    try {
      const source = await readUtf8(path);
      files.push({
        path: displayPath(path, root),
        bytes: source.bytes.length,
        diagnostics: await inspectDocument(path, source.text, { root }),
      });
    } catch (error) {
      scope.errors.push({ path: displayPath(path, root), message: /** @type {Error} */ (error).message });
    }
  }
  const complete = !paths.length || paths.some((path) => inside(resolve(path), join(root, '.zcode')));
  if (complete) {
    const names = await readdir(join(root, '.zcode')).catch((error) => {
      scope.errors.push({ path: '.zcode', message: /** @type {Error} */ (error).message });
      return [];
    });
    for (const name of guidanceFiles)
      if (!(/** @type {string[]} */ (names).includes(name)))
        files.push({
          path: '.zcode/' + name,
          bytes: 0,
          diagnostics: [{ line: 1, column: 1, rule: 'guidance-set', message: '正式文档缺失' }],
        });
    for (const file of files)
      if (dirname(file.path) === '.zcode' && !guidanceFiles.includes(basename(file.path)))
        file.diagnostics.push({ line: 1, column: 1, rule: 'guidance-set', message: '正式文档不在文件清单中' });
  }
  return result(files, scope.errors, { skipped: scope.skipped });
}
await command(import.meta.url, '检查 Markdown 标题、链接、教程结构和正式文档集合。', ({ paths }) => checkDocs(paths));
