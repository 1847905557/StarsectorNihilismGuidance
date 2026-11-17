# 管理舰队中的成员

做随机路人舰队时，把旗舰和指挥官一并交给工厂去掷骰子就已经够省事了。真要让玩家每次飞进 Nipher 都能认出领头的那艘工匠级和指挥官 Brann Hale，就得由我们自己去操作 `FleetDataAPI` 的成员名册了 —— 顺带一提，原版关于怎么设旗舰的那行 Javadoc 还是错的。

---

_本节预期阅读时间：\~15 分钟。_

_本节预期操作时间：\~20 分钟。_

## 前置知识

- [C3-S2] 的工匠战斗装配 `GearIn_artizan_combat`；
- [C11-S1] 的 `GearInPatrolFleetManager` 与 `FleetDataAPI` 入口。

## 成员对象的结构与创建

`FleetMemberAPI` 代表舰队编制中的一个席位。它本身不直接存放损伤或装配的底层字段，而是充当一组子对象的聚合入口：

| 要访问的数据 | `FleetMemberAPI` 方法 | 说明 |
| --- | --- | --- |
| 船体规格与当前装配 | `getHullSpec()`、`getVariant()` | 分别见 [C2-S1] 与 [C11-S4] |
| 船体完整度与装甲格损伤 | `getStatus()` | 返回 `FleetMemberStatusAPI` ([C11-S3]) |
| 战备值 (CR)、封存与维修进度 | `getRepairTracker()` | 返回 `RepairTrackerAPI` ([C11-S3]) |
| 当前舰长 | `getCaptain()`、`setCaptain(person)` | 未指派军官时返回一个 `isDefault() == true` 的匿名默认人物 |
| 单舰可变统计项 | `getStats()` | 返回该成员的 `MutableShipStatsAPI` ([C8-S2]) |
| 所属舰队数据 | `getFleetData()` | 返回持有该成员的 `FleetDataAPI` |

往一支已存在的舰队里塞入新舰船，需要先用装配 ID 创建 `FleetMemberAPI`，用势力的命名规则给它起好船名，再加入 `FleetDataAPI`：

```java
FleetMemberAPI member = Global.getFactory().createFleetMember(FleetMemberType.SHIP, variantId);
member.setShipName(fleet.getFleetData().pickShipName(member, random));
fleet.getFleetData().addFleetMember(member);
```

- `createFleetMember(...)` 只在内存中造出成员对象，必须调用 `addFleetMember(member)` 才会进入舰队编制；
- **手动创建的成员默认没有船名**，不调 `setShipName(...)` 在舰队面板上就会显示为空白；`fleet.getFleetData().pickShipName(member, random)` 会按该势力的 `.faction` 词库自动抽取带前缀的船名；
- `FleetDataAPI` 上还有一个快捷重载 `FleetMemberAPI member = fleet.getFleetData().addFleetMember(variantId);`，一步完成创建与入队并返回新成员，但同样需要手动补上 `setShipName(...)`。

## 指挥官、舰长与军官名单的归属

在 `CampaignFleetAPI` 与 `FleetDataAPI` 中，人物 (`PersonAPI`) 挂在三个彼此独立的位置上：

1. **舰队指挥官 (`fleet.getCommander()` / `fleet.setCommander(person)`)**：显示在星图舰队悬停框顶部、决定整支舰队享受哪些指挥官技能 ([C13-S3]) 的总指挥；
2. **单船舰长 (`member.getCaptain()` / `member.setCaptain(person)`)**：实际坐在某艘具体舰船驾驶席上、为该船提供个人战斗技能加成的舰长；
3. **舰队军官名单 (`fleet.getFleetData().addOfficer(person)` / `getOfficersCopy()`)**：登记在舰队人事编制里的军官列表。

**给其余舰船指派军官时，必须同时调用 `fleet.getFleetData().addOfficer(officer)` 和 `member.setCaptain(officer)`。** 只调后者不加进军官名单，这名军官在战斗结束或舰队同步时可能对不上编制；只加名单不设舰长，这名军官就只是在名册里挂名，没有实际指派到任何舰船上。

创建军官或指挥官人物使用原版的 `OfficerManagerEvent.createOfficer(...)`：

```java
PersonAPI officer = OfficerManagerEvent.createOfficer(
        Global.getSector().getFaction(GearInModPlugin.FACTION_ID),
        level,
        SkillPickPreference.ANY,
        random);
```

该方法会按势力倾向与指定等级自动抽取技能、头像与姓名；拿到返回的 `PersonAPI` 后，可以继续用 `setName(...)`、`setPersonality(...)`、`setRankId(...)` 与 `setPostId(...)` 覆盖其姓名、性格与职务。

::: tip 何时用 FleetParamsV3 自带参数，何时在生成后手动调整

