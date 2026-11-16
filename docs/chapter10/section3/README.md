# 配置生涯势力

`FactionAPI` 有六组能力，关系是其中最常被提到的一组。做剧情的人迟早要用到其余几组：它认识哪些船、抽舰队时偏爱哪些、学说里的进攻性与军官素质、什么算违禁品、舰队名字从哪儿取。

已知与优先分成两个集合。加进优先集合而没加进已知集合，抽取时仍然不会出现。

学说可以直接改，`getDoctrine()` 拿到的那份对象上就有 setter。原版黑色市场开张时就这么抬高自己的战舰下限。改的是这一份对象，已经生成的舰队不受影响。

---

_本节预期阅读时间：\~15 分钟。_

_本节预期操作时间：\~20 分钟。_

## 前置知识

- [C7-S1] 的 `seedRelations(...)`，新档中的初始关系在那里写入；
- [C10-S1] 的 `$GearIn_contacted`，本节的脚本读取它作为触发条件；
- [C3-S1] 的 `gear_industry` 势力。

## 关系的三种表示

同一份关系，游戏用三种形式表达：

- 数值：`float`，范围从 -1 到 1，由 `getRelationship(id)` 读取；
- 等级：`RepLevel`，由数值按阈值换算，界面上的 "友好"、"敌对" 就是它；
- 敌对判断：`isHostileTo(id)`，决定舰队见面是否开火。

正值一侧的阈值如下，负值一侧按同样的阈值对称：

| 等级 | 数值范围       |
| ---- | -------------- |
| 中立 | 0 至 0.09      |
| 偏好 | 0.09 至 0.24   |
| 欢迎 | 0.24 至 0.49   |
| 友好 | 0.49 至 0.74   |
| 合作 | 0.74 及以上    |

**这些边界是源码里的常量，判断时先乘 100 再四舍五入取整。** `RepLevel` 里写着 `T1 = 0.09f`、`T2 = 0.24f`、`T3 = 0.49f`、`T4 = 0.74f`，换算方法只有一句 `(int) Math.round(f * 100f)`。所以 0.094 与 0.09 属同一等级，效果上等价于按百分位取整后比较。区间左开右闭：`0.09` 已经进了下一级，`0.24` 已经进了再下一级。

`getRelationship(id)` 读取的是调用方对 `id` 的关系。读取玩家与齿轮工业的关系时，从 `sector.getFaction("gear_industry")` 出发，传入玩家势力的 ID：

```java
FactionAPI gear = sector.getFaction(GearInModPlugin.FACTION_ID);
String playerId = sector.getPlayerFaction().getId();
float value = gear.getRelationship(playerId);
RepLevel level = gear.getRelationshipLevel(playerId);
```

## 写入关系

`FactionAPI` 提供三个写入方法，语义不同：

| 方法                         | 作用                                   |
| ---------------------------- | -------------------------------------- |
| `setRelationship(id, value)` | 直接设为指定数值                       |
| `adjustRelationship(id, delta)` | 在当前数值上增减                    |
| `ensureAtWorst(id, level)`   | 关系低于该等级时抬到该等级，否则不变   |

`setRelationship` 另有传入 `RepLevel` 的重载，会换算成该等级对应的数值。

本节的奖励只需要 "至少友好"，用 `ensureAtWorst`：

```java
gear.ensureAtWorst(playerId, RepLevel.FRIENDLY);
```

原版在惩罚性行为里写 `ensureAtBest` (把关系压到不高于某级)，在恢复性行为里写 `ensureAtWorst`，方向正好相反。**把方向写反，关系就朝反方向变化。** 对关系已经是合作的玩家，`ensureAtWorst(..., FRIENDLY)` 不会把关系降下来；写成 `ensureAtBest` 则会。

::: tip 为什么不用 setRelationship

`setRelationship(id, RepLevel.FRIENDLY)` 会把关系固定为友好等级的起点数值，已经比这更好的玩家会被降级。想表达 "不低于"，就用 `ensureAtWorst`。

:::

## 势力还记着哪些东西

`FactionAPI` 上一共有六组能力，关系只是第一组。做剧情的人迟早会用到其余几组，它们决定势力手里有什么、怎么打仗、卖什么。

**已知与优先级。** 已知集合决定能不能用，优先级集合决定用得多不多：

