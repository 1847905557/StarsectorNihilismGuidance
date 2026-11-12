// @vitest-environment node

import { FSWatcher, watch } from 'chokidar';
import { join } from 'node:path';
import { fileURLToPath } from 'node:url';
import { describe, expect, it, vi } from 'vitest';
import type { App } from 'vuepress/core';

import { sidebarDiscoveryPlugin } from './sidebar';

vi.mock('chokidar', async (importOriginal) => {
  const actual = await importOriginal<typeof import('chokidar')>();
  return { ...actual, watch: vi.fn(actual.watch) };
});

const docs = fileURLToPath(new URL('../', import.meta.url));

describe('sidebar watcher lifecycle', () => {
  it('cancels a pending restart when VuePress closes the watcher', async () => {
    const watcher = new FSWatcher({});
    vi.mocked(watch).mockReturnValueOnce(watcher);
    const watchers: { close(): Promise<void> }[] = [];
    const restart = vi.fn(async () => {});
    vi.useFakeTimers();

    try {
      await sidebarDiscoveryPlugin().onWatched!({} as App, watchers, restart);
      watcher.emit('add', join(docs, 'chapter999999/README.md'));
      expect(vi.getTimerCount()).toBe(1);

      await watchers[0]!.close();
      await vi.runAllTimersAsync();

      expect(watcher.closed).toBe(true);
      expect(restart).not.toHaveBeenCalled();
    } finally {
      await watcher.close();
      vi.useRealTimers();
    }
  });

  it('combines structural events into one restart and releases its watcher', async () => {
    const watcher = new FSWatcher({});
    vi.mocked(watch).mockReturnValueOnce(watcher);
    const watchers: { close(): Promise<void> }[] = [];
    const restart = vi.fn(async () => {});
    vi.useFakeTimers();

    try {
      await sidebarDiscoveryPlugin().onWatched!({} as App, watchers, restart);
      const path = join(docs, 'chapter999999/section1/README.md');
      watcher.emit('add', path);
      watcher.emit('unlink', path);
      watcher.emit('add', path);
      await vi.advanceTimersByTimeAsync(100);

      expect(restart).toHaveBeenCalledTimes(1);
      await watchers[0]!.close();
      expect(watcher.closed).toBe(true);
      expect(vi.getTimerCount()).toBe(0);
    } finally {
      await watcher.close();
      vi.useRealTimers();
    }
  });
});
