# 制作 MOD 入口类

本节制作并登记 MOD 入口类，让已经加载的 JAR 真正收到游戏生命周期回调。示例会记录各回调的顺序，并用持久数据判断新存档初始化是否已经执行。

*本节预期阅读时间：~15 分钟。*

*本节预期操作时间：~25 分钟。*

## 学习目标

完成后，您将能够：

- 说出 `mod_info.json` 的 `modPlugin` 字段如何被解析 (可选字段)、实例何时创建 (启动资源加载期、进入主菜单前)、实例如何缓存，以及 "未写 modPlugin" 为什么会导致 MOD 一切正常但没有任何回调；
- 按顺序说出全部生命周期回调的调用时机：应用启动 (`onApplicationLoad`)、新建生涯 (`onNewGame` → `onNewGameAfterProcGen` → `onNewGameAfterEconomyLoad` → `onEnabled` → `onNewGameAfterTimePass` → `onGameLoad(true)`)、读档 (仅中途启用时 `onEnabled` → `onGameLoad(false)`)、保存 (`beforeGameSave` → `afterGameSave` / `onGameSaveFailed`)、战斗期 AI picker 与 `onDevModeF8Reload`；
- 解释 `onEnabled(boolean wasEnabledBefore)` 的触发条件 (MOD 不在存档的启用列表里时) 与参数含义，以及它为什么运行在一个独立新实例上；
- 用 `SectorAPI.getPersistentData()` 写 "首次进入存档" 的一次性初始化标记 (`CLASS_MARK` 模式)，并解释没有这个标记时为什么每次读档都会重复执行；
- 从 `starsector.log` 读出回调顺序，按 "启动失败 / 回调不执行 / 回调重复执行" 三类症状定位到具体原因。

## 前置知识

本节需要您先具备以下基础：

- MOD 目录结构、`mod_info.json` 必填/可选字段、启动器与 `starsector.log`(C01-S01)；
- 注册、回调与类名一致概念 (C02-S01)；
- 新档生成入口 `onNewGame()`：C03-S03 的生成入口类为 `GearInSystemGenPlugin`(`data.scripts.gearin.campaign` 包，只实现 `onNewGame()`)；
- 编译 JAR 链路 (`src` → `jars/GearIndustry.jar`)、`mod_info.json` 的 `jars` 声明、jar 类优先于 `.java` 即时编译 (C06-S01)。

`Global` 与 `settings` 的精确讲解在其他页面，本节只使用它们的结论。

## 文件与 API 链路

本节的读者任务是一条链：声明入口类、让加载器实例化它、按顺序收到回调、在首次进入存档时完成一次性初始化。从声明到回调共六段：

| 环节 | 文件或 API | 使用方 / 行为 |
|---|---|---|
| 声明 | `mod_info.json` 的 `modPlugin` | 可选字段：启动时读取，缺失时为 `null` |
| 登记 | 资源加载期 | 每个已启用 MOD 的 `modPlugin` 类名注册进脚本类加载器；`null` 类名会被跳过 |
| 实例化 | `ModManagerAPI.getEnabledModPlugins()` | 首次调用时按 `coreLifecyclePlugin`(原版 `CoreLifecyclePluginImpl`，恒为列表第一个)→ 各 MOD 顺序，经脚本类加载器按类名创建实例 (`Class.forName` + `newInstance`，失败抛 `Problem loading class [...]`)，创建结果**缓存复用**；`modPlugin` 为 `null` 的 MOD 直接跳过 |
| 回调派发 | 游戏管理器 / MOD 启用记录 / 战斗状态 | 新档、读档、保存、F8 重载各节点对缓存列表循环调用 |
| 首次进入门 | `SectorAPI.getPersistentData()` | 返回随存档保存的 `Map<String, Object>`；`CLASS_MARK` 模式用它做一次性初始化门 |
| 实例化 (特例)| 启用状态通知 | `onEnabled` 触发时**每次都新建一个实例**再调用，与上面缓存的实例不是同一个对象 |

::: info `modPlugin` 缺失不是错误
MOD 正常启用，但 `getEnabledModPlugins` 对 `null` 类名直接跳过 ——**没有任何生命周期回调，且没有任何报错**。它是整个教程里最安静的失败方式：一切正常，只是永远不被叫醒；这也是 "回调不执行" 的首选排查方向。
:::

