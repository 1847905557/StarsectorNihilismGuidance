// @vitest-environment jsdom

import { Editor } from '@tiptap/core';
import { afterEach, describe, expect, it } from 'vitest';

import { createEditorExtensions, insertVpContainer, normalizeEditableContent } from './dev-edit-model';

const editors: Editor[] = [];

afterEach(() => {
  editors.splice(0).forEach((editor) => editor.destroy());
  document.body.innerHTML = '';
});

const createEditor = (content: string): Editor => {
  const element = document.createElement('div');
  document.body.append(element);
  const editor = new Editor({ element, content, extensions: createEditorExtensions() });
  editors.push(editor);
  return editor;
};

const nextFrame = (): Promise<void> => new Promise((resolve) => window.requestAnimationFrame(() => resolve()));

describe('normalizeEditableContent', () => {
  it('preserves nested container types, titles and content through Tiptap', () => {
    const content = document.createElement('div');
    content.innerHTML =
      '<div class="hint-container tip"><p class="hint-container-title">Outer</p><p>Outer body</p>' +
      '<details class="hint-container details"><summary>Inner</summary><p>Inner body</p>' +
      '<div class="hint-container warning"><p class="hint-container-title">Leaf</p><p>Leaf body</p></div>' +
      '</details></div>';
    const normalized = normalizeEditableContent(content);
    const editor = createEditor(normalized);
    const html = document.createElement('div');
    html.innerHTML = editor.getHTML();
    const containers = html.querySelectorAll('[data-vp-container]');
    expect(Array.from(containers, (node) => [node.getAttribute('data-vp-container'), node.getAttribute('data-vp-title')])).toEqual([
      ['tip', 'Outer'],
      ['details', 'Inner'],
      ['warning', 'Leaf'],
    ]);
    expect(html.querySelector('[data-vp-container="details"]')?.parentElement?.getAttribute('data-vp-container')).toBe('tip');
    expect(html.querySelector('[data-vp-container="warning"]')?.parentElement?.getAttribute('data-vp-container')).toBe('details');
    expect(html.textContent).toBe('Outer bodyInner bodyLeaf body');
  });

  it('removes presentation nodes and restores editable code metadata', () => {
    const content = document.createElement('div');
    content.innerHTML = `
      <h1><a class="header-anchor">#</a>标题</h1>
      <div class="language-java line-numbers-mode">
        <pre><code><span class="line">one</span><span class="line highlighted">two</span><span class="line"></span></code></pre>
        <div class="line-numbers"><div class="line-number">1</div></div>
      </div>`;

    const normalized = normalizeEditableContent(content);

    expect(normalized).not.toContain('header-anchor');
    expect(normalized).not.toContain('line-numbers');
    expect(normalized).toContain('data-language="java"');
    expect(normalized).toContain('data-highlight-lines="2"');
    expect(normalized).toContain('one\ntwo');
  });

  it('maps containers and the generated section list to structural nodes', () => {
    const content = document.createElement('div');
    content.innerHTML = `
      <h2>本章各节</h2><ul><li><a class="route-link" href="./section1/">第一节</a></li></ul>
      <details class="hint-container details"><summary>展开</summary><p>正文</p></details>`;

    const normalized = normalizeEditableContent(content);

    expect(normalized).toContain('data-auto-section-list="true"');
    expect(normalized).not.toContain('<ul>');
    expect(normalized).toContain('data-vp-container="details"');
    expect(normalized).toContain('data-vp-title="展开"');
  });

  it('captures compact and loose list state from VuePress HTML', () => {
    const content = document.createElement('div');
    content.innerHTML = `
      <ul><li>紧凑一</li><li>紧凑二</li></ul>
      <ul><li><p>非紧凑一</p></li><li><p>非紧凑二</p></li></ul>`;

    const normalized = normalizeEditableContent(content);

    expect(normalized).toContain('<ul data-dev-list-tight="true">');
    expect(normalized).toContain('<ul data-dev-list-tight="false">');
  });
});