如果您只是想调整 `FleetFactoryV3.createFleet(params)` 自动生成的指挥官或军官等级上限，或者想把一个提前造好的 `PersonAPI` 交给工厂去当指挥官，直接设置 `FleetParamsV3` 上的 `params.commander`、`params.commanderLevelLimit` 或 `params.officerLevelLimit` 即可，无需关闭 `params.withOfficers`。

只有像本节这样，需要在工厂抽完常规舰船之后**额外塞入一艘指定型号的旗舰、或对特定舰船增派军官**时，才在 `createFleet(...)` 返回后直接操作 `FleetDataAPI`。

:::

## 更换旗舰与 setFlagship(...) 的注释误导

向舰队加入新旗舰后，需要通知舰队切换旗舰并绑定指挥官：

```java
PersonAPI commander = createCommander(random);
fleet.setCommander(commander);
fleet.getFleetData().setFlagship(flagship);
```

::: warning FleetDataAPI.setFlagship(...) 的接口注释与实际实现完全相反

`FleetDataAPI.setFlagship(FleetMemberAPI)` 的官方 Javadoc 注释声称：调用该方法 "还会将所有其他舰船的舰长重置为属性全为 0 的新人物" (`Will also set the captains of all the other ships to a new person with all-0 stats`)。**这条注释是错的。**

在引擎的 `FleetData.setFlagship(...)` 实际实现中：

1. 遍历现有成员清除旧旗舰标记时，它**只会在某艘船的舰长恰好是玩家本人 (`playerPerson`) 时**才将该船舰长重置为默认匿名人物，**对 NPC 舰队里其他舰船上已经配好的军官分毫不动**；
2. 在方法末尾，它会自动执行 `flagship.setFlagship(true)` 与 **`flagship.setCaptain(getCommander())`** —— 即自动将当前舰队的 `getCommander()` 设为新旗舰的舰长。

因此正确的调用顺序与原版 `FleetFactoryV3` 完全一致：**先调用 `fleet.setCommander(commander)` 设好舰队指挥官，再调用 `fleet.getFleetData().setFlagship(flagship)` 指定旗舰**，两步下来指挥官与旗舰舰长就自动绑定妥当了。如果反过来先调 `setFlagship` 再调 `setCommander`，`setFlagship` 执行时读到的还是旧指挥官，您就不得不自己再去多写一行 `flagship.setCaptain(commander)`。

:::

对舰队成员、旗舰与军官名单做完增删后，最后调用一次 `fleet.forceSync()`，让舰队的成员排序、总点数与指挥官技能加成立即同步生效。

## 巡逻队旗舰与军官编组：GearInPatrolRoster

我们把巡逻队的旗舰增补与人物指派封装为独立的工具类 `GearInPatrolRoster`：

- 为每支巡逻队额外加入一艘 `GearIn_artizan_combat` 工匠级驱逐舰作为旗舰；
- 创建一名 4 级指挥官 `Brann Hale` (性格设为 `Personalities.AGGRESSIVE`，军衔与职务设为 `Ranks.SPACE_COMMANDER` 与 `Ranks.POST_FLEET_COMMANDER`，官方中文分别显示为 `"指挥官"` 与 `"舰队指挥官"`)，先设为舰队指挥官，再将工匠设为旗舰；
- 遍历其余非民用战斗舰船，若点数最高的那艘舰船还没有配军官 (`member.getCaptain().isDefault()`)，则为它增派一名 2 级军官；
- 最后调用 `fleet.forceSync()` 同步舰队状态并返回旗舰成员 (供后续 [C11-S4] 继续改装)。

### 完整编组类

`src/data/scripts/gearin/campaign/GearInPatrolRoster.java`：

