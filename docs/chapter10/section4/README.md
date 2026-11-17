# 配置生涯势力

在第三章写 `.faction` 文件时，填进去的只是这个势力开局时的出厂快照。一旦存档跑起来，声望会在九档等级之间来回浮动，原本没有的船体会随着蓝图事件加进已知池，连军事学说都会在特定刷船场景里被临时复制一份加以改写。

把运行期的 `FactionAPI` 接上，一个势力才算真正在存档里动起来。

---

_本节预期阅读时间：\~15 分钟。_

_本节预期操作时间：\~20 分钟。_

## 前置知识

- [C3-S1] 的 `gear_industry` 势力配置；
- [C7-S1] 的 `seedRelations(...)` 新档初始关系设置；
- [C10-S1] 的 `getPersistentData()` 与 `CampaignClockAPI`；
- [C10-S2] 的 `$GearIn_contacted` 全局接触标记。

## 关系的三种表示与九档等级

两个势力之间的关系在游戏内部同时以三种形式存在：

- **浮点数值 (`float`)**：取值范围 `-1.0` 至 `1.0` (界面上显示为 `-100` 至 `+100`)，通过 `faction.getRelationship(otherFactionId)` 读取；
- **关系等级 (`RepLevel` 枚举)**：按固定阈值由浮点数值换算得到，通过 `faction.getRelationshipLevel(otherFactionId)` 读取；
- **敌对判定 (`boolean`)**：通过 `faction.isHostileTo(otherFactionId)` 判断双方舰队遭遇时是否主动交火 (关系达到 "敌对" 或 "仇恨"，即按四舍五入后 `-50` 点、浮点 `<= -0.495f` 时返回 `true`)。

`RepLevel` 在换算时先把浮点值乘以 `100` 并四舍五入取整 (`Math.round(f * 100f)`)，再按整数点数划入九个对称的等级：

| 枚举名 | 官方中文名 | 点数区间 (`-100` 至 `+100`) | 浮点阈值边界 |
| --- | --- | --- | --- |
| `COOPERATIVE` | 合作 | `+75` 至 `+100` | `>= 0.745` (`T4 = 0.74f`) |
| `FRIENDLY` | 友好 | `+50` 至 `+74` | `0.495` 至 `0.744` (`T3 = 0.49f`) |
| `WELCOMING` | 欢迎 | `+25` 至 `+49` | `0.245` 至 `0.494` (`T2 = 0.24f`) |
| `FAVORABLE` | 良好 | `+10` 至 `+24` | `0.095` 至 `0.244` (`T1 = 0.09f`) |
| `NEUTRAL` | 中立 | `-9` 至 `+9` | `-0.094` 至 `0.094` |
| `SUSPICIOUS` | 怀疑 | `-24` 至 `-10` | `-0.244` 至 `-0.095` |
| `INHOSPITABLE` | 冷淡 | `-49` 至 `-25` | `-0.494` 至 `-0.245` |
| `HOSTILE` | 敌对 | `-74` 至 `-50` | `-0.744` 至 `-0.495` |
| `VENGEFUL` | 仇恨 | `-100` 至 `-75` | `<= -0.745` |

读取玩家与齿轮工业的关系时，既可以从 `sector.getFaction(GearInModPlugin.FACTION_ID)` 出发传入玩家势力 ID，也可以直接调用快捷方法 `gear.getRelToPlayer()`：

```java
FactionAPI gear = sector.getFaction(GearInModPlugin.FACTION_ID);
String playerId = sector.getPlayerFaction().getId();
float value = gear.getRelationship(playerId);
RepLevel level = gear.getRelationshipLevel(playerId);
```

## 写入关系：直接赋值、增减与保底夹逼

`FactionAPI` 上的关系修改都是双向同步的 (改了 A 对 B 的关系，B 对 A 的关系自动取相同值)，常用写入方法有四个：

