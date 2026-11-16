# 让人物承担角色

Foundry 有了联系人，却还没有替它管事的人。巡逻队那边情况相反，指挥官 Brann 一个人干着两份活，好在一支只有几艘船的巡逻队，倒也用不着再多养一个。

---

_本节预期阅读时间：\~12 分钟。_

_本节预期操作时间：\~15 分钟。_

## 前置知识

- [C13-S1] 的固定 ID 人物与重要人物服务；
- [C12-S1] 的 Foundry 市场，管理员在那里任职；
- [C11-S2] 的指挥官与舰长，两者的区别在本节沿用。

## 让 Selene 管理 Foundry

Selene Ward 的 ID 为 `GearIn_admin`，职务取 `Ranks.POST_ADMINISTRATOR`。创建方式与 [C13-S1] 的 Aster 相同：先查后建，登记到重要人物服务，再写入身份。

管理员另找了一个人，没有让 Aster 兼任。联系人要随时能在通信目录里被找到，管理员则牵着整座站的治理数值，两份差事压在一个人身上，以后调哪一边都会牵动另一边。

身份写好之后，指派给市场，并让治理效果与产业重新计算：

```java
market.setAdmin(admin);
admin.getStats().refreshGovernedOutpostEffects(market);
market.reapplyIndustries();
```

`setAdmin(...)` 会把旧管理员从市场与通信目录中移除，替换的是整个岗位，不必另行清理。`refreshGovernedOutpostEffects(...)` 让人物的治理技能作用于这座市场，产业随后重算，才会读到新管理员的数值。

**只调用 `setAdmin`，不刷新、不重算，管理员已经就任，产业却仍按旧数值运行。** 技能在 [C13-S3] 加入后，这一步才有可见的结果。

## 让 Brann 指挥巡逻队

巡逻队的指挥官由 [C11-S2] 的管理器在 `spawnFleet()` 中创建，人物是 Brann Hale。指挥官属于舰队，要写在舰队上，不能写在市场或人物服务里：

```java
fleet.setCommander(commander);
flagship.setCaptain(commander);
```

第一行写指挥官，第二行写旗舰舰长。**两个岗位分别设置，一个不会带动另一个。** Brann 同时担任两职，所以两行都要写。

成员读到的指挥官来自所属舰队，舰队换了人，成员 `getFleetCommander()` 返回的也随之变化。

## 三个岗位的归属

| 岗位 | 持有对象 | 写入 | 读取 |
| ---- | -------- | ---- | ---- |
| 管理员 | `MarketAPI` | `setAdmin(person)` | `getAdmin()` |
| 指挥官 | `CampaignFleetAPI` | `setCommander(person)` | `getCommander()` |
| 舰长 | `FleetMemberAPI` | `setCaptain(person)` | `getCaptain()` |

岗位写在谁身上，效果就只作用于谁。把管理员写到舰队上，市场始终没有管理员。

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

在 `GearInModPlugin.onNewGame()` 中，Aster 之后调用 `GearInAdministratorRole.assign(sector)`。

### 进游戏核对

执行 `gradle jar`，重启游戏，**新建一份存档**，打开 Nipher Foundry 的殖民地界面。

- 管理员显示为 Selene Ward；
- 控制台执行 `runcode Global.getSector().getEconomy().getMarket("GearIn_market_foundry").getAdmin().getId()`，返回 `GearIn_admin`；
- 保存并重新读档，管理员不变。

页面中的现象依据 API 与原版代码推出，编者尚未逐项在游戏内实测。

## 常见问题

- **管理员没有变化**：市场尚未建立，`assign(...)` 返回了 `null`。
    - **解决方案**：让 Foundry 生成器先于管理员执行。

- **读档后出现两个 Selene**：创建没有先查后建。
    - **解决方案**：保留 `getPerson(PERSON_ID)` 的存在性检查。

- **治理技能没有效果**：指派之后没有刷新治理效果，也没有重算产业。
    - **解决方案**：依次调用 `refreshGovernedOutpostEffects(...)` 与 `reapplyIndustries()`。

- **旧存档中没有 Selene**：管理员只在新档中指派。
    - **解决方案**：使用新档测试，见 [C7-S1]。

## 建议参考项

- `com.fs.starfarer.api.campaign.econ.MarketAPI`：`setAdmin(...)` 的注释说明了旧管理员的处理。
- `com.fs.starfarer.api.campaign.CampaignFleetAPI`：`setCommander(...)` 与 `forceSync()`。
- `com.fs.starfarer.api.fleet.FleetMemberAPI`：`setCaptain(...)` 与成员重算。
- `com.fs.starfarer.api.impl.campaign.ids.Ranks`：岗位职务常量。
