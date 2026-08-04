# 修改舰船数值

本节使用 `MutableShipStatsAPI` 修改舰船数值，并确认修饰值的计算顺序、生效时机与稳定 ID。相同改动放在生涯重建和战斗部署阶段会产生不同结果，因此需要先确定修改所属的生命周期。

*本节预期阅读时间：~20 分钟。*

*本节预期操作时间：~30 分钟。*

## 学习目标

完成后，您将能够：

- 说出 `ShipAPI.getMutableStats()` 返回什么、stats 与战斗实体 / 舰队成员 / 装配的绑定关系，以及每个 `MutableStat` 的 base 值从哪些数据列来；
- 复述 `MutableStat` 的计算公式 `(base + base * percent / 100 + flat) * mult`，解释 percent 与 flat 的加算关系、mult 的乘算优先级，以及延迟重算 (`needsRecompute`) 语义；
- 解释 modifier ID 的稳定性语义：同 ID 同类型覆盖、`unmodify(source)` 一次清三类、零值 / 恒等值不创建条目，为什么**必须**使用稳定 ID，以及违反后的可观察结果；
- 说出 `applyEffects` 的三个时机 (`FleetMemberAPI.updateStats` 生涯重建 / `Ship` 构造 / 加入战斗引擎)，并解释为什么 `applyEffectsBeforeShipCreation` 必须可重入；
- 从症状定位典型故障：数值持续累积、解除不生效、两个来源互相覆盖、生涯与战斗数值不一致、percent 陷阱 (base 为 0 / `-100`)。

## 前置知识

阅读本节前，建议先掌握以下既有内容：

- 命名约定 (C01-S01)：MOD ID `gear_in`、数据 / 资源 key 前缀 `GearIn_`、包 `data.scripts.gearin.<domain>`、类 `GearIn<Subject>`。
- 船体注册与数值列 (C01-S02)：`ship_data.csv` 注册行与 `GearIn_artizan` 船体。
- 战斗插件模型 (C07-S01)：`addPlugin(...)` 注册、`EveryFrameCombatPlugin.advance` 每帧回调、暂停检查。
- 测试战役 (C03-S01)：战役 (mission) 概念与 `GearIn_test` 测试战役。
- 编译 JAR 链路 (C06-S01)：MOD 编译产物 `jars\GearIndustry.jar`。
- 生命周期纪律 (C06-S03)：`Global` 生命周期 —— 战斗引擎战斗外是非 null 空引擎、禁止跨战斗缓存引擎引用。
- 战斗引擎读取侧 (C07-S02)：`getPlayerShip()` 永不 null (部署阶段返回 "Command Shuttle" 假舰)、`isAlive()` 过滤、时间语义。

## 文件与 API 链路

本节涉及的接口都在 `com.fs.starfarer.api.combat`：

| 接口 / 类型 | 本节角色 | 链路 |
|---|---|---|
| `ShipAPI` | 战斗实体入口 | `engine.getShips()` / `getPlayerShip()` 的返回元素 (C07-S02)；`getMutableStats()` 取得本节主角 |
| `MutableShipStatsAPI` | 舰船 stats 集合 | `ship.getMutableStats()` 取得；约 90 个 getter 返回 `MutableStat` 或 `StatBonus` |
| `MutableStat` | 固定 base 的数值对象 | `MutableShipStatsAPI` 各 getter 的返回类型；`CombatEngineAPI.getTimeMult()` 也是它 (C07-S02 消费)|
| `MutableStat.StatMod` | 单项修饰 (source / desc / value)| `MutableStat` 内部三张表的条目类型 |
| `StatBonus` | 以调用时 base 计算的另一族修饰 | `MutableShipStatsAPI` 的射程 / 弹药 / CR 类 getter 返回它；本节只做对照，不展开 |
| `HullModEffect` | 船插效果回调 | `applyEffectsBeforeShipCreation` / `applyEffectsAfterShipCreation` / `applyEffectsAfterShipAddedToCombatEngine` 三个时机 (见 "applyEffects 时机")|
| `DynamicStatsAPI` | 动态 stat 容器 | `stats.getDynamic()`；常用作 "标记位" 存储，本节不展开 |

数据到 base 的链路 (谁给 `MutableStat` 定 base)：

```text
ship_data.csv 列（max speed / max flux / flux dissipation ...）
  → 船体 CSV 加载器（"max flux"、"flux dissipation" 列读入）
  → 引擎规格 / 反应堆规格（hull spec 的引擎 / 反应堆规格）
  → 舰船 stats 构造器（maxSpeed = new MutableStat(engineSpec.getMaxSpeed()) 等）
  → MutableStat.base
```

代码到消费的链路 (修饰怎么变成游戏行为)：

