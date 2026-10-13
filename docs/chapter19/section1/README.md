# 接入可选依赖

Nexerelin 和 LunaLib 可以增强 MOD，也可以由玩家选择启用。我们先为现有工程加入编译依赖，再用一个外层桥接类判断运行时状态。库缺席时读回明确的默认值，启用时才进入适配器；同一份 JAR 分别在四种组合下启动，类加载和功能接入都能实际比较。

*本节预期阅读时间：\~18 分钟。*

*本节预期操作时间：\~30 分钟。*

## 学习目标

完成后，您将能够：

- 用精确 MOD ID 分别检测 Nexerelin 与 LunaLib；
- 让基础入口加载不含第三方类型的外层桥接类；
- 在检测通过后进入持有 LunaLib 类型的嵌套适配器；
- 为缺库和缺键定义明确的默认返回值；
- 让每项缺库日志在一次应用运行中最多出现 1 次。

## 前置知识

- [C7-S1] 的应用与生涯加载回调；
- [C7-S2] 的 Global 服务与日志；
- [C18-S1] 的运行时依赖、编译 classpath 和 JAR 输出。

## 先让开发工程找到两库

开发环境安装 Nexerelin 与 LunaLib，按实际目录名把 JAR 加入 [C18-S1] 的 `dependencyJarDirs`。下面的局部代码放在该数组定义后、`dependencies` 块之前：

```groovy
dependencyJarDirs += [
    "${modsDir}/Nexerelin/jars",
    "${modsDir}/Lunalib/jars",
]
```

继续使用 `compileOnly`，保存后运行 `gradle printCompileClasspath`，确认包含 `ExerelinCore.jar` 与 `LunaLib.jar`。**编译期能找到库，与玩家运行时启用了库，是两个条件。**本节按可选接入安排，运行时启用状态由 Java 查询，前文章节的三库硬依赖继续保留。

## 保存桥接类，再调用它

把本节完整的 `GearInCompatBridge` 保存到 `src/data/scripts/gearin/modcompat/GearInCompatBridge.java`。外层查询使用原版 `ModManagerAPI`：

```java
boolean nex = Global.getSettings().getModManager().isModEnabled("nexerelin");
boolean luna = Global.getSettings().getModManager().isModEnabled("lunalib");
```

这两个 ID 来自依赖 MOD 的 `mod_info.json`。设置拥有者继续使用我们的 MOD ID `gear_in`，三者各有用途。

向既有 `GearInModPlugin.onGameLoad(...)` 追加以下局部调用，并导入桥接类，保留此前生涯初始化：

```java
boolean nex = GearInCompatBridge.isNexerelinEnabled();
boolean luna = GearInCompatBridge.isLunaLibEnabled();
boolean fullGraphic = GearInCompatBridge.getFullGraphicEffect(true);
GearInCompatBridge.reportNexerelinFallback();
Global.getLogger(GearInModPlugin.class).info(
        "GearIn optional: nex=" + nex + " luna=" + luna + " fullGraphic=" + fullGraphic);
```

构建重启、载入生涯后搜索 `GearIn optional`。这次先观察布尔值，后面的 Nexerelin 功能在启用分支接入。图形开关的 `true` 是调用方选择的缺省，库缺席时仍返回它；具体效果由使用这个值的代码决定。

## 把库类型留在启用分支后面

**外层负责检测，适配器负责直接调用库。**桥接类的公共签名和字段使用原版或 Java 类型，直接引用 LunaLib 的代码放在独立嵌套类中。局部调用为：

```java
if (isLunaLibEnabled()) {
    return LunaAdapter.getFullGraphicEffect(fallback);
}
```

分支通过后，才调用 `LunaAdapter`。LunaLib 缺席时返回默认值，基础入口可以继续完成自己的工作。

只在基础类中加一个启用判断，还需要看第三方类型放在哪里。父类、接口、字段和方法签名会参与类加载；把这些关系留在适配角色，基础调用方才能保留自己的加载边界。

## 定义字段与缺省值

在 MOD 的 `data/config/LunaSettings.csv` 保存：

```csv
fieldID,fieldName,fieldType,defaultValue,secondaryValue,fieldDescription,minValue,maxValue,tab
GearIn_fullGraphicEffect,Full graphic effect,Boolean,true,,,,,GearIn
```

LunaLib 按文件来源记录设置拥有者，读取时再传入 `gear_in`。适配器中使用包装类型 `Boolean`，字段不存在或无法取值时采用调用方缺省：

```java
Boolean value = LunaSettings.getBoolean(SETTINGS_OWNER_MOD_ID, "GearIn_fullGraphicEffect");
return value == null ? fallback : value;
```

已有值为 `false` 时，返回的就是 `false`。默认值服务缺席或缺键，字段本身的值继续由 LunaLib 管理。

## 按应用运行记录一次缺席

桥接类用静态字段记录已经报告的状态。同一应用中从多个入口读取，缺库日志也只产生一次。以下为局部报告片段：

```java
if (!lunaMissingReported) {
    lunaMissingReported = true;
    Global.getLogger(GearInCompatBridge.class).info(
            "GearIn compat: LunaLib unavailable, fallback=" + fallback);
}
```

读另一份存档时静态标记继续保留；退出游戏并重新启动后，标记重新初始化。编者建议把这条消息用于确认接入状态：需要查的是哪项功能采用了什么缺省，重复打印同一句话并不会提供更多信息。

## 启用组合速查表

| 启用组合 | Nexerelin 检测 | LunaLib 检测 |
|---|---|---|
| 两库都缺席 | false | false |
| 仅 Nexerelin | true | false |
| 仅 LunaLib | false | true |
| 两库都启用 | true | true |