| 方法 | 作用与适用场景 |
| --- | --- |
| `setRelationship(id, value)` / `setRelationship(id, level)` | 直接将关系覆盖为指定数值或对应等级的起始值；适合开新档初始化 ([C7-S1]) |
| `adjustRelationship(id, delta, limit)` | 在当前关系上增减 `delta`，可选传入 `RepLevel` 作为本次增减的上限或下限 |
| `ensureAtWorst(id, level)` | **保底抬升**：若当前关系低于 `level`，则抬升至 `level`；若已高于或等于 `level`，保持原值不动 |
| `ensureAtBest(id, level)` | **封顶压制**：若当前关系高于 `level`，则压低至 `level`；若已低于或等于 `level`，保持原值不动 |

发奖励时如果直接调 `setRelationship(playerId, RepLevel.FRIENDLY)` 去给玩家 "升到友好"，万一玩家此前已经靠刷赏金把关系打到了 `+80` (合作)，这一行下去反倒会把关系扣回友好的起点 `+50`。因此凡是表达 "关系至少提升到某一级"，一律用 `ensureAtWorst`；表达 "敌对事件使关系恶化到不高于某一级"，则用 `ensureAtBest`。

## 军备知识、学说与其它运行期配置

除了声望关系，`FactionAPI` 还掌管着势力的蓝图池、舰队生成学说、市场违禁品与命名规则。

### 已知集合与优先集合

势力能造出哪些舰船、武器、战机和船插，由两组集合共同决定：

```java
Set<String> getKnownShips();
void addKnownShip(String hullId, boolean setTimestamp);
void removeKnownShip(String hullId);
boolean knowsShip(String hullId);

Set<String> getPriorityShips();
void addPriorityShip(String hullId);
```

武器 (`KnownWeapons`)、战机 (`KnownFighters`) 与船插 (`KnownHullMods`) 各有一套同名方法。

- **已知集合 (`Known`) 决定能不能出，优先集合 (`Priority`) 决定优先抽谁。** 把一艘船加进优先集合却没加进已知集合，生成的舰队里依然不会出现这艘船。
- **务必通过 `addKnownShip(...)` / `removeKnownShip(...)` 增删，不要直接往 `getKnownShips()` 返回的 `Set` 里调用 `.add(...)`。** 增删方法内部会自动调用 `clearShipRoleCache()` 清空按战斗角色 (`combatSmall`、`combatCapital` 等) 预计算的选船缓存；直接操作底层集合不会刷新缓存，已经算好的选船池不会感知到新船。
- 第二个参数 `setTimestamp` 控制是否记录学会该蓝图的生涯时间戳 (用于黑市蓝图外流等延迟逻辑)，动态解锁新船时通常传 `true`。

::: warning .faction 文件中的初始蓝图会在每次读档时重新注入

游戏每次加载存档时，都会把 `.faction` 文件里写死的蓝图重新合并进势力的已知集合。因此通过 `addKnownShip(...)` 新增的船体会随存档永久保留；但如果您调用 `removeKnownShip(...)` 删掉了一艘原本写在 `.faction` 文件里的船，玩家下次读档时它又会被加回来。若确需永久剥夺某项初始蓝图，需要把移除名单存进 `getPersistentData()`，并在每次 `onGameLoad(...)` 时重新执行移除。

:::

### 军事学说：直接修改与复制副本

一支舰队里战舰 (`warships`)、航母 (`carriers`)、相位舰 (`phaseShips`) 的占比，以及军官质量 (`officerQuality`)、装配质量 (`shipQuality`) 与进攻性 (`aggression`)，都保存在 `FactionDoctrineAPI` 中：

```java
FactionDoctrineAPI getDoctrine();

int getWarships();       void setWarships(int warships);
int getCarriers();       void setCarriers(int carriers);
int getPhaseShips();     void setPhaseShips(int phaseShips);
int getAggression();     void setAggression(int aggression);
int getShipQuality();    void setShipQuality(int shipQuality);
int getOfficerQuality(); void setOfficerQuality(int officerQuality);
```

这里有一处非常关键的共享引用边界：

