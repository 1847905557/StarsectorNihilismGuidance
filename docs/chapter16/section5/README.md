# 使用 MemoryAPI 保存状态

Memory 是生涯对象随身携带的状态容器。实体是否已经被调查、人物是否说过某句话、市场是否满足某个临时条件，都可以写进所属对象的 `MemoryAPI`；Rules 再通过 `global`、`player`、`entity` 等槽位读回这些状态。

*本节预期阅读时间：~35 分钟。*

*本节预期操作时间：~60 分钟。*

## 学习目标

完成后，您将能够：

- 从 Sector、玩家、舰队、实体、市场、人物和势力取得 `MemoryAPI`；
- 根据是否需要刷新 facts 选择 `getMemory()` 或 `getMemoryWithoutUpdate()`；
- 写入并读取字符串、数值、布尔值、向量和生涯对象；
- 使用存在性、相等和区间方法判断状态；
- 控制键的游戏天过期时间；
- 根据 `MemKeys` 判断 Rules 变量实际读取哪个对象。

## 前置知识

- [C7-S1] 的 `ModPlugin` 与 `onGameLoad`；
- [C10-S1] 的 `EveryFrameScript` 注册和推进；
- [C14-S3] 的交互目标与 active person；
- [C16-S2] 的 Rules 变量、条件和选项回流；
- [C16-S3] 的原版场景如何组装 Rules 上下文；
- [C16-S4] 的 `Token` 如何从 `memoryMap` 解析变量。

## 从游戏对象取得 Memory

`MemoryAPI` 不单独注册。实现了 `HasMemory` 的生涯对象负责持有它，调用方先找到对象，再从对象取得 Memory。状态因此天然带有归属：写在市场上的键随市场保存，写在人物上的键随人物保存，写在 Sector 上的键则属于全局生涯。

### 直接取得当前 Memory

普通 MOD 状态不依赖原版动态 facts，直接取得当前 Memory 即可。写入器首先从 Sector 取得全局 Memory：

```java
MemoryAPI sector = Global.getSector().getMemoryWithoutUpdate();
```

玩家状态属于 `CharacterData`，人物状态属于 `PersonAPI`，势力状态属于 `FactionAPI`。它们可以保存同名键，因为每个对象持有独立的 Memory：

```java
Global.getSector().getCharacterData().getMemoryWithoutUpdate().set(
        GearInMemoryKeys.KEY_PLAYER, true);

PersonAPI person = Global.getSector().getPlayerPerson();
person.getMemoryWithoutUpdate().set(GearInMemoryKeys.KEY_PERSON, "person-ready");
FactionAPI faction = person.getFaction();
faction.getMemoryWithoutUpdate().set(
        GearInMemoryKeys.KEY_FACTION, "person-faction-ready");
```

市场和实体也各有自己的 Memory。Jangala 市场保存市场状态，主实体保存实体状态；主实体上的 `$sourceMarket` 则告诉 Rules 对话哪个市场应进入来源市场槽位：

```java
MarketAPI market = Global.getSector().getEconomy().getMarket("jangala");
if (market != null && market.getPrimaryEntity() != null) {
    market.getMemoryWithoutUpdate().set(GearInMemoryKeys.KEY_MARKET, "market-ready");
    market.getFaction().getMemoryWithoutUpdate().set(
            GearInMemoryKeys.KEY_FACTION, "faction-ready");
    SectorEntityToken entity = market.getPrimaryEntity();
    entity.getMemoryWithoutUpdate().set(GearInMemoryKeys.KEY_ENTITY, "entity-ready");
    entity.getMemoryWithoutUpdate().set(GearInMemoryKeys.KEY_SOURCE_MARKET, "jangala");
}
```

`getMemoryWithoutUpdate()` 返回对象当前持有的 Memory，不主动要求生涯插件重新生成 facts。上面的键全部由示例自己维护，因此读取和写入都使用这一入口。

玩家舰队同样是生涯实体。下面的代码同时演示两种归属：`KEY_ENTITY` 写在舰队自身，`KEY_FLEET` 与 `KEY_OBJECT` 写在 Sector Memory，值都指向玩家舰队。

```java
CampaignFleetAPI fleet = Global.getSector().getPlayerFleet();
if (fleet != null) {
    sector.set(GearInMemoryKeys.KEY_FLEET, fleet);
    sector.set(GearInMemoryKeys.KEY_OBJECT, fleet);
    fleet.getMemoryWithoutUpdate().set(GearInMemoryKeys.KEY_ENTITY, "fleet-ready");
}
```

