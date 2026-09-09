# 制作测试战役装配

本节制作一个自动列出本 MOD 舰船的测试战役，并从装配界面导出正式装配；随后以原版战役为基础建立常规战役，让这些装配进入战役数据。

*本节预期阅读时间：\~12 分钟。*

*本节预期操作时间：\~15 分钟。*

## 学习目标

完成后，您将能够：

- 用 `data/missions/mission_list.csv` 注册测试战役；
- 编写能够自动列出本 MOD 舰船的 `MissionDefinition`；
- 从战役装配界面导出 `.variant` 文件，并整理为 MOD 的正式装配；
- 对照字段表修改装配 JSON，并设置自动装配目标 (`goalVariant`)；
- 从原版战役复制出一个使用正式装配的常规战役。

## 前置知识

- [C1-S1] 的 MOD 目录、`mod_info.json` 与数据合并规则；
- [C1-S2] 的船体登记与装配界面操作；
- [C1-S3] 的战机装配文件及其登记关系；
- [C1-S5] 的武器登记规则；
- [C2-S2] 的船插登记规则。

## 先有装配还是先有战役

制作常规战役需要能够加入舰队的舰船装配，而装配可以直接在战役的装配界面中制作。测试战役会自动列出本 MOD 的舰船，让您先完成武器和船插配置，再把游戏导出的设计整理成正式装配。常规战役和生涯随后都能按装配 ID 使用这些设计。

## 注册测试战役

在 MOD 中新建 `data/missions/mission_list.csv`，写入表头和战役 ID。游戏会按行合并原版与各 MOD 的这个文件，因此只需提交自己的行，严禁复制整份原版文件。

第一行 `mission` 是必须逐字一致的列名，第二行 `GearIn_test` 是战役 ID。这个 ID 同时确定目录 `data/missions/GearIn_test/`；大小写或下划线不一致都会指向错误目录。多行时可以用 `#` 注释掉整行，带 `_test` 后缀的条目仍会正常显示在主菜单 Missions 列表中。

## 复制战役模板

随机挑选一个原版战役，例如 `data/missions/afistfulofcredits`，把它文件夹里的所有东西复制到 `data/missions/GearIn_test/`：

![测试战役模板文件夹的完整内容](./mission_folder.png "mission_folder.png")

原版战役文件夹包含 `descriptor.json`、`icon.jpg`、`mission_text.txt` 和一个存放 `MissionDefinition` 类的源文件。游戏会按照战役 ID 查找 `data.missions.GearIn_test.MissionDefinition`，因此源文件必须留在这个战役目录中，包名也必须与 `GearIn_test` 一致。接下来逐个修改这些内容。

## 配置战役描述

(原版文件自带尾逗号，游戏解析器可以接受；教程示例保持标准 JSON，便于静态工具检查。)

### descriptor.json 字段速查表

| 字段 | 是否必需 | 实际意义 |
|---|---|---|
| `title` | 是 | 战役名称，显示在 Missions 列表 |
| `difficulty` | 是 | 战役难度，可随意填写，只作显示 |
| `icon` | 是 | 战役图标贴图文件名，位于战役文件夹内 (本例沿用复制的 `icon.jpg`) |
| `background` | 否 | 战役背景贴图路径 (相对于 MOD 根目录，可指向原版 `graphics/`) |

`title`、`difficulty`、`icon` 缺失会让加载直接失败。

## 编写战役文本

里面是战役的详细文本，显示在战役详情页，可以随意填写。保持该文件存在即可，缺失同样会让加载失败。

## 实现战役定义

打开复制来的 `MissionDefinitionPlugin` 实现类源文件，整个替换为测试战役的 `MissionDefinition` 实现。文件位置：`data/missions/GearIn_test/`。

用户点击或重新选择战役条目时，游戏会实例化 `data.missions.GearIn_test.MissionDefinition`，再调用 `defineMission(MissionDefinitionAPI api)`。这个方法通过 `MissionDefinitionAPI` 设置双方舰队、说明和战场地图，战斗会使用这些设置生成预览并开始部署。

::: caution 包名是游戏写死的，不能改

**包名必须是 `data.missions.GearIn_test`**：游戏按战役 ID 把类名写死为 `data.missions.<mission_id>.MissionDefinition`，不是教程的命名偏好，**不能**改成 `data.scripts.gearin.*` 之类自己的包。

包名不符时，点击战役会抛出 `Problem loading class`。

:::

游戏启动时会扫描 `data/variants` 及其子目录中的 `.variant` 文件，并为每个已注册船体提供一个 `<hullId>_Hull` 空装配。因此，测试战役可以在正式装配尚未制作时列出已有船体。战役内容在 `defineMission(MissionDefinitionAPI api)` 中定义，共三件事：

