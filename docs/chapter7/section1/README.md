# 制作 MOD 入口类

MOD 入口接收游戏加载、新档、读档与保存时的回调。代码放在哪个方法里，决定它何时执行，也决定它能使用哪些游戏对象。本节整理 `GearInModPlugin`，保留已有星系、关系和声音，在这些回调中安排一次性初始化与阶段日志。

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

## 让游戏加载入口类

入口仍使用 `GearInModPlugin`，源码保存在 `src/data/scripts/gearin/GearInModPlugin.java`。构建之后，游戏通过 `mod_info.json` 的 `modPlugin` 字段找到它。已有 MOD 信息保留实际内容，加载声明采用下面的关系：

```json
{
    "id": "gear_in",
    "name": "齿轮工业",
    "author": "YourName",
    "version": {"major": 0, "minor": 1, "patch": 0},
    "description": "Starsector MOD 教程的练习项目。",
    "gameVersion": "0.98.5a-RC3",
    "jars": ["jars/GearIndustry.jar"],
    "modPlugin": "data.scripts.gearin.GearInModPlugin"
}
```

**`jars` 提供包含代码的文件，`modPlugin` 指定其中的入口类。** 填写实际作者、版本等信息，保留已经登记的数据。游戏取得这个类后，将生命周期回调交给它。

入口继承 `BaseModPlugin`。基类已经实现未使用的方法，我们只覆写本节需要的回调。接下来按游戏实际运行的过程安排代码；完整类在后面集中给出。

## 应用加载后读取配置

`onApplicationLoad()` 在核心加载与脚本编译完成后执行，这时可以使用设置服务。将下面的局部方法放入入口类，记录这次回调与游戏版本：

```java
@Override
public void onApplicationLoad() throws Exception {
    log("onApplicationLoad version=" + Global.getSettings().getGameVersion());
}
```

这条日志用于确认回调已经发生。MOD 信息中的 `gameVersion` 与工程编译目标仍按第六章配置；这里读取设置，不改变它们。

应用加载时还没有当前生涯。星系、势力关系和存档数据要等新档创建或读档完成后处理，因此生成器也不能放在这个方法里。

## 新档生成与读档分别处理

创建新的生涯时，`onNewGame()` 负责生成本 MOD 的星系并设置初始关系。这里沿用第三章的生成器和关系方法：

```java
@Override
public void onNewGame() {
    new GearInSystemGenerator().generate(Global.getSector());
    seedRelations(Global.getSector());
    log("onNewGame");
}
```

星系与关系随后进入存档，读档时继续使用保存的结果。若把生成调用放在每次读档都会执行的方法里，同一个世界便会反复收到创建请求。

`onGameLoad(boolean newGame)` 在新档和读档时都会执行，`newGame` 表示本次是否为新档。进入生涯的声音、需要重新取得的对象，以及每次加载都要登记的运行期功能，都可以在这里处理。**新档生成使用 `onNewGame()`，每次进入存档的处理使用 `onGameLoad(...)`。**

## 为每个存档记录初始化结果

还有一种操作：它需要在当前存档中执行一次，之后读档继续保留结果。本例让齿轮工业参与人物赏金事件，将以下局部片段放入 `onGameLoad(...)`：

```java
SectorAPI sector = Global.getSector();
if (!sector.getPersistentData().containsKey(CLASS_MARK)) {
    SharedData.getData().getPersonBountyEventData().addParticipatingFaction("gear_industry");
    sector.getPersistentData().put(CLASS_MARK, true);
    log("first-entry initialization (CLASS_MARK gate opened)");
}
```

`CLASS_MARK` 的值为 `GearInModPlugin`。首次进入时，persistent data 中没有这个键，于是加入参与势力，再写入完成标记。标记随存档保存，后续加载同一存档时就会跳过初始化。

这里保存的是某个生涯的完成状态，因此使用 `SectorAPI.getPersistentData()`。类的 `static` 字段跟随游戏进程：同一进程可以先后加载不同存档，重新启动又会重新建立静态状态。**每存档一次的条件，要由该存档保存。**

第五章的 UI 反馈仍在每次 `onGameLoad(...)` 中播放。把它放在标记判断之外，首次进入与后续读档都能听到；初始化则继续只执行一次。两项功能在同一个回调里，却有不同的执行条件。

## 接收启用与保存通知

`onEnabled(boolean wasEnabledBefore)` 用于新档或首次向当前存档启用 MOD 的处理，参数说明这个存档以前是否曾启用过本 MOD。已有启用状态的正常读档继续使用 `onGameLoad(...)`。完整例在启用回调中记录参数，以便与读档日志对应。

保存过程则有自己的通知：写入前执行 `beforeGameSave()`，成功后执行 `afterGameSave()`，失败时执行 `onGameSaveFailed()`。加入以下局部方法，记录保存前后的调用：

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

失败回调也保留相同的日志写法。日志用于确认实际调用，初始化标记用于控制功能是否执行；保存通知本身不会替代这个标记。后续入口增加内容时，仍按各项操作所需的对象与时机安排。

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

同一个入口现在接收应用、新档、读档、启用与保存通知。已有星系只在新档生成，UI 反馈随每次进入播放，赏金参与状态则由存档标记控制。回调决定调用时机，功能自己的状态决定本次要不要执行。

后面的生涯功能仍从这个入口接入。生成过程继续交给生成器，入口负责在合适的阶段调用它，日志记录实际发生的过程。

## 建议参考项

- `mod_info.json`：modPlugin 与 jars。
- `com.fs.starfarer.api.BaseModPlugin`：默认入口实现。
- `com.fs.starfarer.api.ModPlugin`：生命周期签名与调用约定。
- `com.fs.starfarer.api.campaign.SectorAPI`：getPersistentData 存档数据。
- `com.fs.starfarer.api.impl.campaign.shared.SharedData`：人物赏金参与势力。
- `Starsector/starsector-core/starsector.log`：入口和回调结果。