### 需要最新 facts 时刷新 Memory

`getMemory()` 会让已注册的 CampaignPlugin 更新对应对象的 Rules facts。Sector、实体、市场、人物和势力分别进入 `updateGlobalFacts`、`updateEntityFacts`、`updateMarketFacts`、`updatePersonFacts` 或 `updateFactionFacts`；返回的仍是该对象持有的 Memory。

示例只调用一次 Sector 的刷新入口：

```java
Global.getSector().getMemory();
```

这次调用会刷新 `$isDevMode`、`$daysSinceStart` 一类全局 facts。自己写入的 `$GearIn_*` 状态已经存在于当前 Memory，不需要借助 facts 更新。

::: tip 如何选择两个入口
读取或写入 MOD 自己维护的键时使用 `getMemoryWithoutUpdate()`。只有当前判断依赖最新 Rules facts 时才调用 `getMemory()`，并把刷新放在真正需要这些 facts 的操作之前。
:::

## 使用 MemoryAPI 读写状态

Memory 键在 Java 中包含开头的 `$`。例如 `"$GearIn_memory_global"` 是实际键名；Rules 中的 `global.` 是选择 Memory 槽位的语法，不会存进键名。

### 写入永久值和限时值

两参数 `set(key, value)` 写入永久值，三参数 `set(key, value, expire)` 同时给出剩余游戏天数：

```java
sector.set(GearInMemoryKeys.KEY_GLOBAL, "sector-ready");
sector.set(GearInMemoryKeys.KEY_NUMBER, 2.5f, 30f);
sector.set(GearInMemoryKeys.KEY_INTEGER, 3);
sector.set(GearInMemoryKeys.KEY_LONG, 4L);
sector.set(GearInMemoryKeys.KEY_VECTOR, new Vector2f(10f, 20f));
```

这里的字符串、整数、长整数和向量不会过期；`KEY_NUMBER` 在写入时获得 30 游戏天寿命。再次使用两参数 `set` 写同一个键，会把它改为永久值并清除原有过期计时。

### 按保存类型读取值

`get(key)` 返回原始对象；类型明确时，类型化读取可以省去转换：

```java
LOG.info("GearInMemoryWriter: string=" + sector.getString(GearInMemoryKeys.KEY_GLOBAL)
        + " number=" + sector.getFloat(GearInMemoryKeys.KEY_NUMBER)
        + " int=" + sector.getInt(GearInMemoryKeys.KEY_INTEGER)
        + " long=" + sector.getLong(GearInMemoryKeys.KEY_LONG)
        + " vector=" + sector.getVector2f(GearInMemoryKeys.KEY_VECTOR));
```

这条日志依次显示 `sector-ready`、`2.5`、`3`、`4` 和向量 `(10, 20)`。接口还提供 `getBoolean`、`getEntity` 和 `getFleet`；保存值类型不符合目标类型时，对象读取方法返回 `null`。

```java
LOG.info("GearInMemoryWriter: contains=" + sector.contains(GearInMemoryKeys.KEY_GLOBAL)
        + " is=" + sector.is(GearInMemoryKeys.KEY_PROBE, true)
        + " between=" + sector.between(GearInMemoryKeys.KEY_NUMBER, 2f, 3f)
        + " entity=" + sector.getEntity(GearInMemoryKeys.KEY_FLEET)
        + " fleet=" + sector.getFleet(GearInMemoryKeys.KEY_FLEET)
        + " object=" + sector.get(GearInMemoryKeys.KEY_OBJECT));
```

同一个舰队值可以由 `getEntity` 作为生涯实体读取，也可以由 `getFleet` 取得更具体的舰队接口；`get` 保留原始对象类型。

### 判断键是否存在以及值是否匹配

`contains(key)` 回答键是否存在。它与 `getBoolean(key)` 的用途不同：缺失键和显式保存的 `false` 都会让 `getBoolean` 返回 `false`，只有 `contains` 能把两种状态区分开。

`is(key, value)` 在键存在时比较对象、浮点或布尔值。`between(key, min, max)` 把数值放进闭区间 `[min, max]` 判断，因此 `2` 和 `3` 也满足 `between(KEY_NUMBER, 2f, 3f)`。

