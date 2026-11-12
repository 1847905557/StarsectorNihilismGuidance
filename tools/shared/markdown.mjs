import MarkdownIt from 'markdown-it';

export const markdown = new MarkdownIt({ html: true });
/** @param {string} text */
export const normalize = (text) => text.replace(/\r\n?/g, '\n');
/** @param {string} text */
export const codeContent = (text) => normalize(text).replace(/\n+$/, '');
/** @param {string} text */
export function parseMarkdown(text) {
  const lines = normalize(text).split('\n');
  const tokens = markdown.parse(normalize(text), {});
  const hidden = new Set();
  const fences = tokens.filter((token) => token.type === 'fence' || token.type === 'code_block');
  for (const token of fences)
    for (let line = /** @type {[number, number]} */ (token.map)[0]; line < /** @type {[number, number]} */ (token.map)[1]; line++)
      hidden.add(line);
  return { lines, tokens, fences, hidden };
}
/** @param {import('markdown-it').Token[]} tokens */
export function headings(tokens) {
  return tokens.flatMap((token, index) =>
    token.type === 'heading_open'
      ? [
          {
            line: /** @type {[number, number]} */ (token.map)[0],
            level: Number(token.tag.slice(1)),
            title: /** @type {import('markdown-it').Token} */ (tokens[index + 1]).content,
          },
        ]
      : [],
  );
}
/** @param {import('markdown-it').Token} token @returns {import('markdown-it').Token[]} */
export function inlineTokens(token) {
  return (token.children || []).flatMap((child) => [child, ...inlineTokens(child)]);
}
/** @param {string} text @param {number} line */
export function lineOffset(text, line) {
  let offset = 0;
  for (let index = 0; index < line; index++) offset = text.indexOf('\n', offset) + 1;
  return offset;
}
