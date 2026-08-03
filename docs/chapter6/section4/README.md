# 全局服务与资源读取

`Global` 门面交出的四个服务对象里，`SettingsAPI`（`Global.getSettings()`）是 MOD 代码中除了 `SectorAPI` 之外最高频的对象 —— 它既是 `data/config/settings.json` 与 `data/strings/strings.json` 的取值入口，也是文本 / JSON / CSV / 合并数据的读取入口，还是各类 spec 的读取入口。前置页面已把 `Global` 门面本身讲透，本节按服务主题校订这四个服务对象：`SettingsAPI` 是主体（取值语义与资源读取），`Logger` 与 `SoundPlayerAPI` 给出用法约定；spec 族由武器、船体、技能等主题章节按主题消费，本节只交代取值语义与通用资源读取。

*本节预期阅读时间：~30 分钟。*

*本节预期操作时间：~60 分钟。*

## 学习目标

完成后，您将能够：

- 说出 `SettingsAPI` 的三大职责面：取值（合并后的 settings.json）、资源读取（文本/JSON/CSV/合并/common 文件）、spec 读取入口，并说出每个读取方法抛什么异常、缺失键时什么行为；
- 区分 `getString(String key)` 与 `getString(String category, String id)`：前者读 settings.json、键缺失抛 `RuntimeException`，后者读 strings.json、键缺失**不抛异常**而是返回占位文本 `Missing string: [...]` 并打一条 `ERROR` 日志 —— 并解释 "双参第一参是默认值" 为什么是误解；
- 说出 settings.json 的合并模型：核心与全部启用 MOD 的对象键合并、启动器关键键被排除、MOD 只提交自己的键；以及 `getInt` 的截断语义（`(int) getFloat(key)`）与 `setFloat`/`setBoolean` 的 "只改内存不落盘" 语义；
- 用 `loadText` / `loadJSON` / `loadCSV` 读单文件（含 `modId` 限定与 `withMods=false` 只读核心），用 `getMergedJSON` / `getMergedJSONForMod` / `getMergedSpreadsheetData` / `getMergedSpreadsheetDataForMod` 做合并读取，说出两者适用场景的区别；
- 用 `readTextFileFromCommon` / `writeTextFileToCommon` / `readJSONFromCommon` / `writeJSONToCommon` 读写 common 文件，说出缺失文件返回空串、单次写入 1MB 上限与跨文件总量上限的失败行为；
- 复述资源路径必须遵守的规则：**必须**使用 `/` 分隔符，**禁止** `..`、`\`、`:` 与首字符 `/`，违反在调用点抛 `RuntimeException`；
- 用 `Global.getLogger(Class)` 记录日志、用 `Global.getSoundPlayer()` 播放声音（消费 C06-S03 的约定与 `S05` 系列注册的 ID），并定位 "settings 键缺失" 与 "字符串键缺失" 两类截然不同的故障症状。

## 前置知识

本节消费 `Global` 门面与声音链的既有约定，建议按以下顺序具备前置：

- `Global` 门面与四阶段 null 语义（C06-S03）：七个 getter，`getSettings()`/`getSoundPlayer()`/`getLogger()` 全程非 null，Logger 与 SoundPlayerAPI 的完整约定。本节不重复其内容，只消费结论。
- 生命周期回调（C06-S02）：`onApplicationLoad` 在全部资源加载完成后回调（本节示例的日志调用点）；persistent data 守卫。
- 构建与配置基础（C06-S01）：编译 JAR 链路；`mod_info.json`；"MOD 的 settings.json 与全局设置按对象键合并，只提交自己的键"。
- 声音链（C05-S01 / C05-S02 / C05-S03）：三个声音 ID 的资产、注册与使用方；本节消费 `GearIn_weapon_fire`，不新增声音 ID。
- 入门基础（C01-S01）：MOD 目录结构、`starsector.log`、数据文件必须 UTF-8 无 BOM（`readText` 不剥离 BOM）。
- 规则系统（S11 系列）：规则段消费 settings（本节只交代 settings 是规则的数据源）。

## 文件与 API 链路

四个服务对象都由 `Global` 静态门面交出，链路如下：

| `Global` 入口 | 返回 | 本节职责 / 下游 |
|---|---|---|
| `getSettings()` | `SettingsAPI` | 取值（settings.json / strings.json）、资源读取（文本/JSON/CSV/合并/common）、spec 读取（`S01`/`S02`/`S07`/`S08` 各页消费）、规则数据源（`S11` 系列）|
| `getLogger(Class)` | log4j `Logger` | `starsector.log`（C01-S01）；本节给出用法约定 |
| `getSoundPlayer()` | `SoundPlayerAPI` | 播放入口；完整约定在 C06-S03、使用方族谱在 C05-S03 |

资源读取链（本节示例的数据文件都在 MOD 根目录下）：

```text
data/config/settings.json      ← 对象键合并（核心 + 全部启用 MOD；启动器关键键排除）
data/strings/strings.json      ← 对象键合并（getString 双参的数据源）
data/config/gear_in/*.json|*.csv   ← MOD 自有的数据文件（本节示例新增）
<存档目录>/common/*.data       ← common 文件（读写入口；存档目录由启动器指定）
        ↓ 读取入口
SettingsAPI.loadText / loadJSON / loadCSV        （单文件）
SettingsAPI.getMergedJSON / getMergedSpreadsheetData
  / getMergedJSONForMod / getMergedSpreadsheetDataForMod   （合并）
SettingsAPI.readTextFileFromCommon / readJSONFromCommon    （common）
        ↓
MOD 代码（本节示例六个工具类，编入 jars\GearIndustry.jar）
```

## SettingsAPI：取值语义（0.98a-RC8）

### settings.json 的合并模型

settings.json 与声音注册页的 sounds.json 一样是对象键合并：核心与全部启用 MOD 的 `data/config/settings.json` 合并成一个对象（合并时应用启动器排除键）。

因此：

- MOD 的 settings.json **只提交自己的键**，不要复制整份原版文件。违反的可观察结果：后加载者覆盖先加载者的同名标量键，MOD 的文件会静默改写原版键值，全局行为（其他 MOD 与原版读到的值）被这个副本决定，且随加载顺序变化。
- 合并时有一组**启动器关键键被排除**，MOD 提交它们不生效：`vsync`、`fps`、`refreshRate`、`devMode`、`godMode`、`playtestingMode`、`resolutionOverride`、`forceAspectRatio`、`windowLocationX`、`windowLocationY`、 `undecoratedWindow`、`queryMaxAASamples`、`maxAASamples`、 `screenScaleOverride`、`maxMemoryMB`、`minMemoryMB` 等。
- 数组键（如 `music_*`、`*color*`）与标量键在合并中的替换/追加规则同声音注册页所述（`mergeJSON`：标量后加载者覆盖、数组追加、`music_`/color 数组整组替换）。

### 取值方法族

以下方法全部以合并后的 settings.json 顶层为对象：

| 方法 | 数据源 | 键缺失 / 类型不符时的行为 |
|---|---|---|
| `getString(String key)` | settings.json | 抛 `RuntimeException`（包装 JSONException），**不返回 null** |
| `getString(String category, String id)` | strings.json（`{category: {id: text}}`）| **不抛异常**：返回字面文本 `Missing string: [<id>]` 并打一条 `ERROR` 日志 |
| `getFloat(String key)` | settings.json | 抛 `RuntimeException`（`getDouble` 包装）|
| `getBoolean(String key)` | settings.json | 抛 `RuntimeException` |
| `optBoolean(String key, boolean defaultValue)` | settings.json | 返回默认值（SettingsAPI 上唯一带默认的取值方法）|
| `getInt(String key)` | settings.json | `(int) getFloat(key)`：小数**截断**；缺失抛 `RuntimeException` |
| `getColor(String key)` | settings.json（`[r,g,b,a]` 或颜色名）| 抛 `RuntimeException` |
| `getJSONObject(String key)` / `getJSONArray(String key)` | settings.json | 抛 `JSONException`（受检，必须处理）；类型不符同样抛 |
| `getSettingsJSON()` | 合并后的可变对象 | 直接修改它**之后必须调 `resetCached()`**，否则 `floatCache`/`booleanCache`/颜色缓存读到旧值 |
| `setFloat(String key, Float)` / `setBoolean(String key, Boolean)` | 写 settingsJson + 缓存 | **只改内存，不写任何文件**：重启即丢；需要持久化请用 common 文件（见下）|

`getFloat`/`getBoolean`/`optBoolean`/`getColor` 等取值方法带进程级缓存（`floatCache`/`booleanCache`/颜色缓存）：同键重复取值不会重新解析；

因此 `getSettingsJSON()` 被修改后不 `resetCached()`，后续 `getFloat`/`getBoolean`/`getColor` 会继续返回旧缓存值（`getString` 不走该缓存，直接读 settingsJson；`getInt` 经 `(int) getFloat` 间接走 `floatCache`，同样会读到旧缓存值）。

### getString 单参 / 双参陷阱

这是 SettingsAPI 上最容易踩的两条歧义：

1. **来源不同**：单参 `getString(key)` 读 `data/config/settings.json`；双参 `getString(category, id)` 读 `data/strings/strings.json` （API javadoc 原文 "Gets a string from a given category in strings.json"）。把两条链混为一谈，会在 "settings.json 里有这个键" 的直觉下读 strings.json 而拿到 `Missing string: [...]`。
2. **失败行为相反**：单参缺失**抛 `RuntimeException`**（调用点崩溃）；双参缺失**不抛异常**，返回 `Missing string: [<id>]` 字面文本并打一条 `ERROR` 日志。双参的 "失败" 是最阴险的一种：流程继续、UI 上直接显示占位文本，只有日志里有痕迹。
3. **双参第一参是 category，不是默认值**：新手常把它当 `getString(key, defaultValue)` 的 "带默认值版本" 使用，把默认值写进第一参 —— 例如 `getString("0.8", "loop_volume_text")` 实际是查 strings.json 里名为 `0.8` 的分类，必然返回 `Missing string: [0.8]`。RC8 的 `SettingsAPI` **没有** `getString(key, defaultValue)` 重载；要带默认值，用 `optBoolean` 或者先 `getSettingsJSON().has(key)` 再取。

strings.json 本身是 `{分类: {键: 文本}}` 两层结构（原版实例：`fleetInteractionDialog` 分类），且与 settings.json 一样跨 MOD 对象键合并，MOD 只需提交自己的分类，不要复制原版分类。

### spec 读取族与脚本入口

`SettingsAPI` 另一大族是按 ID 读已加载 spec 与 `getAllXxxSpecs()` 集合：

- `getWeaponSpec`、`getHullSpec`、`getVariant`、`getShipSystemSpec`、`getSkillSpec`、`getCommoditySpec`、`getHullModSpec`、`getFighterWingSpec`、`getFactionSpec` 等

它们消费的是加载器已解析的对象，不是本节的资源读取链，各主题页已经使用。

另外三个脚本入口：

- `getInstanceOfScript(String className)`（按完整类名创建脚本类）
- `getScriptClassLoader()`
- `getPlugin(String id)`（settings.json `plugins` 段注册的实例，**缓存且跨存档持久**——javadoc 警告字段不得持有存档对象，否则内存泄漏；`plugins` 段的注册面在插件基础章节已述）。

## 资源读取

### 单文件读取：loadText / loadJSON / loadCSV

```java
String   text   = Global.getSettings().loadText("data/config/gear_in/notes.txt");  // 原样文本
JSONObject json = Global.getSettings().loadJSON("data/config/gear_in/services.json"); // 剥 # 注释与尾逗号
JSONArray rows  = Global.getSettings().loadCSV("data/config/gear_in/service_entries.csv"); // 列名作 key 的对象数组
```

- 三个方法都在**合并资源命名空间**里按相对路径找文件（核心与全部启用 MOD 都参与收集）。当多个来源提供同名文件时，单文件读取取哪一份由资源加载器决定，教程不保证 —— 因此自己的数据文件**必须**放在自己的命名空间路径（如 `data/config/gear_in/...`），不要使用原版路径或通用路径。违反的可观察结果：读取结果随 MOD 加载组合变化、文件可能被其他 MOD 同名文件顶掉。
- `loadJSON(path, modId)` / `loadCSV(path, modId)` / `loadText(path, modId)`：把读取范围**限定在指定 MOD 的目录** （`ModManagerAPI.getModSpec(modId).getDirName()` 设置资源过滤）。 `modId` 必须与 `mod_info.json` 的 `id` 完全一致；写错 → `getModSpec` 返回 null → 调用点 NPE。
- `loadJSON(path, false)` / `loadCSV(path, false)`：只读核心，跳过全部 MOD 资源（`ResourceLoader.suppressCustomResources`）。
- 三个方法都声明 `throws IOException, JSONException`（受检异常，**必须**处理：捕获或向上声明）。
- `loadJSON` 的解析器会剥离 `#` 注释与尾逗号（`parseJSONStrippingComments`），但**不**剥离 BOM—— 文件必须 UTF-8 无 BOM（C01-S01），带 BOM 首字符进入解析器，解析失败。
- `openStream(filename)`：直接返回 `InputStream`，同样是相对路径、同样受路径必须遵守的规则约束。

### 合并读取：getMerged 系列

| 方法 | 语义 | 适用场景 |
|---|---|---|
| `getMergedJSON(path)` | 全部来源对象键合并（`mergeJSON` 规则同 settings.json）| 需要 "所有 MOD 的键都进来" 的聚合配置 |
| `getMergedJSONForMod(path, masterMod)` | 以 `masterMod` 的文件为基底，其余来源合并进基底 | 自己拥有主配置、允许他人追加键 |
| `getMergedSpreadsheetData(idColumn, path)` | CSV 行合并：跨文件同 id 行**先到先得**（后加载者跳过）；**同一文件内**重复 id 抛 `RuntimeException("Duplicate key [...] while loading [...]")` | 聚合多来源的表格数据 |
| `getMergedSpreadsheetDataForMod(idColumn, path, masterMod)` | `masterMod` 的行是基底，非 master 来源的**同 id 行替换基底行**（API javadoc 原文："rows with matching ids get replaced by non-master rows. If multiple non-master rows have the same id, one will be picked in an undefined manner"）| **"允许其他 MOD 覆盖自己的数据" 的正式做法** |

`getMergedSpreadsheetDataForMod` 是 "自己的表 + 别人按 id 覆盖行" 的标准做法，正式 MOD 的成熟用法都是把自己的 MOD id 传作 `masterMod`，

例如 `getMergedSpreadsheetDataForMod("id", "data/config/<mod-id>/xxx.csv", "<mod-id>")` 加载可被社区覆盖的数据表（特长表、地形改造条件表等）。

`masterMod` 传 MOD id；传错的可观察结果：没有基底行，覆盖语义失效（行全部追加、同 id 不替换），但本文件行仍会加载，容易漏看。

四个方法都声明 `throws IOException, JSONException`。返回的每一行 JSONObject 都会被注入 `fs_rowSource` 键 （值为来源路径），可用于排查某行来自哪个文件（`rowSourceKey`）。

### common 文件：跨存档的落盘读写

common 文件位于存档目录旁的 `common/` 目录（启动器按 `com.fs.starfarer.settings.paths.saves` 指定），文件名规则：`readTextFileFromCommon("GearIn_services_state")` 对应 `<存档目录>/common/GearIn_services_state.data`。

- `readTextFileFromCommon(filename)`：文件（含写入期的 `.data.inprogress`）不存在时**返回空串，不抛异常**。
- `writeTextFileToCommon(filename, data)`：**单次内容上限 1MB**（API javadoc 原文 "Max size 1mb"；超出抛 `RuntimeException("Max text file string length: ...")`）；另有跨文件**写入总量上限**（超出抛 `RuntimeException("Write limit of [...] characters across multiple files exceeded.")`）；写入走 "先写 `.data.inprogress` 再改名" 的原子流程。
- `fileExistsInCommon(filename)` / `deleteTextFileFromCommon(filename)`：存在性检查与删除。
- `readJSONFromCommon(filename, putInWriteCache)` / `writeJSONToCommon(filename, JSONObject, onlyIfChanged)`：JSON 形态的 common 持久化；`onlyIfChanged=true` 时内容未变则跳过、写入在后台线程执行（`SettingsWriterThread`）； `putInWriteCache` 只在配合 `onlyIfChanged=true` 的写入对象上设 `true`（javadoc 原文，否则浪费内存）。

::: info common 文件与 persistent data 的分工

common 文件是跨存档、跨启动的通用存储（如玩家配置、统计数据），与随存档保存的 `SectorAPI.getPersistentData()`（C06-S02）用途不同：后者随存档走，前者在存档之外。

:::

### 路径必须遵守的规则

以上全部路径参数受同一路径校验：

::: caution 路径必须遵守的规则

**必须**使用 `/` 分隔符，**禁止** Windows 反斜杠；**禁止** `..`、**禁止**首字符 `/`、**禁止** `:`（含盘符前缀）。

违反在调用点直接抛 `RuntimeException`，消息为 ".. and starting with a / are not allowed in filenames. Also, use / instead of \\."，启动或调用处中止。

:::

## Logger：Global.getLogger

`Global.getLogger(Class)` 返回该类的 log4j 日志器（`org.apache.log4j.Logger`），按类名建 category：

**同一类名永远是同一个日志器**，任何阶段可调用（C06-S03 规则 1），输出到 `Starsector\starsector-core\starsector.log`（C01-S01，带 `.1`/`.2`/`.3` 轮转）。

- 标准做法：类内缓存一份 `private static final Logger LOG = Global.getLogger(Xxx.class);`，方法里 `LOG.info(...)`。
- 级别约定：入口与生命周期日志用 `info`，可恢复的异常情况用 `warn`，失败路径用 `error`；`debug` 留给开发期细节。

::: tip 排查做法

日志行是 "故障注入验证" 的最直接证明（本节预期结果与故障分析都依赖日志行）。

:::

## SoundPlayerAPI：Global.getSoundPlayer

`Global.getSoundPlayer()` 任何阶段非 null（C06-S03 规则 1）。三条核心方法的约定与违反后果在 `Global` 门面页面已完整给出，本节只列结论：

| 方法 | 约定 | 违反后的可观察结果 |
|---|---|---|
| `playUISound(String id, float pitch, float volume)` | 一次性 UI 音效；素材**应为** stereo | 未注册 ID → 调用点抛 `RuntimeException: Sound set with id [...] not found` |
| `playSound(String id, float pitch, float volume, Vector2f loc, Vector2f vel)` | 位置音效；素材**必须** mono；`loc` 与 `vel` 都**必须**非 null | `loc`/`vel` 为 null → 调用点 NPE；双声道素材 → 引擎内无法正确播放 |
| `playLoop(String id, Object playingEntity, float pitch, float volume, Vector2f loc, Vector2f vel)` | 循环音效；**必须每帧调用**否则淡出；`playingEntity` **必填**（同实体同循环只播一个）| 只调一次 → 约一帧后淡出；不传/每帧传新实体 → 去重失效 |

本节示例消费声音注册章节注册的 `GearIn_weapon_fire`（作为 UI 音效），**不新增声音 ID**。完整使用方族谱见声音链章节。

## 完整最小例

在既有 MOD 布局与生命周期插件 `GearInModPlugin` 之上新增六个一文件一角色工具类与四个数据文件（不新增声音 ID、不新增注册项）：

```text
Starsector\mods\GearIndustry\
├── build.gradle
├── settings.gradle
├── mod_info.json
├── data\
│   ├── config\
│   │   ├── settings.json        # 追加两个 GearIn_ 顶层键（合并语义，只提交自己的键）
│   │   └── gear_in\
│   │       ├── services.json    # 本节新增：loadJSON 读取的配置文件
│   │       └── service_entries.csv  # 本节新增：getMergedSpreadsheetDataForMod 读取的表
│   └── strings\
│       └── strings.json         # 本节新增：strings 分类（对象键合并，只提交自己的分类）
├── src\
│   └── data\scripts\gearin\
│       ├── GearInModPlugin           # 既有（生命周期章节）；本节只在其回调里调用示例方法
│       ├── GearInServiceLogger       # 本节新增：Logger 角色
│       ├── GearInSettingsReader      # 本节新增：settings.json 单参取值
│       ├── GearInStringTable         # 本节新增：strings.json 双参取值
│       ├── GearInConfigLoader        # 本节新增：配置文件读取
│       ├── GearInCommonStateStore    # 本节新增：common 文件读写
│       └── GearInUiSoundPlayer       # 本节新增：UI 音效播放
└── jars\
```

`data\config\settings.json`（在既有内容上追加两个顶层键，其余不动）：

```json
{
  "GearIn_loop_volume": 0.8,
  "GearIn_service_name": "齿轮工业服务"
}
```

`data\strings\strings.json`（完整文件，只提交自己的分类）：

```json
{
  "GearIn_services": {
    "loop_volume_text": "Loop volume text"
  }
}
```

`data\config\gear_in\services.json`（本节新增，`loadJSON` 读取）：

```json
{
  "displayName": "GearIn services config",
  "maxEntries": 8
}
```

`data\config\gear_in\service_entries.csv`（本节新增，`getMergedSpreadsheetDataForMod` 按 `id` 列合并）：

```csv
id,name
one,First entry
two,Second entry
```

### 六个一文件一角色工具类

原混装多个职责的静态工具类按角色拆成六个小类：一文件一角色、类名按角色命名（`GearIn<Role>`），方法集、数据键、文件路径与声音 ID 与拆分前完全一致；每个类都是私有构造的静态工具，可从任何已注册的脚本/插件调用。各自的职责与消费位置如下。

#### GearInServiceLogger（Logger 角色）

**职责**：进程级缓存一份日志器，以 info 级记录 Global 衍生服务可用性（`logServiceState`）。

**消费位置**：在 `onApplicationLoad` 回调（生命周期插件所有）内调用 `logServiceState()`，其输出供启动日志核对；无注册项、无数据键。

```java
package data.scripts.gearin;

import org.apache.log4j.Logger;

import com.fs.starfarer.api.Global;

/**
 * Logger 用法：进程级缓存一份日志器，输出到 starsector.log。
 *
 * 静态工具，可从任何已注册的脚本/插件调用（C06-S03 的四阶段可用性只约束
 * 生涯/战斗对象，logger 全程非 null）。
 */