### 移除、清空和检查键集合

`unset(key)` 只移除指定键，`clear()` 清空整个 Memory，`isEmpty()` 可确认容器已经没有任何键。示例使用由工厂创建的临时 Memory，避免清空真实生涯对象的状态：

```java
MemoryAPI scratch = Global.getFactory().createMemory();
scratch.set("$temporary", true);
scratch.unset("$temporary");
scratch.set("$clearMe", true);
scratch.clear();
```

`getKeys()` 返回当前全部键，可用于日志核对；`getExpire(key)` 返回指定键剩余的游戏天数。永久键没有过期记录，返回 `-1`。

### MemoryAPI 常用方法速查表

| 方法组 | 直接结果 |
|---|---|
| `set(key, value)` | 写入永久键，并清除该键原有过期时间 |
| `set(key, value, expire)` | 写入值并设置游戏天寿命 |
| `get`、`getString`、`getFloat`、`getInt`、`getLong`、`getBoolean` | 读取原始对象或基础类型 |
| `getVector2f`、`getEntity`、`getFleet` | 读取向量或生涯对象 |
| `contains`、`is`、`between` | 判断存在性、相等关系和闭区间 |
| `unset`、`clear`、`isEmpty` | 移除键、清空容器和判断空容器 |
| `getKeys`、`getExpire` | 检查键集合和剩余游戏天数 |
| `expire` | 修改或取消已有键的过期时间 |
| `advance` | 由引擎推进过期计时 |

## 控制状态过期

过期时间使用游戏天。`set(key, value, 10f)` 让键在生涯推进约 10 游戏天后移除；`expire(key, days)` 可以重新设置已有键的寿命。

```java
sector.set(GearInMemoryKeys.KEY_PROBE, true, 0f);
sector.set(GearInMemoryKeys.KEY_REARM, "temporary", 10f);
sector.expire(GearInMemoryKeys.KEY_REARM, -1f);
```

`KEY_PROBE` 的剩余时间为 0。对话暂停生涯期间它仍可读取；恢复生涯推进后，Memory 的 `advance` 消耗过期时间并将它移除。`KEY_REARM` 原本拥有 10 天寿命，负数 `expire` 取消计时，所以 `getExpire(KEY_REARM)` 返回 `-1`。

```java
if (sector.contains(GearInMemoryKeys.KEY_PROBE)) {
    return;
}
sector.set(GearInMemoryKeys.KEY_READY, true);
LOG.info("GearInMemoryWriter: expire-zero probe removed; keys=" + sector.getKeys());
```

写入器在后续生涯帧等待 probe 消失，再写入 `KEY_READY`。这让对话器只在过期机制确实推进之后启动。`MemoryAPI.advance(...)` 由引擎调用，脚本只观察结果。

## 使用 MemKeys 组织槽位

Rules 收到的不是一个 Memory，而是 `Map<String, MemoryAPI>`。每个 `MemKeys` 常量命名一个槽位，变量前缀据此选择具体对象。

### MemKeys 速查表

| 常量 | 字符串值 | 当前对象 |
|---|---|---|
| `MemKeys.GLOBAL` | `global` | Sector 全局 Memory |
| `MemKeys.PLAYER` | `player` | 玩家 CharacterData Memory |
| `MemKeys.LOCAL` | `local` | 当前局部对象；人物对话中通常是 active person |
| `MemKeys.MARKET` | `market` | 当前交互市场 |
| `MemKeys.SOURCE_MARKET` | `sourceMarket` | 由 local 的 `$sourceMarket` 指定的来源市场 |
| `MemKeys.FACTION` | `faction` | 当前规则上下文势力 |
| `MemKeys.ENTITY` | `entity` | active person 对话背后的交互实体 |
| `MemKeys.PERSON_FACTION` | `personFaction` | active person 所属势力 |
| `MemKeys.MISSION` | `mission` | 当前 active mission 的 Memory |

### 对话插件建立标准槽位

`RuleBasedInteractionDialogPluginImpl` 根据交互目标组装 `GLOBAL`、`PLAYER`、`LOCAL`、`MARKET` 和 `FACTION`。目标 Memory 中的 `$sourceMarket` 保存市场 ID 时，插件再加入 `SOURCE_MARKET`。

对话器把 Jangala 的主实体作为交互目标，并把玩家人物设为 active person：