读链路时的另一个关键结论：除 `onEnabled` 外，全部回调 (含 picker) 都在**同一个缓存实例**上调用，且该实例跨存档复用。跨存档的状态**必须**放 persistent data，不能放实例字段。

链路已经清楚，接下来按游戏进程的节点逐一确认回调时机与顺序，这是读日志时逐行对照的标尺。

## 生命周期总览

回调名以 `com.fs.starfarer.api.ModPlugin` 为准 (共 19 个方法)。`BaseModPlugin` 为全部方法提供空实现，只覆写需要的方法。

### 应用启动 (进入主菜单前)

1. 资源加载期：创建脚本类加载器并登记各 MOD 的 `modPlugin` 类名。
2. 全部资源与脚本加载完成后：对缓存列表逐个调用 **`onApplicationLoad()`**。这是整个游戏进程的第一个回调。
   - 适合：版本检查、读取 `settings`、加载静态数据、初始化外部库 (shader 初始化就发生在这里)。
   - 抛出的异常沿资源加载链向上传播，**游戏启动中止**(错误弹窗 + `starsector.log` 记 `ERROR`)。成熟实例正是利用这一点做版本门：`onApplicationLoad` 里检查 `Global.getSettings().getGameVersion()` 不是 `0.98a` 前缀就抛 `IllegalArgumentException`。

::: caution 禁止在 `onApplicationLoad` 访问 `Global.getSector()`
此刻 sector 尚未建立，`Global.getSector()` 返回 `null`，任何解引用都是 NPE。
:::

### 新建生涯 (游戏管理器的 `newGame`)

按调用顺序：

1. **`onNewGame()`**：在原版 SectorGen 与 `generators.csv` 插件之后、程序化生成 (`SectorProcGen`) 之前调用。新档生成星系/实体放这里 (新档生成页的生成入口)。此时经济尚未加载。
2. **`onNewGameAfterProcGen()`**：程序化生成完成后。
3. **`onNewGameAfterEconomyLoad()`**：`economy.json` 加载并初步推进之后。此时 `getMarket()` 已返回正确的市场 (API javadoc 原文)，适合访问市场、经济实体与人物。
4. **`onEnabled(false)`**(经启用状态通知)：用户舰队创建、初始脚本运行之后。新档的存档启用列表为空，因此必然触发，`wasEnabledBefore=false`。
5. **`onNewGameAfterTimePass()`**：初始约 2 个月时间推进之后。
6. **`onGameLoad(true)`**：全部 `onNewGame*` 链结束后调用，与读档共用同一个入口。
7. 新档流程末尾自动保存：**`beforeGameSave()` → `afterGameSave()`**(调用 `saveGame`)。

### 读档 (游戏管理器的 `loadGame`)

1. 反序列化生涯引擎，其中含存档的 MOD 启用记录 (当前启用列表与历史上启用过的列表)。
2. 读档时逐 MOD 核对启用状态：**只有该 MOD 不在存档的已启用列表里** (即 "中途把 MOD 加进已有存档") 时才调用 **`onEnabled(wasEnabledBefore)`**；`wasEnabledBefore=true` 表示这个存档历史上启用过该 MOD(卸了又装的存档)，否则为 `false`。已在列表里的 MOD 不触发。
3. **`onGameLoad(false)`**：唯一参数表示这是读档 (`false`) 还是新档 (`true`)。
4. 经济恢复与星区推进 (读档后恢复经济并继续推进时间)。

### 保存 (游戏管理器的 `saveGame`；手动与自动同路径)

1. **`beforeGameSave()`**：在写任何存档文件之前。适合清理 "不应当随存档保存" 的临时状态。
2. 序列化 `campaign.xml`/`campaign.zip` 与 `descriptor.xml`。
3. 成功：**`afterGameSave()`**；抛出任何异常**：`onGameSaveFailed()`**，并返回存档失败错误 (`starsector.log` 记 `Error saving game`)。

### 战斗期 (每场战斗)