- `initFleet(FleetSide.PLAYER, "SIM", FleetGoal.ATTACK, false, 5)` 初始化用户舰队 (`"SIM"` 是舰船自动命名前缀，末尾的 `5` 是初始指挥点数)；对 `ENEMY` 侧同样初始化。
- `Global.getSettings().getAllVariantIds()` 枚举全部装配 ID，`getVariant(id)` 读取对应的 `ShipVariantAPI`。代码按船体 ID 前缀、船体尺寸和装配 ID 过滤结果，再用 `addToFleet` 加入用户舰队；跳过 `mission_` 前缀可以避免把旧导出混进来。
- 添加一个敌方空间站 (`remnant_station2_Standard`，原版装配)，再布置地图、星云、点位和小行星带。

想创建第二个测试战役：把包名 `data.missions.GearIn_test` 换成新战役 ID，并把装配过滤用的前缀换成您的前缀。实现不引用任何本 MOD 的私有 ID，因此新战役无需修改其他代码。

替换完毕后即可直接使用，无需后期修正。部署后的战斗画面如下图所示，用户舰队自动列出本 MOD 的所有舰船：

![测试战役中自动列出的 MOD 舰船](./mission_test.png "mission_test.png")

## 完整最小例

注册行写入 `data/missions/mission_list.csv`；战役描述与文本写入 `data/missions/GearIn_test/` 下的 `descriptor.json`、`mission_text.txt`；`MissionDefinition` 类源文件写入同一目录 (包名 `data.missions.GearIn_test`)。

```csv
mission
GearIn_test
```

```json
{
	"title": "Gear 测试",
	"difficulty": "简单",
	"icon": "icon.jpg",
	"background": "graphics/backgrounds/background4.jpg"
}
```

::: details 完整成品：MissionDefinition

