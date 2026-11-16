# 制作每帧战斗插件

每帧战斗插件大概是您会接触到的第一个通用型插件。它能做到的事实在是太多了。遍历舰船、获取战斗状态、舰队指令、地图...

说它是整个战斗的任意门都不为过，几乎可以在这里实现您的一切想法。

---

_本节预期阅读时间：\~15 分钟。_

_本节预期操作时间：\~15 分钟。_

## 前置知识

- [C7-S2] 的 `Global` 服务与 `settings.json` 的合并；
- [C7-S3] 的循环音与 UI 音效；
- [C7-S4] 中 "每帧执行的代码中不写日志" 的要求。

## 插件接口的方法

`EveryFrameCombatPlugin` 有四个方法，游戏按需要分别调用，大多数插件只用到第一个：

| 方法 | 用途 |
| --- | --- |
| `advance(amount, events)` | 每帧调用一次，`amount` 为该帧秒数 |
| `processInputPreCoreControls(...)` | 在游戏处理玩家输入之前调用，可拦截按键 |
| `renderInWorldCoords(viewport)` | 以战场坐标绘制，画面的位置随镜头移动 |
| `renderInUICoords(viewport)` | 以屏幕坐标绘制，画面固定在屏幕上 |

前两个处理逻辑，后两个画东西。要画随舰船移动的效果用战场坐标，要画固定在屏幕上的界面元素用屏幕坐标；[C8-S5] 的分层渲染走的是第三条路，不在这个接口上。

插件继承 `BaseEveryFrameCombatPlugin`，只覆写用得到的方法。接口里还有一个 `init(engine)`，已被标记为弃用，它不保证在 `advance(...)` 之前调用。**引擎对象在 `advance(...)` 里用 `Global.getCombatEngine()` 取，不依赖 `init(...)`。** 这一点与 [C7-S1] 的入口类不同：入口类靠游戏传入的参数，插件靠全局取。

## 两种登记方式与存活范围

有两种登记方式，差别在存活范围：

| 方式 | 何时生效 | 何时失效 |
| --- | --- | --- |
| `settings.json` 的 `plugins` 段 | 每场战斗开始时 | 战斗结束 |
| `engine.addPlugin(...)` | 调用那一刻 | 战斗结束，或用 `removePlugin(...)` 提前摘掉 |

`settings.json` 的写法是在 `plugins` 对象里加一行，键为全局唯一的名字，值为完整类名：

```json
	"plugins":{
		"GearIn_fluxWarning":"data.scripts.gearin.combat.GearInFluxWarningPlugin",
	},
```

**游戏在每场战斗开始时为每个登记的插件创建一个新实例，战斗结束后连同实例一起丢弃。** 实例字段因此只在一场战斗内有效：不必手动清理，也不能用来跨战斗保存任何东西。需要跨战斗保留的状态，写进 [C10-S1] 的 Memory。

键与 [C7-S2] 的设置键在同一个合并后的文件里，同样要带 `GearIn_` 前缀。两个 MOD 登记了同一个键，后加载的一方覆盖前者，被覆盖的插件就此不再运行，而且不会报错。

战斗中途才需要的逻辑用 `engine.addPlugin(...)`，例如某发弹体存在期间才需要的视觉效果；整场都要用的插件走 `settings.json`。

## 每帧要先做的判断

`advance(...)` 每帧调用，暂停时也会调。插件第一件事是判断这一帧该不该处理，`CombatEngineAPI` 上有现成的答案：

| 方法                         | 返回                                                 |
| ---------------------------- | ---------------------------------------------------- |
| `isPaused()`                 | 是否处于暂停                                         |
| `getTotalElapsedTime(false)` | 本场战斗不含暂停的累计秒数                           |
| `getElapsedInLastFrame()`    | 上一帧的实际时长，暂停时也不为 0                     |
| `isSimulation()`             | 是否为模拟战                                         |
| `isMission()`                | 是否为战役                                           |
| `isInCampaign()`             | 是否为生涯中的战斗                                   |
| `isCombatOver()`             | 战斗是否已经结束                                     |
| `getCustomData()`            | 本场战斗共享的 `Map`，供多个插件交换数据             |

**标题画面同样会创建插件。** 主菜单背景里跑着一场没人看的战斗，`settings.json` 登记的插件在那里照样会被实例化并每帧调用。`Global.getCurrentState()` 在主菜单返回 `GameState.TITLE`，不判断，插件就会对着背景里的舰船干活。[C4-S2] 把工匠放进了标题画面的舰船列表，这一层判断因此不能省。

三个判断的写法固定成一行：

