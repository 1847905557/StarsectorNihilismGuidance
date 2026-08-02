# 每帧回调、暂停守卫与基础 AI

本节把此前建立的回调模型落到两个游戏内完整流程上：一是用 `data/config/settings.json` 的 `plugins` 段注册一个每帧战斗插件（示例所有权：`GearInPauseAwarePlugin`），用 `CombatEngineAPI.isPaused()` 守卫状态推进；二是用 `ModPlugin.pickShipAI(...)` 给指定舰船接入一个自定义 `ShipAIPlugin`（示例所有权：`GearInCruiseAI`）。两个示例都只依赖 0.98a-RC8 的公开 API 面。

*本节预期阅读时间：~20 分钟。*

*本节预期操作时间：~30 分钟。*

## 学习目标

完成后，您将能够：

- 在 MOD 的 `data/config/settings.json` 的 `plugins` 段登记一个每帧战斗插件类，并说出从合并读取、类名注册到每场战斗实例化的完整链路；
- 说出 `advance(float amount, List<InputEventAPI> events)`、`processInputPreCoreControls(...)` 与两个 `render...` 回调在每帧中的先后位置，以及暂停期间哪些回调仍被执行；
- 写出带暂停守卫的最小 `advance`：状态推进之前必须检查 `engine.isPaused()`，并说明缺失守卫时的可观察结果；
- 用 `ModPlugin.pickShipAI(FleetMemberAPI, ShipAPI)` 为指定舰船挂接自定义 `ShipAIPlugin`，说出 `PluginPick` 优先级语义、默认 AI 回退与玩家输入优先；
- 用 `starsector.log`、浮动文字与模拟战行为验证插件与 AI 是否被使用方调用。

## 前置知识

本节建立在以下前置内容之上：

- MOD 目录、`mod_info.json` 与 `GearIn_` 前缀约定（S01-P01）；
- 舰船数据行与 `ship_data.csv`（S01-P02）；
- 类、接口、回调、三类注册入口与暂停语义（S02-P01）；
- 数据行 + 脚本类的最小模式（S02-P02）；
- 战术系统的每帧状态回调（S02-P03）。

前置章节已经说明：战斗暂停期间每帧回调仍会被调用，状态推进必须由插件自行守卫。本节给出守卫的完整示例，并把“注册入口 → 游戏创建实例 → 生命周期回调”的三段模型用在战斗插件与舰船 AI 两处。

## 概念：每帧插件与暂停守卫

`EveryFrameCombatPlugin` 是战斗内每帧回调的接口。前置章节讲过它没有暂停参数，暂停状态只能从 `CombatEngineAPI.isPaused()` 读取。本节补上两个运行时事实：插件实例**每场战斗创建一次**，以及每帧回调的**先后顺序**。

- 登记在 `settings.json` `plugins` 段的每个插件类，每场战斗开始时由游戏按类名创建**新实例**并附加（脚本类加载器按类名解析后调用 `newInstance()`，见文件与 API 链路）。战斗结束引擎实例销毁，插件实例随之失效（S02-P01 的“状态放在哪里”）。
- 每帧回调顺序：
   - `processInputPreCoreControls(amount, events)`（核心控制处理前）→ `advance(amount, events)`（引擎推进）→ `renderInWorldCoords(viewport)` / `renderInUICoords(viewport)`（渲染阶段，世界坐标与 UI 坐标两个空间）。
   - 暂停分支里 `advance` 照常被调用，`amount` 也仍然非零；游戏不会自动跳过暂停帧。

::: caution 插件实例禁止跨战斗缓存
settings `plugins` 登记的插件每场战斗创建新实例，战斗结束随引擎销毁。**禁止把插件实例缓存到 `static` 字段或战斗外对象**——把实例留着给第二场战斗用，`elapsed` 会从上一场的值继续涨（或字段为 null 抛错）。
:::

`init(CombatEngineAPI engine)` 在 0.98a-RC8 已标记废弃：API 注释明确“不保证在 `advance()` 之前被调用”。**不得依赖 `init()` 初始化字段**——初始状态必须在构造器或注册点设置（「生命周期与回调时机」主题的完整版本见本节）。

## 文件与 API 链路

### 注册：settings.json 的 plugins 段

1. 游戏启动时读取 `data/config/settings.json`，把所有来源（游戏本体与每个已启用 MOD 的同一路径文件）**深合并**成一份 JSON（文本读取工具按对象字段递归合并、标量后写覆盖先写）。

   因此 MOD 只需提供自己的 `data/config/settings.json`，其中写自己的 `plugins` 条目，其余段沿用本体的值。
