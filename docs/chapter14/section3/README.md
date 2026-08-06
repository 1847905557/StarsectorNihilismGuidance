# 使用 LunaLib 制作设置界面

本节为既有 MOD 接入 LunaLib 设置：用 CSV 定义设置项，在界面中保存，再从代码读取并监听变化。LunaLib 缺失时，已有的可选依赖判断会让 MOD 继续运行。

*本节预期阅读时间：~20 分钟。*

*本节预期操作时间：~45 分钟。*

## 学习目标

完成后，您将能够：

- 写出 `data/config/LunaSettings.csv` 的九列与 `fieldType` 支持清单 (`Header` / `Text` / `Int` / `Boolean` / `Double` / `String` / `Enum` / `Keycode` / `Color` / `Radio`)。
- 说出定义链：谁在什么时候读取谁的 CSV、空 `fieldID` 行被跳过、`defaultValue` 按类型转换及转换失败的可观察结果；
- 说出用户设置文件的位置、创建时机与更新时机：首次加载设置时由 LunaLib 用默认值创建，之后在设置界面点保存按钮更新 (底层是 LazyLib `CommonDataJSONObject.save()`，唯一写盘入口)；`Header` / `Text` 类型不落盘、缺失键自动补默认值；
- 用 `LunaSettings.getBoolean / getInt / getFloat / getDouble / getString / getColor(modId, fieldId)` 读取设置，说出三条约定：返回值是 boxed 值且**可为 null**、缺失时写 `ERROR` 日志并返回 null、未加载时懒触发整份加载。
- 说出 `getFloat`(从保存的 double 转 float) 与 `getColor`(十六进制字符串，`#` 前缀可省略) 的转换规则；
- 用 `LunaSettings.addSettingsListener` 注册 `LunaSettingsListener`，写出按**自己的 MOD id 过滤**的 `settingsChanged(modId)`，说出通知触发时机 (设置界面保存后) 与 "单个监听器抛异常被隔离、不影响其他监听器"；
- 说出 LunaLib 未启用时引用 `lunalib.**` 的后果 (`NoClassDefFoundError`) 与 `isModEnabled("lunalib")` 启用判断回退写法 (与硬依赖检测专题一致，成熟实例同款)；
- 从设置界面、用户设置文件、`starsector.log`、代码读取值四个可观察位置复核设置完整流程，并把 "设置没生效" 定位到定义链、保存链、读取链还是通知链。

## 前置知识

- [C13-S1] 的硬依赖与可选依赖判断，以及 `isModEnabled("lunalib")` 的启用检测；
- [C13-S2] 的 LazyLib `CommonDataJSONObject` 与 `save()` 写盘入口；
- [C12-S3] 的 `loadCSV(path, modId)` 与 CSV 读取；
- [C12-S1] 的 MOD 文件路径和 `/` 分隔规则；
- [C12-S4] 的 common 文件读写与持久化；
- [C6-S2] 的 `modPlugin` 回调与 `onGameLoad` 调用时机；
- [C6-S3] 的 `Global` 生命周期与 `getSettings()` 入口；
- [C13-S4] 的 MagicSettings 设置读取与 ID 归属；
- [C1-S1] 的 MOD ID `gear_in` 与 `GearIn_` 命名约定。

## LunaLib 概览

发行布局 (`Starsector\mods\Lunalib`)：发行包 `jars\LunaLib.jar` 内随附 Kotlin 源文件 `*.kt`。

```text
Starsector\mods\Lunalib\
├── mod_info.json              # id "lunalib"、version "2.0.3"、gameVersion "0.98a-RC7"、
│                              # jars 三件（LunaLib.jar + LunaLib-Kotlin.jar + fuzzywuzzy-1.3.0.jar）、
│                              # modPlugin lunalib.LunaLibPlugin、依赖 lw_lazylib
├── data\
│   └── config\
│       ├── LunaSettings.csv           # LunaLib 自己的设置项定义（九列表头实例）
│       ├── LunaSettingsConfig.json    # 设置界面图标配置（顶层键 = MOD id，lunalib 段实例）
│       ├── LunaSettingsDefault.default # 用户设置文件的初始模板（本发行内容为 {}）
│       ├── settings.json              # 界面/功能注册
│       └── strings.json               # 界面文案
├── graphics\
└── jars\
    └── LunaLib.jar / LunaLib-Kotlin.jar / fuzzywuzzy-1.3.0.jar
```

本节涉及的类与职责：

| 类 | 职责 | 本节范围 |
|---|---|---|
| `lunalib.lunaSettings.LunaSettings`(object，全部 `@JvmStatic`)| 运行时读取 `getX(modId, fieldId)`、监听器注册 `addSettingsListener` / `removeSettingsListener` / `hasSettingsListenerOfClass`、通知入口 `reportSettingsChanged`、运行时定义 `SettingsCreator` | 读取 / 通知 |
| `lunalib.lunaSettings.LunaSettingsListener`(interface)| 变更通知回调，唯一方法 `settingsChanged(modId)` | 通知 |
| `lunalib.backend.ui.settings.LunaSettingsLoader`(object)| `loadDefault`(解析各 MOD 的 CSV)、`saveDefaultsToFile`(补默认值落盘)、`loadSettings`(读入运行时表)；"Can not be used outside LunaLib" | 定义 / 保存 |
| `lunalib.backend.ui.settings.LunaSettingsConfigLoader`(object)| 设置界面图标 `getIconPath(modId)` | 可选 |
| `lunalib.backend.scripts.LoadedSettings` | LunaLib 自己的监听器实例：`modID == "lunalib"` 过滤后重读键位 | 通知 (官方写法)|
| `lunalib.backend.scripts.KeybindsScript` | 生涯每帧脚本：按键打开设置界面 / 调试界面 | 保存 (UI 入口)|

四条链路总览：

```text
定义链：data/config/LunaSettings.csv（每个启用 MOD 一份）
        → LunaSettingsLoader.loadDefault()（按 mod.id 限定读取）→ SettingsData
保存链：设置界面改动 → 保存按钮 → CommonDataJSONObject.save()（LazyLib 写盘）
        → <存档目录>/common/LunaSettings/<modId>.json → 运行时表更新 → reportSettingsChanged
读取链：LunaSettings.getX(modId, fieldId)（懒加载）→ 运行时表 JSONObject → boxed 值 / null
通知链：addSettingsListener 注册 → reportSettingsChanged(modId) → 逐个 settingsChanged(modId)
```

四条链路里，定义链是起点：没有 CSV 就没有设置项，后面三条链路无从谈起。先从定义链开始，再依次走完保存、读取与通知。

## 设置定义：data/config/LunaSettings.csv

LunaLib 的设置项不是注册到某个全局文件，而是**每个已启用 MOD 自己提交一份**固定路径的 CSV：`data/config/LunaSettings.csv`。加载入口在 LunaLib 的应用加载期与首次读取时：