```text
modifyFlat / modifyPercent / modifyMult(稳定 ID, 值)
  → MutableStat 三张表（flatMods / percentMods / multMods，均以 modifier ID 为键）
  → 延迟 recompute（needsRecompute 置位后，下次读取才重算）
  → getModifiedValue() / getModifiedInt()
  → 使用方：引擎控制器（maxSpeed、turnRate，每帧读）、
    幅能追踪器（fluxCapacity / fluxDissipation，每帧重读）、
    FleetMemberAPI（生涯侧 burn / 货舱 / 补给）、战斗引擎.getTimeMult() 等
```

stats 同时绑定三个既有对象：`getEntity()` 返回战斗实体 (仅战斗期非 null)、`getFleetMember()` 返回舰队成员、`getVariant()` 返回装配。base 的数值来源就是上一张图的数据链。

两张图给出的是全景：base 从哪来、修饰最终被谁读。接下来的问题是修饰本身怎么组织 —— 这正是 `MutableStat` 三张表的工作。

## MutableStat 的 modifier 体系

`MutableStat` 保存一个 base (构造时固定) 和三张修饰表，全部以 modifier ID (`StatMod.source`) 为键：

| 表 | 修改方法 | 汇总方式 | 单位语义 |
|---|---|---|---|
| `flatMods` | `modifyFlat(source, value)` | 相加 (`flatMod`)| 直接加减数值 |
| `percentMods` | `modifyPercent(source, value)` | 相加 (`percentMod`)| 按 base 的百分比 |
| `multMods` | `modifyMult(source, value)` | 连乘 (`mult`)| 整体倍率 |

重算公式 (`com.fs.starfarer.api.combat.MutableStat`)：

```text
modified = (base + base * percentMod / 100 + flatMod) * mult
```

由此得到加算优先级的三条推论：

1. **percent 与 flat 都作用在 base 上**，两者之间是纯加算，顺序无关；mult 最后乘整个括号，是 "最高优先级" 的整体倍率。
2. **percent 的基准只有 base**：`modifyPercent(-100)` 只把 `base * percent / 100` 抵消为 0，`flatMod` 仍然生效：
   - base 为 0 的 stat (如 `getMaxCombatReadiness()`，API javadoc 原文 "Base value is 0, modified by crew etc. modifyPercent will do nothing since the base value is 0") 用 `modifyPercent` 没有任何效果。
3. 修改与读取都是**延迟重算**：`modifyXxx` 只置 `needsRecompute` 标志并把条目写进表，`getModifiedValue()` / `getModifiedInt()` / `getFlatMod()` / `getPercentMod()` / `getMult()` 读取时若标志为真才执行 `recompute()`：
   - **读到的永远是包含全部已写修饰的最新值**，不存在 "忘记刷新" 的中间态。

::: info 为什么是延迟重算
修饰的写入远少于读取 —— 一场战斗中 `getModifiedValue()` 被引擎控制器、flux 跟踪器、舰队面板等每帧高频读取。延迟方案把重算推迟到读取时，且只在对应 stat 被污染后执行一次，避免每次写入都触发全量重算。
:::

方法速查 (完整签名见 "建议参考项")：

| 族 | 方法 | 语义 |
|---|---|---|
| 写入 | `modifyFlat(source, value)` / `modifyPercent` / `modifyMult` | 同 ID 同类型覆盖；`value==0` (flat / percent) 或 `value==1` (mult) 且该 ID 无旧条目时不创建条目；同值重复调用只刷新 desc (幂等)|
| 强制写入 | `modifyFlatAlways` / `modifyPercentAlways` / `modifyMultAlways` | 无条件写条目 |
| 解除 | `unmodify(source)` | **同时**清除三张表里该 ID 的条目 |
| 定向解除 | `unmodifyFlat(source)` / `unmodifyPercent` / `unmodifyMult` | 只清一张表 |
| 读取 | `getBaseValue` / `getModifiedValue` / `getModifiedInt` / `getFlatMod` / `getPercentMod` / `getMult` / `isUnmodified` / `getFlatStatMod` (另有 percent / mult 两版)| 见 "延迟重算"；`isUnmodified()` 在**三张表全空**时为 true |
| 改 base | `setBaseValue(base)` | 修改 base 并触发重算 |

`StatBonus` 对照 (一句话)：`MutableShipStatsAPI` 里的射程 / 弹药 / CR 类 getter 返回 `StatBonus`，它不保存固定 base，`computeEffective(baseValue)` 才以调用参数为 base 计算。

本节示例只碰 `MutableStat`。

三张表都以 modifier ID 为键，ID 因此从参数升格为规则的核心：写错 ID，覆盖、清除与累积三条语义会同时失效。

## modifier ID 稳定性 (必须遵守的规则)

modifier ID (`StatMod.source`) 是修饰表唯一的键。以下规则全部有效，**必须 / 禁止**为硬措辞：

1. **必须**使用稳定且唯一的 modifier ID。同一来源对同一 stat 的每一次写入，ID 必须逐字相同 (建议用常量)：
   - 船插场景直接用自己的 hullmod id 参数 ——`applyEffectsBeforeShipCreation(HullSize, MutableShipStatsAPI, String id)` 的 `id` 就是为此提供的，直接 `modifyPercent(id, ...)` 即可。
   - 原版引擎自己也这么做：`ShipEngineControllerAPI` 用常量 `"zero_flux_boost"` 与 `"engine_damage_penalty"` 每帧应用 / 解除；`Ship` 构造器与 `FleetMemberAPI.updateStats` 用 `"flux_capacitors"` / `"flux_vents"` 平铺修饰。
