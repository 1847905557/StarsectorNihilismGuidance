# 注册 GraphicsLib 光照数据

要让武器在开火、飞行和命中时稳定带出光晕，其实不必每次发弹都手写插件。GraphicsLib 在 `ShaderLib.init()` 建好显存环境之后，`LightData` 会直接读入弹体与光束两张 CSV，连带周期渐变和概率开关一起接管。

拿前面做好的都林与乌里弹体做示例，一并核对字段作用域，以及自定义表和原版记录遇到重名 ID 时谁覆盖谁。

---

_本节预期阅读时间：\~16 分钟。_

_本节预期操作时间：\~20 分钟。_

## 前置知识

- [C1-S5] 的 `GearIn_durin_shot` 弹体与 `GearIn_uri` 光束；
- [C1-S7] 的 CSV 逗号字段双引号转义规则；
- [C7-S1] 的 `GearInModPlugin.onApplicationLoad()`；
- [C18-S1] 的 GraphicsLib manifest 依赖与编译 classpath。

## 创建光照数据表

先在 MOD 内创建 `data/lights/GearIn_light_data.csv`，写入完整成品中的表头与两条记录。都林用弹体 ID `GearIn_durin_shot`，乌里用光束武器 ID `GearIn_uri`，类型分别填写 `projectile` 和 `beam`。RGB 数组中的英文逗号仍按 [C1-S7] 处理，用双引号包住整个字段。

文件准备好后，将它接到应用加载入口。下面按这两条记录分别说明三组光源。

## 接到应用加载入口

在既有 `GearInModPlugin.onApplicationLoad() throws Exception` 中追加下面的局部代码，保留此前资源读取和入口日志，并添加 `ShaderLib`、`LightData` 的 import。先用 `ShaderLib.init()` 建立库的运行环境，再检查 shader 与 buffer 能力；两者都可用时，读取自己的光照表：

