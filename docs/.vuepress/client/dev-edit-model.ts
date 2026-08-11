import { Node, mergeAttributes, type Editor, type Extension } from '@tiptap/core'
import CodeBlock from '@tiptap/extension-code-block'
import Image from '@tiptap/extension-image'
import Link from '@tiptap/extension-link'
import { Table, TableCell, TableHeader, TableRow } from '@tiptap/extension-table'
import StarterKit from '@tiptap/starter-kit'

const languageOf = (element: Element): string => {
  const classes = [element.getAttribute('class') ?? '', element.querySelector('code')?.className ?? '']
  for (const value of classes) {
    const match = /(?:^|\s)language-([\w-]+)/.exec(value)
    if (match) return match[1]
  }
  return ''
}

const normalizeCodeBlocks = (root: HTMLElement): void => {
  root.querySelectorAll('div[class*="language-"]').forEach((wrapper) => {
    const source = wrapper.querySelector('pre > code')
    if (!source) return
    const lineNodes = Array.from(source.querySelectorAll(':scope > span.line'))
    const highlighted = lineNodes
      .map((line, index) => (line.classList.contains('highlighted') ? index + 1 : 0))
      .filter(Boolean)
    if (lineNodes.length && !lineNodes[lineNodes.length - 1].textContent) lineNodes.pop()
    const text = lineNodes.length
      ? lineNodes.map((line) => line.textContent ?? '').join('\n')
      : source.textContent ?? ''
    const pre = document.createElement('pre')
    const code = document.createElement('code')
    const language = languageOf(wrapper)
    if (language) {
      code.className = `language-${language}`
      code.dataset.language = language
    }
    if (highlighted.length) code.dataset.highlightLines = highlighted.join(',')
    code.textContent = text
    pre.append(code)
    wrapper.replaceWith(pre)
  })
}

const normalizeContainers = (root: HTMLElement): void => {
  root.querySelectorAll('details.hint-container, div.hint-container').forEach((container) => {
    const classes = Array.from(container.classList)
    const type = container.tagName === 'DETAILS'
      ? 'details'
      : classes.find((value) => value !== 'hint-container') ?? 'info'
    const titleNode = container.querySelector(
      ':scope > summary, :scope > .hint-container-title',
    )
    const replacement = document.createElement('div')
    replacement.dataset.vpContainer = type
    replacement.dataset.vpTitle = titleNode?.textContent?.trim() ?? ''
    Array.from(container.childNodes).forEach((child) => {
      if (child !== titleNode) replacement.append(child.cloneNode(true))
    })
    if (!replacement.textContent?.trim()) replacement.append(document.createElement('p'))
    container.replaceWith(replacement)
  })
}

const normalizeAutomaticSectionList = (root: HTMLElement): void => {
  root.querySelectorAll('h2').forEach((heading) => {
    if (heading.textContent?.trim() !== '本章各节') return
    const list = heading.nextElementSibling
    if (!list?.matches('ul')) return
    const placeholder = document.createElement('div')
    placeholder.dataset.autoSectionList = 'true'
    list.replaceWith(placeholder)
  })
}

export const normalizeEditableContent = (content: HTMLElement): string => {
  const root = content.cloneNode(true) as HTMLElement
  root.querySelectorAll('.header-anchor').forEach((node) => {
    const parent = node.parentElement
    if (parent?.matches('h1, h2, h3, h4, h5, h6')) {
      node.replaceWith(document.createTextNode(node.textContent ?? ''))
    } else {
      node.remove()
    }
  })
  root.querySelectorAll('.line-numbers').forEach((node) => node.remove())
  normalizeCodeBlocks(root)
  normalizeContainers(root)
  normalizeAutomaticSectionList(root)
  return root.innerHTML
}

const DevLink = Link.extend({
  addAttributes() {
    return {
      ...this.parent?.(),
      routeLink: {
        default: false,
        parseHTML: (element: HTMLElement) => element.classList.contains('route-link'),
        renderHTML: (attributes: Record<string, unknown>) =>
          attributes.routeLink ? { class: 'route-link' } : {},
      },
    }
  },
})

const DevCodeBlock = CodeBlock.extend({
  addAttributes() {
    return {
      ...this.parent?.(),
      highlightLines: {
        default: '',
        parseHTML: (element: HTMLElement) => element.getAttribute('data-highlight-lines') ?? '',
        renderHTML: (attributes: Record<string, unknown>) =>
          attributes.highlightLines
            ? { 'data-highlight-lines': String(attributes.highlightLines) }
            : {},
      },
    }
  },
  renderHTML({ node, HTMLAttributes }) {
    const language = node.attrs.language as string | null
    return [
      'pre',
      mergeAttributes(
        language ? { 'data-language': language } : {},
        node.attrs.highlightLines
          ? { 'data-highlight-lines': node.attrs.highlightLines }
          : {},
      ),
      [
        'code',
        mergeAttributes(
          HTMLAttributes,
          language ? { class: `language-${language}`, 'data-language': language } : {},
        ),
        0,
      ],
    ]
  },
})