## 连续查询接入状态

Console Commands 可以在同一生涯中重复读取，核对返回值与报告状态：

```text
runcode for (int i = 0; i < 100; i++) { data.scripts.gearin.modcompat.GearInCompatBridge.getFullGraphicEffect(true); data.scripts.gearin.modcompat.GearInCompatBridge.reportNexerelinFallback(); }
```

同一次应用运行的静态报告标记继续保留，重复读取按当前启用状态取值。

## 完整最小例

桥接类统一检测并报告缺席，LunaLib 取值通过嵌套适配器调用：

::: details 完整成品：GearInCompatBridge

```java
package data.scripts.gearin.modcompat;

import com.fs.starfarer.api.Global;

public final class GearInCompatBridge {
    public static final String NEX_MOD_ID = "nexerelin";
    public static final String LUNA_MOD_ID = "lunalib";
    public static final String SETTINGS_OWNER_MOD_ID = "gear_in";
    public static final String FULL_GRAPHIC_FIELD = "GearIn_fullGraphicEffect";

    private static boolean nexMissingReported;
    private static boolean lunaMissingReported;

    private GearInCompatBridge() {
    }

    public static boolean isNexerelinEnabled() {
        return Global.getSettings().getModManager().isModEnabled(NEX_MOD_ID);
    }

    public static boolean isLunaLibEnabled() {
        return Global.getSettings().getModManager().isModEnabled(LUNA_MOD_ID);
    }

    public static boolean getFullGraphicEffect(boolean fallback) {
        if (!isLunaLibEnabled()) {
            if (!lunaMissingReported) {
                lunaMissingReported = true;
                Global.getLogger(GearInCompatBridge.class).info(
                        "GearIn compat: LunaLib unavailable, fallback=" + fallback);
            }
            return fallback;
        }
        return LunaAdapter.getFullGraphicEffect(fallback);
    }

    public static void reportNexerelinFallback() {
        if (!isNexerelinEnabled() && !nexMissingReported) {
            nexMissingReported = true;
            Global.getLogger(GearInCompatBridge.class).info(
                    "GearIn compat: Nexerelin unavailable, strategic integration disabled");
        }
    }

    private static final class LunaAdapter {
        private static boolean getFullGraphicEffect(boolean fallback) {
            Boolean value = lunalib.lunaSettings.LunaSettings.getBoolean(
                    SETTINGS_OWNER_MOD_ID, FULL_GRAPHIC_FIELD);
            return value == null ? fallback : value;
        }
    }
}
```

:::

## 验证方案

前置数据：Nexerelin、LunaLib 与 Console Commands 在开发环境中可用，启动器能够分别选择四种组合。

1. 保存桥接类、设置 CSV 与生涯调用，构建一次 JAR；依次选择表中的四种组合，重启并载入测试生涯，搜索 `GearIn optional`。
2. 在每种组合的生涯中执行正文连续查询命令，重复两项调用 100 次，核对缺席日志条数。
3. LunaLib 启用时，在面板中分别保存 `true` 与 `false`，重新调用 getter。缺键实验将桥接类的 `FULL_GRAPHIC_FIELD` 暂时改为 `GearIn_missing_graphic_field`，构建重启并观察缺省；实验后恢复 `GearIn_fullGraphicEffect`，构建重启。
4. 在另一测试 MOD 中定义同名字段并修改它，确认拥有者为 `gear_in` 的读取仍使用自己的值。
5. 两库都缺席时进入已有战斗和生涯内容，再在同一次运行中重复载入存档，比较缺席日志。

预期结果：

- 四组检测分别对应表中的布尔值，同一 JAR 能完成启动与载入；
- LunaLib 缺席时 100 次读取返回 `true`，两项缺席报告各自最多一条；
- 已有字段返回实际值，缺键时采用调用方缺省；
- 另一个 MOD 的同名字段按自己的拥有者保存；
- 基础内容可用，一次应用运行中的重复读档保持同一缺席报告状态。

## 故障分析

- **缺少 LunaLib 时出现 NoClassDefFoundError**：基础类的父类、接口、字段或无条件路径包含第三方类型。
    - **解决方案**：将直接库关系放入启用分支后调用的独立适配器，再用缺库组合重启验证。

- **启用检测与启动器状态不符**：查询 ID 与依赖 manifest 不一致。
    - **解决方案**：核对 `nexerelin`、`lunalib` 的实际拼写与启用状态。

- **库已启用但设置读成缺省**：拥有者或字段 ID 不匹配，或当前值不能按所需类型取得。
    - **解决方案**：核对 `gear_in`、`GearIn_fullGraphicEffect` 和 LunaLib 的字段读取日志。

- **同一次运行反复输出缺席消息**：各调用点各自打印，或标记没有存放在共享静态状态中。
    - **解决方案**：统一调用桥接报告入口，按本节静态字段保存报告状态。

## 总结

编译配置让开发工程找到库，桥接类在运行时判断是否启用，适配器在分支通过后调用第三方代码。默认值由使用方决定，已有设置按拥有者和字段读取，日志记录本次应用采用的接入状态。

用四种组合运行同一产物，才能把编译依赖、类加载和实际调用对应起来。后面的兼容角色继续沿用这组检测入口。

## 建议参考项

- `com.fs.starfarer.api.ModManagerAPI`：`isModEnabled` 查询依赖的启用状态；
- `lunalib.lunaSettings.LunaSettings`：`getBoolean` 按 MOD 与字段取值；
- `data/config/LunaSettings.csv`：图形开关定义；
- `Starsector/starsector-core/starsector.log`：接入状态与字段读取日志。
