# MOD 骨架和入口文件

本节将引导您创建一个能被 Starsector 识别的空 MOD，并确定整套教程都会沿用的 ID 规则。

*本节预期阅读时间：~6 分钟。*

*本节预期操作时间：~5 分钟。*

## 学习目标

完成后，您将能够：

- 分清 MOD 根目录、游戏数据目录和资源目录；
- 写出可加载的 `mod_info.json`；
- 为数据对象选择不会与其他 MOD 冲突的 ID；
- 理解同路径文件的合并与覆盖风险；
- 从启动器和 `starsector.log` 判断加载是否成功。

## 前置知识

作为一切的开始，您需要事先准备好以下环境：

- Starsector：独立安装的游戏本体，本教程假定游戏根目录为 `Starsector`；
- 文本编辑器：需能支持 UTF-8 编码和编码转换，编者建议使用 `VS Code`。

## 游戏从哪里读取 MOD

每个 MOD 是 `Starsector/mods/` 下的一个直接子目录。启动器和游戏进程都会扫描这里的直接子目录，只把 "根目录里存在 `mod_info.json`" 的目录当作候选 MOD。

目录内没有这个文件、或文件解析失败、或缺必需字段时，该目录会被整体跳过，不会出现在 MOD 列表中。

本教程使用以下根目录：

```text
Starsector/mods/GearIndustry/
├── data/
├── graphics/
├── sounds/
└── mod_info.json
```

目录名 `GearIndustry` 最好是一个便于人阅读的名字，而为了便于分辨，最好和 `mod_info.json` 的 `id` 相同。而且，编者非常建议您保持目录名稳定，这样某些用户就不至于在 `mods` 文件夹里塞下几十个不同版本了。

常见顶层目录的职责如下：

| 目录 | 当前用途 |
|---|---|
| `data/` | CSV、JSON、舰船、武器、战术系统和其他游戏数据 |
| `graphics/` | 舰船、武器、图标、肖像和 UI 图片 |
| `sounds/` | OGG 音频 |
| `jars/` | 编译后 Java JAR 的常见存放处。但并不是强制性的 |
| `src/` | 开发时保存 Java 源码的常见本地位置。但并不是强制性的 |

现在只需要创建根目录和 `mod_info.json`。其他目录在需要时再建。

## 完善一个最基础的 mod_info.json

在 `Starsector/mods/GearIndustry/mod_info.json` 写入：

```json
{
  "id": "gear_in",
  "name": "齿轮工业",
  "author": "YourName",
  "version": {
    "major": 0,
    "minor": 1,
    "patch": 0
  },
  "description": "Starsector MOD 教程的练习项目。",
  "gameVersion": "0.98a-RC8"
}
```

::: caution 必须保存为 UTF-8 无 BOM

带 BOM 或其他编码会让文件解析失败，MOD 不出现在启动器列表中，`starsector.log` 也会因此记录 `Error loading mod from [MOD 目录]`。

:::

::: info 为什么示例使用标准 JSON

Starsector 的实际 MOD 文件常使用带注释或尾逗号的宽松 JSON；但教程示例将保持标准 JSON，便于编辑器和静态工具检查。

:::

字段作用与缺失后果：

| 字段 | 是否必需 | 作用 |
|---|---:|---|
| `id` | 是 | MOD 的唯一稳定标识。缺失或格式错误会让整个目录解析失败 |
| `name` | 是 | 启动器显示名称。缺失会让整个目录解析失败 |
| `author` | 否 | 作者或团队名称。缺失只影响启动器信息面板 |
| `version`| 建议 | 当前版本。缺失时启动器版本位置显示 `null` |
| `description` | 是 | 启动器信息面板中的说明。缺失会让整个目录解析失败 |
| `gameVersion` | 是 | 游戏版本。缺失或与当前游戏 major 不符时，启动器显示红色错误且无法勾选 |
| `dependencies` | 按需 | 稍后才可能用到，暂时不重要 |
| `jars` | 按需 | 稍后才可能用到，暂时不重要 |
| `modPlugin` | 按需 | 稍后才可能用到，暂时不重要 |

