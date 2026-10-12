import { spawn } from 'node:child_process'
import { createHash } from 'node:crypto'
import { fileURLToPath } from 'node:url'
import {
  copyFileSync,
  existsSync,
  mkdirSync,
  readFileSync,
  renameSync,
  writeFileSync,
} from 'node:fs'
import { basename, dirname, join, resolve, sep } from 'node:path'

import type { Plugin } from 'vuepress'

const CLIENT_FILE = fileURLToPath(new URL('./client/dev-edit-client.ts', import.meta.url)).replace(/\\/g, '/')
const RESTORE_SCRIPT = fileURLToPath(
  new URL('../../tools/restore_markdown.py', import.meta.url),
)
const BACKUP_DIR = resolve(process.cwd(), '.zcode', 'tutorial-work', 'backups', 'edit-writes')
const MAX_BODY_BYTES = 8 * 1024 * 1024

interface ReqLike {
  url?: string
  method?: string
  on?: (event: string, callback: (chunk?: Buffer) => void) => void
}

interface ResLike {
  statusCode?: number
  setHeader?: (key: string, value: string) => void
  end?: (body?: string) => void
}

interface RestoreAsset {
  name: string
  size: number
  base64: string
}

interface RestoreResult {
  ok?: boolean
  markdown?: string
  diagnostics?: string[]
  assets?: RestoreAsset[]
}

interface EditRequest {
  path?: string
  revision?: string
  html?: string
}

export const encode = (content: string): string => {
  let normalized = content.replace(/\r\n/g, '\n').replace(/\r/g, '\n')
  return normalized.replace(/\n*$/, '\n')
}

export const sourceRevision = (content: string | Buffer): string =>
  createHash('sha256').update(content).digest('hex')

export const resolveEditTarget = (docsDir: string, relativePath: string): string | null => {
  if (!relativePath.endsWith('.md') || relativePath.includes('\0')) return null
  const target = resolve(docsDir, relativePath)
  return target.startsWith(resolve(docsDir) + sep) && existsSync(target) ? target : null
}

const writeJson = (response: ResLike, status: number, value: unknown): void => {
  response.statusCode = status
  response.setHeader?.('Content-Type', 'application/json; charset=utf-8')
  response.end?.(JSON.stringify(value))
}

const writeSafe = (target: string, content: string): string => {
  if (!content.trim()) throw new Error('拒绝写回：恢复后的 Markdown 为空')
  const encoded = encode(content)
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
  const relativeName = target.split(sep).slice(-3).join('_')
  const backup = join(BACKUP_DIR, `${stamp}_${relativeName}.bak`)
  copyFileSync(target, backup)
  const temporary = `${target}.tmp-${process.pid}`
  writeFileSync(temporary, encoded, { encoding: 'utf-8', flag: 'wx' })
  renameSync(temporary, target)
  const revision = sourceRevision(Buffer.from(encoded, 'utf-8'))
  console.log(
    `[dev-edit] 写回 ${relativeName}（${Buffer.byteLength(encoded, 'utf-8')} 字节），备份 ${backup}`,
  )
  return revision
}

const collectBody = (request: ReqLike): Promise<Buffer> =>
  new Promise((resolveBody, rejectBody) => {
    const chunks: Buffer[] = []
    let size = 0
    let settled = false
    request.on?.('data', (chunk) => {
      if (settled || !chunk) return
      size += chunk.length
      if (size > MAX_BODY_BYTES) {
        settled = true
        rejectBody(new RangeError('request body exceeds 8 MiB'))
        return
      }
      chunks.push(chunk)
    })
    request.on?.('end', () => {
      if (settled) return
      settled = true
      resolveBody(Buffer.concat(chunks))
    })
  })

export const runMarkdownRestore = (
  html: string,
  target: string,
  docsDir: string,
): Promise<RestoreResult> =>
  new Promise((resolveResult, rejectResult) => {
    const executable = process.env.DEV_EDIT_PYTHON || 'python'
    const child = spawn(executable, ['-B', RESTORE_SCRIPT, '--stdin-json'], {
      cwd: process.cwd(),
      stdio: ['pipe', 'pipe', 'pipe'],
      windowsHide: true,
    })
    const stdout: Buffer[] = []
    const stderr: Buffer[] = []
    child.stdout.on('data', (chunk: Buffer) => stdout.push(chunk))
    child.stderr.on('data', (chunk: Buffer) => stderr.push(chunk))
    child.on('error', rejectResult)
    child.on('close', () => {
      const output = Buffer.concat(stdout).toString('utf-8')
      try {
        const result = JSON.parse(output) as RestoreResult
        resolveResult(result)
      } catch (error) {
        const detail = Buffer.concat(stderr).toString('utf-8').trim()
        rejectResult(
          new Error(`恢复器返回了无效 JSON：${(error as Error).message}${detail ? `；${detail}` : ''}`),
        )
      }
    })
    const payload = JSON.stringify({
      htmlBase64: Buffer.from(
        `<main><div id="content">${html}</div></main>`,
        'utf-8',
      ).toString('base64'),
      path: target,
      docsRoot: docsDir,
    })
    child.stdin.end(Buffer.from(payload, 'utf-8'))
  })

