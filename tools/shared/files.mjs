import { lstat, readdir, readFile, open, rename, chmod, unlink } from 'node:fs/promises';
import { statSync } from 'node:fs';
import { randomUUID } from 'node:crypto';
import { dirname, extname, isAbsolute, join, relative, resolve, sep } from 'node:path';
import { fileURLToPath } from 'node:url';
import ignore from 'ignore';

export const repositoryRoot = fileURLToPath(new URL('../../', import.meta.url));
/** @param {string} value */
export const posixPath = (value) => value.split(sep).join('/');
/** @param {string} path @param {string} root */
export const displayPath = (path, root = repositoryRoot) => posixPath(relative(root, path)) || '.';
/** @param {string} root @param {string} path */
export const inside = (root, path) => {
  const rel = relative(resolve(root), resolve(path));
  return rel === '' || (!rel.startsWith('..' + sep) && rel !== '..' && !isAbsolute(rel));
};
export const textExtensions = new Set([
  '.bat',
  '.cmd',
  '.cjs',
  '.css',
  '.csv',
  '.frag',
  '.glsl',
  '.gradle',
  '.html',
  '.java',
  '.js',
  '.json',
  '.json5',
  '.jsonc',
  '.md',
  '.mjs',
  '.properties',
  '.ps1',
  '.scss',
  '.ship',
  '.skill',
  '.ts',
  '.txt',
  '.variant',
  '.vert',
  '.vue',
  '.xml',
  '.yaml',
  '.yml',
]);
export const textNames = new Set(['.gitattributes', '.gitignore', '.node-version', '.prettierrc', '.editorconfig', 'LICENSE']);
/** @param {string} path */
export const isText = (path) =>
  textNames.has(/** @type {string} */ (path.split(sep).at(-1))) || textExtensions.has(extname(path).toLowerCase());

/**
 * @typedef {{base: string, path: string, rules: import('ignore').Ignore}} IgnoreScope
 * @typedef {{ignored: boolean, source?: string, line?: number, pattern?: string}} IgnoreDecision
 * @param {string} path
 * @returns {Promise<IgnoreScope>}
 */
async function readIgnoreScope(path) {
  const rules = ignore();
  const content = await readFile(path, 'utf8');
  content.split(/\r?\n/).forEach((pattern, index) => rules.add({ pattern, mark: String(index + 1) }));
  return { base: dirname(path), path, rules };
}

/** @param {string} path @param {boolean} directory @param {IgnoreScope[]} scopes @returns {IgnoreDecision} */
function matchIgnore(path, directory, scopes) {
  /** @type {IgnoreDecision} */
  let decision = { ignored: false };
  for (const scope of scopes) {
    if (!inside(scope.base, path) || path === scope.base) continue;
    const match = scope.rules.test(displayPath(path, scope.base) + (directory ? '/' : ''));
    if (match.ignored) {
      const rule = /** @type {import('ignore').IgnoreRule} */ (match.rule);
      decision = { ignored: true, source: scope.path, line: Number(rule.mark), pattern: rule.pattern };
    } else if (match.unignored) decision = { ignored: false };
  }
  return decision;
}

