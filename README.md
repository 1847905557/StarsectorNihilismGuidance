# 虚无主义教程（Nihilism Guidance）

**面向远行星号（Starsector 0.98a-RC8）MOD 制作的完整中文教程文档站**，从零开始带读者做出一个可运行的、包含舰船、武器、船插、战术系统、势力、星系与生涯内容的 MOD。

- 在线阅读：<https://1847905557.github.io/StarsectorNihilismGuidance/>

## 这是什么

一份按章组织的教程，共 18 章（`docs/chapter1` – `docs/chapter18`）、91 个节页（`chapterN/sectionM/README.md`）：

1. 基础：舰船、武器、船插、战术系统、战役数据、音频
2. 进阶：模拟战、模块船、Java 工程、全局服务
3. 战斗与生涯编程：战斗 API、生涯对象、人物技能、rules、监听器与任务
4. 依赖库：LazyLib / GraphicsLib / MagicLib / LunaLib / BoxUtil 接入，以及 Nexerelin 等软依赖兼容

配套仓库内还有一个 **`tutorial-fixtures`** 工程——68 个按单元隔离、可直接编译的示例单元，包含 145 个 Java 文件和 8 个 shader 文件，与教程页面完整代码块逐字对应，作为「照着写就能跑」的权威示例。

## 技术栈

| 部分 | 技术 |
|---|---|
| 文档站 | VuePress 2（Vite bundler）+ TypeScript 配置（`docs/.vuepress/`） |
| 侧边栏 | 自研 `sidebarDiscoveryPlugin`：自动读取各页 H1 生成导航，**无需手工维护目录** |
| 自动跳转 | 自研 markdown-it 插件 `markdown-splink`：正文中的 `[C1-S1]` 单元编号自动变为站内链接 |
| 章索引 | 自研 `markdown-sectionlist`：章索引页的 section 列表由文件系统自动生成（占位符 `<!-- AUTO:SECTION-LIST -->`），标题与侧边栏同取各页 H1 |
| 示例工程 | Gradle 多 sourceSet、Java 17；68 个 Java 单元编译任务 + 8 个 GLSL 着色器校验 |
| 部署 | GitHub Pages（`gh-pages` 分支 + Actions 自动发布） |

## 编辑须知（重要）

- **H1 即导航**：侧边栏从每页第一行 H1 自动生成。改 H1 前先想好；H1 必须保持为文件首个非空行。
- **单元编号写法**：正文引用单元编号写成 `[C1-S1]`，数字不带前导零且位数不限，构建时自动变成同名站内链接。裸编号、双层方括号和手写章节目标链接均不符合契约；代码围栏与行内代码保持原样。
- **章索引页**：`chapterN/README.md` 的「本章各节」下放占位符 `<!-- AUTO:SECTION-LIST -->`，section 列表自动生成——**不要手写链接列表**。
- **页面代码块与 fixture 逐字一致**：` ```java ` 围栏里的示例代码必须与 `tutorial-fixtures/units/Cxx-Syy/` 下源码完全相同，改了 fixture 必须同步页面（反之亦然），并跑 `gradle compileAllFixtures validateShaders` 验证。
- **验收**：改完跑 `npx vuepress build docs` 确认可构建；页面代码块改动后跑 `gradle compileAllFixtures validateShaders` 回归。仓库内另有更严格的机械扫描（痕迹/术语/格式）作为内部编辑流程的一部分，不随仓库分发。

## 本地开发

```bash
pnpm install
pnpm docs:dev     # 本地预览（H1 改动会自动刷新侧边栏）
pnpm docs:build   # 生产构建
cd tutorial-fixtures && gradle compileAllFixtures validateShaders   # 示例工程回归
```

## 许可

内容版权归 **Nihilist** 所有；代码示例遵循仓库 LICENSE（GPL）。
