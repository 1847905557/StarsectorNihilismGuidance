# 制作测试战役与普通战役

测试战役大概编者最常打开的的战役。或许比生涯还常打开。毕竟所有要测试的东西都触手可及。

它值得一次做好，之后就不用再管了。

当然，讲解了测试战役这个特例却反而不解释 "普通的" 战役是很奇怪的，所以我们不妨一次讲全。

---

_本节预期阅读时间：\~12 分钟。_

_本节预期操作时间：\~20 分钟。_

## 前置知识

- [C3-S2] 的舰队装配与 `default_ship_roles.json`，本节制作的装配要登记到其中；
- [C1-S2] 至 [C1-S4] 的工匠、看炉人与锻件，测试战役用它们做靶与测试对象；
- Java 的基本语法，战役定义 `MissionDefinition.java` 是一个完整的类；
- JSON 的基本写法与 [C1-S1] 提到的宽松 JSON，`descriptor.json` 与装配文件按它手工修改。

## 制作测试战役

在制作真正的战役之前，需要有舰船的装配。但是若需要制作舰船的装配，则最好的方法却又往往是从战役里导出。这种先有鸡还是先有蛋的问题可以被**测试战役**解决。

在 `data/missions` 中新建一个文件夹，将其命名为 `前缀名_test`，这是这个战役的文件夹，在本例中为 `GearIn_test`。然后在 `data/missions` 中创建 `mission_list.csv`，将 `GearIn_test` 填入第二行，为这个战役注册：

```csv
mission
GearIn_test
```

注册战役后，开始设置这个测试战役的具体内容。首先随机挑选一个原版战役，将它文件夹里的所有东西都复制到 `GearIn_test` 文件夹内。本例复制的是 `afistfulofcredits`：

![测试战役目录中的描述、图标、文本与源码](./mission_folder.png 'mission_folder.png')

然后逐个文件进行修改。首先是 `descriptor.json`：

```json
{
	"title":"Gear 测试",
	"difficulty":"简单",
	"icon":"icon.jpg",
	"background":"graphics/backgrounds/background4.jpg",
}
```

| 字段         | 实际意义                     |
| ------------ | ---------------------------- |
| `title`      | 战役名称                     |
| `difficulty` | 战役难度，可随意填写         |
| `icon`       | 战役图标贴图，在战役文件夹内 |
| `background` | 战役背景贴图路径             |

`icon.jpg` 是图标贴图，本例不做修改。

然后是 `mission_text.txt`，里面是战役的详细文本，可以随意填写：

```text
地点: 未知
日期: 未知

测试 齿轮工业 的舰船。
```

### 编写战役定义

最后是 `MissionDefinition.java`。本指南将直接分享一份测试战役框架，可以随意使用：

```java
package data.missions.GearIn_test;

// shared by Nihilism, modified from AnyIDElse under license

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.combat.ShipVariantAPI;
import com.fs.starfarer.api.fleet.FleetGoal;
import com.fs.starfarer.api.fleet.FleetMemberType;
import com.fs.starfarer.api.mission.FleetSide;
import com.fs.starfarer.api.mission.MissionDefinitionAPI;
import com.fs.starfarer.api.mission.MissionDefinitionPlugin;

/** 自动列出本 MOD 的全部非战机装配，用于测试与导出。 */
public class MissionDefinition implements MissionDefinitionPlugin {

    // 船体 ID 的前缀，换成您自己的前缀名
    public static final String PREFIX = "GearIn_";

    @Override
    public void defineMission(MissionDefinitionAPI api) {
        // 双方舰队：舰船名前缀、作战目标、是否由 AI 控制、初始指挥点
        api.initFleet(FleetSide.PLAYER, "SIM", FleetGoal.ATTACK, false, 5);
        api.initFleet(FleetSide.ENEMY, "SIM", FleetGoal.ATTACK, true, 5);

        api.setFleetTagline(FleetSide.PLAYER, "TEST");
        api.setFleetTagline(FleetSide.ENEMY, "TEST");

        api.addBriefingItem("TEST");

        // 找出船体 ID 以前缀开头的全部装配，跳过战机和测试战役自己导出的装配
        List<String> variants = new ArrayList<String>();
        for (String id : Global.getSettings().getAllVariantIds()) {
            ShipVariantAPI variant = Global.getSettings().getVariant(id);
            if (variant.getHullSpec().getHullId().startsWith(PREFIX)
                    && variant.getHullSpec().getHullSize() != ShipAPI.HullSize.FIGHTER
                    && !id.startsWith("mission_")) {
                variants.add(id);
            }
        }
        Collections.sort(variants);

        // 全部加入玩家舰队，第一艘为旗舰
        boolean first = true;
        for (String variant : variants) {
            api.addToFleet(FleetSide.PLAYER, variant, FleetMemberType.SHIP, first);
            first = false;
        }

        // 敌方：原版余辉中枢
        api.addToFleet(FleetSide.ENEMY, "remnant_station2_Standard", FleetMemberType.SHIP, false);

        // 地图：大小、星云、传感干扰器、通讯中继站、导航浮标与小行星带
        float width = 10000f;
        float height = 10000f;
        api.initMap(-width / 2f, width / 2f, -height / 2f, height / 2f);

        float minX = -width / 2;
        float minY = -height / 2;

        api.addNebula(minX + width * 0.66f, minY + height * 0.5f, 2000);
        api.addNebula(minX + width * 0.25f, minY + height * 0.6f, 1000);
        api.addNebula(minX + width * 0.25f, minY + height * 0.4f, 1000);

        for (int i = 0; i < 5; i++) {
            float x = (float) Math.random() * width - width / 2;
            float y = (float) Math.random() * height - height / 2;
            float radius = 100f + (float) Math.random() * 400f;
            api.addNebula(x, y, radius);
        }

        api.addObjective(minX + width * 0.25f + 2000f, minY + height * 0.5f, "sensor_array");
        api.addObjective(minX + width * 0.75f - 2000f, minY + height * 0.5f, "comm_relay");
        api.addObjective(minX + width * 0.33f + 2000f, minY + height * 0.4f, "nav_buoy");
        api.addObjective(minX + width * 0.66f - 2000f, minY + height * 0.6f, "nav_buoy");

        api.addAsteroidField(-(minY + height), minY + height, -45, 2000f, 20f, 70f, 100);
    }
}
```

