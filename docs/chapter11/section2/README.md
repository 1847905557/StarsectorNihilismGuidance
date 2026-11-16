# 管理舰队中的成员

舰队工厂会一并配好人员，每次配出来的都不一样。要玩家两次遇到的是同一批人，就得把这一步关掉自己来。

麻烦在于旗舰、指挥官、舰长、军官分属不同的对象，设错了不会报错，只会在舰队信息里显示成空着。

---

_本节预期阅读时间：\~15 分钟。_

_本节预期操作时间：\~20 分钟。_

## 前置知识

- [C11-S1] 的巡逻舰队管理器，本节修改其中的 `spawnFleet()`；
- [C3-S2] 的工匠装配 `GearIn_artizan_combat`，它将作为旗舰；
- [C10-S1] 的 Memory 与脚本，用于理解舰队对象的归属。

## 一个成员是什么

`FleetMemberAPI` 是舰队里的一个位置：一艘船、一份装配、一名舰长，加上一套状态。这三样东西不在同一个对象上。

| 要读什么 | 从哪里取 |
| --- | --- |
| 船体与装配 | `getHullSpec()`、`getVariant()` |
| 状态与损伤 | `getStatus()`、`getRepairTracker()` |
| 舰长 | `getCaptain()` |
| 数值 | `getStats()` |

**`FleetMemberAPI` 上没有 `getCR()` 与 `setCR(...)`，也没有 `setStatus(...)`。** CR 在维修跟踪器上，状态对象只能读不能换。想改成员的状态，改的是 `getStatus()` 拿到的那个对象的内部，或者改 `getRepairTracker()` 上的值。

成员是自己在舰队里的位置，所以 `getFleetData()` 返回它所属的舰队。同一艘船体在两支舰队里是两个成员，各有一份状态。

## 成员从哪里来

先有装配，再有成员：

```java
FleetMemberAPI member = Global.getFactory().createFleetMember(FleetMemberType.SHIP, variantId);
member.setShipName(fleet.getFleetData().pickShipName(member, random));
fleet.getFleetData().addFleetMember(member);
```

`createFleetMember(...)` 只造对象，不进舰队。`addFleetMember(FleetMemberAPI)` 才把它放进去。

还有一个省事的重载：

```java
FleetMemberAPI member = fleet.getFleetData().addFleetMember(variantId);
```

**它一步完成创建与加入，返回新建的成员。** 船名与 ID 由游戏生成，拿到返回值再改。原版的 `EconomyFleetAssignmentAI` 建完立刻把成员封存，用的就是这个重载。

**成员没有船名，玩家在舰队界面看到的就是空的。** 名字要靠 `setShipName(...)` 给，取名的正规入口是 `getFleetData().pickShipName(member, random)`，它按势力的命名规则生成一个。

`FleetMemberType` 决定这是舰船、战机联队还是别的。战机联队用 `FleetMemberType.FIGHTER_WING`，它不占舰船位置，也不设舰长。

## 舰长与军官

舰长是成员上的一个人物，军官是舰队上的人物名单。两者是分开的：

```java
member.getCaptain();
member.setCaptain(person);

fleet.getFleetData().addOfficer(person);
fleet.getFleetData().getOfficersCopy();
fleet.getFleetData().removeOfficer(person);
```

`addOfficer(...)` 把人物登记进舰队名单，`setCaptain(...)` 再把他派到具体成员。**只做其中一步，舰队信息里的军官名单与舰船上的舰长就对不上。**

舰队的指挥官是另一个位置：`getCommander()` 与 `setCommander(...)`。指挥官通常也是旗舰的舰长，但游戏不会把两者绑在一起。

原版的舰队工厂会一并配好人员。要自己指定，用 `OfficerManagerEvent.createOfficer(...)` 抽一个人物，完整签名是：

