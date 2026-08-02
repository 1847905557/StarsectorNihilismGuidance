import { defineClientConfig, usePageData } from 'vuepress/client'
import { defineComponent, h, ref } from 'vue'

/**
 * dev-only 就地文字编辑（2026-08-02 重写）：
 * - 右下角常驻按钮 [编辑文字]：点击进入编辑模式；
 * - 编辑模式下正文文字块（段落/标题/列表项/代码块）contenteditable 可直接修改；
 * - 右下角按钮区切换为 [提交] [取消]：提交逐块 POST /__api/edit-block 写回本地
 *   markdown 并刷新页面；取消放弃修改（有未提交修改时先确认）。
 * - 写回安全性在服务端（plugin-dev-edit.ts 的 writeSafe：空内容拒绝、
 *   行数骤减拒绝、写前备份、原子写）。
 * 非 dev 环境不渲染。
 */

interface EditState {
  el: HTMLElement
  oldText: string
  oldHtml: string
  changed: boolean
}

const DevEditButton = defineComponent({
  setup() {
    if (!import.meta.env.DEV) return () => null
    const page = usePageData()
    const editing = ref(false)
    const saving = ref(false)
    const confirmingCancel = ref(false)
    const status = ref('')
    let states: EditState[] = []

    const isEditable = (el: Element): boolean => {
      if (el.matches('p, h1, h2, h3, h4, h5, h6, pre')) return true
      if (el.matches('li')) return !el.querySelector('ul, ol') // 嵌套列表不支持
      return false
    }

    const collectBlocks = (): EditState[] => {
      // 正文容器：默认主题为 .theme-default-content，本站自定义布局为 #content
      const content =
        document.querySelector('.theme-default-content') ??
        document.querySelector('#content') ??
        document.querySelector('main')
      if (!content) return []
      const list: EditState[] = []
      content.querySelectorAll('p, h1, h2, h3, h4, h5, h6, li, pre').forEach((el) => {
        if (!isEditable(el)) return
        const e = el as HTMLElement
        if (!e.innerText.trim()) return
        list.push({ el: e, oldText: e.innerText.trim(), oldHtml: e.innerHTML, changed: false })
      })
      return list
    }

    /** 块当前 HTML：克隆后剔除页面装饰元素（header-anchor），避免污染写回 */
    const blockHtml = (el: HTMLElement): string => {
      const clone = el.cloneNode(true) as HTMLElement
      clone.querySelectorAll('.header-anchor').forEach((n) => n.remove())
      return clone.innerHTML
    }

    const enterEditMode = () => {
      if (!page.value.filePathRelative) {
        status.value = '该页面没有对应的本地 .md 文件'
        return
      }
      states = collectBlocks()
      if (states.length === 0) {
        status.value = '页面内没有可编辑的文字块'
        return
      }
      states.forEach((s) => {
        s.el.setAttribute('contenteditable', 'true')
        s.el.setAttribute('spellcheck', 'false')
        s.el.classList.add('dev-edit-block')
        s.el.addEventListener('input', () => {
          s.changed = s.el.innerText.trim() !== s.oldText
        })
      })
      editing.value = true
      status.value = ''
    }

    const exitEditMode = () => {
      states.forEach((s) => {
        s.el.removeAttribute('contenteditable')
        s.el.removeAttribute('spellcheck')
        s.el.classList.remove('dev-edit-block')
      })
      states = []
      editing.value = false
      saving.value = false
      confirmingCancel.value = false
      status.value = ''
    }

    const submit = async () => {
      if (!page.value.filePathRelative) return
      const changed = states.filter((s) => s.changed)
      if (changed.length === 0) {
        exitEditMode()
        return
      }
      saving.value = true
      status.value = `正在写回（${changed.length} 处）…`
      try {
        for (const s of changed) {
          const resp = await fetch('/__api/edit-block', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({
              path: page.value.filePathRelative,
              oldText: s.oldText,
              newHtml: blockHtml(s.el),
            }),
          })
          const data = (await resp.json()) as { ok?: boolean; error?: string }
          if (!resp.ok || !data.ok) {
            status.value = `写回失败：${data.error ?? resp.status}（刷新后可重试）`
            saving.value = false
            return
          }
        }
        status.value = '已写回 ✓ 正在刷新…'
        setTimeout(() => location.reload(), 600)
      } catch (e) {
        status.value = `写回失败：${(e as Error).message}`
        saving.value = false
      }
    }

    const cancel = () => {
      if (states.some((s) => s.changed)) {
        // 有未提交修改：右下角内联确认，不用浏览器弹窗
        confirmingCancel.value = true
        return
      }
      exitEditMode()
    }

    const confirmDiscard = () => {
      // 还原已修改块的 DOM（所见即所得：取消后页面恢复原样）
      states.forEach((s) => {
        if (s.changed) s.el.innerHTML = s.oldHtml
      })
      exitEditMode()
    }

    return () => {
      // 全部样式走主题 CSS 变量（--vp-*），明亮/黑暗模式自动适配
      const styles = [
        '.dev-edit-block{outline:1px dashed var(--vp-c-accent);outline-offset:2px;cursor:text;border-radius:2px;transition:outline-color .2s,background-color .2s}',
        '.dev-edit-block:hover{outline-color:var(--vp-c-accent-hover);background-color:var(--vp-c-accent-soft)}',
        '.dev-edit-block:focus{outline:2px solid var(--vp-c-accent);background-color:var(--vp-c-accent-soft)}',
        '.dev-edit-fab{position:fixed;right:18px;bottom:18px;z-index:9999;display:flex;align-items:center;gap:8px}',
        '.dev-edit-panel{display:flex;align-items:center;gap:8px;background:var(--vp-c-bg);border:1px solid var(--vp-c-border);border-radius:10px;box-shadow:0 4px 16px var(--vp-c-shadow);padding:8px 12px}',
        '.dev-edit-panel-confirm{flex-direction:column;align-items:flex-end;border-color:var(--vp-c-red-soft);gap:10px}',
        '.dev-edit-btn{padding:8px 14px;border-radius:8px;border:1px solid var(--vp-c-border);background:var(--vp-c-bg);color:var(--vp-c-text);cursor:pointer;font:14px var(--font-family);transition:background-color .2s,border-color .2s,color .2s}',
        '.dev-edit-btn:hover{background:var(--vp-c-control-hover);border-color:var(--vp-c-border-hard)}',
        '.dev-edit-btn:disabled{opacity:.6;cursor:default}',
        '.dev-edit-btn-primary{background:var(--vp-c-accent-bg);border-color:var(--vp-c-accent-bg);color:var(--vp-c-accent-text)}',
        '.dev-edit-btn-primary:hover{background:var(--vp-c-accent-hover);border-color:var(--vp-c-accent-hover)}',
        '.dev-edit-btn-danger{background:var(--vp-c-red-soft);border-color:var(--vp-c-red-soft);color:var(--vp-c-red-text)}',
        '.dev-edit-btn-danger:hover{background:var(--vp-c-red-bg);border-color:var(--vp-c-red-bg)}',
        '.dev-edit-hint{color:var(--vp-c-text-mute);font:13px var(--font-family)}',
        '.dev-edit-status{color:var(--vp-c-red-text);font:13px var(--font-family)}',
        '.dev-edit-confirm-text{color:var(--vp-c-red-text);font:13px var(--font-family)}',
        '.dev-edit-status-bubble{color:var(--vp-c-red-text);background:var(--vp-c-bg);border:1px solid var(--vp-c-border);padding:4px 8px;border-radius:6px;box-shadow:0 2px 8px var(--vp-c-shadow);font:13px var(--font-family)}',
      ]
      const styleEl = h('style', {}, styles)

      if (!editing.value) {
        return [
          styleEl,
          h('div', { class: 'dev-edit-fab' }, [
            status.value
              ? h('span', { class: 'dev-edit-status-bubble' }, status.value)
              : null,
            h('button', { class: 'dev-edit-btn dev-edit-btn-primary', onClick: enterEditMode }, '编辑文字'),
          ]),
        ]
      }

      return [
        styleEl,
        confirmingCancel.value
          ? h(
              'div',
              { class: 'dev-edit-fab dev-edit-panel dev-edit-panel-confirm' },
              [
                h('span', { class: 'dev-edit-confirm-text' }, '有未提交的修改，确定放弃？'),
                h('div', { style: { display: 'flex', gap: '8px' } }, [
                  h('button', { class: 'dev-edit-btn dev-edit-btn-danger', onClick: confirmDiscard }, '确定放弃'),
                  h('button', { class: 'dev-edit-btn', onClick: () => (confirmingCancel.value = false) }, '继续编辑'),
                ]),
              ],
            )
          : h(
              'div',
              { class: 'dev-edit-fab dev-edit-panel' },
              [
                h('span', { class: 'dev-edit-hint' }, '点击文字直接修改'),
                h(
                  'button',
                  { class: 'dev-edit-btn dev-edit-btn-primary', onClick: submit, disabled: saving.value },
                  saving.value ? '提交中…' : '提交',
                ),
                h('button', { class: 'dev-edit-btn', onClick: cancel }, '取消'),
                status.value
                  ? h('span', { class: 'dev-edit-status' }, status.value)
                  : null,
              ],
            ),
      ]
    }
  },
})

export default defineClientConfig({
  rootComponents: [DevEditButton],
})
