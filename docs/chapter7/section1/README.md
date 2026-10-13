# 制作 MOD 入口类

入口类已经负责新档生成、关系和进入生涯的 UI 反馈。本节在同一个 GearInModPlugin 中加入应用加载、启用、读档与保存的处理，并用存档持久数据记录一次性初始化。完整类继续使用前文生成器与第四章的数据类型，按生命周期安排各项职责。

*本节预期阅读时间：\~10 分钟。*

*本节预期操作时间：\~15 分钟。*

## 学习目标

完成后，您将能够：

- 在 JAR 和 mod_info 中提供有效入口；
- 按应用、新档、读档、启用与保存阶段选择回调；
- 用 persistent data 记录每个存档的初始化；
- 通过日志和保存／读取结果核对调用。

## 前置知识

- [C3-S3] 的入口与势力关系；
- [C3-S4] 的 GearInSystemGenerator；
- [C5-S3] 的 UI 反馈；
- [C6-S2] 至 [C6-S4] 的源码目录、构建与加载；
- Java 继承、方法覆写、Map 与字符串。

## 保持入口注册与已有成果

入口源码保存为 `src/data/scripts/gearin/GearInModPlugin.java`，mod_info 保留实际 JAR 和完整类名：

```json
{
    "id": "gear_in",
    "name": "齿轮工业",
    "author": "YourName",
    "version": {"major": 0, "minor": 1, "patch": 0},
    "description": "Starsector MOD 教程的练习项目。",
    "gameVersion": "0.98a-RC8",
    "jars": ["jars/GearIndustry.jar"],
    "modPlugin": "data.scripts.gearin.GearInModPlugin"
}
```

填写实际信息并保留已登记内容。**JAR 提供类，modPlugin 选择入口**，两个引用要与当前产物对应。

BaseModPlugin 为未使用的方法提供默认实现。本节覆写实际需要的回调，其他生涯和 AI 入口以后再按具体任务添加。

## 在应用加载时读取设置

`onApplicationLoad()` 在核心加载与脚本编译完成后调用，适合读取本 MOD 配置。当前记录游戏版本，局部片段如下：

```java
@Override
public void onApplicationLoad() throws Exception {
    log("onApplicationLoad version=" + Global.getSettings().getGameVersion());
}
```

设置服务已可读取，生涯对象则需要等创建或读档完成。配置声明的 gameVersion 与构建目标继续承担版本约定；本节日志用于观察当前实际环境。

## 区分新档生成与每次进入

`onNewGame()` 创建本 MOD 的星系和初始关系。继续保留第三章的调用：

```java
@Override
public void onNewGame() {
    new GearInSystemGenerator().generate(Global.getSector());
    seedRelations(Global.getSector());
    log("onNewGame");
}
```

这是新生涯专用操作，读取存档使用已保存的对象。`onGameLoad(boolean newGame)` 在新档和读档都执行，参数说明当前是否为新档。

**新档内容与读档共有操作要按用途安排。** 把星系生成放进每次读档回调会重复创建对象，而只在新档回调记录需要重复读取的状态，也可能遗漏已有存档的处理。

## 给存档初始化留一个标记

本例在首次进入该存档时，让齿轮工业参与人物赏金事件，并把标记写入 persistent data：

```java
SectorAPI sector = Global.getSector();
if (!sector.getPersistentData().containsKey(CLASS_MARK)) {
    SharedData.getData().getPersonBountyEventData().addParticipatingFaction("gear_industry");
    sector.getPersistentData().put(CLASS_MARK, true);
    log("first-entry initialization (CLASS_MARK gate opened)");
}
```

CLASS_MARK 对应 GearInModPlugin 这一键。标记随存档保存，之后读取该存档时可直接判断这项初始化已经完成。本例先执行初始化，再写入完成标记。

**状态的保存范围决定标记放在哪里。** 需要随当前生涯保存的数据使用 SectorAPI.getPersistentData，类的 static 字段保存的是进程级状态，两者服务的范围不同。

当前 UI 反馈继续放进 onGameLoad，每次进入生涯播放一次。声音资源和 ID 沿用第五章，完整代码在后面给出。

## 处理启用与保存