::: caution 不要提前填写 `jars`/`modPlugin`

本节的 MOD 还没有 Java 代码，因此不要提前填写这两个字段。指向不存在 JAR 或类的入口会在启动阶段报错并导致加载失败。

:::

## ID 与前缀

同一次游戏启动中，MOD 会把大量数据合并到共享注册表。由于舰船、武器、战机、船插、战术系统、势力和字符串 key 都可能与其他 MOD 发生同名冲突，编者非常建议您提前准备一个自己的独特前缀。

而且，准备独特前缀，也有助于防止您背上一些与您无关的报错黑锅。

本教程固定使用：

```text
MOD ID:       gear_in
数据/资源前缀: GearIn_
Java 包前缀:  data.scripts.gearin.<domain>
```

`<domain>` 按语义域组织 (`combat`、`campaign`、`rules` 等)，类名形如 `GearIn<Subject>`。

例如 `data.scripts.gearin.combat.GearInShipStatsProbe`；这一命名约定会在后续涉及数据注册的页面中沿用。

后续数据 ID 只表达对象含义，例如：

```text
GearIn_artizan
GearIn_faction_name
```

MOD ID `gear_in` 使用小写 ASCII、数字和下划线，可以减少路径大小写、CSV 引用和跨平台打包问题；`name` 可以本地化。`id` 一旦被存档、装配或其他 MOD 引用就应保持稳定。

::: caution 页面编号不是运行时标识

页面编号 (如 C01-S01) 只用于文档导航，永远不进入 MOD ID、数据 ID、包名或类名。

:::

## 数据合并与覆盖

MOD 的 `data/` 会按加载结果并入游戏数据。不同类型文件采用各自的合并逻辑，但排错时可以先遵循两条规则：

1. 把 ID 当成全局名称。
2. 重复 ID 可能覆盖、合并或导致加载错误，结果取决于具体注册表。
3. 把相对路径当成资源地址。
4. 相同资源路径可能让后加载内容遮蔽先加载内容。

::: warning 常见坑：不要复制整份原版文件

不要复制整份原版 CSV 或 JSON 后只改一行。文件内应该只包含当前 MOD 拥有的行、对象和资源，并为它们加固定前缀。

:::

这套目录与 ID 约定会继续用于舰船 (C01-S02)、武器 (C01-S05)、船插 (C02-S02) 和战术系统 (C02-S03)，届时可以沿各自的数据链找到精确使用方。

## 日志

游戏日志位于 `Starsector/starsector-core/starsector.log`，是排查问题的主要依据：MOD 是否被扫描到、加载过程发生了什么、异常和堆栈在哪，都记录在这份日志里。

对于本实例 MOD 而言，游戏启动器扫描 MOD 时将记录 `Found mod: gear_in [...]`，关键文件解析失败时将记录 `Error loading mod from [MOD 目录]` 及异常堆栈。

**本节的 "验证方案" 和 "故障分析" 都以这份日志为对照。**

