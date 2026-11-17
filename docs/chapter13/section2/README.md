# 让人物承担角色

`PersonAPI` 本身只是一份档案，真正替殖民地加稳定度、给整支舰队提航速，或者提升单船战斗性能，全看它被指派到了市场管理员、舰队指挥官还是单舰舰长这三个彼此独立的岗位槽位上。

等到真正派人上岗，Foundry 管理员 Selene Ward 去市场里带起治理数值，巡逻队指挥官 Brann Hale 则一人兼任指挥官与旗舰舰长，正好把这三类槽位都用上。

---

_本节预期阅读时间：\~12 分钟。_

_本节预期操作时间：\~15 分钟。_

## 前置知识

- [C13-S1] 的固定 ID 人物与重要人物服务；
- [C12-S1] 的 Foundry 市场，管理员将在那里任职；
- [C11-S2] 的 `GearInPatrolRoster`，巡逻队的指挥官与旗舰在那里配置。

## 指派市场管理员

管理员 Selene Ward 的 ID 定为 `GearIn_admin`，创建方式沿用 [C13-S1] 的先查后建流程，并额外设定了 `setPersonality(Personalities.STEADY)`。人建好之后，交给 `MarketAPI` 接管：

```java
market.setAdmin(admin);
admin.getStats().refreshGovernedOutpostEffects(market);
market.reapplyIndustries();
```

这三行各自解决的问题并不重叠：

- `market.setAdmin(admin)` 会完成岗位交接 —— 如果市场上原先有旧管理员，引擎会自动将旧管理员从市场人员与通信目录里移除，把新管理员加入 `market.addPerson(admin)`，并在管理员人选确实发生变化时自动调用一次 `admin.getStats().refreshGovernedOutpostEffects(market)`；
- **但 `setAdmin(...)` 不会自动触发 `market.reapplyIndustries()`**，也不会在人选不变、后续只增减技能时替您重刷状态 (`market.reapplyIndustries()` 内部也会同步调用 `refreshGovernedOutpostEffects(this)`)。因此在指派完成后补齐这两行调用，能确保即使人物技能刚刚改动过，所有产业收益与市场属性也能立刻拿到最新数值。

::: tip 管理员与通信目录的默认行为

`market.setAdmin(admin)` 只负责把人物加入市场人物名单 (`market.getPeopleCopy()` 可查到)，**不会**自动把他放进 `market.getCommDirectory()`。如果您的设计希望玩家能在空间站通信列表里直接找管理员对话，仍需像 [C13-S1] 那样手动补一行 `market.getCommDirectory().addPerson(admin)`。

:::

## 舰队指挥官与单舰舰长的分工

在 [C11-S2] 的 `GearInPatrolRoster.configure(...)` 里，巡逻队指挥官 Brann Hale 通过 `createCommander(random)` 生成，随后交由舰队接口指派：

```java
PersonAPI commander = createCommander(random);
fleet.setCommander(commander);
fleet.getFleetData().setFlagship(flagship);
```

舰队层面的人物岗位分成三条线，相互之间的联动关系很容易混淆：

1. **舰队指挥官 (`fleet.setCommander(person)`)**：挂在 `CampaignFleetAPI` 上，决定星图悬停框显示的指挥官是谁，并使该人物的舰队级技能 (`FLEET`、`ALL_SHIPS_IN_FLEET`) 对整支舰队生效；任意成员调用 `member.getFleetCommander()` 读取到的也是这份引用；
2. **单舰舰长 (`member.setCaptain(person)`)**：挂在具体的 `FleetMemberAPI` 上，仅将人物的单舰技能 (`SHIP`) 与 AI 作战性格 (`Personalities`) 施加给这一艘船；普通成员更换舰长绝不会反过来改写 `fleet.getCommander()`；
3. **旗舰同步机制**：正如 [C11-S2] 所验证的，`fleet.getFleetData().setFlagship(flagship)` 会在指定旗舰时自动执行 `flagship.setCaptain(getCommander())`。也就是说，只要先设指挥官再设旗舰，旗舰舰长就自动落位；反之，如果舰队早已定好旗舰、您只是中途换了一名指挥官，或是用 `fleet.setCommander(...)` 之后没有再动旗舰标记，就必须补上一行 `flagship.setCaptain(commander)`，旗舰驾驶席才会跟着换人。

## 三个岗位的归属

| 岗位 | 持有对象 | 写入 | 读取 | 主要读取的人物数据 |
| ---- | -------- | ---- | ---- | ------------------ |
| 管理员 | `MarketAPI` | `setAdmin(person)` | `getAdmin()` | `GOVERNED_OUTPOST` / `ALL_OUTPOSTS` / `CHARACTER_STATS` 技能 |
| 指挥官 | `CampaignFleetAPI` | `setCommander(person)` | `getCommander()` | `FLEET` / `ALL_SHIPS_IN_FLEET` 等全队技能 |
| 舰长 | `FleetMemberAPI` | `setCaptain(person)` | `getCaptain()` | `SHIP` 单舰战斗技能与 `Personalities` 战术性格 |

