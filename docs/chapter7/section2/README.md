# 使用 Global 与读取设置

MOD 里会变的东西有两类：一类是玩家和世界在变，另一类是设计者在变。前者属于存档和星区，后者属于设置文件。这一节管后者，也把取全局对象那套入口一并讲完。

一个 MOD 的参数表最后会长成什么样子，取决于作者愿不愿意隔一段时间就为它重新构建一次。

---

_本节预期阅读时间：\~12 分钟。_

_本节预期操作时间：\~15 分钟。_

## 前置知识

- [C7-S1] 的 `onApplicationLoad()` 调用时机；
- [C2-S3] 的突击填弹器 `GearInAssaultFeedSystem`；
- [C1-S1] 中同路径文件的合并规则。

## Global 提供的是字段，不是查询

`Global` 上每一个取值方法都只是读一个静态字段。`getSector()` 的整个方法体是 `return sectorAPI;`，`getCombatEngine()` 是 `return combatEngine;`：没有判空，没有兜底，也没有在字段为空时替你造一个。

**`Global` 交出的是此刻的真实状态：字段有值就有值，没有就是没有。** 这一点决定了它的用法。游戏在什么时候给这些字段赋值，就有可能在什么时候它们还是空的；而赋值发生在 [C7-S1] 回调表的阶段里，不在你的代码里。

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

调用点已经给了参数的，用参数。命中效果的 `onHit(...)` 传入 `engine`，直接用它，不必再问一次 `Global` —— 参数是调用方当场递过来的，`Global` 是全局可读的副本，两者在战斗中指向同一个对象，但参数的存在本身就说明调用方已经确定了它。`Global` 留给没有参数可用的地方，例如被多处调用的工具方法。

## 设计参数的位置

突击填弹器的四个数值留在设置文件里，调整只需要编辑文件并重启，不必经过构建。用户也能直接改这些值，不必拿到源码。

**适合放进设置的，是设计者会反复调整、用户可能想自行修改的数值。** 公式本身、数组下标、标签字符串之类与代码逻辑绑定的值，留在代码里。把它们也搬进设置，只会让文件变长，并让本该一起修改的代码与数据分处两地。

## 写入设置键

打开 MOD 的 `data/config/settings.json`，在最外层对象中、`plugins` 之前加入：

```json
	"GearIn_assaultFeedRofBonus":1.0,
	"GearIn_assaultFeedFluxReduction":50,
	"GearIn_assaultFeedSpeedBonus":50,
	"GearIn_assaultFeedManeuverBonus":200,
```

游戏把原版与全部 MOD 的 `settings.json` 合并为一个对象，键名冲突时后加载的值生效。**设置键与其他 ID 一样带上 `GearIn_` 前缀。** 写一个 `speedBonus`，迟早会与另一个 MOD 的同名键互相覆盖。

## 在应用加载时读取一次

新建 `src/data/scripts/gearin/GearInSettings.java`，集中读取与保存这些参数：

