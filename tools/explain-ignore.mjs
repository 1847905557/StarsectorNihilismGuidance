import { parseArgs } from 'node:util';
import { displayPath, gitIgnoreMatcher, repositoryRoot } from './shared/files.mjs';
import { isMain } from './shared/cli.mjs';

/** @param {string[]} paths @param {{root?: string}} config */
export async function explainIgnore(paths, { root = repositoryRoot } = {}) {
  const match = await gitIgnoreMatcher(root);
  const files = paths.map((path) => {
    const decision = match(path);
    return { path: displayPath(path, root), ...decision, ...(decision.source ? { source: displayPath(decision.source, root) } : {}) };
  });
  return { checked: files.length, ignored: files.filter((file) => file.ignored).length, files };
}

/** @param {string[]} args */
export async function main(args = process.argv.slice(2)) {
  try {
    const { values, positionals } = parseArgs({
      args,
      allowPositionals: true,
      options: { json: { type: 'boolean' }, help: { type: 'boolean' } },
    });
    if (values.help) {
      console.log('查询路径对应的 Git 忽略规则。\n参数：PATH... --json --help');
      return 0;
    }
    if (!positionals.length) throw new Error('请指定至少一个文件或目录路径');
    const summary = await explainIgnore(positionals);
    if (values.json) console.log(JSON.stringify(summary, null, 2));
    else {
      console.log('查询=' + summary.checked + ' 忽略=' + summary.ignored + ' 包含=' + (summary.checked - summary.ignored));
      for (const file of summary.files)
        console.log(file.path + (file.ignored ? '：忽略，' + file.source + ':' + file.line + ' ' + file.pattern : '：包含'));
    }
    return 0;
  } catch (error) {
    console.error(/** @type {Error} */ (error).message);
    return 2;
  }
}

if (isMain(import.meta.url)) process.exitCode = await main();