- `faction.getDoctrine()` 返回的是该势力当前持有的数据对象 (`FactionAPI` 上没有 `setDoctrine(...)`)。直接对 NPC 势力调用 `setWarships(...)` 只会在**本次运行期**内改变它的舰队与市场刷船配置 —— 引擎在每次读档恢复势力对象时都会用 `.faction` 静态配置重新覆写非玩家势力的 `doctrine`，因此若要长期改动需改 `.faction` 或在 `onGameLoad(...)` 中重新应用。
- 如果您只是想在某次特定刷船或生成某支特殊任务舰队时临时调整配比、**不想污染势力本身的全局学说**，应当像原版黑市插件 (`BlackMarketPlugin`) 那样先调用 `.clone()` 复制一份学说副本，再在副本上修改并传给刷船方法：

```java
FactionDoctrineAPI tempDoctrine = faction.getDoctrine().clone();
tempDoctrine.setWarships(Math.max(2, tempDoctrine.getWarships()));
```

### 违禁品、关税与随机船名

- **违禁品与关税**：`getIllegalCommodities()`、`makeCommodityIllegal(commodityId)`、`makeCommodityLegal(commodityId)` 可在运行期动态调整某项商品在该势力市场是否属于违禁品；`getTariffFraction()` 返回公开市场关税比例。
- **随机船名与人物**：`faction.pickRandomShipName()` 按 `.faction` 文件中的 `shipNamePrefix` 与 `shipNameSources` 词库抽取带势力前缀的随机舰船名；`faction.createRandomPerson()` 则按势力的名字库与头像池生成随机 NPC。

## 接触满 30 天的后续奖励：GearInContactRewardScript

[C10-S2] 在玩家首次靠近 Nipher Prime 时写下了 `$GearIn_contacted` 标记。本节的 `GearInContactRewardScript` 围绕这枚标记运转：当玩家与齿轮工业完成首次接触并经过 `30` 个生涯日后，将齿轮工业对玩家的关系保底抬升至 "友好" (`RepLevel.FRIENDLY`)，同时让齿轮工业学会原版 "雄鹰" (`"eagle"`) 巡洋舰的设计图。

脚本用到了 [C10-S1] 讲过的 `sector.getPersistentData()` 来保存倒计时起点时间戳 `GearIn_contact_reward_start` 与已发放标记 `GearIn_contact_rewarded`：

1. 每 `0.5` 至 `1` 秒检查一次 `$GearIn_contacted`，尚未接触则直接返回；
2. 首次检测到已接触时，向 `getPersistentData()` 写入当前生涯时钟的 `getTimestamp()` 作为起点；
3. 此后每次检查通过 `sector.getClock().getElapsedDaysSince(start)` 计算经过的生涯天数，满 `30` 天后调用 `ensureAtWorst(..., RepLevel.FRIENDLY)` 与 `addKnownShip("eagle", true)`，并写入 `GearIn_contact_rewarded` 让脚本在下一帧通过 `isDone()` 自动退场。

### 完整奖励脚本

`src/data/scripts/gearin/campaign/GearInContactRewardScript.java`：

