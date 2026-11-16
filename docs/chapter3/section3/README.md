# 制作一个生涯星系

势力总不能凭空存在，而势力拥有的殖民地自然是必须属于一个星球和空间站，而星球和空间站...好吧，存在于某个星系里。

所以我们需要一个充满了含有势力的殖民地的星球或空间站的星系。可能搞不好其实还不止一个。

---

_本节预期阅读时间：\~20 分钟。_

_本节预期操作时间：\~20 分钟。_

## 前置知识

- [C3-S1] 的势力 ID `gear_industry`，星系、市场与关系都挂在它上面；
- [C2-S1] 的复制原版类并修改的做法，以及类放在 `data/scripts/` 下的目录写法；
- Java 的基本语法：类、方法、导入与调用，本节起读者要自己写完整的类。

## 制作 MOD 入口类

很不幸的是，您接下来将不可避免地接触更多的代码。**如果您希望将您的 MOD 做好，那您以后将会接触更多、更难的代码，如果您连这部分都难以处理，那将很难进行日后的开发。**

船插的代码只在战斗中运行，这一次的代码要在生涯开局时运行。

游戏启动、读档、开新档时，会调用每个 MOD 的入口类。在 MOD 根目录下创建 `data/scripts/gearin/GearInModPlugin.java`：

```java
package data.scripts.gearin;

import com.fs.starfarer.api.BaseModPlugin;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.RepLevel;
import com.fs.starfarer.api.campaign.SectorAPI;

import data.scripts.gearin.campaign.GearInSystemGenerator;

/** 在新档生成时建立齿轮工业星系与初始关系。 */
public class GearInModPlugin extends BaseModPlugin {

    @Override
    public void onNewGame() {
        new GearInSystemGenerator().generate(Global.getSector());
        seedRelations(Global.getSector());
    }

    private void seedRelations(SectorAPI sector) {
        FactionAPI gear = sector.getFaction("gear_industry");
        gear.setRelationship("player", 0f);
        gear.setRelationship("pirates", RepLevel.HOSTILE);
        gear.setRelationship("hegemony", RepLevel.FRIENDLY);
        gear.setRelationship("tritachyon", RepLevel.HOSTILE);
    }
}
```

`onNewGame()` 在创建新生涯时调用一次。这里先调用星系生成器，再设置齿轮工业与几个原版势力的初始关系。`RepLevel` 是关系等级，从 `VENGEFUL` 到 `COOPERATIVE`；`"player"` 一行直接写数值，`0f` 即中立。原版势力 ID 可以在 `data/world/factions/` 中查到，原版自己的关系设置见 `data/scripts/world/SectorGen.java`。

**`onNewGame()` 只在开新档时运行。** 已有的存档读进来，不会补上星系，也不会改关系。以后凡是写在新档回调里的内容，测试时都要开新档；在旧档里找不到，先确认这个档是不是在改代码之前开的。

然后在 `mod_info.json` 中声明入口类，与已有字段之间用逗号分隔：

```json
"modPlugin": "data.scripts.gearin.GearInModPlugin"
```

**`modPlugin` 的值是入口类的完整类名，由包名和类名组成。** 它与 `package` 语句、文件所在目录逐段对应，错一个字母，游戏就会在启动时报错退出。

## 编写星系生成器

在 `data/scripts/gearin/campaign/` 创建 `GearInSystemGenerator.java`。这个类就是上面入口类调用的生成器。**生成器由入口主动调用**，入口类里的那一行删掉，星系就不会生成。字段旁的注释说明了每个参数的含义：

