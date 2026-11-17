# 编写生涯逐帧脚本

逐帧脚本 (`EveryFrameScript`) 几乎牵动着生涯层所有随时间推进的效果。

需要持续计时、定期检查或推动阶段演变的生涯逻辑，大多都由它的 `advance(amount)` 驱动。后面还会用一条针对 Nipher Foundry 市场的战事周期脚本，把它的调度粒度与存档取舍一起跑实。

---

_本节预期阅读时间：\~12 分钟。_

_本节预期操作时间：\~15 分钟。_

## 前置知识

- [C7-S1] 的 `onNewGame()` 与 `onGameLoad()` 分工；
- [C11-S1] 的巡逻舰队管理器；
- [C12-S6] 的 Warfront 条件与 [C12-S1] 的市场 ID。

## 三个方法与挂载对象

```java
public interface EveryFrameScript {
    boolean isDone();
    boolean runWhilePaused();
    void advance(float amount);
}
```

| 方法 | 调用时机 | 作用 |
| ---- | -------- | ---- |
| `advance(amount)` | 随挂载对象每帧推进，`amount` 为距离上一帧经过的现实秒数 | 执行计时、状态判断或调度逻辑 |
| `isDone()` | 与 `advance(...)` 同一轮判定 | 返回 `true` 后，引擎自动把脚本移出调度列表 |
| `runWhilePaused()` | 引擎推进脚本前检查 | 玩家在大地图暂停时间时是否仍继续触发 `advance(...)` |

**"每帧" 这个说法只在登记于 `SectorAPI` 时成立。** 除了 `SectorAPI` 之外，`LocationAPI`、`SectorEntityToken` 与 `FleetOrStubAPI` 上同样提供 `addScript(...)`。在 `LocationAPI` 的设计里，挂在某个特定星系上的脚本只随该地点的推进而触发 —— **当玩家不在该地点时，每秒实际推进的次数可能更低**。需要全星区稳定计时时挂在 `SectorAPI` 上；只在特定地点或某支舰队存活期间才有意义的脚本 (例如 [C11-S1] 的巡逻任务排程脚本)，挂到对应对象上还能随着目标消失自动停止或回收。

`runWhilePaused()` 通常返回固定的布尔值：生涯周期、建造、减员倒计时几乎都返回 `false`，只有 UI 监控或必须在暂停期间即时响应的检查才设为 `true`。如果需要在暂停时按条件分支控制，直接在 `advance(...)` 内部判断状态更稳妥。

引擎不会自行推断脚本何时算结束，只要 `isDone()` 一直返回 `false`，脚本就会始终留在调度列表里；反之，对于只需要延时几秒或几天就收工的一次性脚本，让 `isDone()` 在任务完成后返回 `true`，就能免去手动调用 `removeScript(...)` 的麻烦。

## 帧时间换算成生涯天数

`amount` 是这一帧经过的现实秒数。玩家在大地图上开启两倍速，或者不同帧率下步长有波动，同一现实秒对应的游戏时间并不相同；若直接累加 `amount`，倒计时会受加速倍率和帧率影响。跨天与周期判断应当统一交给时钟接口换算：

```java
float days = Global.getSector().getClock().convertToDays(amount);
```

`CampaignClockAPI` 还提供 `getDay()`、`getTimestamp()` 与 `getElapsedDaysSince(long)` 等方法，适合记录绝对日期或在间隔几周才触发一次的场景下用时间戳比对，与界面左上角的日期口径完全一致。

## 登记方式与存档兼容性

| 方法 | 是否写入存档 | 读档后的行为 |
| ---- | ------------ | ------------ |
| `SectorAPI.addScript(script)` | 是，脚本实例与字段一并序列化 | 恢复为存档里的原实例，字段值延续 |
| `SectorAPI.addTransientScript(script)` | 否，只存在于当前运行进程 | 自动消失，须由 `onGameLoad(...)` 按需重新登记 |

`SectorAPI` 配套提供了 `hasScript(Class)`、`hasTransientScript(Class)`、`removeScriptsOfClass(Class)` 与 `getScripts()` 用于查询或清理。