```java
@Override
public void onApplicationLoad() throws Exception {
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

当尺寸和强度均大于 0 且 `color` 非空时，加载器就会**先把 `hasStandard` 置为 `true`**，随后再去解析 `color` 数组并保存 `standardSize`、`standardIntensity`、`standardFadeout`、`standardColor` 和 `standardOffset`。

**注意非空颜色字符串一定要格式合法**：只要 `id` 与 `type` 合法，`LightEntry` 在解析各组之前就已经放进了注册表；若 `color` 非空却格式有误，解析抛出异常会记录错误并直接**中止当前 CSV 后续行的加载**，且出错那一行还会带着 `hasStandard == true` 与默认黑色留在表里。查询代码仍应先判 `LightEntry` 是否为 `null`，再读取 `hasStandard`。

## 配置 hit 光源

命中组紧跟 standard 组之后：

```csv
hit size,hit intensity,hit fadeout,hit color
450,0.6,1.25,"[255,100,100]"
100,0.35,,"[135,255,180]"
```

弹体命中时，这组参数提供一次随后淡出的光：`hit size`、`hit intensity`、`hit fadeout` 和 `hit color` 分别决定范围、亮度、淡出时间与颜色。光束的 hit 组则跟随目标端，在 beam 持续期间留在命中位置，所以示例把它的 `hit fadeout` 留空。

同样，尺寸和强度大于 0 且 `hit color` 非空时就会先置 `hasHit = true` 并解析颜色，若仅缺少这些关键值之一则会跳过该组 (`hasHit == false`)：

```java
boolean hasHitLight = uri != null && uri.hasHit;
```

`hasHit == false` 表示该 ID 没有启用命中组，命中位置不会使用自定义 hit 光，但不影响 standard 光源。

## 配置 flash 光源

flash 组描述 projectile 的开火闪光和 beam 的源端光：

```csv
flash size,flash intensity,flash fadeout,flash color,flash offset
80,0.45,0.4,"[255,200,200]",
60,0.35,,"[200,255,225]",
```

尺寸和强度大于 0 且 `flash color` 非空时置 `hasFlash = true` 并解析颜色。projectile 的 flash 按 `flash fadeout` 消退，beam 的 flash 则留在光束源端，随 beam 保持，示例将淡出字段留空。

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

`chance` 的默认值为 `1`。写成 `1` 时，每次满足生成条件都允许创建光效；写成 `0.5` 时，概率检查约有一半通过。对 projectile 而言，基础光与开火闪光分别掷骰判断 `chance`；但到了命中产生 hit 光时，**只要该弹体仍挂着未移除的关联基础光，就会直接绕过 `chance` 概率检查生成 hit 光** (非导弹还会按基础光与设定的强度比缩放 hit 光并移除基础光)，没有关联基础光时才单独判定 `chance`。beam 的三组光则共用一次概率结果，一起出现。

`fighter dim` 默认值为 `true`，战机发射的光效会把尺寸和强度乘以 GraphicsLib 的 `fighterBrightnessScale`；系数小于 1 时减弱，等于 1 时保持原值 (同时战机发射的 projectile 基础光会忽略 `offset`，直接使用零偏移)。普通舰船继续使用原始参数。

光照参数讲究贴合武器手感而非单纯堆高亮度：都林的开火光短促、乌里的源端光持续存在，本来就各自匹配不同范围与色温；若每次齐射都把周围舰船照得白成一片，实战可读性反倒会丢。

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
public void onApplicationLoad() throws Exception {
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

### 进游戏核对

执行 `gradle jar`，重启游戏进入模拟战 (`GearIn_durin_shot` 与 `GearIn_uri` 已随武器加载)：

- 启用 GraphicsLib shader 与 buffer 后，按 `LightDataType.PROJECTILE` / `BEAM` 查询两项 ID 均命中且 `hasStandard`/`hasHit`/`hasFlash` 符合 CSV；关闭 shader 或 buffer 后能安全跳过读取并正常进战斗；
- 发射都林弹体与乌里光束，确认弹体的红色飞行光、开火闪光、命中光与光束的青绿色源端/目标端光按设定持续时间与位置显示；
- 把某条 `chance` 调为 `0.5` 或用战机武器对照 `fighter dim` 的 `TRUE` / `FALSE` 缩放变化，再用同 ID 先后调用覆盖与不覆盖入口核对替换行为。

## 常见问题

- **CSV 已放入 MOD，但 projectile 查询返回 `null`**：入口没有执行，或 `type` 不是精确的 `projectile` (或前面某行颜色数组格式非法导致整张表后续行中止加载)。
    - **解决方案**：确认 `ShaderLib.init()` 先执行且能力条件成立，核对 CSV 颜色格式并检查 `starsector.log` 是否有加载报错，查询时使用 `LightDataType.PROJECTILE`。

- **standard 光存在，但命中位置没有额外光效**：hit 组缺少有效尺寸、强度或颜色。
    - **解决方案**：补齐 `hit size`、`hit intensity`、`hit color`，再确认 `hasHit` 为 `true`。

- **重复加载后仍显示旧参数**：使用了 `readLightDataCSVNoOverwrite(...)`，已有 ID 被保留。
    - **解决方案**：需要替换时改用 `readLightDataCSV(...)`。

## 建议参考项

- `org.dark.shaders.util.ShaderLib`：初始化 GraphicsLib 并读取 shader／buffer 能力；
- `org.dark.shaders.light.LightData`：读取 CSV、选择覆盖策略和查询 projectile／beam 条目；
- `org.dark.shaders.light.LightEntry`：保存 standard、hit、flash 三组解析结果；
- `Starsector/mods/GraphicsLib/data/lights/core_light_data.csv`：原版武器光效配置格式参考；
- `Starsector/mods/GraphicsLib/GRAPHICS_OPTIONS.ini`：`enableShaders` 与 `fighterBrightnessScale` 等运行时开关。
