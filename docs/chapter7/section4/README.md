# 记录和定位 MOD 日志

MOD 在游戏里跑起来之后，除了日志没有任何地方能看到它做了什么。没有断点，没有控制台里的返回值，界面上也看不出来某个方法到底执行过几次。`starsector.log` 是唯一的观察窗口，读它和写它都属于这一节的活。

读的能力决定能不能修好别人报上来的问题；写的能力决定下一次出问题时，日志里剩下的是线索还是空白。

---

_本节预期阅读时间：\~12 分钟。_

_本节预期操作时间：\~10 分钟。_

## 前置知识

- [C7-S1] 的回调时机；
- [C7-S2] 的 `GearInSettings`；
- Java 异常与调用栈的基本概念。

## 日志在哪里

游戏的日志写入 `Starsector/starsector-core/starsector.log`。**这个文件在多次启动之间持续追加，最新的记录在文件末尾。** 打开后先跳到末尾，再向上查找，而不是从头读起；从头读起，看到的往往是几天前另一次启动留下的错误。

文件变得过大时可以直接删除，游戏下次启动会重新创建。排查一个问题之前删掉它，再复现一次，日志里就只剩这一次的记录。

## 读懂一份报错

我们先故意制造一次崩溃，看看报错在日志里长什么样。打开 [C7-S2] 的 `GearInSettings.java`，把读取原版 `"economyIterPerMonth"` 的字符串故意少写一个字母，改成 `"economyIterPerMoth"`，执行 `gradle jar` 后启动游戏。游戏在加载阶段停下，日志末尾出现类似下面的记录：

```text
java.lang.RuntimeException: org.json.JSONException: JSONObject["economyIterPerMoth"] not found.
	at com.fs.starfarer.settings.StarfarerSettings.findFloatSetting(Unknown Source)
	at com.fs.starfarer.settings.StarfarerSettings$1.getFloat(Unknown Source)
	at data.scripts.gearin.GearInSettings.load(GearInSettings.java:32)
	at data.scripts.gearin.GearInModPlugin.onApplicationLoad(GearInModPlugin.java:28)
	...
```

`RuntimeException` 后面那半句是 `JSONException` 的原话，格式固定为 `JSONObject["键名"] not found.`。这条消息本身就把答案给全了：哪个键、不存在。中间两行 `com.fs.starfarer.settings` 属于游戏本体，类名被混淆成单个字母，行号也显示为 `Unknown Source`，它们说明异常是从哪条内部路径抛出来的，不必深究。

**从上往下找第一个带自己包名的帧。** 这里是 `GearInSettings.load(GearInSettings.java:32)`，源文件第 32 行，也就是读取那个键的那一行。它上面的一行 `GearInModPlugin.onApplicationLoad(...)` 告诉我们是谁调用了它，两者合在一起就是完整的因果链：应用加载 → 读取设置 → 键不存在 → 抛出。

**`Caused by:` 段落给的是被包装的原始异常。** 上面这条把 `JSONException` 包进了 `RuntimeException`，外层消息已经带出了原话；遇到外层只写一句笼统的 "invocation failed" 时，往下翻到最后一个 `Caused by:`，那里才是第一现场。

**一份日志里出现多个异常时，从时间上最早的那个看起。** 第一个错误往往会顺着调用链牵出一串后续报错，把第一个修掉，后面的通常一起消失。

看明白之后，把 `GearInSettings.java` 里改错的键名恢复为 `"economyIterPerMonth"`。

### 空指针为什么指错行

`getFaction("gear_industy")` 少一个字母，这一次调用不会报错，它安静地返回 `null`。异常要到下一次拿这个对象取东西时才出现，而那一行往往离写错的那个字符串很远。

Java 17 的说明会指出是哪一次取值遇到了 `null`，格式是 "对 X 调用 Y，而 X 是 null"：

```text
java.lang.NullPointerException: Cannot invoke "com.fs.starfarer.api.campaign.FactionAPI.getId()" because the return value of "com.fs.starfarer.api.campaign.SectorAPI.getFaction(String)" is null
```

它指明的是取 `getId()` 这一处，而键的错误在更早的 `getFaction(...)` 里。**看到这种消息，往回找那个查询，检查它传进去的字符串。** 本节的示范故意选设置键出错，因为键名与异常消息里出现的名字是同一个，不像 ID 查询那样隔了一层。

## 为 MOD 写入日志

日志不只用于读取。在代码的关键位置写入记录，出问题时就能知道代码执行到了哪里、读到了什么值。

`Global.getLogger(Class)` 返回该类对应的 Log4j 日志器，输出写入同一个 `starsector.log`，每行带有类名。在 [C7-S2] 的 `GearInSettings` 中加上 `LOG` 字段，并在读取结束后把校验到的贴图尺寸与全局配置记一行：