- `LunaLibPlugin.onApplicationLoad`：`LunaSettings.addSettingsListener(LoadedSettings())` 后执行 `if (!LunaSettingsLoader.hasLoaded) LunaSettingsLoader.load()`。
- `LunaSettingsLoader.load()` = `loadDefault()` + `saveDefaultsToFile()` + `loadSettings()` (`lunalib.backend.ui.settings.LunaSettingsLoader`)。
- `loadDefault()` 遍历 `Global.getSettings().getModManager().getEnabledModsCopy()`，对每个启用 MOD 执行 `Global.getSettings().loadCSV("data/config/LunaSettings.csv", mod.id)`。
  - 这是 `loadCSV(path, modId)` 的 modId 限定读取，只会读到该 MOD 自己的目录。
- 没有这个文件的 MOD 读取抛异常被静默吞掉 (`catch (_: Throwable) { continue }`)，即 "没写 CSV 的 MOD 没有设置项"。
- 懒加载备用机制：任何 `LunaSettings.getX(...)` 在 `hasLoaded == false` 时先执行一次 `LunaSettingsLoader.load()`(`lunalib.lunaSettings.LunaSettings` 各 getter)。
- 因此使用方在 `onApplicationLoad` 读设置也不需要等 LunaLib 自己的回调顺序 (跨 MOD `onApplicationLoad` 顺序按显示名排序)。

官方表头 (九列，`Starsector\mods\Lunalib\data\config\LunaSettings.csv` 与各成熟实例一致)：

| 列 | 语义 | 解析行为 |
|---|---|---|
| `fieldID` | 设置项唯一 ID，也是运行时读取 key | **空行跳过** (`if (id == "") continue`)；同 MOD 内重复 ID 后读行覆盖前行 |
| `fieldName` | 设置界面的显示名 | 直接读取 |
| `fieldType` | 设置类型 | 支持 `Header` / `Text` / `Int` / `Boolean` / `Double` / `String` / `Enum` / `Keycode` / `Color` / `Radio` |
| `defaultValue` | 默认值，按 `fieldType` 转换 | 见下表；**转换失败抛异常且不捕获** |
| `secondaryValue` | 附加数据：`Radio` 的选项列表 (逗号分隔)、`Enum` 的候选等 | 可选，读取失败为空串 |
| `fieldDescription` | 设置界面的说明文本 | 加载器**先试 `fieldTooltip` 列、再读 `fieldDescription` 列**；官方表头用 `fieldDescription` |
| `minValue` / `maxValue` | 仅 `Int` / `Double` 的数值约束 | 可选；缺失或非法时回退 `0.0` / `100.0` |
| `tab` | 设置界面分页名 | 可选，缺失为空串 |

`defaultValue` 的类型转换 (`lunalib.backend.ui.settings.LunaSettingsLoader`，不在任何 try 内)：

| `fieldType` | `defaultValue` 解析 | 运行时读取方法 |
|---|---|---|
| `Header` | 原样字符串，只做 UI 分组标题 | 不读取 |
| `Text` | 原样字符串，只做 UI 展示 | 不读取 |
| `Boolean` | Kotlin `toBoolean()`：**忽略大小写的 `"true"` 为 true，其余 (含 `"1"`、`"0"`) 一律 false** | `getBoolean` |
| `Int` | `toInt()` | `getInt` |
| `Double` | `toDouble()` | `getDouble` / `getFloat` |
| `String` | 原样字符串 | `getString` |
| `Enum` | 按逗号 `split(",")` 成列表 | LunaSettings 无枚举专用读取方法 |
| `Keycode` | `toInt()`；LWJGL 键码，`0` = 未绑定/未知键 | `getInt` |
| `Color` | 原样十六进制字符串 (如 `#f22e6e`)| `getColor` |
| `Radio` | 原样字符串；选项列表来自 `secondaryValue`，默认选中项 = `defaultValue` | `getString` |

设置界面 (`LunaSettingsUIMainPanel` / `LunaSettingsUIModsPanel` / `LunaSettingsUISettingsPanel`) 按 "已加载设置项的 MOD" 列出条目，按 `tab` 分页渲染控件：
- `Boolean` 是开关按钮；
- `Int`/`Double` 是文本框 + 滑条；
- `Color` 是颜色选择器；
- `Radio` 是单选按钮组；
- `Keycode` 是键位绑定按钮；
- `Header`/`Text` 只展示。

界面在生涯内按键打开 (`lunalib.backend.scripts.KeybindsScript`)。

默认键位定义在 LunaLib 自己的设置项 `luna_SettingsKeybind_new`(默认值 61，键码与 `org.lwjgl.input.Keyboard` 一致)，**`Shift+F2` 组合始终可用**。

::: tip 打开设置界面的技巧
默认键位 (61) 可以在设置界面里被改掉，但 **`Shift+F2` 组合键始终可用** —— 排查 "设置界面打不开" 时直接按 `Shift+F2`。
:::

规则 (`data/config/LunaSettings.csv`)：

1. **必须**：`fieldID` 与运行时读取 key 逐字一致 (大小写敏感)。违反的可观察结果：`getX` 返回 null 并写 `ERROR` 日志 (见读取节)，设置界面有该项但代码读不到。
2. **必须**：`defaultValue` 能被 `fieldType` 解析。
   - 违反的可观察结果：`Int` / `Double` / `Keycode` 写非数字 → `NumberFormatException` 从加载链直接上抛 —— 要么 LunaLib 应用加载期失败、要么首个读取调用点抛异常 (错误堆栈在 `starsector.log`)，该 MOD 全部设置加载中断。
   - `Boolean` 写 `"1"` / `"0"` **不报错**，但被静默解析成 `false`(Kotlin `toBoolean` 只认忽略大小写的 `"true"`)，开关语义反转且无任何日志。
3. **必须**：`fieldType` 使用上表列出的类型名。未列出的类型名不执行任何默认值转换 (`when` 无 else 分支)，`defaultValue` 保持字符串，界面控件与读取语义不可预期。
4. **必须**：文件路径固定为 `data/config/LunaSettings.csv`，遵循 `loadCSV` 的 `/` 路径规则。放错位置或大小写不一致 → 读取异常被静默吞掉，设置界面里没有您的 MOD，且没有任何报错 ——"设置项整个消失" 的首选排查方向。
5. 建议：`Radio` 的 `defaultValue` 出现在 `secondaryValue` 选项列表里；缺席时 UI 默认选中与运行时读取会错位。

::: warning `Boolean` 默认值只认 `"true"`
Kotlin `toBoolean()` 只把忽略大小写的 `"true"` 解析为 true，`"1"` / `"0"` 等其余值一律**静默**解析为 false—— 不报错、无日志，开关语义反转。写 CSV 的 `defaultValue` 时用 `true` / `false`。
:::

定义链只决定 "有哪些设置项、默认值是什么"；设置项改完后要能跨启动保留，靠的是保存链。

## 设置保存：用户设置文件与保存流程

用户设置文件：**`<存档目录>/common/LunaSettings/<modId>.json`** (存档目录由启动器按 `com.fs.starfarer.settings.paths.saves` 指定)。它由 LunaLib 独占管理，MOD 不应自己读写 (见故障分析第 11 条)。

