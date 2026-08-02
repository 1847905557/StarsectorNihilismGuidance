import { fileURLToPath } from 'node:url'
import {
  copyFileSync,
  existsSync,
  mkdirSync,
  readFileSync,
  renameSync,
  writeFileSync,
} from 'node:fs'
import { dirname, join, resolve, sep } from 'node:path'

import MarkdownIt from 'markdown-it'
import TurndownService from 'turndown'
import type { Plugin } from 'vuepress'

/**
 * dev-only 网页内编辑插件：
 * - vite 插件（configureServer 仅 dev 执行）：
 *   - GET  /__api/read?path=       读取文件内容
 *   - POST /__api/edit             整页写回
 *   - POST /__api/edit-block       单块就地编辑写回（所见即所得）
 * - client 端：正文就地编辑（见 client/dev-edit-client.ts）
 * build 产物不含中间件；client 在非 dev 环境不渲染。
 *
 * 写回安全（2026-08-02 加固，教训：edit 接口曾把文件写成 0 字节）：
 * 1. 空内容 / 纯空白内容一律拒绝；
 * 2. edit-block 结果行数骤减（< 原行数 50% 或 < 3 行）一律拒绝；
 * 3. 写前先把原文件备份到 .cursor/tutorial-work/backups/edit-writes/；
 * 4. 临时文件 + rename 原子写，杜绝半写/零写；
 * 5. 每次写回打印日志。
 */

const CLIENT_FILE = fileURLToPath(new URL('./client/dev-edit-client.ts', import.meta.url)).replace(/\\/g, '/')

const BACKUP_DIR = resolve(process.cwd(), '.cursor', 'tutorial-work', 'backups', 'edit-writes')

const encode = (content: string): string => {
  // 契约：UTF-8 无 BOM、CRLF-only、文件尾单个 CRLF
  let c = content.replace(/\r\n/g, '\n').replace(/\r/g, '\n')
  c = c.replace(/\n+$/, '\n')
  return c.replace(/\n/g, '\r\n')
}

/** 校验 + 备份 + 原子写。任何失败都抛错，调用方转 500。 */
const writeSafe = (target: string, content: string, context: string): void => {
  if (!content.trim()) {
    throw new Error('拒绝写回：内容为空（禁止用空内容覆盖文件）')
  }
  const encoded = encode(content)
  if (!encoded.trim()) {
    throw new Error('拒绝写回：编码后内容为空')
  }
  const now = new Date()
  const stamp =
    now.getFullYear() +
    String(now.getMonth() + 1).padStart(2, '0') +
    String(now.getDate()).padStart(2, '0') +
    '-' +
    String(now.getHours()).padStart(2, '0') +
    String(now.getMinutes()).padStart(2, '0') +
    String(now.getSeconds()).padStart(2, '0')
  mkdirSync(BACKUP_DIR, { recursive: true })
  const rel = target.split(sep).slice(-3).join('_')
  const backup = join(BACKUP_DIR, `${stamp}_${rel}.bak`)
  copyFileSync(target, backup)
  const tmp = target + '.tmp-' + process.pid
  writeFileSync(tmp, encoded, 'utf-8')
  renameSync(tmp, target)
  console.log(
    `[dev-edit] ${context} 写回 ${rel}（${Buffer.byteLength(encoded, 'utf-8')} 字节），备份 ${backup}`,
  )
}

