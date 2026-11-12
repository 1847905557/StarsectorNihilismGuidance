import { readFileSync, readdirSync } from 'node:fs';
import { dirname, join, relative, sep } from 'node:path';
import { fileURLToPath } from 'node:url';

import { watch } from 'chokidar';
import type { SidebarArrayOptions, SidebarGroupOptions } from '@vuepress/theme-default';
import type { PluginObject } from 'vuepress/core';

const DOCS_DIR = dirname(dirname(fileURLToPath(import.meta.url)));
const STAGE_PATTERN = /^chapter([1-9]\d*)$/u;
const PHASE_PATTERN = /^section([1-9]\d*)$/u;
const STAGE_README_PATTERN = /^chapter[1-9]\d*\/README\.md$/u;
const SIDEBAR_README_PATTERN = /^chapter[1-9]\d*\/(?:section[1-9]\d*\/)?README\.md$/u;
const RESTART_DEBOUNCE_MS = 100;

interface NumberedDirectory {
  name: string;
  ordinal: string;
  path: string;
}

const toPosixPath = (path: string): string => path.split(sep).join('/');

const getNumberedDirectories = (parentPath: string, pattern: RegExp): NumberedDirectory[] =>
  readdirSync(parentPath, { withFileTypes: true })
    .flatMap((entry) => {
      const match = entry.isDirectory() ? pattern.exec(entry.name) : null;

      return match
        ? [
            {
              name: entry.name,
              ordinal: match[1]!,
              path: join(parentPath, entry.name),
            },
          ]
        : [];
    })
    .sort((left, right) => left.ordinal.length - right.ordinal.length || left.ordinal.localeCompare(right.ordinal, 'en'));

const extractTitle = (readmePath: string): string | null => {
  try {
    const firstContentLine = readFileSync(readmePath, 'utf8')
      .split(/\r?\n/u)
      .find((line) => line.trim().length > 0);
    const match = firstContentLine ? /^# (\S(?:.*\S)?)\s*$/u.exec(firstContentLine) : null;

    return match?.[1] ?? null;
  } catch {
    return null;
  }
};

const readTitle = (readmePath: string, kind: '章' | '节'): string | null => {
  const title = extractTitle(readmePath);

  if (!title) {
    const displayPath = toPosixPath(relative(DOCS_DIR, readmePath));
    console.warn(`[sidebar] 跳过${kind} ${displayPath}：README.md 缺失，或首个非空行不是有效 H1。`);
  }

  return title;
};

const discoverSidebar = (): SidebarArrayOptions => {
  const chapterGroups: SidebarGroupOptions[] = [];

  for (const chapter of getNumberedDirectories(DOCS_DIR, STAGE_PATTERN)) {
    const chapterTitle = readTitle(join(chapter.path, 'README.md'), '章');

    if (!chapterTitle) continue;

    const children: string[] = [];

    for (const section of getNumberedDirectories(chapter.path, PHASE_PATTERN)) {
      if (readTitle(join(section.path, 'README.md'), '节')) {
        children.push(`${section.name}/`);
      }
    }

    const chapterLink = `/${chapter.name}/`;
    chapterGroups.push({
      text: chapterTitle,
      link: chapterLink,
      prefix: chapterLink,
      collapsible: true,
      children,
    });
  }

  return [{ text: '首页', link: '/' }, ...chapterGroups];
};

const sidebar = discoverSidebar();

const refreshSidebar = (): void => {
  sidebar.splice(0, sidebar.length, ...discoverSidebar());
};

export const createSidebar = (): SidebarArrayOptions => sidebar;

export const sidebarDiscoveryPlugin = (): PluginObject => ({
  name: 'sidebar-discovery',
  onWatched: (_app, watchers, restart) => {
    const readmeValidity = new Map<string, boolean>();
    let restartTimer: ReturnType<typeof setTimeout> | undefined;

    for (const chapter of getNumberedDirectories(DOCS_DIR, STAGE_PATTERN)) {
      for (const section of getNumberedDirectories(chapter.path, PHASE_PATTERN)) {
        const readmePath = join(section.path, 'README.md');
        const relativePath = toPosixPath(relative(DOCS_DIR, readmePath));
        readmeValidity.set(relativePath, extractTitle(readmePath) !== null);
      }
    }

    const scheduleRestart = (): void => {
      if (restartTimer) clearTimeout(restartTimer);

      restartTimer = setTimeout(() => {
        restartTimer = undefined;
        refreshSidebar();
        void restart().catch((error: unknown) => {
          console.error('[sidebar] 开发服务重启失败：', error);
        });
      }, RESTART_DEBOUNCE_MS);
    };

    const getRelativeReadmePath = (filePath: string): string => toPosixPath(relative(DOCS_DIR, filePath));

    const handleStructureChange = (filePath: string): void => {
      const relativePath = getRelativeReadmePath(filePath);

      if (SIDEBAR_README_PATTERN.test(relativePath)) scheduleRestart();
    };

    const handleContentChange = (filePath: string): void => {
      const relativePath = getRelativeReadmePath(filePath);

      if (STAGE_README_PATTERN.test(relativePath)) {
        scheduleRestart();
        return;
      }

      if (!SIDEBAR_README_PATTERN.test(relativePath)) return;

      const wasValid = readmeValidity.get(relativePath) ?? false;
      const isValid = extractTitle(filePath) !== null;
      readmeValidity.set(relativePath, isValid);

      if (wasValid !== isValid) scheduleRestart();
    };

    const watcher = watch(DOCS_DIR, {
      depth: 2,
      ignoreInitial: true,
    });
    watcher.on('add', handleStructureChange);
    watcher.on('unlink', handleStructureChange);
    watcher.on('change', handleContentChange);
    const ownedWatcher = {
      close: async () => {
        clearTimeout(restartTimer);
        await watcher.close();
      },
    };
    watchers.push(ownedWatcher);
  },
});