```java
if (engine == null || engine.isPaused() || Global.getCurrentState() == GameState.TITLE) {
    return;
}
```

`engine == null` 防的是战斗结束那一段：引擎已经销毁，某个仍在推进的引用还会再调进来一次。

## 从舰船列表里挑出目标

`engine.getShips()` 返回这一帧的全部舰船，什么都有：敌我双方、战机、空间站模块、已经被打成残骸的舰体。插件自己筛。

筛的条件按用途定，`ShipAPI` 上有一组判断方法，每个问题一个：

| 方法 | 回答 |
| --- | --- |
| `isAlive()` | 还在不在场 |
| `isHulk()` | 是不是残骸 |
| `isFighter()` | 是不是战机 |
| `isStationModule()` | 是不是空间站的模块 |
| `getOwner()` | 属于哪一方，`0` 玩家，`1` 敌方 |
| `getHullSpec().getHullId()` | 船体 ID |

**残骸也要排除。** 舰船被打爆之后对象仍留在列表里一段时间，`isAlive()` 对残骸返回 `false`，但只判它一个不够稳，原版有几处同时检查 `isHulk()`。

**按 ID 前缀筛船体比逐个列 ID 稳。** `getHullId()` 是船体 ID，齿轮工业的全部船体都以 `GearIn_` 开头，按前缀判断，以后加新船体时这里不用改。

## 浮字与状态栏

战斗中有两个地方可以给玩家递信息，都在 `CombatEngineAPI` 上，都不需要自己画：

| 方法 | 出现位置 | 何时消失 |
| --- | --- | --- |
| `addFloatingText(...)` | 战场坐标上的一段浮字 | 几秒后自行消失 |
| `maintainStatusForPlayerShip(...)` | 屏幕左下角状态列表的一项 | 这一帧不调用就消失 |

`addFloatingText(...)` 要给出位置、文字、字号、颜色与跟随对象。它按调用次数生成，**每帧调一次就每帧生成一段**，所以需要节流。节流的写法是给自己记一个下次允许显示的时机，用战斗时间而不是系统时间：

```java
private final Map<ShipAPI, Float> nextTextTime = new HashMap<>();
```

`getTotalElapsedTime(false)` 返回不含暂停的战斗秒数。用系统时钟计时，玩家暂停观察局势时浮字仍会刷，画面就乱了。

这个 `Map` 的键是舰船对象本身，舰船离场后条目再也不会被读到，每帧末尾清掉：`nextTextTime.keySet().removeIf(ship -> !engine.isEntityInPlay(ship));`

`maintainStatusForPlayerShip(key, icon, title, desc, isDebuff)` 只对玩家座舰有效，`key` 用来区分不同来源。它和 [C7-S3] 的循环音属于同一类写法：**每帧调用才持续显示，停止调用后条目开始淡出**，插件不必记住当前有没有在显示。淡出留了宽限 —— 停止续写满 1 秒才开始，再花 0.25 秒淡掉，所以调试时那一行晚一秒多才不见是正常的。

## 换成幅能警告

前面几节讲的是这类插件通用的部分。现在换一个具体的：齿轮工业的舰船幅能超过 80% 时，在舰体上方显示提示；那艘舰船如果正是玩家的座舰，再加警报声与状态栏图标。

选它，是因为齿轮工业以实弹武器和高射速系统为主，突击填弹器一开幅能蹿得很快，过载常常发生在玩家注意到之前。原版只在幅能条上给数值，混战里没人顾得上看。

警报声登记在 `sounds.json`，借用原版座舰战备值告警的文件：

```json
	"GearIn_flux_alarm":[
		{"file":"sounds/sfx_interface/combat_readiness_malfunctions_alarm_flagship_01.ogg","pitch":1.2,"volume":0.4},
	],
```

图标使用原版过载状态的图标。`getSpriteName("ui", "icon_tactical_overloaded")` 按原版 `settings.json` 中登记的名字取得贴图路径，不需要自己书写文件位置。

## 完整插件

`src/data/scripts/gearin/combat/GearInFluxWarningPlugin.java`：

