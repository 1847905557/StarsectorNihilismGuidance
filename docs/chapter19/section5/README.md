# 为 Nexerelin 地面战加入 GearIn 单位与产业

GearIn 的市场进入地面战后，原版定义不会自动理解专属产业和部队。本节在 groundBattleDefs 中登记产业效果与插件，再用 GBDataManager 确认合并结果。

*本节预期阅读时间：~18 分钟。*

*本节预期操作时间：~30 分钟。*

## 学习目标

完成后，您将能够：

- 在 `groundBattleDefs.json` 中登记自定义产业和地面单位；
- 为产业直接绑定插件类，并区分通用 `plugins` 表；
- 用 `GBDataManager` 和 `GroundUnitDef` 在进入地面战前核对合并结果。

## 前置知识

- [C7-S1] 理解 Java 类、方法、字段与入口插件；
- [C8-S3] 理解每帧回调与暂停判断；[C8-S2] 理解战斗对象读取与对象边界；
- 能够用 Gradle 构建示例 MOD，并从 `starsector.log` 定位类名。

## 接入地面战定义

入侵 GearIn 军工市场时，地面战界面显示专属产业防御加成和 GearIn 单位；配置 ID 错误时，探针指出缺失定义而非等到战斗界面空白。

## 文件与 API 链路

`groundBattleDefs.json` 合并 `industries`、`unitTypes`、条件、能力和通用插件；`GBDataManager` 提供产业与通用插件表；`GroundUnitDef` 保存单位定义。产业的 `plugin` 字段直接写完整 Java 类名，不能引用通用 `plugins` 表的别名。

### 登记 GearIn 产业定义

在 `data/config/exerelin/groundBattleDefs.json` 的 `industries` 对象中，为市场产业 ID 增加防御强度、部队贡献和插件类：

```json
"GearIn_training_industry": {
  "strengthMult": 1.35,
  "troopCounts": {
    "marine": 0.5,
    "heavy": 0.25
  },
  "plugin": "data.scripts.gearin.modcompat.GearInGroundBattlePlugin"
}
```

登记后，`GBDataManager.getIndustryDef("GearIn_training_industry")` 返回非默认定义，并能读到 `strengthMult`。

### 登记地面战插件类

在 `groundBattleDefs.json` 的 `unitTypes` 对象中登记一类可在地面战界面出现的 GearIn 单位；完整插件类写在单位自身的 `pluginClass` 字段，`plugin` 是产业条目的字段：

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

`GroundUnitDef.getUnitDef("GearIn_line_infantry")` 会返回定义，且地面战部队选择器能按 `playerCanCreate` 显示它。

### 在加载后检查产业定义

`GearInGroundBattleProbe.hasIndustry()` 与 `hasUnit()` 把缺失产业或单位 ID 变成可诊断布尔值：

```java
boolean ready = GBDataManager.getIndustryDef(industryId) != null;
boolean unitReady = GroundUnitDef.getUnitDef(unitId) != null;
```

`getIndustryDef()` 对未知 ID 会回退到默认产业，因此还要检查返回对象的实际 ID 或关键数值，不能只判断非空。

日志会同时显示产业 ID、`strengthMult` 和 `unit=true`。

### 确认插件映射而非只看列表

`GearInGroundBattleProbe.findPlugin()` 只在检查通用插件时才从 `getPluginsById()` 按短 ID 取类名：

```java
String className = GBDataManager.getPluginsById().get(pluginId);
```

通用插件的返回值与 JSON 中类名逐字一致；产业插件则直接从产业定义核对完整类名。

## 排查关键故障

下面是**局部故障片段**：

```java
"plugin":"GearIn_ground_plugin"
```

实际症状：产业定义存在，但地面战插件没有运行。

原因：产业 `plugin` 字段要求完整 Java 类名；`plugins` 表只供与产业、条件等定义无关的通用插件按短 ID 查找。

下面是**局部修正片段**：

```java
"plugin":"data.scripts.gearin.modcompat.GearInGroundBattlePlugin"
```

复验：重新加载定义，让探针读取产业定义和单位定义，再进入一次该产业的地面战。

## 完整最小例

局部片段说明了行为如何逐步增加；以下是可直接放入示例 MOD 的完整角色类与资源。

::: details 完整成品：GearInGroundBattleProbe

```java
package data.scripts.gearin.modcompat;

import exerelin.campaign.intel.groundbattle.GBDataManager;
import exerelin.campaign.intel.groundbattle.GroundUnitDef;

public final class GearInGroundBattleProbe {
    public boolean hasIndustry(String industryId) {
        return GBDataManager.getIndustryDef(industryId) != null;
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

1. 执行 `gradle jar` 构建示例 MOD，确认编译完成且没有报错。
2. 启动游戏并进入本页场景，执行标题描述的动作。
3. 重复一次边界动作，观察日志、画面、数值或生涯对象是否保持一致。

预期结果：

- `GBDataManager.getIndustryDef` 返回 GearIn 产业定义而非默认定义；
- `GroundUnitDef.getUnitDef` 返回 GearIn 单位；
- 产业插件类名和通用插件映射分别核对成功；
- 地面战界面显示该产业贡献的部队数值。

## 故障分析

- **产业定义存在但插件没有运行**：产业 `plugin` 使用了通用插件短 ID，或完整类名无法实例化。

    - **解决方案**：把可加载类名写在产业对象中，重载定义。

- **运行期查询不到产业**：`industries` 对象键与市场产业 ID 的大小写不一致。

    - **解决方案**：统一 ID，并检查 `getIndustryDef()` 是否仍返回默认定义。

- **插件映射存在但加载失败**：产业引用了错误类名，或误把 `plugins` 别名当作产业类。

    - **解决方案**：改用可实例化的完整类名。

- **单位数值没有变化**：修改落在错误层级，`strengthMult`、`troopCounts` 和单位 `strength` 各自承担不同计算。

    - **解决方案**：调整实际参与计算的字段。

## 总结

地面战接入按定义类型分层：产业和单位使用各自的对象键，产业直接绑定插件类，通用插件才进入 `plugins` 映射。`GBDataManager` 与 `GroundUnitDef` 的运行期查询能在进入战斗前区分真实定义、默认回退和缺失单位。

## 建议参考项

- `groundBattleDefs.json`：地面战合并定义；
- `exerelin.campaign.intel.groundbattle.GBDataManager`：`getIndustryDef` 查询产业定义，`getPluginsById` 查询插件类映射。