- AI picker 在战斗对象创建时调用 (调用点如弹丸生成、武器组与舰船创建)：**`pickShipAI`**(`member` 可为 `null`，`ship` 不可为 `null`)、**`pickWeaponAutofireAI`**、**`pickDroneAI`**、**`pickMissileAI`**。
- 返回 `null` = 放弃本次选择权，游戏使用原版 AI(武器组用点防御自动开火 AI，舰船用基础舰船 AI 与战斗机 AI，导弹按类型使用各自的原版 AI，由创建处的回退分支完成)。
- 返回 `new PluginPick<>(实例, PickPriority.MOD_SET)` = 本 MOD 明确指定该 AI；多 MOD 竞争时按 `PickPriority` 的枚举序取最高优先级 (`getPriorityPlugin`)。MOD 的 picker 一律用 `MOD_SET` 及以上 (`MOD_SPECIFIC`/`HIGHEST` 用于更具体的场景)，原版内部用 `CORE_GENERAL`/`CORE_SET`/`CORE_SPECIFIC`。
- **`onDevModeF8Reload()`**：devMode 下在战斗内按 F8 重载数据后调用，适合重建静态缓存 (成熟实例用它重建静态数据)。

### Codex 数据生成 (RC8 新增)

`onAboutToStartGeneratingCodex()`、`onAboutToLinkCodexEntries()`、`onCodexDataGenerated()` 在 Codex 数据生成阶段按 "开始生成 → 链接条目 → 生成完成" 调用 (API javadoc；Codex 生成器的调用点不在本教程核对范围内)。

成熟实例在 `onCodexDataGenerated()` 里为 Codex 条目写关联。本节示例不使用它们。

回调时机已经明确，但要真正收到这些回调，`mod_info.json` 的声明必须正确。

## 注册：mod_info.json 的 modPlugin

声明只有几行，违规后果都发生在启动期，先看两条必须满足的规则：

- `modPlugin` 的值**必须**是脚本类加载器能 `Class.forName` 实例化的完整类名。类不存在、包名/类名拼错 → 首次 `getEnabledModPlugins()`(启动资源加载期) 抛 `Problem loading class [data.scripts.gearin.GearInModPlugin]`**，启动失败**。
- `modPlugin` 指向的类**必须**继承 `BaseModPlugin`(或实现 `ModPlugin` 全部 19 个方法)。类不是 `ModPlugin` 实现 → `getEnabledModPlugins` 里的强制转换抛 `ClassCastException`**，启动失败**。直接实现接口而非继承基类 → 编译期必须写满 19 个方法，基类用空实现作默认值 (`com.fs.starfarer.api.BaseModPlugin` javadoc 明确建议继承：将来 API 新增方法时基类会补空实现，直接实现接口的 MOD 会被破坏编译)。

::: caution 回调内不得抛出未处理异常
应用启动阶段 → **启动中止**；新档/读档阶段 → **对应流程中止并报错**；保存阶段 → **存档失败并回调 `onGameSaveFailed()`**。版本检查是唯一有意的例外 (见下)。
:::

建议：

- 在 `onApplicationLoad()` 里检查 `Global.getSettings().getGameVersion()` 是否以 `0.98a` 开头，不符就抛异常拒绝启动 (成熟实例都这么做)。不做检查的可观察结果：用户可能手动修改 `mod_info.json`，而在更高版本游戏上行为未定义，问题会在很晚的阶段才暴露。

`mod_info.json` 追加一行 (其余字段沿用既有配置)：

```json
{
  "id": "gear_in",
  "name": "齿轮工业",
  "author": "YourName",
  "version": { "major": 0, "minor": 1, "patch": 0 },
  "description": "Starsector MOD 教程的练习项目。",
  "gameVersion": "0.98a-RC8",
  "jars": ["jars/GearIndustry.jar"],
  "modPlugin": "data.scripts.gearin.GearInModPlugin"
}
```

`modPlugin` 与 `jars` 相互独立：`modPlugin` 只声明入口类名，类的字节码放在哪个 jar(或直接用 `.java` 即时编译) 由 `jars`/源码目录决定。

本节沿用此前建立的 jar 编译路线，`GearInModPlugin` 编进同一个 `jars/GearIndustry.jar`。

## 存档入口：首次进入存档的初始化门

`onGameLoad` 无论新档 (`true`) 还是读档 (`false`) 都会被调用，因此 "每个存档只做一次" 的初始化 (生成补丁、势力注册、脚本注册)**必须**用随存档保存的 persistent data 判断：