const alignedCellAttributes = function (this: { parent?: () => Record<string, unknown> }) {
  return {
    ...this.parent?.(),
    textAlign: {
      default: '',
      parseHTML: (element: HTMLElement) => element.style.textAlign || '',
      renderHTML: (attributes: Record<string, unknown>) =>
        attributes.textAlign ? { style: `text-align: ${String(attributes.textAlign)}` } : {},
    },
  }
}

const DevTableCell = TableCell.extend({ addAttributes: alignedCellAttributes })
const DevTableHeader = TableHeader.extend({ addAttributes: alignedCellAttributes })

export const VpContainer = Node.create({
  name: 'vpContainer',
  group: 'block',
  content: 'block+',
  defining: true,
  addAttributes() {
    return {
      type: { default: 'info' },
      title: { default: '' },
    }
  },
  parseHTML() {
    return [
      {
        tag: 'div[data-vp-container]',
        getAttrs: (element) => ({
          type: (element as HTMLElement).dataset.vpContainer || 'info',
          title: (element as HTMLElement).dataset.vpTitle || '',
        }),
      },
    ]
  },
  renderHTML({ node }) {
    return [
      'div',
      {
        'data-vp-container': node.attrs.type,
        'data-vp-title': node.attrs.title,
        class: `hint-container ${node.attrs.type}`,
      },
      0,
    ]
  },
  addNodeView() {
    return ({ node, editor, getPos }) => {
      let currentNode = node
      const dom = document.createElement('div')
      dom.className = `hint-container ${node.attrs.type} dev-edit-container`
      dom.dataset.vpContainer = node.attrs.type
      const title = document.createElement('input')
      title.className = 'dev-edit-container-title'
      title.value = node.attrs.title
      title.placeholder = `${node.attrs.type} 标题`
      title.setAttribute('aria-label', '容器标题')
      const contentDOM = document.createElement('div')
      contentDOM.className = 'dev-edit-container-content'
      dom.append(title, contentDOM)
      title.addEventListener('input', () => {
        const position = getPos()
        if (typeof position !== 'number') return
        editor.view.dispatch(
          editor.state.tr.setNodeMarkup(position, undefined, {
            ...currentNode.attrs,
            title: title.value,
          }),
        )
      })
      return {
        dom,
        contentDOM,
        update(updatedNode) {
          if (updatedNode.type.name !== 'vpContainer') return false
          currentNode = updatedNode
          dom.className = `hint-container ${updatedNode.attrs.type} dev-edit-container`
          if (title.value !== updatedNode.attrs.title) title.value = updatedNode.attrs.title
          return true
        },
        ignoreMutation(mutation) {
          return mutation.target === title
        },
      }
    }
  },
})

export const AutoSectionList = Node.create({
  name: 'autoSectionList',
  group: 'block',
  atom: true,
  selectable: false,
  parseHTML() {
    return [{ tag: 'div[data-auto-section-list="true"]' }]
  },
  renderHTML() {
    return ['div', { 'data-auto-section-list': 'true', contenteditable: 'false' }]
  },
  addNodeView() {
    return () => {
      const dom = document.createElement('div')
      dom.dataset.autoSectionList = 'true'
      dom.className = 'dev-edit-auto-list'
      dom.contentEditable = 'false'
      dom.textContent = '章节列表由系统自动生成'
      return { dom }
    }
  },
})

export const createEditorExtensions = (): Extension[] => [
  StarterKit.configure({
    codeBlock: false,
    link: false,
    heading: { levels: [1, 2, 3, 4, 5, 6] },
  }),
  DevLink.configure({ openOnClick: false }),
  DevCodeBlock.configure({ enableTabIndentation: true, tabSize: 4 }),
  Image.configure({ inline: true, allowBase64: true }),
  Table.configure({ resizable: false }),
  TableRow,
  DevTableHeader,
  DevTableCell,
  VpContainer,
  AutoSectionList,
]

export const insertVpContainer = (editor: Editor, type: string): boolean =>
  editor
    .chain()
    .focus()
    .insertContent({
      type: 'vpContainer',
      attrs: { type, title: '' },
      content: [{ type: 'paragraph' }],
    })
    .run()
