# 注册 GraphicsLib 光照数据

GraphicsLib 可以直接从 CSV 读取武器光照，无需为每一次开火单独创建光源。这里以都林弹体和乌里光束为例，配置飞行、开火与命中光效；弹体与光束虽然使用相同的字段，实际显示方式却有所不同。

*本节预期阅读时间：\~16 分钟。*

*本节预期操作时间：\~25 分钟。*

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

## 创建光照数据表

先在 MOD 内创建 `data/lights/GearIn_light_data.csv`，写入完整成品中的表头与两条记录。都林用弹体 ID `GearIn_durin_shot`，乌里用光束武器 ID `GearIn_uri`，类型分别填写 `projectile` 和 `beam`。RGB 数组中的英文逗号仍按 [C1-S7] 处理，用双引号包住整个字段。

文件准备好后，将它接到应用加载入口。下面按这两条记录分别说明三组光源。

## 接到应用加载入口

在既有 `GearInModPlugin.onApplicationLoad(...)` 中追加下面的局部代码，保留此前资源读取和入口日志，并添加 `ShaderLib`、`LightData` 的 import。先用 `ShaderLib.init()` 建立库的运行环境，再检查 shader 与 buffer 能力；两者都可用时，读取自己的光照表：

```java
@Override
public void onApplicationLoad() {
    ShaderLib.init();

    if (ShaderLib.areShadersAllowed() && ShaderLib.areBuffersAllowed()) {
        LightData.readLightDataCSV("data/lights/GearIn_light_data.csv");
    }
}
```

`areShadersAllowed()` 和 `areBuffersAllowed()` 读取 GraphicsLib 当前能力开关，任一返回 `false`，本次运行就跳过光照表读取，战斗仍可启动。CSV 读取把条目保存到静态表，战斗中的事件再由 GraphicsLib 的运行环境处理；`onApplicationLoad()` 在这里负责建立登记。

## 让 ID 与查询类型对应

`LightData.readLightDataCSV(...)` 按 `type` 把两行分别放进 projectile 表和 beam 表，类型值使用这里的小写写法。接着用相同类型查询，[C1-S5] 的两个 ID 便分别对应各自的记录：

```java
LightEntry durin = LightData.getLightData(
        "GearIn_durin_shot", LightData.LightDataType.PROJECTILE);
LightEntry uri = LightData.getLightData(
        "GearIn_uri", LightData.LightDataType.BEAM);
```

**查询时，ID 和类型要一起对应。** `LightEntry` 保存解析后的数值和颜色，并用 `hasStandard`、`hasHit`、`hasFlash` 标记三组数据是否成立。

乌里的 beam 行即使已经正确读取，按 `PROJECTILE` 查询 `GearIn_uri` 仍会得到 `null`。

## 配置 standard 光源

下面两行数据共用的前七列描述基础光源：

```csv
id,type,size,intensity,fadeout,color,offset
GearIn_durin_shot,projectile,125,0.5,0.75,"[255,70,70]",10
GearIn_uri,beam,45,0.65,,"[135,175,135]",
```

基础光的范围由 `size` 决定，亮度由 `intensity` 决定；`color` 使用 RGB 数组，`offset` 指定创建时的相对偏移。projectile 的 `fadeout` 决定弹体消失后的基础光消退时间。beam 光则跟随 beam 的生命周期，示例将这项留空。

尺寸和强度都大于 0，颜色非空并可解析时，`hasStandard` 为 `true`。解析值分别保存在 `standardSize`、`standardIntensity`、`standardFadeout`、`standardColor` 和 `standardOffset`。

如果其中一个建立 standard 光源所需的关键值缺失，该组不会被标记为可用。查询代码应先判断 `LightEntry` 是否为 `null`，再读取 `hasStandard`。

## 配置 hit 光源

命中组紧跟 standard 组之后：

```csv
hit size,hit intensity,hit fadeout,hit color
450,0.6,1.25,"[255,100,100]"
100,0.35,,"[135,255,180]"
```

弹体命中时，这组参数提供一次随后淡出的光：`hit size`、`hit intensity`、`hit fadeout` 和 `hit color` 分别决定范围、亮度、淡出时间与颜色。光束的 hit 组则跟随目标端，在 beam 持续期间留在命中位置，所以示例把它的 `hit fadeout` 留空。

