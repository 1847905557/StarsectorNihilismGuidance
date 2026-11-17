# 接入可选依赖

把 Nexerelin 或 LunaLib 直接写进 `mod_info.json` 依赖表，会让未安装这两个扩展的玩家在启动器里无法勾选启用。

可选兼容走的是另一条路：编译时靠 `compileOnly` 让代码认识外库，打包不带进任何三方类，运行时再由启用检测与嵌套适配器决定到底读面板还是退回默认值。

---

_本节预期阅读时间：\~15 分钟。_

_本节预期操作时间：\~25 分钟。_

## 前置知识

- [C1-S1] 的 MOD ID(`GearIndustry`) 与 `mod_info.json` 规范；
- [C7-S1] 的应用与生涯加载回调；
- [C7-S2] 的 `Global` 服务与日志输出；
- [C18-S1] 的 `compileOnly`、`dependencyJarDirs` 与 JAR 输出。

## 把两库加入编译 classpath

先确认本地已安装 Nexerelin(`0.12.1e`) 与 LunaLib(`2.0.5`)，随后打开 [C18-S1] 的 `build.gradle`，按真实文件夹名在 `dependencyJarDirs` 定义之后、`dependencies` 块之前追加：

```groovy
dependencyJarDirs += [
    "${modsDir}/Nexerelin/jars",
    "${modsDir}/Lunalib/jars",
]
```

注意这里同样使用 `compileOnly`。写完运行一次 `gradle printCompileClasspath`，确认输出里包含 `ExerelinCore.jar` 与 `LunaLib.jar`。由于这只是可选兼容，[C18-S1] 里原本写进 `mod_info.json` 的三库强依赖保持不变，**不要**把 `nexerelin` 或 `lunalib` 加进 `dependencies` —— 运行时是否启用，交给 Java 代码自己查。

## 用精确 MOD ID 区分两个概念

把后文的完整 `GearInCompatBridge` 保存到 `src/data/scripts/gearin/modcompat/GearInCompatBridge.java`。判断可选库是否启用时，直接询问原版管理器：

```java
boolean nex = Global.getSettings().getModManager().isModEnabled("nexerelin");
boolean luna = Global.getSettings().getModManager().isModEnabled("lunalib");
```

这里的 `"nexerelin"` 和 `"lunalib"` 来自两库各自 `mod_info.json` 里的 `"id"`(大小写严格匹配)。另外要注意区分另一组常见混淆：LunaSettings 按来源 MOD 存放数据时，同样只认各自 `mod_info.json` 里的 `"id"`。本教程齿轮工业的 MOD ID 在 [C1-S1] 定为 `"GearIndustry"`(而 `"gear_industry"` 是 [C3-S1] 注册的势力 ID)，因此后面向 LunaSettings 取值时，拥有者 ID 必须传 `"GearIndustry"`，否则会在日志里收到 `Could not find mod` 报错并返回 `null`。

## 把第三方类型挡在启用分支后面

**外层负责开关检测与默认回退，嵌套适配器才直接碰第三方库。**只要桥接类的公共签名、字段和早返回路径都不暴露 LunaLib 类型，在 HotSpot 延迟解析下，JVM 就不会在 LunaLib 缺席时提前尝试加载 `LunaSettings`。局部转发写法如下：

```java
if (isLunaLibEnabled()) {
    return LunaAdapter.getFullGraphicEffect(fallback);
}
```

`isLunaLibEnabled()` 返回 `false` 时，代码根本不会触发 `GearInCompatBridge$LunaAdapter` 的类加载与常数池解析，而是直接返回调用方给的 `fallback`。

::: warning 继承与字段类型会在分支之前触发类加载

千万不要为了省事让外层类或 `BaseModPlugin` 直接继承、实现某个可选库的父类/接口，也不要把第三方类写进外层字段或方法签名。只要外层类本身在加载或校验阶段需要解析这些类型，缺库时不等进 `if` 分支就会当场抛 `NoClassDefFoundError`。

:::

## 定义图形开关与包装类型读取

在 MOD 下新建 `data/config/LunaSettings.csv`，写入九列表头与一个布尔示例项：

```csv
fieldID,fieldName,fieldType,defaultValue,secondaryValue,fieldDescription,minValue,maxValue,tab
GearIn_fullGraphicEffect,Full graphic effect,Boolean,true,,,,,GearIn
```

LunaLib 启动时会遍历已启用的 MOD，以 `mod.getId()` 作为键加载各自包里的 `data/config/LunaSettings.csv`，并把默认配置写入 `LunaSettings/GearIndustry.json`。适配器里统一用包装类型 `Boolean` 接返回值，方便把 "玩家特意选了 `false`" 和 "缺库/缺键返回的 `null`" 区分开：

```java
Boolean value = lunalib.lunaSettings.LunaSettings.getBoolean(
        SETTINGS_OWNER_MOD_ID, FULL_GRAPHIC_FIELD);
return value == null ? fallback : value;
```

