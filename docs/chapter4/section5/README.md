# 制作一种星体类型

本节增加一个 GearIn 专属行星类型，把原版纹理、星图图标和一组新的颜色参数组合成 "余烬世界"。整个成果落在 `planets.json`，星系生成器只替换一个类型 ID。

*本节预期阅读时间：~12 分钟。*

*本节预期操作时间：~12 分钟。*

## 学习目标

完成后，您将能够：

- 在 `data/config/planets.json` 注册一个新行星类型；
- 选择纹理、图标、大气、云层、旋转和描述键；
- 判断 `isStar`、资源路径和颜色数组对加载结果的影响；
- 把新类型接到既有 `addPlanet` 调用并在新档中观察。

## 前置知识

- [C3-S3] 的星系生成入口和 `addPlanet` 参数顺序；
- [C3-S4] 的 `PLANET` 描述键；
- 能够启动新档并进入 GearIn 星系。

## 定义星体外观

GearIn Prime 是炼厂世界：棕红色大气、厚云层、低速自转。贴图先复用原版 `terran.jpg`，让本节的重点落在数据字段，而不是图片处理。

## 注册星体类型

### 声明类型与行星身份

```json
"GearIn_ember_world":{
    "name":"Ember World",
    "descriptionId":"GearIn_ember_world",
    "isStar":false,
    "texture":"graphics/planets/terran.jpg",
    "icon":"graphics/warroom/icon_planet.png"
},
```

完成信号：日志进入 `Loading planet data` 后没有 `GearIn_ember_world` 相关异常。

### 挂接行星描述

在 `data/strings/descriptions.csv` 中为这个类型定义自己的 `PLANET` 行：

```csv
GearIn_ember_world,PLANET,被炼厂烟尘和赤红云层包围的工业世界，地表昼夜都映着熔炉的光。,,,,,
```

`id` 与 `descriptionId` 保持一致。CSV 键和转义规则见 [C3-S4]，本页负责定义 `GearIn_ember_world` 这一行。

### 配置大气颜色

```json
"planetColor":[255,255,255,255],
"atmosphereThickness":0.18,
"atmosphereThicknessMin":35,
"atmosphereColor":[190,95,55,180],
"iconColor":[255,150,90,255],
```

完成信号：星球详情的边缘光呈橙红色，星图图标也使用同一色调。

### 配置云层与旋转

```json
"lightPosition":[-2,1,8],
"cloudTexture":"graphics/planets/clouds_banded01.png",
"cloudColor":[255,205,180,180],
"cloudRotation":-8,
"rotation":-3
```

完成信号：进入星系后可以看到云层独立于地表缓慢转动。

### 接入星系生成器

在 `GearInSystemGenerator.generate()` 的 `addPlanet` 行只替换类型参数：

```java
PlanetAPI prime = system.addPlanet(
        "GearIn_planet1", star, "GearIn Prime",
        "GearIn_ember_world", 55, 150, 3200, 120);
```

完成信号：新档中 GearIn Prime 使用余烬世界的外观，而第二颗行星仍保持原版 `barren`。

## 排查星体类型加载故障

下面是**局部故障片段**：

```json
"GearIn_ember_world":{
    "name":"Ember World",
    "isStar":true,
    "texture":"graphics/planets/terran.jpg",
    "icon":"graphics/warroom/icon_planet.png",
    "planetColor":[255,120,80]
},
```

实际症状：加载阶段报 JSON/颜色错误；即使修正颜色后，`addPlanet` 仍把类型当作恒星与行星用途混用。

原因：颜色必须包含 RGBA 四个分量，`isStar` 与 `addPlanet` 的对象身份也必须一致。

下面是**局部修正片段**：

```json
"GearIn_ember_world":{
    "name":"Ember World",
    "descriptionId":"GearIn_ember_world",
    "isStar":false,
    "texture":"graphics/planets/terran.jpg",
    "icon":"graphics/warroom/icon_planet.png",
    "planetColor":[255,255,255,255],
    "atmosphereColor":[190,95,55,180]
},
```

复验：重新启动并创建新档，确认 GearIn Prime 可进入详情页且 `PLANET` 描述键生效。

::: details 完整成品：GearIn_ember_world

```json
"GearIn_ember_world":{
    "name":"Ember World",
    "descriptionId":"GearIn_ember_world",
    "tilt":20,
    "pitch":30,
    "rotation":-3,
    "planetColor":[255,255,255,255],
    "atmosphereThickness":0.18,
    "atmosphereThicknessMin":35,
    "atmosphereColor":[190,95,55,180],
    "texture":"graphics/planets/terran.jpg",
    "icon":"graphics/warroom/icon_planet.png",
    "iconColor":[255,150,90,255],
    "isStar":false,
    "lightPosition":[-2,1,8],
    "cloudTexture":"graphics/planets/clouds_banded01.png",
    "cloudColor":[255,205,180,180],
    "cloudRotation":-8
},
```

:::

## 验证方案

1. 启动游戏，观察 `Loading planet data` 日志。
2. 创建新档并进入 GearIn 星系，定位 GearIn Prime。
3. 打开行星详情和星图，观察地表、大气、云层、图标颜色与描述文本。

预期结果：

- GearIn Prime 使用 `GearIn_ember_world` 的橙红大气与云层；
- 星图图标使用 `iconColor` 的橙色；
- 行星详情显示本页定义的 `PLANET` 描述；
- 第二颗行星仍按原版类型显示。

## 故障分析

- **游戏在主菜单前停止加载**：检查 JSON 逗号、颜色数组长度和所有贴图路径。`planets.json` 会在加载期把贴图加入资源队列。

    - **解决方案**：修正后重新启动游戏。

- **行星详情没有描述**：类型键和描述键可以不同，但必须通过 `descriptionId` 连接。

    - **解决方案**：核对 `descriptionId` 是否等于本页 `PLANET` 行的 ID，修正后创建新档。

- **星体按错误的对象类型生成**：`initStar` 使用恒星类型，`addPlanet` 使用行星类型；二者共享 `planets.json` 注册表，但对象用途不同。

    - **解决方案**：核对 `isStar` 与生成调用，修正后创建新档。

## 总结

行星类型页把 "外观参数" 和 "生成位置" 分开：JSON 定义视觉身份，生成器只消费类型 ID。先复用原版资源验证字段，再替换贴图，是基础篇最稳的迭代顺序。

## 建议参考项

- `data/config/planets.json`：行星与恒星类型注册表；
- `PlanetSpecAPI`：运行期类型字段与描述键；
- [C3-S3]：`initStar`、`addPlanet` 和星系生成顺序；
- [C3-S4]：PLANET 描述挂接。