/** Collect files once, applying each directory's Git ignore rules and preserving explicit scope. */
/** @param {string[]} paths @param {{root?: string, include?: (path: string) => boolean}} config */
export async function collectFiles(paths = [], { root = repositoryRoot, include = () => true } = {}) {
  root = resolve(root);
  const selected = paths.length ? paths.map((path) => resolve(path)) : [root];
  const files = new Map();
  /** @type {import('./cli.mjs').ExecutionError[]} */
  const errors = [];
  let skipped = 0;
  /** @param {string} path @param {IgnoreScope[]} scopes */
  async function walk(path, scopes) {
    try {
      const stat = await lstat(path);
      if (stat.isSymbolicLink() || path === join(root, '.git')) {
        skipped++;
        return;
      }
      const directory = stat.isDirectory();
      if (matchIgnore(path, directory, scopes).ignored) {
        skipped++;
        return;
      }
      if (!directory) {
        if (include(path)) files.set(process.platform === 'win32' ? path.toLowerCase() : path, path);
        else skipped++;
        return;
      }
      const entries = await readdir(path, { withFileTypes: true });
      if (entries.some((entry) => entry.name === '.gitignore' && entry.isFile())) {
        scopes = [...scopes, await readIgnoreScope(join(path, '.gitignore'))];
      }
      for (const entry of entries.sort((a, b) => a.name.localeCompare(b.name, 'en'))) {
        await walk(join(path, entry.name), scopes);
      }
    } catch (error) {
      errors.push({ path: displayPath(path, root), message: /** @type {Error} */ (error).message });
    }
  }
  for (const path of selected) {
    const ancestors = [];
    let current = dirname(path);
    while (inside(root, current)) {
      ancestors.unshift(current);
      if (current === root) break;
      current = dirname(current);
    }
    const scopes = [];
    let linked = false;
    try {
      let ancestor = path;
      while (true) {
        if ((await lstat(ancestor)).isSymbolicLink()) {
          linked = true;
          break;
        }
        const parent = dirname(ancestor);
        if (parent === ancestor) break;
        ancestor = parent;
      }
      if (linked) {
        skipped++;
        continue;
      }
      for (const base of ancestors) {
        try {
          scopes.push(await readIgnoreScope(join(base, '.gitignore')));
        } catch (error) {
          if (/** @type {NodeJS.ErrnoException} */ (error).code !== 'ENOENT') throw error;
        }
      }
    } catch (error) {
      errors.push({ path: displayPath(path, root), message: /** @type {Error} */ (error).message });
      continue;
    }
    await walk(path, scopes);
  }
  return { files: [...files.values()].sort(), skipped, errors };
}

/** @param {string} path */
export async function readUtf8(path) {
  const bytes = await readFile(path);
  const bom = bytes.subarray(0, 3).equals(Buffer.from([0xef, 0xbb, 0xbf]));
  const text = new TextDecoder('utf-8', { fatal: true, ignoreBOM: true }).decode(bytes.subarray(bom ? 3 : 0));
  return { bytes, text, bom };
}

/** Replace a file from a sibling temporary file; failures retain the original and its permissions. */
/** @param {string} path @param {string | Uint8Array} content */
export async function atomicWrite(path, content) {
  const mode = (await lstat(path)).mode;
  const temporary = join(dirname(path), '.' + path.split(sep).at(-1) + '.' + randomUUID() + '.tmp');
  try {
    const handle = await open(temporary, 'wx', mode);
    try {
      await handle.writeFile(content);
      await handle.sync();
    } finally {
      await handle.close();
    }
    await chmod(temporary, mode);
    await rename(temporary, path);
  } finally {
    await unlink(temporary).catch((error) => {
      if (/** @type {NodeJS.ErrnoException} */ (error).code !== 'ENOENT') throw error;
    });
  }
}

/** @param {string} root @returns {Promise<(path: string) => IgnoreDecision>} */
export async function gitIgnoreMatcher(root = repositoryRoot) {
  root = resolve(root);
  const scope = await collectFiles([], { root, include: (path) => path.endsWith(sep + '.gitignore') });
  if (scope.errors.length) throw new Error(scope.errors.map((item) => item.message).join('\n'));
  const rules = await Promise.all(scope.files.sort((a, b) => a.length - b.length).map(readIgnoreScope));
  return (path) => {
    const directoryHint = /[\\/]$/.test(path);
    path = resolve(root, path);
    const directory = directoryHint || statSync(path, { throwIfNoEntry: false })?.isDirectory() === true;
    return matchIgnore(path, directory, rules);
  };
}

/** @param {string} root @returns {Promise<(path: string) => boolean>} */
export async function gitIgnored(root = repositoryRoot) {
  const match = await gitIgnoreMatcher(root);
  return (path) => match(path).ignored;
}

/** @param {string} path @param {string} root */
export function isCode(path, root = repositoryRoot) {
  const name = displayPath(path, root);
  return (
    isText(path) &&
    extname(path) !== '.md' &&
    (/^(?:tools|tests|docs\/\.vuepress|\.github)\//.test(name) ||
      (!name.includes('/') &&
        (/\.(?:[cm]?js|ts|json)$/.test(name) ||
          ['.prettierrc', '.editorconfig', '.gitignore', '.gitattributes', 'pnpm-workspace.yaml'].includes(name))))
  );
}