```java
package data.scripts.gearin.campaign;

import java.awt.Color;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.JumpPointAPI;
import com.fs.starfarer.api.campaign.PlanetAPI;
import com.fs.starfarer.api.campaign.SectorAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.SectorGeneratorPlugin;
import com.fs.starfarer.api.campaign.StarSystemAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;

/** 创建包含两颗行星、通讯中继、基础市场与跳跃点的齿轮工业星系。 */
public class GearInSystemGenerator implements SectorGeneratorPlugin {

    @Override
    public void generate(SectorAPI sector) {
        // 星系 ID、背景图与星系内的光照颜色
        StarSystemAPI system = sector.createStarSystem("Nipher");
        system.setBackgroundTextureFilename("graphics/backgrounds/background2.jpg");
        system.setLightColor(new Color(200, 200, 210));

        // 恒星：ID、类型、半径、日冕大小、恒星风强度、耀斑概率、战备值损失倍率
        PlanetAPI star = system.initStar("nipher", "star_yellow", 600f, 550f, 3f, 0.75f, 1f);
        // 星系在超空间中的坐标
        system.getLocation().set(-16000f, -9000f);

        // 行星：ID、轨道焦点、名称、类型、轨道角、半径、轨道半径、轨道周期 (天)
        PlanetAPI prime = system.addPlanet(
                "GearIn_planet1", star, "Nipher Prime", "terran", 55, 150, 3200, 120);
        PlanetAPI second = system.addPlanet(
                "GearIn_planet2", star, "Nipher Second", "barren", 200, 90, 5200, 200);

        // 原版通讯中继：ID、名称、实体类型、所属势力
        SectorEntityToken stable = system.addCustomEntity(
                "GearIn_stable", "Nipher 通讯中继", "comm_relay", "gear_industry");
        stable.setCircularOrbitPointingDown(star, 90, 2200, 100);

        // 市场：ID 与名称沿用行星，规模 4
        MarketAPI market = Global.getFactory().createMarket(prime.getId(), prime.getName(), 4);
        market.setFactionId("gear_industry");
        market.setPrimaryEntity(prime);
        market.setSurveyLevel(MarketAPI.SurveyLevel.FULL);
        market.addCondition("population_4");
        market.addIndustry("population");
        market.addIndustry("spaceport");
        market.addSubmarket("open_market");
        market.addSubmarket("storage");
        sector.getEconomy().addMarket(market, true);
        prime.setMarket(market);
        prime.setFaction("gear_industry");

        // 星系内的跳跃点，关联主行星
        JumpPointAPI jump = Global.getFactory().createJumpPoint("GearIn_jump", "Nipher 跳跃点");
        jump.setCircularOrbit(star, 300, 1800, 100);
        jump.setRelatedPlanet(prime);
        system.addEntity(jump);
        // 生成超空间一侧的入口：气态巨行星入口、星系边缘跳跃点
        system.autogenerateHyperspaceJumpPoints(true, true);
    }
}
```

星系的名称与 ID 都是 `Nipher`，恒星是一颗黄色恒星。**星系内部的轨道与星系在超空间中的位置是两套坐标。** `addPlanet` 的轨道半径以恒星为中心，`getLocation().set(...)` 则决定 Nipher 在星图上的位置；本例的 `(-16000, -9000)` 位于星区核心以西，原版 Corvus 星系在 `(400, -9400)` 附近，可以以它为参照选择航程。编者把 Nipher 放得离核心区很远，让齿轮工业像一个偏居一隅的工业势力；代价是玩家要专程跑一趟，测试时也一样。

**轨道焦点与轨道参数共同确定行星的位置关系**，两颗行星都绕恒星运行。Nipher Prime 是宜居的类地行星，承载齿轮工业的市场；Nipher Second 是外侧的贫瘠行星，暂时只是一颗行星。

通讯中继直接使用原版的 `comm_relay` 实体类型，归属齿轮工业。它让玩家在星系里能收到情报和联系人消息，原版有人居住的星系几乎都有一座。

### 建立市场

市场与行星是两个对象。**创建市场、加入经济、与行星互相绑定，三步缺一不可。** 只创建不加入经济，市场不参与贸易；只加入经济不绑定行星，玩家靠近行星也打不开它。

`population_4` 是规模条件，与 `createMarket` 的规模保持一致；`population` 与 `spaceport` 是人口与太空港产业，有人居住的市场至少需要这两项。`open_market` 是公开市场，`storage` 是仓储。

这个市场只用于确认星系与势力的归属。行星条件、更多产业与子市场留到 [C12-S1] 讲解。

::: tip 使用 MagicLib 创建市场