```java
PersonAPI createOfficer(FactionAPI faction, int level, SkillPickPreference pref,
        boolean allowNonDoctrinePersonality, CampaignFleetAPI fleet, boolean allowAnyLevel,
        boolean withEliteSkills, int eliteSkillsNumOverride, Random random);
```

原版配指挥官时 `fleet` 传 `null`，配僚舰军官时传舰队本身，其余参数相同。取人的等级与技能倾向由调用方决定，等级越高技能越多。

### 旗舰设在成员上还是舰队上

旗舰有两种设法，作用范围不同：

```java
fleet.getFleetData().setFlagship(member);   // 舰队数据上的，改舰队级关系
member.setFlagship(true);                   // 成员上的，改这一个标记
```

`FleetDataAPI.setFlagship(...)` 处理的是舰队与成员之间的关系，`FleetMemberAPI.setFlagship(boolean)` 只改这个成员自己的标记。原版在批量设定时用的是后者，逐个成员比较 `member == flagship` 再传布尔值。

`FleetDataAPI` 上另有一个 `ensureHasFlagship()`，舰队没有旗舰时用它补一个，比手工挑一艘再设更省事。

上面这些是成员层的东西。接下来给巡逻队配人。

```java
params.withOfficers = false;
CampaignFleetAPI fleet = FleetFactoryV3.createFleet(params);
```

本节关掉它，因为工厂挑旗舰的依据是船体等级与点数，不由我们控制。`FleetParamsV3` 也有 `flagshipVariantId`，但它只是在工厂选定旗舰之后替换其装配，并不指定船体：舰队里没有对应船体时，船体与装配会对不上。直接自己加入旗舰，行为才是确定的。

## 关掉工厂配的人

`FleetFactoryV3.createFleet(...)` 抽完舰船之后还会配人：挑一艘作旗舰，创建指挥官，再按数量给其余舰船配军官。`FleetParamsV3.withOfficers` 为 `false` 时这一整步跳过：

```java
params.withOfficers = false;
CampaignFleetAPI fleet = FleetFactoryV3.createFleet(params);
```

巡逻队要的是每次都一样的旗舰与指挥官，工厂挑人的依据是船体等级与点数，不由我们控制，所以关掉它自己配。

`FleetParamsV3` 上还有 `flagshipVariantId`，但它替换的是工厂已经选定那艘旗舰的装配，不改变选中哪一艘。舰队里没有对应船体时，船体与装配对不上。自己加旗舰，结果才是确定的。

## 巡逻队的旗舰

旗舰用装配 ID 创建，交给舰队，再设为旗舰：

```java
FleetMemberAPI flagship = Global.getFactory().createFleetMember(FleetMemberType.SHIP, FLAGSHIP_VARIANT);
flagship.setShipName(fleet.getFleetData().pickShipName(flagship, random));
fleet.getFleetData().addFleetMember(flagship);
fleet.getFleetData().setFlagship(flagship);
```

`FLAGSHIP_VARIANT` 是 `"GearIn_artizan_combat"`，[C3-S2] 的工匠战斗装配。

**`setFlagship(...)` 要在给其他成员配军官之前调用。** 它会遍历全体成员，把玩家本人或舰队指挥官担任舰长的那些换成新的 `Person("steady")`。先配好的军官如果正好在这一类里，会被换掉。设完旗舰再配人，顺序就不会出问题。

## 巡逻队的指挥官

巡逻队要一个固定的指挥官，姓名、性格、职务都写死：

```java
PersonAPI person = OfficerManagerEvent.createOfficer(
        Global.getSector().getFaction(GearInModPlugin.FACTION_ID),
        COMMANDER_LEVEL, SkillPickPreference.ANY, true, null, true, true, -1, random);
person.setName(new FullName("Brann", "Hale", FullName.Gender.ANY));
person.setPersonality(Personalities.AGGRESSIVE);
person.setRankId(Ranks.SPACE_COMMANDER);
person.setPostId(Ranks.POST_FLEET_COMMANDER);
fleet.setCommander(person);
flagship.setCaptain(person);
```