```java
package data.scripts.gearin;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.SettingsAPI;

/** 齿轮工业的可调参数，在应用加载时从 settings.json 读取一次。 */
public final class GearInSettings {

    private static float assaultFeedRofBonus;
    private static float assaultFeedFluxReduction;
    private static float assaultFeedSpeedBonus;
    private static float assaultFeedManeuverBonus;

    private GearInSettings() {
    }

    public static void load() {
        SettingsAPI settings = Global.getSettings();
        assaultFeedRofBonus = settings.getFloat("GearIn_assaultFeedRofBonus");
        assaultFeedFluxReduction = settings.getFloat("GearIn_assaultFeedFluxReduction");
        assaultFeedSpeedBonus = settings.getFloat("GearIn_assaultFeedSpeedBonus");
        assaultFeedManeuverBonus = settings.getFloat("GearIn_assaultFeedManeuverBonus");
    }

    public static float getAssaultFeedRofBonus() {
        return assaultFeedRofBonus;
    }

    public static float getAssaultFeedFluxReduction() {
        return assaultFeedFluxReduction;
    }

    public static float getAssaultFeedSpeedBonus() {
        return assaultFeedSpeedBonus;
    }

    public static float getAssaultFeedManeuverBonus() {
        return assaultFeedManeuverBonus;
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

这些字段用 `static`。设置在每次启动时加载一次，跟着游戏进程走，与哪份存档无关；换档不改变它们，读档也不重建它们。**判断一个值该放在哪里，看它跟随什么而变化。** 跟随存档的放存档，跟随启动的放 `static`，跟随某个对象的放那个对象的字段。

参数在启动时读取一次，而不在每次使用时读取。战术系统的 `apply(...)` 每帧调用，每帧按字符串查一次设置并无必要；集中读取还有一个好处：键名写错时，启动阶段就会报错，而不是等到某场战斗激活系统时才出现。

### 必填值与可选值

设置读数分两族，区别写在签名里：

```java
float getFloat(String key);
float optFloat(String key, float defVal);
boolean getBoolean(String key);
boolean optBoolean(String key, boolean defaultValue);
```

`opt` 那一族带着默认值参数，键不存在时返回调用方给的那个值 —— 签名里就写着，不必猜。不带 `opt` 的那一族没有默认值可返，读不到就是一个错误。

**MOD 自己附带的键用 `getFloat(...)`。** 这些键随 MOD 一同发布，读不到只说明文件被改坏或键名写错，此时让启动直接失败是正确的；用默认值掩盖它，系统会带着错误参数继续运行，而且不会留下痕迹。`optFloat(...)` 留给本来就允许省略的键，例如允许其他 MOD 覆盖的可选开关。

| 方法 | 签名里能看到的事实 |
| --- | --- |
| `getFloat`、`getBoolean` | 无默认值参数 |
| `optFloat`、`optBoolean` | 有默认值参数，缺键时返回它 |
| `getInt` | 无默认值参数 |
| `getString` | 无默认值参数 |
| `getJSONObject` | 声明 `throws JSONException` |
| `getColor` | 参数名是 `id`，取的是颜色规格 |

缺键时 `getFloat(...)` 会抛异常，类型是 `RuntimeException`，原始信息里带那个键名。`optFloat(...)` 不会：它走 `optDouble(key, defVal)`，键不存在时直接返回调用方给的默认值，所以两者在同一个设置对象上表现不同。

原版自己的写法是这条判据的最好注脚：`api/impl` 下 452 处 `getFloat(...)` 调用，几乎都不包 `try`/`catch`，只有 `BaseSubmarketPlugin` 一处用 `hasKey(...)` 加 `optFloat(...)` 兜底，而它兜的是允许其他 MOD 覆盖的键。**启动时读不到自己发布的键，就该停下来。**

本节的做法是让 `GearInSettings.load()` 直接调用 `getFloat(...)`。真有缺键，启动停在那一刻，日志里带键名，比带着默认值跑完整场战斗要容易查。

## 让战术系统读取参数

修改 `GearInAssaultFeedSystem`，删去四个常量，改为从 `GearInSettings` 读取：

```java
package data.scripts.gearin.shipsystems;

import com.fs.starfarer.api.combat.MutableShipStatsAPI;
import com.fs.starfarer.api.impl.combat.BaseShipSystemScript;

import data.scripts.gearin.GearInSettings;

/** 突击填弹器：实弹射速与机动同时提升的主动系统，数值来自 settings.json。 */
public class GearInAssaultFeedSystem extends BaseShipSystemScript {

    @Override
    public void apply(MutableShipStatsAPI stats, String id, State state, float effectLevel) {
        float rofBonus = GearInSettings.getAssaultFeedRofBonus();
        float fluxReduction = GearInSettings.getAssaultFeedFluxReduction();
        float speedBonus = GearInSettings.getAssaultFeedSpeedBonus();
        float maneuverBonus = GearInSettings.getAssaultFeedManeuverBonus();

        stats.getBallisticRoFMult().modifyMult(id, 1f + rofBonus * effectLevel);
        stats.getBallisticWeaponFluxCostMod().modifyMult(id, 1f - fluxReduction * 0.01f);
        stats.getMaxSpeed().modifyFlat(id, speedBonus * effectLevel);
        stats.getAcceleration().modifyPercent(id, maneuverBonus * effectLevel);
        stats.getDeceleration().modifyPercent(id, maneuverBonus * effectLevel);
        stats.getTurnAcceleration().modifyPercent(id, maneuverBonus * effectLevel);
        stats.getMaxTurnRate().modifyFlat(id, 15f * effectLevel);
    }

