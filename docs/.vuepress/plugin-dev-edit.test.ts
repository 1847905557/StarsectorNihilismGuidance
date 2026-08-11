// @vitest-environment node

import { createHash } from 'node:crypto'
import {
  mkdtempSync,
  mkdirSync,
  readFileSync,
  rmSync,
  writeFileSync,
} from 'node:fs'
import { tmpdir } from 'node:os'
import { join } from 'node:path'

import { afterEach, describe, expect, it } from 'vitest'

import {
  resolveEditTarget,
  runMarkdownRestore,
  sourceRevision,
  validateRestoredAssets,
} from './plugin-dev-edit'

const temporaryRoots: string[] = []

afterEach(() => {
  temporaryRoots.splice(0).forEach((root) => rmSync(root, { recursive: true, force: true }))
})

const fixture = () => {
  const root = mkdtempSync(join(tmpdir(), 'dev-edit-'))
  temporaryRoots.push(root)
  const docs = join(root, 'docs')
  const pageDir = join(docs, 'chapter1')
  mkdirSync(pageDir, { recursive: true })
  const page = join(pageDir, 'README.md')
  writeFileSync(page, '# 原标题\r\n', 'utf-8')
  return { docs, page }
}

describe('dev edit source binding', () => {
  it('uses the exact source bytes for SHA-256 revisions', () => {
    const bytes = Buffer.from('# 标题\r\n', 'utf-8')
    expect(sourceRevision(bytes)).toBe(createHash('sha256').update(bytes).digest('hex'))
    expect(sourceRevision(Buffer.from('# 标题\n', 'utf-8'))).not.toBe(sourceRevision(bytes))
  })

  it('allows existing Markdown below docs and rejects traversal or other extensions', () => {
    const { docs, page } = fixture()
    expect(resolveEditTarget(docs, 'chapter1/README.md')).toBe(page)
    expect(resolveEditTarget(docs, '../README.md')).toBeNull()
    expect(resolveEditTarget(docs, 'chapter1/missing.md')).toBeNull()
    expect(resolveEditTarget(docs, 'chapter1/image.png')).toBeNull()
  })
})

describe('restore process boundary', () => {
  it('restores Tiptap HTML through the stdin JSON contract', async () => {
    const { docs, page } = fixture()
    const result = await runMarkdownRestore(
      '<h1>新标题</h1><pre data-language="java"><code class="language-java">int value = 1;</code></pre>',
      page,
      docs,
    )

    expect(result.ok).toBe(true)
    expect(result.markdown).toContain('# 新标题')
    expect(result.markdown).toContain('```java')
  })

  it('returns structured diagnostics for an empty document', async () => {
    const { docs, page } = fixture()
    const result = await runMarkdownRestore('', page, docs)
    expect(result.ok).toBe(false)
    expect(result.diagnostics?.length).toBeGreaterThan(0)
  })
})

describe('data URI asset contract', () => {
  it('accepts only an existing byte-identical asset with a local filename', () => {
    const { page } = fixture()
    const bytes = Buffer.from('image-bytes')
    writeFileSync(join(page, '..', 'ship.png'), bytes)
    const asset = { name: 'ship.png', size: bytes.length, base64: bytes.toString('base64') }

    expect(validateRestoredAssets(page, [asset])).toEqual([])
    expect(validateRestoredAssets(page, [{ ...asset, name: '../ship.png' }])).toHaveLength(1)
    expect(validateRestoredAssets(page, [{ ...asset, name: 'missing.png' }])).toHaveLength(1)
    expect(
      validateRestoredAssets(page, [{ ...asset, base64: Buffer.from('changed').toString('base64') }]),
    ).toHaveLength(1)
    expect(readFileSync(join(page, '..', 'ship.png'))).toEqual(bytes)
  })
})