## 每次运行只报告一次缺席

如果多个插件每帧或每次进档都调 `GearInCompatBridge`，缺库提示很容易把 `starsector.log` 刷满。利用静态布尔标记，可以让每次应用启动后最多输出一条信息：

```java
if (!lunaMissingReported) {
    lunaMissingReported = true;
    Global.getLogger(GearInCompatBridge.class).info(
            "GearIn compat: LunaLib unavailable, fallback=" + fallback);
}
```

这类状态挂在桥接类的静态字段上，跨读档保留，直到玩家重启游戏进程才重新初始化。

在既有 `GearInModPlugin.onGameLoad(...)` 里补上一组探针日志 (保留原有初始化)：

```java
boolean nex = GearInCompatBridge.isNexerelinEnabled();
boolean luna = GearInCompatBridge.isLunaLibEnabled();
boolean fullGraphic = GearInCompatBridge.getFullGraphicEffect(true);
GearInCompatBridge.reportNexerelinFallback();
Global.getLogger(GearInModPlugin.class).info(
        "GearIn optional: nex=" + nex + " luna=" + luna + " fullGraphic=" + fullGraphic);
```

| 启用组合     | Nexerelin 检测 | LunaLib 检测 |
| ------------ | -------------- | ------------ |
| 两库都缺席   | false          | false        |
| 仅 Nexerelin | true           | false        |
| 仅 LunaLib   | false          | true         |
| 两库都启用   | true           | true         |

## 完整最小例

完整桥接代码同时涵盖启用检测、一次一报日志以及嵌套 `LunaAdapter` 读取：

::: details 完整成品：GearInCompatBridge

```java
package data.scripts.gearin.modcompat;

import com.fs.starfarer.api.Global;

public final class GearInCompatBridge {
    public static final String NEX_MOD_ID = "nexerelin";
    public static final String LUNA_MOD_ID = "lunalib";
    public static final String SETTINGS_OWNER_MOD_ID = "GearIndustry";
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

### 进游戏核对

1. **构建并测试四种启停组合**：编译打一次 JAR，在启动器里依次以 "双关 / 仅 Nexerelin / 仅 LunaLib / 双开" 四组配置启动游戏并载入存档，在 `starsector.log` 搜索 `GearIn optional`，确认布尔输出与上表一一对应且未出现 `NoClassDefFoundError`。
2. **确认缺库不刷屏**：在两库都关闭时用 Console Commands 连续调用 100 次，核对日志里 `LunaLib unavailable` 与 `strategic integration disabled` 各自只出现 1 条，重复读档也不再新增：

```text
runcode for (int i = 0; i < 100; i++) { data.scripts.gearin.modcompat.GearInCompatBridge.getFullGraphicEffect(true); data.scripts.gearin.modcompat.GearInCompatBridge.reportNexerelinFallback(); }
```

3. **核对设置取值与字段隔离**：启用 LunaLib 后在设置面板把 `Full graphic effect` 改为 `false` 并保存，重新读取应拿到 `false` 而非默认值 `true`；在另一测试 MOD 中定义同名的 `GearIn_fullGraphicEffect`，因为 `LunaSettings` 以 `(modID, fieldID)` 分别入表与存盘，各自值互不覆盖。

## 常见问题

- **缺席 LunaLib 时启动或读档直接报 `NoClassDefFoundError`**：外层类的字段、签名、父类/接口直接引用了第三方类，或者跳过 `isLunaLibEnabled()` 提前调到了适配器。
    - **解决方案**：把外层类型严格限定在原版与 JDK 类型以内，直接调用统一放进嵌套适配器。

- **LunaLib 已启用，开关改了却一直退回代码默认值**：`SETTINGS_OWNER_MOD_ID` 被误写成势力 ID 或简称，或者 `LunaSettings.csv` 的字段名与九列逗号数量不对。
    - **解决方案**：核对 `SETTINGS_OWNER_MOD_ID` 是否与 `mod_info.json` 的 `"id"`(`"GearIndustry"`) 一致，并检查 CSV 表头和 `fieldID`。

- **检测结果和启动器勾选状态不一致**：传给 `isModEnabled(...)` 的字符串与依赖 MOD 的 manifest 不符。
    - **解决方案**：核对 `"nexerelin"`、`"lunalib"` 是否与对方 `mod_info.json` 中的 `"id"` 大小写完全一致。

## 建议参考项

- `com.fs.starfarer.api.ModManagerAPI`：`isModEnabled` 查询依赖 MOD 启用状态；
- `lunalib.lunaSettings.LunaSettings`：`getBoolean` 按 MOD ID 与字段名读取设置；
- `data/config/LunaSettings.csv`：图形开关定义入口；
- `Starsector/starsector-core/starsector.log`：查看 `GearIn optional` 与缺失提示。