export const validateRestoredAssets = (target: string, assets: RestoreAsset[]): string[] => {
  const diagnostics: string[] = []
  for (const asset of assets) {
    if (basename(asset.name) !== asset.name || asset.name.includes('..')) {
      diagnostics.push(`内联图片文件名非法：${asset.name}`)
      continue
    }
    const assetPath = join(dirname(target), asset.name)
    if (!existsSync(assetPath)) {
      diagnostics.push(`在线编辑只允许引用现有图片资产：${asset.name}`)
      continue
    }
    const restored = Buffer.from(asset.base64, 'base64')
    if (restored.length !== asset.size || !readFileSync(assetPath).equals(restored)) {
      diagnostics.push(`内联图片内容与页面资产不一致：${asset.name}`)
    }
  }
  return diagnostics
}

export const devEditVitePlugin = () => {
  const docsDir = resolve(process.cwd(), 'docs')
  return {
    name: 'dev-edit-middleware',
    configureServer(server: {
      middlewares: {
        use: (handler: (request: unknown, response: unknown, next?: () => void) => void) => void
      }
    }) {
      console.log('[dev-edit] full-page middleware registered')
      server.middlewares.use((request, response, next) => {
        const req = request as ReqLike
        const res = response as ResLike
        const url = req.url ?? ''
        if (!url.startsWith('/__api/')) {
          next?.()
          return
        }
        if (url !== '/__api/edit-page') {
          writeJson(res, 404, { ok: false, error: 'not found' })
          return
        }
        if (req.method !== 'POST') {
          writeJson(res, 405, { ok: false, error: 'method not allowed' })
          return
        }

        void collectBody(req)
          .then(async (rawBody) => {
            let body: EditRequest
            try {
              body = JSON.parse(rawBody.toString('utf-8')) as EditRequest
            } catch (error) {
              writeJson(res, 400, { ok: false, error: `invalid JSON: ${(error as Error).message}` })
              return
            }
            const relativePath = body.path ?? ''
            const target = resolveEditTarget(docsDir, relativePath)
            if (!target || typeof body.revision !== 'string' || typeof body.html !== 'string') {
              writeJson(res, 400, { ok: false, error: 'invalid path, revision, or html' })
              return
            }
            const current = readFileSync(target)
            const currentRevision = sourceRevision(current)
            if (body.revision !== currentRevision) {
              writeJson(res, 409, {
                ok: false,
                code: 'revision-conflict',
                error: '源文件已被其他操作修改，请保留当前编辑内容并刷新后处理',
              })
              return
            }

            let restored: RestoreResult
            try {
              restored = await runMarkdownRestore(body.html, target, docsDir)
            } catch (error) {
              writeJson(res, 500, { ok: false, error: (error as Error).message })
              return
            }
            if (!restored.ok || typeof restored.markdown !== 'string') {
              writeJson(res, 422, {
                ok: false,
                code: 'validation-failed',
                diagnostics: restored.diagnostics ?? ['恢复器未返回 Markdown'],
              })
              return
            }
            const assetDiagnostics = validateRestoredAssets(target, restored.assets ?? [])
            if (assetDiagnostics.length) {
              writeJson(res, 422, {
                ok: false,
                code: 'validation-failed',
                diagnostics: assetDiagnostics,
              })
              return
            }
            try {
              const revision = writeSafe(target, restored.markdown)
              writeJson(res, 200, { ok: true, revision })
            } catch (error) {
              writeJson(res, 500, { ok: false, error: (error as Error).message })
            }
          })
          .catch((error) => {
            if (error instanceof RangeError) {
              writeJson(res, 413, { ok: false, error: error.message })
            } else {
              writeJson(res, 500, { ok: false, error: (error as Error).message })
            }
          })
      })
    },
  }
}

export const devEditPlugin = (): Plugin => (app) => ({
  name: 'dev-edit',
  clientConfigFile: app.env.isDev ? CLIENT_FILE : undefined,
  extendsPage: (page) => {
    if (!app.env.isDev || !page.filePath || !existsSync(page.filePath)) return
    ;(page.data as Record<string, unknown>).devEditRevision = sourceRevision(readFileSync(page.filePath))
  },
})