**将代码中的两个替换位点替换完毕**后可直接使用：`package` 一行末尾的战役文件夹名，以及 `PREFIX` 的前缀名。它会自动将您 MOD 中的所有舰船列出，无需后期修正。

这份代码比前两章的脚本都长，但您不必逐行读懂它。**它是一件工具，照抄、替换两处即可。** 按船体前缀收集装配、往舰队里加船、摆星云与战场目标，这些写法到 [C6-S1] 建立 Java 工程之后再回头看，会容易得多。现在只需要知道下面这些：

- **战役类的完整名字由目录 ID 决定**。包名必须是 `data.missions.GearIn_test`，与文件夹名逐字一致；
- **这是按船体归属收集装配**。每个船体还有一个游戏自动生成的空装配，所以还没做过装配的船也会出现在列表里；
- 敌方固定是一座原版余辉中枢。

编者选余辉中枢当靶子，是因为它不会跑、火力全面、足够耐打，任何舰船都能在它面前打满一整轮战术系统与武器循环。**比较某项设计改动时，敌方配置与场地保持不变**，否则您分不清变化来自设计本身还是测试条件。代价是它测不出追击与机动，那部分要交给模拟战，见 [C4-S2]。

### 进游戏核对

保存所有文件，启动游戏，在主菜单进入战役，选择 "Gear 测试"：

![测试战役的选择页面与玩家舰队装配入口](./mission_test.png 'mission_test.png')

齿轮工业的全部舰船都在玩家舰队里，敌方是一座余辉中枢。点击舰船旁的装配按钮可以进入装配界面，开始战役则进入战斗。

## 制作装配

[C3-S2] 手写了工匠的装配，看炉人和锻件的装配则留到了这里。您可以在测试战役中为您的舰船安装武器、插件、战机等。被装配过的舰船，会在存档目录的 `missions/GearIn_test/` 下自动生成装配文件：

![测试战役保存到存档目录的装配文件](./variant_export.png 'variant_export.png')

文件名形如 `mission_GearIn_test_ship_0.variant`，末尾的序号对应玩家舰队中的成员。列表里的船不止一艘，打开文件看一眼 `hullId`，确认它是刚才装配的那艘。

将它剪切 (而非复制) 到您 MOD 中 `data/variants` 文件夹内，然后修改文件名为更容易辨识的样子。存档目录里只留下还没整理的设计，下次导出时就不会认错。**正式装配需要自己的 ID。** 文件名改为 `GearIn_furnaceman_combat.variant`，内部的 `variantId` 同步改为 `GearIn_furnaceman_combat`，`displayName` 改为希望显示的装配名：

```json
{
    "displayName": "Combat",
    "fluxCapacitors": 7,
    "fluxVents": 20,
    "hullId": "GearIn_furnaceman",
    "hullMods": ["expanded_deck_crew"],
    "permaMods": [],
    "sMods": [],
    "variantId": "GearIn_furnaceman_combat",
    "goalVariant": true,
    "weaponGroups": [
        {
            "autofire": true,
            "mode": "LINKED",
            "weapons": {
                "WS0001": "GearIn_durin",
                "WS0002": "GearIn_durin"
            }
        },
        {
            "autofire": true,
            "mode": "LINKED",
            "weapons": {
                "WS0003": "vulcan",
                "WS0004": "vulcan",
                "WS0005": "vulcan",
                "WS0006": "vulcan"
            }
        }
    ],
    "wings": ["GearIn_slag_wing", "GearIn_slag_wing"]
}
```

如果您还记得，那么您在此前制作战机装配，以及 [C3-S2] 手写工匠装配的时候，已经见过类似的文件了。事实上，**舰船装配文件和战机装配文件没有本质区别**。航母多出一个 `wings`，按甲板顺序列出每个甲板搭载的联队。