- **创建**：`LunaSettingsLoader.saveDefaultsToFile()` 对每个启用 MOD 调用 LazyLib 的 JSON 读取工具方法 `loadCommonJSON("LunaSettings/<modId>.json", "data/config/LunaSettingsDefault.default")`。
  - LazyLib(`Starsector\mods\LazyLib\javadoc.zip`)："Loads a JSON file from common data, or creates it, populates it with default values, and saves it to disk if it does not already exist."。
  - 文件缺失时创建并落盘，初始内容来自该 MOD 自己的 `data/config/LunaSettingsDefault.default`(可选文件；不提供则从空对象开始)。
- **补默认值**：随后逐项检查，`fieldType` 为 `Text` / `Header` 的跳过，其余键缺失时 `put(fieldID, defaultValue)`；`data.length() > 0` 时 `data.save()` 写盘。
- 因此：用户文件里没有 `Header`/`Text` 键；删除某键后下次启动会以默认值补回。
- **更新**：设置界面点保存按钮 (`lunalib.backend.ui.settings.LunaSettingsUIModsPanel`)—— 把界面收集的 `ChangedSetting` 逐项 `put` 进 `CommonDataJSONObject`，然后依次执行：

```kotlin
data.save()                                    // LazyLib CommonDataJSONObject.save()：唯一写盘入口
LunaSettingsLoader.Settings.put(mod, data)     // 更新运行时表
if (!newGame) LunaSettings.reportSettingsChanged(mod)  // 变更通知
```

`CommonDataJSONObject.save()` 的写盘语义 (`<saves>\common` 目录、唯一写盘入口) 如前置知识中 LazyLib `CommonDataJSONObject` 主题所列。

**不点保存按钮直接关面板不会写盘、不会通知** (保存按钮有 5 帧的保存延迟，`saveDelay = 5`)。

::: info 为什么 "界面保存、代码只读"
LunaLib 的设置保存是 "界面保存、代码只读" 的设计：改设置只能走设置界面，写盘入口是 LazyLib `CommonDataJSONObject.save()`，代码侧没有 "写设置" 的 API—— 这正是用户设置文件由 LunaLib 独占管理、MOD 不应自己读写它的原因。
:::

可选：`data/config/LunaSettingsConfig.json` 给设置界面配 MOD 图标 —— 顶层键 = 自己的 MOD id，值含 `iconPath` (相对 MOD 根的贴图路径，正方形最佳)；

LunaLib 用 `getMergedJSONForMod("data/config/LunaSettingsConfig.json", "lunalib")` 合并读取。

读取类：`lunalib.backend.ui.settings.LunaSettingsConfigLoader`(master 是 LunaLib 自己)。

本节示例不提交此文件 (无 PNG 资产)；不提供时设置界面用默认图标，不影响任何设置功能。

文件写盘之后，运行时代码要拿到这些值，只能通过读取链。

## 运行时读取：LunaSettings 静态读取 API

`lunalib.lunaSettings.LunaSettings` 是 Kotlin object，读取方法全部 `@JvmStatic`，Java 侧直接静态调用 (`LunaSettings.getBoolean(modId, fieldId)`)：

| 方法 | 返回 | 转换与失败行为 |
|---|---|---|
| `getBoolean(modId, fieldId)` | `Boolean`(boxed，可 null)| 直接读 JSON 布尔值 |
| `getInt(modId, fieldId)` | `Integer`(可 null)| 直接读 JSON 整数值 |
| `getDouble(modId, fieldId)` | `Double`(可 null)| 直接读 JSON 数值 |
| `getFloat(modId, fieldId)` | `Float`(可 null)| **从保存的 double 转 float**("Since floats dont exist in .json's, it just converts a saved double")|
| `getString(modId, fieldId)` | `String`(可 null)| 原样字符串 |
| `getColor(modId, fieldId)` | `java.awt.Color`(可 null)| 读字符串；**无 `#` 前缀时自动补 `#`**，`trim().uppercase()` 后 `Color.decode`；非法十六进制 → 捕获 → null + `ERROR` 日志 |

三条约定：

1. **懒加载**：`if (!LunaSettingsLoader.hasLoaded) LunaSettingsLoader.load()`—— 首次调用会触发整份 "解析全部启用 MOD 的 CSV + 落盘默认值 + 读入运行时表"。
   - 因此任何一个已启用 MOD 的 `LunaSettings.csv` 损坏，都会让**所有 MOD** 的首次读取抛异常 (见故障分析第 4 条)。
2. **可 null**：MOD 不存在 → `log.error("LunaSettings: Could not find mod $ModID")` 并返回 null。
   - 字段缺失或类型不符 → `log.error("LunaSettings: Value $FieldID of type ... not found in JSONObject (ModID: $ModID)")` 并返回 null。
   - **必须判空后再拆箱**，直接拆箱 null 抛 NPE。
3. **每次现读运行时表**：getter 不做进程级缓存，保存后运行时表已被 `Settings.put` 更新，同一 JVM 内再读取即新值 ——"读取点自己缓存旧值" 才是设置不生效的常见原因 (故障分析第 6 条)。

::: warning "设置没生效" 先查读取点的缓存
`LunaSettings.getX` 每次现读运行时表、不做进程级缓存 ——"设置界面改了、游戏里不变" 的常见根因是读取点自己把值缓存进了字段，且没注册监听器。需要热更新的缓存放进 `settingsChanged` 里重读。
:::

规则：

6. **必须**：读取结果使用前判空 (boxed 值可 null)。违反的可观察结果：`boolean b = LunaSettings.getBoolean(...)` 在键缺失时 NPE(拆箱发生在赋值时)，调用点崩溃。
7. **必须**：调用任何 `lunalib.**` 之前先 `Global.getSettings().getModManager().isModEnabled("lunalib")` 做启用判断 (与硬依赖检测专题一致)。
   - 违反的可观察结果：LunaLib 未启用时首次解析到含 `LunaSettings` 引用的类 → `NoClassDefFoundError`(`Error` 不被捕获包装，直接冒泡)。
   - 启用判断失败时回退默认值是成熟实例的标准做法：`if (isModEnabled("lunalib")) return LunaSettings.getBoolean(MOD_ID, key); return 默认值;`。

读取链每次现读运行时表，但读取方若把值缓存进字段，界面保存后仍会拿到旧值；通知链解决的就是 "改动何时推给使用方"。

## 变更通知：LunaSettingsListener

`lunalib.lunaSettings.LunaSettingsListener` 是单方法接口：`void settingsChanged(String modId)` ——"gets called whenever settings are saved"。