```java
Set<String> getKnownShips();
void addKnownShip(String hullId, boolean setTimestamp);
Set<String> getKnownWeapons();
void addKnownWeapon(String weaponId, boolean setTimestamp);
Set<String> getKnownFighters();
void addKnownFighter(String wingId, boolean setTimestamp);
Set<String> getKnownHullMods();
void addKnownHullMod(String modId);

Set<String> getPriorityShips();
void addPriorityShip(String hullId);
boolean knowsShip(String hullId);
```

武器、战机、船插各有一套同样的增删与判定。优先级只对已经知道的项有效，加进优先级集合而没加进已知集合，生成的舰队里仍然不会出现。

**舰队构成。** 势力抽一支舰队时按角色挑船，角色是 `combatSmall`、`combatCapital` 这类分类：

```java
List<ShipRolePick> pickShip(String role, ShipPickParams params);
float pickShipAndAddToFleet(String role, ShipPickParams params, CampaignFleetAPI fleet);
Set<String> getVariantsForRole(String roleId);
Map<String, Float> getHullFrequency();
LinkedHashSet<String> getRestrictToVariants();
int getNumAvailableForRole(String roleId, ShipPickMode mode);
```

`ShipPickParams` 里的 `mode` 决定挑哪一批：`ALL` 从全部已知项里挑，`IMPORTED` 只挑通过蓝图获得的，`PRIORITY_THEN_ALL` 先挑优先项。`getHullFrequency()` 给某艘船一个出现权重，原版的 `DelayedBlueprintLearnScript` 就是用它让新学会的船更常见。

**学说。** 一支舰队有多少战舰、多少航母、平均船体等级、军官素质、进攻性，全部来自学说：

```java
FactionDoctrineAPI getDoctrine();

int getWarships();      void setWarships(int warships);
int getAggression();    void setAggression(int aggression);
int getShipQuality();   void setShipQuality(int shipQuality);
int getOfficerQuality();void setOfficerQuality(int officerQuality);
int getNumShips();      void setNumShips(int numShips);
```

这些 setter 是对着 `FactionDoctrineAPI` 调的，拿到学说对象就能改，原版的黑色市场就这样在自己开张时把战舰与航母的下限抬上去。**`FactionAPI` 上只有 `getDoctrine()`，没有 `setDoctrine(...)`**，所以改的是这一份学说对象本身，不是替换掉它。改完立即生效于此后抽取的舰队，已经生成的舰队不受影响。

**市场与产业。** 什么是违禁品、关税多少、会造哪些东西：

```java
Set<String> getIllegalCommodities();
void makeCommodityIllegal(String commodityId);
void makeCommodityLegal(String commodityId);
float getTariffFraction();
float getTollFraction();
Set<String> getKnownIndustries();
```

**名称与外观。** 势力自己取船名、给舰队起类型名、选头像：

```java
String pickRandomShipName();
String pickRandomShipName(Random random);
String pickRandomShipName(String prefix);
String getFleetTypeName(String type);
String getShipNamePrefix();
Color getColor();
String getLogo();
String getCrest();
```

`pickRandomShipName(...)` 用的是 `.faction` 里 `shipNamePrefix` 与 `shipNameSources` 这两项的组合。势力没有配这两项，取回来的名字是空的。给舰队起名、给新船命名都该走它，不要自己拼字符串。

**其余。** `getMemory()` 存势力自己的值，`createRandomPerson()` 按势力的头像池造人物，`getCustom(...)` 读 `.faction` 里 `custom` 那一块的自定义键值。`isHostileTo(...)` 与 `getRelToPlayer()` 是关系组里最常用的两个读取入口。

## 让势力学会一艘船

势力能造、能卖、能编入舰队的船体，由它的已知舰船集合决定。初始集合来自 [C3-S1] 的 `.faction` 文件，运行中用方法增加：

```java
if (!gear.knowsShip(LEARNED_HULL)) {
    gear.addKnownShip(LEARNED_HULL, true);
}
```

`LEARNED_HULL` 是 `"eagle"`，原版的雄鹰级巡洋舰。第二个参数控制是否记录学会的时间戳，原版的蓝图学习都传 `true`。

`addKnownShip(...)` 与 `removeKnownShip(...)` 会自动清理舰船角色挑选缓存，后续生成的舰队才会按新集合选船。**直接修改 `getKnownShips()` 返回的集合不会触发这一步**，需要手动调用 `clearShipRoleCache()`，所以应当始终使用增删方法。

原版的 `DelayedBlueprintLearnScript` 在学习之后还会调用 `addUseWhenImportingShip(...)`，并在 `getHullFrequency()` 中给新船体设一个出现频率。本节不做这两步：雄鹰只是让势力 "能用"，不要求它频繁出现。需要提高出现频率时，参考那个类。