```java
SectorEntityToken target = market.getPrimaryEntity();
PersonAPI person = Global.getSector().getPlayerPerson();
target.setActivePerson(person);
CampaignUIAPI ui = Global.getSector().getCampaignUI();
boolean shown = ui.showInteractionDialog(
        new RuleBasedInteractionDialogPluginImpl(INITIAL_TRIGGER), target);
```

active person 会替换 `LOCAL`：裸变量从人物 Memory 读取；同时插件保留目标实体的 `ENTITY`，并加入人物势力的 `PERSON_FACTION`。于是人物、交互实体和人物势力可以在同一组规则中分别访问。

`MISSION` 只在对话插件设置 active mission 后出现。任务规则使用它保存任务实例自己的状态，普通实体对话不会拥有这一槽位。

### Rules 变量速查表

变量 `$memkey.name` 先用 `memkey` 查找槽位，再从该 Memory 读取实际键 `$name`。下面这些变量分别指向不同对象：

| Rules 写法 | 读取对象 | 实际 Memory 键 |
|---|---|---|
| `$global.GearIn_memory_global` | Sector | `$GearIn_memory_global` |
| `$player.GearIn_memory_player` | 玩家 CharacterData | `$GearIn_memory_player` |
| `$GearIn_memory_person` | LOCAL；本例为玩家人物 | `$GearIn_memory_person` |
| `$entity.GearIn_memory_entity` | Jangala 主实体 | `$GearIn_memory_entity` |
| `$market.GearIn_memory_market` | Jangala 市场 | `$GearIn_memory_market` |
| `$sourceMarket.GearIn_memory_market` | `$sourceMarket` 指向的 Jangala 市场 | `$GearIn_memory_market` |
| `$faction.GearIn_memory_faction` | Jangala 所属势力 | `$GearIn_memory_faction` |
| `$personFaction.GearIn_memory_faction` | 玩家人物所属势力 | `$GearIn_memory_faction` |
| `$mission.GearIn_memory_mission` | active mission | `$GearIn_memory_mission` |

裸变量没有点号，Rules 将它交给 `LOCAL`。人物对话里 `LOCAL` 是 active person，因此人物状态使用 `$GearIn_memory_person`，并不存在单独的 `person` 前缀。

## 在 Rules 中读取 Memory

Java 负责把状态写进正确对象，Rules 只需选择对应槽位。初始规则同时检查八个已建立的 Memory：

```csv
GearIn_memory_welcome,GearInMemoryDialog,"$global.GearIn_memory_ready
$player.GearIn_memory_player
$GearIn_memory_person == person-ready
$entity.GearIn_memory_entity == entity-ready
$market.GearIn_memory_market == market-ready
$sourceMarket.GearIn_memory_market == market-ready
$faction.GearIn_memory_faction == faction-ready
$personFaction.GearIn_memory_faction == person-faction-ready",,"全局：$global.GearIn_memory_global
玩家：$player.GearIn_memory_player
人物：$GearIn_memory_person
实体：$entity.GearIn_memory_entity
市场与来源市场：$market.GearIn_memory_market / $sourceMarket.GearIn_memory_market
当前势力与人物势力：$faction.GearIn_memory_faction / $personFaction.GearIn_memory_faction","GearIn_memory_verify:检查过期状态
GearIn_memory_leave:结束","C16-S5 Memory 槽位"
```

八条条件全部通过后，文本 token replacement 从六类对象读出实际值。玩家人物是 active person，所以裸 `$GearIn_memory_person` 从 `LOCAL` 取得 `person-ready`；`$entity.*` 仍指向 Jangala 主实体。

选择 "检查过期状态" 会写入一个过期时间为 0 的玩家键，并调用结果 trigger：

```csv
GearIn_memory_verify,DialogOptionSelected,$option == GearIn_memory_verify,"$player.GearIn_memory_lastAction = verify 0
FireBest GearInMemoryResult",,,C16-S5 检查入口
```

对话打开时生涯已经推进过一帧，Sector 上的 probe 已被移除，因此结果规则读取 `$global.*` 后显示数值和移除结果：

```csv
GearIn_memory_result_alive,GearInMemoryResult,"$global.GearIn_memory_number >= 2
!$global.GearIn_memory_probe",,"全局数值为 $global.GearIn_memory_number；probe 已在生涯恢复推进后移除。","GearIn_memory_leave:结束",C16-S5 推进后结果
```