public class GearInServiceLogger {

    /** 日志器：进程级缓存一份，输出到 starsector.log。 */
    private static final Logger LOG = Global.getLogger(GearInServiceLogger.class);

    private GearInServiceLogger() {
        // 工具类：不实例化
    }

    /** info 级别：记录 Global 衍生服务可用性，供启动日志核对。 */
    public static void logServiceState() {
        LOG.info("GearIn services: settings=" + (Global.getSettings() != null)
                + " soundPlayer=" + (Global.getSoundPlayer() != null));
    }
}
```

#### GearInSettingsReader（settings.json 单参取值）

**职责**：读 settings.json 顶层键（单参 `getFloat`/`getString`，键缺失抛 `RuntimeException`），持有 `KEY_LOOP_VOLUME`/`KEY_SERVICE_NAME`。

**消费位置**：在 `onGameLoad` 回调（生命周期插件所有）内经 `readServiceName()` 消费（见调用点片段）；settings.json 追加的两个顶层键所有权在本节。

```java
package data.scripts.gearin;

import com.fs.starfarer.api.Global;

/**
 * settings.json 顶层取值（单参 getFloat / getString）。
 *
 * 本节新增的 settings.json 键（对象键合并，MOD 只提交自己的键）：
 * - GearIn_loop_volume（float）、GearIn_service_name（string）。
 * 单参取值缺键抛 RuntimeException（包装 JSONException），不返回 null。
 */
