import { JSDOM } from 'jsdom';
import { dirname, extname } from 'node:path';
import { formatMarkdown } from './format.mjs';
import { inspectDocument } from './check-docs.mjs';
import { inspectText } from './check-text.mjs';

const containerTypes = ['info', 'tip', 'warning', 'caution', 'important', 'note', 'details'];
const imageExtensions = new Map([
  ['image/gif', ['.gif']],
  ['image/jpeg', ['.jpg', '.jpeg']],
  ['image/png', ['.png']],
  ['image/svg+xml', ['.svg']],
  ['image/webp', ['.webp']],
]);
const markType = (node) => ({ STRONG: 'strong', B: 'strong', EM: 'em', I: 'em', S: 's', DEL: 's', STRIKE: 's' })[node.nodeName];
function escapeText(text) {
  return text
    .replace(/\r\n?/g, '\n')
    .replace(/[\\`*_[\]<#~]/g, '\\$&')
    .replace(/&(?=(?:#\d+|#x[\da-f]+|[a-z]\w+);)/gi, '\\&')
    .replace(/^(\s{0,3})(\d+)([.)])(?=\s)/gm, '$1$2\\$3')
    .replace(/^(\s{0,3})(?=(?:[-+]\s|>|:::|[|=]))/gm, '$1\\');
}
function destination(value) {
  const text = value.replaceAll('<', '%3C').replaceAll('>', '%3E');
  return /[\s()]/.test(text) ? '<' + text + '>' : text;
}
function codeSpan(value) {
  const length = Math.max(0, ...[...value.matchAll(/`+/g)].map((match) => match[0].length)) + 1;
  const marker = '`'.repeat(length),
    space = /^`|`$/.test(value) ? ' ' : '';
  return marker + space + value + space + marker;
}
function highlights(value) {
  const values = new Set();
  for (const match of value.matchAll(/(\d+)(?:\s*-\s*(\d+))?/g))
    for (let line = Number(match[1]); line <= Number(match[2] || match[1]); line++) if (line > 0) values.add(line);
  const sorted = [...values].sort((a, b) => a - b),
    groups = [];
  for (let index = 0; index < sorted.length;) {
    const start = sorted[index];
    let end = start;
    while (sorted[index + 1] === end + 1) end = sorted[++index];
    if (end - start >= 2) groups.push(start + '-' + end);
    else groups.push(...Array.from({ length: end - start + 1 }, (_, offset) => String(start + offset)));
    index++;
  }
  return groups.length ? ' {' + groups.join(',') + '}' : '';
}

/** Merge adjacent marks across link boundaries, matching the editor's continuous selection semantics. */
function mergeMarks(root) {
  for (const child of [...root.children]) mergeMarks(child);
  const children = [...root.childNodes];
  const type = (node) => markType(node) || (node.nodeName === 'A' && node.childNodes.length === 1 ? markType(node.firstChild) : undefined);
  for (let index = 0; index < children.length;) {
    const mark = type(children[index]);
    if (!mark) {
      index++;
      continue;
    }
    let end = index + 1;
    while (end < children.length && type(children[end]) === mark) end++;
    if (end > index + 1) {
      const wrapper = root.ownerDocument.createElement(mark);
      root.insertBefore(wrapper, children[index]);
      for (const child of children.slice(index, end)) {
        if (child.nodeName === 'A') {
          const link = child.cloneNode(false);
          while (child.firstChild.firstChild) link.append(child.firstChild.firstChild);
          wrapper.append(link);
        } else while (child.firstChild) wrapper.append(child.firstChild);
        child.remove();
      }
    }
    index = end;
  }
}

