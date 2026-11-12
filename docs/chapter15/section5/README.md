# 使用 `MemoryAPI` 保存状态

Rules 里的变量最终来自某个对象的 Memory。同名键写在人物、市场或星区上，含义可以完全不同；对话切换人物以后，裸变量的读取对象也会改变。本节用一组状态把这些关系接起来：Java 写入，生涯推进期限，Jangala 的对话再分别读出各个对象上的值。

_本节预期阅读时间：\~35 分钟。_

_本节预期操作时间：\~60 分钟。_

## 学习目标

完成后，您将能够：

- 从 `Sector`、玩家、舰队、实体、市场、人物和势力取得 `MemoryAPI`；
- 根据是否需要刷新 `facts` 选择 `getMemory()` 或 `getMemoryWithoutUpdate()`；
- 写入并读取字符串、数值、布尔值、向量和生涯对象；
- 使用存在性、相等和区间方法判断状态；
- 控制键的游戏天过期时间；
- 根据 `MemKeys` 判断 Rules 变量实际读取哪个对象。

## 前置知识

- [C7-S1] 的 `ModPlugin` 与 `onGameLoad`；
- [C10-S1] 的 `EveryFrameScript` 注册和推进；
- [C14-S3] 的交互目标与 `active` `person`；
- [C15-S2] 的 Rules 变量、条件和选项回流；
- [C15-S3] 的原版场景如何组装 Rules 上下文；
- [C15-S4] 的 `Token` 如何从 `memoryMap` 解析变量。

## 为状态选择持有者

保存本节三个类到 `src/data/scripts/gearin/rules/`，将写入器与对话器加入 `GearInModPlugin` 的 `import`。在既有 `onGameLoad(...)` 追加以下局部登记，沿用 `sector`：

```java
if (!sector.hasTransientScript(GearInMemoryWriter.class)) {
    sector.addTransientScript(new GearInMemoryWriter());
}
if (!sector.hasTransientScript(GearInMemoryDialog.class)) {
    sector.addTransientScript(new GearInMemoryDialog());
}
```

写入器和对话器都是读档后创建的临时脚本；它们读写的状态却属于存档中的生涯对象。写入器先清除旧 `ready`，写入 `probe`，等它实际过期后再允许对话启动。脚本的寿命与状态的寿命在这里已经分开了。

**Memory 的归属由持有它的对象决定。**`HasMemory` 提供取得 Memory 的入口：市场状态从市场取得，人物状态从人物取得，全局生涯状态从 `Sector` 取得。选定对象以后，再讨论键名、类型和期限才有意义。

### 直接取得当前 Memory

普通 MOD 状态不依赖原版动态 `facts`，直接取得当前 Memory 即可。写入器首先从 `Sector` 取得全局 Memory：

```java
MemoryAPI sector = Global.getSector().getMemoryWithoutUpdate();
```

玩家与玩家人物尤其值得区分。`CharacterData` 保存玩家状态，`PersonAPI` 保存这个人物的状态；`FactionAPI` 又有自己的 Memory。它们可以使用同名键，存储仍彼此独立：

```java
Global.getSector().getCharacterData().getMemoryWithoutUpdate().set(
        GearInMemoryKeys.KEY_PLAYER, true);

PersonAPI person = Global.getSector().getPlayerPerson();
person.getMemoryWithoutUpdate().set(GearInMemoryKeys.KEY_PERSON, "person-ready");
FactionAPI faction = person.getFaction();
faction.getMemoryWithoutUpdate().set(
        GearInMemoryKeys.KEY_FACTION, "person-faction-ready");
```

接着给 Jangala 的市场与主实体分别写值。两者虽共同表现为一个殖民地，Memory 仍各自持有。实体上的 `$sourceMarket` 另有用途：它保存市场 ID，标准对话据此建立来源市场槽位。这份联系也由写入器准备：

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

上述调用使用 `getMemoryWithoutUpdate()`，取得对象当前持有的 Memory。示例自己的键由写入器维护，读取时无需要求生涯插件重新生成 `facts`。

