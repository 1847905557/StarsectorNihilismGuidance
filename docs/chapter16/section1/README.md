# 让启动器替 MOD 检查硬依赖

GearIn 同时使用四个渲染与工具前置；缺少其中任何一个时，最好的失败位置是启动器，而非战斗加载到一半。本节让声明检查与运行期诊断各负其责，最后能从启动器提示和日志中准确说出缺了哪个库。

*本节预期阅读时间：~18 分钟。*

*本节预期操作时间：~30 分钟。*

## 学习目标

完成后，您将能够：

* 在 `mod_info.json` 中声明依赖 ID 与最低版本，让启动器在加载前完成硬依赖检查；
* 从 `ModSpecAPI.getDependencies()` 读取当前 MOD 的依赖声明；
* 用 `ModManagerAPI` 对齐声明、启用状态与实际安装版本；
* 区分启动器拒绝加载与 Java 诊断日志各自能证明的事实；
* 从 MOD ID、启用状态和版本信息定位依赖检查故障。

## 前置知识

* [C6-S2] 理解 Java 类、方法、字段与入口插件；
* [C7-S1] 理解每帧回调、暂停判断与运行时对象；
* 能够用 Gradle 构建示例 MOD，并从 `starsector.log` 定位类名。

## 明确硬依赖检查需求

玩家把整套 MOD 复制给朋友，对方只装了 LazyLib。启动器应直接列出 GraphicsLib、MagicLib 与 BoxUtil，而开发者自己的日志仍能报告已启用依赖的安装版本。

## 文件与 API 链路

`mod_info.json.dependencies` 负责加载前判定；`ModManagerAPI`、`ModSpecAPI` 与 `ModDependencyAPI` 负责运行期读取同一份声明。版本边界写在 manifest，Java 诊断只解释当前状态。

### 把四项依赖交给启动器

**插入位置：`mod_info.json` 的 `dependencies` 数组。** 声明 ID、名称和最低版本。

```json
"dependencies": [
  {"id":"lw_lazylib","name":"LazyLib","version":"3.0.0"},
  {"id":"shaderLib","name":"GraphicsLib","version":"1.10.0"},
  {"id":"MagicLib","name":"MagicLib","version":"1.5.1"},
  {"id":"BoxUtil","name":"BoxUtil","version":"1.5.4"}
]
```

完成信号：取消勾选任一前置后，启动器立即指出对应依赖。

### 找到 GearIn 自己的声明

**插入位置：`GearInDependencyCheck.reportDeclaredDependencies()` 开头。** 从启用列表取得当前 MOD 的 `ModSpecAPI`。

```java
ModManagerAPI mods = Global.getSettings().getModManager();
ModSpecAPI self = mods.getModSpec(SELF_MOD_ID);
```

完成信号：self 不为 null，后续读取的是 GearIn 自己的 dependencies。

### 逐项读取启用状态

**插入位置：`reportDeclaredDependencies()` 的 `for` 循环。** 把声明 ID 与启动器启用状态对齐。

```java
for (ModDependencyAPI dep : self.getDependencies()) {
    boolean enabled = mods.isModEnabled(dep.getId());
}
```

完成信号：日志中的 enabled 与启动器勾选状态一致。

### 补上安装版本

**插入位置：`enabled` 分支。** 从已启用的 `ModSpecAPI` 读取 `VersionInfoAPI`。

```java
ModSpecAPI installed = mods.getModSpec(dep.getId());
VersionInfoAPI version = installed.getVersionInfo();
String text = version == null ? "unknown" : version.getString();
```

完成信号：日志出现 installedVersion，而非含混的 "已安装"。

### 在应用加载期执行一次诊断

**插入位置：`GearInModPlugin.onApplicationLoad()` 末尾。** 保证诊断只运行一次且 `Global` 设置服务可用。

```java
GearInDependencyCheck.reportDeclaredDependencies();
```

完成信号：starsector.log 出现四条 GearIn dependency check 日志。

## 排查依赖诊断故障

下面是**局部故障片段**：

```java
ModManagerAPI mods = Global.getSettings().getModManager();
mods.getModSpec("GearIn").getDependencies();
```

实际症状：启动器已经允许加载，但应用加载期出现 NullPointerException。

原因：`getModSpec` 按 MOD ID 精确匹配，显示名 `GearIn` 不是 ID `gear_in`。

下面是**局部修正片段**：

```java
ModSpecAPI self = mods.getModSpec("gear_in");
if (self == null) return;
```

复验：恢复 `gear_in` 后启动一次，确认日志列出四项声明，且每项都有 `enabled` 和 `installedVersion`。

## 完整最小例

以下完整类只承担运行期诊断。`mod_info.json` 中的声明仍由启动器读取，应用加载入口只需调用一次 `reportDeclaredDependencies()`。

