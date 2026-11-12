# 制作一个自定义实体

本节在 Nipher 星系加入 "导航锚"，沿用原版中继的图标与贴图，设置自己的名称、发现范围和描述。它采用无插件的基础实体配置，生成器负责放置和轨道，当前先完成可显示、可发现的静态对象。

_本节预期阅读时间：\~14 分钟。_

_本节预期操作时间：\~12 分钟。_

## 学习目标

完成后，您将能够：

- 在 custom_entities.json 登记实体类型；
- 通过 baseId 复用原版基础配置；
- 设置资源、名称、发现参数与地图显示；
- 用 addCustomEntity 放置实体，并绑定 CUSTOM 正文。

## 前置知识

- [C4-S5] 的数据对象合并和资源路径；
- [C3-S3] 的生成器、实体创建和轨道；
- [C3-S4] 的 CUSTOM 描述；
- [C1-S7] 的 CSV 转义和字段边界。

## 复用基础实体配置

在 MOD 的 `data/config/custom_entities.json` 创建 `GearIn_navigation_anchor`。它使用原版 `base_campaign_objective` 作为基础，局部字段如下：

```json
"GearIn_navigation_anchor": {
    "baseId": "base_campaign_objective",
    "defaultName": "GearIn Navigation Anchor",
    "nameInText": "navigation anchor",
    "shortName": "anchor"
}
```

`baseId` 指定基础类型，默认半径、地图显示与渲染层等参数由它提供。`defaultName` 提供默认显示名，nameInText 与 shortName 提供文本中的名称形态，自有条目再填写需要采用的值。

**基础类型的行为也属于继承内容。** 当前选择原版无插件基础，显式设置自己的资源和发现参数；未来需要动态效果时，再按对象职责编写实体插件。基础 ID 需要存在，加载才能建立对应类型。

## 指定图标、贴图与描述

地图图标采用原版 relay，实体贴图采用 com_relay，局部字段如下：

```json
"icon": "graphics/icons/relay.png",
"sprite": "graphics/ships/com_relay.png",
"iconWidth": 24,
"iconHeight": 24,
"spriteWidth": 89,
"spriteHeight": 48,
"customDescriptionId": "GearIn_navigation_anchor"
```

两个资源分别用于地图图标和实体绘制，宽高字段控制各自的显示尺寸。路径相对于 MOD 根目录，原版资源可以直接引用，因此当前条目可以继续采用中继的图片。

在描述文件追加：

```csv
GearIn_navigation_anchor,CUSTOM,一座用于校准本地航路的齿轮工业导航锚。,Nipher 导航锚,,,,
```

本节定义这个描述键。text1 为正文，text2 提供相应界面的标题；实体配置中的 customDescriptionId 与这条记录使用同一键，便完成描述绑定。字段和转义继续沿用 [C3-S4] 与 [C1-S7]。

## 设置发现与地图显示

使用以下局部字段：

```json
"detectionRange": 1800,
"discoverable": true,
"discoveryXP": 150,
"showIconOnMap": true,
"showNameOnMap": true,
"layers": ["STATIONS"]
```

玩家舰队进入发现范围后可以发现实体，并取得 150 发现经验；地图显示开关分别控制图标与名称。STATIONS 选择实体的渲染层。

**发现状态与地图显示分别配置。** 从远处接近实体时，分别核对发现反馈、图标出现与名称显示，便能确认各字段对应的行为。

## 放到既有星系

在 `GearInSystemGenerator.generate()` 中，把此前通讯中继的类型替换为导航锚：

```java
SectorEntityToken anchor = system.addCustomEntity(
        "GearIn_stable", "Nipher 导航锚",
        "GearIn_navigation_anchor", "gear_industry");
anchor.setCircularOrbitPointingDown(star, 90, 2200, 100);
```

`GearIn_stable` 仍为实体 ID，类型参数改为 GearIn_navigation_anchor，势力为 gear_industry，当前显示名为 "Nipher 导航锚"。这次替换的是原有实体的创建语句，避免额外创建同 ID 实体。

类型数据负责表现与发现，生成器设置位置和轨道，描述行提供文字。更改分别写在这三处，按字段的读取时机验证结果。

## 完整最小例

以下内容写入 `data/config/custom_entities.json`；已有文件时添加自有类型键，保留原有内容。描述行和生成器使用上面的绑定。

```json
{
    "GearIn_navigation_anchor": {
        "baseId": "base_campaign_objective",
        "defaultName": "GearIn Navigation Anchor",
        "nameInText": "navigation anchor",
        "shortName": "anchor",
        "icon": "graphics/icons/relay.png",
        "sprite": "graphics/ships/com_relay.png",
        "iconWidth": 24,
        "iconHeight": 24,
        "spriteWidth": 89,
        "spriteHeight": 48,
        "customDescriptionId": "GearIn_navigation_anchor",
        "detectionRange": 1800,
        "discoverable": true,
        "discoveryXP": 150,
        "showIconOnMap": true,
        "showNameOnMap": true,
        "layers": ["STATIONS"]
    }
}
```

## 验证方案

1. 保存实体配置、描述行与生成器，重启游戏，核对自定义实体加载阶段。
2. 创建生涯进入 Nipher，沿半径 2200 的轨道寻找导航锚。
3. 在远处查看地图，再靠近到发现范围，观察名字、图标和经验反馈。
4. 悬停实体并打开详情，核对当前名称和 CUSTOM 正文。
5. 比较轨道和所属势力与创建参数一致。

预期结果：

- 当前实体采用导航锚名称与原版中继贴图、图标。
- 进入发现范围后产生发现结果和 150 经验反馈。
- 地图显示图标和名称，正文读取 GearIn_navigation_anchor,CUSTOM。
- 实体围绕恒星运行，所属势力为齿轮工业。
- 数据加载与生成调用使用已登记基础和类型。

## 故障分析

- **类型加载失败**：baseId 未登记或配置对象结构错误。
    - **解决方案**：核对 base_campaign_objective 和文件顶层对象，保存后重启。

- **地图名称不符**：显示开关或创建时的名称与预期不同。
    - **解决方案**：核对 showNameOnMap、defaultName、nameInText 和 addCustomEntity 传入的名称。

- **靠近后未发现**：发现范围、开关或实体实际所在容器不同。
    - **解决方案**：确认 detectionRange 为正、discoverable 为 true，并核对生成器当前 system。

- **正文不符**：customDescriptionId 与 CUSTOM 描述行没有对应。
    - **解决方案**：统一描述键，保存并重启，查看当前实体。

- **新档生成时报告效果类加载错误**：继承的插件类或显式 pluginClass 值无效。
    - **解决方案**：使用本节无插件的基础类型，保留静态配置，再重新创建生涯。

## 总结

导航锚通过原版基础类型复用显示参数，自有配置提供名称、资源与发现规则。生成器在具体位置放入实体，描述文件绑定独立正文，三部分按对象 ID 和类型键连接。

静态对象先完成这组关系，动态交互与绘制再结合后续实体插件讲解。核对类型加载、生成位置、发现状态和描述结果，可以分别定位到对应文件。

## 建议参考项

- `com.fs.starfarer.api.campaign.CustomEntitySpecAPI`：实体类型、资源、显示与发现字段。
- `com.fs.starfarer.api.campaign.LocationAPI`：addCustomEntity 创建入口。
- `data/config/custom_entities.json`：基础实体和自有类型登记。
- `data/strings/descriptions.csv`：CUSTOM 行与文本字段。
- `graphics/icons/relay.png`：地图图标。
- `graphics/ships/com_relay.png`：实体贴图。
