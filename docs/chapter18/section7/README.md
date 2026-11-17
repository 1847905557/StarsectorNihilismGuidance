# 制作 GraphicsLib 纹理映射

没有法线和材质贴图时，再亮的光源照射舰船表面也像只映在一张平面立绘上，能看出明暗变化却拉不开立体层次。

GraphicsLib 通过纹理映射表替舰体与武器接出漫反射、法线和高光表面三层通道。不过比起手工画全三张图，更值得先弄清的是它在什么条件下会自动补全缺失法线、以及按标签预载入显存的时机，否则很容易出现贴图生效了、帧率却被无端拖垮的窘境。

---

_本节预期阅读时间：\~18 分钟。_

_本节预期操作时间：\~25 分钟。_

## 前置知识

- [C1-S2] 的 `GearIn_artizan` 舰船贴图与船体登记；
- [C18-S5] 在 `onApplicationLoad()` 中完成 GraphicsLib 初始化与能力判断；
- [C18-S6] 能够在战斗中照亮舰船表面的动态点光源；
- 能够导出带透明通道的 RGBA PNG 贴图并查看 `starsector.log`。

## 以原始贴图确定坐标基准

`graphics/ships/GearIn_artizan.png` 是游戏直接绘制的舰船 Sprite 贴图，也是三类附加纹理的坐标基准。GraphicsLib 在相同像素位置采样附加贴图，因此舰首、舰尾、装甲板和透明区都要与原始 Sprite 贴图重合。

material 贴图提供光照计算使用的表面颜色与反射差异。normal 贴图保存每个像素朝向哪个方向，让位于舰船左侧、右侧或上方的光源照出不同明暗。surface 贴图进一步控制镜面高光的强度和硬度；缺少它时，material 与 normal 仍然可以参与光照，只是没有该船专用的 surface 变化。

**三类附加纹理在原始贴图的同一像素位置采样。** 实际制作常先补齐 material 与 normal，再按美术需要决定是否加 surface。每类图表达的信息各有用途，画布和船体位置则一起保持一致。

## 制作同坐标的附加纹理

### 保留 material 的受光颜色

先复制原始舰船 Sprite 贴图，保留画布尺寸与透明通道，再把副本改成 material 贴图。处理时以 "光照下希望保留什么颜色、哪些区域更容易反光" 为判断标准：装甲主体保留可辨认的色相，金属边缘和高光区域可以更亮，孔洞与完全透明区域保持透明。

material 贴图仍是一张完整的 RGBA PNG。它不负责画出光源本身，也不替代原始舰船 Sprite 贴图；GraphicsLib 将它与场景光照组合，得到最终受光颜色。将结果保存为：

```text
graphics/material/ships/GearIn_artizan.png
```

### 用 normal 表现表面方向

normal 贴图用 RGB 保存切线空间法线。接近 `(128, 128, 255)` 的中性蓝表示表面朝向观察者；红色与绿色分量的变化表示表面向左右或上下倾斜，蓝色分量表示朝向观察者的程度。

制作时先从舰船轮廓与装甲高度得到一张连续的高度关系，再转换为切线空间 normal。平坦装甲保持接近中性蓝，舰体边缘、凹槽和隆起逐渐改变方向，透明区继续保持透明。将结果保存为：

```text
graphics/normal/ships/GearIn_artizan.png
```

normal 的颜色表示方向，不应直接使用原始舰船颜色。过强的高度梯度会让装甲像被切出尖锐沟槽；后面的 `magnitude` 还能在不重新绘图的情况下整体调整起伏强度。

### 对齐画布、船体与透明区

将三张图保持为相同像素宽高，舰船中心位于相同像素，朝向一致。导出时保留原画布和透明边缘，原始贴图的中心点与武器槽也使用这套坐标。单独裁掉一张图的透明边缘以后，图片看起来更紧凑，光照却会整体偏到另一处。

可以把三张图放入同一图像文档的三个图层组：原始 Sprite 贴图保持只读，material 与 normal 各自编辑；导出前同时检查画布边界、舰首方向和 alpha。运行后如果亮部整体偏离船体，首先比较三张 PNG 的尺寸和不透明像素包围盒，而不是调整 CSV ID。

## 编写 texture CSV

创建 `data/lights/GearIn_texture_data.csv`，写入下面两条纹理映射：

```csv
id,type,frame,magnitude,map,path
GearIn_artizan,ship,,,material,graphics/material/ships/GearIn_artizan.png
GearIn_artizan,ship,,1,normal,graphics/normal/ships/GearIn_artizan.png
```