public class GearInSettingsReader {

    public static final String KEY_LOOP_VOLUME = "GearIn_loop_volume";
    public static final String KEY_SERVICE_NAME = "GearIn_service_name";

    private GearInSettingsReader() {
        // 工具类：不实例化
    }

    /** 单参 getFloat：读 settings.json 顶层键；键缺失抛 RuntimeException。 */
    public static float readLoopVolume() {
        return Global.getSettings().getFloat(KEY_LOOP_VOLUME);
    }

    /** 单参 getString：读 settings.json 顶层键；键缺失抛 RuntimeException，不返回 null。 */
    public static String readServiceName() {
        return Global.getSettings().getString(KEY_SERVICE_NAME);
    }
}
```

#### GearInStringTable（strings.json 双参取值）

**职责**：读 strings.json 分类文本（双参 `getString`，键缺失返回占位文本并打 ERROR 日志），持有 `STRINGS_CATEGORY`/`STRINGS_KEY`。

**消费位置**：随 UI 文案调用 `getLoopVolumeText()`；strings.json 分类 `GearIn_services` 所有权在本节。

```java
package data.scripts.gearin;

import com.fs.starfarer.api.Global;

/**
 * strings.json 分类文本（双参 getString）。
 *
 * 本节新增的 strings.json 键：分类 GearIn_services -> loop_volume_text。
 * 双参取值缺键不抛异常：返回 "Missing string: [...]" 占位文本并打 ERROR 日志。
 */