2. **禁止**用每次调用都会变化的值拼接 modifier ID (帧号、时间戳、随机数、对象身份、自增计数器)。违反的可观察结果：每次 `modifyXxx` 都在表里**新增**一条条目 (percent 累加、flat 累加、mult 连乘)，`modified` 值单向漂移、只增不减；`unmodify(旧 ID)` 清不掉后加的条目，数值残留；条目数持续增长，一场战斗下来修饰表会膨胀到比您的舰队名单还长，而全程没有一条报错提醒您：
   - 修正：ID 必须是常量。
3. **必须**理解 "同 ID 覆盖" 只在**同一张表**内成立。flat / percent / mult 三张表互相独立：同一个 ID 分别调 `modifyFlat` 与 `modifyPercent` 会得到两条条目，三条全部生效：
    - 可观察结果：只改一个 ID 的 flat 值，percent 与 mult 不受影响；想一次清掉必须 `unmodify(source)` (同时清三类) 或分别 `unmodifyXxx`。
4. **必须**成对设计解除路径：`modifyXxx(id, v)` 与 `unmodifyXxx(id)` 的 ID 必须逐字一致 (常量复用是唯一可靠做法)。违反的可观察结果：`modified` 不回 base、条目残留、无任何报错 (静默失效)。常见写法：先 `unmodifyFlat(id)` 再 `modifyFlat(id, ...)` 换值。
5. **禁止**两个来源共用同一个 modifier ID (例如两个船插都写 `"speed_bonus"`)。可观察结果：后 apply 者覆盖先 apply 者，一方的加成 "凭空消失"，无报错；stats 每次重建时重放顺序决定谁赢，行为随加载顺序漂移。修正：每个来源用自己唯一的前缀 (本节示例用 `GearIn_ship_stats_probe_*` 命名空间)。
6. 每帧重复应用**同一 ID 同类型同值**是安全且推荐的做法：`modifyXxx` 幂等 (同值只刷新 desc，不新增条目)，不会累积。这是 "战斗内每帧动态调整" 可行性的来源 —— 引擎自己的 `zero_flux_boost` 每帧应用 / 解除就是这个做法的实例。
7. **必须**注意 `modifyFlat(id, 0)` / `modifyPercent(id, 0)` / `modifyMult(id, 1)` 的 "不创建条目" 语义：无旧条目时**不创建**；有旧条目时**覆盖为 0 / 1** (条目仍在，`isUnmodified()` 仍为 false)：
   - 需要真正移除条目用 `unmodify`。可观察结果：用 `modifyXxx(id, 0)` 想 "清掉" 加成，`modified` 数值正常了，但表里残留 0 值条目，`isUnmodified()` 判据失效。

::: caution modifier ID：必须遵守的规则
稳定且唯一的 modifier ID 是本节全部规则的总纲：用帧号、时间戳、随机数、对象身份或自增计数器拼接 ID，或让两个来源共用同一个 ID，都属于必须禁止的错误 —— 后果是修饰单向漂移、解除静默失效、加成互相覆盖，且全程没有任何报错。
:::

modifier 写进表里只是第一步，还要知道这些表在什么时机被整体重建与重放 —— 时机选错，数值会在错误的位置生效，或在应该生效的地方完全缺席。

## applyEffects 时机

`HullModEffect` 的 stats 修饰在三个时机被调用：

1. **生涯侧：`FleetMemberAPI.updateStats()`**。stats 被**整体重建**(按装配重新生成一套 stats 集合)，随后按顺序重放：舰长个人技能、舰队指挥官全局技能、每个船插的 `applyEffectsBeforeShipCreation`：
   - `"flux_capacitors"` / `"flux_vents"` 平铺修饰、`"mothballed_mod_id"` 封存修饰、`getDynamic().removeUmodified()`。
    - 只要 `statUpdateNeeded` (改装配、换舰长、进出舰队界面等) 就会重建重放。
2. **战斗侧：舰船对象创建**。同样先按装配重建 stats 集合、重放舰队指挥官技能与 buff，随后创建舰船实体 (传入重建出的 stats 与装配)。
   - `Ship` 构造器里再次对全部船插调用 `applyEffectsBeforeShipCreation`，然后 `applyEffectsAfterShipCreation(ship, id)`。
3. **加入战斗引擎：`CombatEngineAPI.addObjectToEngine`** 对每艘新舰调用 `applyEffectsAfterShipAddedToCombatEngine()`，触发船插的 `applyEffectsAfterShipAddedToCombatEngine(ship, id)`：
   - API javadoc 原文 "Only called once. Not called again if the ship is removed and then added back to the engine."(默认空实现)。

由此得到必须遵守的规则：