您可能此前在论坛上见过或用过[某些用于辅助检查日志的工具](https://www.fossic.org/thread-2751-1-1.html)，但您马上要和它说再见了。作为一个合格的 Modder，您必须学会手撕日志，否则后续调试大概无法顺利进行。

## 验证方案

前置数据：已经创建 `Starsector/mods/GearIndustry/`，并按本节示例写好 `mod_info.json`。

1. 启动 `starsector.exe`。
2. 在启动器打开 Mods 列表。
3. 找到 "齿轮工业"，确认没有版本不兼容或依赖缺失提示。
4. 勾选它并启动游戏，进入主菜单后退出。
5. 打开 `Starsector/starsector-core/starsector.log`，搜索 `gear_in`。

预期结果：

- 启动器列表行显示 "齿轮工业 0.1.0"，信息面板显示描述、作者和要求版本，游戏进入主菜单。
- `starsector.log` 中出现 `Found mod: gear_in [...]`，游戏启动阶段出现已启用 MOD 列表，且没有由 `GearIndustry` 引起的 `ERROR`。

恭喜，您正式开始了您的 Modding 旅程。

## 故障分析

### 启动器中没有 MOD(manifest 缺失)

- 触发条件：`mod_info.json` 不在 MOD 的直接根目录，或文件实际名为 `mod_info.json.txt`。
- 可观察结果：Mods 列表没有 "齿轮工业"。
- 原因：启动器只把 "直接子目录 + 根目录 `mod_info.json`" 识别为 MOD，找不到该文件时目录不会进入列表。
- 修正：确认路径恰好是 `Starsector/mods/GearIndustry/mod_info.json`，并让编辑器显示文件扩展名。

### manifest 解析失败

- 触发条件：JSON 语法错误 (引号、逗号或花括号缺失)、文件带 BOM 或非 UTF-8 编码，或缺少 `id`/`name`/`description` 之一。
- 可观察结果：MOD 不出现在列表中 (而不是显示错误行)；`starsector.log` 记录 `Error loading mod from [MOD 目录]` 以及解析异常的堆栈。
- 原因：manifest 没有形成可解析对象，或缺少必需字段。
- 修正：先恢复本节的标准 JSON，再逐字段修改；保持 UTF-8 无 BOM。

### 游戏版本提示不匹配

- 触发条件：`gameVersion` 缺失，或仍是旧教程中的版本号 (major 与当前不符，如 `0.95a-RC15`)。
- 可观察结果：启动器把 MOD 行显示为红色并提示 "Error: this mod requires a different version of the game."，且无法勾选。
- 如果此前已勾选，启动器下次启动时会自动取消勾选，并弹窗提示不兼容 MOD 已被禁用。
- 原因：manifest 声明与当前游戏版本不兼容。
- 仅 RC 编号或小版本不同 (如 `0.98a-RC6`) 会被视为兼容，最多显示黄色警告。
- 修正：使用本教程范例的 `0.98a-RC8` 版本。

### 添加数据后出现别人的内容或重复条目

- 触发条件：使用 `test_ship`、`my_weapon` 等常见 ID，或复制原版整行但保留原 ID。
- 可观察结果：条目被替换、出现重复、装配引用到错误对象，或加载阶段报 duplicate/unknown ID。
- 原因：多个来源争用同一注册表键。
- 修正：改为 `GearIn_<语义名>` 形式，例如 `GearIn_artizan`，并同步所有使用方。

## 总结

一个可加载 MOD 的最简流程是：`Starsector/mods` 下的独立根目录、根目录中的 `mod_info.json`、稳定 MOD ID、与当前游戏一致的 `gameVersion`，以及启动器和日志两处验证。

这套流程一旦成立，后续所有 MOD 内容 —— 无论是数据行还是 Java 入口 —— 都只是向这套已验证的目录与 ID 体系中登记对象。

加载类故障也因此总能先归因到这套流程中的某一环，再向对应的使用方深入排查。

## 建议参考项

- `com.fs.starfarer.api.ModManagerAPI`：MOD 状态管理器。
- `com.fs.starfarer.api.ModSpecAPI`：MOD 信息。
- `com.fs.starfarer.api.ModDependencyAPI`：MOD 依赖项。
- `com.fs.starfarer.api.VersionInfoAPI`：MOD 版本信息。
- `com.fs.starfarer.api.BaseModPlugin`：常用的基础 MOD 入口之一。
- `Starsector/starsector-core/data`：原版数据根目录。
- `Starsector/starsector-core/starsector.log`：游戏日志，排查加载问题的主要依据。
