# 制作一个自定义实体

本节定义一个无需新 Java 插件的生涯实体：它使用已有中继资源的图标和精灵，拥有 GearIn 自己的名称、发现范围和 CUSTOM 描述，并由 [C3-S4] 的星系生成器投放。

*本节预期阅读时间：~14 分钟。*

*本节预期操作时间：~12 分钟。*

## 学习目标

完成后，您将能够：

- 在 `data/config/custom_entities.json` 增加静态实体类型；
- 用 `baseId` 继承一组稳定的默认显示参数；
- 配置图标、精灵、地图名称、可发现性和发现经验；
- 用 `addCustomEntity` 投放类型，并由 CUSTOM 描述完成界面闭环。

## 前置知识

- [C4-S5] 的数据文件合并和资源路径；
- [C3-S4] 的 `addCustomEntity` 与轨道设置；
- [C3-S5] 的 CUSTOM 描述键。

## 确定实体用途

"导航锚" 不会提供新的交互逻辑，它负责一件基础世界对象应该负责的事：在星图上显示、靠近后被发现、拥有稳定的图标和描述。动态交互与自绘插件留给后续进阶篇。

## 配置实体属性

### 建立实体类型键

```json
"GearIn_navigation_anchor":{
    "baseId":"comm_relay",
    "pluginClass":"",
    "defaultName":"GearIn Navigation Anchor",
    "nameInText":"navigation anchor",
    "shortName":"anchor"
},
```

加载器能在 `baseId` 解析后得到一个完整实体 spec，空 `pluginClass` 让它保持静态实体。

::: caution `baseId` 必须指向已加载的实体类型

`baseId` 指向不存在的类型时，继承解析无法完成，主菜单加载阶段就生成不出完整实体 spec，后续 `addCustomEntity` 也找不到该类型。

:::

### 配置图标与外观

```json
"icon":"graphics/icons/relay.png",
"sprite":"graphics/ships/com_relay.png",
"iconWidth":24,
"iconHeight":24,
"spriteWidth":89,
"spriteHeight":48,
"customDescriptionId":"GearIn_navigation_anchor",
```

地图图标、实体精灵和描述键都指向 GearIn 类型。

在 `data/strings/descriptions.csv` 中定义这个键：

```csv
GearIn_navigation_anchor,CUSTOM,一座用于校准本地航路的齿轮工业导航锚。,GearIn 导航锚,,,,
```

CSV 键和转义规则见 [C3-S5]，本页负责定义 `GearIn_navigation_anchor` 这一行。

### 配置发现规则

```json
"detectionRange":1800,
"discoverable":true,
"discoveryXP":150,
"showIconOnMap":true,
"showNameOnMap":true,
"layers":[STATIONS]
```

玩家舰队靠近时，实体从未知状态变为可见，并在日志/经验反馈中出现发现结果。

### 投放生涯实体

```java
SectorEntityToken anchor = system.addCustomEntity(
        "GearIn_stable", "GearIn 导航锚",
        "GearIn_navigation_anchor", "gear_industry");
anchor.setCircularOrbitPointingDown(star, 90, 2200, 100);
```

新档中导航锚位于恒星轨道上，势力颜色和地图名称可观察。

::: details 完整成品：GearIn_navigation_anchor

```json
"GearIn_navigation_anchor":{
    "baseId":"comm_relay",
    "pluginClass":"",
    "defaultName":"GearIn Navigation Anchor",
    "nameInText":"navigation anchor",
    "shortName":"anchor",
    "icon":"graphics/icons/relay.png",
    "sprite":"graphics/ships/com_relay.png",
    "iconWidth":24,
    "iconHeight":24,
    "spriteWidth":89,
    "spriteHeight":48,
    "customDescriptionId":"GearIn_navigation_anchor",
    "detectionRange":1800,
    "discoverable":true,
    "discoveryXP":150,
    "showIconOnMap":true,
    "showNameOnMap":true,
    "layers":[STATIONS]
},
```

:::

## 验证方案

前置数据：已启用包含本节 `GearIn_navigation_anchor` 类型注册、CUSTOM 描述行和 `addCustomEntity` 投放替换的 GearIn MOD。

1. 启动游戏并观察自定义实体 spec 加载日志。
2. 创建新档进入 GearIn 星系，沿导航锚轨道寻找实体。
3. 先在远处观察地图信息，再靠近实体，打开详情并核对 CUSTOM 描述。

预期结果：

- 实体有 GearIn 名称、图标和中继外观；
- 进入检测范围后实体可被发现并获得发现经验；
- 详情标题来自实体名，正文来自 `GearIn_navigation_anchor,CUSTOM` 的 text1；
- 日志没有 `baseId`、贴图或实体类型缺失错误。

## 故障分析

- **地图上没有实体名称**：地图显示开关与实体显示文本是两组字段。
    - **解决方案**：检查 `showNameOnMap`、`defaultName` 和 `nameInText`，修正后创建新档。

- **靠近实体后没有发现提示**：`detectionRange` 不大于 0、`discoverable` 不为 true，或生成器没有把实体放进当前星系。
    - **解决方案**：修正后创建新档并让舰队进入探测范围。

- **交互界面显示错误的描述**：实体类型 ID 与描述键可以不同，但必须显式连接。
    - **解决方案**：检查 `customDescriptionId` 是否逐字匹配本页的 CUSTOM 行，修正后重新进入实体交互界面。

## 总结

静态 custom entity 的工作量集中在 spec：继承一套可靠默认值，再覆盖身份、资源和发现参数。生成器只负责投放位置，描述文件负责最后的界面文字，因此三条链可以分开排错。

## 建议参考项

- `com.fs.starfarer.api.campaign.CustomEntitySpecAPI`：运行期实体 spec 字段；
- `com.fs.starfarer.api.campaign.LocationAPI`：`addCustomEntity` 投放入口；
- `data/config/custom_entities.json`：生涯实体类型；
- `data/strings/descriptions.csv`：CUSTOM 行格式的对照依据；
- `graphics/icons/relay.png`：本节复用的原版图标；
- `graphics/ships/com_relay.png`：本节复用的原版精灵。