- **改 stats 必须放在 `applyEffectsBeforeShipCreation`**。API javadoc (`com.fs.starfarer.api.combat.HullModEffect`) 原文：
  - "Effects applied here should NOT affect ship stats as this does not get called from the campaign. Apply stat changes in applyEffectsBeforeShipCreation() instead, as that does affect the campaign."。
  - 违反的可观察结果：把 `modifyXxx` 写进 `applyEffectsAfterShipCreation`，战斗内生效、生涯 (舰队面板、货舱 / 燃料 / 补给、burn 等级等) 完全不生效 —— 同一套装配两套数值。
- **`applyEffectsBeforeShipCreation` 必须可重入 (幂等)**：stats 会被反复重建，每次重建都重放全部修饰。叠加式写法 (每次 +10%、每次 new ID) 会在每次重建后数值继续膨胀 (生涯面板数值越看越大)；覆盖式 + 稳定 ID (规则 1、6) 是唯一安全写法。
- **战斗内运行期改 stats 逐帧生效**：`FluxTrackerAPI.advance` 每帧调用 `updateFluxValuesFromStats()` 重读 `getFluxCapacity().getModifiedValue()` / `getFluxDissipation().getModifiedValue()`：
  - `ShipEngineControllerAPI` 每帧读 `getMaxSpeed().getModifiedValue()`；
  - 因此战斗插件在 `advance` 里改 stats，下一帧即被消费，不需要任何 "刷新" 调用。

规则讲完，还需要一个当场可观察的现场。本节把它们落成一个最小插件类：用施加、覆盖、解除三个阶段，把幂等、同 ID 覆盖、一次清除三条语义变成日志里可以直接读到的数字变化。

## 完整最小例

在既有 MOD 布局与战斗插件模型之上新增一个插件类文件 (不新增任何资源、不新增任何注册项)，包与类名遵循命名约定 (`data.scripts.gearin.combat`、`GearInShipStatsProbe`)：

```text
Starsector\mods\GearIndustry\
├── build.gradle
├── settings.gradle
├── mod_info.json
├── data\
├── src\
│   └── data\scripts\gearin\
│       ├── GearInModPlugin            # C06-S02 所有；本节不改动
│       ├── GearInGlobalAccess         # C06-S03 所有；本节只消费
│       └── combat\
│           ├── GearInCombatStatePlugin   # C07-S02 所有；本节只消费
│           └── GearInShipStatsProbe      # 本节新增
└── jars\
```

### GearInShipStatsProbe