玩家舰队同样是生涯实体。下面的代码同时演示两种归属：`KEY_ENTITY` 写在舰队自身，`KEY_FLEET` 与 `KEY_OBJECT` 写在 `Sector` Memory，值都指向玩家舰队。

```java
CampaignFleetAPI fleet = Global.getSector().getPlayerFleet();
if (fleet != null) {
    sector.set(GearInMemoryKeys.KEY_FLEET, fleet);
    sector.set(GearInMemoryKeys.KEY_OBJECT, fleet);
    fleet.getMemoryWithoutUpdate().set(GearInMemoryKeys.KEY_ENTITY, "fleet-ready");
}
```

### 读取动态 `facts` 时再刷新

`getMemory()` 会让已注册的 `CampaignPlugin` 更新对应对象的 Rules `facts`。`Sector`、实体、市场、人物和势力分别进入 `updateGlobalFacts`、`updateEntityFacts`、`updateMarketFacts`、`updatePersonFacts` 或 `updateFactionFacts`；返回的仍是该对象持有的 Memory。

示例只调用一次 `Sector` 的刷新入口：

```java
Global.getSector().getMemory();
```

这次调用会刷新 `$isDevMode`、`$daysSinceStart` 一类全局 `facts`。因此，读取动态事实时用 `getMemory()`，读取和写入 MOD 自己维护的键时用 `getMemoryWithoutUpdate()`。这两个方法决定是否刷新内容，状态的持有者始终是原对象。

## 写入值，并按实际含义读取

Memory 键在 Java 中包含开头的 `$`。例如 `"$GearIn_memory_global"` 是实际键名；Rules 中的 `global.` 是选择 Memory 槽位的语法，不会存进键名。

### 写入永久值和限时值

两参数 `set(...)` 写入无期限的值，三参数形式再提供以游戏天计的有效期。写入器给常用类型各准备一个值，其中数值 `2.5f` 保留 30 天：

```java
sector.set(GearInMemoryKeys.KEY_GLOBAL, "sector-ready");
sector.set(GearInMemoryKeys.KEY_NUMBER, 2.5f, 30f);
sector.set(GearInMemoryKeys.KEY_INTEGER, 3);
sector.set(GearInMemoryKeys.KEY_LONG, 4L);
sector.set(GearInMemoryKeys.KEY_VECTOR, new Vector2f(10f, 20f));
```

这里的字符串、整数、长整数和向量不会过期。`KEY_NUMBER` 则从写入时开始消耗 30 游戏天寿命。**再次用两参数 `set` 写入同一个键，会清除原有期限。** 更新数值时若仍需要限时状态，就要同时处理它的剩余寿命。

### 按当前类型读取状态

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

玩家舰队既是生涯实体，也是舰队，因此同一个值可以由 `getEntity` 或 `getFleet` 读取。日志里的判断还确认了全局键存在、`probe` 为 `true`，以及 `2.5` 落在 `[2, 3]` 内。

::: warning 读档后的舰队引用
`getFleet()` 本身不触发 Memory 中实体引用的恢复。读档后首次访问这份 Memory 时，先由 `get()`、`getEntity()` 或 `contains()` 完成恢复，再读取舰队；无法找到的实体引用会被移除。
:::

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

`getKeys()` 返回当前全部键，写入器在准备完成时把它们记录到日志。`getExpire(key)` 则返回剩余游戏天数；无期限键没有过期记录，返回 `-1`。

### `MemoryAPI` 常用方法速查表

| 方法组                                                            | 直接结果                           |
| ----------------------------------------------------------------- | ---------------------------------- |
| `set(key, value)`                                                 | 写入永久键，并清除该键原有过期时间 |
| `set(key, value, expire)`                                         | 写入值并设置游戏天寿命             |
| `get`、`getString`、`getFloat`、`getInt`、`getLong`、`getBoolean` | 读取原始对象或基础类型             |
| `getVector2f`、`getEntity`、`getFleet`                            | 读取向量或生涯对象                 |
| `contains`、`is`、`between`                                       | 判断存在性、相等关系和闭区间       |
| `unset`、`clear`、`isEmpty`                                       | 移除键、清空容器和判断空容器       |
| `getKeys`、`getExpire`                                            | 检查键集合和剩余游戏天数           |
| `expire`                                                          | 修改或取消已有键的过期时间         |
| `advance`                                                         | 由引擎推进过期计时                 |