2. 加载期遍历合并后 `plugins` 对象的全部值（类名），逐个注册为脚本类。
3. 每场战斗开始时，游戏为每个注册的 `EveryFrameCombatPlugin` 类创建**新实例**，包装为地形插件加入战斗地图的地形列表。

   随后战斗地图初始化时对每个地形调用 `init`（此时插件收到 `engine`）。
4. 每帧：战斗地图在核心控制处理前调用插件的 `processInputPreCoreControls`，随后在推进阶段调用 `advance`（事件列表被复制为不可变列表传入）。

   渲染阶段由 `renderBackground`（UI 坐标）与 `renderForeground`（世界坐标）调用两个 `render...`。

::: caution `plugins` 段两条必须遵守的规则

**规则一：`plugins` 的 key 必须全局唯一。**

合并时同一 key 后读来源覆盖先读来源，key 与其他来源重复时，被登记的不是本 MOD 的类。可观察结果：本 MOD 的插件行为消失（或出现两次），且通常没有报错。原版 key（`coreLifecyclePlugin`、`genericCombatPlugin` 等）**禁止**占用。

**规则二：`plugins` 的值必须是完整类名（包名.类名），且类必须存在。**

类名解析失败时游戏抛出 `RuntimeException("Problem loading class [...]")`。类名拼写正确但文件路径与包名不符（例如放进 `src\` 下），类加载静默失败，插件没有任何行为。
:::

对应 API：

- `EveryFrameCombatPlugin`（四个回调 + 继承的 `init`）；
- `BaseEveryFrameCombatPlugin`（全部空实现的基类，**建议继承它**）；
- `CombatEngineAPI.addPlugin(EveryFrameCombatPlugin)` / `removePlugin(...)`。

`addPlugin(...)` 是**战斗内**注册入口（区别于 settings 登记）：`CombatEngineAPI.addPlugin` 在调用内立即执行 `plugin.init(engine)` 并把插件加入地形列表；`removePlugin(...)` 从列表移除，可提前摘除插件。

`addPlugin` 由运行中的战斗上下文调用（例如另一个插件在 `advance` 里注册）。

原版 `GenericCombatPlugin`（登记在 settings `plugins` 段的 `genericCombatPlugin`）就是用它拿一次引擎引用后立刻 `removePlugin(this)` 的原版实例。

### 暂停语义（0.98a-RC8）

暂停时（`CombatEngineAPI.isPaused()` 为真）游戏仍推进战斗地图并调用插件的 `advance`，因此插件 `advance` 在暂停期间**每帧照常执行**。

`getElapsedInLastFrame()` 的 API 注释也明确暂停时返回的是**当前帧**的值而不是 0。

游戏只保证“战斗逻辑不推进”（舰船、系统状态机等），插件自己的状态推进是插件自己的责任。

::: caution 状态推进必须先过暂停守卫
**任何会推进状态、累计时间或修改数值的代码，在 `advance` 里必须先检查 `engine.isPaused()`。** 可观察结果：不做暂停检查时，暂停期间 `elapsed` 照常累计，解除暂停后数值已经偏了——本节示例的 `calls` 与 `elapsed` 两个计数就是为直接观察这一条设计的。
:::

### 基础 AI 链路

普通舰船与战机各有核心内置的默认 AI。MOD 替换 AI 的正式入口是 `ModPlugin.pickShipAI(FleetMemberAPI member, ShipAPI ship)`：

1. 舰船被创建/部署时（`ShipAPI.setDefaultAI`、`CombatFleetManagerAPI` 部署流程等），游戏询问各启用 MOD 的 `ModPlugin`。
2. 对每个 `modPlugin` 调用 `pickShipAI`，收集所有非 `null` 的返回；返回类型是 `PluginPick<ShipAIPlugin>`——一个 `plugin` 加一个 `PickPriority`。
3. 收集到的多个 pick 按 `PickPriority` 枚举顺序（`CORE_GENERAL < MOD_GENERAL < CORE_SET < MOD_SET < CORE_SPECIFIC < MOD_SPECIFIC < HIGHEST`）取最高者；**同一优先级下多个 MOD 都返回非 null 时，取谁未定义**（引擎对相同优先级不做稳定区分）。
4. 全部 MOD 都返回 `null` 时回退核心默认 AI（普通舰船与战机各有内置实现）。

`member` 参数可为 `null`（`ModPlugin` API 注释）；`ship` 不可为 `null`，且可能是一艘战机。

注意 `ShipAPI.setShipAI(ShipAIPlugin)` 的 API 注释：`getShipAI()` 返回的不是传入的实例，而是它的包装器；传入的实例仍会收到全部回调（内部包装器转发 `advance` 等），不需要为此写额外代码。

`ShipAPI.resetDefaultAI()` 直接换回核心实现，不经过 MOD 的 `pickShipAI`。

对应 API：

- `ShipAIPlugin`（`advance(float)`、`forceCircumstanceEvaluation()`、`setDoNotFireDelay(float)`、 `needsRefit()`、`getAIFlags()`、`cancelCurrentManeuver()`、`getConfig()`、`setTargetOverride(ShipAPI)`）；
- `ShipAIConfig`（`alwaysStrafeOffensively`、`backingOffWhileNotVentingAllowed`、`turnToFaceWithUndamagedArmor`、 `burnDriveIgnoreEnemies`、`personalityOverride`）；
- `ShipwideAIFlags`（`setFlag` / `hasFlag` / `getCustom`，默认持续 `FLAG_DURATION = 0.5` 秒后自动清除）；
- `ShipCommand`（`ACCELERATE`、`DECELERATE`、`STRAFE_LEFT`、 `STRAFE_RIGHT`、`TURN_LEFT`、`TURN_RIGHT`、`TOGGLE_SHIELD_OR_PHASE_CLOAK`、`TOGGLE_AUTOFIRE`、`FIRE`、 `VENT_FLUX`、`USE_SYSTEM`、`PULL_BACK_FIGHTERS`、`HOLD_FIRE` 等）。

AI 输出指令的唯一通道是 `ShipAPI.giveCommand(ShipCommand command, Object param, int groupNumber)`，API 注释明确它**只应被 `ShipAIPlugin` 调用**。

玩家直接控制的舰船由玩家输入驱动，**不要**给玩家正在控制的船挂自定义 AI 做演示。

## 完整最小例：GearInPauseAwarePlugin

给 `GearIndustry` 做一个每帧战斗插件：统计“非暂停时间”累计秒数（`elapsed`）与回调总次数（`calls`），每累计 10 秒非暂停时间向日志输出一次，并给玩家舰船挂一条浮动文字。暂停期间 `calls` 继续增长、`elapsed` 冻结——这正是暂停守卫的可观察结果。

### 第一步：注册到 settings.json

文件：`Starsector\mods\GearIndustry\data\config\settings.json`（MOD 中不存在就新建，只写自己的 `plugins` 条目）：

```json
{
    "plugins": {
        "gearInPauseAwarePlugin": "data.scripts.gearin.combat.GearInPauseAwarePlugin"
    }
}
```

要点：

- 文件路径与核心相同（`data/config/settings.json`），游戏启动时深合并；**不要**把核心 `settings.json` 整体复制进 MOD——那样会覆盖其他来源的合并。
- key（`gearInPauseAwarePlugin`）全局唯一，带 `GearIn_` 前缀的可读命名同理（规则一）。
- 值为插件类的完整类名，必须与下面的包名 + 类名完全一致（规则二）。

### 第二步：插件类

文件：`Starsector\mods\GearIndustry\data\scripts\gearin\combat\GearInPauseAwarePlugin`（fixture 中位于 `tutorial-fixtures/units/S02-P04/src/main/java/data/scripts/gearin/combat/`，内容一致）。

游戏启动时按类名在 MOD 根目录解析对应源文件并即时编译；后续章节起教程改为编译进 JAR，类名规则不变。

```java
package data.scripts.gearin.combat;