- **注册**：`LunaSettings.addSettingsListener(listener)`(`@JvmStatic`)；`removeSettingsListener(listener)`；幂等查询 `hasSettingsListenerOfClass(Class)`。
- 注意：**不要用 `addListener` / `removeListener` / `hasListenerOfClass`**—— `@Deprecated("Deprecated due to not being JvmStatic, please use addSettingsListener() instead.")`。
- 这三个不是静态方法，Java 侧调用走对象实例 (`LunaSettings.INSTANCE`)，且语义被官方明确废弃。
- **触发时机**：设置界面保存按钮流程的最后一步 `if (!newGame) LunaSettings.reportSettingsChanged(mod)`(`lunalib.backend.ui.settings.LunaSettingsUIModsPanel`)—— 即**用户在设置界面改动并保存后**回调。
- 代码侧没有任何写设置入口 (LunaLib 的设计就是 "界面保存，代码只读")。
- **遍历与隔离**：`reportSettingsChanged(modID)` 遍历监听器列表**，每个监听器的调用包在 `try/catch (Throwable)` 里**。
- 单个监听器抛异常只写一条 `debug` 日志 ("Failed to call LunaSettings listener for $modID")，不影响其他监听器 (`lunalib.lunaSettings.LunaSettings`)。
- **不去重**：`addSettingsListener` 直接 `listeners.add(listener)`，没有去重 —— 同一实例注册两次，回调执行两次。重复注册是 "回调执行多次" 的根因，用 `hasSettingsListenerOfClass` 先查实现幂等。
- **官方做法**：LunaLib 自己的 `LoadedSettings` 监听器 (`lunalib.backend.scripts.LoadedSettings`) 在 `settingsChanged(modID)` 里先 `if (modID == "lunalib")` 过滤，再重读键位缓存。
- **必须按自己的 MOD id 过滤**，否则任何一个 MOD 保存设置都会触发您的刷新逻辑。

规则：

8. **必须**：`settingsChanged(modId)` 先按自己的 MOD id 过滤 (如 `MOD_ID.equals(modId)`)，再刷新缓存/执行逻辑。违反的可观察结果：其他 MOD 的保存动作触发您的重载/重算，行为随其他 MOD 的用户操作而变化。
9. **必须**：注册幂等 (`hasSettingsListenerOfClass` 先查或只在一个回调里注册一次)。违反的可观察结果：同一次保存触发多次回调 (日志出现多条 `reloaded`)。
10. **必须**：注册入口要保证 LunaLib 存在 —— 注册本身也走第 7 条的启用判断。违反的可观察结果：LunaLib 未启用时 `NoClassDefFoundError`。

四条链路都介绍完了，下表把每一环的文件、API 与使用方收拢到一起，便于对照。

## 文件与 API 链路

| 环节 | 文件或 API | 使用方 / 行为 |
|---|---|---|
| 定义 | `data/config/LunaSettings.csv`(每个启用 MOD 一份)| `LunaSettingsLoader.loadDefault()`：`loadCSV("data/config/LunaSettings.csv", mod.id)`(modId 限定)；空 `fieldID` 行跳过；`defaultValue` 按类型转换 (不捕获异常)；`hasLoaded = true` |
| 用户设置文件 | `<存档目录>/common/LunaSettings/<modId>.json` | `loadCommonJSON("LunaSettings/<modId>.json", "data/config/LunaSettingsDefault.default")`(LazyLib JSON 读取工具：缺失即创建+落盘)；`saveDefaultsToFile` 补默认值 (`Header`/`Text` 跳过)、`data.save()` 写盘 |
| 保存入口 | LunaLib 设置界面 (`LunaSettingsUIModsPanel`)| 保存按钮 (`saveDelay = 5`)：`data.save()` → `Settings.put(mod, data)` → `if (!newGame) reportSettingsChanged(mod)` |
| 运行时读取 | `LunaSettings.getBoolean/getInt/getFloat/getDouble/getString/getColor(modId, fieldId)` | 懒加载 `load()`；MOD/字段缺失 → `ERROR` 日志 + null；`getFloat` 从 double 转；`getColor` 补 `#` 后 `Color.decode` |
| 变更通知 | `LunaSettingsListener.settingsChanged(modId)` | `addSettingsListener` 注册 (不去重)；`reportSettingsChanged` 逐个回调 (try/catch Throwable 隔离，`lunalib.lunaSettings.LunaSettings`)|
| 界面入口 | `KeybindsScript`(生涯 transient 脚本)| 默认键位 `luna_SettingsKeybind_new`(61)+ `Shift+F2` 备用键位；`openLunaCustomPanel(LunaSettingsUIMainPanel(false))` |
| 图标 (可选)| `data/config/LunaSettingsConfig.json` | `LunaSettingsConfigLoader`：`getMergedJSONForMod(..., "lunalib")` 读顶层键 `modId` 的 `iconPath` |
| 运行时检测 | `Global.getSettings().getModManager().isModEnabled("lunalib")` | 启用判断：LunaLib 未启用时回退代码默认值 [C13-S1]|

把前面分散在各节的十条规则集中列出，便于自查。

## 规则汇总

| # | 规则 | 强度 | 违反的可观察结果 |
|---|---|---|---|
| 1 | `fieldID` 与运行时读取 key 逐字一致 | 必须 | `getX` 返回 null + `ERROR` 日志 |
| 2 | `defaultValue` 能被 `fieldType` 解析 | 必须 | `NumberFormatException` 上抛 (加载期或首次读取调用点)；`Boolean` 写 `"1"`/`"0"` 静默变 false |
| 3 | `fieldType` 使用受支持类型名 | 必须 | 不转换、界面控件与读取语义不可预期 |
| 4 | CSV 路径固定 `data/config/LunaSettings.csv` | 必须 | 读取异常被吞，设置界面没有您的 MOD，无报错 |
| 5 | `Radio` 默认值出现在 `secondaryValue` 列表 | 建议 | UI 默认选中与运行时读取错位 |
| 6 | 读取结果判空后再拆箱 | 必须 | 拆箱 null 抛 NPE |
| 7 | 调用 `lunalib.**` 前 `isModEnabled("lunalib")` 启用判断 | 必须 (继承硬依赖检测专题)| LunaLib 未启用 → `NoClassDefFoundError` |
| 8 | `settingsChanged` 按自己的 modId 过滤 | 必须 | 其他 MOD 保存也触发您的逻辑 |
| 9 | 监听器注册幂等 (`hasSettingsListenerOfClass`)| 必须 | 一次保存多次回调 |
| 10 | 保存走 LunaLib 设置界面；不自己读写用户设置文件 | 建议 | 与运行时表不同步：改动丢失或读到旧值 |

规则只是清单。下面把四条链路完整实现进 GearIndustry：先写 CSV，再写三个类，最后在既有 `GearInModPlugin` 里接入两行。

## 完整最小例

在前置知识所列累计工程布局上追加 (MOD 根目录 `Starsector\mods\GearIndustry`)：

```text
Starsector\mods\GearIndustry\
├── build.gradle
├── settings.gradle
├── mod_info.json                # C1-S1/C13-S1 累积；本节不改动 dependencies
├── data\
│   └── config\
│       └── LunaSettings.csv     # 本节新增：设置项定义表（文件名由 LunaLib 固定）
├── src\
│   └── data\scripts\gearin\
│       ├── GearInModPlugin      # C6-S2 所有；本节追加两行接入
│       └── lib\
│           ├── GearInLunaSettingsDefs      # 本节新增：设置定义角色（常量）
│           ├── GearInLunaSettingsReader    # 本节新增：运行时读取角色（门控 getter）
│           └── GearInLunaSettingsListener  # 本节新增：变更通知角色（注册/注销）
└── jars\
```

