# Workflow

本文件维护任务调查、修改和验证协议。项目组成与运行链路见 `.zcode/overview.md`，工具参数见 `tools/README.md`。

## 改动前

- 教程文字与页面组织任务读取 `.zcode/writing-guidelines.md`。
- 涉及文字呈现的任务读取 `.zcode/text-guidelines.md`。
- 技术事实、Java 与 fixture 任务读取 `.zcode/technical-guidelines.md`。
- VuePress、TypeScript 和页面编辑器任务读取 `.zcode/frontend-guidelines.md`。
- 视觉任务读取 `.zcode/css-guidelines.md`。
- 工具任务读取 `tools/README.md`。
- 游戏术语任务读取 `.zcode/terminology.md`。
- 完整页面工作同时阅读写作、文本、技术和术语规范。
- 修改前必须完整阅读工作范围和相似文件，确认工作区状态、实际入口与修改边界。
- 自动写入前必须执行同范围 dry-run，核对目标与结果。

## 问题处理

- 沿对象定义、数据写入、注册或绑定、读取和最终显示取证，追到实际原因。
- 根据相同原因影响的调用与数据关系确定修正范围，同类处理保持一致。
- 客观规则交给机械检查，技术含义和作者表达结合完整上下文判断。

## 验证方式

- 根据修改文件、受影响行为和依赖关系选择检查。
- 启动的命令必须执行完毕，记录完整命令、退出码和结果，根据实际修改或失败安排复检。
- 任何改动运行 `git diff --check`。
- 文本修改对本次范围运行 `pnpm format:check` 与 `pnpm check:text`。
- 正式维护文档与教程页面结构运行 `pnpm check:docs`。
- 完整 Java、shader 或 fixture 修改运行 `pnpm check:fixtures`，Java 修改编译所属单元。
- shader 资源修改运行 `validateShaders`；该任务检查资源目录与非空内容，编译、链接和画面按示例另行验证。
- JavaScript、TypeScript 和 Vue 修改运行 `pnpm lint` 与 `pnpm lint:full`。
- ESLint 配置修改运行两档全仓检查和对应配置行为测试。
- 工具修改运行 `pnpm test:tools`，测试与 `.mjs` 实现共址。
- 页面编辑模型与写回插件修改运行 `test:dev-edit`。
- 站点配置、页面渲染、插件或构建链路修改运行 `docs:build`，交互和视觉变化沿实际页面验证。
- 专项审计按任务保存完整清单与调用、数据及结果证据。
- 验收逐项说明依据，状态采用 `通过`、`失败`、`不适用` 或 `待人工执行`，整体结论覆盖全部必要验证。

## 测试服务器管理

- 启动、重启或关闭服务前，核实所属项目、已有实例、端口和状态，并取得具体操作授权。
- 保留并复用已有可用服务，服务生命周期独立于单次检查。

## 常用命令

```powershell
pnpm docs:dev
pnpm docs:build
pnpm test:dev-edit
pnpm check
pnpm lint:full
pnpm test:tools
gradle -p tutorial-fixtures verifyTutorialFixtures --no-daemon
git diff --check
```

- 文本、Markdown 和教程结构检查显式传入本次文件或目录范围。
- `pnpm format` 整理与预览相同范围的文件，`pnpm check` 按包命令顺序执行各类检查。
- Java 单元编译使用 `gradle -p tutorial-fixtures compileC5s3Java --no-daemon`，任务名按所属单元选择。

## 事后要求

- 交付列明修改文件、内容、完整检查命令、退出码和精确结果。
- 核对结论说明数据来源、处理或调用过程与最终结果，证据范围对应结论范围。
- 风格核对说明相关段落承担的操作、解释或评价职责，记录详略随任务确定。
- 正式文档采用唯一 H1、职责说明、H2/H3 和紧凑列表，每项长期要求由一份文档维护。
- 正式文档必须只描述当前有效事实与要求，内容符合所属职责。
- 任务清单、样本、裁决和执行证据按需要存入 `.zcode/plans/`，作者执行记录随任务交付。
- 文档修改保持统一分类与书写格式，同步直接相关的入口和检查要求。