import java.awt.Color;
import java.util.List;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.BaseEveryFrameCombatPlugin;
import com.fs.starfarer.api.combat.CombatEngineAPI;
import com.fs.starfarer.api.input.InputEventAPI;

/**
 * 每帧战斗插件：演示暂停守卫与状态归属。
 *
 * <p>注册：MOD 的 data/config/settings.json 的 plugins 段写入本类完整类名。
 * 游戏启动时把核心与所有 MOD 的 settings.json 深合并（文本读取工具.readJSONMerged），
 * 并把 plugins 段全部类名注册为脚本类；每场战斗开始时为其中每个
 * EveryFrameCombatPlugin 类创建新实例并附加（脚本类加载器.loadScriptInstance 的
 * newInstance），战斗结束实例随引擎销毁，不得缓存到下一场战斗。</p>
 *
 * <p>advance() 在战斗暂停期间仍然每帧被调用，amount 也仍然非零；是否推进状态
 * 由插件自己决定：本例只用非暂停帧累加 elapsed，暂停帧只累计调用次数 calls。
 * 初始状态在构造器设置，不依赖已废弃的 init()（不保证在第一次 advance() 之前调用）。</p>
 */
public class GearInPauseAwarePlugin extends BaseEveryFrameCombatPlugin {

    /** 非暂停时间每累计多少秒输出一次报告。 */
    public static final float REPORT_INTERVAL = 10f;