```java
if (!sector.getPersistentData().containsKey(CLASS_MARK)) {
    sector.getPersistentData().put(CLASS_MARK, true);
    // 一次性初始化：仅在该存档第一次进入时执行
}
```

::: caution "每个存档只做一次" 的初始化必须用 persistent data 判断
违反的可观察结果：**每次读档都重复执行初始化** —— 实体/星系重复生成、`addScript`/`registerPlugin` 重复挂载、`SharedData` 赏金参与势力重复注册。日志里表现为每次读档都出现 "首次进入" 日志。
:::

- 标记键的取值：常见做法是用类名字符串，与存档共存亡；旧存档缺键 = 视为首次进入，正好覆盖 "中途启用 MOD" 的场景。
- `onEnabled(wasEnabledBefore)` 与 persistent data 标记的分工：`onEnabled` 只回答 "这个 MOD 本次不在存档启用列表里"(新档或中途启用)，`wasEnabledBefore` 回答 "该存档历史上是否启用过"；它适合做存档兼容修补。但它在**独立新建的实例**上调用，任何写入实例字段的状态都会丢失，所以 "是否完成初始化" 必须落在 persistent data 上，不能落在实例字段上。

::: warning 别把生涯状态存进实例字段
缓存实例跨存档复用：把 `SectorAPI`、实体等生涯对象存入实例字段，字段会残留上一档的对象 (旧档对象泄漏)，行为错乱且难排查；`onEnabled` 的独立新实例则会让字段状态直接丢失。状态一律进 `getPersistentData()` 或随存档序列化的对象。
:::

- 脚本/插件注册前**建议**先查重：`Global.getSector().hasScript(类.class)`、`getListenerManager().hasListenerOfClass(...)`、`getGenericPlugins().hasPlugin(...)`，与 persistent data 标记互为冗余。
- `configureXStream(XStream)` 在每次 (重新) 构建存档 XStream 时对全部已启用 MOD 调用 (保存与读取前都会构建)，用于登记自定义类型到存档序列化；小数据优先放 `getPersistentData()`，无需动 XStream。

规则已经齐了，把它们落成一个完整类，编进既有的 jar。

## 完整最小例

在既有 MOD 布局上增加入口类 (MOD 根目录 `Starsector/mods/GearIndustry`，`build.gradle`、`settings.gradle` 沿用既有配置)：

```text
Starsector/mods/GearIndustry/
├── build.gradle
├── settings.gradle
├── mod_info.json          # 追加 modPlugin 字段（见上）
├── data/
├── src/
│   └── data/scripts/gearin/GearInModPlugin   # 本节新增
└── jars/
```

接管新档生成页的入口时：`mod_info.json` 的 `modPlugin` 从 `data.scripts.gearin.campaign.GearInSystemGenPlugin` 改为 `data.scripts.gearin.GearInModPlugin`，生成与关系播种逻辑随入口搬到本节类的 `onNewGame`(见下 "累计工程补全片段")；

旧的入口类 `GearInSystemGenPlugin` 如残留不会报错，只会安静地成为一份不再被调用的旧实现，应删除。

### GearInModPlugin

```java
package data.scripts.gearin;

import com.fs.starfarer.api.BaseModPlugin;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.SectorAPI;
import com.fs.starfarer.api.impl.campaign.shared.SharedData;

/**
 * 完整最小例：ModPlugin 生命周期、注册与存档入口。
 * mod_info.json 的 modPlugin 字段指向本类。
 * 每个回调打印一行日志；"首次进入存档"的一次性初始化用 sector persistent data 门控（CLASS_MARK 模式）。
 */
public class GearInModPlugin extends BaseModPlugin {

    /** 存档持久化标记：本 MOD 是否已完成首次进入初始化（随存档保存）。 */
    public static final String CLASS_MARK = "GearInModPlugin";

    @Override
    public void onApplicationLoad() throws Exception {
        String version = Global.getSettings().getGameVersion();
        if (!version.startsWith("0.98a")) {
            throw new IllegalArgumentException("齿轮工业要求游戏版本 0.98a，当前为 [" + version + "]");
        }
        log("onApplicationLoad");
    }

    @Override
    public void onEnabled(boolean wasEnabledBefore) {
        log("onEnabled wasEnabledBefore=" + wasEnabledBefore);
    }

    @Override
    public void onNewGame() {
        log("onNewGame");
    }

    @Override
    public void onGameLoad(boolean newGame) {
        SectorAPI sector = Global.getSector();
        if (!sector.getPersistentData().containsKey(CLASS_MARK)) {
            sector.getPersistentData().put(CLASS_MARK, true);
            SharedData.getData().getPersonBountyEventData().addParticipatingFaction("gear_industry");
            log("first-entry initialization (CLASS_MARK gate opened)");
        }
        log("onGameLoad newGame=" + newGame);
    }

    @Override
    public void beforeGameSave() {
        log("beforeGameSave");
    }

    @Override
    public void afterGameSave() {
        log("afterGameSave");
    }

    private static void log(String message) {
        Global.getLogger(GearInModPlugin.class).info("GearIn lifecycle: " + message);
    }
}
```

