# 自写工具接口

本目录提供文字检查、文档结构检查、示例契约检查和页面编辑器的 Markdown 恢复函数。工具使用根目录的 Node.js 依赖，命令在仓库根目录执行。

## 检查命令

### 文字检查

`check-text.mjs` 检查文本编码、换行及正文和注释中的文字规则：

```powershell
pnpm check:text [PATH...] [--code] [--json] [--verbose] [--help]
```

- `PATH...` 接受多个文件或目录，省略时扫描仓库内支持的文本类型。
- `--code` 将范围限定为工程源码、配置及其测试。
- 正文、行内代码、链接、图片、围栏、注释和字面量按所属语法识别。
- 编码检查覆盖 UTF-8 与 BOM；由 Prettier 管理的文件交由其检查基础排版，其余文本由本工具检查 LF、行尾空白与末尾换行。`.prettierignore` 用于选择基础排版检查方式，文字检查的文件收集遵循下述范围规则。

### 文档结构检查

`check-docs.mjs` 检查 Markdown 标题、围栏、容器、相对链接、教程页面结构和正式维护文档集合：

```powershell
pnpm check:docs [PATH...] [--json] [--verbose] [--help]
```

- `PATH...` 接受多个文件或目录，省略时扫描仓库 Markdown。
- 教程规则按页面在 `docs/` 中的实际位置选择，核对章页、节页、章节引用、图片和故障列表。
- 正式维护文档清单由脚本中的 `guidanceFiles` 定义。默认检查、仓库根目录检查及 `.zcode/` 目录检查核对完整集合，显式单文件检查处理所选文档。

### 示例契约检查

`check-fixtures.mjs` 核对教程完整代码、fixture 文件与 Gradle 清单的对应关系：

```powershell
pnpm check:fixtures [--repo-root PATH] [--json] [--verbose] [--help]
```

- 默认检查工具所在仓库，`--repo-root` 指定另一仓库根目录。
- 每次读取节页、`tutorial-fixtures/units/` 与 `tutorial-fixtures/build.gradle`，核对单元目录、页面编号及 `javaUnitIds`、`shaderUnitIds`。
- Java 围栏通过包名和顶层类型名定位源码；shader 围栏通过单元和内容匹配资源。代码经统一换行与末尾换行处理后比较内容，并双向核对页面与文件。
- `statistics` 返回 `pages`、`fixtureUnits`、`javaFiles`、`shaderFiles`、`completeJavaBlocks`、`matchedJavaBlocks`、`completeShaderBlocks` 和 `matchedShaderBlocks`，分别记录页面、单元、文件及完整代码块与匹配数量。

## 范围与输出

检查命令只读取文件。默认范围以工具所在仓库为根，显式路径相对当前工作目录解析；文件收集遵守各级 `.gitignore`，跳过 Git 元数据和符号链接，并合并重复文件。

- `--help` 输出命令说明与参数。
- 默认输出状态、扫描数、问题文件数、问题条数和执行错误数，并逐条输出诊断；`--verbose` 另列通过文件。
- `--json` 输出结构化检查结果；参数解析等执行异常通过标准错误输出消息。
- 诊断包含 `line`、`column`、`rule` 和 `message`，行列从 `1` 开始，文件路径按仓库根目录显示。

JSON 结果字段：

- `status`：`pass`、`findings` 或 `error`。
- `scanned`、`bytes`：检查记录数与成功读取的原始字节总数。
- `problemFiles`、`problems`：有诊断的文件数与诊断总数。
- `skipped`：文件收集阶段跳过的条目数。
- `changed`、`written`：检查命令均为 `0`，可读摘要中对应 "待修改" 与 "已写入"。
- `files`：文件记录，每项包含 `path`、`bytes` 和 `diagnostics`。
- `errors`：执行错误，每项包含 `path` 和 `message`。
- `statistics`：示例契约检查的专项计数。

退出码为 `0` 时检查通过，为 `1` 时存在规则诊断，为 `2` 时存在参数、读取或解析等执行错误；执行错误优先决定状态和退出码。

## 忽略原因查询

`explain-ignore.mjs` 按文件收集器使用的各级 Git 忽略规则查询指定路径：

```powershell
pnpm explain:ignore PATH... [--json] [--help]
```

- `PATH...` 接受一个或多个文件或目录路径，按当前工作目录解释，支持查询尚未创建的路径。
- 可读输出列出包含或忽略状态；忽略项附带来源 `.gitignore`、行号和匹配规则。
- JSON 返回 `checked`、`ignored` 和 `files`。每项包含 `path`、`ignored`；忽略项另含 `source`、`line` 和 `pattern`。
- 查询成功退出码为 `0`，参数或读取失败为 `2`。

## 页面恢复函数

`restore-markdown.mjs` 导出异步函数，由 `docs/.vuepress/plugin-dev-edit.ts` 直接调用：

```javascript
const result = await restoreMarkdown(html, path, docsRoot);
```

- `html`：页面正文的 HTML 片段。
- `path`：目标 Markdown 文件的绝对路径。
- `docsRoot`：教程 `docs/` 目录的绝对路径。

函数通过 jsdom 恢复行内标记、链接、列表、表格、VuePress 容器、代码语言与高亮行，将章目录恢复为自动列表占位符，并完成文字整理、Markdown 排版与检查。

- 成功返回 `{ ok: true, markdown, assets }`，`markdown` 为恢复后的正文。
- `assets` 收集 data URI 图片，每项为 `{ name, size, base64 }`，分别表示原始文件名、解码后的字节数和 Base64 内容；图片身份由 `title` 文件名核对。
- 失败返回 `{ ok: false, diagnostics }`，`diagnostics` 为原因字符串数组。
- 调用方负责版本检查、图片核对、备份和文件写入。

## 工具测试入口

`pnpm test:tools` 调用 `test-tools.mjs`，按 Git 忽略规则收集 `tools/` 内的共址 `.check.mjs` 文件，再交给 Node.js 内置测试运行器执行。终端直接接收测试输出，命令采用测试进程退出码；进程启动错误使用退出码 `2`。