public class GearInStringTable {

    public static final String STRINGS_CATEGORY = "GearIn_services";
    public static final String STRINGS_KEY = "loop_volume_text";

    private GearInStringTable() {
        // 工具类：不实例化
    }

    /** 双参 getString：读 strings.json 的 分类->键；缺失不抛异常，返回占位文本并打 ERROR 日志。 */
    public static String getLoopVolumeText() {
        return Global.getSettings().getString(STRINGS_CATEGORY, STRINGS_KEY);
    }
}
```

#### GearInConfigLoader（配置文件读取）

**职责**：配置文件读取（`loadJSON` 单文件 + `getMergedSpreadsheetDataForMod` 合并 CSV），持有 `CONFIG_PATH`/`ENTRIES_PATH`。

**消费位置**：在 `onGameLoad` 回调（生命周期插件所有）内经 `loadServiceEntries()` 消费（见调用点片段）； `services.json` 与 `service_entries.csv` 所有权在本节。

```java
package data.scripts.gearin;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.SettingsAPI;

/**
 * 配置文件读取：单文件 loadJSON 与合并 CSV（getMergedSpreadsheetDataForMod）。
 *
 * 路径一律用 "/"：禁止 ".."、反斜杠、":" 及首字符 "/"（违反在调用点抛 RuntimeException）。
 */