`COMMANDER_LEVEL` 取 4，技能数量跟着等级走，具体技能由 `SkillPickPreference.ANY` 决定。性格取 `AGGRESSIVE`，与 [C11-S1] 的 "遇强固守" 标记配合：主动靠近较弱的对手，遇到明显更强的就地固守。

`setCommander(...)` 与 `setCaptain(...)` 两个都要写。只写前者，舰队信息里有人，旗舰实际由无名人物驾驶。

## 巡逻队的军官

旗舰以外的成员没有军官，给点数最高的一艘配一名：

```java
PersonAPI officer = OfficerManagerEvent.createOfficer(
        Global.getSector().getFaction(GearInModPlugin.FACTION_ID),
        OFFICER_LEVEL, SkillPickPreference.ANY, true, fleet, true, true, -1, random);
officer.setPostId(Ranks.POST_OFFICER);
fleet.getFleetData().addOfficer(officer);
target.setCaptain(officer);
```

`OFFICER_LEVEL` 取 2，低于指挥官。这一处 `createOfficer(...)` 的 `fleet` 参数传舰队本身，配指挥官时传的是 `null`。

挑 `target` 时跳过旗舰、战机联队与民用舰船：旗舰已有指挥官，战机联队不设舰长，民用舰船不值得配。剩下的成员里取 `getFleetPointCost()` 最高者，军官跟着最贵的那艘船走。要给更多舰船配，把它改成循环。

### 顺序

`spawnFleet()` 里的这几步有先后：

1. 以 `withOfficers = false` 创建舰队；
2. 加入旗舰并 `setFlagship(...)`；
3. 设置指挥官与旗舰舰长；
4. 给僚舰配军官；
5. 调用 `forceSync()`，再把舰队加入地点。

完整的 `spawnFleet()` 见 [C11-S1] 的完整类，这里只讲每一步的理由。

### 进游戏核对

执行 `gradle jar`，重启游戏，**新建一份存档**，用控制台命令 `jump Nipher` 到 Nipher Prime 轨道附近，选中巡逻舰队打开舰队信息。

- 旗舰是一艘工匠级，舰长与舰队指挥官同为 Brann Hale；
- 指挥官的性格为进取，职务显示为舰队指挥官；
- 其余舰船中，点数最高的一艘有一名军官，其余没有；
- 保存并重新读档，上述人员没有变化。

页面中的现象依据 API 与原版代码推出，编者尚未逐项在游戏内实测。

## 常见问题

- **舰队里刚配好的军官消失了**：在给僚舰配军官之后才调用 `setFlagship(...)`。
    - **解决方案**：`setFlagship(...)` 放在配军官之前。

- **指挥官的名字出现了，旗舰却由无名人物驾驶**：只调用了 `setCommander(...)`。
    - **解决方案**：同时对旗舰调用 `setCaptain(...)`。

- **旗舰的船体与预期不符**：依赖 `flagshipVariantId`，它只替换工厂选定旗舰的装配。
    - **解决方案**：自己创建成员并加入舰队，再设为旗舰。

- **旗舰没有船名**：没有调用 `pickShipName(...)`。
    - **解决方案**：创建成员后立即设置船名。

- **舰队汇总没有包含新成员**：改动之后没有同步。
    - **解决方案**：在加入地点之前调用 `forceSync()`。

## 建议参考项

- `com.fs.starfarer.api.campaign.FleetDataAPI`：成员、旗舰与军官名单的方法，`setFlagship(...)` 对成员舰长的影响不体现在签名上。
- `com.fs.starfarer.api.impl.campaign.events.OfficerManagerEvent`：`createOfficer(...)` 的各个重载与技能倾向。
- `com.fs.starfarer.api.impl.campaign.fleets.FleetFactoryV3`：`addCommanderAndOfficersV2(...)` 是原版配置指挥官与军官的完整做法。
- `com.fs.starfarer.api.impl.campaign.ids.Ranks`：军衔与职务常量。
