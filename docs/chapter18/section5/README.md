# 注册 GraphicsLib 光照数据

GraphicsLib 可以根据 projectile 与 beam ID 自动生成飞行光、命中光和开火闪光。应用加载时先初始化 shader，再确认 shader 与 buffer 能力，最后读取 light CSV；战斗中的弹体与光束才能按 ID 找到对应光照参数。本节使用已经制作好的 `GearIn_durin_shot` 和 `GearIn_uri`，完整解释 light CSV 的登记入口、字段关系与覆盖策略。

*本节预期阅读时间：~16 分钟。*

*本节预期操作时间：~25 分钟。*

## 学习目标

完成后，您将能够：

- 在 `onApplicationLoad()` 中按正确顺序初始化 GraphicsLib；
- 用 shader 与 buffer 能力判断决定是否读取 light CSV；
- 区分 projectile 与 beam 的 `LightEntry` 注册表；
- 解释 standard、hit、flash 三组光照字段的生成条件；
- 使用覆盖或不覆盖入口处理已有 ID。

## 前置知识

- [C18-S1] 已完成 GraphicsLib 的 manifest、Gradle 和运行时依赖接入；
- [C1-S5] 已制作 `GearIn_durin_shot` 与 `GearIn_uri`；
- 了解 `GearInModPlugin` 的 `onApplicationLoad()` 入口和 CSV 文件路径；
- 能够读取 `starsector.log` 中的加载错误。

## 认识 GraphicsLib 的初始化顺序

GraphicsLib 的光照数据依赖渲染能力。`ShaderLib.init()` 负责建立库的运行环境，能力判断随后读取当前配置；只有两项能力都可用时，使用方才登记自己的光照数据。把 CSV 读取提前到初始化之前，得到的不是 "稍后自动补读"，而是本次应用加载没有完成登记。

## 在 `onApplicationLoad()` 中初始化并判断能力

将应用加载入口写成下面的局部片段：

```java
@Override
public void onApplicationLoad() {
    ShaderLib.init();

    if (ShaderLib.areShadersAllowed() && ShaderLib.areBuffersAllowed()) {
        LightData.readLightDataCSV("data/lights/GearIn_light_data.csv");
    }
}
```

`areShadersAllowed()` 和 `areBuffersAllowed()` 读取的是 GraphicsLib 当前能力开关。任一返回 `false` 时，读取调用不会执行，战斗仍可启动，但本 MOD 的光照登记不会进入本次运行。`onApplicationLoad()` 只负责建立登记表，战斗事件如何使用登记结果由 GraphicsLib 自己处理。

## 登记 light CSV

`LightData.readLightDataCSV(...)` 读取 `data/lights/GearIn_light_data.csv` 后，把 `projectile` 行放入 projectile 表，把 `beam` 行放入 beam 表。`type` 必须逐字写成这两个值，其他拼写不会建立可查询条目。

## 认识 `LightData` 的 projectile／beam 分类

前置章节的两个 ID 分属不同表：

```java
LightEntry durin = LightData.getLightData(
        "GearIn_durin_shot", LightData.LightDataType.PROJECTILE);
LightEntry uri = LightData.getLightData(
        "GearIn_uri", LightData.LightDataType.BEAM);
```

查询时 ID 和类型必须同时匹配。把 `GearIn_uri` 按 `PROJECTILE` 查询会得到 `null`，即使 beam 行已经被正确读取。`LightEntry` 保存 CSV 解析后的数值和颜色，并以 `hasStandard`、`hasHit`、`hasFlash` 标记三组数据是否成立。

## 配置 standard 光源

下面两行数据共用的前七列描述基础光源：

```csv
id,type,size,intensity,fadeout,color,offset
GearIn_durin_shot,projectile,125,0.5,0.75,"[255,70,70]",10
GearIn_uri,beam,45,0.65,,"[135,175,135]",
```

`size` 控制光源范围，`intensity` 控制亮度，`color` 是 RGB 数组，`offset` 是 GraphicsLib 创建光源时使用的相对偏移。projectile 的 `fadeout` 控制基础光源在弹体消失后的消退时间；beam 光源跟随 beam 生命周期，示例因此把该字段留空。尺寸、强度和颜色都有效时，`hasStandard` 为 `true`，对应值分别出现在 `standardSize`、`standardIntensity`、`standardFadeout`、`standardColor` 和 `standardOffset`。

如果其中一个建立 standard 光源所需的关键值缺失，该组不会被标记为可用。查询代码应先判断 `LightEntry` 是否为 `null`，再读取 `hasStandard`。

## 配置 hit 光源

命中组紧跟 standard 组之后：

```csv
hit size,hit intensity,hit fadeout,hit color
450,0.6,1.25,"[255,100,100]"
100,0.35,,"[135,255,180]"
```

projectile 命中目标时，GraphicsLib 根据 `hit size`、`hit intensity`、`hit fadeout` 和 `hit color` 创建命中后淡出的光。beam 则把 hit 组作为目标端光，并在光束持续期间跟随命中位置；示例因此把 beam 的 `hit fadeout` 留空。尺寸、强度和颜色都有效时，`hasHit` 为 `true`。

```java
boolean hasHitLight = uri != null && uri.hasHit;
```

`hasHit == false` 表示该 ID 没有完整命中组，命中位置不会使用自定义 hit 光，但不影响 standard 光源。

## 配置 flash 光源

flash 组描述 projectile 的开火闪光和 beam 的源端光：

```csv
flash size,flash intensity,flash fadeout,flash color,flash offset
80,0.45,0.4,"[255,200,200]",
60,0.35,,"[200,255,225]",
```