    @Override
    public void unapply(MutableShipStatsAPI stats, String id) {
        stats.getBallisticRoFMult().unmodify(id);
        stats.getBallisticWeaponFluxCostMod().unmodify(id);
        stats.getMaxSpeed().unmodify(id);
        stats.getAcceleration().unmodify(id);
        stats.getDeceleration().unmodify(id);
        stats.getTurnAcceleration().unmodify(id);
        stats.getMaxTurnRate().unmodify(id);
    }

    @Override
    public StatusData getStatusData(int index, State state, float effectLevel) {
        if (index == 0) {
            int speed = (int) (GearInSettings.getAssaultFeedSpeedBonus() * effectLevel);
            return new StatusData("+" + speed + " top speed", false);
        }
        if (index == 1) {
            return new StatusData("improved maneuverability", false);
        }
        if (index == 2) {
            int rof = (int) (GearInSettings.getAssaultFeedRofBonus() * effectLevel * 100f);
            return new StatusData("ballistic rate of fire +" + rof + "%", false);
        }
        if (index == 3) {
            int flux = (int) GearInSettings.getAssaultFeedFluxReduction();
            return new StatusData("ballistic flux use -" + flux + "%", false);
        }
        return null;
    }
}
```

最大转向速率的 `15f` 仍留在代码里。它在 [C2-S3] 中只是配合机动加成的附带数值，从来没有单独调过，也就不值得占用一个设置键。确实需要调整它时，再移入设置即可。

### 进游戏核对

执行 `gradle jar`，重启游戏，在测试战役中部署工匠并激活突击填弹器，效果应与之前一致。

然后关闭游戏，把 `settings.json` 中的 `GearIn_assaultFeedSpeedBonus` 改为 `150`，**不构建**，直接重启。再次激活系统，状态栏显示的航速加成变为 `+150`，速度的提升明显大于之前。核对完把数值改回 `50`。

## 运行期改写的代价

`getSettingsJSON()` 交出来的是那份合并后的对象本身。改它之后必须调 `resetCached()`：不调的话，之后读到的仍是缓存里的旧值。

改写只出现在内存里，重启就回到文件中的值。这是 "这一次运行里的值"，没有持久化，所以适合的场合只有一类：玩家在游戏内拖动某个设置面板，想立刻看到效果。齿轮工业不做，[C19-S5] 的 LunaLib 面板才会把本节的参数接到游戏内菜单上。

对 MOD 作者更实际的判断是：**没有界面就没有理由在运行期写设置。** 想改数值就改文件再重启，那条路径短，也不会让 "当前值" 与 "文件里的值" 出现两份。

## 常见问题

- **启动时报错，日志中出现 `GearIn_assaultFeed...` 键名**：`settings.json` 中缺少这个键，或键名拼写不一致。
    - **解决方案**：对照 `GearInSettings.load()` 中的键名逐一核对，注意大小写。

- **启动时报 `settings.json` 的 JSON 解析错误**：新加的行缺少逗号，或误删了 `plugins` 前的括号。
    - **解决方案**：按日志中的行号检查新加入的几行。

- **改了设置文件，游戏中的数值没有变化**：参数在 `onApplicationLoad()` 中读取，只在启动时读取一次；或者 `GearInSettings.load()` 没有被调用。
    - **解决方案**：完全退出游戏后重启，并确认入口类覆写了 `onApplicationLoad()`。

## 建议参考项

- `com.fs.starfarer.api.Global`：全部静态服务入口。
- `com.fs.starfarer.api.SettingsAPI`：`getFloat`、`optFloat`、`getSettingsJSON` 与 `resetCached`。
- `com.fs.starfarer.api.GameState`：当前场景的枚举。
- `data/config/settings.json`：原版设置文件，可以对照原版键的写法。