```java
package data.missions.GearIn_test;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.combat.ShipVariantAPI;
import com.fs.starfarer.api.fleet.FleetGoal;
import com.fs.starfarer.api.fleet.FleetMemberType;
import com.fs.starfarer.api.mission.FleetSide;
import com.fs.starfarer.api.mission.MissionDefinitionAPI;
import com.fs.starfarer.api.mission.MissionDefinitionPlugin;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 测试战役模板：把本 MOD 所有非战机装配自动加入玩家舰队，用于装配验证与导出。
 */
public class MissionDefinition implements MissionDefinitionPlugin {

    public static final String PREFIX = "GearIn_";

    @Override
    public void defineMission(MissionDefinitionAPI api) {
        api.initFleet(FleetSide.PLAYER, "SIM", FleetGoal.ATTACK, false, 5);
        api.initFleet(FleetSide.ENEMY, "SIM", FleetGoal.ATTACK, true, 5);

        api.setFleetTagline(FleetSide.PLAYER, "TEST");
        api.setFleetTagline(FleetSide.ENEMY, "TEST");

        api.addBriefingItem("TEST");

        List<String> variants = new ArrayList<>();
        for (String id : Global.getSettings().getAllVariantIds()) {
            ShipVariantAPI variant = Global.getSettings().getVariant(id);
            if (variant.getHullSpec().getHullId().startsWith(PREFIX)
                    && variant.getHullSpec().getHullSize() != ShipAPI.HullSize.FIGHTER
                    && !id.startsWith("mission_")) {
                variants.add(id);
            }
        }
        Collections.sort(variants);

        boolean first = true;
        for (String variant : variants) {
            api.addToFleet(FleetSide.PLAYER, variant, FleetMemberType.SHIP, first);
            first = false;
        }

        api.addToFleet(FleetSide.ENEMY, "remnant_station2_Standard", FleetMemberType.SHIP, false);

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

:::

## 在测试战役中导出装配

启动游戏进入该测试战役。用户舰队会列出当前 MOD 的所有船体；注意每个船体即使还没有任何正式装配，也会以自动生成的 `<hullId>_Hull` 空装配形式出现。为空装配装上武器、船插，再让游戏自动导出，即是本节取得正式装配的方法。

在装配界面修改舰船设计后，游戏会把当前设计写入存档目录的 `missions\<mission_id>`：

![测试战役导出的舰船装配文件](./variant_export.png "variant_export.png")

存档根目录由 `vmparams` 的 `-Dcom.fs.starfarer.settings.paths.saves` 决定。默认位置是 `saves/missions/GearIn_test/`，文件名为 `mission_GearIn_test_ship_0.variant`(第一个进入用户舰队列表的船序号为 0)，其中的 `variantId` 也是 `mission_GearIn_test_ship_0`。

然后修改文件名为容易辨识的形式 —— 本例是 `GearIn_artizan` 这艘船的装配，改名为 `GearIn_artizan_combat.variant`。打开文件：

```json
{
    "displayName": "Custom",
    "fluxCapacitors": 0,
    "fluxVents": 0,
    "hullId": "GearIn_artizan",
    "hullMods": [],
    "permaMods": [],
    "sMods": [],
    "variantId": "mission_GearIn_test_ship_0",
    "weaponGroups": [
        {
            "autofire": false,
            "mode": "LINKED",
            "weapons": {
                "WS0003": "lightdualac",
                "WS0004": "lightdualac"
            }
        },
        {
            "autofire": true,
            "mode": "ALTERNATING",
            "weapons": {
                "WS0001": "phasecl",
                "WS0002": "phasecl"
            }
        },
        {
            "autofire": true,
            "mode": "LINKED",
            "weapons": {
                "WS0005": "GearIn_durin",
                "WS0006": "GearIn_durin"
            }
        }
    ]
}
```

需要修改两处：

- `variantId`：从 `mission_GearIn_test_ship_0` 改为 `GearIn_artizan_combat`(建议与文件名一致，且不要保留 `mission_` 前缀 —— 那是任务导出装配的命名空间)；
- `displayName`：从 `Custom` 改为希望显示的装配名，例如 `突击型`。

其余字段无需修改：武器和船插已经在测试战役里装配好了，`hullId` 指向 `GearIn_artizan` 无误。

::: warning variantId 千万要记得改

游戏会扫描 `data/variants` 及其子目录中的 `.variant` 文件，并以 `variantId` 注册装配。这个 ID 必须全局唯一；复制会让同一个 ID 同时存在于存档导出目录和 MOD 目录，先注册的装配生效，后注册的装配会被跳过。

:::

### 装配 JSON 字段速查表

| 字段 | 是否必需 | 实际意义 |
|---|---|---|
| `variantId` | 是 | 装配 ID，全局唯一，建议与文件名一致 |
| `hullId` | 是 | 指向已注册船体，必须与 `.ship`、`ship_data.csv` 一致 |
| `displayName` | 是 | 装配显示名，出现在装配界面与舰队面板 |
| `fluxVents` / `fluxCapacitors` | 是 | 幅散 / 幅容数量 |
| `weaponGroups` | 是 | 武器组数组；每组含 `mode`(只能是 `LINKED` 或 `ALTERNATING`，`WeaponGroupType` 枚举仅这两个值)、`autofire`(可选，默认 `false`)、`weapons`(槽位 ID → 武器 ID 的对象) |
| `hullMods` | 否 | 已安装船插 ID 数组 |
| `permaMods` / `sMods` | 否 | 永久船插 / S 插数组，常规装配通常留空 |
| `wings` | 否 | 航母的机库装配，按甲板顺序列出战机 wing ID |
| `goalVariant` | 否 | 布尔，默认 `false`；为 `true` 时该装配作为自动装配目标出现 |
| `tags` | 否 | 字符串数组，`hullMods` 之外的自由标记 |
| `modules` | 否 | 模块舰 (空间站、超体等) 的模块装配，本节不展开 |

字段的必填性直接来自装配文件的 JSON 解析规则，`displayName`、`hullId`、`variantId` 为必读，`fluxVents`/`fluxCapacitors` 为必读整数，`weaponGroups` 为必读数组；其余为可选。

::: tip 自动装配

如果您希望这个装配出现在装配界面左下角的 "自动装配" 里，在 `variantId` 附近加上：

```json {1}
    "goalVariant": true,
```

自动装配目标按船体分组：每个 hull 至少需要一个 `goalVariant` 装配，该装配才会出现在对应船体的自动装配候选里。

:::

如果您还记得前置知识中的战机装配文件 [C1-S3]，它的结构与此相同 —— \*\* 舰船装配文件和战机装配文件没有本质区别\*\*，只是战机装配通常没有 `fluxVents` 之外的复杂内容，且它的 `hullId` 指向战机船体。

编者建议为您的所有舰船各制作 2\~3 个装配 (不同定位：突击、支援、长程等)，作为后续测试和战役配置的素材。

## 制作常规战役

有了装配之后，制作常规战役极其简单：

1. 想好战役名称，编纂一个合理且不会与其他 MOD 冲突的战役 ID(沿用 `GearIn_` 前缀，例如 `GearIn_artizan_demo`)。
2. 随机挑选一个原版战役文件夹 (例如 `afistfulofcredits`)，整体复制到 MOD 的 `data/missions` 下，文件夹改名为新战役 ID。
3. 逐个替换内容：
    - `MissionDefinition` 类的**包名**改为 `data.missions.<新ID>`；
    - 舰队初始化参数、`setFleetTagline` / `addBriefingItem` 的文本改成自己的文案；
    - `addToFleet` 里的装配 ID 换成您自己 MOD 的装配 (例如 `GearIn_artizan_combat`)，敌舰换成您想测试的对手；
    - 地图、星云和点位按需调整。
4. 在 `data/missions/mission_list.csv` 中追加一行新战役 ID。

发布 MOD 时，如果您不希望用户在 Missions 列表里看到您的调试战役，建议把测试战役从 `mission_list.csv` 中注释或移除 (`#`)。

