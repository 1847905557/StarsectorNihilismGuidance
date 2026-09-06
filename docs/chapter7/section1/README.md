# 制作 MOD 入口类

本节为 MOD 声明一个入口类，并把新档初始化与存档状态放进合适的生命周期回调。入口类使用 [C3-S3] 已建立的 `GearInModPlugin`，本节补充它在工程化 JAR 中的正式注册和持久化处理。

*本节预期阅读时间：约 10 分钟。*

*本节预期操作时间：约 15 分钟。*

## 学习目标

完成后，您将能够：

- 在 `mod_info.json` 中登记 `modPlugin` 完整类名；
- 创建继承 `BaseModPlugin` 的入口类并覆写需要的回调；
- 区分启动、新档、读档和保存阶段的核心回调；
- 用 persistent data 让一次性初始化只对每个存档执行一次；
- 根据日志和存档结果定位入口类故障。

## 前置知识

- [C1-S1] 的 MOD 目录、`mod_info.json` 和 `starsector.log`；
- [C3-S3] 的 `GearInModPlugin`、`onNewGame()` 与势力关系设置；
- [C3-S4] 的星系生成调用；
- [C6-S1] 至 [C6-S4] 的 Java 17、源码迁移、Gradle 和 JAR 构建。

## 声明入口类

在 `mod_info.json` 中加入 `modPlugin`。字段值必须是 Java 文件的完整类名；本例的包名是 `data.scripts.gearin`，类名是 `GearInModPlugin`。

```json
{
  "id": "gear_in",
  "name": "齿轮工业",
  "author": "YourName",
  "version": { "major": 0, "minor": 1, "patch": 0 },
  "description": "Starsector MOD 教程的练习项目。",
  "gameVersion": "0.98a-RC8",
  "jars": ["jars/GearIndustry.jar"],
  "modPlugin": "data.scripts.gearin.GearInModPlugin"
}
```

`modPlugin` 只声明入口类，`jars` 负责让游戏加载包含该类的产物。两项都必须保留，路径和完整类名逐字一致。

## 创建入口类

Java 源码放在 `src/data/scripts/gearin/GearInModPlugin.java`。入口类继承 `BaseModPlugin`；这个基类提供未使用回调的默认实现，类只覆写当前任务需要的回调。

```java
public class GearInModPlugin extends BaseModPlugin {
```

### 处理启动检查

`onApplicationLoad()` 在游戏资源加载完成后执行。此时可以读取设置并检查版本，`Global.getSector()` 还没有可用的生涯对象，因此生涯操作必须放在后面的回调。

```java
@Override
public void onApplicationLoad() throws Exception {
    String version = Global.getSettings().getGameVersion();
    if (!version.startsWith("0.98a")) {
        throw new IllegalArgumentException("齿轮工业要求游戏版本 0.98a，当前为 [" + version + "]");
    }
    log("onApplicationLoad");
}
```

### 处理新档和读档

`onNewGame()` 只在创建新生涯时执行，因此 [C3-S3] 的关系设置和 [C3-S4] 的星系生成继续放在这个回调中。`onGameLoad(boolean newGame)` 在新档和读档都会执行；需要跨读档保留的初始化放在它的 persistent data 门内。

```java
@Override
public void onNewGame() {
    log("onNewGame");
}

@Override
public void onGameLoad(boolean newGame) {
    SectorAPI sector = Global.getSector();
    if (!sector.getPersistentData().containsKey(CLASS_MARK)) {
        sector.getPersistentData().put(CLASS_MARK, true);
        SharedData.getData().getPersonBountyEventData().addParticipatingFaction("gear_industry");
        log("first-entry initialization (CLASS_MARK gate opened)");
    }
    log("onGameLoad newGame=" + newGame);
}
```

`CLASS_MARK` 随存档保存。标记不存在时执行一次初始化并写入标记；读档再次进入时直接跳过初始化。

### 处理启用和保存

`onEnabled(boolean wasEnabledBefore)` 只在 MOD 加入当前存档的启用列表时执行；新档会传入 `false`，中途启用已有存档时参数表示该存档历史上是否启用过本 MOD。保存时，`beforeGameSave()` 在写存档前执行，成功后调用 `afterGameSave()`；保存失败时由 `onGameSaveFailed()` 报告。

```java
@Override
public void onEnabled(boolean wasEnabledBefore) {
    log("onEnabled wasEnabledBefore=" + wasEnabledBefore);
}

@Override
public void beforeGameSave() {
    log("beforeGameSave");
}

@Override
public void afterGameSave() {
    log("afterGameSave");
}
```

## 完整最小例

文件路径为 `src/data/scripts/gearin/GearInModPlugin.java`，编译产物为 `jars/GearIndustry.jar`。

::: details 完整成品：GearInModPlugin