人物的 `Personalities` (`TIMID`、`CAUTIOUS`、`STEADY`、`AGGRESSIVE`、`RECKLESS`) 主要影响舰长或指挥舰船在实战中的交战距离与推进意愿；在管理员身上它只作为基础档案字段保留，并不左右市场产值。

## 完整的管理员类

`src/data/scripts/gearin/campaign/GearInAdministratorRole.java`：

```java
package data.scripts.gearin.campaign;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.SectorAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.characters.FullName;
import com.fs.starfarer.api.characters.PersonAPI;
import com.fs.starfarer.api.impl.campaign.ids.Personalities;
import com.fs.starfarer.api.impl.campaign.ids.Ranks;

import data.scripts.gearin.GearInModPlugin;

/** Foundry 的管理员 Selene Ward：按固定 ID 创建一次，指派为市场管理员后刷新治理效果与产业。 */
public final class GearInAdministratorRole {

    public static final String PERSON_ID = "GearIn_admin";

    private GearInAdministratorRole() {
    }

    /** Foundry 尚未建立时返回 null。 */
    public static PersonAPI assign(SectorAPI sector) {
        MarketAPI market = sector.getEconomy().getMarket(GearInFoundryGenerator.MARKET_ID);
        if (market == null) {
            return null;
        }
        PersonAPI admin = sector.getImportantPeople().getPerson(PERSON_ID);
        if (admin == null) {
            admin = Global.getFactory().createPerson();
            admin.setId(PERSON_ID);
            admin.setName(new FullName("Selene", "Ward", FullName.Gender.FEMALE));
            admin.setFaction(GearInModPlugin.FACTION_ID);
            admin.setRankId(Ranks.CITIZEN);
            admin.setPostId(Ranks.POST_ADMINISTRATOR);
            admin.setPersonality(Personalities.STEADY);
            sector.getImportantPeople().addPerson(admin);
        }
        market.setAdmin(admin);
        admin.getStats().refreshGovernedOutpostEffects(market);
        market.reapplyIndustries();
        return admin;
    }
}
```

在 `GearInModPlugin.onNewGame()` 中，Aster 创建之后调用 `GearInAdministratorRole.assign(sector)`。

### 进游戏核对

执行 `gradle jar`，重启游戏，**新建一份存档**，打开 Nipher Foundry 的殖民地界面：

- 管理员栏显示为 Selene Ward；
- 控制台执行 `runcode Global.getSector().getEconomy().getMarket("GearIn_market_foundry").getAdmin().getId()` 返回 `GearIn_admin`；
- 保存并重新读档，管理员绑定保持不变。

## 常见问题

- **管理员没有变化**：市场尚未建立，`assign(...)` 返回了 `null`。
    - **解决方案**：让 `GearInFoundryGenerator` 先于 `GearInAdministratorRole.assign(sector)` 执行。

- **读档后出现两个同名管理员**：创建时没有先查 `ImportantPeopleAPI`，或把创建放进了每次读档都会运行的回调里。
    - **解决方案**：保留 `getPerson(PERSON_ID)` 的存在性检查，并放在 `onNewGame()` 执行。

- **管理员人选对了，但治理技能加成或产业数值没跟上**：更换技能后未重新触发 `refreshGovernedOutpostEffects(market)`，或未调用 `market.reapplyIndustries()`。
    - **解决方案**：在变更技能或指派管理员后，依次调用 `refreshGovernedOutpostEffects(market)` 与 `market.reapplyIndustries()`。

- **换了舰队指挥官，旗舰驾驶席上还是旧面孔**：在已经设定过旗舰的前提下只调用了 `fleet.setCommander(...)`，未重新绑定旗舰舰长。
    - **解决方案**：确保 `fleet.setCommander(...)` 在 `setFlagship(...)` 之前调用，或手动补调 `flagship.setCaptain(commander)`。

- **旧存档中没有 Selene**：管理员只在新档中创建指派。
    - **解决方案**：使用新档测试，见 [C7-S1]。

## 建议参考项

- `com.fs.starfarer.api.campaign.econ.MarketAPI`：`setAdmin(...)` 会移除旧管理员出市场与通信目录并触发一次治理效果刷新，但不触发产业重算。
- `com.fs.starfarer.api.campaign.CampaignFleetAPI`：`setCommander(...)` 与 `forceSync()`。
- `com.fs.starfarer.api.fleet.FleetMemberAPI`：`setCaptain(...)` 与 `getFleetCommander()` 的区分。
- `com.fs.starfarer.api.impl.campaign.ids.Personalities`：原版 AI 作战性格常量 (`TIMID` 至 `RECKLESS`)。
- `com.fs.starfarer.api.impl.campaign.ids.Ranks`：岗位职务常量。
