import { Editor } from '@tiptap/core';
import {
  AlignCenter,
  AlignLeft,
  AlignRight,
  Bold,
  Braces,
  Code2,
  Columns3,
  Italic,
  List,
  ListOrdered,
  Minus,
  PanelBottom,
  PanelLeft,
  PanelRight,
  PanelTop,
  Quote,
  Redo2,
  RefreshCw,
  Rows3,
  Save,
  Table2,
  TextSelect,
  Trash2,
  Undo2,
  X,
} from '@lucide/vue';
import { defineComponent, h, onBeforeUnmount, ref, watch, type Component } from 'vue';
import { defineClientConfig, usePageData } from 'vuepress/client';

import { createEditorExtensions, insertVpContainer, normalizeEditableContent } from './dev-edit-model.js';

interface DevPageData {
  filePathRelative?: string | null;
  devEditRevision?: string;
}

interface EditResponse {
  ok?: boolean;
  revision?: string;
  code?: string;
  error?: string;
  diagnostics?: string[];
}

const DevEditButton = defineComponent({
  setup() {
    if (!import.meta.env.DEV) return () => null;
    const page = usePageData();
    const editing = ref(false);
    const saving = ref(false);
    const dirty = ref(false);
    const confirmingCancel = ref(false);
    const refreshRequired = ref(false);
    const status = ref('');
    const editorVersion = ref(0);
    let editor: Editor | null = null;
    let contentElement: HTMLElement | null = null;
    let originalHtml = '';
    let editingRevision = '';
    let expectedRevision = '';
    let hmrTimer: ReturnType<typeof setTimeout> | null = null;

    const currentPage = (): DevPageData => page.value as unknown as DevPageData;

    const beforeUnload = (event: BeforeUnloadEvent) => {
      if (!editing.value || !dirty.value) return;
      event.preventDefault();
      event.returnValue = '';
    };

    const blockNavigation = (event: MouseEvent) => {
      if (!editing.value || !dirty.value) return;
      const target = event.target as Element | null;
      if (!target?.closest('a')) return;
      event.preventDefault();
      event.stopPropagation();
      status.value = '请先提交或取消当前编辑';
    };

    const clearHmrTimer = () => {
      if (hmrTimer) clearTimeout(hmrTimer);
      hmrTimer = null;
    };

    const destroyEditor = (restoreOriginal: boolean) => {
      clearHmrTimer();
      editor?.destroy();
      editor = null;
      if (restoreOriginal && contentElement) contentElement.innerHTML = originalHtml;
      window.removeEventListener('beforeunload', beforeUnload);
      document.removeEventListener('click', blockNavigation, true);
      contentElement?.classList.remove('dev-edit-content');
      contentElement = null;
      originalHtml = '';
      editingRevision = '';
      expectedRevision = '';
      editing.value = false;
      saving.value = false;
      dirty.value = false;
      confirmingCancel.value = false;
      refreshRequired.value = false;
      status.value = '';
    };

    const containsNewImage = (event: ClipboardEvent | DragEvent): boolean => {
      const transfer = 'clipboardData' in event ? event.clipboardData : event.dataTransfer;
      if (!transfer) return false;
      if (Array.from(transfer.files).some((file) => file.type.startsWith('image/'))) return true;
      return /<img\b/i.test(transfer.getData('text/html'));
    };

    const enterEditMode = () => {
      const filePath = currentPage().filePathRelative;
      const revision = currentPage().devEditRevision;
      const content = document.querySelector<HTMLElement>('#content');
      if (!filePath || !revision) {
        status.value = '该页面缺少可编辑源文件或源版本';
        return;
      }
      if (!content) {
        status.value = '页面缺少 #content 正文容器';
        return;
      }
      contentElement = content;
      originalHtml = content.innerHTML;
      editingRevision = revision;
      const normalized = normalizeEditableContent(content);
      content.innerHTML = '';
      content.classList.add('dev-edit-content');
      editor = new Editor({
        element: content,
        extensions: createEditorExtensions(),
        content: normalized,
        parseOptions: { preserveWhitespace: 'full' },
        editorProps: {
          attributes: {
            class: 'dev-edit-surface',
            spellcheck: 'false',
          },
          handlePaste: (_view, event) => {
            if (!containsNewImage(event)) return false;
            status.value = '在线编辑只允许移动或删除现有图片';
            return true;
          },
          handleDrop: (_view, event, _slice, moved) => {
            if (moved || !containsNewImage(event)) return false;
            status.value = '在线编辑只允许移动或删除现有图片';
            return true;
          },
        },
        onUpdate: () => {
          dirty.value = true;
          editorVersion.value += 1;
        },
        onSelectionUpdate: () => {
          editorVersion.value += 1;
        },
      });
      window.addEventListener('beforeunload', beforeUnload);
      document.addEventListener('click', blockNavigation, true);
      editing.value = true;
      status.value = '';
    };

    const submit = async () => {
      const filePath = currentPage().filePathRelative;
      if (!editor || !filePath || saving.value) return;
      if (!dirty.value) {
        destroyEditor(true);
        return;
      }
      const savingEditor = editor;
      const isCurrent = () => editor === savingEditor && currentPage().filePathRelative === filePath;
      saving.value = true;
      savingEditor.setEditable(false);
      status.value = '正在恢复并校验整页 Markdown...';
      try {
        const response = await fetch('/__api/edit-page', {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify({
            path: filePath,
            revision: editingRevision,
            html: savingEditor.getHTML(),
          }),
        });
        const data = (await response.json()) as EditResponse;
        if (!isCurrent()) return;
        if (!response.ok || !data.ok || !data.revision) {
          const failure = data.code === 'revision-conflict' ? '版本冲突' : '写回失败';
          const details = data.diagnostics?.join('\n') ?? data.error ?? String(response.status);
          status.value = data.diagnostics?.length ? `${failure}：\n${details}` : `${failure}：${details}`;
          saving.value = false;
          savingEditor.setEditable(true);
          return;
        }
        expectedRevision = data.revision;
        dirty.value = false;
        if (currentPage().devEditRevision === expectedRevision) {
          destroyEditor(expectedRevision === editingRevision);
          return;
        }
        status.value = '已写回，正在等待页面热更新...';
        hmrTimer = setTimeout(() => {
          if (!isCurrent()) return;
          saving.value = false;
          refreshRequired.value = true;
          status.value = '文件已写回，页面热更新超时';
        }, 5000);
      } catch (error) {
        if (!isCurrent()) return;
        status.value = `写回失败：${(error as Error).message}`;
        saving.value = false;
        savingEditor.setEditable(true);
      }
    };

    const cancel = () => {
      if (dirty.value) {
        confirmingCancel.value = true;
        return;
      }
      destroyEditor(true);
    };

    watch(
      () => currentPage().devEditRevision,
      (revision) => {
        if (!editing.value || !expectedRevision || revision !== expectedRevision) return;
        destroyEditor(false);
      },
    );

    watch(
      () => currentPage().filePathRelative,
      (_next, previous) => {
        if (previous && editing.value) destroyEditor(false);
      },
    );

    onBeforeUnmount(() => destroyEditor(false));

    const run = (command: (current: Editor) => boolean) => {
      if (!editor || saving.value) return;
      command(editor);
      editorVersion.value += 1;
    };

    const iconButton = (label: string, icon: Component, command: (current: Editor) => boolean, active = false) =>
      h(
        'button',
        {
          type: 'button',
          class: ['dev-edit-icon-btn', active ? 'active' : ''],
          title: label,
          'aria-label': label,
          disabled: saving.value,
          onMousedown: (event: MouseEvent) => event.preventDefault(),
          onClick: () => run(command),
        },
        [h(icon, { size: 17, strokeWidth: 2 })],
      );

    const tableButton = (label: string, icon: Component, command: (current: Editor) => boolean) =>
      h(
        'button',
        {
          type: 'button',
          class: 'dev-edit-icon-btn dev-edit-table-btn',
          title: label,
          'aria-label': label,
          disabled: saving.value,
          onMousedown: (event: MouseEvent) => event.preventDefault(),
          onClick: () => run(command),
        },
        [h(icon, { size: 16, strokeWidth: 2 })],
      );

    const tableToolbar = () => {
      void editorVersion.value;
      if (!editor?.isActive('table')) return null;
      return h('div', { class: 'dev-edit-table-tools', role: 'toolbar', 'aria-label': '表格编辑' }, [
        tableButton('在上方插入行', PanelTop, (current) => current.chain().focus().addRowBefore().run()),
        tableButton('在下方插入行', PanelBottom, (current) => current.chain().focus().addRowAfter().run()),
        tableButton('删除当前行', Rows3, (current) => current.chain().focus().deleteRow().run()),
        tableButton('在左侧插入列', PanelLeft, (current) => current.chain().focus().addColumnBefore().run()),
        tableButton('在右侧插入列', PanelRight, (current) => current.chain().focus().addColumnAfter().run()),
        tableButton('删除当前列', Columns3, (current) => current.chain().focus().deleteColumn().run()),
        tableButton('删除表格', Trash2, (current) => current.chain().focus().deleteTable().run()),
        tableButton('单元格左对齐', AlignLeft, (current) => current.chain().focus().setCellAttribute('align', 'left').run()),
        tableButton('单元格居中', AlignCenter, (current) => current.chain().focus().setCellAttribute('align', 'center').run()),
        tableButton('单元格右对齐', AlignRight, (current) => current.chain().focus().setCellAttribute('align', 'right').run()),
      ]);
    };

    const blockSelect = () => {
      void editorVersion.value;
      const value = editor?.isActive('heading', { level: 1 })
        ? 'h1'
        : editor?.isActive('heading', { level: 2 })
          ? 'h2'
          : editor?.isActive('heading', { level: 3 })
            ? 'h3'
            : 'p';
      return h(
        'select',
        {
          class: 'dev-edit-select',
          title: '段落类型',
          value,
          disabled: saving.value,
          onChange: (event: Event) => {
            const selected = (event.target as HTMLSelectElement).value;
            run((current) =>
              selected === 'p'
                ? current.chain().focus().setParagraph().run()
                : current
                    .chain()
                    .focus()
                    .setHeading({ level: Number(selected.slice(1)) as 1 | 2 | 3 })
                    .run(),
            );
          },
        },
        [
          h('option', { value: 'p' }, '正文'),
          h('option', { value: 'h1' }, 'H1'),
          h('option', { value: 'h2' }, 'H2'),
          h('option', { value: 'h3' }, 'H3'),
        ],
      );
    };

    const containerSelect = () =>
      h(
        'select',
        {
          class: 'dev-edit-select',
          title: '插入 VuePress 容器',
          value: '',
          disabled: saving.value,
          onChange: (event: Event) => {
            const select = event.target as HTMLSelectElement;
            if (editor && select.value) insertVpContainer(editor, select.value);
            select.value = '';
          },
        },
        [
          h('option', { value: '', disabled: true }, '插入容器'),
          ...['info', 'tip', 'warning', 'caution', 'details'].map((type) => h('option', { value: type }, type)),
        ],
      );

    return () => {
      void editorVersion.value;
      const styles = h('style', {}, [
        '.dev-edit-fab{position:fixed;right:18px;bottom:18px;z-index:9999;display:flex;align-items:flex-end;gap:8px;max-width:calc(100vw - 36px)}',
        '.dev-edit-panel{display:flex;align-items:center;gap:6px;flex-wrap:wrap;justify-content:flex-end;background:var(--vp-c-bg);border:1px solid var(--vp-c-border);border-radius:8px;box-shadow:0 4px 16px var(--vp-c-shadow);padding:8px}',
        '.dev-edit-panel-confirm{flex-direction:column;align-items:flex-end;border-color:var(--vp-c-red-soft)}',
        '.dev-edit-btn,.dev-edit-icon-btn,.dev-edit-select{height:34px;border:1px solid var(--vp-c-border);border-radius:6px;background:var(--vp-c-bg);color:var(--vp-c-text);font:14px var(--font-family)}',
        '.dev-edit-btn{padding:0 12px;cursor:pointer}.dev-edit-icon-btn{width:34px;padding:0;display:grid;place-items:center;cursor:pointer}.dev-edit-select{padding:0 7px}',
        '.dev-edit-btn:hover,.dev-edit-icon-btn:hover,.dev-edit-icon-btn.active,.dev-edit-select:hover{background:var(--vp-c-control-hover);border-color:var(--vp-c-accent)}',
        '.dev-edit-table-tools{display:inline-flex;align-items:center;gap:4px;padding-left:6px;border-left:1px solid var(--vp-c-border)}.dev-edit-table-btn{width:30px;height:30px}',
        '.dev-edit-btn:disabled,.dev-edit-icon-btn:disabled,.dev-edit-select:disabled{opacity:.55;cursor:default}',
        '.dev-edit-btn-primary{background:var(--vp-c-accent-bg);border-color:var(--vp-c-accent-bg);color:var(--vp-c-accent-text)}',
        '.dev-edit-btn-danger{background:var(--vp-c-red-soft);border-color:var(--vp-c-red-soft);color:var(--vp-c-red-text)}',
        '.dev-edit-status{display:block;max-width:520px;max-height:min(50vh,360px);overflow:auto;white-space:pre-wrap;line-height:1.5;color:var(--vp-c-red-text);background:var(--vp-c-bg);border:1px solid var(--vp-c-border);padding:7px 9px;border-radius:6px;box-shadow:0 2px 8px var(--vp-c-shadow);font:13px var(--font-family);overflow-wrap:anywhere}',
        '.dev-edit-content{outline:2px solid var(--vp-c-accent);outline-offset:10px;min-height:40vh}.dev-edit-surface{outline:none;min-height:40vh}',
        '.dev-edit-surface img{cursor:grab}.dev-edit-surface img.ProseMirror-selectednode{outline:3px solid var(--vp-c-accent)}',
        '.dev-edit-container{border:1px solid var(--vp-c-border);padding:12px 16px}.dev-edit-container-type{display:block;height:26px;box-sizing:border-box;max-width:150px;margin-bottom:8px;border:1px solid var(--vp-c-border);border-radius:4px;background:var(--vp-c-bg);color:var(--vp-c-text);font:12px var(--font-family);padding:0 6px;outline:none}.dev-edit-container-type:focus,.dev-edit-container-type:hover{border-color:var(--vp-c-accent)}.dev-edit-container-title{display:block;width:100%;box-sizing:border-box;border:0;border-bottom:1px solid var(--vp-c-border);background:transparent;color:var(--vp-c-text);font-weight:600;padding:4px 0 8px;margin-bottom:8px;outline:none}',
        '.dev-edit-container-content>:first-child{margin-top:0}.dev-edit-container-content>:last-child{margin-bottom:0}',
        '.dev-edit-auto-list{border:1px dashed var(--vp-c-border);color:var(--vp-c-text-mute);padding:10px 12px;border-radius:4px}',
        '.dev-edit-code{margin:16px 0;border:1px solid var(--vp-c-border);border-left:3px solid var(--vp-c-accent);border-radius:6px;overflow:hidden;background:var(--code-c-bg);transition:border-color .15s,box-shadow .15s}',
        '.dev-edit-code:focus-within{border-color:var(--vp-c-accent);box-shadow:0 0 0 2px var(--vp-c-accent-soft)}',
        '.dev-edit-code-header{height:38px;box-sizing:border-box;display:flex;align-items:center;gap:8px;padding:0 10px;background:var(--vp-c-bg-alt);border-bottom:1px solid var(--vp-c-border);color:var(--vp-c-text-mute);font:12px var(--font-family)}',
        '.dev-edit-code-kind{height:22px;display:inline-flex;align-items:center;padding:0 7px;border-radius:4px;background:var(--vp-c-control);color:var(--vp-c-text);font-weight:700}',
        '.dev-edit-code-language,.dev-edit-code-highlights{height:26px;box-sizing:border-box;border:1px solid var(--vp-c-border);border-radius:4px;background:var(--vp-c-bg);color:var(--vp-c-text);font:12px var(--font-family);outline:none}',
        '.dev-edit-code-language{width:126px;padding:0 6px}.dev-edit-code-highlights{width:78px;padding:0 7px}.dev-edit-code-highlight-label{margin-left:auto}',
        '.dev-edit-code-language:focus,.dev-edit-code-highlights:focus{border-color:var(--vp-c-accent)}.dev-edit-code-highlights:invalid{border-color:var(--vp-c-red)}',
        '.dev-edit-code pre{position:relative;display:grid;grid-template-columns:max-content minmax(0,1fr);margin:0!important;border-radius:0!important;min-height:72px;padding:18px 0!important;background:var(--code-c-bg)!important;overflow:hidden}.dev-edit-code-line-gutter{position:relative;padding:0 12px 0 16px;border-right:1px solid var(--vp-c-border);color:var(--vp-c-text-mute);font:13px/1.5 var(--font-family-mono);text-align:right;user-select:none}.dev-edit-code-line-number{display:flex;align-items:flex-start;justify-content:flex-end;box-sizing:border-box;position:absolute;left:0;right:12px;width:auto;min-height:1.5em;padding:0;border:0;background:transparent;color:inherit;font:inherit;line-height:inherit;text-align:inherit;cursor:pointer}.dev-edit-code-line-number:hover,.dev-edit-code-line-number.active{background:var(--vp-c-accent-soft);color:var(--vp-c-accent-text)}.dev-edit-code code{display:block;min-width:0;min-height:36px;padding:0 18px;overflow:auto;white-space:pre-wrap;overflow-wrap:anywhere;font:13px/1.5 var(--font-family-mono)}',
        '@media (max-width:719px){.dev-edit-fab{right:10px;bottom:10px;max-width:calc(100vw - 20px)}.dev-edit-panel{max-height:46vh;overflow:auto}}',
      ]);

      if (!editing.value) {
        return [
          styles,
          h('div', { class: 'dev-edit-fab' }, [
            status.value ? h('span', { class: 'dev-edit-status' }, status.value) : null,
            h('button', { class: 'dev-edit-btn dev-edit-btn-primary', onClick: enterEditMode }, '编辑页面'),
          ]),
        ];
      }

      if (confirmingCancel.value) {
        return [
          styles,
          h('div', { class: 'dev-edit-fab dev-edit-panel dev-edit-panel-confirm' }, [
            h('span', { class: 'dev-edit-status' }, '有未提交的修改，确定放弃？'),
            h('div', { style: { display: 'flex', gap: '8px' } }, [
              h('button', { class: 'dev-edit-btn dev-edit-btn-danger', onClick: () => destroyEditor(true) }, '确定放弃'),
              h('button', { class: 'dev-edit-btn', onClick: () => (confirmingCancel.value = false) }, '继续编辑'),
            ]),
          ]),
        ];
      }

      return [
        styles,
        h('div', { class: 'dev-edit-fab', 'data-testid': 'dev-edit-toolbar' }, [
          status.value ? h('span', { class: 'dev-edit-status' }, status.value) : null,
          h('div', { class: 'dev-edit-panel' }, [
            iconButton('撤销', Undo2, (current) => current.chain().focus().undo().run()),
            iconButton('重做', Redo2, (current) => current.chain().focus().redo().run()),
            blockSelect(),
            iconButton('粗体', Bold, (current) => current.chain().focus().toggleBold().run(), Boolean(editor?.isActive('bold'))),
            iconButton('斜体', Italic, (current) => current.chain().focus().toggleItalic().run(), Boolean(editor?.isActive('italic'))),
            iconButton('行内代码', Braces, (current) => current.chain().focus().toggleCode().run(), Boolean(editor?.isActive('code'))),
            iconButton('清除格式', TextSelect, (current) => current.chain().focus().unsetAllMarks().clearNodes().run()),
            iconButton(
              '无序列表',
              List,
              (current) => current.chain().focus().toggleBulletList().run(),
              Boolean(editor?.isActive('bulletList')),
            ),
            iconButton(
              '有序列表',
              ListOrdered,
              (current) => current.chain().focus().toggleOrderedList().run(),
              Boolean(editor?.isActive('orderedList')),
            ),
            iconButton(
              '引用',
              Quote,
              (current) => current.chain().focus().toggleBlockquote().run(),
              Boolean(editor?.isActive('blockquote')),
            ),
            iconButton(
              '代码围栏',
              Code2,
              (current) => current.chain().focus().toggleCodeBlock().run(),
              Boolean(editor?.isActive('codeBlock')),
            ),
            iconButton('表格', Table2, (current) => current.chain().focus().insertTable({ rows: 3, cols: 2, withHeaderRow: true }).run()),
            tableToolbar(),
            iconButton('水平线', Minus, (current) => current.chain().focus().setHorizontalRule().run()),
            containerSelect(),
            refreshRequired.value
              ? iconButton('刷新页面', RefreshCw, () => {
                  location.reload();
                  return true;
                })
              : null,
            h('button', { class: 'dev-edit-btn dev-edit-btn-primary', disabled: saving.value, onClick: submit }, [
              h(Save, { size: 16 }),
              saving.value ? '校验中' : '提交',
            ]),
            h('button', { class: 'dev-edit-icon-btn', title: '取消', 'aria-label': '取消', onClick: cancel }, [h(X, { size: 17 })]),
          ]),
        ]),
      ];
    };
  },
});

export default defineClientConfig({
  rootComponents: [DevEditButton],
});
