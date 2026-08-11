// @vitest-environment jsdom

import { Editor } from '@tiptap/core'
import { afterEach, describe, expect, it } from 'vitest'

import {
  createEditorExtensions,
  insertVpContainer,
  normalizeEditableContent,
} from './dev-edit-model'

const editors: Editor[] = []

afterEach(() => {
  editors.splice(0).forEach((editor) => editor.destroy())
  document.body.innerHTML = ''
})

const createEditor = (content: string): Editor => {
  const element = document.createElement('div')
  document.body.append(element)
  const editor = new Editor({ element, content, extensions: createEditorExtensions() })
  editors.push(editor)
  return editor
}

describe('normalizeEditableContent', () => {
  it('removes presentation nodes and restores editable code metadata', () => {
    const content = document.createElement('div')
    content.innerHTML = `
      <h1><a class="header-anchor">#</a>标题</h1>
      <div class="language-java line-numbers-mode">
        <pre><code><span class="line">one</span><span class="line highlighted">two</span><span class="line"></span></code></pre>
        <div class="line-numbers"><div class="line-number">1</div></div>
      </div>`

    const normalized = normalizeEditableContent(content)

    expect(normalized).not.toContain('header-anchor')
    expect(normalized).not.toContain('line-numbers')
    expect(normalized).toContain('data-language="java"')
    expect(normalized).toContain('data-highlight-lines="2"')
    expect(normalized).toContain('one\ntwo')
  })

  it('maps containers and the generated section list to structural nodes', () => {
    const content = document.createElement('div')
    content.innerHTML = `
      <h2>本章各节</h2><ul><li><a class="route-link" href="./section1/">第一节</a></li></ul>
      <details class="hint-container details"><summary>展开</summary><p>正文</p></details>`

    const normalized = normalizeEditableContent(content)

    expect(normalized).toContain('data-auto-section-list="true"')
    expect(normalized).not.toContain('<ul>')
    expect(normalized).toContain('data-vp-container="details"')
    expect(normalized).toContain('data-vp-title="展开"')
  })

  it('captures compact and loose list state from VuePress HTML', () => {
    const content = document.createElement('div')
    content.innerHTML = `
      <ul><li>紧凑一</li><li>紧凑二</li></ul>
      <ul><li><p>非紧凑一</p></li><li><p>非紧凑二</p></li></ul>`

    const normalized = normalizeEditableContent(content)

    expect(normalized).toContain('<ul data-dev-list-tight="true">')
    expect(normalized).toContain('<ul data-dev-list-tight="false">')
  })
})

describe('Tiptap document contract', () => {
  it('round-trips code, route links, aligned tables, images, and containers', () => {
    const editor = createEditor(`
      <h1>标题</h1>
      <p><a class="route-link" href="./next/">下一节</a></p>
      <pre data-language="java" data-highlight-lines="2"><code class="language-java" data-language="java">one\ntwo</code></pre>
      <table><tbody><tr><th style="text-align: center">列</th><td style="text-align: right">值</td></tr></tbody></table>
      <p><img src="./ship.png" alt="舰船" title="ship.png"></p>
      <div data-vp-container="warning" data-vp-title="注意"><p>正文</p></div>`)

    const html = editor.getHTML()

    expect(html).toContain('class="route-link"')
    expect(html).toContain('data-language="java"')
    expect(html).toContain('data-highlight-lines="2"')
    expect(html).toContain('style="text-align: center;"')
    expect(html).toContain('title="ship.png"')
    expect(html).toContain('data-vp-container="warning"')
    expect(html).toContain('data-vp-title="注意"')
  })

  it('creates a valid empty VuePress container', () => {
    const editor = createEditor('<h1>标题</h1><p>正文</p>')

    expect(insertVpContainer(editor, 'tip')).toBe(true)
    expect(editor.getHTML()).toContain('data-vp-container="tip"')
  })

  it('round-trips list tightness metadata', () => {
    const editor = createEditor(
      '<ul data-dev-list-tight="true"><li>紧凑</li></ul>' +
        '<ul data-dev-list-tight="false"><li><p>非紧凑</p></li></ul>',
    )

    const html = editor.getHTML()

    expect(html).toContain('data-dev-list-tight="true"')
    expect(html).toContain('data-dev-list-tight="false"')
  })

  it('renders editable code metadata without changing saved HTML structure', () => {
    const editor = createEditor(
      '<pre data-language="java" data-highlight-lines="2,4"><code class="language-java">one\ntwo</code></pre>',
    )
    const language = document.querySelector<HTMLSelectElement>('.dev-edit-code-language')
    const highlights = document.querySelector<HTMLInputElement>('.dev-edit-code-highlights')

    expect(document.querySelector('.dev-edit-code-header')?.textContent).toContain('CODE')
    expect(language?.value).toBe('java')
    expect(highlights?.value).toBe('2,4')

    language!.value = 'json'
    language!.dispatchEvent(new Event('change'))
    highlights!.value = '1,3'
    highlights!.dispatchEvent(new Event('input'))

    expect(editor.getHTML()).toContain('data-language="json"')
    expect(editor.getHTML()).toContain('data-highlight-lines="1,3"')
    expect(editor.getHTML()).not.toContain('dev-edit-code-header')
  })
})