只要尺寸、强度和颜色满足解析条件，`hasFlash` 就会为 `true`。projectile 的 flash 按 `flash fadeout` 消退；beam 的 flash 绑定在光束源端并随 beam 保持，示例将淡出字段留空。flash 使用自己的尺寸、亮度、颜色和偏移，不会覆盖 standard 组。

```java
boolean hasFlashLight = durin != null && durin.hasFlash;
```

## 使用 `chance` 与 `fighter dim`

完整数据行的最后两组字段控制生成概率和战机亮度：

```csv
chance,fighter dim
1,TRUE
1,TRUE
```

`chance` 的默认值为 `1`。写成 `1` 时，每次满足生成条件都允许创建光效；写成 `0.5` 时，约一半事件会生成。`fighter dim` 默认值为 `true`，战机发射的光效会按 GraphicsLib 的战机系数缩小尺寸和强度；普通舰船使用原始尺寸和强度。

这些字段不会改变 `LightEntry` 的分类。它们只参与 GraphicsLib 在战斗事件中决定是否生成和使用多亮的过程。

## 选择覆盖或保留原登记

需要替换已有 ID 时，使用覆盖读取：

```java
LightData.readLightDataCSV("data/lights/GearIn_light_data.csv");
```

同一 `id` 和 `type` 已存在时，后读取的条目会替换先前条目。只想补充没有登记过的 ID 时，改用不覆盖读取：

```java
LightData.readLightDataCSVNoOverwrite(
        "data/lights/GearIn_light_data.csv");
```

不覆盖入口发现对应类型已经存在相同 ID 时跳过该行。两个入口都把 projectile 和 beam 分开处理；ID 相同但类型不同，仍然属于两条不同登记。

## 完整最小例

下面的入口和 CSV 共同组成光照数据登记结果。

```java
@Override
public void onApplicationLoad() {
    ShaderLib.init();

    if (ShaderLib.areShadersAllowed() && ShaderLib.areBuffersAllowed()) {
        LightData.readLightDataCSV("data/lights/GearIn_light_data.csv");
    }
}
```

```csv
id,type,size,intensity,fadeout,color,offset,hit size,hit intensity,hit fadeout,hit color,flash size,flash intensity,flash fadeout,flash color,flash offset,chance,fighter dim
GearIn_durin_shot,projectile,125,0.5,0.75,"[255,70,70]",10,450,0.6,1.25,"[255,100,100]",80,0.45,0.4,"[255,200,200]",,1,TRUE
GearIn_uri,beam,45,0.65,,"[135,175,135]",,100,0.35,,"[135,255,180]",60,0.35,,"[200,255,225]",,1,TRUE
```

## 验证方案

前置数据：GraphicsLib 已安装并启用，`GearIn_durin_shot` 和 `GearIn_uri` 已按前置章节加入武器数据，`onApplicationLoad()` 已挂载到 MOD plugin。

1. 启动游戏并检查 `ShaderLib.init()` 所在应用加载入口没有异常。
2. 关闭 shader 或 buffer 能力之一，确认 light CSV 不进入有效登记。
3. 开启两项能力，按 `PROJECTILE` 查询 `GearIn_durin_shot`，确认得到 standard、hit 和 flash 条目。
4. 按 `BEAM` 查询 `GearIn_uri`，确认类型匹配且 `hasStandard`、`hasHit`、`hasFlash` 均符合数据行。
5. 发射都林自动炮，观察红色基础光、闪光和命中光的尺寸与淡出时间。
6. 开火乌里光束，观察青绿色基础光、源端光和目标端光的参数差异。
7. 将一条记录的 `chance` 改为 `0.5`，重复开火并观察光效出现频率变化。
8. 用战机发射 projectile，比较 `fighter dim` 为 `TRUE` 和 `FALSE` 时的尺寸与亮度。
9. 用相同 ID 先后调用覆盖和不覆盖入口，查询 `LightEntry` 并比较最终字段。

预期结果：

- 能力判断成功时，light CSV 完成登记；
- projectile 与 beam 必须使用各自的 `LightDataType` 查询；
- standard、hit 和 flash 分别提供基础光、命中或目标端光，以及开火闪光或源端光；
- `chance` 改变生成频率，`fighter dim` 只影响战机来源光效的尺寸与亮度；
- 覆盖入口替换已有 ID，不覆盖入口保留先前条目。

## 故障分析

- **CSV 已放入 MOD，但 projectile 查询返回 `null`**：入口没有执行，或 `type` 不是精确的 `projectile`。

    - **解决方案**：确认 `ShaderLib.init()` 先执行，能力条件成立，并使用 `LightDataType.PROJECTILE` 查询。

- **standard 光存在，但命中位置没有额外光效**：hit 组缺少有效尺寸、强度或颜色。

    - **解决方案**：补齐 `hit size`、`hit intensity`、`hit color`，再确认 `hasHit` 为 `true`。

- **重复加载后仍显示旧参数**：使用了 `readLightDataCSVNoOverwrite(...)`，已有 ID 被保留。

    - **解决方案**：需要替换时改用 `readLightDataCSV(...)`。

## 总结

GraphicsLib 光照数据的关键是加载顺序和注册表职责。先初始化并确认能力，再读取 light CSV；数据表把 projectile／beam 登记到对应光照表中，standard、hit、flash 分别承担基础、命中和短暂开火表现，`chance` 与 `fighter dim` 调整生成概率和战机亮度。

## 建议参考项

- `org.dark.shaders.util.ShaderLib`：初始化 GraphicsLib 并读取 shader／buffer 能力；
- `org.dark.shaders.light.LightData`：读取 CSV、选择覆盖策略和查询 projectile／beam 条目；
- `org.dark.shaders.light.LightEntry`：保存 standard、hit、flash 三组解析结果。
