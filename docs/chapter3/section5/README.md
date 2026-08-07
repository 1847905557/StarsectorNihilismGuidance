# 制作一个自定义实体

本节定义一个无需新 Java 插件的生涯实体：它使用已有中继资源的图标和精灵，拥有 GearIn 自己的名称、发现范围和 CUSTOM 描述，并由既有星系生成模板投放。

*本节预期阅读时间：~14 分钟。*

*本节预期操作时间：~12 分钟。*

## 学习目标

完成后，您将能够：

* 在 `data/config/custom_entities.json` 增加静态实体类型；
* 用 `baseId` 继承一组稳定的默认显示参数；
* 配置图标、精灵、地图名称、可发现性和发现经验；
* 用 `addCustomEntity` 投放类型，并由 CUSTOM 描述完成界面闭环。

## 前置知识

* [C3-S4] 的数据文件合并和资源路径；
* [C3-S6] 的 `addCustomEntity` 与轨道设置；
* [C3-S8] 的 CUSTOM 描述键。

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

完成信号：加载器能在 `baseId` 解析后得到一个完整实体 spec，空 `pluginClass` 让它保持静态实体。

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

完成信号：地图图标、实体精灵和描述键都指向 GearIn 类型。

### 配置发现规则

```json
"detectionRange":1800,
"discoverable":true,
"discoveryXP":150,
"showIconOnMap":true,
"showNameOnMap":true,
"layers":[STATIONS]
```

完成信号：玩家舰队靠近时，实体从未知状态变为可见，并在日志/经验反馈中出现发现结果。

### 投放生涯实体

```java
SectorEntityToken anchor = system.addCustomEntity(
        "GearIn_stable", "GearIn 导航锚",
        "GearIn_navigation_anchor", "gear_industry");
anchor.setCircularOrbitPointingDown(star, 90, 2200, 100);
```

完成信号：新档中导航锚位于恒星轨道上，势力颜色和地图名称可观察。

## 排查实体继承故障

下面是**局部故障片段**：

```json
"GearIn_navigation_anchor":{
    "baseId":"missing_relay",
    "detectionRange":1800,
    "discoverable":true
},
```

实际症状：主菜单加载阶段无法生成完整的自定义实体 spec，后续 `addCustomEntity` 找不到类型。

原因：`baseId` 必须指向已经加载的实体类型；继承解析完成后，子对象才会进入 spec 表。

下面是**局部修正片段**：

```json
"GearIn_navigation_anchor":{
    "baseId":"comm_relay",
    "pluginClass":"",
    "defaultName":"GearIn Navigation Anchor",
    "customDescriptionId":"GearIn_navigation_anchor",
    "detectionRange":1800,
    "discoverable":true,
    "discoveryXP":150
},
```

复验：重启后观察 `Loading custom campaign entity data`，再创建新档确认实体能被投放和发现。

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

1. 启动游戏并观察自定义实体 spec 加载日志。
2. 创建新档进入 GearIn 星系，沿导航锚轨道寻找实体。
3. 先在远处观察地图信息，再靠近实体，打开详情并核对 CUSTOM 描述。

预期结果：

* 实体有 GearIn 名称、图标和中继外观；
* 进入检测范围后实体可被发现并获得发现经验；
* 详情标题和正文来自 `GearIn_navigation_anchor,CUSTOM`；
* 日志没有 `baseId`、贴图或实体类型缺失错误。

## 故障分析

- **地图上没有实体名称**：检查 `showNameOnMap`、`defaultName` 和 `nameInText`。地图显示开关与实体显示文本是两组字段。修正后创建新档并打开星图，实体名称应出现在对应位置。
- **靠近实体后没有发现提示**：检查 `detectionRange` 是否大于 0、`discoverable` 是否为 true，并确认生成器已经把实体放进当前星系。修正后创建新档并让舰队进入探测范围，界面应出现发现提示并结算经验。
- **交互界面显示错误的描述**：检查 `customDescriptionId` 是否逐字匹配 [C3-S8] 的 CUSTOM 行。实体类型 ID 与描述键可以不同，但必须显式连接。修正后重新进入实体交互界面，目标描述应正常显示。

## 总结

静态 custom entity 的工作量集中在 spec：继承一套可靠默认值，再覆盖身份、资源和发现参数。生成器只负责投放位置，描述文件负责最后的界面文字，因此三条链可以分开排错。

## 建议参考项

* `data/config/custom_entities.json`：自定义生涯实体类型；
* `CustomEntitySpecAPI`：运行期实体 spec 字段；
* `LocationAPI.addCustomEntity`：投放入口；
* [C3-S8]：CUSTOM 描述挂接。