## 等待生涯实际推进期限

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

写入器在后续生涯帧检测 `probe`，消失后才设置 `KEY_READY`。对话器据此启动，初始规则便能读到确实过期后的状态。`getMemory()` 的事实刷新与这次推进各有职责，刷新一次并不能让零期限键立即消失。

编者更倾向于用实际状态衔接这两个脚本，而不依赖它们的登记顺序。期限由引擎推进；写入器确认移除，对话器确认准备完成，各自的条件都能独立说明为何继续执行。

## 让对话读到对应对象

状态已经写好，Rules 如何选择其中一份？对话交给规则的是 `Map<String, MemoryAPI>`，其中每个槽位指向一份 Memory。`MemKeys` 规定槽位名称，变量前缀选择槽位，余下部分才是对象上的实际键。

### `MemKeys` 速查表

| 常量                     | 字符串值        | 当前对象                                         |
| ------------------------ | --------------- | ------------------------------------------------ |
| `MemKeys.GLOBAL`         | `global`        | `Sector` 全局 Memory                             |
| `MemKeys.PLAYER`         | `player`        | 玩家 `CharacterData` Memory                      |
| `MemKeys.LOCAL`          | `local`         | 当前局部对象；人物对话中通常是 `active` `person` |
| `MemKeys.MARKET`         | `market`        | 当前交互市场                                     |
| `MemKeys.SOURCE_MARKET`  | `sourceMarket`  | 由交互实体的 `$sourceMarket` 指定的来源市场      |
| `MemKeys.FACTION`        | `faction`       | 当前规则上下文势力                               |
| `MemKeys.ENTITY`         | `entity`        | `active` `person` 对话背后的交互实体             |
| `MemKeys.PERSON_FACTION` | `personFaction` | `active` `person` 所属势力                       |
| `MemKeys.MISSION`        | `mission`       | 当前 `active` `mission` 的 Memory                |

### 对话插件建立标准槽位

`RuleBasedInteractionDialogPluginImpl` 根据交互目标组装 `GLOBAL`、`PLAYER`、`LOCAL`、`MARKET` 和 `FACTION`。目标 Memory 中的 `$sourceMarket` 保存市场 ID 时，插件再加入 `SOURCE_MARKET`。

对话器把 Jangala 的主实体作为交互目标，并把玩家人物设为 `active` `person`：

```java
SectorEntityToken target = market.getPrimaryEntity();
PersonAPI person = Global.getSector().getPlayerPerson();
target.setActivePerson(person);
CampaignUIAPI ui = Global.getSector().getCampaignUI();
boolean shown = ui.showInteractionDialog(
        new RuleBasedInteractionDialogPluginImpl(INITIAL_TRIGGER), target);
```

设置活动人物以后，`LOCAL` 改为人物 Memory，裸变量随之改从人物读取。插件同时保留目标实体的 `ENTITY`，并加入人物势力的 `PERSON_FACTION`。因此，同一段对话可以分别读取人物、实体和人物势力。

`MISSION` 只在对话插件设置 `active` `mission` 后出现。任务规则使用它保存任务实例自己的状态，普通实体对话不会拥有这一槽位。

### Rules 变量速查表

变量 `$memkey.name` 先用 `memkey` 查找槽位，再从该 Memory 读取实际键 `$name`。下面这些变量分别指向不同对象：