```java
package data.scripts.gearin.combat;

import java.util.List;

import org.apache.log4j.Logger;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.BaseEveryFrameCombatPlugin;
import com.fs.starfarer.api.combat.CombatEngineAPI;
import com.fs.starfarer.api.combat.MutableShipStatsAPI;
import com.fs.starfarer.api.combat.MutableStat;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.input.InputEventAPI;
import com.fs.starfarer.api.util.IntervalUtil;

/**
 * 完整最小例：ShipAPI / MutableShipStatsAPI / MutableStat 与 modifier ID。
 *
 * 本插件由战斗上下文注册（C07-S01 的插件模型），只在本场战斗内有效：
 * - 引擎引用在 init() 中保存，只在本场战斗使用；禁止跨战斗缓存（C06-S03）；
 * - advance() 在暂停时仍会被调用，状态推进前必须先检查 engine.isPaused()（C07-S01）；
 * - getPlayerShip() 在玩家未部署时返回 "Command Shuttle" 假舰（C07-S02），
 *   写操作前用 isShuttlePod() 排除，避免把修饰加到假舰上；
 * - MutableShipStatsAPI 是舰船 stats 的入口：三个 MutableStat 修饰表
 *   （flat / percent / mult）各以 modifier ID 为键；同 ID 同类型调用是幂等覆盖，
 *   每帧重复应用是安全姿势（引擎自己的 zero_flux_boost 同款）；
 * - modifier ID 必须稳定唯一：本节所有修饰 ID 是进程内常量，属本节专属命名空间。
 */
public class GearInShipStatsProbe extends BaseEveryFrameCombatPlugin {

    private static final Logger LOG = Global.getLogger(GearInShipStatsProbe.class);

    /** 稳定 modifier ID（本节专属命名空间，进程内不再变化；命名契约见 C01-S01）。 */
    private static final String MOD_SPEED_PERCENT = "GearIn_ship_stats_probe_speed_percent";
    private static final String MOD_SPEED_FLAT = "GearIn_ship_stats_probe_speed_flat";
    private static final String MOD_SPEED_MULT = "GearIn_ship_stats_probe_speed_mult";
    private static final String MOD_VOLATILE_PREFIX = "GearIn_ship_stats_probe_volatile_";

    /**
     * 故障注入开关（对应"故障分析"故障 1）：
     * true 时每帧用自增 ID 追加一条 percent 修饰，观察 percentMods 只增不减、
     * modified 值持续漂移、unmodify 之后残留条目清不掉。
     */
    private static final boolean PROBE_VOLATILE_ID_FAULT = false;

    /** 战斗推进时间（getTotalElapsedTime(false)，暂停不计）的三个阶段分界。 */
    private static final float APPLY_AT = 2f;      // t=2s 起施加三种修饰（每帧重复，幂等）
    private static final float OVERWRITE_AT = 12f; // t=12s 起把 percent 覆盖为 -20（同 ID 不同值）
    private static final float UNMODIFY_AT = 22f;  // t=22s 起全部解除

    private CombatEngineAPI engine;
    private final IntervalUtil reportInterval = new IntervalUtil(2f, 2f);
    private long volatileCounter = 0L;

    @Override
    public void init(CombatEngineAPI engine) {
        this.engine = engine;
        LOG.info("GearIn stats probe init: engine ready");
    }

    @Override
    public void advance(float amount, List<InputEventAPI> events) {
        if (engine.isPaused()) {
            return; // 暂停守卫：暂停时 advance 仍会被调用，状态不得推进（C07-S01）
        }
        ShipAPI ship = engine.getPlayerShip();
        if (ship == null || !ship.isAlive() || ship.isHulk() || ship.isShuttlePod()) {
            return; // 部署阶段拿到的 "Command Shuttle" 假舰不做写操作（C07-S02）
        }
        MutableShipStatsAPI stats = ship.getMutableStats();

        float t = engine.getTotalElapsedTime(false);
        if (t >= UNMODIFY_AT) {
            unapplyModifiers(stats);
        } else if (t >= OVERWRITE_AT) {
            overwriteModifiers(stats);
        } else if (t >= APPLY_AT) {
            applyModifiers(stats);
        }

        if (PROBE_VOLATILE_ID_FAULT) {
            stats.getMaxSpeed().modifyPercent(MOD_VOLATILE_PREFIX + volatileCounter++, 0.01f);
        }

        reportInterval.advance(amount);
        if (reportInterval.intervalElapsed()) {
            logStats(ship, stats);
        }
    }

    /** 施加三种修饰：同一稳定 ID 每帧重复调用，同类型同值 → 仅刷新 desc，条目不新增。 */
    private void applyModifiers(MutableShipStatsAPI stats) {
        stats.getMaxSpeed().modifyPercent(MOD_SPEED_PERCENT, 25f);
        stats.getMaxSpeed().modifyFlat(MOD_SPEED_FLAT, 10f);
        stats.getMaxSpeed().modifyMult(MOD_SPEED_MULT, 1.5f);
    }

    /** 覆盖阶段：同一 ID 不同值 → 替换旧条目，条数不变。 */
    private void overwriteModifiers(MutableShipStatsAPI stats) {
        stats.getMaxSpeed().modifyPercent(MOD_SPEED_PERCENT, -20f);
    }

    /** 解除阶段：unmodify(source) 同时清除 flat / percent / mult 三类条目。 */
    private void unapplyModifiers(MutableShipStatsAPI stats) {
        stats.getMaxSpeed().unmodify(MOD_SPEED_PERCENT);
        stats.getMaxSpeed().unmodify(MOD_SPEED_FLAT);
        stats.getMaxSpeed().unmodify(MOD_SPEED_MULT);
    }

    /** 周期报告：maxSpeed 的 base / 最终值 / 三类修饰合计与条目数，以及 flux 两个 stat 的 base / 最终值。 */
    private void logStats(ShipAPI ship, MutableShipStatsAPI stats) {
        MutableStat maxSpeed = stats.getMaxSpeed();
        LOG.info("GearIn stats read: hull=" + ship.getHullSpec().getHullId()
                + " maxSpeed base=" + maxSpeed.getBaseValue()
                + " modified=" + maxSpeed.getModifiedValue()
                + " flat=" + maxSpeed.getFlatMod()
                + " percent=" + maxSpeed.getPercentMod()
                + " mult=" + maxSpeed.getMult()
                + " entries flat=" + maxSpeed.getFlatMods().size()
                + " percent=" + maxSpeed.getPercentMods().size()
                + " mult=" + maxSpeed.getMultMods().size()
                + " unmodified=" + maxSpeed.isUnmodified());
        LOG.info("GearIn stats flux: capacity base=" + stats.getFluxCapacity().getBaseValue()
                + " modified=" + stats.getFluxCapacity().getModifiedValue()
                + " dissipation base=" + stats.getFluxDissipation().getBaseValue()
                + " modified=" + stats.getFluxDissipation().getModifiedValue());
    }
}
```

对应关系：

