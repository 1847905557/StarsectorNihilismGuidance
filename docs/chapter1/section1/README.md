# MOD 骨架和入口文件

本节将引导您创建一个能被 Starsector 识别的空 MOD，解释 `mod_info.json` 的内容，并确定整套教程都会沿用的 ID 规则。

*本节预期阅读时间：\~6 分钟。*

*本节预期操作时间：\~5 分钟。*

## 学习目标

完成后，您将能够：

- 分清 MOD 根目录、游戏数据目录和资源目录；
- 写出可加载的 `mod_info.json`；
- 为数据对象选择不会与其他 MOD 冲突的 ID；
- 理解同路径文件的合并与覆盖风险；
- 从启动器和 `starsector.log` 判断加载是否成功。

## 前置知识

- 能够创建目录、显示文件扩展名并编辑纯文本文件；
- 理解 UTF-8 无 BOM 的含义，并能检查和修复格式错误的文本。

## 游戏从哪里读取 MOD

每个 MOD 是 `Starsector/mods/` 下的一个直接子目录。启动器和游戏进程都会扫描这里的直接子目录，只把 "根目录里存在 `mod_info.json`" 的目录当作候选 MOD。

目录内没有这个文件、或文件解析失败、或缺必需字段时，该目录会被整体跳过，内含的不完整 MOD (或其实根本不是 MOD) 将不会出现在 MOD 列表中。

本教程使用以下根目录：

```text
Starsector/mods/GearIndustry/
├── data/
├── graphics/
├── sounds/
└── mod_info.json
```

目录名 `GearIndustry` 最好是一个便于人阅读的名字，而为了便于分辨，最好和 `mod_info.json` 的 `id` 相同。而且，编者非常建议您保持目录名稳定，这样某些用户就不至于在 `mods` 文件夹里塞下几十个不同版本了。

### 顶层目录职责速查表

常见顶层目录的职责如下：

| 目录 | 当前用途 |
|---|---|
| `data/` | CSV、JSON、舰船、武器、战术系统和其他游戏数据 |
| `graphics/` | 舰船、武器、图标、肖像和 UI 图片 |
| `sounds/` | OGG 音频 |
| `jars/` | 编译后 Java JAR 的常见存放处。但并不是强制性的 |
| `src/` | 开发时保存 Java 源码的常见本地位置。但并不是强制性的 |

现在只需要创建根目录和 `mod_info.json`。其他目录在需要时再建。

## 完善一个最基础的 mod\_info.json

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

这也是推荐您使用诸如 VS Code 的专业编辑器的原因。它们几乎不会带来新的编码问题，而记事本则会。

:::

::: info 示例使用标准 JSON

Starsector 实际上使用的是 允许注释/允许尾逗号/允许\#型注释 的宽松 JSON (也称为 ALEXSON)；但教程示例将保持标准 JSON，这便于编辑器和静态工具检查，也有利于您的身心健康。

:::

### mod\_info.json 字段速查表

字段作用与缺失后果：

| 字段 | 是否必需 | 作用 |
|---|---|---|
| `id` | 是 | MOD 的唯一稳定标识。缺失或格式错误会让整个目录解析失败 |
| `name` | 是 | 启动器显示名称。缺失会让整个目录解析失败 |
| `author` | 否 | 作者或团队名称。缺失只影响启动器信息面板 |
| `version` | 建议 | 当前版本。缺失时启动器版本位置留空 |
| `description` | 是 | 启动器信息面板中的说明。缺失会让整个目录解析失败 |
| `gameVersion` | 是 | 游戏版本。缺失或与当前游戏 major 不符时，启动器显示红色错误且无法勾选 |
| `dependencies` | 按需 | 本页不使用 |
| `jars` | 按需 | 本页不使用 |
| `modPlugin` | 按需 | 本页不使用 |

::: warning 不要提前填写 jars/modPlugin

本节的 MOD 还没有 Java 代码，因此不要提前填写这两个字段。指向不存在 JAR 或类的入口会在启动阶段报错并导致加载失败。

:::

## ID 与前缀

大量不同的 MOD 会加载大量数据，导致舰船、武器、战机、船插、战术系统、势力和字符串 key 都可能与其他 MOD 发生同名冲突。因此，编者非常建议您提前准备一个自己的独特前缀。

