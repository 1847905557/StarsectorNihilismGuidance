# Technical Guidelines

本文件规定教程与 fixture 的技术事实、示例身份、示例取材、Java 写法和完整成品契约。教学组织见 `.zcode/writing-guidelines.md`，文字呈现见 `.zcode/text-guidelines.md`。

## 版本与事实

- 教程使用 Starsector `0.98.5a-RC3` 与 Java 17。
- 依赖库版本：LazyLib 3.0.0、MagicLib 1.5.6、GraphicsLib 1.12.1、LunaLib 2.0.5。升级时以各库 `mod_info.json` 重新核对。
- 技术事实依据该版本游戏核对：`starsector-core/starfarer.api.zip` 中的公开 API 源码、`starsector-core/data` 中的原版数据，或游戏内直接观察。严禁采用或暗示反混淆产物。
- API、字段、ID、路径、日志、命令和数值保持准确，改写文字时不改变技术含义。
- 运行期动态值可以写 "以实际日志为准"。

## 示例身份

- 示例 MOD ID 与文件夹名均为 `GearIndustry`，jar 为 `jars/GearIndustry.jar`；数据、资源和 strings 键使用 `GearIn_<subject>`。
- 教程中的成果同步到仓库 `sample/GearIndustry`，该目录始终是一个可直接放入 `mods/` 运行的 MOD。
- 首舰为 `工匠-级 (Artizan)`，ID `GearIn_artizan`，沿用现有贴图。
- Java 包使用 `data.scripts.gearin.<domain>`，按 `combat`、`campaign`、`rules` 等职责组织。
- Java 类名使用表达角色的 `GearIn<Role>`；教程单元编号 `Cxx-Syy` 只用于页面和 fixture，严禁进入运行时名称。
- 新 ID 由一个页面定义，其余页面按相同含义复用并引用定义页。

## 示例取材

- 每节产出一个能直接用在 MOD 里的功能，如一个可装配的船插、一个会刷新的势力舰队、一个可接取的任务，而不是只打印日志的探针。
- API 速查、依赖库方法集等只适合片段的主题直接给片段，不硬造一个类。
- 写法可以参考 `Kratogen_TA` 与 `Polaris_Prime` 中的常见组织，如 `data/scripts/plugins`、`rulecmd`、`campaign/intel`。这两个 MOD 只代表各自的需求，没有用到的写法以原版 API 与原版数据为依据。
- 能复制原版文件再修改的场合，先说明复制哪个原版文件，再讲改哪些项。

## Java 与教学代码

- 每个 Java 类承担一个角色；跨角色实现分开写，并说明在哪里绑定。
- 先说明要解决的问题，再给当前步骤需要的片段，交代写入位置、调用关系和生效条件。
- 片段标明用途；完整类包含实际包、import、入口和依赖。
- 故障演示使用真实可达的条件，局部故障片段和修正片段明确标识。
- 封装、依赖和异常处理只在有明确需求时加入。

## 完整成品与 fixture

- 页面中的完整 Java 类必须与 `tutorial-fixtures/units/Cxx-Syy` 中的对应文件逐字一致，源码路径由包名与类名确定。
- 每个 fixture Java 文件必须在所属页面完整出现，并登记在 `build.gradle` 的 `javaUnitIds` 中。
- 完整 shader 以 `#version` 开头，与对应 `.vert` 或 `.frag` 资源逐字一致，并登记到 `shaderUnitIds`。
- 页面、fixture 目录、源码、资源和 Gradle 清单双向对应。
- 局部片段和解释性数据不进入 fixture。
- 读者的构建与游戏内确认写在教程正文；fixture 路径和作者核验结果写在工具说明与任务记录中。

## Java 与资源格式

- Java 使用 4 空格缩进，续行相对语句起点缩进 8 空格，大括号展开。
- import 组内按字母排序。
- 注释简短；第 1–5 章的片段可以使用解释每行作用的行注释，完整成品保留角色说明所需的注释。
- 分节横幅写成 `// --- 标题 ---`，注释中的引号使用 ASCII 双引号。
- JSON、CSV、`.skill`、`.variant`、`.txt` 和 shader 按自身语法与原版样本排版；宽松 JSON 的注释、尾逗号和 Tab 按游戏解析语义处理。