## 验证方案

前置数据：GearIn 的 `mod_info.json` 已声明四项依赖，`GearInDependencyCheck` 已编译进 JAR，并在 `onApplicationLoad()` 中调用一次。

1. 保持四项依赖启用，启动游戏并在 `starsector.log` 搜索 `GearIn dependency check`。
2. 核对四行日志中的依赖名称、ID、`enabled=true` 和安装版本。
3. 退出游戏，在启动器中取消勾选 BoxUtil，再尝试勾选 GearIn。
4. 恢复 BoxUtil，将 `SELF_MOD_ID` 临时改为显示名 `GearIn`，启动一次以复现本页故障。
5. 把 ID 恢复为 `gear_in`，重新构建并启动，确认四行诊断恢复。

预期结果：

* 四项依赖齐全时，日志准确列出 4 条声明，并报告各自的启用状态与安装版本；
* BoxUtil 未启用时，启动器在进入游戏前阻止 GearIn 加载并指出缺失依赖；
* 使用显示名查询时，故障发生在 `getModSpec("GearIn")` 返回 `null` 之后；
* 恢复精确 ID 后，应用加载阶段没有未处理异常。

## 故障分析

- **应用加载期出现 `NullPointerException`**：`getModSpec` 使用了显示名或错误大小写，无法匹配 MOD ID。改用 manifest 中的精确 ID 并保留 null 检查，重启后确认诊断日志出现。
- **启动器提示依赖版本不满足**：dependencies 中的最低版本高于已安装前置版本。核对两份 manifest 的版本值，更新前置或调整兼容范围后重新启动。
- **已安装前置却显示 `enabled=false`**：前置没有在启动器中勾选，或诊断 ID 大小写不一致。启用前置并统一 ID，再检查加载日志。
- **诊断显示 `installedVersion=unknown`**：前置 manifest 没有提供可读版本对象。由启动器继续执行依赖判断，并确认 `enabled` 与依赖状态符合预期。

## 总结

硬依赖的价值在于让错误提前发生，而且提示对象明确。manifest 决定能否加载，Java 诊断解释当前加载了什么；两者读取同一组 id，职责并不重复。

## 建议参考项

* `mod_info.json`：硬依赖声明与最低版本；
* `ModManagerAPI`：读取启用列表；
* `ModDependencyAPI`：读取当前 MOD 声明的依赖项。
::: details 完整成品：GearInDependencyCheck

```java
package data.scripts.gearin.modcompat;

import java.util.List;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.ModDependencyAPI;
import com.fs.starfarer.api.ModManagerAPI;
import com.fs.starfarer.api.ModSpecAPI;
import com.fs.starfarer.api.VersionInfoAPI;

/**
 * 依赖的运行时检测：把 mod_info.json 声明的 dependencies 与启动器 MOD 管理器
 * 的运行时状态对齐，逐项输出 id、名称、是否启用与安装版本。
 *
 * 声明式硬依赖由启动器把关（缺失或版本不符时无法勾选）；本类只负责
 * 在代码里复核声明与事实，并把不一致暴露成明确的日志行。
 */
public class GearInDependencyCheck {

    public static final String SELF_MOD_ID = "gear_in";

    private GearInDependencyCheck() {
    }

    /**
     * 在 MOD 插件的 onApplicationLoad() 中调用一次（接入方式见页面正文）。
     */
    public static void reportDeclaredDependencies() {
        ModManagerAPI modManager = Global.getSettings().getModManager();
        ModSpecAPI self = modManager.getModSpec(SELF_MOD_ID);
        if (self == null) {
            Global.getLogger(GearInDependencyCheck.class).warn(
                "GearIn dependency check: mod [" + SELF_MOD_ID + "] is not enabled; cannot read declared dependencies");
            return;
        }
        List<ModDependencyAPI> declared = self.getDependencies();
        if (declared.isEmpty()) {
            Global.getLogger(GearInDependencyCheck.class).info(
                "GearIn dependency check: no dependencies declared in mod_info.json");
            return;
        }
        for (ModDependencyAPI dep : declared) {
            boolean enabled = modManager.isModEnabled(dep.getId());
            String installedVersion = "not installed";
            if (enabled) {
                ModSpecAPI installed = modManager.getModSpec(dep.getId());
                VersionInfoAPI versionInfo = installed.getVersionInfo();
                if (versionInfo != null) {
                    installedVersion = versionInfo.getString();
                }
            }
            Global.getLogger(GearInDependencyCheck.class).info(
                "GearIn dependency check: " + dep.getName() + " (" + dep.getId()
                    + ") enabled=" + enabled
                    + " installedVersion=" + installedVersion);
        }
    }
}
```

:::