onEnabled 在新档或首次向当前存档启用 MOD 时调用。wasEnabledBefore 表示该存档以前是否曾启用过本 MOD；已有启用状态的正常读档采用 onGameLoad。

保存前调用 beforeGameSave，成功后调用 afterGameSave，失败时调用 onGameSaveFailed。当前都记录阶段日志，便于比较实际顺序：

```java
@Override
public void beforeGameSave() {
    log("beforeGameSave");
}

@Override
public void afterGameSave() {
    log("afterGameSave");
}
```

编者建议先把需要处理的状态与调用时机写清楚。入口类会随开发增加内容，按阶段分配职责，比在一个回调里完成所有操作更容易检查。

## 完整最小例

保存入口类并继续使用 [C3-S4] 的生成器源码。src 中的两个包路径参与同一个 MOD 构建，关系设置与 UI 反馈由当前入口保留。

::: details 完整成品：GearInModPlugin

```java
package data.scripts.gearin;

import com.fs.starfarer.api.BaseModPlugin;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.RepLevel;
import com.fs.starfarer.api.campaign.SectorAPI;
import com.fs.starfarer.api.impl.campaign.shared.SharedData;

import data.scripts.gearin.campaign.GearInSystemGenerator;

/** 接收MOD生命周期回调，生成新档内容并记录存档初始化与保存。 */
public class GearInModPlugin extends BaseModPlugin {

    public static final String CLASS_MARK = "GearInModPlugin";

    @Override
    public void onApplicationLoad() throws Exception {
        log("onApplicationLoad version=" + Global.getSettings().getGameVersion());
    }

    @Override
    public void onEnabled(boolean wasEnabledBefore) {
        log("onEnabled wasEnabledBefore=" + wasEnabledBefore);
    }

    @Override
    public void onNewGame() {
        new GearInSystemGenerator().generate(Global.getSector());
        seedRelations(Global.getSector());
        log("onNewGame");
    }

    @Override
    public void onGameLoad(boolean newGame) {
        SectorAPI sector = Global.getSector();
        if (!sector.getPersistentData().containsKey(CLASS_MARK)) {
            SharedData.getData().getPersonBountyEventData().addParticipatingFaction("gear_industry");
            sector.getPersistentData().put(CLASS_MARK, true);
            log("first-entry initialization (CLASS_MARK gate opened)");
        }
        Global.getSoundPlayer().playUISound("GearIn_ui_feedback", 1f, 1f);
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

    @Override
    public void onGameSaveFailed() {
        log("onGameSaveFailed");
    }

    private void seedRelations(SectorAPI sector) {
        FactionAPI gear = sector.getFaction("gear_industry");
        gear.setRelationship("player", 0f);
        gear.setRelationship("pirates", RepLevel.HOSTILE);
        gear.setRelationship("hegemony", RepLevel.FRIENDLY);
        gear.setRelationship("tritachyon", RepLevel.HOSTILE);
    }

    private static void log(String message) {
        Global.getLogger(GearInModPlugin.class).info("GearIn lifecycle: " + message);
    }
}
```

:::

下面是当前生成器的完整形态，保存为 `src/data/scripts/gearin/campaign/GearInSystemGenerator.java`。它承接第三章的 Second 描述绑定和第四章的余烬世界、导航锚；对应数据继续使用前文已经登记的内容。

::: details 完整成品：GearInSystemGenerator