/** 源 markdown 原文 → 纯文本（用于与编辑前 innerText 匹配定位） */
const plainText = (src: string): string =>
  src
    .replace(/\[([^\]]*)\]\([^)]*\)/g, '$1')
    .replace(/`{1,3}([^`]+)`{1,3}/g, '$1')
    .replace(/\*\*([^*]+)\*\*/g, '$1')
    .replace(/\*([^*]+)\*/g, '$1')
    .replace(/~~([^~]+)~~/g, '$1')
    .replace(/<br\s*\/?>/gi, '')
    .trim()

/** 块转换后把 C-S 绝对链接还原为契约裸编号 */
const restoreSpLinks = (mdText: string): string =>
  mdText.replace(/\[(C\d{1,2}-S\d{1,2})\]\([^)]*chapter\d+\/section\d+\/\)/g, '$1')

const mdParser = new MarkdownIt()
const turndown = new TurndownService({ codeBlockStyle: 'fenced', headingStyle: 'atx' })

interface ReqLike {
  url?: string
  method?: string
  on?: (ev: string, cb: (chunk: Buffer) => void) => void
}

interface ResLike {
  statusCode?: number
  setHeader?: (k: string, v: string) => void
  end?: (body?: string) => void
}

/** vite 插件：dev 中间件（configureServer 仅 dev 模式调用） */
export const devEditVitePlugin = () => {
  const docsDir = resolve(process.cwd(), 'docs')
  return {
    name: 'dev-edit-middleware',
    configureServer(server: { middlewares: { use: (h: (req: unknown, res: unknown, next?: unknown) => void) => void } }) {
      console.log('[dev-edit] middleware registered')
      server.middlewares.use((req, res, next) => {
        const r = req as ReqLike
        const w = res as ResLike
        const url = r.url ?? ''
        if (!url.startsWith('/__api/')) {
          next?.()
          return
        }

        /** 路径白名单校验：docs 内的 .md 且文件必须存在 */
        const resolveTarget = (): string | null => {
          const u = new URL(url, 'http://localhost')
          const rel = u.searchParams.get('path') ?? ''
          if (!rel.endsWith('.md') || rel.includes('\0')) return null
          const target = resolve(docsDir, rel)
          if (!target.startsWith(docsDir + sep) || !existsSync(target)) return null
          return target
        }

        // GET /__api/read?path=xxx —— 读取文件内容（供编辑框加载）
        if (url.startsWith('/__api/read')) {
          if (r.method !== 'GET') {
            w.statusCode = 405
            w.end?.('method not allowed')
            return
          }
          const target = resolveTarget()
          if (!target) {
            w.statusCode = 400
            w.end?.(JSON.stringify({ ok: false, error: 'path not allowed or missing' }))
            return
          }
          try {
            w.statusCode = 200
            w.setHeader?.('Content-Type', 'application/json')
            w.end?.(JSON.stringify({ ok: true, content: readFileSync(target, 'utf-8') }))
          } catch (e) {
            w.statusCode = 500
            w.end?.(JSON.stringify({ ok: false, error: (e as Error).message }))
          }
          return
        }

        // POST /__api/edit —— 校验后写回
        // 注意：必须精确匹配 /__api/edit（edit-block 是它的前缀子路由），
        // 且 edit-block 的判断必须排在本分支之前，否则会被吞掉。
        if (url === '/__api/edit') {
          if (r.method !== 'POST') {
            w.statusCode = 405
            w.end?.('method not allowed')
            return
          }
          const chunks: Buffer[] = []
          r.on?.('data', (c) => chunks.push(c))
          r.on?.('end', () => {
            try {
              const body = JSON.parse(Buffer.concat(chunks).toString('utf-8')) as {
                path?: string
                content?: string
              }
              const rel = body.path ?? ''
              if (!rel.endsWith('.md') || rel.includes('\0')) {
                w.statusCode = 400
                w.end?.(JSON.stringify({ ok: false, error: 'invalid path' }))
                return
              }
              const target = resolve(docsDir, rel)
              if (!target.startsWith(docsDir + sep) || !existsSync(target)) {
                w.statusCode = 400
                w.end?.(JSON.stringify({ ok: false, error: 'path not allowed or missing' }))
                return
              }
              try {
                writeSafe(target, body.content ?? '', '/__api/edit')
              } catch (err) {
                w.statusCode = 400
                w.end?.(JSON.stringify({ ok: false, error: (err as Error).message }))
                return
              }
              w.statusCode = 200
              w.setHeader?.('Content-Type', 'application/json')
              w.end?.(JSON.stringify({ ok: true }))
            } catch (e) {
              w.statusCode = 500
              w.end?.(JSON.stringify({ ok: false, error: (e as Error).message }))
            }
          })
          return
        }

        // POST /__api/edit-block —— 单块就地编辑写回（所见即所得）
        if (url.startsWith('/__api/edit-block')) {
          if (r.method !== 'POST') {
            w.statusCode = 405
            w.end?.('method not allowed')
            return
          }
          const chunks: Buffer[] = []
          r.on?.('data', (c) => chunks.push(c))
          r.on?.('end', () => {
            try {
              const body = JSON.parse(Buffer.concat(chunks).toString('utf-8')) as {
                path?: string
                oldText?: string
                newHtml?: string
              }
              const rel = body.path ?? ''
              if (!rel.endsWith('.md') || rel.includes('\0')) {
                w.statusCode = 400
                w.end?.(JSON.stringify({ ok: false, error: 'invalid path' }))
                return
              }
              const target = resolve(docsDir, rel)
              if (!target.startsWith(docsDir + sep) || !existsSync(target)) {
                w.statusCode = 400
                w.end?.(JSON.stringify({ ok: false, error: 'path not allowed or missing' }))
                return
              }
              const oldText = (body.oldText ?? '').trim()
              const newHtml = body.newHtml ?? ''
              if (!oldText || !newHtml) {
                w.statusCode = 400
                w.end?.(JSON.stringify({ ok: false, error: 'empty oldText/newHtml' }))
                return
              }
              const source = readFileSync(target, 'utf-8')
              const tokens = mdParser.parse(source, {})
              // 收集可编辑文本块（扁平 token 流，按序）
              // 注意：段落/标题的正文在 inline token 上，容器类型是 paragraph_open / heading_open
              interface Block {
                start: number
                end: number
                raw: string
                plain: string
                fence: boolean
                heading: boolean
                level: number
              }
              const blocks: Block[] = []
              let pending: { kind: 'paragraph' | 'heading'; level: number; map: [number, number] | null } | null = null
              for (const t of tokens) {
                if (t.type === 'paragraph_open' || t.type === 'heading_open') {
                  pending = {
                    kind: t.type === 'heading_open' ? 'heading' : 'paragraph',
                    level: t.type === 'heading_open' ? Number(t.tag?.[1] ?? 1) : 0,
                    map: t.map,
                  }
                } else if (t.type === 'inline' && pending && t.map) {
                  blocks.push({
                    start: pending.map?.[0] ?? 0,
                    end: pending.map?.[1] ?? 0,
                    raw: t.content,
                    plain: plainText(t.content),
                    fence: false,
                    heading: pending.kind === 'heading',
                    level: pending.level,
                  })
                  pending = null
                } else if (t.type === 'paragraph_close' || t.type === 'heading_close') {
                  pending = null
                } else if (t.type === 'fence') {
                  blocks.push({ start: t.map?.[0] ?? 0, end: t.map?.[1] ?? 0, raw: t.content, plain: t.content.trim(), fence: true, heading: false, level: 0 })
                }
              }
              const idx = blocks.findIndex((b) => b.plain === oldText)
              if (idx < 0) {
                w.statusCode = 404
                w.end?.(JSON.stringify({ ok: false, error: 'block not found (页面已变化？请刷新后重试)' }))
                return
              }
              const block = blocks[idx]
              let newMd = turndown.turndown(newHtml).trim()
              newMd = restoreSpLinks(newMd)
              const lines = source.split(/\r\n|\n/)
              const eol = source.includes('\r\n') ? '\r\n' : '\n'
              if (block.fence) {
                const inner = newMd.replace(/^```[^\n]*\n?/, '').replace(/\n?```\s*$/, '')
                const newLines = inner.split('\n')
                lines.splice(block.start + 1, Math.max(0, block.end - block.start - 2), ...newLines)
              } else if (block.heading) {
                // 标题：保留 # 标记（第一行加 #x，续行不再加）
                const marker = '#'.repeat(block.level) + ' '
                const newLines = newMd.split('\n').map((l, i) => (i === 0 ? marker + l : l))
                lines.splice(block.start, Math.max(1, block.end - block.start), ...newLines)
              } else {
                // 段落/列表项：若原行是列表项，保留列表标记（第一行加标记，续行缩进两格）
                const orig = lines[block.start] ?? ''
                const listMatch = /^(\s*(?:[-*+]|\d+\.)\s+)/.exec(orig)
                const newLines = newMd.split('\n').map((l, i) => {
                  if (i === 0) return listMatch ? listMatch[1] + l : l
                  return listMatch ? '  ' + l : l
                })
                lines.splice(block.start, Math.max(1, block.end - block.start), ...newLines)
              }
              // 行数骤减防御：整页行数不允许跌破原行数 50%（且至少 3 行），防 splice 灾难性误写
              const resultLines = lines.join(eol).split(/\r\n|\n/)
              const originalLines = source.split(/\r\n|\n/).length
              if (
                resultLines.length < 3 ||
                resultLines.length < Math.floor(originalLines * 0.5)
              ) {
                w.statusCode = 400
                w.end?.(
                  JSON.stringify({
                    ok: false,
                    error: `拒绝写回：行数从 ${originalLines} 骤减到 ${resultLines.length}`,
                  }),
                )
                return
              }
              writeSafe(target, lines.join(eol), '/__api/edit-block')
              w.statusCode = 200
              w.setHeader?.('Content-Type', 'application/json')
              w.end?.(JSON.stringify({ ok: true }))
            } catch (e) {
              w.statusCode = 500
              w.end?.(JSON.stringify({ ok: false, error: (e as Error).message }))
            }
          })
          return
        }

        w.statusCode = 404
        w.end?.('not found')
      })
    },
  }
}

/** VuePress 插件：注入 client 端编辑 UI */
export const devEditPlugin = (): Plugin => ({
  name: 'dev-edit',
  clientConfigFile: CLIENT_FILE,
})
