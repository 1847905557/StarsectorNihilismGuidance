# Java 基础：类、接口与插件

本节不要求您掌握 Java 语法体系。目标是建立一个足以阅读 Starsector 插件的心智模型：游戏拥有对象，在特定时机调用对象上的方法；您的代码通过实现约定接收这些回调。

*本节预期阅读时间：~15 分钟。*

*本节预期操作时间：~10 分钟。*

## 学习目标

完成后，您将能够：

- 分清类、实例、接口、方法、参数和字段；
- 说出 0.98a-RC8 的三类注册入口（`mod_info.json` 的 `modPlugin`、`data/config/settings.json` 的 `plugins`、战斗内 `addPlugin(...)`）和各自注册的对象；
- 沿“注册入口 → 游戏创建实例 → 生命周期回调”阅读一个最小插件，指出谁创建实例、谁调用方法、参数代表什么；
- 解释暂停语义：战斗暂停期间每帧回调仍会被调用，状态推进必须由插件自行守卫。

## 前置知识

本节建议在完成前置单元后阅读，您需要已经理解：

- 数据 ID 与使用方模型（S01-P01）：数据由 ID 标识、由使用方读取；插件只是把使用方从内置逻辑换成一个 Java 对象；
- `mod_info.json` 的 `modPlugin` 字段（S01-P01）：此前标记为“按需”，本节说明它背后的实例创建与回调机制。

## 六个词先够用

| 词 | 本教程中的含义 |
|---|---|
| 类 class | 描述对象拥有哪些数据和方法的代码模板 |
| 实例 instance | 运行时按类创建出的具体对象 |
| 接口 interface | 调用方与实现方共同遵守的方法清单 |
| 方法 method | 对象能执行的操作 |
| 参数 parameter | 调用方法时交给它的输入 |
| 字段 field | 实例在多次方法调用之间保存的状态 |

Starsector API 中常见的 `ShipAPI`、`CombatEngineAPI` 以接口暴露能力。通常接收这些接口的实例并调用方法，而不是自己 `new ShipAPI()`。

## 回调是什么

普通调用是调用方代码决定何时调用方法：

```text
您的代码 ──调用──> 某个方法
```

回调是游戏决定时机：

```text
注册入口 ──> 游戏创建实例 ──> 游戏在生命周期节点调用方法
```

战斗每帧插件、舰船与武器上的行为脚本、产业和事件处理对象形式不同，但都遵循这条链。必须先找到注册入口：单独写一个类并把它编译进 JAR，不会自动让游戏调用它。可观察结果：游戏内没有任何行为；若 `modPlugin` 指向不存在的类，启动器会报加载失败。

## 文件与 API 链路

### 本页文件

本页示例位于 MOD 源码包 `data.scripts.gearin.basics`（相对 MOD 根目录：`src/data/scripts/gearin/basics/`），由三个类协作：

- `GearInFramePlugin`（回调约定接口）；
- `GearInCounterPlugin`（暂停守卫计数器）；
- `GearInCallbackRunner`（演示运行器）。

::: info 为什么先看纯 Java 示例？

本页示例是纯 Java，不依赖 Starsector，可在任何 Java 17 环境编译运行；它用模拟的使用方演示回调链。真实的游戏对象、存档和战斗引擎在后续页面逐步接入。

:::

### 三类注册入口（0.98a-RC8）

1. `mod_info.json` 的 `modPlugin`：声明 MOD 的 `ModPlugin` 入口类。
   - 游戏启动时读取该字段并创建一个实例，在应用加载、新游戏、载入存档等生命周期节点调用其方法。
   - API 注释明确：`modPlugin` 类的新实例在游戏启动时创建；建议继承 `BaseModPlugin` 而不是直接实现 `ModPlugin`，这样 API 新增方法时会自动获得空实现而不破坏编译。
   - JAR 构建与 `modPlugin` 入口的完整实装在编译构建与 MOD 生命周期章节展开（S06-P01、S06-P02）。
2. `data/config/settings.json` 的 `plugins` 段：按“唯一 key → 完整类名”登记。
   - 该段注释声明它用于登记 `EveryFrameCombatPlugin`，是 `data/scripts/plugins` 目录的替代入口。
   - 登记在其中的每帧战斗插件类会在每场战斗被创建新实例并附加（原版 `genericCombatPlugin` 就是走这个入口）。
3. 战斗内 `CombatEngineAPI.addPlugin(...)`：在战斗上下文把插件实例加入当前战斗的每帧回调列表；`removePlugin(...)` 可提前移除，战斗结束由引擎清理。

### 生命周期与回调时机

