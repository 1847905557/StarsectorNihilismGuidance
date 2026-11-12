import { lstat, readdir, readFile, open, rename, chmod, unlink } from 'node:fs/promises';
import { randomUUID } from 'node:crypto';
import { dirname, extname, isAbsolute, join, relative, resolve, sep } from 'node:path';
import { fileURLToPath } from 'node:url';
import ignore from 'ignore';

export const repositoryRoot = fileURLToPath(new URL('../../', import.meta.url));
export const posixPath = (value) => value.split(sep).join('/');
export const displayPath = (path, root = repositoryRoot) => posixPath(relative(root, path)) || '.';
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
export const textNames = new Set(['.gitattributes', '.gitignore', '.node-version', '.prettierrc', 'LICENSE']);
export const isText = (path) => textNames.has(path.split(sep).at(-1)) || textExtensions.has(extname(path).toLowerCase());

/** Collect files once, applying each directory's Git ignore rules and preserving explicit scope. */
export async function collectFiles(paths = [], { root = repositoryRoot, include = () => true } = {}) {
  root = resolve(root);
  const selected = paths.length ? paths.map((path) => resolve(path)) : [root];
  const files = new Map();
  const errors = [];
  let skipped = 0;
  async function walk(path, scopes) {
    try {
      const stat = await lstat(path);
      if (stat.isSymbolicLink() || path === join(root, '.git')) {
        skipped++;
        return;
      }
      const directory = stat.isDirectory();
      let excluded = false;
      for (const { base, rules } of scopes) {
        const rel = posixPath(relative(base, path)) + (directory ? '/' : '');
        if (!rel) continue;
        const match = rules.test(rel);
        excluded = match.ignored || (!match.unignored && excluded);
      }
      if (excluded) {
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
        scopes = [...scopes, { base: path, rules: ignore().add(await readFile(join(path, '.gitignore'), 'utf8')) }];
      }
      for (const entry of entries.sort((a, b) => a.name.localeCompare(b.name, 'en'))) {
        await walk(join(path, entry.name), scopes);
      }
    } catch (error) {
      errors.push({ path: displayPath(path, root), message: error.message });
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
          scopes.push({ base, rules: ignore().add(await readFile(join(base, '.gitignore'), 'utf8')) });
        } catch (error) {
          if (error.code !== 'ENOENT') throw error;
        }
      }
    } catch (error) {
      errors.push({ path: displayPath(path, root), message: error.message });
      continue;
    }
    await walk(path, scopes);
  }
  return { files: [...files.values()].sort(), skipped, errors };
}

export async function readUtf8(path) {
  const bytes = await readFile(path);
  const bom = bytes.subarray(0, 3).equals(Buffer.from([0xef, 0xbb, 0xbf]));
  const text = new TextDecoder('utf-8', { fatal: true, ignoreBOM: true }).decode(bytes.subarray(bom ? 3 : 0));
  return { bytes, text, bom };
}

/** Replace a file from a sibling temporary file; failures retain the original and its permissions. */
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
      if (error.code !== 'ENOENT') throw error;
    });
  }
}