任务槽位由 active mission 提供。任务对话可以用同样的方法读取任务 Memory：

```csv
GearIn_memory_mission,GearInMemoryMission,$mission.GearIn_memory_mission,,当前任务 Memory 已经进入 mission 槽位。,,C16-S5 active mission
```

这里的实际键仍是 `$GearIn_memory_mission`；`mission.` 只负责从 `memoryMap` 选择任务对象。

## 组装 Memory 状态

三个类各自承担一项职责：`GearInMemoryKeys` 统一键名，`GearInMemoryWriter` 写入并观察过期状态，`GearInMemoryDialog` 在状态准备完成后建立 Rules 对话。它们位于同一包中：

```text
mods/GearIndustry/
├── data/
│   └── campaign/
│       └── rules.csv
└── src/
    └── data/scripts/gearin/rules/
        ├── GearInMemoryKeys.java
        ├── GearInMemoryWriter.java
        └── GearInMemoryDialog.java
```

在 `onGameLoad(...)` 中注册两个 `EveryFrameScript`：

```java
Global.getSector().addScript(new GearInMemoryWriter());
Global.getSector().addScript(new GearInMemoryDialog());
```

写入器先设置 probe；引擎推进 Memory 并移除 probe 后，写入器设置 ready。对话器只观察 ready，因此两个脚本用明确状态衔接。

Memory 随所属生涯对象进入存档。永久键在读档后继续存在，限时键保留剩余寿命；对象本身消失时，其 Memory 也不再提供这些状态。

## 完整最小例

将以下规则追加到 `data/campaign/rules.csv`：

```csv
id,trigger,conditions,script,text,options,notes
GearIn_memory_welcome,GearInMemoryDialog,"$global.GearIn_memory_ready
$player.GearIn_memory_player
$GearIn_memory_person == person-ready
$entity.GearIn_memory_entity == entity-ready
$market.GearIn_memory_market == market-ready
$sourceMarket.GearIn_memory_market == market-ready
$faction.GearIn_memory_faction == faction-ready
$personFaction.GearIn_memory_faction == person-faction-ready",,"全局：$global.GearIn_memory_global
玩家：$player.GearIn_memory_player
人物：$GearIn_memory_person
实体：$entity.GearIn_memory_entity
市场与来源市场：$market.GearIn_memory_market / $sourceMarket.GearIn_memory_market
当前势力与人物势力：$faction.GearIn_memory_faction / $personFaction.GearIn_memory_faction","GearIn_memory_verify:检查过期状态
GearIn_memory_leave:结束","C16-S5 Memory 槽位"
GearIn_memory_verify,DialogOptionSelected,$option == GearIn_memory_verify,"$player.GearIn_memory_lastAction = verify 0
FireBest GearInMemoryResult",,,C16-S5 检查入口
GearIn_memory_result_expired,GearInMemoryResult,$global.GearIn_memory_probe,,过期时间为 0 的键仍然存在。,"GearIn_memory_leave:结束",C16-S5 暂停期结果
GearIn_memory_result_alive,GearInMemoryResult,"$global.GearIn_memory_number >= 2
!$global.GearIn_memory_probe",,"全局数值为 $global.GearIn_memory_number；probe 已在生涯恢复推进后移除。","GearIn_memory_leave:结束",C16-S5 推进后结果
GearIn_memory_mission,GearInMemoryMission,$mission.GearIn_memory_mission,,当前任务 Memory 已经进入 mission 槽位。,,C16-S5 active mission
GearIn_memory_leave,DialogOptionSelected,$option == GearIn_memory_leave,DismissDialog,,,C16-S5 结束
```

::: details 完整成品：GearInMemoryKeys

```java
package data.scripts.gearin.rules;

/** Memory keys shared by the Memory writer and Rules dialog. */
public final class GearInMemoryKeys {

    private GearInMemoryKeys() {
    }

    public static final String KEY_GLOBAL = "$GearIn_memory_global";
    public static final String KEY_PLAYER = "$GearIn_memory_player";
    public static final String KEY_PERSON = "$GearIn_memory_person";
    public static final String KEY_ENTITY = "$GearIn_memory_entity";
    public static final String KEY_MARKET = "$GearIn_memory_market";
    public static final String KEY_SOURCE_MARKET = "$sourceMarket";
    public static final String KEY_FACTION = "$GearIn_memory_faction";
    public static final String KEY_NUMBER = "$GearIn_memory_number";
    public static final String KEY_INTEGER = "$GearIn_memory_integer";
    public static final String KEY_LONG = "$GearIn_memory_long";
    public static final String KEY_VECTOR = "$GearIn_memory_vector";
    public static final String KEY_FLEET = "$GearIn_memory_fleet";
    public static final String KEY_OBJECT = "$GearIn_memory_object";
    public static final String KEY_PROBE = "$GearIn_memory_probe";
    public static final String KEY_REARM = "$GearIn_memory_rearm";
    public static final String KEY_READY = "$GearIn_memory_ready";
}
```