## 验证方案

前置数据：`mod_info.json`[C1-S1]、船体 `GearIn_artizan`[C1-S2]、武器 `GearIn_durin`[C1-S5]、本节的 `data/missions/mission_list.csv`、`data/missions/GearIn_test/`(descriptor.json、mission\_text.txt、icon、`MissionDefinition` 类源文件) 与 `data/variants/GearIn_artizan_combat.variant`。

1. 启动游戏，打开主菜单 Missions 列表。
2. 点击测试战役并部署，观察用户舰队、敌方空间站和地图。
3. 返回主菜单前在装配界面修改一艘船的武器，检查 `saves/missions/GearIn_test/mission_GearIn_test_ship_0.variant` 已生成且内容与修改一致。
4. 把导出文件剪贴为 `data/variants/GearIn_artizan_combat.variant`，修改 `variantId` / `displayName`，加入 `"goalVariant": true`，然后重启游戏并打开任意装配界面。
5. 打开新建的常规战役，核对舰队、文案与地图，再检查 `starsector.log` 的任务加载记录。

预期结果：

- 启动游戏后，主菜单 Missions 列表出现 "一次好买卖"(`GearIn_test`) 条目；点击后详情页显示图标、背景、`mission_text.txt` 文本与 "Tactical Briefing" 下的 `TEST` 条目；日志出现 `Loading missions`，且没有相关 `ERROR`。
- 部署后用户舰队列出本 MOD 所有船体 (每个 hull 至少一个条目，尚未制作装配的以空装配形态出现)，敌方为余烬空间站；地图含星云、传感器阵列、通讯中继、导航信标和小行星带。
- 在装配界面修改任何一艘船并返回后，`saves/missions/GearIn_test/` 出现 `mission_GearIn_test_ship_0.variant`，内容即该船当前装配。
- 把导出文件剪贴到 `data/variants`、改名并改 `variantId` 后重启游戏：日志出现 `Loading variant [data/variants/GearIn_artizan_combat.variant]`；设置 `goalVariant: true` 后，该装配出现在装配界面的自动装配列表。
- 常规战役出现在 Missions 列表，并使用自己的装配、文案和地图设置。

## 故障分析

- **Missions 列表里没有您的战役**：战役行以 `#` 开头时列表不会出现条目；已注册的战役文件夹缺失不会静默缺条目，而是加载中断。`mission_list.csv` 缺失仅贡献 0 行、列表为空 (不中断加载)；表头不是 `mission` 时 `readCSV` 对每行取 `mission` 键会直接抛异常、中断整个加载。

    - **解决方案**：确认 CSV 第一行是 `mission`、第二行是战役 ID，且目录为 `data/missions/GearIn_test`，重启。

- **游戏加载中断，进不了主菜单**：已注册的战役文件夹、`descriptor.json`、`mission_text.txt` 或图标缺失，JSON 语法错误，或 `title`、`difficulty`、`icon` 任一必填字段缺失时，`Loading missions` 后会出现异常堆栈。

    - **解决方案**：补齐文件和字段并确认图标真实存在，重启。

- **战役出现但点击时报错无法进入**：`MissionDefinition` 源文件缺失，或包名、类名不是 `data.missions.<ID>.MissionDefinition` 时，点击会抛出 `Problem loading class [data.missions.<ID>.MissionDefinition]`。

    - **解决方案**：让包名逐字匹配战役 ID，并把类放入 `data/missions/<ID>/`。

- **战役里没有自己的船**：`PREFIX` 拼写或大小写错误，或过滤条件使用了错误前缀。`getAllVariantIds()` 遍历装配 ID，船体过滤应读取 `variant.getHullSpec().getHullId()`。

    - **解决方案**：将前缀统一为 `GearIn_`，重新进入战役。