- 每帧回调：`EveryFrameCombatPlugin.advance(float amount, List<InputEventAPI> events)`。`amount` 是上一帧经过的秒数，`events` 是本帧输入事件；RC8 的真实签名没有暂停参数。
- `CombatEngineAPI` 由引擎在战斗开始时提供，一场战斗一个实例；插件实例通常由注册点创建，随战斗结束失效，不要缓存到下一场战斗。

::: caution 不要依赖 init() 初始化字段

`init(CombatEngineAPI engine)` 在 RC8 已标记废弃：不保证在 `advance()` 之前被调用，不得依赖它初始化字段。可观察结果：第一次 `advance()` 时依赖 `init()` 的字段仍为 `null`，触发空指针异常。初始状态应在构造器或注册点设置。

:::

::: warning 判空不是战斗上下文的可靠判据

`Global.getCombatEngine()` 只在战斗运行期“有效”。

RC8 中该引擎是进程级单例：进入主菜单时即创建，战斗结束时销毁后紧邻重建，因此战斗外返回的是非 null 空引擎，仅资源加载期为 `null`。

判空不能作为战斗上下文的可靠判据，可用 `Global.getCurrentState()`（`GameState.COMBAT`）或引擎状态方法 （`isInCampaign()`/`isInCampaignSim()`/`isSimulation()`）判断。

:::

### 暂停语义

战斗暂停（用户暂停或打开命令界面）时，帧循环仍在运行：`advance()` 仍会被调用， `getElapsedInLastFrame()` 也仍返回当前帧值。游戏不会自动跳过暂停帧，必须由插件自己检查 `CombatEngineAPI.isPaused()`，在状态推进之前返回。

对比：运行期脚本（`EveryFrameScript`）用 `runWhilePaused()` 显式声明暂停时是否继续推进（脚本与暂停的完整模型在战役章节展开）；战斗插件没有这个开关，所以暂停守卫是插件的责任。

::: warning 忘记暂停守卫是常见坑

可观察结果：`advance()` 里不做暂停检查，暂停期间状态照常累计，解除暂停后数值已经偏了；`engine.isPaused()` 守卫在成熟实例模组中随处可见。

:::

## 完整最小例

下面用纯 Java 模拟游戏回调。它不依赖 Starsector，先展示接口、实例和状态如何配合。示例按“调用约定 / 实现 / 演示驱动”拆成三个小类，同在 `data.scripts.gearin.basics` 包内协作：

- `GearInFramePlugin`：回调约定接口——定义“游戏每帧会调用什么方法、参数代表什么”；
- `GearInCounterPlugin`：暂停守卫计数器——实现约定，只推进非暂停帧的状态；
- `GearInCallbackRunner`：演示运行器——模拟游戏使用方，驱动插件并打印结果。

### GearInFramePlugin：回调约定接口

每帧回调的调用约定，对应真实 API 的 `EveryFrameCombatPlugin`：游戏每帧调用 `advance`，由参数携带本帧上下文。`paused` 是教学简化，表示当前帧是否暂停。

```java
package data.scripts.gearin.basics;

/**
 * 每帧回调的调用约定。
 *
 * <p>对应真实 API 的 {@code EveryFrameCombatPlugin}：游戏每帧调用 {@code advance}，
 * 由参数携带本帧上下文。{@code paused} 是教学简化，表示当前帧是否暂停。</p>
 */
public interface GearInFramePlugin {

    /**
     * 本帧回调。
     *
     * @param amount 本帧经过的秒数
     * @param paused 当前帧是否处于暂停状态
     */
    void advance(float amount, boolean paused);
}
```

### GearInCounterPlugin：暂停守卫计数器

实现 `GearInFramePlugin`：非暂停帧把帧增量累入 `elapsed`，暂停帧直接返回，不推进状态——对应真实插件里 `if (engine.isPaused()) return;` 的守卫。

```java
package data.scripts.gearin.basics;

/**
 * 带暂停守卫的累计计数器。
 *
 * <p>实现 {@link GearInFramePlugin}：非暂停帧把帧增量累入 {@code elapsed}，
 * 暂停帧直接返回，不推进状态——对应真实插件里 {@code if (engine.isPaused()) return;} 的守卫。</p>
 */
public final class GearInCounterPlugin implements GearInFramePlugin {

    private float elapsed;

    @Override
    public void advance(float amount, boolean paused) {
        if (paused) {
            return;
        }
        elapsed += amount;
    }

    public float getElapsed() {
        return elapsed;
    }
}
```

### GearInCallbackRunner：演示运行器

模拟游戏使用方：`main` 按帧调用插件回调并打印累计结果，`runFrame()` 只依赖接口、不关心实现细节。

