# Overview

本仓库维护 Starsector `0.98.5a-RC3` 的中文 MOD 制作教程、可编译示例与维护工具，面向游戏数据、资源和 Java 扩展制作，通过 VuePress 文档站交付。

## 项目目标

- 按制作任务讲清对象、操作、注册关系、生效条件和观察结果。
- 保持教程完整代码、fixture 源码、资源和构建清单一致。
- 从页面目录和标题生成导航，提供本地页面编辑与 Markdown 恢复能力。
- 用工具核对客观格式、页面结构和示例契约，按上下文审阅技术含义与作者表达。

## 技术栈

- VuePress 2 + Vite
- Vue 3 + TypeScript
- Tiptap
- Markdown-it
- Node.js 工具与内置测试
- Gradle + Java 17
- Node.js 与 pnpm 的要求由 `package.json` 声明，CI 的 Node.js 主版本由 `.node-version` 指定。

## 宏观层级

### 教程站

- `docs/`：教程首页、章页、节页及所属图片，`chapterN/sectionM/README.md` 对应教程单元。
- `docs/.vuepress/`：站点配置、侧边栏发现、Markdown 插件与开发模式页面编辑。
- `docs/.vuepress/client/`：Tiptap 编辑模型、页面交互、提交与页面更新交接。
- `docs/.vuepress/styles/`：站点通用样式，编辑器展示规则由所属客户端维护。

### 示例工程

- `tutorial-fixtures/units/`：按 `Cxx-Syy` 单元保存 Java 源码与 shader 资源。
- `tutorial-fixtures/build.gradle`：登记单元、创建独立 source set、装配本机游戏与依赖 JAR，组织契约检查、编译和资源验证。
- `tutorial-fixtures/gradle.properties`：本机游戏根、依赖 MOD 根与工具配置。

### 维护与发布

- `tools/`：文字、文档与 fixture 检查，格式处理及页面编辑恢复。
- `tools/shared/`：遵守 Git 忽略规则的范围收集、文本读取、原子写入、Markdown 解析与通用结果。
- `.zcode/`：项目说明、工作流程和分类规范；任务材料归 `.zcode/plans/`，编辑器写回备份归 `.zcode/tutorial-work/backups/edit-writes/`。
- `.github/workflows/docs.yml`：文档站构建、Pages artifact 上传与发布。

## 项目内关键链路

- 站点装配：`config.ts` → Vite、主题与开发插件 → 侧边栏及 Markdown 扩展 → VuePress 页面。
- 页面导航：章、节目录与 README 首个非空行 H1 → `sidebar.ts` 按编号生成侧边栏 → 开发 watcher 按目录、章标题和节标题有效性变化刷新。
- 章目录：章页 `<!-- AUTO:SECTION-LIST -->` → 所属节目录与 H1 → `markdown-sectionlist.ts` 生成列表 token → 页面章内目录。
- 章节引用：正文 `[C1-S1]` → `markdown-splink.ts` 核对文本 token、已有链接与目标页面 → 使用站点 base 生成节页链接。
- 页面编辑：开发模式交付路径与源版本 → 客户端捕获原始 HTML 并建立 Tiptap 实例 → `POST /__api/edit-page` → 中间件核对目标与版本 → Node 恢复函数与图片核对 → 备份、原子写回与新版本 → 客户端等待对应页面热更新并释放编辑实例。
- 编辑释放：页面切换、取消或卸载 → 所属编辑器销毁 → 原始内容按操作恢复 → 事件监听、观察器和定时器释放。
- 示例契约：教程完整 Java 类或 shader → `check-fixtures.mjs` → 页面、源码、资源与 Gradle 显式清单双向核对 → 定位诊断和退出码。
- Java 编译：独立 source set → 本机游戏与依赖 MOD 的 JAR → 契约检查 → Java 17 编译 → `compileAllFixtures` 汇总。
- shader 核验：登记的 shader 单元 → 契约检查与 `validateShaders` 资源目录、非空检查 → 所属示例的编译、链接及游戏画面验证。
- 站点发布：`main` 推送、拉取请求或手动触发 → 安装固定依赖 → `pnpm docs:build` → `docs/.vuepress/dist/`；`main` 上的推送或手动构建 → Pages artifact → 部署任务 → GitHub Pages。
