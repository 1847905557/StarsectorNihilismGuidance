# 让可选依赖缺席时安全降级

软依赖允许 GearIn 在缺少 Nexerelin 或 LunaLib 时继续启动，但前提是库类引用被收敛在不会误加载的适配器中。本节建立启用检测、调用隔离和一次性降级日志。

*本节预期阅读时间：~18 分钟。*

*本节预期操作时间：~30 分钟。*

## 学习目标

完成后，您将能够：

* 用精确 MOD ID 分别检测 Nexerelin 与 LunaLib；
* 让基础入口只加载不含第三方类型的外层桥接类；
* 在检测通过后才触发持有 LunaLib 类型的嵌套适配器；
* 为缺库和缺键定义明确的默认返回值；
* 让每项缺库日志在一次应用运行中最多出现 1 次。

## 前置知识

* [C6-S2] 理解 Java 类、方法、字段与入口插件；
* [C7-S1] 理解每帧回调、暂停判断与运行时对象；
* 能够用 Gradle 构建示例 MOD，并从 `starsector.log` 定位类名。

## 明确当前需求

同一份 GearIn 在三种组合下启动：两个库都缺席、只装 LunaLib、两个都安装。基础内容始终可用，扩展功能只在对应库启用时接入。

## 文件与 API 链路

`ModManagerAPI.isModEnabled` 判断启用状态；入口类只引用 `GearInCompatBridge`；外层桥接类只包含原版类型；私有 `LunaAdapter` 直接引用 LunaLib，并且只在启用分支首次调用时加载。两个静态布尔值分别限制 Nexerelin 与 LunaLib 的缺席日志。

### 用准确 MOD id 判断启用状态

**插入位置：`GearInCompatBridge` 的两个查询方法。** 分别读取 Nexerelin 与 LunaLib 的启用状态。

```java
boolean nex = Global.getSettings().getModManager().isModEnabled("nexerelin");
boolean luna = Global.getSettings().getModManager().isModEnabled("lunalib");
```

完成信号：三种安装组合得到对应布尔值。

### 把库引用留在适配器类

**插入位置：`getFullGraphicEffect()` 的启用分支。** 外层方法只在检测通过后触发持有 LunaLib 字节码引用的嵌套类。

```java
if (isLunaLibEnabled()) {
    return LunaAdapter.getFullGraphicEffect(fallback);
}
```

完成信号：LunaLib 缺席时，`LunaAdapter` 不初始化，外层桥接类仍可完成 Nexerelin 检测和默认值返回。

### 为缺席功能定义明确行为

**插入位置：每个 disabled 分支。** 保持基础玩法并关闭增强 UI 或战略接入。

```java
if (!luna) {
    LOG.info("GearIn optional feature disabled: LunaLib settings");
}
```

完成信号：玩家仍能进入存档，日志说明关闭了哪项增强。

### 让降级日志只出现一次

**插入位置：外层桥接类的缺席分支。** 避免多个调用点重复报告同一缺席状态。

```java
if (!lunaMissingReported) {
    lunaMissingReported = true;
    LOG.info(message);
}
```

完成信号：starsector.log 每次读档只有一条对应消息。

### 对包装类型返回值处理 null

**插入位置：`LunaAdapter.getFullGraphicEffect()`。** 在拆箱前提供调用方默认值。

```java
Boolean value = LunaSettings.getBoolean("gear_in", "showFx");
return value == null ? true : value;
```

完成信号：缺键时使用页面声明的默认设置。

## 排查关键故障

下面是**局部故障片段**：

```java
if (Global.getSettings().getModManager().isModEnabled("lunalib")) {
}
LunaSettings.getBoolean("gear_in", "showFx");
```

实际症状：未安装 LunaLib 时仍抛 NoClassDefFoundError。

原因：检测只包住空分支，第三方类调用仍位于无条件执行路径。

下面是**局部修正片段**：

```java
if (isLunaEnabled()) {
    return GearInLunaSettingsReader.isFxEnabled();
}
return true;
```

复验：移除 LunaLib 后连续调用设置入口 100 次，确认返回值始终等于 fallback，日志只出现 1 条 LunaLib 缺席消息，并且没有类加载错误。

## 完整最小例

完整桥接类同时覆盖两个可选依赖。Nexerelin 专题只在 `isNexerelinEnabled()` 为真时进入各自适配器；LunaLib 设置读取则通过延迟加载的嵌套类隔离第三方类型。

## 验证方案

分别准备 4 种启用组合：两个库都缺席、仅 Nexerelin、仅 LunaLib、两个库都启用。每种组合都使用同一 GearIn JAR。

### 启用组合检测结果速查表

| 安装组合 | Nexerelin 检测 | LunaLib 检测 |
|---|---|---|
| 两个库都缺席 | false | false |
| 仅 Nexerelin | true | false |
| 仅 LunaLib | false | true |
| 两个库都启用 | true | true |

1. 启动并读档，记录两个 `is*Enabled()` 的返回值。
2. 连续调用 `getFullGraphicEffect(true)` 100 次，记录返回值和缺席日志条数。
3. 连续调用 `reportNexerelinFallback()` 100 次，记录 Nexerelin 缺席日志条数。
4. LunaLib 启用时分别测试设置值 true、false 和字段缺失。
5. 两个库都缺席时进入基础战斗与生涯界面，确认非兼容内容可用。

预期结果：

* 4 种组合的两个布尔值分别为 `false/false`、`true/false`、`false/true`、`true/true`；
* LunaLib 缺席时 100 次读取均返回 fallback，缺席日志为 1 条；
* Nexerelin 缺席时 100 次报告只产生 1 条战略接入禁用日志；
* LunaLib 字段存在时返回实际值，字段缺失时返回 fallback；
* 两个库都缺席时基础内容仍可进入，没有 `NoClassDefFoundError`。

## 故障分析

- **未安装 LunaLib 时仍抛 `NoClassDefFoundError`**：第三方类型仍出现在无条件路径，或被外层静态字段提前解析。把直接引用收进延迟加载的适配器，只在启用分支调用；禁用 LunaLib 后确认 MOD 能进入游戏。
- **`isModEnabled` 始终返回 false**：检测 ID 的大小写或拼写与 manifest 不一致。改用精确 MOD ID，启用前置后确认检测结果为 true。
- **检测通过后仍在加载期崩溃**：适配器类在入口字段或静态初始化中被提前解析。移除外层类型引用并延迟到分支内部，重启后确认类只在前置可用时加载。
- **缺少前置时功能悄悄变化**：降级分支没有输出诊断。增加只记录一次的降级日志，禁用前置后确认日志说明采用的替代行为。

## 总结

软依赖的完整做法包括启用判断、库调用隔离、明确降级和可定位日志。判断本身只回答库是否启用，代码组织才决定缺库时类加载是否安全。

## 建议参考项

* `ModManagerAPI.isModEnabled`：判断可选 MOD 是否启用；
* 适配器类：收敛第三方类型引用；
* `starsector.log`：确认增强功能启用或降级。
::: details 完整成品：GearInCompatBridge

```java
package data.scripts.gearin.modcompat;

import com.fs.starfarer.api.Global;

public final class GearInCompatBridge {
    public static final String NEX_MOD_ID = "nexerelin";
    public static final String LUNA_MOD_ID = "lunalib";
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
                    LUNA_MOD_ID, FULL_GRAPHIC_FIELD);
            return value == null ? fallback : value;
        }
    }
}
```

:::
