import { Mark, Node, mergeAttributes, type Editor, type Extension } from '@tiptap/core'
import CodeBlock from '@tiptap/extension-code-block'
import Image from '@tiptap/extension-image'
import { BulletList, ListItem, OrderedList } from '@tiptap/extension-list'
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
    if (highlighted.length) {
      const highlightLines = highlighted.join(',')
      code.dataset.highlightLines = highlightLines
      pre.dataset.highlightLines = highlightLines
    }
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

const normalizeListTightness = (root: HTMLElement): void => {
  root.querySelectorAll('ul, ol').forEach((list) => {
    const items = Array.from(list.children).filter((child) => child.matches('li'))
    const tight = items.every((item) =>
      !Array.from(item.children).some((child) => child.matches('p')),
    )
    list.setAttribute('data-dev-list-tight', String(tight))
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
  normalizeListTightness(root)
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
  addNodeView() {
    return ({ node, editor, getPos }) => {
      let currentNode = node
      const dom = document.createElement('div')
      dom.className = 'dev-edit-code'
      const header = document.createElement('div')
      header.className = 'dev-edit-code-header'
      header.contentEditable = 'false'
      const kind = document.createElement('span')
      kind.className = 'dev-edit-code-kind'
      kind.textContent = 'CODE'
      const language = document.createElement('select')
      language.className = 'dev-edit-code-language'
      language.setAttribute('aria-label', '代码语言')
      const commonLanguages = [
        '',
        'text',
        'java',
        'json',
        'csv',
        'yaml',
        'xml',
        'html',
        'css',
        'javascript',
        'typescript',
        'bash',
        'powershell',
        'ini',
        'properties',
        'gradle',
        'glsl',
      ]
      const setLanguageOptions = (value: string) => {
        const values = value && !commonLanguages.includes(value)
          ? [...commonLanguages, value]
          : commonLanguages
        language.replaceChildren(
          ...values.map((item) => {
            const option = document.createElement('option')
            option.value = item
            option.textContent = item ? item.toUpperCase() : 'TEXT'
            return option
          }),
        )
        language.value = value
      }
      const highlightLabel = document.createElement('span')
      highlightLabel.className = 'dev-edit-code-highlight-label'
      highlightLabel.textContent = '高亮'
      const highlights = document.createElement('input')
      highlights.className = 'dev-edit-code-highlights'
      highlights.type = 'text'
      highlights.inputMode = 'numeric'
      highlights.placeholder = '2,4,6'
      highlights.setAttribute('aria-label', '代码高亮行')
      const pre = document.createElement('pre')
      const lineGutter = document.createElement('div')
      lineGutter.className = 'dev-edit-code-line-gutter'
      lineGutter.contentEditable = 'false'
      const contentDOM = document.createElement('code')
      pre.append(lineGutter, contentDOM)
      header.append(kind, language, highlightLabel, highlights)
      dom.append(header, pre)

      const highlightSet = (value: unknown): number[] =>
        String(value ?? '')
          .split(',')
          .map((item) => Number(item.trim()))
          .filter((item) => Number.isInteger(item) && item > 0)
          .filter((item, index, values) => values.indexOf(item) === index)
          .sort((a, b) => a - b)

      const measureLineHeights = () => {
        const buttons = Array.from(lineGutter.children) as HTMLButtonElement[]
        if (!buttons.length) return
        const computed = window.getComputedStyle(contentDOM)
        const parsedLineHeight = Number.parseFloat(computed.lineHeight)
        const lineHeight = Number.isFinite(parsedLineHeight) && parsedLineHeight > 0
          ? parsedLineHeight
          : 19.5
        const textNodes: Text[] = []
        const walker = document.createTreeWalker(contentDOM, NodeFilter.SHOW_TEXT)
        let current: globalThis.Node | null
        while ((current = walker.nextNode())) textNodes.push(current as Text)
        const text = contentDOM.textContent ?? ''
        const gutterTop = lineGutter.getBoundingClientRect().top
        const resolvePosition = (offset: number): [Text, number] | null => {
          let cursor = 0
          for (const textNode of textNodes) {
            const end = cursor + textNode.data.length
            if (offset <= end) return [textNode, Math.max(0, offset - cursor)]
            cursor = end
          }
          const last = textNodes[textNodes.length - 1]
          return last ? [last, last.data.length] : null
        }
        let start = 0
        let fallbackTop = 0
        buttons.forEach((button) => {
          const end = text.indexOf('\n', start)
          const lineEnd = end < 0 ? text.length : end
          const startPosition = resolvePosition(start)
          // End on the last character so the following newline's line box is not included.
          const measuredEnd = lineEnd > start ? lineEnd - 1 : lineEnd
          const endPosition = resolvePosition(measuredEnd)
          let top = fallbackTop
          let measured = false
          if (startPosition && endPosition) {
            const range = document.createRange()
            range.setStart(startPosition[0], startPosition[1])
            range.setEnd(endPosition[0], endPosition[1])
            if (typeof range.getClientRects === 'function') {
              const rects = Array.from(range.getClientRects())
              const visibleRects = rects.filter((rect) => rect.height > 0)
              if (visibleRects.length) {
                measured = true
                top = visibleRects[0].top - gutterTop
                fallbackTop = visibleRects[visibleRects.length - 1].bottom - gutterTop
              }
            }
          }
          button.style.position = 'absolute'
          button.style.top = `${Math.max(0, top)}px`
          button.style.height = `${lineHeight}px`
          if (!measured) fallbackTop += lineHeight
          start = end < 0 ? text.length : end + 1
        })
      }

      const scheduleLineMeasurement = () => {
        if (typeof window.requestAnimationFrame === 'function') {
          window.requestAnimationFrame(measureLineHeights)
        } else {
          measureLineHeights()
        }
      }

      const renderLineNumbers = () => {
        const lines = Math.max(1, currentNode.textContent.split('\n').length)
        const highlighted = new Set(highlightSet(currentNode.attrs.highlightLines))
        lineGutter.replaceChildren(
          ...Array.from({ length: lines }, (_, index) => {
            const number = index + 1
            const button = document.createElement('button')
            button.type = 'button'
            button.className = 'dev-edit-code-line-number'
            button.textContent = String(number)
            button.dataset.line = String(number)
            button.setAttribute('aria-label', `切换第 ${number} 行高亮`)
            button.classList.toggle('active', highlighted.has(number))
            button.addEventListener('mousedown', (event) => event.preventDefault())
            button.addEventListener('click', () => {
              const next = highlighted.has(number)
                ? [...highlighted].filter((item) => item !== number)
                : [...highlighted, number]
              next.sort((a, b) => a - b)
              updateAttributes({ highlightLines: next.join(',') })
            })
            return button
          }),
        )
        scheduleLineMeasurement()
      }

      const updateAttributes = (attributes: Record<string, unknown>) => {
        const position = getPos()
        if (typeof position !== 'number') return
        editor.view.dispatch(
          editor.state.tr.setNodeMarkup(position, undefined, {
            ...currentNode.attrs,
            ...attributes,
          }),
        )
      }
      const syncControls = () => {
        const currentLanguage = String(currentNode.attrs.language ?? '')
        if (language.value !== currentLanguage) setLanguageOptions(currentLanguage)
        const currentHighlights = String(currentNode.attrs.highlightLines ?? '')
        if (highlights.value !== currentHighlights) highlights.value = currentHighlights
        renderLineNumbers()
      }
      setLanguageOptions(String(node.attrs.language ?? ''))
      highlights.value = String(node.attrs.highlightLines ?? '')
      renderLineNumbers()
      const resizeObserver = typeof ResizeObserver === 'function'
        ? new ResizeObserver(scheduleLineMeasurement)
        : null
      resizeObserver?.observe(pre)
      language.addEventListener('change', () => {
        updateAttributes({ language: language.value || null })
      })
      highlights.addEventListener('input', () => {
        const value = highlights.value.trim()
        const valid = !value || /^\d+(?:,\d+)*$/.test(value)
        highlights.setCustomValidity(valid ? '' : '高亮行必须使用逗号分隔的正整数')
        if (valid) updateAttributes({ highlightLines: value })
      })

      return {
        dom,
        contentDOM,
        update(updatedNode) {
          if (updatedNode.type.name !== 'codeBlock') return false
          currentNode = updatedNode
          syncControls()
          return true
        },
        stopEvent(event) {
          return header.contains(event.target as globalThis.Node)
            || lineGutter.contains(event.target as globalThis.Node)
        },
        ignoreMutation(mutation) {
          return header.contains(mutation.target) || lineGutter.contains(mutation.target)
        },
        destroy() {
          resizeObserver?.disconnect()
        },
      }
    }
  },
})

// Inline code must be able to retain an enclosing emphasis mark.
const DevCode = Mark.create({
  name: 'code',
  excludes: '',
  code: true,
  exitable: true,
  parseHTML: () => [{ tag: 'code' }],
  renderHTML: ({ HTMLAttributes }) => ['code', mergeAttributes(HTMLAttributes), 0],
  addCommands() {
    return {
      setCode: () => ({ commands }: { commands: { setMark: (name: string) => boolean } }) =>
        commands.setMark(this.name),
      toggleCode: () => ({ commands }: { commands: { toggleMark: (name: string) => boolean } }) =>
        commands.toggleMark(this.name),
      unsetCode: () => ({ commands }: { commands: { unsetMark: (name: string) => boolean } }) =>
        commands.unsetMark(this.name),
    }
  },
})

const listTightAttributes = function (this: { parent?: () => Record<string, unknown> }) {
  return {
    ...this.parent?.(),
    tight: {
      default: true,
      parseHTML: (element: HTMLElement) => element.getAttribute('data-dev-list-tight') !== 'false',
      renderHTML: (attributes: Record<string, unknown>) => ({
        'data-dev-list-tight': attributes.tight === false ? 'false' : 'true',
      }),
    },
  }
}

const DevBulletList = BulletList.extend({ addAttributes: listTightAttributes })
const DevOrderedList = OrderedList.extend({ addAttributes: listTightAttributes })

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
      const type = document.createElement('select')
      type.className = 'dev-edit-container-type'
      type.setAttribute('aria-label', '容器类型')
      const containerTypes = [
        ['info', 'INFO'],
        ['tip', 'TIP'],
        ['warning', 'WARNING'],
        ['caution', 'CAUTION'],
        ['important', 'IMPORTANT'],
        ['note', 'NOTE'],
        ['details', 'DETAILS'],
      ]
      type.replaceChildren(
        ...containerTypes.map(([value, label]) => {
          const option = document.createElement('option')
          option.value = value
          option.textContent = label
          return option
        }),
      )
      const title = document.createElement('input')
      title.className = 'dev-edit-container-title'
      title.value = node.attrs.title
      title.placeholder = `${node.attrs.type} 标题`
      title.setAttribute('aria-label', '容器标题')
      const contentDOM = document.createElement('div')
      contentDOM.className = 'dev-edit-container-content'
      type.value = node.attrs.type
      dom.append(type, title, contentDOM)
      const updateAttributes = (attributes: Record<string, unknown>) => {
        const position = getPos()
        if (typeof position !== 'number') return
        editor.view.dispatch(
          editor.state.tr.setNodeMarkup(position, undefined, {
            ...currentNode.attrs,
            ...attributes,
          }),
        )
      }
      type.addEventListener('change', () => {
        updateAttributes({ type: type.value })
      })
      title.addEventListener('input', () => {
        updateAttributes({ title: title.value })
      })
      return {
        dom,
        contentDOM,
        update(updatedNode) {
          if (updatedNode.type.name !== 'vpContainer') return false
          currentNode = updatedNode
          dom.className = `hint-container ${updatedNode.attrs.type} dev-edit-container`
          dom.dataset.vpContainer = updatedNode.attrs.type
          if (type.value !== updatedNode.attrs.type) type.value = updatedNode.attrs.type
          if (title.value !== updatedNode.attrs.title) title.value = updatedNode.attrs.title
          title.placeholder = `${updatedNode.attrs.type} 标题`
          return true
        },
        ignoreMutation(mutation) {
          return mutation.target === title || mutation.target === type
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
    code: false,
    link: false,
    listItem: false,
    bulletList: false,
    orderedList: false,
    listKeymap: false,
    heading: { levels: [1, 2, 3, 4, 5, 6] },
  }),
  ListItem,
  DevBulletList,
  DevOrderedList,
  DevLink.configure({ openOnClick: false }),
  DevCode,
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