```java
package data.scripts.gearin.campaign;

import java.util.Random;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.characters.FullName;
import com.fs.starfarer.api.characters.PersonAPI;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.fleet.FleetMemberType;
import com.fs.starfarer.api.impl.campaign.events.OfficerManagerEvent;
import com.fs.starfarer.api.impl.campaign.events.OfficerManagerEvent.SkillPickPreference;
import com.fs.starfarer.api.impl.campaign.ids.Personalities;
import com.fs.starfarer.api.impl.campaign.ids.Ranks;

import data.scripts.gearin.GearInModPlugin;

/** 为巡逻舰队增补工匠旗舰、指派指挥官，并为尚无军官的战斗舰船增派一名军官。 */
public final class GearInPatrolRoster {

    /** 巡逻队旗舰的装配 ID，沿用 C3-S2 配置的工匠。 */
    public static final String FLAGSHIP_VARIANT = "GearIn_artizan_combat";

    /** 指挥官等级。 */
    private static final int COMMANDER_LEVEL = 4;

    /** 增派军官等级。 */
    private static final int OFFICER_LEVEL = 2;

    private GearInPatrolRoster() {
    }

    public static FleetMemberAPI configure(CampaignFleetAPI fleet, Random random) {
        FleetMemberAPI flagship = Global.getFactory().createFleetMember(FleetMemberType.SHIP, FLAGSHIP_VARIANT);
        flagship.setShipName(fleet.getFleetData().pickShipName(flagship, random));
        fleet.getFleetData().addFleetMember(flagship);

        PersonAPI commander = createCommander(random);
        fleet.setCommander(commander);
        fleet.getFleetData().setFlagship(flagship);

        addWingOfficerIfNeeded(fleet, flagship, random);
        fleet.forceSync();
        return flagship;
    }

    /** 创建巡逻队指挥官：等级与技能由原版抽选，姓名、性格与职务自行指定。 */
    public static PersonAPI createCommander(Random random) {
        PersonAPI person = OfficerManagerEvent.createOfficer(
                Global.getSector().getFaction(GearInModPlugin.FACTION_ID),
                COMMANDER_LEVEL, SkillPickPreference.ANY, random);
        person.setName(new FullName("Brann", "Hale", FullName.Gender.ANY));
        person.setPersonality(Personalities.AGGRESSIVE);
        person.setRankId(Ranks.SPACE_COMMANDER);
        person.setPostId(Ranks.POST_FLEET_COMMANDER);
        return person;
    }

    /** 给点数最高且尚未配备舰长的非旗舰战斗成员增派一名军官。 */
    private static void addWingOfficerIfNeeded(CampaignFleetAPI fleet, FleetMemberAPI flagship, Random random) {
        FleetMemberAPI target = null;
        for (FleetMemberAPI member : fleet.getFleetData().getMembersListCopy()) {
            if (member == flagship || member.isFighterWing() || member.isCivilian()) {
                continue;
            }
            if (member.getCaptain() != null && !member.getCaptain().isDefault()) {
                continue;
            }
            if (target == null || member.getFleetPointCost() > target.getFleetPointCost()) {
                target = member;
            }
        }
        if (target == null) {
            return;
        }
        PersonAPI officer = OfficerManagerEvent.createOfficer(
                Global.getSector().getFaction(GearInModPlugin.FACTION_ID),
                OFFICER_LEVEL, SkillPickPreference.ANY, random);
        officer.setPostId(Ranks.POST_OFFICER);
        fleet.getFleetData().addOfficer(officer);
        target.setCaptain(officer);
    }
}
```

### 接入巡逻队管理器

在 [C11-S1] 的 `GearInPatrolFleetManager.spawnFleet()` 中，`createFleet(params)` 判空之后加入一行调用即可：

```java
CampaignFleetAPI fleet = FleetFactoryV3.createFleet(params);
if (fleet == null) {
    return null;
}
GearInPatrolRoster.configure(fleet, random);
```

### 进游戏核对

执行 `gradle jar`，重启游戏，**新建一份存档**，执行 `jump Nipher` 查看轨道上的巡逻队：

- 巡逻队由一艘带有 `GIS` 前缀船名的工匠级驱逐舰领航，旗舰舰长与舰队指挥官同为 4 级的 `Brann Hale`；
- 舰队中点数最高的那艘非旗舰战斗舰船配有军官；
- 保存并重新读档，旗舰、船名与指挥官信息保持不变。

## 常见问题

- **舰队信息面板顶部显示了新指挥官，但旗舰驾驶席上仍是旧舰长或默认头像**：先调用了 `fleet.getFleetData().setFlagship(flagship)`，后调用了 `fleet.setCommander(commander)`，且没有重新设置旗舰舰长。
    - **解决方案**：先调用 `fleet.setCommander(commander)`，再调用 `fleet.getFleetData().setFlagship(flagship)`，让 `setFlagship` 自动把新指挥官绑定到旗舰驾驶席上。

- **手动加入的旗舰在界面上船名一栏为空白**：调用 `createFleetMember(...)` 后漏掉了 `setShipName(...)`。
    - **解决方案**：使用 `flagship.setShipName(fleet.getFleetData().pickShipName(flagship, random))` 按势力词库生成船名。

- **加入新成员或更换指挥官后，舰队的移动速度与属性没有刷新**：修改成员名册后未触发同步。
    - **解决方案**：在完成成员与军官调整后调用 `fleet.forceSync()`。

## 建议参考项

- `com.fs.starfarer.api.campaign.FleetDataAPI`：舰队成员增删、`setFlagship(...)`、军官名单与 `forceSync()`。
- `com.fs.starfarer.api.fleet.FleetMemberAPI`：单舰成员状态、舰长与装配入口。
- `com.fs.starfarer.api.impl.campaign.events.OfficerManagerEvent`：`createOfficer(...)` 军官生成工具方法。
- `com.fs.starfarer.api.impl.campaign.fleets.FleetFactoryV3`：原版生成舰队、选定旗舰与指派指挥官的实现。
- `com.fs.starfarer.api.impl.campaign.ids.Ranks`：原版军衔与岗位 ID 常量。