两行共用 `GearIn_artizan` 与 `ship`，因此都进入这艘舰船的纹理登记；`map` 决定它们分别进入 material 表还是 normal 表。GraphicsLib 用 ID、对象类型和动画帧组成查询键，同一个 ID 可以同时拥有三类 map。

### texture CSV 字段速查表

| 字段        | 当前值               | 读取结果                          | 常见错误                       |
| ----------- | -------------------- | --------------------------------- | ------------------------------ |
| `id`        | `GearIn_artizan`     | 对象查询键                        | 写成装配 ID 后无法匹配船体     |
| `type`      | `ship`               | 选择舰船纹理表                    | 大小写或拼写错误会跳过该行     |
| `frame`     | 空                   | 非动画对象使用第 0 帧             | 舰船填写正数会被丢弃并记录警告 |
| `magnitude` | 空／`1`              | 默认值为 1，normal 行控制起伏强度 | 过大时表面方向显得尖锐         |
| `map`       | `material`／`normal` | 选择三类纹理表之一                | 未使用规定小写值时无法登记     |
| `path`      | `graphics/...`       | 指向实际 PNG                      | 路径存在拼写差异时加载失败     |

`id` 对应对象本身，而不是 CSV 行的任意名称。对于舰船，使用 hull ID；对于武器，使用 weapon ID；对于导弹，使用其 missile hull 或 projectile 关系所使用的 ID。`path` 从 MOD 根目录开始书写，并保留实际文件名大小写。

## 在应用加载时登记 texture CSV

[C18-S5] 的应用入口已经初始化 GraphicsLib，并在能力可用时登记 light CSV。纹理映射使用同一个能力分支，在现有入口中加入 `TextureData.readTextureDataCSV(...)`：

```java
@Override
public void onApplicationLoad() {
    ShaderLib.init();

    if (ShaderLib.areShadersAllowed() && ShaderLib.areBuffersAllowed()) {
        LightData.readLightDataCSV("data/lights/GearIn_light_data.csv");
        TextureData.readTextureDataCSV("data/lights/GearIn_texture_data.csv");
    }
}
```

`TextureData.readTextureDataCSV(...)` 读取全部数据行，把合法的 ID、对象类型、动画帧和 map 组合加入 GraphicsLib 的纹理注册表。能力判断失败时，这份 MOD 纹理表不会在本次应用运行中登记；能力恢复后需要重新启动游戏，让应用加载入口重新执行。

### 选择覆盖策略

默认入口允许后读取的数据替换同一查询键下的旧登记：

```java
TextureData.readTextureDataCSV(
        "data/lights/GearIn_texture_data.csv");
```

只想补充缺失映射时，使用不覆盖入口：

```java
TextureData.readTextureDataCSVNoOverwrite(
        "data/lights/GearIn_texture_data.csv");
```

重复关系由 ID、对象类型、动画帧和 map 共同确定。覆盖入口让后读取行成为查询结果；不覆盖入口保留已经存在的条目，并跳过对应的重复映射。

## 选择对象类型与动画帧

`type` 的值决定 GraphicsLib 把纹理放入哪个对象注册表。CSV 只接受下面列出的小写值：

### 对象类型速查表

| 分组     | `type` 值                                                            | 对应对象                     |
| -------- | -------------------------------------------------------------------- | ---------------------------- |
| 舰船     | `ship`                                                               | 舰船与战机船体               |
| 炮塔主体 | `turret`、`turretbarrel`、`turretunder`                              | 炮塔、炮管与底图             |
| 炮塔盖板 | `turretcoversmall`、`turretcovermedium`、`turretcoverlarge`          | 三种尺寸的炮塔槽盖板         |
| 硬点主体 | `hardpoint`、`hardpointbarrel`、`hardpointunder`                     | 硬点、炮管与底图             |
| 硬点盖板 | `hardpointcoversmall`、`hardpointcovermedium`、`hardpointcoverlarge` | 三种尺寸的硬点槽盖板         |
| 导弹     | `missile`                                                            | 使用舰船式 Sprite 贴图的导弹 |
| 小行星   | `asteroid`                                                           | 战斗中的小行星               |

只有 `turret` 与 `hardpoint` 会读取正数 `frame`。这两类对象可以按动画帧登记不同贴图；其他类型即使填写正数，加载器也会记录不兼容警告并把该帧值丢弃。舰船固定使用第 0 帧，因此示例保持空单元格。

