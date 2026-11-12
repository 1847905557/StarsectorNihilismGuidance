import { parseArgs } from 'node:util';
import { pathToFileURL } from 'node:url';
import { resolve } from 'node:path';

/** @param {string} url */
export const isMain = (url) => process.argv[1] && pathToFileURL(resolve(process.argv[1])).href === url;
/** @param {string[]} args @param {import('node:util').ParseArgsOptionsConfig} extra @returns {Options} */
export function options(args = process.argv.slice(2), extra = {}) {
  const parsed = parseArgs({
    args,
    allowPositionals: true,
    options: { json: { type: 'boolean' }, verbose: { type: 'boolean' }, help: { type: 'boolean' }, ...extra },
  });
  return /** @type {Options} */ ({ ...parsed.values, paths: parsed.positionals });
}
/**
 * @typedef {{line: number, column: number, rule: string, message: string}} Diagnostic
 * @typedef {{path: string, bytes: number, diagnostics: Diagnostic[]}} FileResult
 * @typedef {{path: string, message: string}} ExecutionError
 * @typedef {{scanned?: number, skipped?: number, changed?: number, written?: number, statistics?: Record<string, number>}} Counts
 * @typedef {ReturnType<typeof result>} Summary
 * @typedef {{paths: string[], json?: boolean, verbose?: boolean, help?: boolean, write?: boolean, code?: boolean, 'repo-root'?: string}} Options
 * @param {FileResult[]} files
 * @param {ExecutionError[]} errors
 * @param {Counts} statistics
 */
export function result(files = [], errors = [], statistics = {}) {
  const diagnostics = files.flatMap((file) => file.diagnostics);
  const { scanned = files.length, ...counts } = statistics;
  return {
    status: errors.length ? 'error' : diagnostics.length ? 'findings' : 'pass',
    scanned,
    bytes: files.reduce((total, file) => total + file.bytes, 0),
    problemFiles: files.filter((file) => file.diagnostics.length).length,
    problems: diagnostics.length,
    changed: 0,
    written: 0,
    ...counts,
    files,
    errors,
  };
}
/** @param {Summary} summary */
export const exitCode = (summary) => (summary.errors.length ? 2 : summary.problems ? 1 : 0);
/** @param {Summary} summary @param {{json?: boolean, verbose?: boolean}} config */
export function emit(summary, { json = false, verbose = false } = {}) {
  if (json) {
    process.stdout.write(JSON.stringify(summary, null, 2) + '\n');
    return;
  }
  console.log(
    '状态=' +
      summary.status +
      ' 扫描=' +
      summary.scanned +
      ' 问题文件=' +
      summary.problemFiles +
      ' 问题=' +
      summary.problems +
      ' 待修改=' +
      summary.changed +
      ' 已写入=' +
      summary.written +
      ' 错误=' +
      summary.errors.length,
  );
  if (summary.statistics)
    console.log(
      Object.entries(summary.statistics)
        .map(([key, value]) => key + '=' + value)
        .join(' '),
    );
  for (const file of summary.files) {
    if (verbose && !file.diagnostics.length) console.log(file.path);
    for (const item of file.diagnostics)
      console.log(file.path + ':' + item.line + ':' + item.column + ' [' + item.rule + '] ' + item.message);
  }
  for (const error of summary.errors) console.error(error.path + ': ' + error.message);
}
/** @param {string} url @param {string} description @param {(options: Options) => Promise<Summary>} run @param {import('node:util').ParseArgsOptionsConfig} extra */
export async function command(url, description, run, extra = {}) {
  if (!isMain(url)) return;
  try {
    const config = options(process.argv.slice(2), extra);
    if (config.help) {
      console.log(
        description +
          '\n参数：PATH... --json --verbose --help' +
          Object.keys(extra)
            .map((key) => ' --' + key)
            .join(''),
      );
      return;
    }
    const summary = await run(config);
    emit(summary, config);
    process.exitCode = exitCode(summary);
  } catch (error) {
    console.error(/** @type {Error} */ (error).message);
    process.exitCode = 2;
  }
}
/** @param {string} text @param {number} offset @param {string} rule @param {string} message @returns {Diagnostic} */
export function diagnostic(text, offset, rule, message) {
  const before = text.slice(0, offset);
  return { line: before.split('\n').length, column: offset - before.lastIndexOf('\n'), rule, message };
}