对应关系：

- `extends BaseModPlugin`：提供全部 19 个 `ModPlugin` 方法的空实现，只需覆写用到的回调；直接实现接口则要写满全部方法 (API javadoc 的 NOTE)。
- `onApplicationLoad() throws Exception`：接口签名自带 `throws Exception`，版本检查在这里抛 `IllegalArgumentException` 会中止启动。
- `getPersistentData().containsKey(CLASS_MARK)`：存档级判断，见 "存档入口"。
- `SharedData.getData().getPersonBountyEventData().addParticipatingFaction("gear_industry")`：把势力注册进赏金机制的参与列表 (沿用旧版示例行为)，只在首次进入时执行一次。
- `Global.getLogger(GearInModPlugin.class)`：日志写入 `Starsector/starsector-core/starsector.log`。

累计工程补全片段 (本节示例类不含新档生成页的生成器类，累计工程把生成入口接回 `onNewGame`)：

```java
    @Override
    public void onNewGame() {
        // C03-S03：新档生成入口（齿轮工业星系与关系播种），累计工程保留
        new data.scripts.gearin.campaign.GearInSystemGenerator().generate(Global.getSector());
        seedRelations(Global.getSector());
        log("onNewGame");
    }
```

构建命令 (在 MOD 根目录 `Starsector/mods/GearIndustry/` 执行)：

```text
gradle jar
```

产物 `jars/GearIndustry.jar` 应包含 `data/scripts/gearin/GearInModPlugin.class`(可用 `jar tf` 核对)。

类已经就位，下面用日志顺序验证整条链路。

## 验证方案

前置数据：编译 JAR 链路建立的 MOD 布局 (C06-S01)——`mod_info.json`(追加本节 `modPlugin` 字段)、`build.gradle`、`settings.gradle`、入口类 `GearInModPlugin`(累计工程另含新档生成页的补全片段，C03-S03)。

操作步骤：

1. 在 MOD 根目录 `Starsector/mods/GearIndustry/` 执行 `gradle jar`，确认 `BUILD SUCCESSFUL`，且 `jar tf jars/GearIndustry.jar` 含 `data/scripts/gearin/GearInModPlugin.class`。
2. 启动游戏勾选 "齿轮工业"，进入主菜单后退出；`starsector.log` 确认出现 `GearIn lifecycle: onApplicationLoad`，且无 `gear_in` 引起的 `ERROR`。
3. 新建生涯，确认日志顺序为 `onNewGame` → `onEnabled wasEnabledBefore=false` → `first-entry initialization (CLASS_MARK gate opened)` → `onGameLoad newGame=true` → `beforeGameSave` → `afterGameSave`(新档末尾自动存档)。
4. 读档并手动保存，确认日志为 `onGameLoad newGame=false`(无 `onEnabled` 行、无 `first-entry` 行)，随后 `beforeGameSave` → `afterGameSave`。
5. 故障注入 1：注释掉 `mod_info.json` 的 `modPlugin` 行，重启并新建生涯 ——MOD 一切正常但没有任何 `GearIn lifecycle:` 日志 (对应故障分析第 3 条)；改回后确认恢复。
6. 故障注入 2：注释掉 `onGameLoad` 里的 `getPersistentData().put(CLASS_MARK, true)`，重启读档 —— 每次读档 `first-entry initialization` 日志重现 (对应故障分析第 5 条)；改回后确认恢复。

预期结果：