```java
package data.scripts.gearin;

import java.awt.Color;

import org.apache.log4j.Logger;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.SettingsAPI;
import com.fs.starfarer.api.graphics.SpriteAPI;

/** 齿轮工业的全局设置与预加载资源入口，在应用加载时初始化并记录日志。 */
public final class GearInSettings {

    private static final Logger LOG = Global.getLogger(GearInSettings.class);

    public static final String DESIGN_TYPE = "齿轮工业";
    public static final String FX_CATEGORY = "fx";
    public static final String HEAT_GLOW_KEY = "GearIn_heat_glow";
    public static final String INTEL_CATEGORY = "intel";
    public static final String FLEET_ICON_KEY = "GearIn_fleet_icon";
    public static final String SALVAGE_BONUS_XP_KEY = "GearIn_salvage";

    private static Color designTypeColor;
    private static String fleetIconName;
    private static float salvageBonusXp;
    private static int economyIterPerMonth;
    private static int maxShipsInFleet;

    private GearInSettings() {
    }

    public static void load() {
        SettingsAPI settings = Global.getSettings();
        designTypeColor = settings.getDesignTypeColor(DESIGN_TYPE);
        fleetIconName = settings.getSpriteName(INTEL_CATEGORY, FLEET_ICON_KEY);
        salvageBonusXp = settings.getBonusXP(SALVAGE_BONUS_XP_KEY);
        economyIterPerMonth = (int) settings.getFloat("economyIterPerMonth");
        maxShipsInFleet = settings.getInt("maxShipsInFleet");
        SpriteAPI glow = createHeatGlowSprite();
        LOG.info("GearIn settings loaded: glow=" + (int) glow.getWidth() + "x" + (int) glow.getHeight()
                + ", icon=" + fleetIconName + ", salvageBonusXP=" + salvageBonusXp
                + ", economyIterPerMonth=" + economyIterPerMonth + ", maxShips=" + maxShipsInFleet);
    }

    public static SpriteAPI createHeatGlowSprite() {
        return Global.getSettings().getSprite(FX_CATEGORY, HEAT_GLOW_KEY);
    }

    public static Color getDesignTypeColor() {
        return designTypeColor;
    }

    public static String getFleetIconName() {
        return fleetIconName;
    }

    public static float getSalvageBonusXp() {
        return salvageBonusXp;
    }

    public static int getEconomyIterPerMonth() {
        return economyIterPerMonth;
    }

    public static int getMaxShipsInFleet() {
        return maxShipsInFleet;
    }
}
```

日志器保存在 `static final` 字段中，每个类取得一次。这一行的价值在于：当启动阶段登记的资源或配置有变动时，日志中直接写着游戏实际读到的贴图尺寸、路径与各项数值，不必再去猜是文件没保存、键名写错，还是改错了目录。

### 选择级别

| 级别    | 用途                                               |
| ------- | -------------------------------------------------- |
| `info`  | 正常流程中值得留下的结果，例如加载完成、读到的参数 |
| `warn`  | 流程可以继续，但状态不符合预期                     |
| `error` | 当前操作失败，通常同时传入异常对象                 |

记录异常时，把异常对象作为第二个参数传入：

```java
LOG.error("Failed to load patrol config", exception);
```

这样日志中会保留完整的调用栈。只写 `exception.getMessage()`，得到的是一句缺少位置的说明，前面读报错时用到的 `at` 行全部丢失。

**日志写给将来排查问题的人看，每一行都应当能独立说明发生了什么。** 只写一个数字，或者只写 "here"，过几周连作者自己也认不出它来自哪里。同时，每帧执行的代码中不应写入日志：一场战斗每秒数十帧，几分钟就能写出数万行，既拖慢游戏，也淹没了真正有用的记录。

### 进游戏核对

执行 `gradle jar`，重启游戏。在 `starsector.log` 末尾向上搜索 `GearIn settings loaded`，应看到一行：

```text
INFO  data.scripts.gearin.GearInSettings  - GearIn settings loaded: glow=64x64, icon=graphics/factions/crest_gear_industry.png, salvageBonusXP=0.5, economyIterPerMonth=10, maxShips=30
```

行首的时间与线程名以实际日志为准。把 `settings.json` 中的 `GearIn_salvage` 改掉再重启，这一行随之变化。

## 常见问题

- **日志中找不到自己写入的记录**：游戏使用的仍是旧的 JAR，或者查看的是另一个游戏目录下的日志。
    - **解决方案**：重新执行 `gradle jar`，确认 `starsector.log` 位于当前启用的游戏的 `starsector-core` 中。

- **调用栈中没有自己包名的帧**：异常发生在游戏本体内部，通常由数据文件引起。
    - **解决方案**：查看异常说明与 `Caused by:` 中提到的文件名或 ID，检查对应的数据文件。

- **游戏没有弹出错误就直接退出，日志末尾也没有异常**：通常是内存不足或显卡驱动问题，与 MOD 代码无关的可能性较大。
    - **解决方案**：先在不启用本 MOD 的情况下复现，确认是否为 MOD 引起。

## 建议参考项

- `starsector.log`：游戏日志，按启动追加。
- `com.fs.starfarer.api.Global`：`getLogger(Class)` 取得日志器。
- `org.apache.log4j.Logger`：`info`、`warn`、`error` 方法，异常对象作为第二个参数传入。