### data/config/LunaSettings.csv(完整文件；九列表头与 LunaLib 官方一致)

```csv
fieldID,fieldName,fieldType,defaultValue,secondaryValue,fieldDescription,minValue,maxValue,tab
GearIn_visual_header,GearIn 显示设置,Header,,,设置界面里的分组标题。,,,GearIn
GearIn_trails_enabled,轨迹特效,Boolean,true,,"启用 GearIn 的轨迹特效；关闭后不再生成轨迹粒子。",,,GearIn
GearIn_max_entries,最大条目数,Int,12,,"面板最多同时显示的条目数。",1,100,GearIn
GearIn_pulse_delay,脉冲间隔（秒）,Double,1.5,,"两次脉冲之间的间隔秒数；运行时用 getFloat 读取。",0.1,10.0,GearIn
GearIn_marker_color,标记颜色,Color,#f22e6e,,"轨迹标记的颜色；十六进制字符串，读取时 # 前缀可省略。",,,GearIn
GearIn_label_text,标签文本,String,GearIn,,"面板标题栏显示的文本。",,,GearIn
GearIn_fullGraphicEffect,Full Graphic Effect,Boolean,true,,"是否启用生涯全屏转场特效；由 C14-S1 的可选依赖示例消费。",,,GearIn
```

### GearInLunaSettingsDefs(设置定义角色)

职责：设置定义角色 —— 持有 MOD id(`gear_in`)、LunaLib 的 MOD id(`lunalib`) 与五个 `KEY_*` / 五个 `DEFAULT_*` 常量。

它们逐字镜像 `data/config/LunaSettings.csv` 的 `fieldID` / `defaultValue` 列 (规则 1、2)。

`Header` 类型只做 UI 分组、不参与读取/保存，因此没有对应常量。

绑定位置：无注册入口，纯常量入口类；`GearInLunaSettingsReader` 与 `GearInLunaSettingsListener` 在包内直接使用这些常量。

```java
package data.scripts.gearin.lib;

import java.awt.Color;

/**
 * 设置定义角色：LunaSettings 字段 ID 与代码内默认值，与 data/config/LunaSettings.csv
 * 的 fieldID / defaultValue 列逐字一致（硬规则 1、2）。Header 类型只做 UI 分组，
 * 不参与读取/保存，因此没有对应的 KEY 常量。
 */
public class GearInLunaSettingsDefs {

    /** 本 MOD 的 id（C1-S1 命名契约）：也是 LunaSettings.csv 归属与用户设置文件的键。 */
    public static final String MOD_ID = "gear_in";

    /** LunaLib 的 MOD id（逐字来自其 mod_info.json，大小写敏感）。 */
    public static final String LUNA_LIB_MOD_ID = "lunalib";

    // 设置项 fieldID：与 data/config/LunaSettings.csv 的 fieldID 列逐字一致。
    public static final String KEY_TRAILS = "GearIn_trails_enabled";
    public static final String KEY_MAX_ENTRIES = "GearIn_max_entries";
    public static final String KEY_PULSE_DELAY = "GearIn_pulse_delay";
    public static final String KEY_MARKER_COLOR = "GearIn_marker_color";
    public static final String KEY_LABEL_TEXT = "GearIn_label_text";

    // 代码内默认值：LunaLib 未启用或读取返回 null 时的回退（与 CSV 的 defaultValue 一致）。
    public static final boolean DEFAULT_TRAILS = true;
    public static final int DEFAULT_MAX_ENTRIES = 12;
    public static final float DEFAULT_PULSE_DELAY = 1.5f;
    public static final Color DEFAULT_MARKER_COLOR = new Color(0xf2, 0x2e, 0x6e);
    public static final String DEFAULT_LABEL_TEXT = "GearIn";

    private GearInLunaSettingsDefs() {
        // 工具类：不实例化
    }
}
```

### GearInLunaSettingsReader(运行时读取角色)

职责：运行时读取角色 —— 用 `isLunaLibEnabled()` 做启用判断，再提供五个三段式 getter (先判断，再 `LunaSettings.getX(MOD_ID, KEY_*)`，判空回退 `DEFAULT_*`；规则 6、7)。

绑定位置：无注册入口，静态工具类；由 `GearInLunaSettingsListener.logCurrentValues` 与接入片段消费。

类加载路径不引用 `lunalib.**`(`lunalib` 类型只出现在方法体调用里，不在字段、静态初始化与签名中)，LunaLib 未启用时可直接安全调用。

```java
package data.scripts.gearin.lib;

import java.awt.Color;

import com.fs.starfarer.api.Global;

import lunalib.lunaSettings.LunaSettings;

/**
 * 运行时读取角色：门控读取 LunaLib 设置（LunaSettings 静态读取 API）。
 * 每个 getter 都是三段式：先过 isModEnabled("lunalib") 门（C13-S1），
 * 再调用 LunaSettings.getX，返回 null 时回退代码内默认值（Defs）。
 * 类自身不引用 lunalib.** 于类加载路径，未启用时可直接安全调用。
 */
public class GearInLunaSettingsReader {

    private GearInLunaSettingsReader() {
        // 工具类：不实例化
    }

    /** LunaLib 是否已启用：调用任何 lunalib.** 之前必须先通过这道门（C13-S1）。 */
    public static boolean isLunaLibEnabled() {
        return Global.getSettings().getModManager().isModEnabled(GearInLunaSettingsDefs.LUNA_LIB_MOD_ID);
    }

    /** 轨迹特效开关：Boolean 设置，缺省 true。 */
    public static boolean isTrailsEnabled() {
        if (!isLunaLibEnabled()) {
            return GearInLunaSettingsDefs.DEFAULT_TRAILS;
        }
        Boolean value = LunaSettings.getBoolean(GearInLunaSettingsDefs.MOD_ID, GearInLunaSettingsDefs.KEY_TRAILS);
        return value != null ? value : GearInLunaSettingsDefs.DEFAULT_TRAILS;
    }

    /** 面板最大条目数：Int 设置，缺省 12。 */
    public static int getMaxEntries() {
        if (!isLunaLibEnabled()) {
            return GearInLunaSettingsDefs.DEFAULT_MAX_ENTRIES;
        }
        Integer value = LunaSettings.getInt(GearInLunaSettingsDefs.MOD_ID, GearInLunaSettingsDefs.KEY_MAX_ENTRIES);
        return value != null ? value : GearInLunaSettingsDefs.DEFAULT_MAX_ENTRIES;
    }

    /** 脉冲间隔（秒）：CSV 里是 Double 类型，读取时用 getFloat（从保存的 double 转换）。 */
    public static float getPulseDelay() {
        if (!isLunaLibEnabled()) {
            return GearInLunaSettingsDefs.DEFAULT_PULSE_DELAY;
        }
        Float value = LunaSettings.getFloat(GearInLunaSettingsDefs.MOD_ID, GearInLunaSettingsDefs.KEY_PULSE_DELAY);
        return value != null ? value : GearInLunaSettingsDefs.DEFAULT_PULSE_DELAY;
    }

    /** 标记颜色：Color 设置，保存/读取十六进制字符串（# 前缀可省略）。 */
    public static Color getMarkerColor() {
        if (!isLunaLibEnabled()) {
            return GearInLunaSettingsDefs.DEFAULT_MARKER_COLOR;
        }
        Color value = LunaSettings.getColor(GearInLunaSettingsDefs.MOD_ID, GearInLunaSettingsDefs.KEY_MARKER_COLOR);
        return value != null ? value : GearInLunaSettingsDefs.DEFAULT_MARKER_COLOR;
    }

    /** 标签文本：String 设置，按原样读取。 */
    public static String getLabelText() {
        if (!isLunaLibEnabled()) {
            return GearInLunaSettingsDefs.DEFAULT_LABEL_TEXT;
        }
        String value = LunaSettings.getString(GearInLunaSettingsDefs.MOD_ID, GearInLunaSettingsDefs.KEY_LABEL_TEXT);
        return value != null ? value : GearInLunaSettingsDefs.DEFAULT_LABEL_TEXT;
    }
}
```

