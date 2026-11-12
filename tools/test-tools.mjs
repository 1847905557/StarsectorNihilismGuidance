import { spawn } from 'node:child_process';
import { collectFiles } from './shared/files.mjs';

const scope = await collectFiles(['tools'], { include: (path) => path.endsWith('.check.mjs') });
if (scope.errors.length) throw new Error(scope.errors.map((item) => item.message).join('\n'));
const child = spawn(process.execPath, ['--test', ...scope.files], { stdio: 'inherit', windowsHide: true });
child.on('error', (error) => {
  console.error(error.message);
  process.exitCode = 2;
});
child.on('exit', (code) => {
  process.exitCode = code ?? 2;
});