MagicLib 的 `MagicCampaign.addSimpleMarket(...)` 可以把上面这一串调用压成一行；MagicLib 的接入与用法见 [C18-S11]。MagicLib 是三前置的一个，可以确定几乎所有玩家都会使用它。使用广为流传的前置 Mod 而非什么不知从何而来的陌生前置 Mod 更利于您 Mod 的流通。

:::

### 生成跳跃点

`createJumpPoint` 在星系内创建一个跳跃点，`setRelatedPlanet` 让它在星图上显示为通往 Nipher Prime 的入口。`autogenerateHyperspaceJumpPoints(true, true)` 在超空间一侧生成对应的入口，第一个 `true` 允许在气态巨行星处生成单向入口，第二个 `true` 额外在星系边缘生成一个双向跳跃点。

**在调整星系时，入口位置也属于设计的一部分**：玩家抵达后还要从跳跃点驶向行星，本例的跳跃点在 1800 的轨道上，离 Nipher Prime 不远。

**没有调用 `autogenerateHyperspaceJumpPoints`，星系在超空间中就没有入口。** 星图上能看到它，但玩家飞到那里什么也进不去。

### 进游戏核对

保存两个类与 `mod_info.json`，启动游戏，创建一个新生涯。打开星图，在核心区以西找到 Nipher：

- 星系内有恒星、Nipher Prime、Nipher Second、通讯中继与跳跃点；
- Nipher Prime 归属齿轮工业，靠近后可以进入市场交易，也可以使用仓储；
- 在情报的势力页查看齿轮工业，与海盗、速子科技敌对，与霸主友好。

## 继续扩充星系

Nipher 现在只有两颗行星、一座中继和一个市场，看上去空空如也，但稍后即可为它添加星球与各种各样的东西。

在**原版的 `data/scripts/world/corvus` 文件夹内，是 Corvus 星系的生成文件。而在 `data/scripts/world/systems` 文件夹内则是几乎所有其它星系的生成文件。**

编者认为**一切创作的本质，不过是对现有元素的再次排列组合**，而在代码上则只需要不断复制、粘贴与修改即可。您可以在上述各类星系里找到星球、陨石带、碎片带、极光、空间站、跳跃点、星门等物体的添加方法，只需要将它们任意进行排列组合，复制到 `generate` 中，然后再根据所需进行微调即可。

如果您需要各类对于参数的注解，那么**您可以查阅 `starsector-core/starfarer.api.zip` 中 `LocationAPI` 与 `StarSystemAPI` 的源码注释**，在这里全部列出会导致篇幅过长。

## 常见问题

- **启动时报 `Error loading [data.scripts.gearin.GearInModPlugin]`**：`modPlugin` 的值与入口类的包名、类名或所在目录不一致。
    - **解决方案**：按完整类名逐段核对 `package` 语句与文件路径，保存后重启。

- **启动时报编译错误，指向 `GearInSystemGenerator`**：生成器的包名与目录不一致，或入口类缺少它的 `import`。
    - **解决方案**：确认生成器位于 `data/scripts/gearin/campaign/`，入口类写有 `import data.scripts.gearin.campaign.GearInSystemGenerator;`，保存后重启。

- **星图上找不到 Nipher**：使用的是改代码之前的存档。
    - **解决方案**：创建新生涯。

- **Nipher 出现在星图上，但进不去**：没有调用 `autogenerateHyperspaceJumpPoints`。
    - **解决方案**：在 `generate` 末尾补上这一行，保存后重启并创建新生涯。

- **靠近 Nipher Prime 无法进入市场**：市场没有加入经济，或没有与行星互相绑定。
    - **解决方案**：核对 `addMarket`、`setPrimaryEntity` 与 `setMarket` 三处调用，保存后重启并创建新生涯。

## 建议参考项

- `data/scripts/world/SectorGen.java`：原版星区生成入口。
- `data/scripts/world/systems/`：原版各星系的生成脚本。
- `com.fs.starfarer.api.BaseModPlugin`：入口类的基类，`onNewGame` 等回调在这里。
- `com.fs.starfarer.api.campaign.SectorAPI`：创建星系、读取势力的入口。
- `com.fs.starfarer.api.campaign.StarSystemAPI`：星系对象，添加恒星、行星与跳跃点的方法。