`map` 则只接受 `material`、`normal` 与 `surface`。surface 行使用相同的 ID、type、frame 和 path 关系，例如 `GearIn_artizan,ship,,,surface,graphics/surface/ships/GearIn_artizan.png`；本节不把这行加入最终数据。

## 查询显式纹理条目

`TextureData.readTextureDataCSV(...)` 完成登记后，可以按登记时使用的四部分键查询 material：

```java
TextureEntry material = TextureData.getTextureData(
        "GearIn_artizan",
        TextureData.TextureDataType.MATERIAL_MAP,
        TextureData.ObjectType.SHIP,
        0);
```

第一个参数是 hull ID，第二个参数选择 material 表，第三个参数对应 CSV 的 `ship`。舰船忽略帧参数，这里统一传 `0`；turret 与 hardpoint 才按具体帧号匹配。ID、map 或对象类型不对应，或者该类贴图的加载开关关闭时，查询返回 `null`。

普通查询会在需要时加载纹理，但不会返回标记为 `autoGen` 的 normal。显式登记以及不带该标记的已建立映射，都能按相应键取得。

normal 使用同样的对象键，只替换 map 枚举：

```java
TextureEntry normal = TextureData.getTextureData(
        "GearIn_artizan",
        TextureData.TextureDataType.NORMAL_MAP,
        TextureData.ObjectType.SHIP,
        0);
```

`TextureEntry` 将路径、强度、加载状态和 Sprite 贴图放在同一个对象中。查询成功后，`spriteName` 是 CSV 路径，`magnitude` 是解析后的数值，`sprite` 是当前可用的 `SpriteAPI`；路径不可加载时，普通查询最终返回 `null`。

### `TextureEntry` 状态速查表

下面的字段描述同一条登记从路径到显存的状态：

| 字段            | 含义                       | 直接观察                          |
| --------------- | -------------------------- | --------------------------------- |
| `sprite`        | 已载入的 `SpriteAPI`       | 可交给渲染流程使用                |
| `spriteName`    | 原始或缓存纹理路径         | 核对实际资源来源                  |
| `magnitude`     | CSV 解析出的强度           | normal 示例为 1                   |
| `autoGen`       | 是否属于自动生成 normal    | 显式 normal 为 `false`            |
| `validToLoad`   | 条目是否允许按路径加载纹理 | 允许加载但尚未驻留时仍可为 `true` |
| `loaded`        | 纹理当前是否已经载入       | 动态加载前后会变化                |
| `alwaysPreload` | 对象是否要求常驻预载       | 来自 `graphicslib_preload` 标签   |
| `vramSize`      | 估算显存占用               | 单位为字节                        |

`loaded` 与 `validToLoad` 描述两个不同阶段：前者表示纹理当前驻留，后者表示它可以在需要时载入。使用 `sprite` 之前应以查询结果为准；文件路径错误时，条目无法提供可用 Sprite 贴图。

## 使用自动生成作为替代路径

显式 normal 行提供稳定且由美术人员控制的表面方向。需要兼容尚未制作 normal 的舰船时，把实际舰船对象交给带自动生成能力的查询：

```java
TextureEntry normal = TextureData.getTextureDataWithAutoGen(
        "GearIn_artizan",
        TextureData.TextureDataType.NORMAL_MAP,
        TextureData.ObjectType.SHIP,
        0,
        ship,
        false);
```

`ship` 让 GraphicsLib 能从 `ShipAPI.getHullSpec().getSpriteName()` 找到原始 Sprite 贴图。最后的 `false` 表示返回前要取得可用的 sprite；设置为 `true` 时，方法可以返回尚未载入、`sprite == null` 的条目，因此通常不适合直接使用纹理的代码。

如果相同对象键已经有显式 normal，查询直接返回显式条目。只有 normal 缺失、`ship` 有效且 `autoGenNormals` 已启用时，GraphicsLib 才会尝试生成；`autoGen` 字段能够区分最终来源。

自动生成结果保存在 `Starsector/mods/GraphicsLib/cache`。游戏版本、已启用 MOD 的 ID 与版本列表共同决定缓存是否继续有效，发生变化后再生成。

::: warning 修改 Sprite 贴图不会自动改变缓存依据
只修改 PNG 内容、保持游戏与 MOD 版本信息不变时，现有自动法线缓存仍可能继续使用。测试工匠新贴图时，关闭游戏后移除 `Starsector/mods/GraphicsLib/cache/GearIn_artizan___SHIP_normal.png`，再启动并触发生成，比较新结果。
:::