而且，准备独特前缀，也有助于防止您背上一些与您无关的报错黑锅。

本教程固定使用：

```text
MOD ID:       gear_in
数据/资源前缀: GearIn_
Java 包前缀:  data.scripts.gearin.<domain>
```

数据 ID 只表达对象含义，例如：

```text
GearIn_artizan
GearIn_faction_name
```

最好不要用太过偏门的字符，以免各类路径大小写、CSV 引用和跨平台打包问题；且为了后续长期维护考虑，各类 `id` 应保持稳定。

::: info 除此之外...

`<domain>` 按语义域组织 (`combat`、`campaign`、`rules` 等)，类名形如 `GearIn<Subject>`。

例如 `data.scripts.gearin.combat.GearInShipStatsProbe`；数据注册继续沿用这一命名约定。

...如果上面这两行您没听懂，也没关系，目前暂时用不上。

:::

## 数据合并与覆盖

MOD 的 `data/` 会按加载结果并入游戏数据。不同类型文件采用各自的合并逻辑，但排错时可以先查下列易错项：

1. ID 重复。
2. 路径重复。
3. 把 ID 当成全局名称。
4. 把路径填成了 ID，反之亦然。

::: warning 避免合并重复

对于各种明显是会合并的文件，比如 `ship_data.csv`，不要复制整份原版 CSV 或 JSON 后只改一行。文件内应该只包含您希望增添的内容。

:::

这套目录与 ID 约定同样适用于舰船、武器、船插和战术系统，可以沿各自的数据链找到精确使用方。

## 日志

游戏日志位于 `Starsector/starsector-core/starsector.log`，包罗万象，是排查问题的主要依据。

**本教程的 "验证方案" 和 "故障分析" 都以这份日志为对照。**

您可能此前在论坛上见过或用过[某些用于辅助检查日志的工具](https://www.fossic.org/thread-2751-1-1.html)，但本教程要求您直接阅读日志。作为一个合格的 Modder，**您必须学会手撕日志**，才能更有效率地找到问题。

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

## 故障分析

- **启动器中没有 GearIn MOD**：`mod_info.json` 不在 MOD 的直接根目录，或文件实际名被写成了 `mod_info.json.txt`。这个问题常见于 Windows 未显示文件扩展名的时候。
    - **解决方案**：确认文件恰好是 `Starsector/mods/GearIndustry/mod_info.json`。

- **日志报告 manifest 解析失败**：JSON 缺少引号、逗号或花括号，文件带 BOM、编码并非 UTF-8，或缺少 `id`、`name`、`description` 时，`starsector.log` 会记录 `Error loading mod from [MOD 目录]` 和解析堆栈。
    - **解决方案**：逐字检查 JSON 是否写错，并以 UTF-8 无 BOM 保存。

- **启动器将 MOD 标红并禁止勾选**：`gameVersion` 缺失时，启动器会提示 "Error: this mod does not specify which version of the game it requires and can not be enabled."；主版本不匹配时会显示 "Error: this mod requires a different version of the game."。已经启用的 MOD 会在下次启动时被取消勾选。
    - **解决方案**：将版本写为实际游戏版本。

- **添加数据后出现重复条目或错误对象**：使用了 `test_ship`、`my_weapon` 等常见 ID，或复制数据后保留原 ID。非常可能导致 ID 重复。
    - **解决方案**：将 ID 改为 `GearIn_<语义名>` 形式，例如 `GearIn_artizan`，并同步全部引用。

## 总结

只要在 `Starsector/mods` 下的某个新建文件夹中加入正确的 `mod_info.json`，您就一定程度上成为了一个 Modder。

感想如何？是兴奋还是怯魅？

总而言之，恭喜您正式开始了您的 Modding 旅程。

## 建议参考项

- `com.fs.starfarer.api.ModManagerAPI`：MOD 状态管理器。
- `com.fs.starfarer.api.ModSpecAPI`：MOD 信息。
- `com.fs.starfarer.api.ModDependencyAPI`：MOD 依赖项。
- `com.fs.starfarer.api.VersionInfoAPI`：MOD 版本信息。
- `com.fs.starfarer.api.BaseModPlugin`：常用的基础 MOD 入口之一。
- `Starsector/starsector-core/data`：原版数据根目录。
- `Starsector/starsector-core/starsector.log`：游戏日志，排查加载问题的主要依据。