| Rules 写法                             | 读取对象                            | 实际 Memory 键           |
| -------------------------------------- | ----------------------------------- | ------------------------ |
| `$global.GearIn_memory_global`         | `Sector`                            | `$GearIn_memory_global`  |
| `$player.GearIn_memory_player`         | 玩家 `CharacterData`                | `$GearIn_memory_player`  |
| `$GearIn_memory_person`                | LOCAL；本例为玩家人物               | `$GearIn_memory_person`  |
| `$entity.GearIn_memory_entity`         | Jangala 主实体                      | `$GearIn_memory_entity`  |
| `$market.GearIn_memory_market`         | Jangala 市场                        | `$GearIn_memory_market`  |
| `$sourceMarket.GearIn_memory_market`   | `$sourceMarket` 指向的 Jangala 市场 | `$GearIn_memory_market`  |
| `$faction.GearIn_memory_faction`       | Jangala 所属势力                    | `$GearIn_memory_faction` |
| `$personFaction.GearIn_memory_faction` | 玩家人物所属势力                    | `$GearIn_memory_faction` |
| `$mission.GearIn_memory_mission`       | `active` `mission`                  | `$GearIn_memory_mission` |

裸变量没有点号，Rules 将它交给 `LOCAL`。人物对话里 `LOCAL` 是 `active` `person`，因此人物状态使用 `$GearIn_memory_person`，并不存在单独的 `person` 前缀。

## 检查条件并替换对话文本

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
GearIn_memory_leave:结束","C15-S5 Memory 槽位"
```

八条条件共同确认状态写入和槽位组装成功，文本再用变量替换读出六类对象上的值。玩家人物是活动人物，裸 `$GearIn_memory_person` 从 `LOCAL` 取得 `person-ready`；`$entity.*` 仍指向 Jangala 主实体。两份 Memory 同时参与对话，但承担不同含义。

选择 "检查过期状态" 会写入一个过期时间为 0 的玩家键，并调用结果 `trigger`：

```csv
GearIn_memory_verify,DialogOptionSelected,$option == GearIn_memory_verify,"$player.GearIn_memory_lastAction = verify 0
FireBest GearInMemoryResult",,,C15-S5 检查入口
```

对话打开时生涯已经推进过一帧，`Sector` 上的 `probe` 已被移除，因此结果规则读取 `$global.*` 后显示数值和移除结果：

```csv
GearIn_memory_result_alive,GearInMemoryResult,"$global.GearIn_memory_number >= 2
!$global.GearIn_memory_probe",,"全局数值为 $global.GearIn_memory_number；probe 已在生涯恢复推进后移除。","GearIn_memory_leave:结束",C15-S5 推进后结果
```

任务槽位由 `active` `mission` 提供。任务对话可以用同样的方法读取任务 Memory：

```csv
GearIn_memory_mission,GearInMemoryMission,$mission.GearIn_memory_mission,,当前任务 Memory 已经进入 mission 槽位。,,C15-S5 active mission
```

这里的实际键仍是 `$GearIn_memory_mission`；`mission.` 只负责从 `memoryMap` 选择任务对象。

## 接入脚本，并恢复临时人物

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

本节开头的登记位于 `onGameLoad(...)`。两个脚本在每次加载存档后创建，已有同类临时脚本时沿用当前实例：

```java
if (!sector.hasTransientScript(GearInMemoryWriter.class)) {
    sector.addTransientScript(new GearInMemoryWriter());
}
if (!sector.hasTransientScript(GearInMemoryDialog.class)) {
    sector.addTransientScript(new GearInMemoryDialog());
}
```

`GearInMemoryWriter` 完成写入并等待 `probe` 消失，`GearInMemoryDialog` 等待 `ready`，随后临时把玩家人物设为活动人物。为了让八个槽位的比较不影响原对话，它在修改前保存原人物，关闭选项与 ESC 使用同一选项 ID，关闭前恢复人物；界面未能打开时也立即恢复，下一次回调再尝试。

**人物的临时切换随对话结束，Memory 中的状态随持有者保存。** 无期限键读档后继续存在，限时键保留剩余寿命；对象本身消失时，也就失去了从该对象取得状态的入口。

## 完整最小例

把下面六条规则追加到已有 `data/campaign/rules.csv`，保留原表头。三个类与登记一并构建。

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
GearIn_memory_leave:结束","C15-S5 Memory 槽位"
GearIn_memory_verify,DialogOptionSelected,$option == GearIn_memory_verify,"$player.GearIn_memory_lastAction = verify 0
FireBest GearInMemoryResult",,,C15-S5 检查入口
GearIn_memory_result_expired,GearInMemoryResult,$global.GearIn_memory_probe,,过期时间为 0 的键仍然存在。,"GearIn_memory_leave:结束",C15-S5 暂停期结果
GearIn_memory_result_alive,GearInMemoryResult,"$global.GearIn_memory_number >= 2
!$global.GearIn_memory_probe",,"全局数值为 $global.GearIn_memory_number；probe 已在生涯恢复推进后移除。","GearIn_memory_leave:结束",C15-S5 推进后结果
GearIn_memory_mission,GearInMemoryMission,$mission.GearIn_memory_mission,,当前任务 Memory 已经进入 mission 槽位。,,C15-S5 active mission
GearIn_memory_leave,DialogOptionSelected,$option == GearIn_memory_leave,DismissDialog,,,C15-S5 结束
```

