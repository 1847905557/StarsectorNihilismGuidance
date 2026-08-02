import { existsSync } from 'node:fs'
import { join } from 'node:path'

import type MarkdownIt from 'markdown-it'
import type Token from 'markdown-it/lib/token.mjs'

/**
 * Sxx-Pyy 单元编号自动跳转插件（契约见 validation.md 判据 12）：
 * 正文裸写 Sxx-Pyy，渲染为指向 /stageN/phaseM/ 的链接，显示格式 [Sxx-Pyy]。
 * 代码围栏内（fence）与反引号内（inline code）天然不经过 text token，不受影响。
 * 注意：SP_RE 必须有两个捕获组（组 1 = stage、组 2 = phase），m[0] 为完整编号。
 */
const SP_RE = /(?<![0-9A-Za-z])S(\d{1,2})-P(\d{1,2})(?![0-9A-Za-z])/g

/** 构建/开发均在项目根运行 vuepress，cwd 即仓库根 */
const DOCS_DIR = join(process.cwd(), 'docs')

const targetExists = (stage: number, phase: number): boolean => {
  try {
    return existsSync(join(DOCS_DIR, `stage${stage}`, `phase${phase}`, 'README.md'))
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
      for (const tok of block.children) {
        if (tok.type !== 'text') {
          children.push(tok)
          continue
        }
        const content = tok.content
        let pos = 0
        SP_RE.lastIndex = 0
        let m: RegExpExecArray | null
        let matched = false
        while ((m = SP_RE.exec(content)) !== null) {
          matched = true
          const stage = Number.parseInt(m[1], 10)
          const phase = Number.parseInt(m[2], 10)
          if (pos < m.index) {
            children.push(makeText(state, content.slice(pos, m.index)))
          }
          if (targetExists(stage, phase)) {
            const open = new state.Token('link_open', 'a', 1)
            open.attrs = [['href', `${base}stage${stage}/phase${phase}/`]]
            children.push(open, makeText(state, `[${m[0]}]`), new state.Token('link_close', 'a', -1))
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