### GearInLunaSettingsListener(变更通知角色)

职责：变更通知角色 —— 幂等注册/注销监听器 (`registerListener` / `unregisterListener`：先做启用判断，再 `hasSettingsListenerOfClass` 先查)，`settingsChanged` 按 `MOD_ID` 过滤后输出 `reloaded` 日志并重读当前值；

`logCurrentValues` / `toHex` 随本角色持有 (接入片段进档时调用一次，对照用)。

绑定位置：`registerListener()` 接入 `GearInModPlugin.onGameLoad`([C6-S2] 所有，本节示例类不含该片段)。

行为纪律：监听器实例 (匿名 `LunaSettingsListener`) 在 `registerListener()` 方法体内、启用判断通过**之后**才创建，而不是放在静态字段初始化器里 —— 类加载路径零 `lunalib.**` 引用，LunaLib 未启用时 (硬依赖检测专题场景) 调用本类不会抛 `NoClassDefFoundError`。

```java
package data.scripts.gearin.lib;

import java.awt.Color;

import org.apache.log4j.Logger;

import com.fs.starfarer.api.Global;

import lunalib.lunaSettings.LunaSettings;
import lunalib.lunaSettings.LunaSettingsListener;

/**
 * 变更通知角色：注册/注销 LunaSettings 监听器，在设置界面保存后按本 MOD 过滤并
 * 重读输出当前生效值。addSettingsListener 不去重，注册前必须用
 * hasSettingsListenerOfClass 幂等查询（重复注册会让同一次保存触发多次回调）。
 *
 * 监听器实例在门控通过之后才创建 —— 类加载路径不触碰 lunalib.**，LunaLib 未启用
 * 时（C13-S1 场景）调用本类不会抛 NoClassDefFoundError，门控纪律见硬规则 7、10。
 */
public class GearInLunaSettingsListener {

    private static final Logger LOG = Global.getLogger(GearInLunaSettingsListener.class);
    private static LunaSettingsListener registered;

    private GearInLunaSettingsListener() {
        // 工具类：不实例化
    }

    /**
     * 注册（幂等）：LunaLib 未启用时跳过并写 warn。注册入口见页面正文的接入片段
     * （GearInModPlugin.onGameLoad 一行）；监听器实例在门控之后才创建。
     */
    public static void registerListener() {
        if (!GearInLunaSettingsReader.isLunaLibEnabled()) {
            LOG.warn("GearIn luna settings: LunaLib not enabled, listener not registered");
            return;
        }
        if (registered != null && LunaSettings.hasSettingsListenerOfClass(registered.getClass())) {
            LOG.info("GearIn luna settings: listener already registered");
            return;
        }
        registered = new LunaSettingsListener() {
            @Override
            public void settingsChanged(String modId) {
                if (!GearInLunaSettingsDefs.MOD_ID.equals(modId)) {
                    return; // 其他 MOD 的保存动作不影响本 MOD 的缓存
                }
                LOG.info("GearIn luna settings: reloaded");
                logCurrentValues();
            }
        };
        LunaSettings.addSettingsListener(registered);
        LOG.info("GearIn luna settings: listener registered");
    }

    /** 取消注册（测试清理或卸载场景）。 */
    public static void unregisterListener() {
        if (!GearInLunaSettingsReader.isLunaLibEnabled()) {
            return;
        }
        if (registered != null) {
            LunaSettings.removeSettingsListener(registered);
            registered = null;
        }
    }

    /** 输出当前生效值：用于对照设置界面与用户设置文件（进档时可调用一次）。 */
    public static void logCurrentValues() {
        LOG.info("GearIn luna settings: trails=" + GearInLunaSettingsReader.isTrailsEnabled()
                + " maxEntries=" + GearInLunaSettingsReader.getMaxEntries()
                + " pulseDelay=" + GearInLunaSettingsReader.getPulseDelay()
                + " markerColor=" + toHex(GearInLunaSettingsReader.getMarkerColor())
                + " labelText=" + GearInLunaSettingsReader.getLabelText());
    }

    private static String toHex(Color color) {
        return String.format("#%02x%02x%02x", color.getRed(), color.getGreen(), color.getBlue());
    }
}
```

::: caution 启用判断必须先于任何 `lunalib.**` 引用
"调用前先判断是否启用" 对类加载同样成立：监听器实例的创建、`LunaSettings` 的静态调用等一切 `lunalib.**` 引用都必须发生在 `isLunaLibEnabled()` 判断通过之后 —— 不要在静态初始化器或字段初始化里触碰 `lunalib` 类型。

违反的可观察结果：LunaLib 未启用时首次解析到该类 → `NoClassDefFoundError` 直接冒泡 (`Error` 不被捕获包装)。
:::

### 职责分工与协作 (对应关系)

- `GearInLunaSettingsDefs.MOD_ID = "gear_in"`：CSV 归属与用户设置文件的文件名键 (`LunaSettings/gear_in.json`)。
- `LUNA_LIB_MOD_ID = "lunalib"` 逐字来自 `Starsector\mods\Lunalib\mod_info.json` 的 `id`。
- Defs 的五个 `KEY_*` 常量与 CSV 的 `fieldID` 列逐字一致 (规则 1)；五个 `DEFAULT_*` 与 CSV 的 `defaultValue` 一致 (规则 2 的 "代码内回退与 CSV 默认值保持一致")。
- Reader 每个 getter 先做 `isLunaLibEnabled()` 启用判断 (规则 7)，再调用 `LunaSettings.getX`，再判空回退 (规则 6)—— 三段式是读取侧标准写法。
- `getPulseDelay` 用 `getFloat`：演示 "CSV 类型 Double、读取转 float"；`getMarkerColor` 用 `getColor`：演示十六进制颜色读取。
- Listener 的匿名监听器按 `MOD_ID.equals(modId)` 过滤 (规则 8)，回调里重读并输出一行日志 (`reloaded` + 当前值)。
- Listener 的 `registerListener` 用 `hasSettingsListenerOfClass` 幂等 (规则 9)；LunaLib 未启用时写 warn 跳过 (与启用判断同一纪律)。
- Listener 的 `logCurrentValues` 输出格式与页面预期结果一致；`toHex` 把 `Color` 转回 `#rrggbb` 便于对照用户设置文件。

