# 登记 Nexerelin 地面战单位与产业

Element Works 已经在市场中生产商品，现在给它补一份地面战定义。我们登记驻军贡献和防御强度，再创建一种玩家可组建的线列步兵。配置读取以后先查询实际 ID 与数值，再进入该市场的地面战观察；市场产业、地面战产业和单位各自使用自己的定义入口。

*本节预期阅读时间：\~18 分钟。*

*本节预期操作时间：\~30 分钟。*

## 学习目标

完成后，您将能够：

- 在 `groundBattleDefs.json` 登记产业和地面单位；
- 为产业绑定实际插件类，区分通用插件映射；
- 查询真实产业定义、默认回退与单位记录；
- 在地面战中观察配置的防御与部队结果。

## 前置知识

- [C12-S5] 的 `GearIn_element_works` 与实际市场产业；
- [C19-S1] 的 Nexerelin 编译与运行时接入；
- [C19-S2] 的齿轮工业势力；
- [C17-S2] 的 JSON 字段和合并对象。

## 给已有产业补一份地面战定义

创建 `data/config/exerelin/groundBattleDefs.json`，先保存本节完整 JSON。产业使用 [C12-S5] 已定义的 `GearIn_element_works`，以下为 `industries` 对象中的局部条目：

```json
"GearIn_element_works": {
    "strengthMult": 1.35,
    "troopCounts": {
        "marine": 0.5,
        "heavy": 0.25
    },
    "plugin": "exerelin.campaign.intel.groundbattle.plugins.IndustryForBattlePlugin"
}
```

**地面战产业键对应实际市场产业 ID。**这份记录提供基础防御强度倍率 1.35，以及 marine、heavy 两类驻军贡献。贡献值参与库的驻军生成计算，实际数量还取决于市场和驻军基础；它们不是直接写入 0.5 个士兵。

本例直接使用 Nexerelin 的 `IndustryForBattlePlugin`。它读取这份定义，按产业建造、中断和其他状态处理强度与贡献。正常完成的产业可以观察基础值，建造或真正中断时相应效果会改变；实际 AI 核心等修饰也继续参与。

`plugin` 放完整类名，库创建产业战斗对象时实例化它。市场生产继续使用 `GearInElementWorksIndustry`，地面战插件处理的是另一份战斗对象。

## 登记玩家可创建的线列步兵

在 `unitTypes` 对象加入：

```json
"GearIn_line_infantry": {
    "name": "GearIn line infantry",
    "type": "MARINE",
    "playerCanCreate": true,
    "strength": 1.2,
    "personnel": {
        "commodityId": "marines",
        "crewReplacerJobId": "nex_groundBattle_marines",
        "mult": 1
    },
    "tags": ["marine"],
    "sortOrder": 1.5
}
```

`type` 选择部队类型，`playerCanCreate` 允许玩家组建；`strength` 为 1.2，人员使用 `marines`，每份人数比例为 1。`crewReplacerJobId` 连接人员替代规则，`tags` 用于部队分类，`sortOrder` 调整显示顺序。

单位插件字段叫 `pluginClass`，省略时使用 `GroundUnitPlugin`。本例保持库的普通单位插件，数值与人员输入已经足够表达这支步兵。需要特殊单位行为时，再为 `pluginClass` 指定实际实现。

## 合并定义与通用插件各自读取什么

Nexerelin 从各 MOD 合并同路径 JSON。`industries`、`unitTypes`、`conditions`、`abilities` 与 `plugins` 分别保存不同定义；本例只增加产业与单位，库自己的条件、能力和通用插件继续存在。

通用 `plugins` 对象将短 ID 映射到完整类名。以下局部查询只读取该表：

```java
String className = GBDataManager.getPluginsById().get(pluginId);
```

产业的 `plugin` 则直接读取该产业条目的类名。**通用映射与产业绑定分别核对。**复制一个短 ID 到产业字段时，类加载器仍会把它当类名，因而无法实例化。

## 保存探针，确认真实登记

将完整类保存到 `src/data/scripts/gearin/modcompat/GearInGroundBattleProbe.java`。在 Nexerelin 已启用的生涯中，用 Console Commands 运行：

```text
runcode data.scripts.gearin.modcompat.GearInGroundBattleProbe p = new data.scripts.gearin.modcompat.GearInGroundBattleProbe(); com.fs.starfarer.api.Global.getLogger(p.getClass()).info("GearIn ground: " + p.describeIndustry("GearIn_element_works") + " exact=" + p.hasIndustry("GearIn_element_works") + " unit=" + p.hasUnit("GearIn_line_infantry"));
```

查询的产业 ID、强度、贡献与插件都进入日志。未知产业会返回 `industryId` 为 `default` 的对象，所以 `hasIndustry` 比较实际 ID；单位未登记时，`GroundUnitDef.getUnitDef(...)` 返回 `null`。

编者建议从对象身份开始查。默认定义能让地面战继续运行，但自有字段也会一起回到默认；画面没有崩溃，还需要确认到底使用了哪条记录。

## 完整最小例

JSON 保存到 `data/config/exerelin/groundBattleDefs.json`，两个对象分别增加产业和单位：

