import MarkdownIt from 'markdown-it';

export const markdown = new MarkdownIt({ html: true });
export const normalize = (text) => text.replace(/\r\n?/g, '\n');
export const codeContent = (text) => normalize(text).replace(/\n+$/, '');
export function parseMarkdown(text) {
  const lines = normalize(text).split('\n');
  const tokens = markdown.parse(normalize(text), {});
  const hidden = new Set();
  const fences = tokens.filter((token) => token.type === 'fence' || token.type === 'code_block');
  for (const token of fences) for (let line = token.map[0]; line < token.map[1]; line++) hidden.add(line);
  return { lines, tokens, fences, hidden };
}
export function headings(tokens) {
  return tokens.flatMap((token, index) =>
    token.type === 'heading_open' ? [{ line: token.map[0], level: Number(token.tag.slice(1)), title: tokens[index + 1].content }] : [],
  );
}
export function inlineTokens(token) {
  return (token.children || []).flatMap((child) => [child, ...inlineTokens(child)]);
}
export function lineOffset(text, line) {
  let offset = 0;
  for (let index = 0; index < line; index++) offset = text.indexOf('\n', offset) + 1;
  return offset;
}