:::
::: details 完整成品：GearInMemoryWriter

```java
package data.scripts.gearin.rules;

import org.lwjgl.util.vector.Vector2f;

import org.apache.log4j.Logger;

import com.fs.starfarer.api.EveryFrameScript;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.characters.PersonAPI;

/** Writes typed Memory values and observes an expire-zero key. */
public class GearInMemoryWriter implements EveryFrameScript {

    private static final Logger LOG = Global.getLogger(GearInMemoryWriter.class);
    private int phase = 0;

    @Override
    public boolean isDone() {
        return phase >= 2;
    }

    @Override
    public boolean runWhilePaused() {
        return false;
    }

    @Override
    public void advance(float amount) {
        if (Global.getSector() == null || phase >= 2) {
            return;
        }
        MemoryAPI sector = Global.getSector().getMemoryWithoutUpdate();
        if (phase == 0) {
            writeValues(sector);
            phase = 1;
            return;
        }
        if (sector.contains(GearInMemoryKeys.KEY_PROBE)) {
            return;
        }
        sector.set(GearInMemoryKeys.KEY_READY, true);
        LOG.info("GearInMemoryWriter: expire-zero probe removed; keys=" + sector.getKeys());
        phase = 2;
    }

    private void writeValues(MemoryAPI sector) {
        sector.set(GearInMemoryKeys.KEY_GLOBAL, "sector-ready");
        sector.set(GearInMemoryKeys.KEY_NUMBER, 2.5f, 30f);
        sector.set(GearInMemoryKeys.KEY_INTEGER, 3);
        sector.set(GearInMemoryKeys.KEY_LONG, 4L);
        sector.set(GearInMemoryKeys.KEY_VECTOR, new Vector2f(10f, 20f));
        sector.set(GearInMemoryKeys.KEY_PROBE, true, 0f);
        sector.set(GearInMemoryKeys.KEY_REARM, "temporary", 10f);
        sector.expire(GearInMemoryKeys.KEY_REARM, -1f);

        CampaignFleetAPI fleet = Global.getSector().getPlayerFleet();
        if (fleet != null) {
            sector.set(GearInMemoryKeys.KEY_FLEET, fleet);
            sector.set(GearInMemoryKeys.KEY_OBJECT, fleet);
            fleet.getMemoryWithoutUpdate().set(GearInMemoryKeys.KEY_ENTITY, "fleet-ready");
        }
        Global.getSector().getCharacterData().getMemoryWithoutUpdate().set(
                GearInMemoryKeys.KEY_PLAYER, true);

        PersonAPI person = Global.getSector().getPlayerPerson();
        person.getMemoryWithoutUpdate().set(GearInMemoryKeys.KEY_PERSON, "person-ready");
        FactionAPI faction = person.getFaction();
        faction.getMemoryWithoutUpdate().set(
                GearInMemoryKeys.KEY_FACTION, "person-faction-ready");

        MarketAPI market = Global.getSector().getEconomy().getMarket("jangala");
        if (market != null && market.getPrimaryEntity() != null) {
            market.getMemoryWithoutUpdate().set(GearInMemoryKeys.KEY_MARKET, "market-ready");
            market.getFaction().getMemoryWithoutUpdate().set(
                    GearInMemoryKeys.KEY_FACTION, "faction-ready");
            SectorEntityToken entity = market.getPrimaryEntity();
            entity.getMemoryWithoutUpdate().set(GearInMemoryKeys.KEY_ENTITY, "entity-ready");
            entity.getMemoryWithoutUpdate().set(GearInMemoryKeys.KEY_SOURCE_MARKET, "jangala");
        }

        MemoryAPI scratch = Global.getFactory().createMemory();
        scratch.set("$temporary", true);
        scratch.unset("$temporary");
        scratch.set("$clearMe", true);
        scratch.clear();

        Global.getSector().getMemory();
        LOG.info("GearInMemoryWriter: string=" + sector.getString(GearInMemoryKeys.KEY_GLOBAL)
                + " number=" + sector.getFloat(GearInMemoryKeys.KEY_NUMBER)
                + " int=" + sector.getInt(GearInMemoryKeys.KEY_INTEGER)
                + " long=" + sector.getLong(GearInMemoryKeys.KEY_LONG)
                + " vector=" + sector.getVector2f(GearInMemoryKeys.KEY_VECTOR));
        LOG.info("GearInMemoryWriter: contains=" + sector.contains(GearInMemoryKeys.KEY_GLOBAL)
                + " is=" + sector.is(GearInMemoryKeys.KEY_PROBE, true)
                + " between=" + sector.between(GearInMemoryKeys.KEY_NUMBER, 2f, 3f)
                + " entity=" + sector.getEntity(GearInMemoryKeys.KEY_FLEET)
                + " fleet=" + sector.getFleet(GearInMemoryKeys.KEY_FLEET)
                + " object=" + sector.get(GearInMemoryKeys.KEY_OBJECT));
        LOG.info("GearInMemoryWriter: probeExpire=" + sector.getExpire(GearInMemoryKeys.KEY_PROBE)
                + " rearmExpire=" + sector.getExpire(GearInMemoryKeys.KEY_REARM)
                + " scratchEmpty=" + scratch.isEmpty());
    }
}
```