- **构建**：`gradle jar` 以 `BUILD SUCCESSFUL` 结束；`jar tf jars/GearIndustry.jar` 能看到 `data/scripts/gearin/GearInModPlugin.class`。
- **启动**：启动游戏勾选 "齿轮工业"，进入主菜单前 `starsector.log` 出现一行 `GearIn lifecycle: onApplicationLoad`。没有这行 → 见故障分析第 3 条。
- **新建生涯**：日志按顺序出现 (新档流程，`onEnabled` 在新档必触发，之后自动存档)：

```text
GearIn lifecycle: onNewGame GearIn lifecycle: onEnabled wasEnabledBefore=false GearIn lifecycle: first-entry initialization (CLASS_MARK gate opened) GearIn lifecycle: onGameLoad newGame=true GearIn lifecycle: beforeGameSave GearIn lifecycle: afterGameSave
```

- **读档**：日志只出现 `GearIn lifecycle: onGameLoad newGame=false`(MOD 本就在存档启用列表，不触发 `onEnabled`；`first-entry` 行不再出现，门已开)。若这个存档是 "中途启用" 本 MOD 建出来的，则首次读它时 `onEnabled wasEnabledBefore=false` 会先于 `onGameLoad` 出现。
- **保存** (读档后手动保存)：日志依次出现 `beforeGameSave` → `afterGameSave`。

通过信号：操作 2–4 的日志行与顺序全部符合；操作 5、6 的注入与恢复都符合预期。

清理：取消勾选测试 MOD；删除测试期间残留的旧入口类 `GearInSystemGenPlugin`(`data.scripts.gearin.campaign` 包)。

## 故障分析

1. **启动失败，报错 `Problem loading class [data.scripts.gearin.GearInModPlugin]`**。触发：`modPlugin` 值拼错、包名与目录不符、类没编进 jar(漏了 `src` 路径) 或 jar 没声明。原因：脚本类加载器按类名加载实例，`Class.forName` 找不到与类名对应的字节码。可观察：启动阶段 (进入主菜单前) 报错弹窗，日志里该异常附 `ClassNotFoundException`/`NoClassDefFoundError` 原因。修正：用 `jar tf` 核对类路径，与 `modPlugin` 值逐段对照。
2. **启动失败，报错 `ClassCastException`**。触发：`modPlugin` 指向的类没有继承 `BaseModPlugin` 也没有实现 `ModPlugin`。原因：`getEnabledModPlugins` 的 `(ModPlugin)` 强制转换失败。修正：让类 `extends BaseModPlugin`。
3. **启动正常、游戏正常，但没有任何 `GearIn lifecycle:` 日志**。触发：`mod_info.json` 漏写 `modPlugin`(字段可选)，或写在了未启用的分支。原因：`getEnabledModPlugins` 对 `null` 类名直接跳过，因此无报错、无回调 —— 它是本节最安静的失败方式，一切正常，只是永远不被叫醒。修正：补上 `modPlugin` 并核对类名。
4. **启动失败，堆栈指向 `onApplicationLoad` 的 `NullPointerException`**。触发：在 `onApplicationLoad` 里访问 `Global.getSector()`。原因：此刻 sector 尚未建立，`Global.getSector()` 返回 `null`，任何解引用都是 NPE。修正：应用级数据放这里，生涯状态全部移到 `onGameLoad`/`onNewGame`。
5. **每次读档都重复 "首次进入" 日志，实体/势力注册重复**。触发：一次性初始化没有 persistent data 标记，直接写在 `onGameLoad` 里。原因：`onGameLoad` 对新档和读档都会调用，缺标记时每次读档都重跑初始化。可观察：每次读档 `first-entry initialization` 日志重现；`SharedData` 赏金参与势力、脚本等重复注册 (`addParticipatingFaction` 重复加入同一势力)。修正：用 `CLASS_MARK` 标记 (见 "存档入口")，并把日志行移到标记内验证。
6. **脚本/监听器重复推进，行为随读档次数叠加**。触发：`onGameLoad` 里 `addScript`/`registerPlugin` 不查重、不做一次性判断。可观察：SDF 脚本、事件链等重复实例各自推进。修正：注册前用 `hasScript`/`hasListenerOfClass`/`hasPlugin` 查重，或把注册收敛到 persistent data 标记块。
7. **新档与读档对同一状态重复写入**。触发：同一初始化既写在 `onNewGame` 又写在 `onGameLoad(true)`，新档两条路径都会走。可观察：新档初始化执行两遍，出现重复注册或重复实体。修正：把共用初始化收敛到 `onGameLoad` 加 persistent data 标记；`onNewGame` 只放 "纯新档" 逻辑 (如保留名登记、新档生成页的生成入口)。
8. **旧档对象泄漏，行为错乱且难排查**。触发：把 `SectorAPI`、实体等生涯对象存入实例字段。原因：`getEnabledModPlugins` 返回的缓存实例跨存档复用，字段仍指向上一档的对象。修正：状态一律进 `getPersistentData()` 或随存档序列化的对象。
9. **版本检查缺失，高版本上行为未定义**。触发：`onApplicationLoad` 里不检查 `getGameVersion()`。可观察：用户手动修改 `mod_info.json` 把 MOD 用在更高版本游戏上时，问题在很晚的阶段才暴露。修正：检查版本是否以 `0.98a` 开头，不符就抛异常拒绝启动 (成熟实例做法，有意让启动中止)。

