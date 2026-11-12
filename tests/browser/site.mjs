import { mkdir, writeFile } from 'node:fs/promises';
import { join, relative } from 'node:path';
import { repositoryRoot } from '../../tools/shared/files.mjs';

const docs = join(repositoryRoot, '.cache/browser-site/docs');
await mkdir(join(docs, '.vuepress'), { recursive: true });
const config = relative(join(docs, '.vuepress'), join(repositoryRoot, 'docs/.vuepress/config.ts')).replaceAll('\\', '/');
await writeFile(join(docs, '.vuepress/config.ts'), 'export {default} from ' + JSON.stringify(config) + ';\n', 'utf8');
const page =
  '# Browser editing\n\nFirst paragraph.\n\n## Code\n\n' +
  '```java {2}\nint first = 1;\nint second = 2;\n```\n\n' +
  '## Table\n\n| Name | Value |\n| --- | --- |\n| First | One |\n| Second | Two |\n';
await writeFile(join(docs, 'README.md'), page, 'utf8');
await writeFile(join(docs, 'other.md'), '# Other page\n\n[Back](./README.md)\n', 'utf8');