:::

::: details 完整成品：GearInMemoryDialog

```java
package data.scripts.gearin.rules;

import org.apache.log4j.Logger;

import com.fs.starfarer.api.EveryFrameScript;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignUIAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.characters.PersonAPI;
import com.fs.starfarer.api.impl.campaign.RuleBasedInteractionDialogPluginImpl;

/** Opens a Rules dialog after the Memory writer finishes. */
public class GearInMemoryDialog implements EveryFrameScript {

    public static final String INITIAL_TRIGGER = "GearInMemoryDialog";

    private static final Logger LOG = Global.getLogger(GearInMemoryDialog.class);

    private boolean done = false;

    @Override
    public boolean isDone() {
        return done;
    }

    @Override
    public boolean runWhilePaused() {
        return false;
    }

    @Override
    public void advance(float amount) {
        if (Global.getSector() == null || done) {
            return;
        }
        MemoryAPI sector = Global.getSector().getMemoryWithoutUpdate();
        if (!sector.getBoolean(GearInMemoryKeys.KEY_READY)) {
            return;
        }
        MarketAPI market = Global.getSector().getEconomy().getMarket("jangala");
        if (market == null || market.getPrimaryEntity() == null) {
            LOG.warn("GearInMemoryDialog: jangala interaction target unavailable");
            done = true;
            return;
        }
        SectorEntityToken target = market.getPrimaryEntity();
        PersonAPI person = Global.getSector().getPlayerPerson();
        target.setActivePerson(person);
        CampaignUIAPI ui = Global.getSector().getCampaignUI();
        boolean shown = ui.showInteractionDialog(
                new RuleBasedInteractionDialogPluginImpl(INITIAL_TRIGGER), target);
        if (shown) {
            LOG.info("GearInMemoryDialog: dialog shown=true target=" + target.getId());
            done = true;
        }
    }
}
```

:::

## 验证方案

前置数据：包含三个 Java 类、两项脚本注册和六条 `GearIn_memory_*` 规则的 MOD；测试存档可以进入 Jangala，并允许保存和读档。