::: warning 读档后蓝图会被重新加回

`.faction` 文件中的蓝图，每次读档都会重新加入势力。想永久移除一个 `.faction` 里写着的船体，必须自己保存移除清单，并在每次读档时再移除一遍。本节只增不减，不受影响。

:::

## 奖励脚本

奖励的条件是 "接触满 30 天"。接触由 [C10-S1] 的脚本写入 `$GearIn_contacted`，本节的脚本负责读它、计时、发放，两者只通过这个标记联系：

1. 每隔 0.5 至 1 秒检查一次 `$GearIn_contacted`，未接触则返回；
2. 第一次发现已接触时，把当前时间戳写入存档公共数据 `GearIn_contact_reward_start`，作为倒计时的起点；
3. 之后每次检查，用 `getElapsedDaysSince(start)` 取得经过的生涯天数，不足 30 天则返回；
4. 满 30 天后，抬升关系、学会雄鹰，写入 `GearIn_contact_rewarded`。

**起点保存在存档公共数据里，不放在脚本字段里。** 脚本是 transient 的，读档后重新创建，字段归零，倒计时会重新开始。时间戳随存档保存，读档后脚本读到的仍是原来的起点。

`isDone()` 读取 `GearIn_contact_rewarded`，奖励发放后脚本在下一帧被清除；入口类也只在奖励尚未发放时登记它，两处判断一致。

### 登记脚本

在 `GearInModPlugin.onGameLoad(...)` 中，接触脚本的登记之后加入：

```java
if (!sector.getPersistentData().containsKey(GearInContactRewardScript.REWARDED_KEY)
        && !sector.hasTransientScript(GearInContactRewardScript.class)) {
    sector.addTransientScript(new GearInContactRewardScript());
}
```

## 完整的奖励脚本

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
        if (!sector.getMemory().getBoolean(GearInContactScript.CONTACTED_KEY)) {
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

### 进游戏核对

执行 `gradle jar`，重启游戏，**新建一份存档**。

- 用 `jump Nipher` 靠近 Nipher Prime，听到提示音后接触已记录。此时齿轮工业与玩家的关系仍是初始的 0；
- 在控制台执行 `runcode Global.getSector().getPersistentData().get("GearIn_contact_reward_start")`，返回一个时间戳；
- 保存并重新读档，再执行同一条语句，返回的时间戳不变，倒计时没有重新开始；
- 推进 30 个生涯天，齿轮工业对玩家的关系升到友好，并在外交界面中可以看到；
- 打开齿轮工业市场的舰船交易，雄鹰出现在可能的货源中。

## 常见问题

- **关系一直没有变化**：奖励脚本没有登记，或者 `$GearIn_contacted` 从未写入。
    - **解决方案**：核对 `onGameLoad(...)` 中的登记，先确认 [C10-S1] 的接触已经发生。

- **每次读档后倒计时都重新开始**：起点时间戳保存在了脚本字段里。
    - **解决方案**：起点写入 `getPersistentData()`，脚本只负责读取。

- **玩家关系反而被降低**：把 `ensureAtWorst` 写成了 `ensureAtBest`。
    - **解决方案**：要表达 "不低于友好"，用 `ensureAtWorst`。

- **学会的船体没有出现在舰队中**：直接修改了 `getKnownShips()` 返回的集合，没有清理角色挑选缓存。
    - **解决方案**：改用 `addKnownShip(...)`，或在直接修改后调用 `clearShipRoleCache()`。

- **旧存档中没有任何变化**：脚本依赖 Nipher 市场与接触标记，旧存档没有它们。
    - **解决方案**：使用新档测试，见 [C7-S1]。

## 建议参考项

- `com.fs.starfarer.api.campaign.FactionAPI`：关系读写、已知项与学说，本节用到的是关系写入与 `addKnownShip(...)`。
- `com.fs.starfarer.api.campaign.RepLevel`：各等级的阈值与 `isAtBest(...)`、`isAtWorst(...)`。
- `com.fs.starfarer.api.impl.campaign.DelayedBlueprintLearnScript`：原版学习蓝图的完整写法，包括频率与导入设置。
- `com.fs.starfarer.api.impl.campaign.CoreReputationPlugin`：原版声望事件对 `ensureAtBest`、`ensureAtWorst` 的处理。
- `data/world/factions/`：原版势力文件的已知舰船写法。