### 控制自动生成范围

需要主动建立某个 Sprite 贴图的 material、normal 与 surface 映射时，可以把对象键和原始 Sprite 贴图路径交给 `mapSpriteToMNSWithAutoGen(...)`：

```java
TextureData.mapSpriteToMNSWithAutoGen(
        "GearIn_artizan",
        "graphics/ships/GearIn_artizan.png",
        TextureData.ObjectType.SHIP,
        0,
        true,
        false);
```

这组位置参数依次确定映射键 (对象 ID、贴图类别、对象类型与动画帧)；后两个布尔参数则控制是否在缺失时自动生成 normal，以及是否以覆盖模式跳过打上自动生成标记 (常规兼容路径保持为 `false`)。自动查询会识别 `graphicslib_no_autogen` 阻止自动生成，而 `graphicslib_autogen_override` 决定结果标记；已有显式 normal 时始终优先使用。

需要清晰表现指定装甲凹槽与隆起时，手绘或烘焙显式 normal 远比自动生成靠谱。自动生成只依据原始 Sprite 贴图的亮度推算高度，原图上的涂装花纹和暗部阴影也会被当成凹凸变化。

`autoGenNormalMap(...)` 直接从一张 Sprite 贴图生成 normal，并返回生成后的 `SpriteAPI`：

```java
SpriteAPI generated = TextureData.autoGenNormalMap(
        "graphics/ships/GearIn_artizan.png",
        "GearIn_artizan",
        TextureData.ObjectType.SHIP,
        0);
```

这个入口适合已经掌握对象键和缓存规则的专用工具。常规战斗代码使用 `getTextureDataWithAutoGen(...)` 即可；`autoGenMissingNormalMaps()` 只在 GraphicsLib 自己的 `ShaderModPlugin.onApplicationLoad()` 末尾扫一遍已加载表，由于各 MOD 的 `onApplicationLoad()` 执行顺位不固定，测试时删掉某船显式 normal 后，稳定触发点是在战斗中经由 `LightShader` -> `getTextureDataWithAutoGen(..., ship, false)` 按需生成并写盘。

## 理解缓存、按需加载与预载

GraphicsLib 默认按当前战斗需要加载舰船、武器和导弹纹理，并卸载已经不再需要的条目。遗漏的登记会在首次查询时按需加载，因此 `validToLoad == true` 与 `loaded == false` 可以同时出现；完成加载后，`loaded` 和 `sprite` 才反映当前可用状态。

设置 `preloadAllMaps = true` 会在预载阶段加载全部 material、normal 与 surface 贴图。它能减少首次使用时的加载停顿，也会提前占用全部贴图的显存。对象标签 `graphicslib_preload` 只让对应对象的贴图常驻预载，适合少量关键舰船或武器。

需要在战斗脚本中等待预载完成时，读取战斗引擎的 custom data：

```java
if (engine.getCustomData().containsKey(ShaderHook.TEXTURE_PRELOAD_KEY)) {
    TextureEntry normal = TextureData.getTextureDataWithAutoGen(
            "GearIn_artizan",
            TextureData.TextureDataType.NORMAL_MAP,
            TextureData.ObjectType.SHIP,
            0,
            ship,
            false);
}
```

`ShaderHook.TEXTURE_PRELOAD_KEY` 与 `TextureData.ALWAYS_PRELOAD_TAG` 的字符串值都是 `graphicslib_preload`，前者是战斗状态键，后者是对象标签。两个值存在于不同容器，读取时要分别使用 `engine.getCustomData()` 与对象 spec 的标签接口。

## 常用字段与方法速查表

| 入口                             | 关键参数                                              | 返回或结果                                |
| -------------------------------- | ----------------------------------------------------- | ----------------------------------------- |
| `getTextureData(...)`            | ID、map、对象类型、帧                                 | 查询显式登记并按需加载，缺失时返回 `null` |
| `getTextureDataWithAutoGen(...)` | 显式查询参数、实际对象、`unloadedOk`                  | 优先显式条目，必要时生成 normal           |
| `mapSpriteToMNSWithAutoGen(...)` | 对象键、Sprite 贴图路径、类型、帧、生成开关、覆盖开关 | 建立三类纹理映射并处理缺失 normal         |
| `autoGenNormalMap(...)`          | Sprite 贴图路径、对象键、类型、帧                     | 返回自动生成的 normal Sprite 贴图         |
| `autoGenMissingNormalMaps()`     | 无                                                    | 由 GraphicsLib 初始化流程扫描缺失 normal  |
| `TextureEntry.spriteName`        | —                                                     | 显式路径或缓存路径                        |
| `TextureEntry.magnitude`         | —                                                     | normal 起伏强度，默认 1                   |
| `TextureEntry.loaded`            | —                                                     | 当前是否已经载入                          |
| `TextureEntry.vramSize`          | —                                                     | 估算显存占用，单位为字节                  |

