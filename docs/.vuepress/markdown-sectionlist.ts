import { readdirSync, readFileSync } from 'node:fs'
import { join } from 'node:path'

import type MarkdownIt from 'markdown-it'
import type Token from 'markdown-it/lib/token.mjs'

/**
 * 章索引页 section 列表自动生成 (契约见 .zcode/text-guidelines.md)：
 * chapterN/README.md 中放置占位符 `<!-- AUTO:SECTION-LIST -->`，渲染时自动替换为
 * 该章全部 section 的链接列表 (数据源 = 文件系统，H1 与侧边栏同一来源)。
 */
const PLACEHOLDER = '<!-- AUTO:SECTION-LIST -->'
const CHAPTER_RE = /^chapter([1-9]\d*)\/README\.md$/u
const SECTION_RE = /^section([1-9]\d*)$/u

const toPosix = (s: string): string => s.split('\\').join('/')

/** 数字序目录列表 (与 sidebar.ts 同逻辑) */
const getSectionDirs = (chapterDir: string): { name: string; ordinal: number }[] =>
  readdirSync(chapterDir, { withFileTypes: true })
    .flatMap((entry) => {
      const m = entry.isDirectory() ? SECTION_RE.exec(entry.name) : null
      return m ? [{ name: entry.name, ordinal: Number.parseInt(m[1], 10) }] : []
    })
    .sort((a, b) => a.ordinal - b.ordinal)

/** 读取 section README 首个非空行 H1(与 sidebar.ts 同逻辑) */
const readTitle = (readmePath: string): string | null => {
  try {
    const first = readFileSync(readmePath, 'utf8')
      .split(/\r?\n/u)
      .find((line) => line.trim().length > 0)
    const m = first ? /^# (\S(?:.*\S)?)\s*$/u.exec(first) : null
    return m?.[1] ?? null
  } catch {
    return null
  }
}

export const sectionListPlugin = (
  md: MarkdownIt,
  options?: { base?: string; docsDir?: string },
): void => {
  const base = options?.base ?? ''
  const docsDir = options?.docsDir ?? join(process.cwd(), 'docs')
  md.core.ruler.push('section-list', (state) => {
    const rel = toPosix(state.env?.filePathRelative ?? '')
    const chapterMatch = CHAPTER_RE.exec(rel)
    if (!chapterMatch) return
    const chapter = chapterMatch[1]

    // 收集 section 列表
    const items: { ordinal: number; title: string }[] = []
    for (const dir of getSectionDirs(join(docsDir, `chapter${chapter}`))) {
      const title = readTitle(join(docsDir, `chapter${chapter}`, dir.name, 'README.md'))
      if (title) items.push({ ordinal: dir.ordinal, title })
    }
    if (items.length === 0) return

    // 找到占位符 token 并替换为列表 token
    const newTokens: Token[] = []
    let replaced = false
    for (const block of state.tokens) {
      if (
        !replaced &&
        block.type === 'html_block' &&
        block.content.trim() === PLACEHOLDER
      ) {
        const bulletOpen = new state.Token('bullet_list_open', 'ul', 1)
        bulletOpen.attrs = [['class', 'section-list']]
        const bulletClose = new state.Token('bullet_list_close', 'ul', -1)
        newTokens.push(bulletOpen)
        for (const item of items) {
          const li = new state.Token('list_item_open', 'li', 1)
          const liClose = new state.Token('list_item_close', 'li', -1)
          const inline = new state.Token('inline', '', 0)
          inline.children = []
          inline.content = ''
          const linkOpen = new state.Token('link_open', 'a', 1)
          linkOpen.attrs = [
            ['href', `${base}chapter${chapter}/section${item.ordinal}/`],
          ]
          const text = new state.Token('text', '', 0)
          text.content = item.title
          const linkClose = new state.Token('link_close', 'a', -1)
          inline.children.push(linkOpen, text, linkClose)
          newTokens.push(li, inline, liClose)
        }
        newTokens.push(bulletClose)
        replaced = true
        continue
      }
      newTokens.push(block)
    }
    if (replaced) state.tokens = newTokens
  })
}
