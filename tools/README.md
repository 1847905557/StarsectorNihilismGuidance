# 项目维护工具

本目录提供教程文本、Markdown、规范和 fixture 的检查与维护工具，使用 Python 3.10+ 标准库。命令示例从仓库根目录执行。

## 选择检查范围

带 `PATH...` 的工具接受一个或多个文件、目录。显式相对路径按当前工作目录解释；默认范围由脚本所在位置确定。文本、Markdown 格式、哈希与维护入口默认扫描仓库，故障格式与教程结构默认扫描 `docs/`，规范和 fixture 检查使用各自固定入口。

通用扫描跳过 `.git`、`node_modules`、`build`、`dist`、Python 缓存及配置中列出的缓存目录，并跳过符号链接。文本工具使用扩展名与文件名白名单；Markdown 工具处理 `.md`；哈希工具处理扫描范围内全部文件。规范检查依赖本地 [.zcode 核验入口](../.zcode/validation.md) 和三份标准。

## 工具速查

| 工具 | 用途与参数 |
|---|---|
| `check_text_format.py PATH...` | 检查 UTF-8、BOM、换行、行尾空白与末尾换行；`--apply` 写入规范化结果 |
| `check_markdown_format.py PATH...` | 检查 Markdown 标记、字符与间距；`--apply` 修复可确定的格式 |
| `check_markdown_structure.py PATH...` | 检查 docs 页面的章页外框、标题、围栏、容器、章节引用、速查表、图片、验证与故障结构 |
| `check_standards.py PATH...` | 检查三份标准和核验入口的文件集合、职责声明、链接、路由与 Unicode 字符总量 |
| `check_fixtures.py` | 核对页面、Java 源码、shader、fixture 目录与 Gradle 清单的双向一致性 |
| `hash_scope.py PATH...` | 记录文件数、字节数与按路径排序的聚合 SHA-256 |
| `text_maintenance.py PATH...` | 编排文本与 Markdown 格式检查，按路径分派教程、规范结构检查；`--apply` 修复后复检 |
| `format_fault_analysis.py PATH...` | 检查故障症状与解决方案之间、故障条目之间的空行；`--apply` 修复条目边界 |
| `restore_markdown.py INDEX_HTML` | 从单个 VuePress HTML 的 `div#content` 恢复 Markdown 和图片；`--out PATH` 写入产物 |

各工具的完整参数见 `--help`。`--json` 输出结构化结果；提供 `--verbose` 的工具可列出逐文件明细。

章页结构检查识别 `docs/chapter数字/README.md`，核对技术基线的存在、唯一性、位置与行格式，以及章介绍、目录标题和自动列表占位符的顺序。节页按验证、故障、参考项和完整成品等适用职责检查。介绍的段落组织、教学范围与作者表达由人工核对；技术基线整行的保留情况通过编辑前后逐字比较确认。

## 检查与写入

检查器默认报告诊断和待修改数量。写入前必须完成同范围 dry-run，并核对修改目标与内容。

`--apply` 工具使用严格 UTF-8 解码和同目录原子写回，保留已有文件权限。写入采用 UTF-8 无 BOM、LF 和末尾单换行。文本工具自动清理 Markdown 散文、普通 txt 文本和无扩展名文本的行尾空白；代码围栏、缩进内容及源码／数据文件的行尾空白保留并报告诊断。Markdown 格式器修复结构、散文与支持语言的可识别注释，代码和数据字面量保持内容，相关字符问题通过诊断报告。

文本和 Markdown 格式器的写入结果包含原始诊断、改动数量、已写入数量，以及 `remaining_files` 中的复检明细；`problems`、`problem_files` 和最终状态采用复检结果。维护入口按文件和诊断去重汇总，并提供各检查器的 `components` 结果。字节数按成功读取的原始文件计算；问题数按诊断条目计算，条目内的 `=N` 表示该类问题的出现次数。

CLI 退出码：`0` 表示最终通过，`1` 表示仍有问题，`2` 表示参数或执行错误。故障格式工具的 dry-run 在存在待修复内容时返回 `1`。哈希工具成功返回 `0`，执行错误返回 `2`。

## HTML 恢复

省略 `--out` 时检查可恢复性并报告待输出产物；成功返回 `0`。`--out` 接受任意 `.md` 文件路径或目录，目录内的 Markdown 文件名为 `README.md`。同内容的已有产物保持原文件。

恢复器处理章节引用、容器、紧凑与宽松列表、表格、代码语言及高亮行。图片按 title 恢复原始文件名；本地构建图片与 data URI 图片写在 Markdown 同目录，同名图片内容冲突会产生诊断。

在线编辑使用 `--stdin-json`：请求包含 `html` 或 UTF-8 HTML 的 `htmlBase64`、源文件 `path` 和 `docsRoot`；响应包含 `ok`，成功时提供 `markdown` 与 `assets`，失败时提供 `diagnostics`。此接口成功退出 `0`、诊断失败退出 `1`，在线编辑调用方负责写回与图片核对。

## 常用命令

检查教程与规范：

```powershell
python -X utf8 -B tools/text_maintenance.py docs
python -X utf8 -B tools/check_fixtures.py
python -X utf8 -B tools/check_standards.py
python -X utf8 -B tools/text_maintenance.py .zcode/standards .zcode/validation.md
```

确认 dry-run 后写入并复检：

```powershell
python -X utf8 -B tools/text_maintenance.py docs --apply
```

执行工具与在线编辑测试：

```powershell
python -X utf8 -B -m unittest discover -s tools/tests -p "test_*.py" -v
pnpm test:dev-edit
```