::: details 完整成品：GearInMemoryKeys

```java
package data.scripts.gearin.rules;

/** 写入器与规则对话共用的 Memory 键。 */
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

import org.apache.log4j.Logger;
import org.lwjgl.util.vector.Vector2f;

import com.fs.starfarer.api.EveryFrameScript;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.characters.PersonAPI;

/** 写入各对象状态，并等待零期限键过期。 */
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
        MemoryAPI sector = Global.getSector().getMemoryWithoutUpdate();
        if (phase == 0) {
            sector.unset(GearInMemoryKeys.KEY_READY);
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
import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.characters.PersonAPI;
import com.fs.starfarer.api.impl.campaign.RuleBasedInteractionDialogPluginImpl;

/** 状态准备完成后打开规则对话，并恢复临时人物。 */
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
        PersonAPI previousPerson = target.getActivePerson();
        PersonAPI person = Global.getSector().getPlayerPerson();
        target.setActivePerson(person);
        CampaignUIAPI ui = Global.getSector().getCampaignUI();
        RuleBasedInteractionDialogPluginImpl plugin =
                new RuleBasedInteractionDialogPluginImpl(INITIAL_TRIGGER) {
                    @Override
                    public void init(InteractionDialogAPI dialog) {
                        super.init(dialog);
                        dialog.setOptionOnEscape("结束", "GearIn_memory_leave");
                    }

                    @Override
                    public void optionSelected(String optionText, Object optionData) {
                        if ("GearIn_memory_leave".equals(optionData)) {
                            target.setActivePerson(previousPerson);
                        }
                        super.optionSelected(optionText, optionData);
                    }
                };
        boolean shown = ui.showInteractionDialog(plugin, target);
        if (shown) {
            LOG.info("GearInMemoryDialog: dialog shown=true target=" + target.getId());
            done = true;
        } else {
            target.setActivePerson(previousPerson);
        }
    }
}
```

:::

## 验证方案

前置数据：包含三个 Java 类、两项脚本注册和六条 `GearIn_memory_*` 规则的 MOD；测试存档可以进入 Jangala，并允许保存和读档。

1. 注册 `GearInMemoryWriter` 和 `GearInMemoryDialog`，构建 JAR 后确认 `GearInMemoryKeys`、`GearInMemoryWriter`、`GearInMemoryDialog` 三个类进入 `data/scripts/gearin/rules/`。
2. 启动包含六条 `GearIn_memory_*` 规则的生涯，确认日志加载 `welcome`、`verify`、`result` 和 `leave`。
3. 核对 `writer` 日志中的 `sector-ready`、`2.5`、`3`、`4`、向量 `(10, 20)`，以及 `contains`、`is`、`between` 的结果。
4. 核对 `getEntity`、`getFleet`、`get` 读到玩家舰队，且 `probe` 的 `getExpire` 为 `0`，`rearm` 的 `getExpire` 为 `-1`。
5. 恢复生涯推进，确认 `probe` 消失、`writer` 写入 `ready`，随后打开 Jangala 对话。
6. 在对话文本中核对 `global`、`player`、LOCAL、`entity`、`market`、`sourceMarket`、`faction`、`personFaction` 八个槽位的替换结果。
7. 选择 "检查过期状态"，确认结果规则显示全局数值，并说明 `probe` 已被移除。
8. 选择 "结束" 或按 ESC，确认原 `active` `person` 恢复，再让生涯推进，核对玩家侧临时键已移除。
9. 在设置 `active` `mission` 的任务对话中触发 `GearInMemoryMission`，确认 `$mission.*` 从任务 Memory 读取。
10. 保存并读档，确认永久键仍属于原对象，限时键按剩余游戏天数继续计时。
11. 写入器显式刷新一次星区 `facts`，规则对话初始化另按其标准上下文刷新相关对象；自有状态的普通写入继续用 `getMemoryWithoutUpdate()`。