看炉人是航母，编者给它两座甲板都配了炉渣，加上扩编飞行甲板人员，让它专心放飞机；六门小型炮里两门都林、四门火神炮，负责清理贴近的战机与导弹。

锻件按同样的方式导出，整理为 `GearIn_forging_combat.variant`：

```json
{
    "displayName": "Combat",
    "fluxCapacitors": 7,
    "fluxVents": 10,
    "hullId": "GearIn_forging",
    "hullMods": ["heavyarmor"],
    "permaMods": [],
    "sMods": [],
    "variantId": "GearIn_forging_combat",
    "goalVariant": true,
    "weaponGroups": [
        {
            "autofire": false,
            "mode": "LINKED",
            "weapons": {
                "WS0001": "chaingun"
            }
        }
    ]
}
```

::: tip 自动装配

如果您希望装配会出现在装配界面左下角的 `自动装配` 里，您需要在 `variantId` 附近为它加上 `"goalVariant": true`，与 [C3-S2] 的工匠装配相同。

:::

火神炮与突击链炮都带有原版的低科标签，[C3-S2] 已经让齿轮工业掌握了它们，生涯舰队按这两个装配重新装配时不会换掉它们。

### 登记到舰船角色

打开 [C3-S2] 创建的 `default_ship_roles.json`，把两个装配登记到各自的角色：

```json
{
    "combatMedium": {
        "GearIn_artizan_combat": 5
    },
    "carrierMedium": {
        "GearIn_furnaceman_combat": 5
    },
    "phaseSmall": {
        "GearIn_forging_combat": 5
    }
}
```

编者建议您接下来为您的所有舰船制作 2\~3 个装配，以待稍后使用。装配会决定舰船在势力舰队里的实际表现，同一个角色下登记几个定位不同的装配，例如突击、支援与长程，玩家遇见的齿轮工业舰队就不会千篇一律。

### 进游戏核对

保存所有文件，重启游戏：

- 进入测试战役，玩家舰队中出现 `GearIn_furnaceman_combat` 与 `GearIn_forging_combat`，装备与文件一致；
- 在装配界面打开看炉人，左下角的自动装配列表中出现这个装配；
- 读取一个生涯存档，用控制台 `SpawnFleet gear_industry 60` 生成舰队，看炉人与锻件带着这两套装配出现。

## 制作战役

在您已经拥有装配的情况下，制作普通战役就很直接了。

先定下战役名字和一个不重复的战役 ID，再像前面那样挑一个原版战役文件夹复制到 `data/missions/` 下，改掉文件夹名、`MissionDefinition.java` 里的 `package` 声明、显示文本和装配 ID，最后在 `mission_list.csv` 里登记即可。

原版战役的 `MissionDefinition.java` 比测试战役短得多，大部分是 `addToFleet` 加船与几行地图设置，照着改装配 ID 即可，同样不必深究其余部分。

测试战役可以继续保留在自己的工作 MOD 中。发布时，在 `mission_list.csv` 中的 `GearIn_test` 一行前加上 `#` 即可把它从战役列表中关闭。编者倾向于发布时也留着它：玩家拿它看您的全部舰船，比在数据库里翻快得多。

## 常见问题

- **战役列表里没有 Gear 测试**：`mission_list.csv` 的第一行不是 `mission`，或战役 ID 与文件夹名不一致。
    - **解决方案**：确认第一行是 `mission`，第二行与 `data/missions/` 下的文件夹名逐字一致，保存后重启。

- **游戏加载中断，进不了主菜单**：已登记的战役文件夹里缺少 `descriptor.json`、`mission_text.txt` 或图标，或 `descriptor.json` 缺少 `title`、`difficulty`、`icon`。
    - **解决方案**：补齐文件与字段，确认图标真实存在，保存后重启。

- **点击战役时报 `Problem loading class [data.missions.GearIn_test.MissionDefinition]`**：`package` 一行与文件夹名不一致，或文件不在战役文件夹内。
    - **解决方案**：把包名改为 `data.missions.` 加上文件夹名，保存后重启。

- **战役里没有自己的船**：`PREFIX` 与船体 ID 的前缀不一致，大小写也算。
    - **解决方案**：把 `PREFIX` 改为船体 ID 的前缀，重新进入战役。

- **放回 MOD 的装配没有出现，战役里仍是导出前的设计**：文件改了名，内部的 `variantId` 仍是 `mission_` 开头。模板会跳过 `mission_` 开头的装配。
    - **解决方案**：把 `variantId` 改为与文件名一致的唯一 ID，保存后重启。

- **加载时报 `Key [mode] has invalid value [...]`**：武器组的 `mode` 写错。它只能是 `LINKED` 或 `ALTERNATING`。
    - **解决方案**：改为两者之一，保存后重启。

## 建议参考项

- `data/missions/mission_list.csv`：原版战役注册表，本节的登记方式照它写。
- `data/missions/ambush/`：本节随机选来复制的原版战役文件夹之一，包含 `descriptor.json`、`MissionDefinition.java` 与装配样本。
- `data/variants/`：原版装配文件，对照装配字段的写法。
