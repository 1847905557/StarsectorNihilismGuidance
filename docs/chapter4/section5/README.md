# 制作一种星体类型

本节把 GearIn Prime 改为自定义的余烬世界。行星类型在 planets.json 中登记，提供贴图、颜色、大气、云层和旋转参数；星系生成器继续负责位置与轨道，只替换当前行星使用的类型 ID。

_本节预期阅读时间：\~12 分钟。_

_本节预期操作时间：\~12 分钟。_

## 学习目标

完成后，您将能够：

- 登记一个新的行星类型；
- 选择纹理、图标、大气、云层、旋转与描述键；
- 核对 isStar、颜色数组和资源路径；
- 在已有生成器中使用类型，并观察新档结果。

## 前置知识

- [C3-S4] 的星系生成器、Prime 和 addPlanet；
- [C3-S5] 的 PLANET 描述与查找键；
- [C1-S1] 的数据目录和对象合并；
- [C1-S7] 的 CSV 描述格式。

## 确定余烬世界的外观

GearIn Prime 使用棕红大气、厚云层和较低的旋转参数，表现炼厂世界。纹理先采用原版 terran，云层也采用已登记的原版资源。

**外观设计可以从组合现有资源开始。** 地表纹理还会受到光照、色调与云层叠加的影响，单独的图片预览与行星渲染未必相同。确定需要自制的贴图时，以这组叠加后的表现为依据。

## 登记类型与描述

在 MOD 的 `data/config/planets.json` 顶层对象中添加 `GearIn_ember_world`。下面是身份与资源的局部字段：

```json
"GearIn_ember_world":{
    "name":"Ember World",
    "descriptionId":"GearIn_ember_world",
    "isStar":false,
    "texture":"graphics/planets/terran.jpg",
    "icon":"graphics/warroom/icon_planet.png"
},
```

`name` 是类型名称，`isStar: false` 表示行星类型，texture 与 icon 分别指向原版地表纹理和星图图标。`descriptionId` 再将这个类型连接到 PLANET 描述行。

在已有 `descriptions.csv` 追加：

```csv
GearIn_ember_world,PLANET,被炼厂烟尘和赤红云层包围的工业世界，地表昼夜都映着熔炉的光。,,,,,
```

这条描述由本节定义。**类型 ID 与描述键可以分别设置，本例采用同一个值**，便于追踪；CSV 转义沿用第一章，字段使用沿用第三章。

## 配置地表、大气与星图颜色

身份与资源确定后，在同一个类型对象中继续填写颜色字段：

```json
"planetColor":[255,255,255,255],
"atmosphereThickness":0.18,
"atmosphereThicknessMin":35,
"atmosphereColor":[190,95,55,180],
"iconColor":[255,150,90,255],
```

planetColor 为地表贴图着色，本例保留白色；atmosphereColor 设置棕红大气，iconColor 设置星图图标的橙色。颜色均使用 RGBA 四项。

atmosphereThickness 与 atmosphereThicknessMin 控制大气层的厚度表现，当前采用 0.18 与 35。行星边缘的大气与星图图标分别由对应字段控制，验证时也在这两处分别核对。

## 设置云层与旋转

```json
"lightPosition":[-2,1,8],
"cloudTexture":"graphics/planets/clouds_banded01.png",
"cloudColor":[255,205,180,180],
"cloudRotation":-8,
"rotation":-3
```

cloudTexture 使用原版云纹理，cloudColor 给云层着色。rotation 与 cloudRotation 分别控制地表和云层的旋转，本例为 -3 与 -8。两层使用各自的参数，因此云层可以相对地表独立变化。

完整例还设置 tilt 为 20、pitch 为 30，lightPosition 为 `[-2,1,8]`，共同影响表面的角度与受光。调整时先固定贴图，比较一组参数带来的变化，再继续修改下一组。

## 用新类型替换 Prime

在 `GearInSystemGenerator.generate()` 的 Prime 创建语句中，把类型改为：

```java
PlanetAPI prime = system.addPlanet(
        "GearIn_planet1", star, "GearIn Prime",
        "GearIn_ember_world", 55, 150, 3200, 120);
```

行星实体 ID、名称和轨道保留，只将类型参数替换为刚才登记的键。Second 继续使用 barren，市场仍绑定 Prime，已有的实体与市场关系继续沿用。

## 完整最小例

以下对象写入 `data/config/planets.json`，已有文件时只追加自己的类型键；描述行与生成器采用上面的设置。

```json
{
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
}
}
```

## 验证方案

1. 保存类型数据、描述行与生成器，重启游戏，检查 planet data 加载记录。
2. 创建新生涯，进入 GearIn，查看 Prime 与 Second。
3. 悬停 Prime，打开详情和星图，比较地表、大气、云层、图标与描述。
4. 等待行星画面更新，观察云层和地表的相对旋转。

预期结果：

- Prime 使用 GearIn_ember_world，显示棕红大气、独立云层和当前旋转参数。
- 星图图标采用橙色。
- 类型说明读取本节 PLANET 描述。
- Second 保留原版 barren，Prime 的市场和轨道关系保持已设配置。

## 故障分析

- **主菜单前出现 JSON 或资源错误**：类型字段格式、逗号、颜色长度或图片路径错误。
    - **解决方案**：按日志检查字段和资源，填写四项颜色并核对实际文件。

- **行星描述不符**：descriptionId 与 PLANET 行不对应。
    - **解决方案**：核对两处描述键，保存并重启后查看。

- **仍显示原版类型**：生成器中的类型 ID 未替换，或当前生涯采用此前生成的对象。
    - **解决方案**：核对 Prime 的 addPlanet 参数，保存并创建测试生涯。

- **对象使用恒星或行星类型不符**：isStar 与当前生成调用的用途不同。
    - **解决方案**：恒星初始化使用恒星类型，行星创建使用行星类型，再创建生涯验证。

## 总结

星体类型定义外观和描述，生成器引用类型并决定位置与轨道。本节保留 Prime 的实体和市场关系，只替换视觉类型，地表、大气、云层和图标各自有对应参数。

这种组织方式便于复用：同一种类型可以出现在多颗行星上，每颗行星仍有自己的位置与实体说明。需要为具体行星写独立文本时，继续使用第三章的 CUSTOM 绑定。

## 建议参考项

- `com.fs.starfarer.api.campaign.PlanetSpecAPI`：类型字段与描述键。
- `data/config/planets.json`：恒星与行星类型。
- `data/strings/descriptions.csv`：PLANET 描述行。
- `graphics/planets/terran.jpg`：地表纹理。
- `graphics/planets/clouds_banded01.png`：云层纹理。
- `graphics/warroom/icon_planet.png`：星图图标。