describe('Tiptap document contract', () => {
  it('keeps the standard inline code shortcut and preserves enclosing emphasis', () => {
    const editor = createEditor('<p><strong>Inline</strong></p>');
    editor.commands.selectAll();

    expect(editor.commands.keyboardShortcut('Mod-e')).toBe(true);
    expect(editor.getHTML()).toContain('<strong><code>Inline</code></strong>');
    expect(editor.commands.keyboardShortcut('Mod-e')).toBe(true);
    expect(editor.getHTML()).toContain('<strong>Inline</strong>');
  });

  it('converts inline backtick input using the standard code rule', () => {
    const editor = createEditor('<p></p>');

    for (const char of String.fromCharCode(96) + 'Inline' + String.fromCharCode(96)) {
      const handled = editor.view.someProp('handleTextInput', (handler) =>
        handler(editor.view, editor.state.selection.from, editor.state.selection.to, char, () => editor.state.tr.insertText(char)),
      );
      if (!handled) editor.commands.insertContent(char);
    }

    expect(editor.getHTML()).toContain('<code>Inline</code>');
  });

  it('changes native header and cell alignment through table commands', () => {
    const editor = createEditor(
      '<table><tr><th style="text-align:center">Title</th><th>Value</th></tr><tr><td align="right">Body</td><td>Other</td></tr></table>',
    );
    const cellPositions: number[] = [];
    editor.state.doc.descendants((node, position) => {
      if (node.type.name === 'tableHeader' || node.type.name === 'tableCell') cellPositions.push(position + 1);
    });

    editor.commands.setTextSelection(cellPositions[0]!);
    expect(editor.commands.setCellAttribute('align', 'left')).toBe(true);
    editor.commands.setTextSelection(cellPositions[2]!);
    expect(editor.commands.setCellAttribute('align', 'center')).toBe(true);
    const html = document.createElement('div');
    html.innerHTML = editor.getHTML();

    expect(html.querySelector('th')!.style.textAlign).toBe('left');
    expect(html.querySelector('td')!.style.textAlign).toBe('center');
    expect(html.textContent).toBe('TitleValueBodyOther');
  });

  it('clears marks and every nested list level while preserving text', () => {
    const editor = createEditor('<p><strong>Root</strong></p><ul><li><p>Parent</p><ul><li><p><em>Child</em></p></li></ul></li></ul>');
    editor.commands.selectAll();

    expect(editor.chain().unsetAllMarks().clearNodes().run()).toBe(true);
    const html = document.createElement('div');
    html.innerHTML = editor.getHTML();
    expect(Array.from(html.children, (node) => node.tagName)).toEqual(['P', 'P', 'P', 'P']);
    expect(html.textContent).toBe('RootParentChild');
  });

  it('focuses and blurs the editable surface through editor commands', async () => {
    const editor = createEditor('<p>Focus target</p>');

    expect(editor.commands.focus('end', { scrollIntoView: false })).toBe(true);
    await nextFrame();
    expect(document.activeElement).toBe(editor.view.dom);
    expect(editor.commands.blur()).toBe(true);
    await nextFrame();
    expect(document.activeElement).toBe(document.body);
  });

  it('uses computed code line height for every gutter control', async () => {
    const style = document.createElement('style');
    style.textContent = '.dev-edit-code code { font-size: 13px; line-height: 24px; }';
    document.body.append(style);
    createEditor('<pre><code>first\nsecond</code></pre>');
    await nextFrame();

    const code = document.querySelector<HTMLElement>('.dev-edit-code code')!;
    const numbers = document.querySelectorAll<HTMLButtonElement>('.dev-edit-code-line-number');
    expect(window.getComputedStyle(code).lineHeight).toBe('24px');
    expect(Array.from(numbers, (number) => number.style.height)).toEqual(['24px', '24px']);
  });

  it('wraps, switches and splits paragraphs through editor commands', () => {
    const editor = createEditor('<p>First second</p>');

    expect(editor.commands.toggleBlockquote()).toBe(true);
    expect(editor.getHTML()).toContain('<blockquote><p>First second</p></blockquote>');
    expect(editor.commands.toggleBlockquote()).toBe(true);
    expect(editor.commands.toggleBulletList()).toBe(true);
    expect(editor.getHTML()).toContain('<ul data-dev-list-tight="true"><li><p>First second</p></li></ul>');
    expect(editor.commands.toggleOrderedList()).toBe(true);
    expect(editor.getHTML()).toContain('<ol data-dev-list-tight="true"><li><p>First second</p></li></ol>');

    editor.commands.setContent('<p>First second</p>');
    editor.commands.setTextSelection(6);
    expect(editor.commands.splitBlock()).toBe(true);
    expect(editor.getHTML()).toBe('<p>First</p><p> second</p>');
  });

  it('preserves inline code inside bold text', () => {
    const editor = createEditor('<p><strong><code>AddShip</code> 找不到航母</strong></p>');

    expect(editor.getHTML()).toContain('<strong><code>AddShip</code> 找不到航母</strong>');
  });

  it('round-trips code, route links, aligned tables, images, and containers', () => {
    const editor = createEditor(`
      <h1>标题</h1>
      <p><a class="route-link" href="./next/">下一节</a></p>
      <pre data-language="java" data-highlight-lines="2"><code class="language-java" data-language="java">one\ntwo</code></pre>
      <table><tbody><tr><th style="text-align: center">列</th><td style="text-align: right">值</td></tr></tbody></table>
      <p><img src="./ship.png" alt="舰船" title="ship.png"></p>
      <div data-vp-container="warning" data-vp-title="注意"><p>正文</p></div>`);

    const html = editor.getHTML();

    expect(html).toContain('class="route-link"');
    expect(html).toContain('data-language="java"');
    expect(html).toContain('data-highlight-lines="2"');
    expect(html).toContain('style="text-align: center;"');
    expect(html).toContain('title="ship.png"');
    expect(html).toContain('data-vp-container="warning"');
    expect(html).toContain('data-vp-title="注意"');
  });

  it('supports inserting and deleting table rows and columns', () => {
    const editor = createEditor('<table><tbody><tr><th>A</th><th>B</th></tr><tr><td>C</td><td>D</td></tr></tbody></table>');

    editor.commands.setTextSelection(3);
    expect(editor.isActive('table')).toBe(true);
    expect(editor.chain().focus().addRowAfter().run()).toBe(true);
    expect(editor.getHTML()).toMatch(/<tr>[\s\S]*<tr>[\s\S]*<tr>/);
    expect(editor.chain().focus().addColumnAfter().run()).toBe(true);
    expect((editor.getHTML().match(/<(?:th|td)(?:\s|>)/g) ?? []).length).toBe(9);
    expect(editor.chain().focus().deleteRow().run()).toBe(true);
    expect((editor.getHTML().match(/<tr>/g) ?? []).length).toBe(2);
    expect(editor.chain().focus().deleteColumn().run()).toBe(true);
    expect((editor.getHTML().match(/<(?:th|td)(?:\s|>)/g) ?? []).length).toBe(4);
    expect(editor.chain().focus().deleteTable().run()).toBe(true);
    expect(editor.getHTML()).not.toContain('<table>');
  });

  it('creates a valid empty VuePress container', () => {
    const editor = createEditor('<h1>标题</h1><p>正文</p>');

    expect(insertVpContainer(editor, 'tip')).toBe(true);
    expect(editor.getHTML()).toContain('data-vp-container="tip"');
  });

  it('switches a VuePress container type without changing its title or body', () => {
    const editor = createEditor('<div data-vp-container="warning" data-vp-title="甲板设置"><p>正文</p></div>');
    const type = document.querySelector<HTMLSelectElement>('.dev-edit-container-type');

    expect(type?.value).toBe('warning');
    type!.value = 'caution';
    type!.dispatchEvent(new Event('change'));

    expect(editor.getHTML()).toContain('data-vp-container="caution"');
    expect(editor.getHTML()).toContain('data-vp-title="甲板设置"');
    expect(editor.getHTML()).toContain('<p>正文</p>');
    expect(document.querySelector<HTMLElement>('.dev-edit-container')?.className).toContain('caution');
  });

  it('round-trips list tightness metadata', () => {
    const editor = createEditor(
      '<ul data-dev-list-tight="true"><li>紧凑</li></ul>' + '<ul data-dev-list-tight="false"><li><p>非紧凑</p></li></ul>',
    );

    const html = editor.getHTML();

    expect(html).toContain('data-dev-list-tight="true"');
    expect(html).toContain('data-dev-list-tight="false"');
  });

  it('renders editable code metadata without changing saved HTML structure', () => {
    const editor = createEditor('<pre data-language="java" data-highlight-lines="2,4"><code class="language-java">one\ntwo</code></pre>');
    const language = document.querySelector<HTMLSelectElement>('.dev-edit-code-language');
    const highlights = document.querySelector<HTMLInputElement>('.dev-edit-code-highlights');

    expect(document.querySelector('.dev-edit-code-header')?.textContent).toContain('CODE');
    expect(language?.value).toBe('java');
    expect(highlights?.value).toBe('2,4');

    language!.value = 'json';
    language!.dispatchEvent(new Event('change'));
    highlights!.value = '1,3';
    highlights!.dispatchEvent(new Event('input'));

    expect(editor.getHTML()).toContain('data-language="json"');
    expect(editor.getHTML()).toContain('data-highlight-lines="1,3"');
    expect(editor.getHTML()).not.toContain('dev-edit-code-header');
  });

  it('keeps highlight metadata independent across multiple code blocks', () => {
    const editor = createEditor(
      '<pre data-language="csv" data-highlight-lines="2"><code class="language-csv">one\ntwo</code></pre>' +
        '<pre data-language="json" data-highlight-lines="4"><code class="language-json">one\ntwo\nthree\nfour</code></pre>',
    );

    expect(editor.getHTML()).toContain('data-language="csv" data-highlight-lines="2"');
    expect(editor.getHTML()).toContain('data-language="json" data-highlight-lines="4"');
    const inputs = Array.from(document.querySelectorAll<HTMLInputElement>('.dev-edit-code-highlights'));
    expect(inputs.map((input) => input.value)).toEqual(['2', '4']);
  });

  it('restores highlighted lines from VuePress line spans for every code block', () => {
    const content = document.createElement('div');
    content.innerHTML =
      '<div class="language-csv"><pre><code><span class="line">one</span>' +
      '<span class="line highlighted">two</span></code></pre></div>' +
      '<div class="language-json"><pre><code><span class="line">one</span>' +
      '<span class="line">two</span><span class="line highlighted">three</span></code></pre></div>';

    const normalized = normalizeEditableContent(content);
    const editor = createEditor(normalized);
    const inputs = Array.from(document.querySelectorAll<HTMLInputElement>('.dev-edit-code-highlights'));

    expect(normalized).toContain('<pre data-highlight-lines="2">');
    expect(normalized).toContain('<pre data-highlight-lines="3">');
    expect(inputs.map((input) => input.value)).toEqual(['2', '3']);
    expect(editor.getHTML()).toContain('data-highlight-lines="2"');
    expect(editor.getHTML()).toContain('data-highlight-lines="3"');
  });

  it('shows line numbers and toggles highlighted lines from the gutter', () => {
    const editor = createEditor('<pre data-highlight-lines="2,4"><code>one\ntwo\nthree\nfour</code></pre>');
    const numbers = Array.from(document.querySelectorAll<HTMLButtonElement>('.dev-edit-code-line-number'));
    const highlights = document.querySelector<HTMLInputElement>('.dev-edit-code-highlights');

    expect(numbers.map((button) => button.textContent)).toEqual(['1', '2', '3', '4']);
    expect(numbers[1]!.classList.contains('active')).toBe(true);
    expect(numbers[3]!.classList.contains('active')).toBe(true);

    numbers[2]!.click();
    expect(highlights?.value).toBe('2,3,4');
    expect(editor.getHTML()).toContain('data-highlight-lines="2,3,4"');

    document.querySelector<HTMLButtonElement>('[data-line="2"]')!.click();
    expect(highlights?.value).toBe('3,4');
    expect(document.querySelector<HTMLButtonElement>('[data-line="2"]')?.classList.contains('active')).toBe(false);
  });

  it('keeps line numbers aligned with empty and trailing-newline code', () => {
    const editor = createEditor('<pre><code>one\n\n</code></pre>');

    expect(document.querySelectorAll('.dev-edit-code-line-number')).toHaveLength(3);
    editor.commands.setContent('<pre><code></code></pre>');
    expect(document.querySelectorAll('.dev-edit-code-line-number')).toHaveLength(1);
  });
});
