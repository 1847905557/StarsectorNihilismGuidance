# 项目维护工具

本目录提供文字、文档结构与示例契约检查，以及页面编辑器使用的 Markdown 恢复能力。工具使用 Node.js，与教程站共用根目录依赖和 pnpm 锁文件。

## 环境与命令

Node.js 与 pnpm 版本要求见根 `package.json`。在仓库根目录安装依赖并执行命令：

```powershell
pnpm install --frozen-lockfile
pnpm format:check
pnpm lint
pnpm lint:full
pnpm check:text
pnpm check:docs
pnpm check:fixtures
pnpm check
pnpm test:tools
```

- `format:check` 预览格式问题，`format` 写入同一范围的文字整理与 Prettier 结果。
- `check:text` 检查 UTF-8、BOM、换行、行尾空白及正文和注释中的文字格式。
- `check:docs` 检查 Markdown 标题、围栏、容器、链接、教程结构和正式维护文档集合。
- `check:fixtures` 核对页面完整 Java 类、shader、fixture 文件与 Gradle 显式清单。
- `lint` 执行普通档 ESLint，`lint:full` 执行包含类型信息的完整档，`lint:fix` 应用 ESLint 支持的修复。
- `check` 依次执行格式、普通档 ESLint、文字、文档和 fixture 检查。
- `test:tools` 使用 Node 内置测试运行 `tools/` 下的共址 `.check.mjs`。

指定本次文件或目录范围：

```powershell
pnpm format:check docs/chapter1
pnpm format docs/chapter1
pnpm check:text docs/chapter1
pnpm check:docs docs/chapter1
```

写入前必须先执行同范围 `format:check`，核对目标与预期修改。各工具入口支持 `--help`；检查和格式入口支持 `--json`、`--verbose`。

## 文件范围

- 默认范围从工具所在仓库定位，显式路径按当前工作目录解释。
- 文件收集遵守各级 `.gitignore`，跳过 Git 元数据和符号链接；路径重复按文件身份合并。
- 基础文本整理覆盖选定文本的编码和换行；Prettier 处理正式 Markdown、工具 `.mjs`、根包与格式配置、workspace 配置及页面写回插件和测试。
- 文字检查按文本扩展名和文件名选择输入，代码注释按所属语言识别，字面量保持原内容。
- 文档检查处理范围内的 Markdown，教程约定按 `docs/` 中首页、章页和节页的真实位置选择。
- `.zcode/` 根目录的 8 份正式文档由 `check-docs.mjs` 登记，目录级检查核对完整集合，单文件检查处理所选文档。
- fixture 检查读取完整页面、单元、源码和 Gradle 清单，`--repo-root` 可指定检查仓库。

## ESLint

普通档由根 `eslint.config.mjs` 维护，采用 JavaScript、TypeScript 和 Vue 的推荐规则。完整档由 `eslint.full.config.mjs` 消费同一基础，增加 Promise 等待、异步回调使用、await 对象、异常处理中的返回等待和分支完整性检查。两档都要求零警告。

- 浏览器代码、Node 端插件、工具与配置、测试分别消费所属全局对象和类型环境。
- 类型配置由 `tsconfig.base.json` 交付严格公共选项，各执行环境通过对应配置声明文件范围、模块解析和类型来源。
- `.mjs` 工具在 Node 类型环境中参与完整档检查，同目录的测试使用相同环境。
- 工程源码、工具、配置及其测试进入 ESLint，站点产物和任务工作区按配置排除。
- 配置行为测试验证实际文件范围与环境，并用 5 类违规样例验证普通档和完整档的规则差异。

## 格式与文字

Prettier 配置由根 `.prettierrc` 维护。Markdown 保留正文换行，使用 4 空格缩进，围栏中的源码按原内容交付。教程文字规则见 `.zcode/text-guidelines.md`。

- 正文整理处理中英文间距、括号、引号、省略号和破折号。
- Markdown 行内代码、链接目标、图片身份和围栏内容按实际语法区分。
- Java、JavaScript、GLSL 等源码按注释与字符串分别处理，源码与数据字面量必须保持实际内容。
- 格式入口汇合文字整理、常见问题空行整理与 Prettier 输出，重复执行得到相同结果。
- 文件通过同目录临时文件原子替换，保留已有权限，写入失败保留原文件并清理临时文件。

## 输出与诊断

默认输出状态、扫描文件数、问题文件数、问题条数、待修改数、实际写入数和执行错误数。诊断包含文件、行、列、规则与原因，`--verbose` 同时列出通过文件。

JSON 输出包含 `status`、`scanned`、`bytes`、`problemFiles`、`problems`、`changed`、`written`、`skipped`、`files` 和 `errors`。每个文件记录原始读取字节数及诊断。fixture 的 `statistics` 另列页面、单元、Java、shader 及对应匹配数量。

- `pass` 表示本次检查通过，退出码为 `0`。
- `findings` 表示存在规则问题，退出码为 `1`。
- `error` 表示参数、读取、解析或写入执行错误，退出码为 `2`。
- 格式预览报告待修改文件，实际写入后的问题状态以整理结果为准，改动与写入数量记录实际动作。

## 页面编辑恢复

`restore-markdown.mjs` 导出 `restoreMarkdown(html, path, docsRoot)`，由开发模式页面写回插件直接调用。

恢复过程使用 jsdom 读取编辑 HTML，保留行内标记、章节引用、列表、表格、容器、代码语言与高亮行。结果通过同一格式与文档检查后交付。调用方继续负责版本检查、图片核对、备份和写回。

- 成功结果包含 `ok`、`markdown` 和 `assets`。
- 图片记录包含 `name`、`size` 和 `base64`，图片身份由 title 的原始文件名确定。
- 失败结果包含 `ok: false` 和 `diagnostics`，页面编辑器保留当前内容供处理。

## 实现与测试

- `shared/` 维护文件收集、UTF-8 读取、原子写入、Markdown 解析及命令输出。
- 检查函数返回结构化结果，命令入口负责参数、输出和退出码。
- 同类能力必须复用公共实现，规则消费解析后的内容及原始位置。
- 测试与实现共址，断言公开行为、诊断、文件结果和退出码。
- 文件测试独立创建临时目录并登记释放，覆盖正常内容、可达违规和执行错误。
- 修改恢复能力时同时运行 `pnpm test:dev-edit`，修改站点接入时执行 `pnpm docs:build`。