```java
package data.scripts.gearin.basics;

/**
 * 演示运行器：模拟游戏消费者，按帧调用插件回调并打印累计结果。
 *
 * <p>运行 {@code main} 输出 {@code 0.25}：第一帧 {@code 0.25f} 计入，
 * 第二帧以暂停状态传入 {@code 5f}，被 {@link GearInCounterPlugin} 的守卫拦下。</p>
 */
public final class GearInCallbackRunner {

    private GearInCallbackRunner() {
    }

    public static void main(String[] args) {
        GearInCounterPlugin plugin = new GearInCounterPlugin();
        runFrame(plugin, 0.25f, false);
        runFrame(plugin, 5f, true);
        System.out.println(plugin.getElapsed());
    }

    private static void runFrame(GearInFramePlugin plugin, float amount, boolean paused) {
        plugin.advance(amount, paused);
    }
}
```

对应关系：

- `GearInFramePlugin` 是调用约定；真实 API 用 `EveryFrameCombatPlugin` 接口或 `BaseEveryFrameCombatPlugin` 基类。
- `GearInCounterPlugin` 是您的实现类。
- `plugin` 是实例，`elapsed` 是它持有的状态。
- `runFrame()` 模拟游戏使用方，它只依赖接口，不关心实现细节。
- `advance()` 是回调，`amount` 与 `paused` 是本次调用的上下文。

真实 API 对照（0.98a-RC8）：

- 真实每帧回调签名是 `EveryFrameCombatPlugin.advance(float amount, List<InputEventAPI> events)`，没有 `paused` 参数；暂停状态来自 `CombatEngineAPI.isPaused()`。
- 本示例的 `paused` 参数是教学简化，对应真实战斗中“当前帧是否暂停”的状态，用来展示“暂停时状态不得推进”的规则。
- `GearInCounterPlugin` 里 `if (paused) return;` 的守卫，在真实插件里写成 `if (engine.isPaused()) return;`。

### 验证方案（基于本例）

本页没有任何游戏运行时对象（没有 `modPlugin`、没有 `settings.json` 的 `plugins` 条目、没有战斗内注册），游戏内没有可观察的验证对象；验证以编译运行替代。

- 前置数据：本页三个类——`GearInFramePlugin`、`GearInCounterPlugin`、`GearInCallbackRunner`。
- 操作：在 IDE 直接运行 `GearInCallbackRunner` 的 `main`，或 `javac` 编译后用 `java` 运行；再做一次故障注入——删除 `GearInCounterPlugin` 中 `if (paused) { return; }` 守卫后重新运行。
- 通过信号：正常版输出 `0.25`，删除守卫后输出 `5.25`，两者对比证明暂停守卫生效。
- 游戏内验证：从第一个游戏内回调示例开始，届时必须能指出注册入口、实例所有者、回调时机和暂停行为。
- 清理：无游戏内修改需要清理。

## 预期结果

运行 `GearInCallbackRunner` 的 `main`（IDE 直接运行，或 `javac` 编译后 `java` 运行），控制台必须输出：

```text
0.25
```

第一帧 `0.25f` 计入 `elapsed`；第二帧以暂停状态传入 `5f`，被守卫拦下，不得进入状态。把 `if (paused) { return; }` 删掉后输出会变成 `5.25`——这就是“实例驱动故障分析”第 3 条的可观察结果。

## 继承与实现

`implements GearInFramePlugin` 表示类承诺提供接口声明的方法。

`@Override` 让编译器检查方法签名：拼错方法名、参数类型或返回类型时必须编译失败（可观察结果：javac 报 method does not override or implement a method from a supertype），而不是静默变成一个游戏永远不会调用的新方法。

Starsector 也提供 `Base...` 基类：`BaseEveryFrameCombatPlugin` 实现全部每帧回调的空版本， `BaseModPlugin` 实现 `ModPlugin` 全部方法的空版本。

选择原则：API 要求接口时用 `implements`；提供合适基类时通常用 `extends`（建议优先继承 `Base...` 基类，API 新增方法时自动获得空实现而不破坏编译，见 `BaseModPlugin` 的 API 注释）。注意 Java 类只能继承一个父类。

## 状态放在哪里

字段随实例生存。必须先知道实例归属：

- 每艘船一个实例时，字段可保存该船状态；
- 全战斗共享一个实例时，单个字段会混合所有船；
- 战斗插件实例随注册点创建、随战斗结束清理，不要缓存到下一场战斗；
- 存档重载后仍需存在的数据必须进入存档持久化入口；普通字段和 `static` 字段都不会自动进入存档（持久化入口在战役章节展开）；
- `static` 字段属于类加载器，全进程共享，不等同于存档数据。