## 总结

本节把新档生成阶段的最小入口补全为完整生命周期：`mod_info.json` 的 `modPlugin` 声明入口类 (可选字段，缺失 = 无回调且无报错)，启动资源加载期实例化并缓存，新档/读档/保存各节点按固定顺序回调全部已启用 MOD(原版 `CoreLifecyclePluginImpl` 恒在最前)。读时序时依次问：`modPlugin` 声明了吗、类继承 `BaseModPlugin` 吗、回调属于哪个阶段 (启动/新档链/读档/保存/战斗)、一次性初始化有没有 persistent data 标记。跨存档状态放 `getPersistentData()`，不要放实例字段；`onEnabled` 只在 MOD 进入已有存档时触发且运行在独立新实例上。AI picker 返回 `null` 保留原版，返回 `PluginPick` 时按 `PickPriority` 竞争；`onGameSaveFailed` 只在保存失败时回调，`afterGameSave` 只在成功时回调。

从逻辑上看，生命周期回调回答的是 "游戏进程何时调用 MOD"，本节已把这条固定约定完整走通；它的自然延伸，是回调中登记的内容如何随存档持久化、读档时如何恢复 —— persistent data 与 XStream 序列化正是这条线上的下一环。

## 建议参考项

- Java API (包路径)：
  - `com.fs.starfarer.api.ModPlugin`：全部 19 个生命周期回调的签名与 javadoc，含 `onEnabled` 触发条件与 `wasEnabledBefore` 语义、`onGameLoad` 双重含义、建议继承 `BaseModPlugin` 的 NOTE。
  - `com.fs.starfarer.api.BaseModPlugin`：全部回调的空实现，继承它只需覆写用到的回调。
  - `com.fs.starfarer.api.PluginPick` 与 `com.fs.starfarer.api.campaign.CampaignPlugin`：AI picker 的返回值与多 MOD 竞争时的优先级 (`PickPriority` 枚举)。
  - `com.fs.starfarer.api.campaign.SectorAPI`：`getPersistentData()`(随存档保存的 `Map<String, Object>`)、`addScript`、`registerPlugin`。
  - `com.fs.starfarer.api.SettingsAPI`：`getGameVersion()`(版本门检查)、`getModManager()`。
  - `com.fs.starfarer.api.ModManagerAPI`：`isModEnabled`。
  - `com.fs.starfarer.api.impl.campaign.CoreLifecyclePluginImpl`：原版生命周期插件，作为回调列表第一个参与全部回调。
  - `com.fs.starfarer.api.impl.campaign.shared.SharedData`：`getPersonBountyEventData().addParticipatingFaction(...)`，赏金参与势力注册。
- 原版数据 (以游戏安装根为基准)：
  - `data/config/settings.json`：`coreLifecyclePlugin` 字段，原版生命周期插件类名所在。
  - `starsector-core/starsector.log`：回调日志与故障定位。
- 示例 MOD 内 (以 MOD 根目录为基准)：
  - `data/scripts/gearin/`：本节入口类 `GearInModPlugin`(含存档持久化标记 `CLASS_MARK`) 所在源目录。
  - `jars/GearIndustry.jar`：编译产物，含 `data/scripts/gearin/GearInModPlugin.class`。
  - `mod_info.json`：`modPlugin` 注册字段。
