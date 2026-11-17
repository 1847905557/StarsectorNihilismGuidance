# 使用 Global 与读取设置

除了跟着单份存档走的数据之外，MOD 里还有另一大批东西与具体哪局生涯无关：代码里要绘制的特效贴图、设计类型的专属颜色、故事点返还的经验比例，以及原版自己在 `settings.json` 里定下的全局规则。

这些跨存档、伴随整个游戏进程的资源与全局配置，统一归 `Global.getSettings()` 管理。用好它之前，得先分清 `Global` 上各个入口在什么阶段才有值，以及 `settings.json` 里到底什么才值得放进去。

---

_本节预期阅读时间：\~12 分钟。_

_本节预期操作时间：\~15 分钟。_

## 前置知识

- [C7-S1] 的 `onApplicationLoad()` 调用时机；
- [C1-S1] 中同路径文件的合并规则；
- [C1-S2] 的 `ship_data.csv` 与 `tech/manufacturer` 列。

## Global 提供的是字段，不是查询

`Global` 上的取值方法本质上只是直接返回静态字段：`getSector()` 的方法体就是 `return sectorAPI;`，`getCombatEngine()` 就是 `return combatEngine;`。

里面没有判空，没有懒加载回退，字段为空时就原样返回 `null`。**但考虑到原版的游戏加载周期还算稳健，在绝大多数情况下您应该都能安全调用它们。**

| 方法 | 读的字段 | 何时为空 |
| --- | --- | --- |
| `getSector()` | `sectorAPI` | 尚未进入生涯，如 `onApplicationLoad()` |
| `getCombatEngine()` | `combatEngine` | 不在战斗中 |
| `getSettings()` | `settingsAPI` | 启动早期，数据尚未加载 |
| `getSoundPlayer()` | 音效播放器，见 [C7-S3] | 同上 |
| `getFactory()` | 创建生涯对象的工厂 | 同上 |
| `getLogger(...)` | Log4j 日志器 | 不会为空 |
| `getCurrentState()` | `TITLE`、`CAMPAIGN`、`COMBAT` | 不会为空 |

空值在这里属于正常状态：字段还没被赋值。真正的错误发生在下一步，拿着空值去取成员，报错点离原因隔了好几层。**在 `onApplicationLoad()` 里调 `getSector()` 再取任何东西，那一行就是空指针。**

如果回调方法本身的签名里已经把对象作为参数传了进来 (例如命中效果 `onHit(...)` 自带的 `engine`)，直接用参数即可，不必再去调一次 `Global.getCombatEngine()`。参数既然出现在签名里，就代表调用方已经准备好了当前上下文的实例；`Global` 留给那些拿不到上下文参数的工具方法或深层调用。同时绝不要把 `CombatEngineAPI` 等战场对象顺手存进 `static` 字段、入口类成员或生涯对象里：一场战斗结束后引擎本该释放回收，只要有一处跨作用域引用泄露到生涯层，整场战斗上下文就会一路占着内存直到拖垮游戏。

## settings.json 真正该放什么

新手常犯的一个毛病，是把某个船插或战术系统自己的三四个技能数值塞进 `data/config/settings.json`。那种只归单个类消费的平衡常数，直接写成类顶部的 `public static final float` 常量最清楚，塞进全局表只会污染命名空间。

游戏把原版与全部已启用 MOD 的 `data/config/settings.json` 合并成同一个顶层对象。**真正值得放进 `settings.json` 的，是游戏引擎预定义了结构的全局登记项，以及跨存档共用的全局参数。**

在 MOD 的 `data/config/settings.json` 中写入：

```json
{
	"designTypeColors":{
		"齿轮工业":[245,50,30,255]
	},
	"graphics":{
		"fx":{
			"GearIn_heat_glow":"graphics/fx/glow64.png"
		},
		"intel":{
			"GearIn_fleet_icon":"graphics/factions/crest_gear_industry.png"
		}
	},
	"bonusXP":{
		"GearIn_salvage":0.5
	}
}
```

这里登记了三类在成熟 MOD 中最常用的结构：