后续每个插件都会明确实例由谁创建、何时回调、何时销毁。

## 实例驱动故障分析

下面每条都给出触发条件、可观察结果和修正。

1. 类能编译但没有效果：缺少数据或插件注册入口。
   - 可观察：把 `GearInCounterPlugin` 编译进 JAR 而不注册，游戏内没有任何行为；`modPlugin` 指向不存在的类则加载失败。
   - 修正：先沿 `mod_info.json` 的 `modPlugin`、 `settings.json` 的 `plugins`、战斗内 `addPlugin(...)` 找到使用方如何取得类名或实例。
2. `@Override` 报错：方法签名与接口不一致。
   - 可观察：把 `GearInFramePlugin` 的 `advance(float, boolean)` 改成 `advance(float)` 后编译失败，报错指向 `@Override` 行。
   - 修正：以 RC8 API 源码为准逐项核对方法名、参数类型和返回类型。
3. 暂停时数值继续增长：回调仍在执行，但代码没有处理暂停。
   - 可观察：删除 `if (paused) return;` 后本示例输出 `5.25` 而不是 `0.25`；游戏内表现为暂停期间状态照常变化。
   - 修正：状态推进前检查暂停——本示例检查 `paused`，真实插件检查 `engine.isPaused()`。
4. 两艘船共享异常状态：状态被放进 `static` 字段或全战斗共享实例的单个字段。修正：按实体保存状态，或让每个实体拥有自己的实例。
5. 重载存档后状态消失：普通 Java 字段没有进入存档持久化模型。修正：需要跨存档的数据走存档持久化入口（持久化机制在战役章节展开）。
6. 战斗外读取战斗引擎：`Global.getCombatEngine()` 在战斗外返回的是非 null 空引擎，仅资源加载期为 `null`。
   - 引擎生命周期：进入主菜单时创建；战斗结束时销毁引擎实例后紧邻重建。
   - 可观察：非战斗场景运行的脚本里直接调用 `engine.isPaused()` 不抛空指针异常——空引擎是真实对象，`isPaused()` 只是读回布尔字段，但结果对战斗上下文无意义。
   - 修正：判空不能作为战斗上下文的可靠判据；用 `Global.getCurrentState()`（`GameState.COMBAT`）或引擎状态方法（`isInCampaign()`/`isInCampaignSim()`/`isSimulation()`）判断。

## 总结

读插件时依次问：谁注册它（`modPlugin` / `plugins` / `addPlugin`）、谁创建实例、哪个方法被回调、参数代表什么、暂停时回调是否仍被调用、字段归谁、何时清理。

这套提问顺序是后续所有插件阅读的统一框架：无论回调以接口还是基类形态出现，注册入口、实例归属与暂停守卫这三个环节始终成立，它们决定了行为何时执行、由谁执行、以什么状态执行。

## 建议参考项

- `com.fs.starfarer.api.combat.EveryFrameCombatPlugin`：每帧战斗回调接口，`advance(float amount, List<InputEventAPI> events)` 是每帧入口。
- `com.fs.starfarer.api.combat.CombatEnginePlugin`：战斗引擎级插件接口。
- `com.fs.starfarer.api.combat.BaseEveryFrameCombatPlugin`：全部每帧回调的空版本基类。
- `com.fs.starfarer.api.combat.CombatEngineAPI`：当前战斗引擎接口，提供 `isPaused()`、`addPlugin(...)`、`removePlugin(...)`、`getElapsedInLastFrame()`。
- `com.fs.starfarer.api.ModPlugin` 与 `com.fs.starfarer.api.BaseModPlugin`：MOD 入口接口与推荐继承的空版本基类。
- `com.fs.starfarer.api.EveryFrameScript`：战役运行期脚本接口，`runWhilePaused()` 声明暂停语义。
- `com.fs.starfarer.api.Global`：进程级静态入口，提供 `getCombatEngine()` 与 `getCurrentState()`。
- `com.fs.starfarer.api.impl.combat.GenericCombatPlugin`：原版登记在 `settings.json` `plugins` 段的类，继承 `BaseEveryFrameCombatPlugin`。
- `mod_info.json`：`modPlugin` 字段声明 MOD 入口类。
- `data/config/settings.json`：`plugins` 段按“唯一 key → 完整类名”登记每帧战斗插件。
- `data/scripts/plugins`：原版每帧战斗插件目录，`settings.json` `plugins` 段的替代位置。
- `data.scripts.gearin.basics`：本页三个教学类（`GearInFramePlugin`、`GearInCounterPlugin`、`GearInCallbackRunner`）所在的 MOD 源码包。
