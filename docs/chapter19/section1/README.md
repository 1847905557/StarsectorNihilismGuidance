# 接入可选依赖

可选依赖要同时处理库存在与缺席时的行为。本节为 Nexerelin 和 LunaLib 加入编译路径，用桥接类检测启用状态，再把 LunaLib 的直接调用留在适配器里。库缺席时图形开关采用调用方缺省，已有基础内容继续运行；同一份 JAR 用四种组合验证这个约定。

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

## 为开发工程提供两库 JAR

开发环境安装 Nexerelin 与 LunaLib，按实际目录名把 JAR 加入 [C18-S1] 的 `dependencyJarDirs`。下面的局部代码放在该数组定义后、`dependencies` 块之前：

```groovy
dependencyJarDirs += [
    "${modsDir}/Nexerelin/jars",
    "${modsDir}/Lunalib/jars",
]
```

继续使用 `compileOnly`，保存后执行 `gradle printCompileClasspath`，确认包含 `ExerelinCore.jar` 与 `LunaLib.jar`。编译器需要这些类型，玩家运行时则可以选择是否启用两库。前文章节的三库硬依赖继续保留，本节的可选状态由 Java 查询。

## 用 MOD ID 检测启用状态

把本节完整的 `GearInCompatBridge` 保存到 `src/data/scripts/gearin/modcompat/GearInCompatBridge.java`。外层查询使用原版 `ModManagerAPI`：

```java
boolean nex = Global.getSettings().getModManager().isModEnabled("nexerelin");
boolean luna = Global.getSettings().getModManager().isModEnabled("lunalib");
```

这两个 ID 来自依赖 MOD 的 `mod_info.json`。LunaLib 设置的拥有者则是 `gear_in`，读取时由自己的 MOD ID 与字段名共同定位值。启用检测决定能否调用库，拥有者决定调用后读取哪份设置。

## 把库类型留在启用分支后面

**外层负责检测，适配器负责直接调用库。**桥接类的公共签名和字段使用原版或 Java 类型，直接引用 LunaLib 的代码放在独立嵌套类中。局部调用为：

```java
if (isLunaLibEnabled()) {
    return LunaAdapter.getFullGraphicEffect(fallback);
}
```

分支通过后才进入 `LunaAdapter`，库缺席时直接返回缺省。适配器的 class 文件与外层分开，基础入口可以取得检测结果，而无需先建立 LunaLib 的直接类型关系。

::: warning 父类依赖发生在方法判断之前
基础入口若继承依赖库的类，缺库时可能在加载父类阶段就失败，方法里的启用判断尚未执行。接口、公用签名与字段类型也要按实际使用位置核对；外层桥接保留 Java、原版和自有类型。
:::

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

读另一份存档时静态标记继续保留，退出并重启应用后再初始化。这个记录范围与依赖的启用状态一致：库在本次应用中缺席，重复调用仍采用相同缺省；日志因此按应用报告一次。

## 接到生涯入口比较结果

向既有 `GearInModPlugin.onGameLoad(...)` 追加局部调用，导入桥接类并保留此前初始化：

```java
boolean nex = GearInCompatBridge.isNexerelinEnabled();
boolean luna = GearInCompatBridge.isLunaLibEnabled();
boolean fullGraphic = GearInCompatBridge.getFullGraphicEffect(true);
GearInCompatBridge.reportNexerelinFallback();
Global.getLogger(GearInModPlugin.class).info(
        "GearIn optional: nex=" + nex + " luna=" + luna + " fullGraphic=" + fullGraphic);
```

构建、重启并载入生涯后，搜索 `GearIn optional`。调用方给图形开关选择缺省 `true`，库缺席时返回这个值；已有设置为 `false` 时读取它本身。具体效果由后面的使用方处理，Nexerelin 功能也在启用分支接入。

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

桥接把启用检测、直接库调用与缺席行为接成明确的分支：基础入口取得状态，适配器读取第三方设置，调用方决定缺省。四种组合使用同一份产物，分别确认类加载与运行结果；后续兼容角色继续沿用这些检测入口。

## 建议参考项

- `com.fs.starfarer.api.ModManagerAPI`：`isModEnabled` 查询依赖的启用状态；
- `lunalib.lunaSettings.LunaSettings`：`getBoolean` 按 MOD 与字段取值；
- `data/config/LunaSettings.csv`：图形开关定义；
- `Starsector/starsector-core/starsector.log`：接入状态与字段读取日志。