public class GearInConfigLoader {

    public static final String CONFIG_PATH = "data/config/gear_in/services.json";
    public static final String ENTRIES_PATH = "data/config/gear_in/service_entries.csv";

    private GearInConfigLoader() {
        // 工具类：不实例化
    }

    /** 单文件读取：loadJSON 解析 MOD 自己的配置文件（# 注释与尾逗号会被剥离）。 */
    public static JSONObject loadServiceConfig() throws IOException, JSONException {
        return Global.getSettings().loadJSON(CONFIG_PATH);
    }

    /** 合并读取：以本 MOD 的 CSV 为基底，其他 MOD 可按 id 列替换行（主行被覆盖）。 */
    public static List<JSONObject> loadServiceEntries() throws IOException, JSONException {
        SettingsAPI settings = Global.getSettings();
        JSONArray rows = settings.getMergedSpreadsheetDataForMod("id", ENTRIES_PATH, "gear_in");
        List<JSONObject> result = new ArrayList<>();
        for (int i = 0; i < rows.length(); i++) {
            result.add(rows.getJSONObject(i));
        }
        return result;
    }
}
```

#### GearInCommonStateStore（common 文件读写）

**职责**：common 文件读写（`readTextFileFromCommon`/`writeTextFileToCommon`），持有 `COMMON_FILE`。

**消费位置**：在 `onGameLoad` 回调（生命周期插件所有）内经 `readCommonState()`/`writeCommonState()` 完成往返（见调用点片段）；common 文件名 `GearIn_services_state` 所有权在本节。

```java
package data.scripts.gearin;

import java.io.IOException;

import com.fs.starfarer.api.Global;

/**
 * common 文件读写：<saves>/common/GearIn_services_state.data。
 *
 * 缺失返回空串（含 .data.inprogress 回退）；单次写入上限 1MB（超出抛
 * RuntimeException），且存在跨文件写入总量上限。
 */
public class GearInCommonStateStore {

    public static final String COMMON_FILE = "GearIn_services_state";

    private GearInCommonStateStore() {
        // 工具类：不实例化
    }

    /** common 文件读取：文件缺失返回空串，不抛异常。 */
    public static String readCommonState() throws IOException {
        return Global.getSettings().readTextFileFromCommon(COMMON_FILE);
    }

