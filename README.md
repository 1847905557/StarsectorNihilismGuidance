# Starsector Nihilism Guidance

本仓库维护面向 Starsector `0.98a-RC8` 的中文 MOD 制作教程，以及配套示例和核验工程。教程覆盖游戏数据与资源制作、Java 实现和依赖 MOD 集成；页面中的完整示例与 Gradle fixtures 通过核验工具保持一致。

[在线阅读教程](https://1847905557.github.io/StarsectorNihilismGuidance/)

## 项目组成

### 教程站

`docs/` 保存教程页面，站点使用 VuePress 构建。`docs/.vuepress/` 中的插件从页面内容生成导航和站内链接：`sidebar.ts` 读取章页与节页的 H1，`markdown-sectionlist.ts` 按目录生成章内目录，`markdown-splink.ts` 将 `[C1-S1]` 形式的引用链接到对应页面。

开发模式中提供页面编辑器。编辑器将页面内容还原为 Markdown 并写回对应的 `docs/` 文件；它只在本地开发站点启用。

### 示例工程

`tutorial-fixtures/` 使用 Gradle 管理 Java 和 shader 示例。Java 示例按教程单元放入独立 source set，编译目标为 Java 17，并使用本机 Starsector 和依赖 MOD 提供的 API。

### 页面与 fixture 契约

教程页面负责讲解和展示，fixture 保存相应的可编译类与资源。完整 Java 类和 shader 源码在页面与 fixture 中逐字一致。`tools/check_fixtures.py` 核对页面、fixture 文件和 Gradle 清单之间的对应关系；Gradle 编译与验证任务会运行这项检查。修改完整示例时，同步更新页面与对应 fixture。

`verifyTutorialFixtures` 汇总 Java 编译与 shader 资源检查；shader 检查确认已登记的资源目录和非空资源。

### 核验工具

`tools/` 提供 Python 3.10+ 标准库工具，用于检查文本与 Markdown 格式、教程结构和 fixture 契约，也包含将站点编辑结果还原为 Markdown 的恢复器。工具参数和检查范围见[工具说明](tools/README.md)。

## 本地开发

文档站使用 Node.js `>=24.21.0` 和 pnpm `12.4.1`。核验工具使用 Python `3.10+`；Java fixture 使用 Java `17`。

在仓库根目录安装依赖并启动文档站：

```powershell
pnpm install --frozen-lockfile
pnpm docs:dev
```

构建站点并测试页面编辑器与维护工具：

```powershell
pnpm docs:build
pnpm test:dev-edit
python -X utf8 -B -m unittest discover -s tools/tests -p "test_*.py" -v
```

Java fixture 编译需要本机安装 Starsector `0.98a-RC8` 和教程涉及的依赖 MOD。在本机 `tutorial-fixtures/gradle.properties` 中设置 `starsectorRoot`，指向包含 `starsector-core/` 的游戏安装目录；Gradle 会从该目录的 `mods/` 读取依赖库。

```powershell
cd tutorial-fixtures
gradle verifyTutorialFixtures
```

## 构建与发布

`.github/workflows/docs.yml` 为针对 `main` 的推送和拉取请求构建文档站，也支持手动运行构建。Pages artifact 上传和 GitHub Pages 发布仅在工作分支为 `main` 时执行。

## 版权与许可

教程正文版权归 Nihilist 所有；代码示例遵循仓库的 [GPL-3.0 许可证](LICENSE)。