    private CombatEngineAPI engine;
    private float elapsed;
    private float timeSinceLastReport;
    private int calls;
    private int pausedCalls;

    public GearInPauseAwarePlugin() {
        // 初始状态在这里设置；init() 已废弃，不保证在第一次 advance() 之前被调用
    }

    @Override
    public void advance(float amount, List<InputEventAPI> events) {
        calls++;
        if (engine == null) {
            engine = Global.getCombatEngine();
            if (engine == null) {
                return;
            }
        }
        if (engine.isPaused()) {
            pausedCalls++;
            return;
        }
        elapsed += amount;
        timeSinceLastReport += amount;
        if (timeSinceLastReport >= REPORT_INTERVAL) {
            timeSinceLastReport = 0f;
            report(engine);
        }
    }

    private void report(CombatEngineAPI engine) {
        String text = "GearIn: elapsed " + Math.round(elapsed) + "s, calls " + calls
                + " (paused " + pausedCalls + ")";
        Global.getLogger(GearInPauseAwarePlugin.class).info(text);
        if (engine.getPlayerShip() != null) {
            engine.addFloatingText(engine.getPlayerShip().getLocation(), text,
                    14f, Color.WHITE, engine.getPlayerShip(), 0f, 3f);
        }
    }
}
```

逐段说明：

- `extends BaseEveryFrameCombatPlugin`：只覆写 `advance`；其余回调（`processInputPreCoreControls`、两个 `render...`、`init`）沿用基类空实现。
- `engine` 字段**用到时才获取**：第一次 `advance` 里用 `Global.getCombatEngine()` 取得并缓存。这正是对 `init()` 废弃注释的响应——不假设 `init` 在 `advance` 之前被调用（S02-P01「生命周期与回调时机」已说明 `init` 之后 `advance` 可能先到）。战斗外 `Global.getCombatEngine()` 返回的是非 null 空引擎（S02-P01），仅资源加载期为 `null`；判空只是防御性检查。
- 暂停守卫：`if (engine.isPaused()) { pausedCalls++; return; }` 是本节的核心。`calls` 在守卫**之前**递增，因此暂停帧也被计数；`elapsed` 与 `timeSinceLastReport` 只走非暂停分支。游戏不自动跳过暂停帧，这两组计数就是给“暂停期间回调照常执行”留的观测点。
- `report`：`Global.getLogger(...)` 输出到 `starsector.log`；`engine.addFloatingText(...)` 在玩家舰船位置显示浮动文字（`attachedTo` 让文字跟随舰船移动，`flashDuration` 3 秒）。
- 初始状态在构造器设置；字段全部为实例字段，随插件实例（每场战斗一个）生存。

### 验证方案（基于本例）

前置数据：`data/config/settings.json`（`plugins` 段，key `gearInPauseAwarePlugin`）与本示例的插件类。

操作：

1. 启动游戏确认能进入主菜单（验证 settings.json 合并与类名注册不报错；若报 `Problem loading class`，见故障分析第 2 条）。
2. 控制台生成 `GearIn_artizan` 进入战斗模拟：约 10 秒后 `starsector.log` 出现 `GearIn: elapsed ...`，玩家舰船位置出现一行白色浮动文字，约 3 秒后消失。
3. 暂停约 20 秒再恢复：确认暂停期间没有新报告，恢复后第一次报告的 `elapsed` 与暂停前连续（暂停的 20 秒没有计入），而 `calls` 明显大于按帧率推算的调用数（对照预期结果第 3 条）。
4. 可选故障注入：删掉 `advance` 里 `if (engine.isPaused()) {...return;}` 后重启进战斗，暂停 20 秒恢复，`elapsed` 应照常增长（故障分析第 3 条）；改回后恢复。
5. 退出游戏后检查 `Starsector\starsector-core\starsector.log`：存在 `GearIn: elapsed ...`，无与 `GearInPauseAwarePlugin` 相关的 `ERROR`。

通过信号：日志与浮动文字出现，且数值符合暂停语义——暂停期间 `elapsed` 冻结、`calls` 继续增长。

清理：移除 `settings.json` 的 `plugins` 条目即可恢复原状（教程虚构 MOD，无需额外清理）。

## 预期结果（GearInPauseAwarePlugin）

1. 启动游戏进入战斗模拟（有玩家舰船），日志出现 `GearIn: elapsed ...s, calls ... (paused 0)`；同一时刻玩家舰船位置出现一行白色浮动文字，约 3 秒后消失。
2. 非暂停运行约 15 秒：出现约 1–2 次报告，`elapsed` 与真实战斗时间一致（误差为报告间隔取整）。
3. 按 ESC（或打开命令界面）暂停约 20 秒：**没有**新报告；恢复后第一次报告的 `elapsed` 与暂停前连续 （暂停的 20 秒没有计入），而 `calls` 明显大于按帧率推算的调用数——暂停期间 `advance` 一直在被调用，只是被守卫拦下了。
4. `starsector.log` 中没有与 `GearInPauseAwarePlugin` 相关的 `ERROR`。

## 速查表：常用 ShipCommand 与回调

| ShipCommand | 效果 |
|---|---|
| `ACCELERATE` / `DECELERATE` / `ACCELERATE_BACKWARDS` | 前进 / 减速 / 后退 |
| `STRAFE_LEFT` / `STRAFE_RIGHT` | 左右平移 |
| `TURN_LEFT` / `TURN_RIGHT` | 转向 |
| `TOGGLE_SHIELD_OR_PHASE_CLOAK` | 开/关护盾（相位舰为相位斗篷）|
| `TOGGLE_AUTOFIRE` / `FIRE` / `HOLD_FIRE` | 武器组自动开火 / 开火 / 停火 |
| `VENT_FLUX` | 排散幅能 |
| `USE_SYSTEM` | 激活战术系统 |
| `PULL_BACK_FIGHTERS` | 召回战机 |

每帧插件回调（`EveryFrameCombatPlugin`，0.98a-RC8）：

| 方法 | 时机 |
|---|---|
| `processInputPreCoreControls(float, List<InputEventAPI>)` | 核心控制处理之前 |
| `advance(float, List<InputEventAPI>)` | 引擎推进阶段，暂停期间也调用 |
| `renderInWorldCoords(ViewportAPI)` | 渲染阶段，世界坐标空间 |
| `renderInUICoords(ViewportAPI)` | 渲染阶段，UI 坐标空间（叠加在界面上）|
| `init(CombatEngineAPI)` | **已废弃**；不保证在 `advance` 前调用，不要用它初始化 |

## 第二个完整示例：GearInCruiseAI（基础 AI）

做一个最小巡航 AI：不瞄准、不转向，只在直行方向加减速——幅能正常时持续 `ACCELERATE`，超载或排散时 `DECELERATE`。它演示 `ShipAIPlugin` 的完整方法集与两个“必须自己维护”的点。

文件：`Starsector\mods\GearIndustry\data\scripts\gearin\combat\GearInCruiseAI`（fixture 中位于 `tutorial-fixtures/units/S02-P04/src/main/java/data/scripts/gearin/combat/`，内容一致）。

```java
package data.scripts.gearin.combat;