```json
{
    "industries": {
        "GearIn_element_works": {
            "strengthMult": 1.35,
            "troopCounts": {
                "marine": 0.5,
                "heavy": 0.25
            },
            "plugin": "exerelin.campaign.intel.groundbattle.plugins.IndustryForBattlePlugin"
        }
    },
    "unitTypes": {
        "GearIn_line_infantry": {
            "name": "GearIn line infantry",
            "type": "MARINE",
            "playerCanCreate": true,
            "strength": 1.2,
            "personnel": {
                "commodityId": "marines",
                "crewReplacerJobId": "nex_groundBattle_marines",
                "mult": 1
            },
            "tags": ["marine"],
            "sortOrder": 1.5
        }
    }
}
```

探针读取登记结果：

::: details 完整成品：GearInGroundBattleProbe

```java
package data.scripts.gearin.modcompat;

import exerelin.campaign.intel.groundbattle.GBDataManager;
import exerelin.campaign.intel.groundbattle.GroundUnitDef;

/** 查询地面战产业、单位与通用插件的实际登记。 */
public final class GearInGroundBattleProbe {
    public boolean hasIndustry(String industryId) {
        return industryId.equals(GBDataManager.getIndustryDef(industryId).industryId);
    }

    public String describeIndustry(String industryId) {
        GBDataManager.IndustryDef def = GBDataManager.getIndustryDef(industryId);
        return "industry=" + def.industryId + " strengthMult=" + def.strengthMult
                + " troops=" + def.troopCounts + " plugin=" + def.plugin;
    }

    public String findPlugin(String pluginId) {
        return GBDataManager.getPluginsById().get(pluginId);
    }

    public boolean hasUnit(String unitId) {
        return GroundUnitDef.getUnitDef(unitId) != null;
    }
}
```

:::

## 验证方案

前置数据：Nexerelin 与 Console Commands 已启用，测试生涯存在完成建造、正常运作的 Element Works 市场；玩家拥有用于组建部队的陆战队员。

1. 保存定义和探针，构建重启，载入测试生涯并执行正文查询。
2. 打开该市场的入侵地面战，查看 Element Works 与驻军；在玩家部队创建入口选择 GearIn line infantry，观察人数、基础强度与显示位置。
3. 将测试 JSON 的产业键暂时改为大小写不同的名称，重启并查询原 ID；记录默认结果，再恢复 `GearIn_element_works`、重启。
4. 比较正常产业与建造或中断状态下的防御和贡献，结合市场与其他修饰判断最终数值。
5. 用原版通用插件短 ID 调用 `findPlugin(...)`，比较 JSON 的通用映射和产业查询的完整类名。

预期结果：

- 原 ID 的 `exact=true`，`industry=GearIn_element_works`，基础倍率 1.35，驻军贡献包含 marine 0.5 与 heavy 0.25；
- `unit=true`，玩家部队入口显示可创建的线列步兵，并采用其人员与强度定义；
- 未登记原 ID 时 `exact=false`，描述返回 default，而单位查询按自身 ID 进行；
- 地面战按产业当前状态和其他修饰计算实际效果，通用插件与产业类名分别来自各自对象。

## 故障分析

- **产业查询一直显示 default**：定义键与实际市场产业 ID 不一致。
    - **解决方案**：统一 `GearIn_element_works`，重启并比较 `industryId`。

- **产业插件实例化失败**：`plugin` 填入了通用短 ID 或无法加载的类名。
    - **解决方案**：采用本节完整 `IndustryForBattlePlugin` 类名，重新启动。

- **线列步兵没有出现在创建入口**：单位 ID、type、可创建开关或人员资源未对应。
    - **解决方案**：核对 `GearIn_line_infantry` 条目与玩家陆战队员，再打开部队创建。

- **实际防御与 1.35 不同**：当前产业状态、AI 核心或其他修饰参与最终计算。
    - **解决方案**：先以正常完成的产业核对基础定义，再观察各修饰。

- **修改 JSON 后仍读到旧数据**：当前定义表尚未重新加载。
    - **解决方案**：保存并重启后查询，采用库的 `loadDefs()` 重载时在测试生涯核对当前活动战斗。

## 总结

已有市场产业通过同一个 ID 取得地面战定义，玩家部队则从 unitTypes 创建。产业插件直接绑定类名，通用插件用独立映射；探针先确认身份，再对照数值和画面。

Element Works 的生产职责继续保留，地面战新增自己的防御与驻军关系。按实际使用方分开配置，两个场景都能找到各自的输入和观察位置。

## 建议参考项

- `exerelin.campaign.intel.groundbattle.GBDataManager`：定义合并、产业回退和通用插件映射；
- `exerelin.campaign.intel.groundbattle.GroundUnitDef`：单位字段与查询；
- `exerelin.campaign.intel.groundbattle.plugins.IndustryForBattlePlugin`：产业战斗状态、强度与驻军贡献；
- `exerelin.campaign.intel.groundbattle.plugins.GroundUnitPlugin`：普通单位行为；
- `data/config/exerelin/groundBattleDefs.json`：本节定义入口。
