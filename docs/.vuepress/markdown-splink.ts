import { existsSync } from 'node:fs'
import { join } from 'node:path'

import type MarkdownIt from 'markdown-it'
import type Token from 'markdown-it/lib/token.mjs'

/**
 * [C1-S1] 单元编号自动跳转插件：
 * 只有一层方括号包围、数字不带前导零的编号会生成 /chapterN/sectionM/ 链接。
 * 代码围栏与行内代码不经过 text token；现有链接内的文本由 linkDepth 保护。
 */
const CS_RE = /(?<!\[)\[C([1-9]\d*)-S([1-9]\d*)\](?!\])/g

/** 构建/开发均在项目根运行 vuepress，cwd 即仓库根 */
const DOCS_DIR = join(process.cwd(), 'docs')

const targetExists = (chapter: number, section: number): boolean => {
  try {
    return existsSync(join(DOCS_DIR, `chapter${chapter}`, `section${section}`, 'README.md'))
  } catch {
    return false
  }
}

const makeText = (state: { Token: new (type: string, tag: string, nesting: number) => Token }, content: string): Token => {
  const tok = new state.Token('text', '', 0)
  tok.content = content
  return tok
}

export const spLinkPlugin = (md: MarkdownIt, options?: { base?: string }): void => {
  const base = options?.base ?? ''
  md.core.ruler.push('sp-link', (state) => {
    for (const block of state.tokens) {
      if (block.type !== 'inline' || !block.children) continue
      const children: Token[] = []
      let linkDepth = 0
      for (const tok of block.children) {
        if (tok.type === 'link_open') {
          linkDepth += 1
          children.push(tok)
          continue
        }
        if (tok.type === 'link_close') {
          children.push(tok)
          linkDepth = Math.max(0, linkDepth - 1)
          continue
        }
        if (tok.type !== 'text' || linkDepth > 0) {
          children.push(tok)
          continue
        }
        const content = tok.content
        let pos = 0
        CS_RE.lastIndex = 0
        let m: RegExpExecArray | null
        let matched = false
        while ((m = CS_RE.exec(content)) !== null) {
          matched = true
          const chapter = Number.parseInt(m[1], 10)
          const section = Number.parseInt(m[2], 10)
          if (pos < m.index) {
            children.push(makeText(state, content.slice(pos, m.index)))
          }
          if (targetExists(chapter, section)) {
            const open = new state.Token('link_open', 'a', 1)
            open.attrs = [['href', `${base}chapter${chapter}/section${section}/`]]
            children.push(open, makeText(state, m[0]), new state.Token('link_close', 'a', -1))
          } else {
            children.push(makeText(state, m[0]))
          }
          pos = m.index + m[0].length
        }
        if (matched && pos < content.length) {
          children.push(makeText(state, content.slice(pos)))
        }
        if (!matched) children.push(tok)
      }
      block.children = children
    }
  })
}