### 接入片段 (在既有 GearInModPlugin 中追加两行)

接入片段 (工程片段 ——`GearInModPlugin` 归 [C6-S2] 所有，本节不改动那些文件；本节示例类不含该片段)：

```java
    // GearInModPlugin（C6-S2 所有）onGameLoad(boolean newGame) 内新增两行
    GearInLunaSettingsListener.registerListener();   // 幂等注册；LunaLib 未启用时自动跳过并写 warn
    GearInLunaSettingsListener.logCurrentValues();  // 每次进档输出当前生效值（对照用）
```

### 构建

构建命令 (在 `Starsector\mods\GearIndustry\` 执行)：

```text
gradle jar
```

产物 `jars\GearIndustry.jar` 应包含 `data/scripts/gearin/lib/` 下三个类的 `.class` 条目 (可用 `jar tf` 核对)。

三个条目：`GearInLunaSettingsDefs.class`、`GearInLunaSettingsReader.class`、`GearInLunaSettingsListener.class`。

## 验证方案

前置数据：[C13-S1] 与 [C13-S4] 的累计工程、本节的 `data\config\LunaSettings.csv`、三个示例类、`GearInModPlugin.onGameLoad` 中的两行接入代码，以及已安装并勾选的 LunaLib。

1. 在 `Starsector\mods\GearIndustry\` 执行 `gradle jar`，再用 `jar tf jars\GearIndustry.jar` 核对三个类与 `data\config\LunaSettings.csv`。
2. 勾选 LunaLib 与 "齿轮工业" 启动游戏，到主菜单后退出。
3. 打开 `<存档目录>\common\LunaSettings\gear_in.json`，核对五个数据键。
4. 新建或读取生涯存档，查看注册与取值日志；再次进入存档，核对监听器注册状态。
5. 进入生涯后按设置键位 (默认 61，`Shift+F2` 始终可用) 打开 LunaLib 设置界面。
6. 打开 `GearIn` 页签，核对分组标题、五项设置和控件类型。
7. 把 `GearIn_pulse_delay` 改为 `3.0` 并保存，查看日志与用户设置文件。
8. 重启游戏后再次进入存档，核对 `pulseDelay`。
9. 手动删除 `gear_in.json` 中的 `GearIn_max_entries` 键，再重启游戏。

预期结果：

- 构建以 `BUILD SUCCESSFUL` 结束；`jar tf` 能看到 `GearInLunaSettingsDefs.class`、`GearInLunaSettingsReader.class`、`GearInLunaSettingsListener.class` 与 `data/config/LunaSettings.csv`。
- 启动过程没有由 `gear_in` 引起的 `ERROR`，用户设置文件生成五个数据键，不含 `GearIn_visual_header`，其初始内容为：

  ```json
  { "GearIn_trails_enabled": true, "GearIn_max_entries": 12, "GearIn_pulse_delay": 1.5, "GearIn_marker_color": "#f22e6e", "GearIn_label_text": "GearIn" }
  ```

- 进入存档时出现监听器注册与当前值日志：

  ```text
  GearIn luna settings: listener registered
  GearIn luna settings: trails=true maxEntries=12 pulseDelay=1.5 markerColor=#f22e6e labelText=GearIn
  ```

- 再次进入存档时注册日志变为 `listener already registered`，没有重复监听器。
- 设置界面左侧出现 "齿轮工业"；`GearIn` 页签显示分组标题 `GearIn 显示设置` 和五项设置，控件分别为开关、滑条与数字框、颜色选择器和文本输入框。
- 保存后出现 `reloaded` 与 `pulseDelay=3.0` 的当前值日志，`gear_in.json` 中 `GearIn_pulse_delay` 变为 `3.0`。
- 重启并进入存档后，`logCurrentValues` 仍读到 `pulseDelay=3.0`。
- 删除 `GearIn_max_entries` 后重启，该键以 `12` 补回，`pulseDelay=3.0` 继续保留。

清理：在设置界面把 `GearIn_pulse_delay` 恢复为 `1.5`。

## 故障分析

1. **调用点 NPE，堆栈指向 `LunaSettings.getX(...)` 的赋值行。**`getX` 在 MOD 或字段缺失时返回 null，`boolean b = LunaSettings.getBoolean(...)` 这类直接拆箱会在赋值时抛 NPE (拆箱发生时返回的 `Boolean` 对象为 null)。修正：按本节 getter 的三段式先判空回退，再查 `starsector.log` 中带 `LunaSettings:` 前缀的 `ERROR` 行确认根因。
2. **`ERROR LunaSettings: Could not find mod <xxx>`，读取恒为默认值。**触发条件是 modId 传错：最常见是传了显示名 (`LunaLib`、`齿轮工业`)，或把 `mod_info.json` 的 `id` 拼错、大小写不符。每条读取各记一条 `ERROR`，值全部回退。修正：modId 用 `mod_info.json` 的 `id` 逐字 (`gear_in`、`lunalib`)。
3. **`ERROR LunaSettings: Value <key> of type ... not found in JSONObject (ModID: ...)`。**触发条件是 `fieldID` 与 CSV 不一致 (拼写、大小写、前后缀)，或读取方法与存储类型不符 (如对 `Double` 键调 `getBoolean`)。该键读取返回 null 并写这条 `ERROR`。修正：把 `KEY_*` 常量与 CSV `fieldID` 列逐字对照，再按读取方法表核对类型。
4. **首次读取调用点抛 `NumberFormatException`(或 LunaLib 应用加载期失败)。**某个**已启用** MOD(不一定是自己的) 的 `LunaSettings.csv` 里 `defaultValue` 与 `fieldType` 不匹配 (如 `Int` 写 `abc`、`Double` 写空串)，异常沿 `loadDefault` → `load()` 上抛 —— LunaLib 的 `onApplicationLoad` 失败，或所有 MOD 的首次 `getX` 都抛异常，`starsector.log` 的异常堆栈指向类型转换行。修正：修好所有启用 MOD 的 CSV 默认值；这是 "自己设置正常、别人一读就炸" 的隐蔽方向。
5. **`Boolean` 开关语义反转。**CSV `defaultValue` 写 `1` / `0`(或 `"true"` 之外的其他拼写) 就会触发：Kotlin `toBoolean()` 只认忽略大小写的 `"true"`，其余全部解析为 false。表现是不报错、无日志，设置界面默认是关、代码读到 false，与 CSV 意图相反。修正：`defaultValue` 写 `true` / `false`。
6. **设置界面改了并保存，游戏里读取值不变。**触发条件是读取点把值缓存进了字段 (只在进档时读一次) 且没注册监听器。此时 `gear_in.json` 已更新、界面显示新值，但运行逻辑仍用旧值；日志没有 `GearIn luna settings: reloaded`(说明监听器未注册或未过滤到本 MOD)。修正：需要热更新的缓存放在 `settingsChanged` 里重读 (注册入口见接入片段)；不需要热更新的值按进档/重启生效理解。`getX` 本身每次现读运行时表，不做进程级缓存。
7. **一次保存触发多次回调。**`registerListener` 被多个回调重复调用 (如 `onApplicationLoad` 和 `onGameLoad` 各调一次) 就会触发，因为 `addSettingsListener` 不去重。表现是保存一次，日志出现多条 `reloaded`。修正：`hasSettingsListenerOfClass` 先查 (本节示例已内置)，或只在单一回调里注册。
8. **其他 MOD 保存设置后您的逻辑被触发。**触发条件是 `settingsChanged(modId)` 没按 modId 过滤：别的 MOD 用户保存设置，您的刷新/重算代码跟着执行。修正：先 `MOD_ID.equals(modId)` 再干活 (`LoadedSettings` 官方做法)。
9. **`NoClassDefFoundError`，堆栈指向 `lunalib.lunaSettings.LunaSettings`。**LunaLib 未启用/未安装时，代码没做启用判断就调用 `lunalib.**`(或启用判断写在 `LunaSettings` 引用之后)。`Error` 直接冒泡 (脚本加载的错误处理只包装 `Exception`，与硬依赖检测专题一致)，启动或调用点崩溃。修正：启用判断必须发生在任何 `lunalib.**` 引用之前 (本节 `GearInLunaSettingsReader.isLunaLibEnabled()` 三段式的第一段)；"判断先于引用" 对类加载同样成立 —— 未启用的类首次解析即抛。
10. **`getColor` 返回 null，日志 `Value <key> of type String not found`。**用户设置文件里该键存了非法十六进制 (如 `hello`)：`Color.decode` 抛异常被捕获转 null。颜色回退代码默认值，并写一条 `ERROR`。修正：给用户文件该键写回合法 `#rrggbb`；代码侧判空回退备用值。
11. **自己直接读写 `LunaSettings/gear_in.json` 后改动丢失或读到旧值。**绕过 LunaLib 保存链 (比如用 `writeJSONToCommon` 直接改用户设置文件)：文件改了但运行时表没更新 (`LunaSettingsLoader.Settings` 是旧对象)，代码读到旧值直到下次 `load()`；反过来直接改运行时表，保存时会被 `data.save()` 覆盖。原因在于用户设置文件由 LunaLib 独占管理 (`saveDefaultsToFile` + 界面保存)。修正：不要自己读写该文件 —— 需要 "代码改设置" 的场景用 `SettingsCreator` 的运行时定义 (`LunaSettings.SettingsCreator.addX(...)` + `refresh(modId)`)，"Has to be called after Settings have been added, otherwise they will not load."；让用户在界面里保存。本节示例不覆盖该场景。
12. **设置界面里没有 "齿轮工业" 条目。**CSV 路径/文件名错 (如放在 `data/config/GearIn_luna_settings.csv` 或大小写不一致)、或 `fieldID` 列全空都会触发。`loadCSV` 异常被吞，因此无任何报错，界面列表缺本 MOD。修正：核对 `data/config/LunaSettings.csv` 路径与九列表头 (`loadCSV` 路径规则：`/` 分隔符、无 `..`)。

## 总结

LunaLib 设置完整流程共四段：定义 (每个启用 MOD 一份的 `data/config/LunaSettings.csv`，`fieldID` 即运行时读取 key，`defaultValue` 按 `fieldType` 转换，转换失败是唯一能让加载直接抛异常的写法)、保存 (用户设置文件 `<存档目录>/common/LunaSettings/<modId>.json` 由 LunaLib 首次加载创建、设置界面保存按钮更新，`Header` / `Text` 不落盘、缺键补默认值)、读取 (静态 getter 懒加载、boxed 可 null、缺失写 `ERROR` 日志) 与通知 (保存后逐个回调，单监听器异常被隔离，回调必须按自己的 modId 过滤、注册必须幂等)。

排查 "设置没生效" 时按链逐层定位：先看设置界面有没有自己的 MOD 与页签 (定义链，问题在 CSV 路径或类型)，再核对 `gear_in.json` 内容 (保存链，问题在是否点了保存)，再看 `starsector.log` 的 `LunaSettings:` `ERROR` 行与代码读取值 (读取链，问题在 modId / fieldID / 判空)，最后看 `listener registered` 与 `reloaded` 日志 (通知链，问题在注册入口与过滤)。

无论哪一段，调用 `lunalib.**` 前先做 `isModEnabled("lunalib")` 启用判断并回退代码默认值 (与硬依赖检测专题一致，成熟实例同款做法)。本节的使用写法 (启用判断 + 判空 + 幂等注册) 是通用骨架，可原样平移到其他可选依赖库的接入上；对照另一套 `MagicSettings` 设置链即可看到同一套 "定义 —— 保存 —— 读取 —— 通知" 流程。`SettingsCreator` 的运行时定义与本节静态 CSV 定义互补，适合需要按用户进度动态出设置的场景。调试菜单、Refit UI 等其余 LunaLib 功能不在本节范围。

## 建议参考项

- `lunalib.lunaSettings.LunaSettings`：静态读取 API(六个 `getX(modId, fieldId)`)。
- 监听器注册入口：`addSettingsListener` / `hasSettingsListenerOfClass` / `removeSettingsListener`。
- `lunalib.lunaSettings.LunaSettingsListener`：单方法变更通知接口，`settingsChanged(modId)`。
- `lunalib.backend.ui.settings.LunaSettingsLoader`：设置链加载器 (`loadDefault` / `saveDefaultsToFile` / `loadSettings`)。
- `lunalib.backend.ui.settings.LunaSettingsConfigLoader`：设置界面图标读取 (`getIconPath(modId)`)，可选功能。
- `lunalib.backend.scripts.LoadedSettings`：官方监听器写法 ——`settingsChanged` 先按自己的 MOD id 过滤。
- `lunalib.backend.scripts.KeybindsScript`：设置界面键位入口 (默认键位 + `Shift+F2` 备用键位)。
- `com.fs.starfarer.api.Global`：`getSettings().getModManager().isModEnabled(...)` 启用判断入口。
- `data/scripts/gearin/lib`：本节三个示例类 (`GearInLunaSettingsDefs` / `GearInLunaSettingsReader` / `GearInLunaSettingsListener`) 所在包。
- `data/config/LunaSettings.csv`：设置项定义表 (九列)，文件名与读取路径由 LunaLib 固定。
- `data/config/LunaSettingsConfig.json`：可选，设置界面 MOD 图标配置。
- `<存档目录>/common/LunaSettings/<modId>.json`：用户设置文件，LunaLib 独占管理。
