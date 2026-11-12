import { extname } from 'node:path';
import { collectFiles, displayPath, isText, readUtf8, repositoryRoot } from './shared/files.mjs';
import { command, diagnostic, result } from './shared/cli.mjs';
import { parseMarkdown, lineOffset } from './shared/markdown.mjs';

const cjk = '\\u3400-\\u9fff\\uf900-\\ufaff';
const chinese = '，。；：？！、';

/** Format prose only; callers select prose and comment spans before invoking this function. */
export function formatProse(value, { wordSpacing = true } = {}) {
  let text = value
    .replace(/[（]/g, '(')
    .replace(/[）]/g, ')')
    .replace(/[“”]/g, '"')
    .replace(/[‘’]/g, "'")
    .replace(/…+|\.{4,}/g, '...')
    .replace(/\([ \t]+/g, '(')
    .replace(/[ \t]+\)/g, ')')
    .replace(new RegExp('([' + cjk + '])\\(', 'g'), '$1 (')
    .replace(new RegExp('\\)([' + cjk + '])', 'g'), ') $1')
    .replace(new RegExp('[ \\t]+([' + chinese + '])', 'g'), '$1')
    .replace(new RegExp('([' + chinese + '])[ \\t]+(?=[^\\s|])', 'g'), '$1')
    .replace(/(\S)——/g, '$1 ——')
    .replace(/——(\S)/g, '—— $1');
  if (wordSpacing)
    text = text
      .replace(new RegExp('([' + cjk + '])([A-Za-z0-9])', 'g'), '$1 $2')
      .replace(new RegExp('([A-Za-z0-9])([' + cjk + '])', 'g'), '$1 $2');
  text = text.replace(/(?<![A-Za-z0-9])(["'])([^"'\n]*?)\1/g, (match, quote, content, offset, all) => {
    const before = offset > 0 && new RegExp('[' + cjk + ']').test(all[offset - 1]) ? ' ' : '';
    const after = new RegExp('[' + cjk + ']').test(all[offset + match.length] || '') ? ' ' : '';
    return before + quote + content.trim() + quote + after;
  });
  return text;
}

function protectedProse(value) {
  const protectedValues = [];
  const save = (text) => {
    const index = protectedValues.push(text) - 1;
    return '\ufff0' + index + '\ufff1';
  };
  // Destinations and inline code retain their exact bytes; labels are normal prose.
  let masked = value
    .replace(/(`+)[\s\S]*?\1/g, save)
    .replace(/!?\[(?:\\.|[^\]])*\]\((?:<[^>]*>|(?:\\.|[^)])*)\)/g, (match) => {
      if (match.startsWith('!')) return save(match);
      const end = match.indexOf('](');
      return '[' + protectedProse(match.slice(1, end)) + ']' + save(match.slice(end + 1));
    })
    .replace(/https?:\/\/[^\s<>]+|<[^>]*>|\\[!"#$%&'()*+,\-./:;<=>?@[\]^_`{|}~\\]/g, save);
  masked = formatProse(masked);
  for (let index = 0; index < protectedValues.length; index++) {
    const marker = '\ufff0' + index + '\ufff1';
    // A code span or link destination acts as an English word at a CJK boundary.
    masked = masked
      .replace(new RegExp('([' + cjk + '])' + marker, 'g'), '$1 ' + marker)
      .replace(new RegExp(marker + '([' + cjk + '])', 'g'), marker + ' $1');
    masked = masked.replaceAll(marker, () => protectedValues[index]);
  }
  return masked;
}

/** Locate comments with lexical string handling, including Java text blocks and shell quoting. */
export function commentSpans(text, language) {
  const hash = /^(?:bash|sh|shell|powershell|ps1|properties|yaml|yml|python)$/.test(language);
  const slash = /^(?:java|javascript|js|typescript|ts|glsl|vert|frag|c|cpp|jsonc|json5|gradle|css|scss|mjs)$/.test(language);
  if (!hash && !slash) return [];
  const spans = [];
  let index = 0;
  while (index < text.length) {
    const triple = text.startsWith('"""', index) || text.startsWith("'''", index);
    if (triple) {
      const delimiter = text.slice(index, index + 3);
      const end = text.indexOf(delimiter, index + 3);
      index = end < 0 ? text.length : end + 3;
    } else if ('"\'`'.includes(text[index])) {
      const quote = text[index++];
      while (index < text.length) {
        if (text[index] === '\\') index += 2;
        else if (text[index++] === quote) break;
      }
    } else if (slash && text.startsWith('//', index)) {
      const end = text.indexOf('\n', index);
      spans.push([index + 2, end < 0 ? text.length : end]);
      index = end < 0 ? text.length : end;
    } else if (slash && text.startsWith('/*', index)) {
      const end = text.indexOf('*/', index + 2);
      spans.push([index + 2, end < 0 ? text.length : end]);
      index = end < 0 ? text.length : end + 2;
    } else if (hash && text[index] === '#' && !text.startsWith('#version', index) && (index === 0 || /\s/.test(text[index - 1]))) {
      const end = text.indexOf('\n', index);
      spans.push([index + 1, end < 0 ? text.length : end]);
      index = end < 0 ? text.length : end;
    } else index++;
  }
  return spans;
}

export function transformTypography(text, path = 'document.md', { comments = true } = {}) {
  if (extname(path).toLowerCase() !== '.md') {
    const language = extname(path).slice(1).toLowerCase();
    let output = text;
    for (const [start, end] of commentSpans(text, language).reverse()) {
      output = output.slice(0, start) + formatComment(text.slice(start, end)) + output.slice(end);
    }
    return output;
  }
  const { lines, hidden, fences } = parseMarkdown(text);
  const edits = [];
  for (let index = 0; index < lines.length; index++) {
    if (hidden.has(index)) continue;
    const line = lines[index];
    if (/^\s*<!--|^\s*:::|^\s*\|?[ :|-]+\|[ :|-]*$/.test(line)) continue;
    const formatted = protectedProse(line);
    if (formatted !== line) edits.push([lineOffset(text, index), lineOffset(text, index) + line.length, formatted]);
  }
  if (comments) {
    for (const fence of fences.filter((token) => token.type === 'fence')) {
      const start = lineOffset(text, fence.map[0] + 1);
      for (const [from, to] of commentSpans(fence.content, fence.info.split(/\s/)[0])) {
        edits.push([start + from, start + to, formatComment(fence.content.slice(from, to))]);
      }
    }
  }
  let output = text;
  for (const [start, end, value] of edits.sort((a, b) => b[0] - a[0])) output = output.slice(0, start) + value + output.slice(end);
  return output;
}

function formatComment(text) {
  return text
    .split('\n')
    .map((line) => {
      if (!line.trim()) return line;
      const leading = line.match(/^\s*/)[0];
      const trailing = line.match(/[ \t]*$/)[0];
      const content = line.slice(leading.length, line.length - trailing.length);
      return leading + formatProse(content, { wordSpacing: false }) + trailing;
    })
    .join('\n');
}

export function inspectText(text, { bom = false, path = 'document.md' } = {}) {
  const diagnostics = [];
  const add = (offset, rule, message) => diagnostics.push(diagnostic(text, offset, rule, message));
  if (bom) add(0, 'encoding-bom', '文本使用 UTF-8 无 BOM');
  for (const match of text.matchAll(/\r/g)) add(match.index, 'line-ending', '换行使用 LF');
  if (!text.endsWith('\n') || text.endsWith('\n\n')) add(text.length, 'terminal-newline', '文件末尾保留一个换行');
  for (const match of text.matchAll(/[ \t]+(?=\r?$)/gm)) add(match.index, 'trailing-space', '行尾存在空格或 Tab');
  const desired = transformTypography(text, path);
  if (extname(path).toLowerCase() === '.md') {
    const { lines, hidden } = parseMarkdown(text);
    for (let index = 0; index < lines.length; index++) {
      if (hidden.has(index)) continue;
      const line = lines[index].replace(/(`+)[\s\S]*?\1/g, (match) => ' '.repeat(match.length));
      for (const match of line.matchAll(/(?<!—)—(?!—)/g)) {
        if (
          /^\s*—\s*$/.test(line.slice(0, match.index).split('|').at(-1) + '—' + line.slice(match.index + 1).split('|')[0]) &&
          line.includes('|')
        )
          continue;
        add(lineOffset(text, index) + match.index, 'single-dash', '句中破折号使用 ——');
      }
    }
  }
  const currentLines = text.split('\n');
  const desiredLines = desired.split('\n');
  for (let index = 0; index < currentLines.length; index++) {
    if (currentLines[index] !== desiredLines[index]) {
      let column = 0;
      while (currentLines[index][column] === desiredLines[index][column]) column++;
      add(lineOffset(text, index) + column, 'typography', '文字格式应为：' + desiredLines[index].trim());
    }
  }
  return diagnostics;
}

export async function checkText(paths = [], { root = repositoryRoot } = {}) {
  const scope = await collectFiles(paths, { root, include: isText });
  const files = [];
  for (const path of scope.files) {
    try {
      const source = await readUtf8(path);
      files.push({
        path: displayPath(path, root),
        bytes: source.bytes.length,
        diagnostics: inspectText(source.text, { bom: source.bom, path }),
      });
    } catch (error) {
      scope.errors.push({ path: displayPath(path, root), message: error.message });
    }
  }
  return result(files, scope.errors, { skipped: scope.skipped });
}
await command(import.meta.url, '检查文本编码、换行和正文及注释的文字格式。', ({ paths }) => checkText(paths));
