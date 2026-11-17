# 制作 MOD 入口类

游戏加载一个 MOD 的代码，起点是 `mod_info.json` 里的 `modPlugin` 类名。游戏在启动时把这个类实例化一次，随后在启动、开新档、读档、存盘等节点依次调用它上面的回调方法。MOD 与游戏生命周期之间的接口全在这一张回调表上，没挂进这张表的代码，游戏不会主动去调。

这也让入口类有了一层微妙的双重身份：它是整个 MOD 里唯一不必操心 "怎么被触发" 的类，但它的实例跟着游戏进程走，而玩家在一次进程里随时可能切换不同的存档。

---

_本节预期阅读时间：\~12 分钟。_

_本节预期操作时间：\~10 分钟。_

## 前置知识

- [C3-S3] 的 `GearInModPlugin` 与星系生成器；
- [C6-S2] 的源码目录与构建流程；
- Java 的继承与方法覆写。

## 入口类的实例只有一份

`mod_info.json` 的 `modPlugin` 指向入口类。它必须实现 `com.fs.starfarer.api.ModPlugin`，接口的全部方法都是游戏会调用的回调。

直接实现 `ModPlugin` 接口得把所有回调方法写全，所以实战中一律继承 `BaseModPlugin`：它把每个回调都给了空实现，按需覆写即可。**其中 `pick...` 系列默认返回 `null`，含义是交给原版处理**；等到 [C18-S2] 用 `pickMissileAI(...)` 给德瓦林指定导弹 AI 时才会覆写它，其余保持默认。

入口类的实例在游戏启动时创建一次。玩家退出到主菜单再读另一份存档，游戏进程没有退出，实例没有重建，字段里仍是上一份存档留下的值。**实例在游戏进程里创建一次；存档是一份一份的，每次进入生涯都对应其中一份。实例与存档的存续期不重合。** 凡是属于某份存档的东西，都不能放在入口类的字段里。

## 每次启动只跑一次的回调

`onApplicationLoad()` 在全部核心数据加载完、全部脚本编译完之后调用，每次启动游戏一次。

此时还没有生涯。可以读取设置、贴图、CSV 与 JSON，不能访问 `Global.getSector()` —— 那里还是空的，等它被填上要等到新档生成或存档载入。**在 `onApplicationLoad()` 里碰 `Global.getSector()` 得到的是一串空指针**，而这个回调只跑一次，出错的位置看起来离原因很远。

下一节读取 MOD 参数就在这个回调里。

## 开新档与进入生涯

这两个词在 MOD 开发里经常被混着说，回调用起来却是分开的。

`onNewGame()` 只在创建新生涯时调用，星区内容在这里生成，取代旧的 `generators.csv`。[C3-S3] 的星系生成与初始关系都在这里。

`onGameLoad(boolean newGame)` 每次进入生涯都调用，新档也走一遍，参数说明这一次是不是新开的档。

**两者的边界很清楚：`onNewGame()` 里的代码，一份存档从头到尾只跑一次；`onGameLoad(...)` 里的代码，每次读这份档都会跑一遍。** 把逻辑放错了回调，就会制造出 "每次读档都多执行一遍" 或 "旧存档永远漏掉这一步" 两类问题，而且都不会当场报错。

`onEnabled(boolean wasEnabledBefore)` 在 `onGameLoad(...)` 之前，只在本 MOD 第一次出现在这份存档里时触发。参数为 `true` 表示这份存档此前启用过本 MOD，中途关掉又在恢复 —— 此时并不是第一次。

## 中途加入的 MOD 拿不到新档

用户把齿轮工业加进一份已经玩了几十小时的存档，游戏调用 `onEnabled(false)` 与 `onGameLoad(false)`，不调用 `onNewGame()`。Nipher 星系不会出现，而星系生成器里那一堆市场登记、势力关系、巡逻投放全都跟着没发生。

从技术上讲，可以在 `onEnabled(...)` 里补一次生成。这份存档的经济已经按几十小时的步长跑过，势力关系、舰队编制都定了型，中途加入的星系要补齐的东西不止一个生成器：市场得进经济系统并重新结算，关系得和既有的局面接上，巡逻队得投到一张已经运转的地图里。**补不全是常态，补坏的地方要在玩家几十小时之后才暴露。**

增加星系的 MOD 应当直接要求开新档，并在 MOD 说明的第一屏写明这一条。想试用的用户因此要重开一档，代价明确，换来的是每一份存档的世界都按同一个过程长出来。

## 更新时补上一次性初始化

要求开新档，不等于旧存档永远停在旧版本。MOD 更新常常加入只需执行一次的设置，而在更新之前开出的存档也应当拿到它。

以人物赏金为例。原版的人物赏金只由名单中的势力发布，`PersonBountyEventData` 在构造时写死了那几个势力 ID。齿轮工业要进这份名单，而名单属于存档数据 —— `onNewGame()` 对旧存档不再调用，所以这段放在每次都会执行的 `onGameLoad(...)` 里，用一个存档标记控制只执行一次：

```java
Map<String, Object> data = Global.getSector().getPersistentData();
if (!data.containsKey(BOUNTY_JOINED_KEY)) {
    SharedData.getData().getPersonBountyEventData().addParticipatingFaction(FACTION_ID);
    data.put(BOUNTY_JOINED_KEY, true);
}
```

`getPersistentData()` 是当前存档的公共数据表，写入的值随存档保存、随读档恢复。第一次进入这份存档时表里没有这个键，于是加入名单并写下标记；此后读同一份存档，判断直接跳过。新档从空表开始，走的也是同一条路。