    /** common 文件写入：单次上限 1MB（超出抛 RuntimeException）。 */
    public static void writeCommonState(String state) throws IOException {
        Global.getSettings().writeTextFileToCommon(COMMON_FILE, state);
    }
}
```

#### GearInUiSoundPlayer（UI 音效播放）

**职责**：播放 UI 音效（`playUISound`），持有 `SOUND_ID_UI`。

**消费位置**：任意 UI 事件接入 `playFireUISound(0.8f)`（见调用点片段）；声音 ID `GearIn_weapon_fire` 由声音注册章节所有，本节只消费、不新增。

```java
package data.scripts.gearin;

import com.fs.starfarer.api.Global;

/**
 * SoundPlayerAPI 用法：播放 UI 音效。
 *
 * 声音 ID GearIn_weapon_fire 由 C05-S01/C05-S02 所有，本节只消费，不新增声音 ID。
 */
public class GearInUiSoundPlayer {

    public static final String SOUND_ID_UI = "GearIn_weapon_fire";

    private GearInUiSoundPlayer() {
        // 工具类：不实例化
    }

    /** UI 音效（C05-S01 规格：素材建议 stereo）。 */
    public static void playFireUISound(float volume) {
        Global.getSoundPlayer().playUISound(SOUND_ID_UI, 1f, volume);
    }
}
```

### 对应关系

- `GearInServiceLogger.logServiceState`：`Global.getLogger(GearInServiceLogger.class)` 类内缓存一份日志器（"Logger" 节）。
- `GearInSettingsReader.readLoopVolume` / `readServiceName`：单参取值，settings.json 键缺失即抛 `RuntimeException`—— 故障分析第 1 条。
- `GearInStringTable.getLoopVolumeText`：双参取值，strings.json 键缺失返回占位文本 —— 故障分析第 2、3 条。
- `GearInConfigLoader.loadServiceConfig`：`loadJSON` 单文件读取（`#` 注释与尾逗号可写，BOM 不可）。
- `GearInConfigLoader.loadServiceEntries`：`getMergedSpreadsheetDataForMod("id", ...)` 合并读取，`masterMod` 用本 MOD id `gear_in`；数据文件必须存在，否则抛 `IOException`/`JSONException`。
- `GearInCommonStateStore.readCommonState` / `writeCommonState`：common 文件往返；缺失返回空串。
- `GearInUiSoundPlayer.playFireUISound`：消费 `GearIn_weapon_fire`（C05-S01/C05-S02 注册，未注册会在调用点抛 `Sound set with id [...] not found`）。

### 调用点（工程片段）

`GearInModPlugin` 归生命周期章节所有，本节只演示调用，不改动那些文件：

```java
    // GearInModPlugin（生命周期章节所有）onApplicationLoad() 内新增一行
    GearInServiceLogger.logServiceState(); // 预期：GearIn services: settings=true soundPlayer=true
```

```java
    // GearInModPlugin（生命周期章节所有）onGameLoad(boolean) 内新增（受检异常必须处理）
    try {
        List<JSONObject> entries = GearInConfigLoader.loadServiceEntries();
        String state = GearInCommonStateStore.readCommonState();
        int visits = state.isEmpty() ? 1 : Integer.parseInt(state.trim()) + 1;
        GearInCommonStateStore.writeCommonState(String.valueOf(visits));
        Global.getLogger(GearInModPlugin.class).info(
            "GearIn entries=" + entries.size() + " visits=" + visits
                + " name=" + GearInSettingsReader.readServiceName());
    } catch (IOException | JSONException e) {
        Global.getLogger(GearInModPlugin.class).error("GearIn services init failed", e);
    }
```

```java
    // 任意 UI 事件（工程片段）：复用已注册的声音 ID 播放一次 UI 音
    GearInUiSoundPlayer.playFireUISound(0.8f);
```

### 构建命令