- `init` 的 `GearIn stats probe init` 行：确认插件挂上 (战斗上下文注册)。
- `advance` 的三道检查：`isPaused()` (暂停检查)、`isAlive()` / `isHulk()` (残骸过滤)、`isShuttlePod()` (排除部署阶段的 "Command Shuttle" 假舰 —— 假舰是存活实体，不排除就会把修饰写到假舰上)。
- 阶段划分用 `engine.getTotalElapsedTime(false)` (暂停不计的战斗推进时间)：t∈[2,12) 施加、t∈[12,22) 覆盖 percent、t≥22 解除 —— 三个阶段把 "幂等重复应用 / 同 ID 覆盖 / unmodify 一次清三类" 三个语义变成可观察的日志表现。
- `applyModifiers` 每帧重复调用：同 ID 同值幂等 (条目数恒为 1)—— 规则 6 的现场演示。
- `overwriteModifiers` 同 ID 改值：条目数不变、percent 合计从 25 变 -20—— 规则 1、3 的现场演示。
- `unapplyModifiers`：`unmodify(source)` 一次清三类，`isUnmodified()` 回 true—— 规则 4 的现场演示。
- `logStats`：`getBaseValue()` / `getModifiedValue()` / `getFlatMod()` / `getPercentMod()` / `getMult()` / 三表 `size()` / `isUnmodified()` 一次打印；`entries` 数字是 "条目数是否稳定" 的直接证明。
- `PROBE_VOLATILE_ID_FAULT` 故障开关：对应故障分析 1，默认关。
- `IntervalUtil(2f, 2f)`：固定 2 秒节奏的周期触发器。

::: warning 日志 "没反应" 的三个排查点
如果 `Starsector/starsector-core/starsector.log` 里既没有 `GearIn stats probe init` 也没有周期报告行，优先检查三处：注册片段是否真的生效 (是否遗漏或注释)、战斗是否处于暂停 (暂停检查不推进状态)、玩家舰是否仍在部署阶段 ("Command Shuttle" 假舰会被 `isShuttlePod()` 检查排除)。
:::

注册片段 (工程片段 —— 本节不修改任何既有文件，只演示注册调用的写法)：

```java
    // 已有战斗插件的 init() 内新增一行
    @Override
    public void init(CombatEngineAPI engine) {
        engine.addPlugin(new GearInShipStatsProbe());
        // ... 原有逻辑
    }
```