- **`designTypeColors`**：为舰船与武器的 `tech/manufacturer` (设计类型) 指定专属颜色。第一章里工匠与都林的这一列填的是 `未知`，把它改成 `齿轮工业` 后，悬停面板右上角的 "设计类型：齿轮工业" 就会自动染上这里的红橙色。
- **`graphics`**：按自定义分类 (`fx`、`intel`、`misc` 等) 登记代码里要用的贴图路径。**写进 `graphics` 段的贴图，游戏会在启动阶段自动预加载进内存**；如果不在 `settings.json` 登记，等到战斗中途才拿路径去读一张从未加载过的图，往往会直接报错或显示异常。
- **`bonusXP`**：登记故事点操作返还的额外经验比例，供交互对话或特殊改装按 key 读取。

合并表由所有 MOD 共用，因此除设计类型显示名外，自定义的贴图键与 `bonusXP` 键一律带上 `GearIn_` 前缀，避免与别的 MOD 互相覆盖。

## 取 SpriteAPI 与取贴图路径

登记在 `graphics` 下的贴图，在代码里有两套读取方法，用途完全不同：

| 方法 | 返回类型 | 用途 |
| --- | --- | --- |
| `getSprite(category, key)` | `SpriteAPI` | 战斗渲染、发光特效、自定义绘制，见 [C8-S5] |
| `getSpriteName(category, key)` | `String` | 人物头像 `setPortraitSprite(...)`、情报图标等只收路径字符串的接口 |

**`getSprite(category, key)` 每次调用都会返回一个新的 `SpriteAPI` 包装实例。** 底层纹理在启动时已经加载好，新建包装实例的开销极小；但 `SpriteAPI` 自身持有尺寸 (`setSize`)、颜色 (`setColor`)、角度 (`setAngle`) 和透明度 (`setAlphaMult`) 状态。如果把同一个 `SpriteAPI` 存进 `static` 字段给全场舰船共用，A 船改了尺寸和透明度，B 船画出来的光环就会跟着变形。

因此，路径字符串可以缓存在 `static` 字段里，而 `SpriteAPI` 应当在每个渲染对象初始化时各自调用 `getSprite(category, key)` 获取一份独立实例。

## 读取原版设置与必填判据

除了读取 MOD 自己登记的项，`SettingsAPI` 也是读取原版全局规则的入口。比如原版把每个月的经济结算次数写在 `"economyIterPerMonth"` (默认 `10`)，把舰队最大舰船数写在 `"maxShipsInFleet"` (默认 `30`)。代码需要对齐这些全局规则时，直接从 `Global.getSettings()` 读取，而不是在类里硬写 `10` 或 `30`，这样即使玩家或大修 MOD 改了全局上限，您的逻辑也能自动跟上。

设置读数分两族，区别直接写在方法签名里：

```java
float getFloat(String key);
float optFloat(String key, float defVal);
boolean getBoolean(String key);
boolean optBoolean(String key, boolean defaultValue);
```

`opt` 那一族从签名上就带着默认值参数 `defVal`，键不存在时直接返回它；不带 `opt` 的那一族没有默认值可退，读不到就会直接抛异常。

**原版自带的键与本 MOD 随包发布的键，一律用 `getFloat(...)` / `getInt(...)`。** 这些键在正常环境下必定存在，读不到只说明配置文件被改坏或键名拼错，此时让启动直接报错停下反而是好事；要是用默认值把错误盖过去，程序会带着错参数静默跑下去，排查起来麻烦得多。`optFloat(...)` 留给本来就允许缺省的键，比如供其他 MOD 按需覆盖的可选开关。

| 方法 | 签名里能看到的事实 |
| --- | --- |
| `getFloat`、`getBoolean`、`getInt`、`getString` | 无默认值参数，缺键时抛 `RuntimeException` |
| `optFloat`、`optBoolean` | 带默认值参数，缺键时返回默认值 |
| `getDesignTypeColor(designType)` | 读取 `designTypeColors` 中登记的颜色 |
| `getBonusXP(key)` | 读取 `bonusXP` 中登记的额外经验比例 |
| `getColor(id)` | 读取顶层颜色规格 |
| `isDevMode()` | 当前是否开启开发者模式 |

翻看原版 `api/impl` 下的调用也能看到同样的分工：绝大多数 `getFloat(...)` 调用都不包 `try`/`catch`，只有 `BaseSubmarketPlugin` 等极少数允许外部覆盖的地方才用 `hasKey(...)` 配合 `optFloat(...)` 提供默认回退。**启动时读不到自己发布的键，就该当场停下来。**