- **战役里出现没有武器的空船**：每个注册船体都会生成 `<hullId>_Hull` 空装配，模板会在没有正式装配时把它列出。

    - **解决方案**：用测试战役完成武器装配并导出正式 variant，再重新进入战役。

- **导出文件放回 MOD 后仍读到旧内容**：只改文件名却保留 `mission_` 开头的 `variantId`，或新 ID 与其他来源重复时，旧注册项仍会命中，后注册项会被静默跳过。

    - **解决方案**：剪贴导出文件，将 `variantId` 改为与文件名一致的唯一 ID，例如 `GearIn_artizan_combat`，删除旧同名文件并重启。

- **装配里的武器不出现**：`weapons` 中的槽位 ID 不属于该船体、武器 ID 未注册，或 `hullId` 不一致时，部署会报告 `slot id not found`，装配构建会报告 `Weapon spec not found`。

    - **解决方案**：只使用 [C1-S2] 已确认的槽位和 [C1-S5] 注册的武器，不确定时重新在测试战役中导出，再次部署。

- **装配加载直接失败**：`weaponGroups` 的 `mode` 不是 `LINKED` 或 `ALTERNATING`，或必填字段缺失时，`Loading variant [...]` 后会抛出 `Key [mode] has invalid value [...]`。

    - **解决方案**：改用有效枚举值并补齐字段，重启。

- **自动装配列表没有目标装配**：`goalVariant` 键名拼错、值不是 `true`，或该 hull 没有其他可装配目标时，列表不会显示该 variant。

    - **解决方案**：精确写入 `"goalVariant": true` 并核对 `hullId`。

- **常规战役复制后仍是原版内容**：只改文件夹名而未同步 `MissionDefinition` 的包名、文本和装配 ID，会保留原版舰船与文案；包名不匹配时还会出现 `Problem loading class`。

    - **解决方案**：按 "制作常规战役" 的清单同步替换并在 `mission_list.csv` 注册，重启。

## 总结

`data/missions/mission_list.csv` 注册战役 ID，同名目录提供描述、文本、图标和 `MissionDefinition`。玩家选择战役时，`defineMission()` 使用 `MissionDefinitionAPI` 设置舰队、说明和地图。

测试战役会列出已注册船体的装配，并把装配界面中的设计保存到 `<saves>/missions/<ID>/`。将导出文件剪贴到 `data/variants` 并设置唯一的 `variantId` 后，它会成为战役与生涯都能读取的正式装配。

`SettingsAPI` 可以按 ID 读取或枚举装配，`FactoryAPI` 和 `MissionDefinitionAPI` 则使用这些装配创建舰队成员。常规战役通过自己的注册行、目录和定义类使用本节整理好的装配。

调试时先问战役注册了吗 (Missions 列表)、类名对吗 (点击是否报 `Problem loading class`)、装配 ID 唯一吗 (重复会被静默跳过)。

[C3-S2] 会把常规势力 (faction) 的舰船池与舰队配置接入生涯阵营，[C3-S3] 再设置初始关系。

## 建议参考项

- `com.fs.starfarer.api.Global`：全局入口，`getSettings()` 提供 `SettingsAPI`。
- `com.fs.starfarer.api.mission.MissionDefinitionPlugin`：战役脚本入口；
- `com.fs.starfarer.api.mission.MissionDefinitionAPI`：战役定义接口；
- `com.fs.starfarer.api.mission.FleetSide`：舰队侧枚举；
- `com.fs.starfarer.api.fleet.FleetGoal`：舰队目标枚举；
- `com.fs.starfarer.api.fleet.FleetMemberType`：成员类型枚举。
- `com.fs.starfarer.api.combat.ShipVariantAPI`：装配信息读取；
- `com.fs.starfarer.api.combat.ShipHullSpecAPI`：船体规格读取；
- `com.fs.starfarer.api.combat.ShipAPI`：战斗实体读取 (`HullSize` 舰体等级)。
- `com.fs.starfarer.api.SettingsAPI`：按 ID 读装配、枚举全部装配 ID (`getVariant`、`getAllVariantIds`)。
- `com.fs.starfarer.api.loading.WeaponGroupType`：武器组模式枚举 (`LINKED`、`ALTERNATING`)。
- `data/missions/mission_list.csv`：原版战役注册表 (列名 `mission`)；
- `data/missions/afistfulofcredits/`：原版战役样例 (descriptor.json、mission\_text.txt、icon、`MissionDefinition` 类源文件)。
- `data/variants/fighters/broadsword_Fighter.variant`：原版战机装配样例；
- `data/variants/stations/remnant_station2_Standard.variant`：模板敌舰使用的原版空间站装配；