预期结果：

- 三个类与六条规则均完成加载，`writer` 日志中的类型化读取、判断和过期时间与写入值一致；
- Rules 对话显示八个标准槽位中的状态，任务上下文能够额外读取 `MISSION`；
- `probe` 与玩家一次性键在生涯恢复推进后移除，永久键和剩余过期时间随存档恢复。

## 故障分析

- **Java 写入时报键名错误**：键缺少 `$` 前缀。
    - **解决方案**：修正键常量后重新写入。

- **Rules 条件始终不命中**：变量前缀指向了错误槽位。
    - **解决方案**：按 `MemKeys` 表核对对象归属。

- **人物键为空**：对话未设置 `active` `person`，`LOCAL` 仍是交互目标。
    - **解决方案**：设置人物后重建对话上下文。

- **`$sourceMarket.*` 没有值**：交互实体的 `$sourceMarket` 没有有效市场 ID。
    - **解决方案**：写入目标市场 ID。

- **缺失键与显式 `false` 无法区分**：`getBoolean` 对两者都返回 `false`。
    - **解决方案**：先用 `contains` 判断键是否存在。

- **永久键意外过期**：调用了三参数 `set` 或正数 `expire`。
    - **解决方案**：使用两参数 `set` 清除过期时间。

- **过期时间为 0 的键仍可读**：对话暂停了生涯推进。
    - **解决方案**：退出对话后恢复推进。

- **普通读取持续触发 `facts`**：调用点使用了 `getMemory()`。
    - **解决方案**：自维护键改用 `getMemoryWithoutUpdate()`。

- **读档后对象键消失**：实体引用无法恢复。
    - **解决方案**：保存稳定 ID 并在读档后重新查询。

## 总结

Memory 让 Java 与 Rules 共用对象上的状态。持有者决定状态归属，写入方法决定值和期限，对话槽位决定规则从哪里读取。示例用零期限键衔接生涯推进与界面打开，同时把临时人物切换的恢复留在对话结束处；存档中的状态与运行中的脚本由此各有明确寿命。

## 建议参考项

- `com.fs.starfarer.api.campaign.rules.MemoryAPI`：键值读写、类型化读取、判断和过期。
- `com.fs.starfarer.api.campaign.rules.HasMemory`：`getMemory()` 与 `getMemoryWithoutUpdate()`。
- `com.fs.starfarer.api.campaign.rules.MemKeys`：九个 Rules `memoryMap` 槽位。
- `com.fs.starfarer.api.impl.campaign.RuleBasedInteractionDialogPluginImpl`：对话槽位组装与 `active` `person`。
- `com.fs.starfarer.api.campaign.SectorAPI`：`Sector` 和玩家 Memory 入口。
- `com.fs.starfarer.api.campaign.SectorEntityToken`：实体 Memory 与 `active` `person`。
- `com.fs.starfarer.api.campaign.econ.MarketAPI`：市场 Memory 入口。
- `com.fs.starfarer.api.characters.PersonAPI`：人物 Memory 入口。
- `com.fs.starfarer.api.campaign.FactionAPI`：势力 Memory 入口。
- `data/campaign/rules.csv`：`GearIn_memory_*` 规则数据。
