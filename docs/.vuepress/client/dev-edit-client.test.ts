// @vitest-environment jsdom

import { createApp, nextTick, ref, type App } from 'vue';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import clientConfig from './dev-edit-client';

const editors = vi.hoisted(
  () =>
    [] as {
      options: { onUpdate: () => void };
      destroy: ReturnType<typeof vi.fn>;
      setEditable: ReturnType<typeof vi.fn>;
    }[],
);
vi.mock('@tiptap/core', () => ({
  Editor: class {
    options;
    destroy = vi.fn();
    setEditable = vi.fn();
    constructor(options: { onUpdate: () => void }) {
      this.options = options;
      editors.push(this);
    }
    getHTML() {
      return '<h1>Edited page</h1>';
    }
    isActive() {
      return false;
    }
  },
}));
vi.mock('./dev-edit-model.js', () => ({
  createEditorExtensions: () => [],
  insertVpContainer: () => true,
  normalizeEditableContent: (content: HTMLElement) => content.innerHTML,
}));
vi.mock('vuepress/client', () => ({
  defineClientConfig: (config: unknown) => config,
  usePageData: () => page,
}));

const page = ref({ filePathRelative: 'README.md', devEditRevision: 'old' });
let app: App | undefined;
let resolveResponse: (response: Response) => void;
const button = (label: string) => Array.from(document.querySelectorAll('button')).find((node) => node.textContent === label)!;
const respond = async (body: Record<string, unknown>, ok = true) => {
  resolveResponse({ ok, status: ok ? 200 : 500, json: () => Promise.resolve(body) } as Response);
  await vi.advanceTimersByTimeAsync(0);
  await nextTick();
};
beforeEach(async () => {
  vi.useFakeTimers();
  page.value = { filePathRelative: 'README.md', devEditRevision: 'old' };
  editors.length = 0;
  vi.stubGlobal(
    'fetch',
    vi.fn(
      () =>
        new Promise<Response>((resolve) => {
          resolveResponse = resolve;
        }),
    ),
  );
  document.body.innerHTML = '<main id="content"><h1>Original</h1></main><div id="host"></div>';
  app = createApp(clientConfig.rootComponents![0]!);
  app.mount('#host');
  button('编辑页面').click();
  await nextTick();
  editors[0]!.options.onUpdate();
  await nextTick();
});
afterEach(() => {
  app?.unmount();
  app = undefined;
  vi.clearAllTimers();
  vi.useRealTimers();
  vi.unstubAllGlobals();
  document.body.innerHTML = '';
});
const submit = async () => {
  button('提交').click();
  await nextTick();
};

describe('save response ownership', () => {
  for (const ok of [true, false]) {
    it('ignores a completed request after navigation, success=' + ok, async () => {
      const original = editors[0]!;
      await submit();
      page.value = { filePathRelative: 'other.md', devEditRevision: 'other' };
      await nextTick();
      expect(original.destroy).toHaveBeenCalledTimes(1);
      await respond(ok ? { ok: true, revision: 'saved' } : { ok: false, error: 'failure' }, ok);
      expect(original.setEditable).toHaveBeenCalledTimes(1);
      expect(document.querySelector('.dev-edit-status')).toBeNull();
      expect(vi.getTimerCount()).toBe(0);
    });
  }
  it('ignores a failed request after the component unmounts', async () => {
    const original = editors[0]!;
    await submit();
    app?.unmount();
    app = undefined;
    await respond({ ok: false, error: 'failure' }, false);
    expect(original.destroy).toHaveBeenCalledTimes(1);
    expect(original.setEditable).toHaveBeenCalledTimes(1);
    expect(vi.getTimerCount()).toBe(0);
  });
  it('preserves a new editing instance when the cancelled request fails', async () => {
    await submit();
    document.querySelector<HTMLButtonElement>('button[title="取消"]')!.click();
    await nextTick();
    button('确定放弃').click();
    await nextTick();
    button('编辑页面').click();
    await nextTick();
    const current = editors[1]!;
    await respond({ ok: false, error: 'old failure' }, false);
    expect(current.destroy).not.toHaveBeenCalled();
    expect(current.setEditable).not.toHaveBeenCalled();
    expect(document.querySelector('.dev-edit-status')).toBeNull();
  });
});

describe('save and HMR ordering', () => {
  it('restores the current page when saving produces the same source revision', async () => {
    await submit();
    await respond({ ok: true, revision: 'old' });

    expect(editors[0]!.destroy).toHaveBeenCalledTimes(1);
    expect(document.querySelector('#content')?.innerHTML).toBe('<h1>Original</h1>');
    expect(vi.getTimerCount()).toBe(0);
  });

  it('finishes immediately when the page version arrives before the response', async () => {
    await submit();
    page.value.devEditRevision = 'saved';
    await nextTick();
    await respond({ ok: true, revision: 'saved' });
    expect(editors[0]!.destroy).toHaveBeenCalledTimes(1);
    expect(document.querySelector('.dev-edit-content')).toBeNull();
    expect(vi.getTimerCount()).toBe(0);
  });
  it('finishes and clears the timeout when the response arrives first', async () => {
    await submit();
    await respond({ ok: true, revision: 'saved' });
    expect(document.querySelector('.dev-edit-status')?.textContent).toContain('正在等待页面热更新');
    page.value.devEditRevision = 'saved';
    await nextTick();
    expect(editors[0]!.destroy).toHaveBeenCalledTimes(1);
    expect(vi.getTimerCount()).toBe(0);
  });
  it('keeps an active failed save editable', async () => {
    await submit();
    await respond({ ok: false, error: 'restore failure' }, false);
    expect(editors[0]!.setEditable.mock.calls.map(([value]) => value)).toEqual([false, true]);
    expect(editors[0]!.destroy).not.toHaveBeenCalled();
    expect(document.querySelector('.dev-edit-status')?.textContent).toContain('restore failure');
  });
});