import com.fs.starfarer.api.combat.ShipAIConfig;
import com.fs.starfarer.api.combat.ShipAIPlugin;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.combat.ShipCommand;
import com.fs.starfarer.api.combat.ShipwideAIFlags;
import com.fs.starfarer.api.combat.ShipwideAIFlags.AIFlags;

/**
 * 最小巡航 AI：不瞄准、不转向，只在直行方向上加减速。
 *
 * <p>挂接：在 MOD 的 ModPlugin.pickShipAI(FleetMemberAPI, ShipAPI) 里对本船返回
 * new PluginPick&lt;&gt;(new GearInCruiseAI(ship), PickPriority.MOD_GENERAL)，其余船返回
 * null 继续使用默认 AI（舰船 AI / 战机 AI）。ModPlugin 的完整实装见 S06-P01、
 * S06-P02。玩家直接控制的舰船不建议挂自定义 AI：玩家输入优先。</p>
 *
 * <p>两个必须自己维护的点：</p>
 * <ul>
 *   <li>ShipwideAIFlags 的自动过期由 advance() 驱动（默认 AI 在自己的 advance() 里调用
 *       flags.advance(amount)），自定义 AI 必须每帧自己调用，
 *       否则 setFlag 的 flag 永不自动清除；</li>
 *   <li>指令只能通过 ShipAPI.giveCommand(...) 发出，该方法只应被 ShipAIPlugin 调用。</li>
 * </ul>
 */
public class GearInCruiseAI implements ShipAIPlugin {

    private final ShipAPI ship;
    private final ShipAIConfig config = new ShipAIConfig();
    private final ShipwideAIFlags flags = new ShipwideAIFlags();

    public GearInCruiseAI(ShipAPI ship) {
        this.ship = ship;
    }

