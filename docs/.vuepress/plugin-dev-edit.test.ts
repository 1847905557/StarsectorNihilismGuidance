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
  encode,
  resolveEditTarget,
  runMarkdownRestore,
  sourceRevision,
  validateRestoredAssets,
} from './plugin-dev-edit'

const temporaryRoots: string[] = []

afterEach(() => {
  temporaryRoots.splice(0).forEach((root) => rmSync(root, { recursive: true, force: true }))
})

const fixture = (relativeDir = 'chapter1/section1') => {
  const root = mkdtempSync(join(tmpdir(), 'dev-edit-'))
  temporaryRoots.push(root)
  const docs = join(root, 'docs')
  const pageDir = join(docs, relativeDir)
  mkdirSync(pageDir, { recursive: true })
  const page = join(pageDir, 'README.md')
  writeFileSync(page, '# 原标题\r\n', 'utf-8')
  return { docs, page }
}

describe('dev edit source binding', () => {
  it('writes LF with a single terminal newline', () => {
    expect(encode('# 标题\r\n正文\r\n\r\n')).toBe('# 标题\n正文\n')
    expect(encode('# 标题')).toBe('# 标题\n')
  })
  it('uses the exact source bytes for SHA-256 revisions', () => {
    const bytes = Buffer.from('# 标题\r\n', 'utf-8')
    expect(sourceRevision(bytes)).toBe(createHash('sha256').update(bytes).digest('hex'))
    expect(sourceRevision(Buffer.from('# 标题\n', 'utf-8'))).not.toBe(sourceRevision(bytes))
  })

  it('allows existing Markdown below docs and rejects traversal or other extensions', () => {
    const { docs, page } = fixture()
    expect(resolveEditTarget(docs, 'chapter1/section1/README.md')).toBe(page)
    expect(resolveEditTarget(docs, '../README.md')).toBeNull()
    expect(resolveEditTarget(docs, 'chapter1/section1/missing.md')).toBeNull()
    expect(resolveEditTarget(docs, 'chapter1/section1/image.png')).toBeNull()
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

  it('transports complex Unicode Tiptap HTML without JSON ambiguity', async () => {
    const { docs, page } = fixture()
    const result = await runMarkdownRestore(
      '<h1>虚无主义教程 (Nihilism Guidance)</h1>' +
        '<p>调整“此处”这个字，并保留 "双引号"。</p>' +
        '<div data-vp-container="tip" data-vp-title="技能要求？">' +
        '<p><strong>代码</strong>：保留中文属性与标点。</p></div>' +
        '<pre data-language="json"><code class="language-json">{"key": "值"}</code></pre>',
      page,
      docs,
    )

    expect(result.ok).toBe(true)
    expect(result.markdown).toContain('调整 "此处" 这个字')
    expect(result.markdown).toContain('::: tip 技能要求？')
    expect(result.markdown).toContain('{"key": "值"}')
  })

  it('merges continuous marks split by a Tiptap link boundary', async () => {
    const { docs, page } = fixture()
    const result = await runMarkdownRestore(
      '<h1>标题</h1>' +
        '<p><strong>前文</strong><a href="https://example.com/"><strong>链接</strong></a>' +
        '<strong>后文</strong></p>' +
        '<p><em>斜体前</em><a href="https://example.com/"><em>斜体链接</em></a>' +
        '<em>斜体后</em></p>' +
        '<p><s>删除前</s><a href="https://example.com/"><s>删除链接</s></a>' +
        '<s>删除后</s></p>',
      page,
      docs,
    )

    expect(result.ok).toBe(true)
    expect(result.markdown).toContain('**前文[链接](https://example.com/) 后文**')
    expect(result.markdown).toContain('*斜体前[斜体链接](https://example.com/) 斜体后*')
    expect(result.markdown).toContain('~~删除前[删除链接](https://example.com/) 删除后~~')
  })

  it('returns structured diagnostics for an empty document', async () => {
    const { docs, page } = fixture()
    const result = await runMarkdownRestore('', page, docs)
    expect(result.ok).toBe(false)
    expect(result.diagnostics?.length).toBeGreaterThan(0)
  })

  it('restores a chapter baseline, introduction, and automatic section list', async () => {
    const { docs, page } = fixture('chapter1')
    const result = await runMarkdownRestore(
      '<h1>章页</h1><p>本章技术基线：Starsector <code>0.98a-RC8</code>。</p>' +
        '<p>共同主题。</p><p>实际用途。</p><p>理解重点。</p>' +
        '<h2>本章各节</h2><div data-auto-section-list="true"></div>',
      page,
      docs,
    )

    expect(result.ok).toBe(true)
    expect(result.markdown).toBe(
      '# 章页\n\n本章技术基线：Starsector `0.98a-RC8`。\n\n' +
        '共同主题。\n\n实际用途。\n\n理解重点。\n\n' +
        '## 本章各节\n\n<!-- AUTO:SECTION-LIST -->\n',
    )
  })

  it('returns chapter diagnostics when the baseline is missing', async () => {
    const { docs, page } = fixture('chapter1')
    const result = await runMarkdownRestore(
      '<h1>章页</h1><p>章介绍。</p>' +
        '<h2>本章各节</h2><div data-auto-section-list="true"></div>',
      page,
      docs,
    )

    expect(result.ok).toBe(false)
    expect(result.diagnostics).toContain('章页必须包含一个本章技术基线段落；当前 0 个')
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