同样，尺寸和强度大于 0、颜色非空并可解析时，`hasHit` 才为 `true`。查询可以先确认这组数据是否成立：

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

尺寸和强度大于 0、颜色非空并可解析时，`hasFlash` 为 `true`。projectile 的 flash 按 `flash fadeout` 消退，beam 的 flash 则留在光束源端，随 beam 保持，示例将淡出字段留空。

**flash 的参数独立于 standard，不会覆盖基础光。** 尺寸、亮度、颜色与偏移分别读取 flash 组的字段，下面通过标记确认开火光是否完整：

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

`chance` 的默认值为 `1`。写成 `1` 时，每次满足生成条件都允许创建光效；写成 `0.5` 时，概率检查约有一半通过。projectile 的基础光与开火闪光分别检查概率；命中时若仍有关联基础光，也可以继续生成 hit 光。beam 的三组光共用一次概率结果，一起出现。

`fighter dim` 默认值为 `true`，战机发射的光效会把尺寸和强度乘以 GraphicsLib 的 `fighterBrightnessScale`；系数小于 1 时减弱，等于 1 时保持原值。普通舰船继续使用原始参数。

因此，降低 `chance` 后，各组 projectile 光源的实际出现频率可能不同。`fighter dim` 决定战机来源采用的尺寸与强度，记录仍属于原来的 projectile 或 beam 类型。

::: details 编者对光照强度的取舍

**编者更看重光照能否表现武器的特点。** 都林的开火光短促，乌里的源端光持续存在，本来就适合不同的范围、颜色和时长。强烈与克制都可以成为一种风格；不过，若每次开火都把附近的舰船照得难以辨认，玩家记住的恐怕也只剩那片亮光了。

:::

## 选择覆盖或保留原登记

需要替换已有 ID 时，使用覆盖读取：

```java
LightData.readLightDataCSV("data/lights/GearIn_light_data.csv");
```

同一 `id` 和 `type` 已有记录时，后读取的条目会替换它。若这次只补充尚未登记的对象，改用不覆盖入口：

```java
LightData.readLightDataCSVNoOverwrite(
        "data/lights/GearIn_light_data.csv");
```

不覆盖入口遇到对应类型中的相同 ID，就跳过这一行。两个入口都分别处理 projectile 与 beam，ID 相同而类型不同时，仍是两条登记。

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

1. 保存光照表与应用调用，构建 JAR 并重启，检查初始化和 light CSV 的日志。
2. 在 GraphicsLib 配置中关闭 shader，重启并确认本节能力分支跳过读取；恢复开关后重启，再完成后面的查询。buffer 不可用时，同一分支也会跳过。
3. 开启两项能力，按 `PROJECTILE` 查询 `GearIn_durin_shot`，确认得到 standard、hit 和 flash 条目。
4. 按 `BEAM` 查询 `GearIn_uri`，确认类型匹配且 `hasStandard`、`hasHit`、`hasFlash` 均符合数据行。
5. 发射都林自动炮，观察红色基础光、闪光和命中光的尺寸与淡出时间。
6. 开火乌里光束，观察青绿色基础光、源端光和目标端光的参数差异。
7. 将一条记录的 `chance` 改为 `0.5`，重复开火并观察光效出现频率变化。
8. 将 GraphicsLib 的 `fighterBrightnessScale` 暂设为 `0.5`，用战机发射 projectile，比较 `fighter dim` 为 `TRUE` 和 `FALSE` 时的尺寸与亮度，再恢复原系数。
9. 用相同 ID 先后调用覆盖和不覆盖入口，查询 `LightEntry` 并比较最终字段。

预期结果：

- 能力判断成功时，light CSV 完成登记；
- projectile 与 beam 的查询分别命中各自 `LightDataType`；
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

两条记录经应用加载入口登记后，都林弹体与乌里光束便有了各自的光照参数。ID 与类型共同确定查询的条目，standard、hit、flash 则分别决定基础光、命中或目标端光，以及开火闪光或源端光。CSV 字段相同，光源的显示位置与持续方式仍取决于实际对象。

## 建议参考项

- `org.dark.shaders.util.ShaderLib`：初始化 GraphicsLib 并读取 shader／buffer 能力；
- `org.dark.shaders.light.LightData`：读取 CSV、选择覆盖策略和查询 projectile／beam 条目；
- `org.dark.shaders.light.LightEntry`：保存 standard、hit、flash 三组解析结果。