```java
package data.scripts.gearin.campaign;

import java.awt.Color;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.JumpPointAPI;
import com.fs.starfarer.api.campaign.PlanetAPI;
import com.fs.starfarer.api.campaign.SectorAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.SectorGeneratorPlugin;
import com.fs.starfarer.api.campaign.StarSystemAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;

/** 创建齿轮工业星系，使用自定义行星、导航锚与独立描述。 */
public class GearInSystemGenerator implements SectorGeneratorPlugin {

    @Override
    public void generate(SectorAPI sector) {
        StarSystemAPI system = sector.createStarSystem("GearIn");
        system.setBackgroundTextureFilename("graphics/backgrounds/background2.jpg");
        system.setLightColor(new Color(200, 200, 210));

        PlanetAPI star = system.initStar("gearin", "star_yellow", 600f, 550f, 3f, 0.75f, 1f);
        system.getLocation().set(-16000f, -9000f);

        PlanetAPI prime = system.addPlanet(
                "GearIn_planet1", star, "GearIn Prime", "GearIn_ember_world", 55, 150, 3200, 120);
        PlanetAPI second = system.addPlanet(
                "GearIn_planet2", star, "GearIn Second", "barren", 200, 90, 5200, 200);
        second.setCustomDescriptionId("GearIn_description_second");

        SectorEntityToken stable = system.addCustomEntity(
                "GearIn_stable", "GearIn 导航锚", "GearIn_navigation_anchor", "gear_industry");
        stable.setCircularOrbitPointingDown(star, 90, 2200, 100);

        MarketAPI market = Global.getFactory().createMarket(prime.getId(), prime.getName(), 4);
        market.setFactionId("gear_industry");
        market.setPrimaryEntity(prime);
        market.setSurveyLevel(MarketAPI.SurveyLevel.FULL);
        market.addCondition("population_4");
        market.addIndustry("population");
        market.addIndustry("spaceport");
        market.addSubmarket("open_market");
        market.addSubmarket("storage");
        sector.getEconomy().addMarket(market, true);
        prime.setMarket(market);
        prime.setFaction("gear_industry");

        JumpPointAPI jump = Global.getFactory().createJumpPoint("GearIn_jump", "GearIn 跳跃点");
        jump.setCircularOrbit(star, 300, 1800, 100);
        jump.setRelatedPlanet(prime);
        system.addEntity(jump);
        system.autogenerateHyperspaceJumpPoints(true, true);
    }
}
```

:::

## 验证方案

1. 构建 JAR，核对入口与生成器 class 条目及 mod_info 声明。
2. 启动游戏，查看 GearIn lifecycle 的应用加载日志和版本。
3. 创建生涯，核对 GearIn 星系、初始关系、onNewGame、启用、首次初始化与 onGameLoad newGame=true。
4. 保存后读取同一存档，核对 onGameLoad newGame=false 与 UI 反馈。
5. 手动保存，比较 beforeGameSave 和 afterGameSave 记录。

预期结果：

- 入口类有效，应用加载阶段记录实际版本。
- 新档创建星系和关系，首次初始化写入持久标记。
- 保存后的同一存档读取时继续使用标记，一次性初始化日志保持一次。
- 每次进入生涯播放 UI 反馈并记录当前 newGame 值。
- 保存成功记录前后回调，失败路径由 onGameSaveFailed 记录。

## 故障分析

- **入口类加载失败**：JAR 条目、package、class 或 modPlugin 不同。
    - **解决方案**：按完整类名核对产物与加载值，重新构建。

- **入口转换时报 ClassCastException**：登记的类没有实现 MOD 入口接口。
    - **解决方案**：使用 BaseModPlugin 或正确的 ModPlugin 实现，再构建。

- **应用加载时访问生涯抛空指针**：当前 Sector 尚未建立。
    - **解决方案**：将生涯操作安排到创建或读档回调。

- **每次读档重复初始化**：标记未写入存档数据，或当前读取的是不同存档。
    - **解决方案**：核对 CLASS_MARK 键和 getPersistentData，在同一保存档测试。

- **新档缺少原有星系**：替换入口时遗漏生成器调用。
    - **解决方案**：在 onNewGame 保留 generate 和关系设置，再创建生涯。

## 总结

入口登记和生命周期共同决定代码何时执行。应用加载读取配置，新档回调生成内容，读档回调处理存档状态，保存回调记录写入结果；persistent data 使一次性初始化与当前存档联系起来。

本节继续使用原来的星系、关系和 UI 反馈，再增加明确的阶段处理。后面的功能也按这套对象与时机关系接入入口，日志则用于核对真实调用。

## 建议参考项

- `mod_info.json`：modPlugin 与 jars。
- `com.fs.starfarer.api.BaseModPlugin`：默认入口实现。
- `com.fs.starfarer.api.ModPlugin`：生命周期签名与调用约定。
- `com.fs.starfarer.api.campaign.SectorAPI`：getPersistentData 存档数据。
- `com.fs.starfarer.api.impl.campaign.shared.SharedData`：人物赏金参与势力。
- `Starsector/starsector-core/starsector.log`：入口和回调结果。