## 在应用加载时集中初始化

新建 `src/data/scripts/gearin/GearInSettings.java`，在 `onApplicationLoad()` 阶段一次性读取全局配置，并校验预加载贴图是否就位：

```java
package data.scripts.gearin;

import java.awt.Color;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.SettingsAPI;
import com.fs.starfarer.api.graphics.SpriteAPI;

/** 齿轮工业的全局设置与预加载资源入口，在应用加载时初始化一次。 */
public final class GearInSettings {

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
        createHeatGlowSprite();
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

在 `GearInModPlugin` 中覆写 `onApplicationLoad()` 并调用它：

```java
@Override
public void onApplicationLoad() throws Exception {
    GearInSettings.load();
}
```

`load()` 最后调用了一次 `createHeatGlowSprite()`，用于在游戏启动那一刻就验证 `"fx"` 下的 `"GearIn_heat_glow"` 是否登记正确。就目前而言，返回的临时 `SpriteAPI` 直接丢弃即可，等到 [C8-S5] 的分层渲染插件画热量光环时，再调用 `GearInSettings.createHeatGlowSprite()` 拿到自己那一份独立实例。

数值与路径字段用 `static` 保存。这些设置随游戏启动加载一次，跟着进程走，与哪份存档无关；换档不改变它们，读档也不重建它们。**判断一个值该放在哪里，看它跟随什么而变化。** 跟随单局生涯的放存档，跟随进程启动的放 `static`，带自身绘制状态的 `SpriteAPI` 则跟着具体渲染实例走。

### 进游戏核对

打开 MOD 的 `data/hulls/ship_data.csv`，把工匠 (`GearIn_artizan`) 那一行的 `tech/manufacturer` 列从 `未知` 改为 `齿轮工业`。执行 `gradle jar` 并重启游戏：

- 游戏正常进入主菜单，说明 `GearInSettings.load()` 中的各项全局配置与 `GearIn_heat_glow` 贴图均已通过启动校验；
- 进入测试战役或生涯，悬停查看工匠，面板右上角的 "设计类型：齿轮工业" 显示为 `[245, 50, 30, 255]` 对应的红橙色。

## 运行期改写的代价

`getSettingsJSON()` 交出来的是那份合并后的对象本身。改它之后必须调 `resetCached()`：不调的话，之后读到的仍是缓存里的旧值。

改写只留在当前内存里，重启就回到文件中的值。适合这么做的场合只有一类：玩家在游戏内拖动某个设置面板，想立刻看到效果。齿轮工业在基础部分不做运行时改写，[C19-S5] 的 LunaLib 面板才会用到这一套机制。**没有设置界面，就没有理由在运行期写设置。**

## 常见问题

- **启动时报错，日志中出现键名或贴图找不到**：`settings.json` 中缺少对应键，或 `graphics` 下的贴图路径指向了不存在的文件。
    - **解决方案**：对照 `GearInSettings` 中的常量核对分类名、键名与 PNG 文件路径，注意大小写。

- **启动时报 `settings.json` 的 JSON 解析错误**：新加的对象段缺少逗号，或花括号未配对。
    - **解决方案**：按日志中的行号检查 `designTypeColors`、`graphics` 与 `bonusXP` 的括号和逗号。

- **设计类型文字仍然是默认灰色**：`ship_data.csv` 的 `tech/manufacturer` 文字与 `designTypeColors` 里的键不完全一致。
    - **解决方案**：确认两处填写的字符串逐字一致，完全退出游戏后重启。

## 建议参考项

- `com.fs.starfarer.api.Global`：全部静态服务入口。
- `com.fs.starfarer.api.SettingsAPI`：`getSprite`、`getSpriteName`、`getDesignTypeColor`、`getBonusXP`、`getFloat` 与 `optFloat`。
- `com.fs.starfarer.api.graphics.SpriteAPI`：贴图绘制对象，持有独立的尺寸、颜色与透明度状态。
- `com.fs.starfarer.api.GameState`：当前场景的枚举。
- `data/config/settings.json`：原版设置文件，可对照 `graphics`、`designTypeColors`、`bonusXP` 与其它全局变量的写法。