**"每份存档执行一次" 这个要求，只能由存档里的东西来满足。** 换成入口类的字段，那字段属于游戏进程，读过 A 档再读 B 档，B 档会被当成已经初始化过；换成 `static` 字段，情况相同且更难看出来。

标记也不能省。`addParticipatingFaction(...)` 只是往名单末尾追加，不检查重复；去掉判断，每读一次档齿轮工业就在名单里多一条，赏金发布概率随读档次数一起升高。这类错误不会报错，只会在几十小时之后表现为某个势力发布赏金过于频繁。

::: tip 标记键的命名

存档公共数据表由原版与全部 MOD 共用，键名同样要带 `GearIn_` 前缀。希望您还没忘。

:::

## 保存前后的三个回调

`beforeGameSave()` 在写盘之前，`afterGameSave()` 在成功之后，失败走 `onGameSaveFailed()`。

它们的用途基本只有一个：把不能写进存档的对象在写盘前摘出去，写完之后放回。齿轮工业没有这类对象，入口类不覆写这三个。

## 完整入口类

修改 `src/data/scripts/gearin/GearInModPlugin.java`。星系生成器沿用 [C4-S6] 的版本，不需要改动：

```java
package data.scripts.gearin;

import java.util.Map;

import com.fs.starfarer.api.BaseModPlugin;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.RepLevel;
import com.fs.starfarer.api.campaign.SectorAPI;
import com.fs.starfarer.api.impl.campaign.shared.SharedData;

import data.scripts.gearin.campaign.GearInSystemGenerator;

/** MOD 入口：新档生成星系与关系，进入生涯时为存档补上一次性初始化。 */
public class GearInModPlugin extends BaseModPlugin {

    public static final String FACTION_ID = "gear_industry";

    /** 存档公共数据中记录 "已加入人物赏金名单" 的键。 */
    private static final String BOUNTY_JOINED_KEY = "GearIn_bounty_joined";

    @Override
    public void onNewGame() {
        SectorAPI sector = Global.getSector();
        new GearInSystemGenerator().generate(sector);
        seedRelations(sector);
    }

    @Override
    public void onGameLoad(boolean newGame) {
        Map<String, Object> data = Global.getSector().getPersistentData();
        if (!data.containsKey(BOUNTY_JOINED_KEY)) {
            SharedData.getData().getPersonBountyEventData().addParticipatingFaction(FACTION_ID);
            data.put(BOUNTY_JOINED_KEY, true);
        }
    }

    private void seedRelations(SectorAPI sector) {
        FactionAPI gear = sector.getFaction(FACTION_ID);
        gear.setRelationship("player", 0f);
        gear.setRelationship("pirates", RepLevel.HOSTILE);
        gear.setRelationship("hegemony", RepLevel.FRIENDLY);
        gear.setRelationship("tritachyon", RepLevel.HOSTILE);
    }
}
```

势力 ID 提成 `FACTION_ID` 常量。此后其他类引用齿轮工业时都从这里取，不在各处散写字符串。

### 进游戏核对

执行 `gradle jar`，重启游戏。

- 开一个新档，Nipher 星系与初始关系照常出现。
- 在赏金界面能看到的条目取决于随机发布，短期内未必轮到齿轮工业，这一项留到后续游玩中留意。
- 保存，退出到主菜单，读回这份存档，重复几次。标记是否真的拦住了重复追加，界面上看不出来，要靠下一条。

在 `onGameLoad(...)` 末尾临时加一行，把名单打进日志：

```java
Global.getLogger(GearInModPlugin.class).info("GearIn bounty factions: "
        + SharedData.getData().getPersonBountyEventData().getParticipatingFactions());
```

读档几次后打开 `starsector.log` 搜索 `GearIn bounty factions`，每一行里 `gear_industry` 都应当只出现一次。核对完删掉这一行。日志的写法在 [C7-S4] 展开。

## 常见问题

- **启动时报 `Error loading [data.scripts.gearin.GearInModPlugin]`**：JAR 里没有这个类，或 `modPlugin` 写的类名与 `package` 对不上。
    - **解决方案**：`jar tf` 看 JAR 内容，核对 `mod_info.json` 里的完整类名。

- **已有存档里找不到 Nipher 星系**：存档在本 MOD 加入之前创建，`onNewGame()` 没有对它调用过。
    - **解决方案**：开新档测试。

- **`onApplicationLoad()` 里抛 `NullPointerException`**：这个回调访问了 `Global.getSector()`，此时生涯还没有建立。
    - **解决方案**：把访问生涯的代码移到 `onNewGame()` 或 `onGameLoad(...)`。

- **每读一次档，某件事就重复一次**：放在了 `onGameLoad(...)` 里却没有存档标记。
    - **解决方案**：改用存档公共数据里的键做判断。

## 建议参考项

- `com.fs.starfarer.api.ModPlugin`：全部回调的签名，入口类实现的就是这个接口。
- `com.fs.starfarer.api.BaseModPlugin`：各回调的默认实现，`pick...` 系列一律返回 `null`。
- `com.fs.starfarer.api.campaign.SectorAPI`：`getPersistentData()` 存档公共数据。
- `com.fs.starfarer.api.impl.campaign.shared.PersonBountyEventData`：人物赏金的参与势力名单。
- `com.fs.starfarer.api.impl.campaign.CoreLifecyclePluginImpl`：原版自己的生命周期插件，可以对照它在各回调里做了什么。