构建命令 (在 `Starsector\mods\GearIndustry\` 执行)：

```text
gradle jar
```

产物 `jars\GearIndustry.jar` 应包含 `data/scripts/gearin/combat/GearInShipStatsProbe.class` (可用 `jar tf` 核对)。

## 验证方案

前置数据：既有 MOD 布局与战斗插件接入 (含本节注册片段)、`GearInShipStatsProbe` 源码、`GearIn_test` 测试战役 (C03-S01，玩家舰 `GearIn_artizan`)。

操作步骤：

1. **游戏构建**：在 `Starsector\mods\GearIndustry\` 执行 `gradle jar`，确认 `BUILD SUCCESSFUL` 且 `jar tf jars\GearIndustry.jar` 含 `data/scripts/gearin/combat/GearInShipStatsProbe.class`。
2. **战役运行**：进入 `GearIn_test` 一场战斗，在 `Starsector/starsector-core/starsector.log` 核对 `GearIn stats probe init: engine ready`，随后每 2 秒两条 `GearIn stats read:` / `GearIn stats flux:`：
   - 没有 `gear_in` 引起的 `ERROR`。
3. **三阶段数值**：战斗时间 2–12 秒看 `maxSpeed modified=165.0` (entries 各 1)、12–22 秒看 `modified=111.0` (percent=-20.0)、22 秒后看 `modified=80.0 unmodified=true` (数值推导见下文预期结果)。同屏按速度对比：施加阶段玩家舰明显更快。
4. **暂停语义**：战斗内按空格暂停约 10 秒再恢复，暂停期间无报告；恢复后阶段仍按战斗时间推进 (暂停不计入 `getTotalElapsedTime(false)`)。
5. **故障注入 1**：把 `PROBE_VOLATILE_ID_FAULT` 改为 `true` 重新构建进战斗：`entries percent` 每帧增长、`percent` 合计与 `modified` 持续漂移、`unmodify` 之后 `entries` 仍残留 (对应故障分析 1)；改回 `false` 后确认恢复。
6. **故障注入 2**：把 `applyModifiers` 里的 `modifyPercent(MOD_SPEED_PERCENT, 25f)` 换成 `modifyPercent(MOD_SPEED_PERCENT + "_x", 25f)` 且 `unapplyModifiers` 不变：解除阶段 `modified` 不回 80 (ID 不一致，对应故障分析 2)；改回后确认恢复。

预期结果：

- 步骤 1：以 `BUILD SUCCESSFUL` 结束，退出码 0。
- 步骤 2：以 `BUILD SUCCESSFUL` 结束；`jar tf jars\GearIndustry.jar` 能看到 `data/scripts/gearin/combat/GearInShipStatsProbe.class`。
- 步骤 3：`starsector.log` 出现一行 `GearIn stats probe init: engine ready`，随后每 2 秒两条 `GearIn stats read:` / `GearIn stats flux:`，没有 `ERROR`。
- 步骤 4 施加阶段 (战斗时间 2–12 秒)：`maxSpeed base=80.0 modified=165.0 flat=10.0 percent=25.0 mult=1.5 entries flat=1 percent=1 mult=1 unmodified=false`：
  - `GearIn_artizan` 的 max speed 列是 80，`(80 + 80*25/100 + 10) * 1.5 = 165`；三个 entries 恒为 1，证明同 ID 幂等 (规则 6)。
  - `GearIn stats flux:` 为 `capacity base=3800.0 modified=3800.0 dissipation base=200.0 modified=200.0` (未修饰，base 即最终值)。
  - 玩家舰明显比基础速度快 (165 对 80，约 2 倍)；解除阶段回到基础速度。`isShuttlePod()` 检查保证部署阶段假舰不被修改 (战役开局即设置玩家舰，通常观察不到假舰阶段)。
- 步骤 4 覆盖阶段 (12–22 秒)：`maxSpeed modified=111.0 percent=-20.0`，`entries` 仍为 1—— 同 ID 不同值覆盖旧条目 (规则 1、3)：`(80 - 80*20/100 + 10) * 1.5 = 111`。
- 步骤 4 解除阶段 (22 秒后)：`maxSpeed base=80.0 modified=80.0 flat=0.0 percent=0.0 mult=1.0 entries flat=0 percent=0 mult=0 unmodified=true`—— `unmodify(source)` 一次清三类 (规则 4)；速度回到基础值。
- 步骤 5：暂停期间没有报告输出 (检查生效)；恢复后阶段仍按战斗时间推进 (暂停不计入 `getTotalElapsedTime(false)`)。
- 步骤 6：`entries percent` 每帧增长、`percent` 合计与 `modified` 持续漂移、`unmodify` 之后 `entries` 仍残留；改回 `false` 后恢复。
- 步骤 7：解除阶段 `modified` 不回 80；改回后恢复。

- 包 `data.scripts.gearin.combat`；
- 类 `GearInShipStatsProbe`；
- modifier ID 前缀 `GearIn_ship_stats_probe_`。

通过信号：步骤 1–5 全部符合；步骤 6、7 的注入与恢复都符合预期。

清理：取消勾选测试 MOD

::: tip 日志核对技巧
在 `Starsector/starsector-core/starsector.log` 中按时间戳检索 `GearIn stats` 前缀即可定位本插件的全部输出行；出现 `ERROR` 时优先查看 `gear_in` 包相关的堆栈。
:::

任一预期结果没有出现，验证即判定失败，按故障分析的对应条目核对。

## 故障分析

以下故障按症状分组；第 1、2 项可以用验证方案的故障注入开关直接复现，其余对应正文各条规则。

1. **不稳定 ID 导致修饰累积 ("数值越来越大")**。触发：`stats.getMaxSpeed().modifyPercent("GearIn_speed_" + counter++, v)` 每帧调用 (把 `PROBE_VOLATILE_ID_FAULT` 改为 `true` 即复现)：
   - 可观察：`entries percent` 每帧 +1，`percent` 合计与 `modified` 持续漂移；战斗结束前条目数已达数百；`unmodify("GearIn_speed_" + 0)` 只清第一条，`modified` 不回 base。
   - 修正：ID 用常量 (规则 2)；同 ID 每帧重复是幂等的 (规则 6)。
2. **unmodify 的 ID 与 modify 不一致，解除不生效**。触发：`modifyPercent("GearIn_a_b", 25f)` 但 `unmodify("GearIn_a_c")` (或拼接字符串笔误)。可观察：`modified` 不回 base、条目残留、**没有任何报错** (静默失效)。修正：modify 与 unmodify 用同一个常量 (规则 4)。
3. **同 ID 跨类型调用产生双条目**。触发：`modifyFlat("speed_shared", 10f)` + `modifyPercent("speed_shared", 25f)` + `modifyMult("speed_shared", 1.5f)`：
   - 可观察：三条全部生效 (值 165)，但 `entries flat=1 percent=1 mult=1`—— "同 ID 覆盖" 的直觉失效，改 flat 值不影响 percent 条目。
   - 修正：理解三张表独立 (规则 3)，需要整体管理时用 `unmodify(source)` 一次清三类。
4. **两个船插共用修饰 ID，加成互相覆盖**。触发：两个船插的 `applyEffectsBeforeShipCreation` 都用 `"speed_bonus"` 做 ID。可观察：后重放者覆盖先重放者，一方加成 "消失"，无报错；生涯 stats 重建顺序变化时谁赢跟着变。修正：每个来源用自己的唯一 ID (船插直接用 `id` 参数，规则 5)。
5. **percent 陷阱：base 为 0 的 stat 用 `modifyPercent` 无效**。触发：`stats.getMaxCombatReadiness().modifyPercent(id, 50f)` (该 stat base 为 0，API javadoc 原文)。可观察：`modified` 恒为 0—— `0 + 0*50/100 = 0`：
   - 修正：用 `modifyFlat` 或 `setBaseValue` (公式推论 2)。
6. **percent 陷阱：`modifyPercent(id, -100)` 只抵消 base**。触发：`modifyPercent(id, -100f)` + `modifyFlat(id2, 10f)`。可观察：`modified = 10` 而不是 0——flat 与 percent 都作用在 base 上，`-100%` 只清零 `base * percent / 100` 一项：
   - 修正：需要真正归零时用 flat 抵消或 `setBaseValue` (公式推论 2)。
7. **stats 修改放在 `applyEffectsAfterShipCreation`，生涯不生效**。触发：船插在该回调里 `stats.getMaxSpeed().modifyPercent(...)`：
   - 可观察：战斗内生效、生涯 (舰队面板速度、货舱 / 燃料 / 补给、burn) 不生效 —— 该回调 "does not get called from the campaign"(API javadoc 原文，`com.fs.starfarer.api.combat.HullModEffect`)。
   - 修正：改 stats 一律放 `applyEffectsBeforeShipCreation` (必须遵守的规则)。
8. **`applyEffectsBeforeShipCreation` 非幂等，stats 重建后数值膨胀**。触发：回调里写叠加式逻辑 (每次调用 +10%，或每次用新 ID 追加)。可观察：生涯里改一次装配 / 进出一次舰队界面，面板数值就大一圈 ——`FleetMemberAPI.updateStats()` 每次重建 stats 并重放全部修饰，叠加式写法每次重建都再叠一层。
   - 修正：覆盖式 + 稳定 ID (必须遵守的规则)，修改可重入。
9. **战斗外拿引擎的 ship 改 stats，"改了没反应"**。触发：在生涯 / 主菜单里 `Global.getCombatEngine().getPlayerShip().getMutableStats().getMaxSpeed().modifyFlat(...)`。可观察：无报错、无效果 —— 战斗外是非 null 空引擎，改的 stats 没有任何使用方。
   - 修正：战斗内才用引擎取 ship；生涯侧 stats 从 `FleetMemberAPI` 读取 (生涯域页面完整讲解，本节不展开)。

## 总结

本节把 "改一个舰船属性" 整理成一条可复用的判断链：入口是 `ShipAPI.getMutableStats()`，每个 `MutableStat` 的 base 来自 `ship_data.csv` 列；写修饰时记住公式 `(base + base * percent / 100 + flat) * mult` —— percent 与 flat 都按 base 加算，mult 最后整体乘，读取是延迟重算，永远拿到最新值。

modifier ID 是这套体系的键。稳定唯一、按来源命名空间区分、modify 与 unmodify 复用同一个常量，覆盖、幂等与一次清除三条语义就同时成立；反过来，用帧号或随机数拼 ID、两个来源共用 ID，后果是漂移、残留与互相覆盖，而且全程没有一条报错 —— 这类故障只能靠日志里的条目数与数值发现。

生效时机按生命周期分三段：生涯侧 `FleetMemberAPI.updateStats` 整体重建并重放，战斗侧 `Ship` 构造再次重放，加入引擎时 `applyEffectsAfterShipAddedToCombatEngine` 只调一次。改 stats 一律放 `applyEffectsBeforeShipCreation` 并保持可重入；战斗内运行期修改逐帧生效，不需要刷新调用。排错时按四个问题定位：base 从哪来、写进哪张表、用哪个 ID、在哪个时机被重放 —— 本节示例把这四个问题全部做成了日志里可读的三阶段。

生涯侧 stats 的读取入口 (`FleetMemberAPI`) 与舰队成员身份，逻辑上承接本节的 "stats 入口" 主线，属生涯域页面的完整讲解范围。

本节建立的 `MutableStat` 三表模型与 modifier ID 纪律，在战斗域的时间倍率、伤害结算等数值路径上会被反复使用。

## 建议参考项

- `com.fs.starfarer.api.combat.ShipAPI`：战斗实体入口，`getMutableStats()`、`isShuttlePod()`、`isHulk()` 等读取与检查方法所在。
- `com.fs.starfarer.api.combat.MutableShipStatsAPI`：舰船 stats 集合，约 90 个 getter 返回 `MutableStat` 或 `StatBonus`。
- `com.fs.starfarer.api.combat.MutableStat`：固定 base 的数值对象，三张修饰表、重算公式与延迟重算语义所在。
- `com.fs.starfarer.api.combat.StatBonus`：以调用时 base 计算的另一族修饰 (射程 / 弹药 / CR)。
- `com.fs.starfarer.api.combat.HullModEffect`：`applyEffectsBeforeShipCreation` 等三个 stats 修饰时机。
- `com.fs.starfarer.api.combat.CombatEngineAPI`：`getTimeMult()` 返回 `MutableStat`；本节还用 `getTotalElapsedTime(boolean)` 做阶段判断。
- `com.fs.starfarer.api.combat.BaseEveryFrameCombatPlugin`：示例插件继承的基类。
- `data/hulls/ship_data.csv`：`GearIn_artizan` 注册行及 max speed / max flux / flux dissipation 数值列。
- `Starsector/mods/GearIndustry/`：示例 MOD 根目录 (构建命令执行位置)。
- `Starsector/starsector-core/starsector.log`：游戏运行日志 (验证方案的日志核对位置)。