选持久还是临时，主要取决于脚本自身字段里有没有不能丢的状态，以及旧存档兼容的包袱有多重。如果脚本完全依靠读写 Memory 或市场标记运转、自身不带字段，优先用 `addTransientScript(...)` 在 `onGameLoad(...)` 中挂上；这样即便玩家日后升级或卸载 MOD，存档 XML 里也不会留下找不到类的幽灵引用。反之，若脚本要在字段里守着倒计时或阶段状态，就必须用 `addScript(...)` 进存档。

**一旦选择让脚本随存档保存，类的全限定名与字段结构就是您的存档格式。** 发布后随便改包名、重命名类，或者删掉玩家仍在用的旧脚本类，都会导致旧档在读取反序列化时直接报错；同时也要避免在脚本字段里长期缓存市场、实体、人物的直接引用，按 ID 从 `SectorAPI` 现查总能拿到最新对象，也能减少存档体积与失效引用。

## 永久循环与自清理脚本

按存活周期来看，逐帧脚本常见的写法有永久轮转与自终止两种：

1. **永久轮转脚本 (`isDone()` 恒为 `false`)**：例如全局周期调度或持久监控器，通过 `addScript(...)` 在新档初始化时登记一次，之后长期驱动自身状态翻转；
2. **自终止脚本 (条件达成后 `isDone()` 返回 `true`)**：例如延迟 5 天触发的援军事件、或者某支舰队存活期间才存在的辅助脚本，倒计时归零或目标销毁 (`!fleet.isAlive()`) 后把完成标志置为 `true`，引擎会在下一轮自动将它从脚本表清除，不留多余内存开销。

## 把战事周期写进 Foundry

以 Nipher Foundry 的战事轮换为例：我们需要让市场条件 `GearIn_warfront` 周期性开启与关闭，分别持续 60 天战事与 30 天平息。因为剩余天数 `daysLeft` 保存在脚本字段里，本节采用 `addScript(...)` 持久登记：

```java
daysLeft -= sector.getClock().convertToDays(amount);
if (daysLeft > 0f) {
    return;
}
```

归零之后，直接以目标市场当前是否持有 `GearIn_warfront` 条件来判定下一个阶段，不再额外维护冗余布尔字段：

```java
if (market.hasCondition(GearInWarfrontCondition.CONDITION_ID)) {
    market.removeCondition(GearInWarfrontCondition.CONDITION_ID);
    daysLeft = CALM_DAYS;
} else {
    market.addCondition(GearInWarfrontCondition.CONDITION_ID);
    daysLeft = WAR_DAYS;
}
```

- 直接复用市场条件本身作状态真值，能避免脚本字段与市场实际状态在意外篡改或异常后出现分歧；
- 市场每次通过 `sector.getEconomy().getMarket(GearInFoundryGenerator.MARKET_ID)` 按 ID 查找，若市场不存在则直接返回，不在脚本对象里保留 `MarketAPI` 成员引用；
- 每当阶段切换时顺手在左下角发一条提示消息，玩家不用点开殖民地面板也能知道 Foundry 当前处于战时还是平息期 (`Warfront` 条件会在战时期间提高危险度、压低流通性并抬高巡逻队装配质量，平时则会一并撤销)。

## 完整的脚本

`src/data/scripts/gearin/campaign/GearInWarfrontCycleScript.java`：