```java
package data.scripts.gearin.combat;

import java.awt.Color;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.fs.starfarer.api.GameState;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.BaseEveryFrameCombatPlugin;
import com.fs.starfarer.api.combat.CombatEngineAPI;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.input.InputEventAPI;

/** 齿轮工业舰船幅能过高时在舰体上方提示，玩家座舰另有警报声与状态栏图标。 */
public class GearInFluxWarningPlugin extends BaseEveryFrameCombatPlugin {

    private static final String HULL_PREFIX = "GearIn_";
    private static final float WARNING_LEVEL = 0.8f;
    private static final float TEXT_INTERVAL = 2f;
    private static final Color TEXT_COLOR = new Color(255, 140, 60, 255);
    private static final String STATUS_KEY = "GearIn_flux_warning";

    /** 每艘舰船下一次允许显示提示的战斗时间，只在本场战斗内有效。 */
    private final Map<ShipAPI, Float> nextTextTime = new HashMap<>();

    @Override
    public void advance(float amount, List<InputEventAPI> events) {
        CombatEngineAPI engine = Global.getCombatEngine();
        if (engine == null || engine.isPaused() || Global.getCurrentState() == GameState.TITLE) {
            return;
        }
        float now = engine.getTotalElapsedTime(false);
        for (ShipAPI ship : engine.getShips()) {
            if (!isWatched(ship) || ship.getFluxLevel() < WARNING_LEVEL) {
                continue;
            }
            Float next = nextTextTime.get(ship);
            if (next == null || now >= next) {
                engine.addFloatingText(ship.getLocation(), "Flux critical", 24f, TEXT_COLOR, ship, 0.5f, 1f);
                nextTextTime.put(ship, now + TEXT_INTERVAL);
            }
            if (ship == engine.getPlayerShip()) {
                warnPlayer(engine, ship);
            }
        }
        nextTextTime.keySet().removeIf(ship -> !engine.isEntityInPlay(ship));
    }

    private static boolean isWatched(ShipAPI ship) {
        return ship.isAlive()
                && !ship.isHulk()
                && !ship.isFighter()
                && !ship.isStationModule()
                && ship.getOwner() == 0
                && ship.getHullSpec().getHullId().startsWith(HULL_PREFIX);
    }

    private static void warnPlayer(CombatEngineAPI engine, ShipAPI ship) {
        Global.getSoundPlayer().playUILoop("GearIn_flux_alarm", 1f, 1f);
        String icon = Global.getSettings().getSpriteName("ui", "icon_tactical_overloaded");
        int percent = (int) (ship.getFluxLevel() * 100f);
        engine.maintainStatusForPlayerShip(STATUS_KEY, icon, "Flux warning", percent + "% flux", true);
    }
}
```

阈值 `0.8` 与间隔 `2` 秒留在代码里，而没有放进 [C7-S2] 的设置。它们只关乎提示的手感，与平衡无关。阈值再低，突击填弹器一开警告就会响个不停；再高，警告出来时离过载已经没剩多少时间。

提示文字暂时写成英文，与 [C2-S3] 中战术系统的状态文字保持一致。把这些字符串移入本地化文件，见 [C17-S3]。

### 进游戏核对

执行 `gradle jar`，重启游戏。

- 标题画面中的工匠不显示任何提示。
- 进入测试战役，操控工匠，开启护盾承受攻击，或连续开火并激活突击填弹器。幅能超过 80% 后，舰体上方每两秒出现一次 `Flux critical`，同时响起警报，左下角状态列表出现 `Flux warning` 一项并显示当前百分比。
- 停火或排散幅能，幅能降到 80% 以下，警报淡出，状态项消失。
- 用 `Tab` 切换到己方另一艘齿轮工业舰船，原座舰的提示文字仍按幅能显示，警报与状态项则跟随新的座舰。
- 暂停战斗，提示文字不再刷新。

## 常见问题

- **插件没有任何效果，日志中也没有报错**：`plugins` 中的键与其他 MOD 冲突被覆盖，或类名拼写错误。
    - **解决方案**：确认键带 `GearIn_` 前缀，值与源码的 `package` 和类名完全一致。

- **启动时报 `ClassNotFoundException`，类名为 `GearInFluxWarningPlugin`**：JAR 没有重新构建，或类所在的包与登记不一致。
    - **解决方案**：执行 `gradle jar`，用 `jar tf` 确认类在 `data/scripts/gearin/combat/` 下。

- **警报在幅能回落后仍然持续**：`playUILoop(...)` 被放在了判断之外，每帧无条件调用。
    - **解决方案**：只在幅能超过阈值的分支中调用。

## 建议参考项

- `com.fs.starfarer.api.combat.EveryFrameCombatPlugin`：插件接口。
- `com.fs.starfarer.api.combat.BaseEveryFrameCombatPlugin`：空实现基类。
- `com.fs.starfarer.api.combat.CombatEngineAPI`：引擎状态、舰船列表、浮动文字与状态栏。
- `com.fs.starfarer.api.impl.campaign.skills.CommRelayScript`：原版通讯中继插件，`maintainStatusForPlayerShip(...)` 的实际用法。
- `data/config/settings.json`：原版 `plugins` 段，以及 `ui` 类别下的状态图标名。