## 完整最小例

应用加载入口同时保留 [C18-S5] 的 light 登记，并加入本节的 texture 登记：

```java
@Override
public void onApplicationLoad() {
    ShaderLib.init();

    if (ShaderLib.areShadersAllowed() && ShaderLib.areBuffersAllowed()) {
        LightData.readLightDataCSV("data/lights/GearIn_light_data.csv");
        TextureData.readTextureDataCSV("data/lights/GearIn_texture_data.csv");
    }
}
```

最终目录保留原始 Sprite 贴图，并为 GraphicsLib 增加两张坐标完全一致的贴图：

```text
graphics/ships/GearIn_artizan.png
graphics/material/ships/GearIn_artizan.png
graphics/normal/ships/GearIn_artizan.png
data/lights/GearIn_texture_data.csv
```

`GearIn_texture_data.csv` 使用以下完整内容：

```csv
id,type,frame,magnitude,map,path
GearIn_artizan,ship,,,material,graphics/material/ships/GearIn_artizan.png
GearIn_artizan,ship,,1,normal,graphics/normal/ships/GearIn_artizan.png
```

### 进游戏核对

准备对齐画布导出的 `GearIn_artizan` material 与 normal PNG，确认 `[C18-S5]` 已初始化 GraphicsLib，进入含动态光源的模拟战：

- 启动阶段正常读入 `GearIn_texture_data.csv` 且 `starsector.log` 无路径或字段警告，material 与 normal 查询命中 `GearIn_artizan` (`SHIP`、帧 `0`、`magnitude == 1`)；
- 在 [C18-S6] 的动态点光源下旋转工匠级，受光颜色随 material 变化，装甲明暗斜率随船体朝向同步旋转，透明区无溢出像素；
- 临时移除显式 normal 行并在开启 `autoGenNormals` 时让该船上场受光，可在 `Starsector/mods/GraphicsLib/cache` 看到 `GearIn_artizan___SHIP_normal.png` 且 `autoGen == true`；再对照 `graphicslib_no_autogen` / `graphicslib_preload` 与 `preloadAllMaps` 观察按需/预载显存差异。

## 常见问题

- **查询结果为 `null`**：ID、对象类型、map、适用的动画帧、加载开关或资源路径没有对应到可用条目。
    - **解决方案**：核对登记与加载开关；turret 和 hardpoint 另核对帧号，并确认 PNG 能够加载。

- **贴图整体偏离舰船**：material 或 normal 的画布、中心、透明边界或朝向与原始 Sprite 贴图不同。
    - **解决方案**：在同一画布中重新对齐三张图后导出。

- **表面凹凸方向相反或过于尖锐**：normal 通道方向错误，或 `magnitude` 过大。
    - **解决方案**：修正法线通道，并从 `magnitude = 1` 开始校准。

- **缺失 normal 后没有自动生成**：全局开关、对象标签、实际对象参数或原始 Sprite 贴图路径阻止了生成。
    - **解决方案**：确认 `autoGenNormals` 已启用、对象未带禁止标签，并让舰船进入战斗渲染或把实际 `ShipAPI` 传入查询。

- **第一次进入战斗出现明显停顿**：大量纹理在首次使用时同时按需载入。
    - **解决方案**：只给关键对象添加 `graphicslib_preload`，再以显存统计决定是否启用 `preloadAllMaps`。

## 建议参考项

- `org.dark.shaders.util.TextureData`：CSV 登记、纹理查询、自动生成与加载策略；
- `org.dark.shaders.util.TextureEntry`：纹理路径、强度、来源和驻留状态；
- `org.dark.shaders.util.ShaderHook`：战斗纹理预载完成状态；
- `Starsector/mods/GraphicsLib/data/lights/core_texture_data.csv`：对象类型与字段格式；
- `Starsector/mods/GraphicsLib/GRAPHICS_OPTIONS.ini`：自动生成和预载配置。