1. 注册 `GearInMemoryWriter` 和 `GearInMemoryDialog`，构建 JAR 后确认 `GearInMemoryKeys`、`GearInMemoryWriter`、`GearInMemoryDialog` 三个类进入 `data/scripts/gearin/rules/`。
2. 启动包含六条 `GearIn_memory_*` 规则的生涯，确认日志加载 welcome、verify、result 和 leave。
3. 核对 writer 日志中的 `sector-ready`、`2.5`、`3`、`4`、向量 `(10, 20)`，以及 `contains`、`is`、`between` 的结果。
4. 核对 `getEntity`、`getFleet`、`get` 读到玩家舰队，且 probe 的 `getExpire` 为 `0`，rearm 的 `getExpire` 为 `-1`。
5. 恢复生涯推进，确认 probe 消失、writer 写入 ready，随后打开 Jangala 对话。
6. 在对话文本中核对 global、player、LOCAL、entity、market、sourceMarket、faction、personFaction 八个槽位的替换结果。
7. 选择 "检查过期状态"，确认结果规则显示全局数值，并说明 probe 已被移除。
8. 选择 "结束" 或按 ESC，关闭对话；继续推进后核对玩家侧 `$GearIn_memory_lastAction` 已移除。
9. 在设置 active mission 的任务对话中触发 `GearInMemoryMission`，确认 `$mission.*` 从任务 Memory 读取。
10. 保存并读档，确认永久键仍属于原对象，限时键按剩余游戏天数继续计时。
11. 核对示例只有一次显式 `getMemory()` facts 刷新，普通读写均使用 `getMemoryWithoutUpdate()`。

预期结果：

- 三个类与六条规则均完成加载，writer 日志中的类型化读取、判断和过期时间与写入值一致；
- Rules 对话显示八个标准槽位中的状态，任务上下文能够额外读取 `MISSION`；
- probe 与玩家一次性键在生涯恢复推进后移除，永久键和剩余过期时间随存档恢复。

## 故障分析

- **Java 写入时报键名错误**：键缺少 `$` 前缀。

    - **解决方案**：修正键常量后重新写入。

- **Rules 条件始终不命中**：变量前缀指向了错误槽位。

    - **解决方案**：按 `MemKeys` 表核对对象归属。

- **人物键为空**：对话未设置 active person，`LOCAL` 仍是交互目标。

    - **解决方案**：设置人物后重建对话上下文。

- **`$sourceMarket.*` 没有值**：local 的 `$sourceMarket` 没有有效市场 ID。

    - **解决方案**：写入目标市场 ID。

- **缺失键与显式 false 无法区分**：`getBoolean` 对两者都返回 `false`。

    - **解决方案**：先用 `contains` 判断键是否存在。

- **永久键意外过期**：调用了三参数 `set` 或正数 `expire`。

    - **解决方案**：使用两参数 `set` 清除过期时间。

- **过期时间为 0 的键仍可读**：对话暂停了生涯推进。

    - **解决方案**：退出对话后恢复推进。

- **普通读取持续触发 facts**：调用点使用了 `getMemory()`。

    - **解决方案**：自维护键改用 `getMemoryWithoutUpdate()`。

- **读档后对象键消失**：实体引用无法恢复。

    - **解决方案**：保存稳定 ID 并在读档后重新查询。

## 总结

`MemoryAPI` 将状态归属于具体生涯对象。普通 MOD 状态使用 `getMemoryWithoutUpdate()` 读写，需要最新 facts 时再使用 `getMemory()`；`set`、类型化读取、判断方法和过期控制共同决定值、存在性与寿命。

Rules 通过 `MemKeys` 槽位读取这些对象：`$global.*`、`$player.*`、`$entity.*`、`$market.*`、`$sourceMarket.*`、`$faction.*`、`$personFaction.*`、`$mission.*` 选择指定 Memory，裸变量读取 `LOCAL`。Java 写入的对象归属和 Rules 读取的槽位一致时，同一状态可用于条件、文本和后续对话。

## 建议参考项

- `com.fs.starfarer.api.campaign.rules.MemoryAPI`：键值读写、类型化读取、判断和过期。
- `com.fs.starfarer.api.campaign.rules.HasMemory`：`getMemory()` 与 `getMemoryWithoutUpdate()`。
- `com.fs.starfarer.api.campaign.rules.MemKeys`：九个 Rules memoryMap 槽位。
- `com.fs.starfarer.api.impl.campaign.RuleBasedInteractionDialogPluginImpl`：对话槽位组装与 active person。
- `com.fs.starfarer.api.campaign.SectorAPI`：Sector 和玩家 Memory 入口。
- `com.fs.starfarer.api.campaign.SectorEntityToken`：实体 Memory 与 active person。
- `com.fs.starfarer.api.campaign.econ.MarketAPI`：市场 Memory 入口。
- `com.fs.starfarer.api.characters.PersonAPI`：人物 Memory 入口。
- `com.fs.starfarer.api.campaign.FactionAPI`：势力 Memory 入口。
- `data/campaign/rules.csv`：`GearIn_memory_*` 规则数据。