在 `Starsector\mods\GearIndustry\` 执行，沿用既有构建配置：

```text
gradle jar
```

产物 `jars\GearIndustry.jar` 应包含六个新类（可用 `jar tf` 核对）。本节六段源码与 fixture 逐字一致，fixture 编译验收命令见 "预期结果"。

### 验证方案（基于本例）

前置数据：既有 MOD 布局（C06-S01）、生命周期插件 `GearInModPlugin`（C06-S02，含本节调用片段）、本节六个工具类与四个数据文件、声音注册（C05-S01/C05-S02）、测试战役 `GearIn_test`（C03-S01）。

- 操作 1（fixture 编译）：在 Git Bash 执行 `gradle -p tutorial-fixtures compileC06s04Java --no-daemon`，确认 `BUILD SUCCESSFUL`。
- 操作 2（游戏构建）：在 `Starsector\mods\GearIndustry\` 执行 `gradle jar`，确认 `BUILD SUCCESSFUL` 且 `jar tf jars\GearIndustry.jar` 含六个新类；确认 `data\config\gear_in\services.json`、`service_entries.csv`、`data\strings\strings.json` 均已随 MOD 打包。
- 操作 3（启动）：勾选 "齿轮工业" 启动到主菜单后退出；`starsector.log` 核对 `GearIn services: settings=true soundPlayer=true`，无 `gear_in` 引起的 `ERROR`。
- 操作 4（新档/读档）：新建生涯，日志出现 `GearIn entries=2 visits=1 name=齿轮工业服务`；退出并读档，`visits=2`（common 文件落盘往返成功）；重复读档 visits 递增。
- 操作 5（故障对照 A—— 双参缺失）：把 `GearInStringTable.STRINGS_KEY` 临时改成不存在的键，调用 `GearInStringTable.getLoopVolumeText()`：无异常、返回 `Missing string: [...]`、日志一条 `ERROR`；改回后确认恢复。
- 操作 6（故障对照 B—— 单参缺失）：把 `GearInSettingsReader.KEY_SERVICE_NAME` 临时改错并调用 `GearInSettingsReader.readServiceName()`：调用点抛 `RuntimeException`；改回后确认恢复。
- 操作 7（故障对照 C—— 路径违规）：把 `GearInConfigLoader.CONFIG_PATH` 临时改成 `data\config\gear_in\services.json`（反斜杠）：读取调用点抛 `RuntimeException`（路径校验）；改回后确认恢复。
- 操作 8（UI 音效）：接入 `GearInUiSoundPlayer.playFireUISound` 的 UI 事件触发时听到 `GearIn_weapon_fire`；把 `GearInUiSoundPlayer.SOUND_ID_UI` 改错后触发 → 调用点抛 `Sound set with id [...] not found`，改回后确认恢复。

通过信号：操作 1–4 全部符合；操作 5–8 的注入与恢复都符合预期。

清理：取消勾选测试 MOD；确认没有对共享文件（`tutorial-fixtures\build.gradle` 等）做过修改。

## 预期结果

1. **fixture 编译（验收 J）**：在 Git Bash 执行
```text gradle -p tutorial-fixtures compileC06s04Java --no-daemon ``` 以 `BUILD SUCCESSFUL` 结束（退出码 0）。
2. **游戏构建**：`gradle jar` 以 `BUILD SUCCESSFUL` 结束；
`jar tf jars\GearIndustry.jar` 能看到 `data/scripts/gearin/GearInServiceLogger.class` 等六个新类。
3. **启动（资源加载期）**：进入主菜单前 `starsector.log` 出现一行
```text GearIn services: settings=true soundPlayer=true ``` （`onApplicationLoad` 里调用；`settings`/`soundPlayer` 在此阶段已非 null，`C06-S03` 规则 1）；启动正常完成，没有 `gear_in` 引起的 `ERROR`。
4. **新档 / 读档（生涯回调）**：`onGameLoad` 里资源读取与 common 往返执行成功，日志出现
```text GearIn entries=2 visits=1 name=齿轮工业服务 ``` （首次进档 `visits=1`；第二次读档 `visits=2`—— 证明 common 文件落盘并被再次读回）。`data\config\gear_in\services.json` 存在但无人消费时报 `ERROR`。
5. **双参缺失的对照**：临时把 `GearInStringTable.STRINGS_KEY` 改成不存在的键并调用 `GearInStringTable.getLoopVolumeText()`：
**不抛异常**，方法返回 `Missing string: [不存在的键]`，日志出现一条 `ERROR Missing string: [不存在的键]`；改回后消失。这是故障分析第 2 条的现场证明。
6. **单参缺失的对照**：临时把 `GearInSettingsReader.KEY_SERVICE_NAME` 改错并调用 `GearInSettingsReader.readServiceName()`：
调用点抛 `RuntimeException`（堆栈指向该行），流程中止或由调用方 catch；改回后恢复。与第 5 条对比即"单参抛异常、双参占位文本"。
7. **UI 音效**：接入 `GearInUiSoundPlayer.playFireUISound` 的 UI 事件触发时听到 `GearIn_weapon_fire`
（与 C05-S03 已接的武器开火音同一 ID，互不影响）。

静态自查：六个工具类的源码与 fixture 逐字一致（包括 import 顺序）；settings 键 / strings 键 / 文件路径与页面片段逐字一致（`GearIn_loop_volume`、`GearIn_service_name`、`GearIn_services`、`loop_volume_text`、 `data/config/gear_in/services.json`、`data/config/gear_in/service_entries.csv`、`GearIn_services_state`）；没有把工作单元编号写进任何运行时名称。

## 实例驱动故障分析

1. **调用点抛 `RuntimeException`，堆栈指向 `getString`/`getFloat` 行，消息是 JSONException 的原因。**
触发：单参取值键在 settings.json 中缺失或类型不符（如对 `GearIn_loop_volume` 这种 float 键调 `getString`）。可观察：异常在取值调用点直接抛出，消息是 JSONException 的原因（单参取值不返回 null）。修正：核对 settings.json 键名与类型逐字一致；需要可选键时用 `optBoolean` 或先 `getSettingsJSON().has(key)` 再取。
2. **UI 上出现字面 `Missing string: [xxx]`，流程一切正常、没有任何异常。**
触发：双参 `getString(category, id)` 的分类或键缺失（包括把 settings.json 的键名当 strings 键用）。可观察：不抛异常，返回占位文本，只有 `starsector.log` 里一条 `ERROR Missing string: [...]`。修正：核对 strings.json 的分类与键；正文文案一律走 strings.json，settings.json 只放数值/开关。
3. **把"默认值"写进双参第一参。** 触发：误以为 `getString(key, defaultValue)` 存在。
可观察：把默认值当分类名查 strings.json，返回 `Missing string: [<默认值>]`。修正：RC8 没有该重载；用 `optBoolean` 或 `has()` 判断。
4. **读取方法抛 `IOException`。** 触发：路径拼错、文件不存在、大小写不一致（含扩展名）。
可观察：`loadText`/`loadJSON`/`loadCSV`/common 读取在调用点抛 `IOException`（common 文件是唯一例外：缺失返回空串）。修正：按 MOD 根目录逐段核对路径与文件名大小写。
5. **读取方法抛 `RuntimeException: .. and starting with a / are not allowed in filenames...`。**
触发：路径含 `..`、`\`、`:` 或首字符 `/`（如把 Windows 路径 `data\config\...` 直接传入）。可观察：路径校验在调用点抛异常。修正：全部路径用 `/`，去掉盘符与 `..`。
6. **`getMergedSpreadsheetData` 抛 `RuntimeException: Duplicate key [...] while loading [...]`。**
触发：同一 CSV 文件内 id 列重复（`service_entries.csv` 出现两个 `one`）。可观察：读取调用点抛异常。修正：去重；跨文件重复则不会报错而是先到先得。
7. **`getMergedSpreadsheetDataForMod` 的覆盖不生效。** 触发：`masterMod` 传的不是 MOD id（或该 MOD 没有提供同路径文件）。
可观察：无基底行，其他 MOD 的行全部追加、同 id 不替换（覆盖语义静默失效，但行仍会加载）。修正：`masterMod` 传拥有主文件的 MOD 自己的 id。
8. **`writeTextFileToCommon` 抛 `RuntimeException: Max text file string length: ...` 或
`Write limit of [...] characters across multiple files exceeded.`** 触发：单次写入超 1MB，或多次写入累计超总量上限。修正：common 文件只放小数据（统计、配置）；大内容用 MOD 自带数据文件。
9. **播放调用点抛 `RuntimeException: Sound set with id [...] not found`。** 触发：引用了未注册或拼错的 sound ID（含大小写不一致）。
可观察：异常在调用点抛出（C05-S03 有完整定位流程）。修正：核对声音注册章节注册的 ID 逐字一致。
10. **`setFloat`/`setBoolean` 之后重启，值全部丢失。** 触发：以为它们会持久化玩家配置。
可观察：只改了内存中的 settingsJson 与缓存，不写任何文件，进程退出即丢。修正：需要跨启动持久化用 common 文件（`writeTextFileToCommon`/`writeJSONToCommon`）；随存档走的数据用 persistent data（随存档保存）。

## 总结

本节把 `Global` 交出的四个服务对象接到实际工程上：`SettingsAPI` 是取值与资源读取的总入口，`getLogger` 与 `getSoundPlayer` 给出用法约定，并以六个一文件一角色工具类演示全程。记忆要点：

- **取值两条链**：单参 `getString(key)`/`getFloat`/`getBoolean`/`getInt`/`getColor` 读 settings.json，缺失抛 `RuntimeException`；
双参 `getString(category, id)` 读 strings.json，缺失**不抛异常**、返回 `Missing string: [...]` 占位文本并打 `ERROR` 日志。双参第一参是分类，**不是默认值**。
- **合并模型**：settings.json 与 strings.json 都是跨 MOD 对象键合并，MOD 只提交自己的键；启动器关键键（`devMode`、`maxMemoryMB` 等）被排除。
- **资源读取三族**：单文件（`loadText`/`loadJSON`/`loadCSV`，含 `modId` 限定与 `withMods=false`）、
合并（`getMergedJSON`/`getMergedJSONForMod`/`getMergedSpreadsheetData`/`getMergedSpreadsheetDataForMod`—— 后者是"允许他人覆盖自己数据"的正式做法）、common（`readTextFileFromCommon` 缺失返回空串；写入单次 1MB、总量有限）。
- **路径必须遵守的规则**：`/` 分隔符，禁止 `..`、`\`、`:`、首字符 `/`，违反即 `RuntimeException`。
- **内存修改**：`getSettingsJSON()` 直接改后必须 `resetCached()`；`setFloat`/`setBoolean` 不落盘。
- 所有读取方法都抛受检的 `IOException`/`JSONException`，调用方必须处理；数据文件必须 UTF-8 无 BOM。

四个服务对象的可用阶段与声音约定已由前置章节给出；本节把它们接到取值、资源读取与 common 持久化上。逻辑上的后续是两条主线：其一，settings 作为规则系统的数据源 —— 规则段从这里取值，规则的消费语义属于规则系统主题；其二，两条持久化路径（common 落盘与随存档的 persistent data）在具体功能中的取舍。

## 建议参考项

- `com.fs.starfarer.api.SettingsAPI`：取值（`getString(String)`/`getString(String,String)`/`getFloat`/`getBoolean`/
`optBoolean`/`getInt`/`getColor`/ `getJSONObject`/`getJSONArray`）、内存修改（`getSettingsJSON`/`resetCached`/`setFloat`/`setBoolean`）、资源读取（`loadText`/`loadJSON`/`loadCSV` 及 `(path, modId)` 与 `(path, withMods)` 重载、`openStream`、 `getMergedJSON`/`getMergedJSONForMod`/`getMergedSpreadsheetData`/`getMergedSpreadsheetDataForMod`、 `readTextFileFromCommon`/`writeTextFileToCommon`/`fileExistsInCommon`/ `deleteTextFileFromCommon`/`readJSONFromCommon`/`writeJSONToCommon`）、脚本与插件（`getInstanceOfScript`/`getScriptClassLoader`/`getPlugin`）—— 本节取值与资源读取主体。
- `com.fs.starfarer.api.Global`：`getSettings()`/`getLogger(Class)`/`getSoundPlayer()`—— 四个服务对象的门面入口。
- `com.fs.starfarer.api.SoundPlayerAPI`：`playUISound`/`playSound`/`playLoop`—— 三条播放方法
（素材 stereo "should"、mono "must"、循环每帧续期、`playingEntity` 必填）。
- `org.json.JSONObject` / `org.json.JSONArray` / `org.json.JSONException`：`loadJSON`/合并读取的返回类型与受检异常（org.json 运行时提供）。
- `org.apache.log4j.Logger`：`Global.getLogger(Class)` 的返回类型（log4j 运行时提供）。
- `data/config/settings.json`：对象键合并的取值数据源；本节追加 `GearIn_loop_volume`、`GearIn_service_name` 两个顶层键。
- `data/strings/strings.json`：`{分类: {键: 文本}}` 两层结构；本节追加分类 `GearIn_services`（键 `loop_volume_text`）。
- `data/config/gear_in/services.json`：本节 `loadJSON` 单文件读取示例。
- `data/config/gear_in/service_entries.csv`：本节 `getMergedSpreadsheetDataForMod` 合并读取示例（`id` 列）。
- `GearIn_services_state`：本节 common 文件名（对应 `<存档目录>/common/GearIn_services_state.data`）。
- `tutorial-fixtures/build.gradle`：`compileC06s04Java` 任务（source set `s06p04` → `units/C06-S04/src/main/java`）。