```java
package data.scripts.gearin;

import com.fs.starfarer.api.BaseModPlugin;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.SectorAPI;
import com.fs.starfarer.api.impl.campaign.shared.SharedData;

/**
 * 完整最小例：ModPlugin 生命周期、注册与存档入口。
 * mod_info.json 的 modPlugin 字段指向本类。
 * 每个回调打印一行日志；"首次进入存档" 的一次性初始化用 sector persistent data 门控 (CLASS_MARK 模式)。
 */
public class GearInModPlugin extends BaseModPlugin {

    /** 存档持久化标记：本 MOD 是否已完成首次进入初始化 (随存档保存)。 */
    public static final String CLASS_MARK = "GearInModPlugin";

    @Override
    public void onApplicationLoad() throws Exception {
        String version = Global.getSettings().getGameVersion();
        if (!version.startsWith("0.98a")) {
            throw new IllegalArgumentException("齿轮工业要求游戏版本 0.98a，当前为 [" + version + "]");
        }
        log("onApplicationLoad");
    }

    @Override
    public void onEnabled(boolean wasEnabledBefore) {
        log("onEnabled wasEnabledBefore=" + wasEnabledBefore);
    }

    @Override
    public void onNewGame() {
        log("onNewGame");
    }

    @Override
    public void onGameLoad(boolean newGame) {
        SectorAPI sector = Global.getSector();
        if (!sector.getPersistentData().containsKey(CLASS_MARK)) {
            sector.getPersistentData().put(CLASS_MARK, true);
            SharedData.getData().getPersonBountyEventData().addParticipatingFaction("gear_industry");
            log("first-entry initialization (CLASS_MARK gate opened)");
        }
        log("onGameLoad newGame=" + newGame);
    }

    @Override
    public void beforeGameSave() {
        log("beforeGameSave");
    }

    @Override
    public void afterGameSave() {
        log("afterGameSave");
    }

    private static void log(String message) {
        Global.getLogger(GearInModPlugin.class).info("GearIn lifecycle: " + message);
    }
}
```

:::

## 验证方案

前置数据：已按 [C6-S1] 至 [C6-S4] 配置 Java 17、`src/`、Gradle 和 JAR；`mod_info.json` 已声明 `jars` 与 `modPlugin`。

1. 在 MOD 根目录执行 `gradle jar`，并用 `jar tf jars/GearIndustry.jar` 确认存在 `data/scripts/gearin/GearInModPlugin.class`。
2. 启动游戏并查看 `starsector.log`，确认出现 `GearIn lifecycle: onApplicationLoad` 且没有入口类加载错误。
3. 新建生涯，确认日志依次出现 `onNewGame`、`onEnabled wasEnabledBefore=false`、首次初始化、`onGameLoad newGame=true`，随后是保存回调。
4. 读档并手动保存，确认只出现 `onGameLoad newGame=false` 和保存回调，首次初始化日志不再出现。

预期结果：入口类成功加载；新档初始化执行一次；读档不会重复初始化；保存成功时出现 `beforeGameSave` 和 `afterGameSave`。

## 故障分析

- **启动时报 `Error loading [data.scripts.gearin.GearInModPlugin]`**：`modPlugin`、包名、类名或 JAR 条目不一致。

    - **解决方案**：用 `jar tf` 核对 class 路径，再逐字核对 `modPlugin`、`package` 和类名。

- **启动时报 `ClassCastException`**：入口类没有继承 `BaseModPlugin`。

    - **解决方案**：让入口类继承 `BaseModPlugin`，重新构建 JAR。

- **`onApplicationLoad()` 抛出 `NullPointerException`**：启动阶段访问了尚未建立的 `Global.getSector()`。

    - **解决方案**：把生涯对象访问移到 `onNewGame()` 或 `onGameLoad()`。

- **每次读档都出现首次初始化日志**：初始化没有使用 persistent data 标记。

    - **解决方案**：用 `CLASS_MARK` 判断并在首次执行后写入 `getPersistentData()`。

- **游戏启动正常但没有 `GearIn lifecycle:` 日志**：`modPlugin` 没有声明有效的完整类名。

    - **解决方案**：补齐 `mod_info.json` 的 `modPlugin` 值，重新构建并重启。

## 总结

`mod_info.json` 用完整类名声明入口，`BaseModPlugin` 让类只覆写需要的回调。启动检查放在 `onApplicationLoad()`，新档专属操作放在 `onNewGame()`，新档和读档共有的一次性操作放在带 persistent data 门的 `onGameLoad()`，保存状态由保存回调记录。日志、JAR 条目和新档/读档结果共同确认入口已经接入。

## 建议参考项

- `mod_info.json`：`modPlugin` 和 `jars` 声明；
- `com.fs.starfarer.api.BaseModPlugin`：入口类的默认回调实现；
- `com.fs.starfarer.api.ModPlugin`：核心生命周期回调签名；
- `com.fs.starfarer.api.campaign.SectorAPI`：`getPersistentData()` 存档持久数据；
- `starsector-core/starsector.log`：入口加载和回调日志。