```java
package data.scripts.gearin.campaign;

import java.util.Map;

import com.fs.starfarer.api.EveryFrameScript;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.RepLevel;
import com.fs.starfarer.api.campaign.SectorAPI;
import com.fs.starfarer.api.util.IntervalUtil;

import data.scripts.gearin.GearInModPlugin;

/** 玩家与齿轮工业接触满 30 天后，关系至少提升到友好，并让势力学会雄鹰的设计图。 */
public class GearInContactRewardScript implements EveryFrameScript {

    /** 存档公共数据：接触后倒计时的起点时间戳。 */
    public static final String START_KEY = "GearIn_contact_reward_start";

    /** 存档公共数据：奖励已发放。 */
    public static final String REWARDED_KEY = "GearIn_contact_rewarded";

    private static final float DELAY_DAYS = 30f;
    private static final String LEARNED_HULL = "eagle";

    private final IntervalUtil interval = new IntervalUtil(0.5f, 1f);

    @Override
    public boolean isDone() {
        return Global.getSector().getPersistentData().containsKey(REWARDED_KEY);
    }

    @Override
    public boolean runWhilePaused() {
        return false;
    }

    @Override
    public void advance(float amount) {
        interval.advance(amount);
        if (!interval.intervalElapsed()) {
            return;
        }
        SectorAPI sector = Global.getSector();
        if (!sector.getMemoryWithoutUpdate().getBoolean(GearInContactScript.CONTACTED_KEY)) {
            return;
        }
        Map<String, Object> data = sector.getPersistentData();
        if (!data.containsKey(START_KEY)) {
            data.put(START_KEY, sector.getClock().getTimestamp());
            return;
        }
        long start = (Long) data.get(START_KEY);
        if (sector.getClock().getElapsedDaysSince(start) < DELAY_DAYS) {
            return;
        }

        FactionAPI gear = sector.getFaction(GearInModPlugin.FACTION_ID);
        gear.ensureAtWorst(sector.getPlayerFaction().getId(), RepLevel.FRIENDLY);
        if (!gear.knowsShip(LEARNED_HULL)) {
            gear.addKnownShip(LEARNED_HULL, true);
        }
        data.put(REWARDED_KEY, true);
    }
}
```

### 在入口类中登记脚本

在 `GearInModPlugin.onGameLoad(...)` 中，于 `GearInContactScript` 的登记之后加入：

```java
if (!sector.getPersistentData().containsKey(GearInContactRewardScript.REWARDED_KEY)
        && !sector.hasTransientScript(GearInContactRewardScript.class)) {
    sector.addTransientScript(new GearInContactRewardScript());
}
```

### 进游戏核对

执行 `gradle jar`，重启游戏，**新建一份存档**。

- 用 `jump Nipher` 靠近 Nipher Prime 触发首次接触，此时齿轮工业与玩家的关系仍为初始的 `0` (中立)；
- 在控制台执行 `runcode Global.getSector().getPersistentData().get("GearIn_contact_reward_start")`，可以看到已写入的 `Long` 时间戳；保存并读档后再次查看，该时间戳保持不变；
- 在星图上推进 `30` 个生涯日后打开势力界面，齿轮工业与玩家的关系已升至 `+50` (友好)；
- 在控制台执行 `runcode Global.getSector().getFaction("gear_industry").knowsShip("eagle")`，返回 `true`。

## 常见问题

- **接触后读档一次，30 天倒计时又从头算起**：将起点时间戳存进了瞬态脚本的成员变量，而非 `sector.getPersistentData()`。
    - **解决方案**：倒计时起点必须写入 `getPersistentData()` 或全局 Memory。

- **原本关系已经是 "合作" 的玩家在触发奖励后掉回了 "友好"**：误用了 `setRelationship(..., RepLevel.FRIENDLY)` 或 `ensureAtBest(...)`。
    - **解决方案**：保底抬升关系应使用 `ensureAtWorst(..., RepLevel.FRIENDLY)`。

- **向 `getKnownShips()` 添加船体后，新生成的舰队里始终抽不到该船**：直接对 `getKnownShips()` 返回的集合调用了 `.add(...)`，未刷新角色挑选缓存。
    - **解决方案**：改用 `faction.addKnownShip(hullId, true)`。

## 建议参考项

- `com.fs.starfarer.api.campaign.FactionAPI`：关系读写、已知蓝图增删、学说与命名接口。
- `com.fs.starfarer.api.campaign.RepLevel`：九档关系等级枚举、阈值与 `isAtWorst(...)` / `isAtBest(...)`。
- `com.fs.starfarer.api.campaign.FactionDoctrineAPI`：势力军事学说配置与 `.clone()`。
- `com.fs.starfarer.api.impl.campaign.DelayedBlueprintLearnScript`：原版蓝图延迟学习脚本。
- `com.fs.starfarer.api.impl.campaign.submarkets.BlackMarketPlugin`：原版黑市通过 `getDoctrine().clone()` 临时调整刷船学说的实现。