export async function restoreMarkdown(html, path, docsRoot) {
  if (!html.trim()) return { ok: false, diagnostics: ['HTML 正文为空'] };
  const dom = new JSDOM('<main><div id="content">' + html + '</div></main>');
  const assets = new Map();
  try {
    const root = dom.window.document.querySelector('#content');
    mergeMarks(root);
    const inline = (node) => {
      if (node.nodeType === 3) return escapeText(node.data);
      const tag = node.nodeName;
      const body = () => [...node.childNodes].map(inline).join('');
      if (tag === 'EM' || tag === 'I') return '_' + body() + '_';
      if (tag === 'STRONG' || tag === 'B') return '**' + body() + '**';
      if (['S', 'DEL', 'STRIKE'].includes(tag)) return '~~' + body() + '~~';
      if (tag === 'CODE') return codeSpan(node.textContent);
      if (tag === 'A') {
        if (node.classList.contains('header-anchor')) return body();
        if (node.classList.contains('route-link') && /\/chapter\d+\/section\d+\//.test(node.getAttribute('href') || ''))
          return node.textContent;
        return '[' + body() + '](' + destination(node.getAttribute('href') || '') + ')';
      }
      if (tag === 'IMG') {
        const src = node.getAttribute('src') || '',
          title = node.getAttribute('title') || '',
          alt = escapeText(node.getAttribute('alt') || '');
        if (!/^[A-Za-z0-9][A-Za-z0-9._-]*\.(?:gif|jpe?g|png|svg|webp)$/i.test(title) || title.includes('..'))
          throw new Error('图片 title 必须写合法的原始文件名');
        if (!alt.trim()) throw new Error('图片 alt 必须为非空语义描述');
        const data = /^data:(image\/[\w.+-]+);base64,([\s\S]+)$/.exec(src);
        if (data) {
          if (!/^(?:[A-Za-z0-9+/]{4})*(?:[A-Za-z0-9+/]{2}==|[A-Za-z0-9+/]{3}=)?$/.test(data[2]))
            throw new Error('data URI 图片不是有效 Base64');
          if (!imageExtensions.get(data[1])?.includes(extname(title).toLowerCase()))
            throw new Error('图片 title 扩展名与 data URI 类型不一致');
          const bytes = Buffer.from(data[2], 'base64');
          if (assets.has(title) && !assets.get(title).equals(bytes)) throw new Error('图片文件名冲突：' + title);
          assets.set(title, bytes);
          return '![' + alt + '](./' + title + ' "' + title + '")';
        }
        const url = new URL(src, 'https://local.invalid/');
        const remote = /^https?:/.test(src);
        const name = decodeURIComponent(url.pathname.split('/').at(-1));
        const restored = remote ? name : name.replace(/-[A-Za-z0-9_-]{8}(?=\.[A-Za-z0-9]+$)/, '');
        if (restored !== title) throw new Error('图片 title 与文件名不一致：' + title + ' != ' + restored);
        return '![' + alt + '](' + (remote ? destination(src) : './' + title) + ' "' + title + '")';
      }
      if (tag === 'BR') return '<br>\n';
      return body();
    };
    const renderChildren = (node) => [...node.childNodes].flatMap(block).filter(Boolean).join('\n\n');
    function list(node, depth = 0) {
      const items = [...node.children].filter((child) => child.nodeName === 'LI');
      const tight = node.dataset.devListTight
        ? node.dataset.devListTight !== 'false'
        : items.every((item) => ![...item.children].some((child) => child.nodeName === 'P'));
      return items
        .map((item, index) => {
          const marker = node.nodeName === 'OL' ? String(Number(node.getAttribute('start') || 1) + index) + '. ' : '- ';
          const indent = '    '.repeat(depth);
          const parts = [],
            pending = [];
          const flush = () => {
            const value = pending.splice(0).join('').trim();
            if (value) parts.push(value);
          };
          for (const child of item.childNodes) {
            if (['UL', 'OL'].includes(child.nodeName)) {
              flush();
              parts.push({ list: child });
            } else if (child.nodeName === 'P') {
              flush();
              parts.push([...child.childNodes].map(inline).join('').trim());
            } else if (['PRE', 'DIV', 'BLOCKQUOTE'].includes(child.nodeName)) {
              flush();
              parts.push(block(child));
            } else pending.push(inline(child));
          }
          flush();
          let started = false;
          return (
            parts
              .map((part) => {
                if (part.list) {
                  const prefix = started ? '' : indent + marker.trimEnd() + '\n';
                  started = true;
                  return prefix + (tight ? '' : '\n') + list(part.list, depth + 1);
                }
                const value = part.split('\n');
                if (!started) {
                  started = true;
                  return (
                    indent +
                    marker +
                    value[0] +
                    value
                      .slice(1)
                      .map((line) => '\n' + indent + ' '.repeat(marker.length) + line)
                      .join('')
                  );
                }
                return '\n' + value.map((line) => indent + ' '.repeat(marker.length) + line).join('\n');
              })
              .join('\n') || indent + marker.trimEnd()
          );
        })
        .join(tight ? '\n' : '\n\n');
    }
    function block(node) {
      if (node.nodeType !== 1) return '';
      const tag = node.nodeName;
      if (/^H[1-6]$/.test(tag)) return '#'.repeat(Number(tag[1])) + ' ' + [...node.childNodes].map(inline).join('').trim();
      if (tag === 'P') return [...node.childNodes].map(inline).join('').trim();
      if (node.dataset.autoSectionList === 'true') return '<!-- AUTO:SECTION-LIST -->';
      if (tag === 'PRE') {
        const code = node.querySelector(':scope > code');
        if (!code) throw new Error('pre 元素缺少 code 子元素');
        const language = code.dataset.language || node.dataset.language || /(?:^|\s)language-([\w-]+)/.exec(code.className)?.[1] || '';
        const spans = [...code.children].filter((child) => child.matches('span.line'));
        let numbers = spans.flatMap((line, index) => (line.classList.contains('highlighted') ? [index + 1] : []));
        if (spans.length && !spans.at(-1).textContent) spans.pop();
        const value = spans.length
          ? spans.map((line) => line.textContent).join('\n')
          : code.textContent.replace(/\r\n?/g, '\n').replace(/\n$/, '');
        const highlight = code.dataset.highlightLines || node.dataset.highlightLines || numbers.join(',');
        const marker = '`'.repeat(Math.max(3, ...[...value.matchAll(/`+/g)].map((match) => match[0].length + 1)));
        return marker + language + highlights(highlight) + '\n' + value + '\n' + marker;
      }
      if (tag === 'UL' || tag === 'OL') return list(node);
      if (tag === 'TABLE') {
        const rows = [...node.querySelectorAll('tr')].map((row) =>
          [...row.children]
            .filter((cell) => ['TH', 'TD'].includes(cell.nodeName))
            .map((cell) => [...cell.childNodes].map(inline).join('').trim().replaceAll('|', '\\|')),
        );
        if (!rows.length) throw new Error('表格没有数据行');
        const aligns = [...node.querySelector('tr').children].map(
          (cell) => ({ left: ':---', right: '---:', center: ':---:' })[cell.style.textAlign] || '---',
        );
        return [
          '| ' + rows[0].join(' | ') + ' |',
          '|' + aligns.join('|') + '|',
          ...rows.slice(1).map((row) => '| ' + row.join(' | ') + ' |'),
        ].join('\n');
      }
      if (tag === 'BLOCKQUOTE')
        return renderChildren(node)
          .split('\n')
          .map((line) => (line ? '> ' + line : '>'))
          .join('\n');
      if (tag === 'HR') return '---';
      if (tag === 'DETAILS' || node.classList.contains('hint-container') || node.dataset.vpContainer) {
        const type =
          node.dataset.vpContainer ||
          (tag === 'DETAILS' ? 'details' : containerTypes.find((type) => node.classList.contains(type))) ||
          'info';
        const titleNode = node.querySelector(':scope > summary, :scope > .hint-container-title');
        const title = node.dataset.vpTitle || (titleNode ? [...titleNode.childNodes].map(inline).join('').trim() : '');
        const body = [...node.childNodes]
          .filter((child) => child !== titleNode)
          .map(block)
          .filter(Boolean)
          .join('\n\n');
        return '::: ' + type + (title ? ' ' + title : '') + '\n\n' + body + '\n\n:::';
      }
      return renderChildren(node);
    }
    const blocks = [];
    let replaceList = false;
    for (const child of root.children) {
      if (replaceList && (child.nodeName === 'UL' || child.dataset.autoSectionList === 'true')) {
        blocks.push('<!-- AUTO:SECTION-LIST -->');
        replaceList = false;
        continue;
      }
      const value = block(child);
      if (value) blocks.push(value);
      if (child.nodeName === 'H2' && child.textContent.trim() === '本章各节') replaceList = true;
    }
    if (replaceList) throw new Error('本章各节标题后缺少自动生成列表');
    if (!blocks.length) throw new Error('教程正文为空');
    const markdown = await formatMarkdown(blocks.join('\n\n') + '\n', path);
    const issues = [...inspectText(markdown, { path }), ...(await inspectDocument(path, markdown, { root: dirname(docsRoot) }))];
    if (issues.length) return { ok: false, diagnostics: issues.map((item) => item.message) };
    return {
      ok: true,
      markdown,
      assets: [...assets]
        .sort(([a], [b]) => a.localeCompare(b))
        .map(([name, bytes]) => ({ name, size: bytes.length, base64: bytes.toString('base64') })),
    };
  } catch (error) {
    return { ok: false, diagnostics: [error.message] };
  } finally {
    dom.window.close();
  }
}