```java
package data.scripts.gearin.campaign;

import com.fs.starfarer.api.EveryFrameScript;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.SectorAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;

/** Foundry 周围的战事时起时息：按生涯天数在市场上周期性添加或移除 Warfront 条件。脚本随存档保存。 */
public class GearInWarfrontCycleScript implements EveryFrameScript {

    /** 战事持续的生涯天数。 */
    private static final float WAR_DAYS = 60f;

    /** 战事平息的生涯天数。 */
    private static final float CALM_DAYS = 30f;

    /** 当前阶段还剩多少生涯天，随存档保存。新档开局时 Foundry 正处于战事中。 */
    private float daysLeft = WAR_DAYS;

    @Override
    public boolean isDone() {
        return false;
    }

    @Override
    public boolean runWhilePaused() {
        return false;
    }

    @Override
    public void advance(float amount) {
        SectorAPI sector = Global.getSector();
        daysLeft -= sector.getClock().convertToDays(amount);
        if (daysLeft > 0f) {
            return;
        }
        MarketAPI market = sector.getEconomy().getMarket(GearInFoundryGenerator.MARKET_ID);
        if (market == null) {
            return;
        }
        if (market.hasCondition(GearInWarfrontCondition.CONDITION_ID)) {
            market.removeCondition(GearInWarfrontCondition.CONDITION_ID);
            daysLeft = CALM_DAYS;
            sector.getCampaignUI().addMessage("Nipher Foundry 周围的战事暂时平息了。");
        } else {
            market.addCondition(GearInWarfrontCondition.CONDITION_ID);
            daysLeft = WAR_DAYS;
            sector.getCampaignUI().addMessage("Nipher Foundry 周围再度爆发战事。");
        }
    }
}
```

## 登记战事脚本

在 `GearInModPlugin.onNewGame()` 中，紧跟在 Foundry 生成器之后执行：

```java
new GearInFoundryGenerator().generate(sector);
sector.addScript(new GearInWarfrontCycleScript());
```

持久脚本在开新档时注册一次即可，不要顺手放进 `onGameLoad(...)` —— 后者每次读档都会再跑一遍，若没有去重标记，每次读档都会在星区里多叠出一份周期脚本，**后果就是战事消息连续刷屏、倒计时被成倍消耗，却没有任何异常报错**。如果后续版本更新需要给旧存档补挂持久脚本，应当像 [C11-S1] 那样配合存档公共数据标记或 `sector.hasScript(...)` 做一次性校验。

### 进游戏核对

执行 `gradle jar`，重启游戏，**新建一份存档**，停靠 Nipher Foundry：

- 初始状态下市场条件栏中存在 `Warfront`；
- 推进约 60 个生涯天，左下角弹出战事平息提示，Foundry 市场的 `Warfront` 条件消失；
- 继续推进约 30 天，弹出再度开战提示，`Warfront` 条件重新挂回；
- 在周期中途保存并重新读档，剩余阶段天数继续按原先节奏推进。

## 常见问题

- **读档之后脚本不再生效**：登记误用了 `addTransientScript(...)`，脚本未能随存档保存。
    - **解决方案**：需要保留字段状态的脚本改用 `addScript(...)`，或将状态改存到 Memory 后改为在 `onGameLoad(...)` 重新登记临时脚本。

- **同一件事连续触发两次**：脚本被重复注册了多份，常见于将 `addScript(...)` 直接放进 `onGameLoad(...)`。
    - **解决方案**：持久脚本只在 `onNewGame()` 登记，或在补登记前先用 `hasScript(...)` / 持久化标记做幂等判断。

- **更新 MOD 后旧存档报错读不出来**：随存档保存的脚本类被改了类名、移了包路径或直接删除。
    - **解决方案**：一旦随存档类发布，保持全限定类名不变；仅在确认不再保存时改用无状态临时脚本。

- **时间加速下周期时长飘忽不定**：把 `advance(float amount)` 传入的现实秒数当成了游戏天数或直接按帧固定加减。
    - **解决方案**：统一调用 `Global.getSector().getClock().convertToDays(amount)` 换算成生涯天数。

## 建议参考项

- `com.fs.starfarer.api.EveryFrameScript`：`advance`、`isDone` 与 `runWhilePaused` 的接口契约。
- `com.fs.starfarer.api.campaign.SectorAPI`：`addScript`、`addTransientScript`、`hasScript` 与清理接口。
- `com.fs.starfarer.api.campaign.LocationAPI`：星系级局部脚本的挂载与降频说明。
- `com.fs.starfarer.api.campaign.CampaignClockAPI`：`convertToDays(...)`、`getDay()` 与时间戳计算。