    @Override
    public void advance(float amount) {
        flags.advance(amount);
        if (!ship.isAlive() || ship.isHulk()) {
            return;
        }
        if (ship.getFluxTracker().isOverloadedOrVenting()) {
            ship.giveCommand(ShipCommand.DECELERATE, null, 0);
            return;
        }
        flags.setFlag(AIFlags.CUSTOM1);
        ship.giveCommand(ShipCommand.ACCELERATE, null, 0);
    }

    @Override
    public void forceCircumstanceEvaluation() {
        // 本 AI 每帧评估，无需周期外的强制刷新
    }

    @Override
    public void setDoNotFireDelay(float amount) {
        // 本 AI 不涉及开火
    }

    @Override
    public boolean needsRefit() {
        // 仅战机联队成员查询；本 AI 用于普通舰船
        return false;
    }

    @Override
    public ShipwideAIFlags getAIFlags() {
        return flags;
    }

    @Override
    public void cancelCurrentManeuver() {
        // 本 AI 没有需要取消的机动
    }

    @Override
    public ShipAIConfig getConfig() {
        return config;
    }
}
```

逐段说明：

- `implements ShipAIPlugin`：接口的全部方法都必须实现（`setTargetOverride` 有 `default` 实现，可以不覆写）。`ShipAIPlugin` 没有空实现基类，这一点与 `EveryFrameCombatPlugin`（有 `BaseEveryFrameCombatPlugin`）不同。
- `advance(float amount)`：AI 的主循环，每帧由引擎调用。第一行 `flags.advance(amount)` 驱动 AI 旗标过期——`ShipwideAIFlags` 的默认持续 `FLAG_DURATION = 0.5` 秒，**自定义 AI 必须自己调用 `advance` 驱动清除**，引擎不会自动做（核心默认 AI 在自己的 `advance` 里就是这么做的）。
- `giveCommand(ShipCommand.ACCELERATE, null, 0)`：输出指令。`param` 一般是鼠标位置之类的上下文，本示例不需要传 `null`；`groupNumber` 只用于武器组相关指令，填 `0`。API 注释明确这个方法**只应被 `ShipAIPlugin` 调用**。
- `getAIFlags()`：返回本 AI 的旗标实例。引擎侧 `ShipAPI.getAIFlags()` 对自定义 `ShipAIPlugin` 直接返回 `ai.getAIFlags()`——所以其他读取该船旗标的系统，看到的就是该 AI 返回的这份。`CUSTOM1` 等 `CUSTOM1..CUSTOM5` 是留给 MOD 的通用旗标。
- `getConfig()`：返回 `ShipAIConfig`。默认字段值即“没有特别指示”，本示例不改任何字段。
- `needsRefit()`：只对战机联队成员查询，普通舰船返回 `false`。

::: warning 自定义 AI 的两个易错点
忘记在 `advance` 里调用 `flags.advance(amount)` 时，`setFlag` 的旗标永不自动清除（默认持续 `FLAG_DURATION = 0.5` 秒后过期）。

在 `ShipAIPlugin` 之外（例如每帧插件里）调用 `giveCommand` 时，指令不产生可观察效果——该 API 只面向 AI 调用方。
:::

### 挂接：ModPlugin.pickShipAI

把巡航 AI 接到此前章节制作的 `GearIn_artizan`（工匠-级）上。在 MOD 的 ModPlugin 类中覆写（**片段**，完整的 modPlugin 注册与 JAR 实装见后续相应章节的讲解）：

```java
// 在 MOD 的 ModPlugin 类中覆写（片段；完整 modPlugin 实装见后续相应章节）：
@Override
public PluginPick<ShipAIPlugin> pickShipAI(FleetMemberAPI member, ShipAPI ship) {
    if ("GearIn_artizan".equals(ship.getHullSpec().getHullId())) {
        return new PluginPick<ShipAIPlugin>(new GearInCruiseAI(ship), PickPriority.MOD_GENERAL);
    }
    return null; // 其余舰船继续使用默认 AI（舰船 AI / 战机 AI）
}
```

要点：

- 按 `ship.getHullSpec().getHullId()` 过滤，其余船返回 `null` 使用默认 AI。**不要**对不认识的船返回自定义 AI——默认 AI 的边界回避、目标选择等行为是舰船行为的一部分，替换掉它等于替换舰船的整套行为。
- `PickPriority.MOD_GENERAL` 是 MOD 替换“一类场景”的标准档位；更高优先级（`MOD_SPECIFIC`、`HIGHEST`）留给特定遭遇与合包冲突处理（完整优先级语义见 `CampaignPlugin.PickPriority` 注释）。
- 玩家直接控制的舰船由玩家输入驱动，AI 的 `giveCommand` 不是玩家操作；**演示本示例请使用 AI 控制的舰船**（敌方或友军）。

### 验证方案（基于本例）

前置数据：`GearInCruiseAI` 类与 ModPlugin 中的 `pickShipAI` 片段（挂在 `GearIn_artizan` 上）。

操作：

1. 部署一艘 AI 控制的 `GearIn_artizan`（敌方或友军侧，非玩家直接控制）：该船出生后保持当前朝向直线加速，不转向、不规避、不开火（对照预期结果第 1 条）。
2. 观察约 20 秒：该船持续加速直到越过地图边界，之后被引擎按撤退处理移出战斗（对照预期结果第 2 条）。
3. 可选：给该船装配会持续产生幅能的武器并让其开火，幅能超载（或排散）期间它发出 `DECELERATE`，速度下降；超载结束后恢复 `ACCELERATE`（对照预期结果第 3 条）。

通过信号：该船行为与同场战斗中默认 AI 的敌舰明显不同——默认 AI 会转向、开火与规避，这就是“替换整套行为”的直观差异。

清理：移除 ModPlugin 中的 `pickShipAI` 片段即可恢复原状（教程虚构 MOD，无需额外清理）。

## 预期结果（GearInCruiseAI）

1. 部署一艘由 AI 控制的 `GearIn_artizan`（敌方或友军侧，非玩家直接控制）：该船出生后保持当前朝向直线加速，不转向、不规避、不开火。
2. 观察约 20 秒：该船持续加速直到越过地图边界，之后被引擎按撤退处理移出战斗。
3. 可选：给该船装配会持续产生幅能的武器并让其开火，幅能超载（或排散）期间它发出 `DECELERATE`，速度下降；超载结束后恢复 `ACCELERATE`。
4. 该船不参与任何目标选择——对比同场战斗中默认 AI 的敌舰会转向、开火与规避，这就是“替换整套行为”的直观差异。

## 实例驱动故障分析

每条给出触发条件、可观察结果与修正。

::: tip 排错顺序
先沿注册入口确认使用方拿到了您的对象（日志 / 浮动文字），再核对类名与路径，再检查暂停守卫与状态归属，最后核对 AI 的挂接条件与优先级。
:::

1. **插件没有任何行为。**
   - 触发：`settings.json` 没写 `plugins` 条目、类名写错、或 key 与其他来源重复（规则一、二）。
   - 可观察：日志没有 `GearIn: elapsed ...`；key 重复时多半无报错。
   - 修正：确认文件在 MOD 的 `data/config/settings.json`（不是 `data/scripts/`），key 带唯一前缀，值与本类包名 + 类名逐字符一致。
2. **`Problem loading class [...]` 异常。**
   - 触发：`plugins` 值类名与类不存在或包名不符。
   - 可观察：加载或战斗开始时抛 `RuntimeException("Problem loading class [data.scripts.gearin.combat.GearInPauseAwarePlugin]")`。
   - 修正：类名与包声明一致，文件位于 MOD 根目录 `data/scripts/gearin/combat/`（放进 `src\` 不会被脚本加载器解析）。
3. **暂停期间状态照常推进。**
   - 触发：`advance` 里在检查 `engine.isPaused()` 之前就累加/修改状态。
   - 可观察：暂停 20 秒后 `elapsed` 也增加了约 20 秒（对比 `calls` 与 `elapsed` 的增长速率，两者不再同幅）。
   - 修正：状态推进代码必须放在守卫之后；守卫只拦状态，不拦“计数调用次数”这类无副作用操作 （示例中的 `calls++` 在守卫前是故意的）。
4. **第一次 `advance` 抛空指针异常。**
   - 触发：在 `init(engine)` 里保存 `engine` 并假设它先于 `advance` 被调用。
   - 可观察：第一次 `advance` 时 `engine` 为 `null`，`engine.isPaused()` 抛 NPE；异常也可能发生在 `init` 之后 `advance` 之前（`init` 不保证调用顺序）。
   - 修正：初始状态放构造器；引擎引用在 `advance` 第一帧用 `Global.getCombatEngine()` 获取（用到时才取，示例做法），不要依赖 `init`。
5. **上一场战斗的状态残留。**
   - 触发：把插件实例缓存到 `static` 字段或战斗外对象，第二场战斗继续用。
   - 可观察：第二场战斗的 `elapsed` 从上一场的值继续涨（或字段为 null 抛错）。
   - 修正：settings `plugins` 登记的方式每场战斗自动创建新实例；不要手动缓存实例（S02-P01 的“状态放在哪里”）。
6. **自定义 AI 没有生效（舰船仍是默认行为）。**
   - 触发：`pickShipAI` 没被覆写、返回了 `null`、类名/包名不一致、或同优先级下被其他 MOD 的 pick 覆盖（未定义行为）。
   - 可观察：该船照常转向、规避、开火（默认 AI 行为），日志无报错。
   - 修正：确认覆写生效（先给所有船返回自定义 AI 验证一次）；同优先级冲突按“优先级更高者胜”调整，或改用 `MOD_SET`/`MOD_SPECIFIC`。
7. **`setFlag` 的旗标永不自动清除。**
   - 触发：自定义 AI 忘记在 `advance` 里调用 `flags.advance(amount)`。
   - 可观察：`hasFlag(CUSTOM1)` 永远为真——旗标本应在 `FLAG_DURATION`（0.5 秒）后自动过期。
   - 修正：`advance` 第一行调用 `flags.advance(amount)`（示例做法）。
8. **`giveCommand` 无效。**
   - 触发：在 `ShipAIPlugin` 之外（例如每帧插件里）对舰船调用 `giveCommand`。
   - 可观察：指令不产生可观察效果（该 API 只面向 AI 调用方）。
   - 修正：把行为放进 AI 的 `advance`；给玩家直接控制的船发指令同样无效，玩家输入优先。

## 总结

本节把“注册入口 → 游戏创建实例 → 生命周期回调”的三段模型落到两个战斗侧完整流程上：

- **每帧插件**：`data/config/settings.json` 的 `plugins` 段（key 唯一、值为完整类名）→ 加载期注册类名 → 每场战斗 `newInstance` 并包装为地形插件 → 战斗地图每帧回调（暂停期间照常）。
- **暂停守卫**：状态推进之前必须 `engine.isPaused()`，初始状态放构造器、不依赖废弃的 `init`。
- **基础 AI**：`ModPlugin.pickShipAI` 返回 `PluginPick`（`null` = 用默认 AI，同优先级取谁未定义），自定义 `ShipAIPlugin` 自己驱动旗标过期、通过 `giveCommand` 输出指令。

排错顺序：先沿注册入口确认使用方拿到了您的对象（日志 / 浮动文字），再核对类名与路径，再检查暂停守卫与状态归属，最后核对 AI 的挂接条件与优先级。这套三段模型同样适用于生涯侧的对象——对象不同，注册入口与生命周期随之不同，而“谁在什么时机被调用、状态归谁管”的提问方式保持不变。

## 建议参考项

- `com.fs.starfarer.api.combat.EveryFrameCombatPlugin`：每帧插件回调接口（`advance`、`processInputPreCoreControls`、两个 `render...`，暂停期间照常被调用）。
- `com.fs.starfarer.api.combat.BaseEveryFrameCombatPlugin`：全部空实现的基类，示例继承它只覆写 `advance`。
- `com.fs.starfarer.api.combat.CombatEngineAPI`：`isPaused()`、`getElapsedInLastFrame()`（暂停时返回当前帧值）、`addPlugin` / `removePlugin`、`getPlayerShip()`、`addFloatingText(...)`。
- `com.fs.starfarer.api.Global`：`getLogger(Class)`（输出到 `starsector.log`）、`getCombatEngine()` （战斗外返回非 null 空引擎，仅资源加载期为 `null`）。
- `com.fs.starfarer.api.ModPlugin`：`pickShipAI(FleetMemberAPI, ShipAPI)`（`member` 可空、`ship` 不可空、可为战机）与 `pickDroneAI` / `pickMissileAI`。
- `com.fs.starfarer.api.PluginPick`：`plugin` + `PickPriority` 的返回值包装。
- `com.fs.starfarer.api.campaign.CampaignPlugin`：`PickPriority` 枚举（7 档，同优先级取谁未定义）。
- `com.fs.starfarer.api.combat.ShipAIPlugin` / `ShipAIConfig` / `ShipwideAIFlags` / `ShipCommand`：自定义 AI 的接口、配置、旗标（`FLAG_DURATION = 0.5` 秒自动清除）与指令枚举。
- `com.fs.starfarer.api.combat.ShipAPI`：`giveCommand(...)`（只应由 `ShipAIPlugin` 调用）、`setShipAI` （返回包装器）、`resetDefaultAI`、`getAIFlags`。
- `com.fs.starfarer.api.combat.FluxTrackerAPI`：`isOverloadedOrVenting()`。
- `data/config/settings.json`：`plugins` 段注册入口（游戏本体与所有 MOD 同路径文件深合并，key 全局唯一）。
- `Starsector/starsector-core/starsector.log`：运行日志，插件 `Global.getLogger(...)` 的输出位置。
