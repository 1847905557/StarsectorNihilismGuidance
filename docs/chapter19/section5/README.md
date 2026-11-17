# 制作即时生效的 LunaLib 设置面板

只在代码里留一个布尔开关还不够顺手，真正调手感时还是想把开关、整数滑条、间隔小数、颜色和文字全写进 `LunaSettings.csv`，在游戏内改完保存、下一有效帧即可更新画面。

读取器和监听继续隔离在嵌套适配层之后，即使玩家后续移除 LunaLib，整套战斗标记照常稳定运行。

---

_本节预期阅读时间：\~18 分钟。_

_本节预期操作时间：\~30 分钟。_

## 前置知识

- [C1-S1] 的 MOD ID(`GearIndustry`)；
- [C1-S7] 的 CSV 空列与转义规则；
- [C8-S1] 的战斗插件、暂停判断与 `settings.json` 登记；
- [C18-S3] 的附近敌舰查询；
- [C19-S1] 的 LunaLib 依赖、拥有者 MOD ID 与 `LunaAdapter` 隔离模式。

## 按九列表头扩展设置字段

打开 [C19-S1] 建好的 `data/config/LunaSettings.csv`，保留原有 `GearIn_fullGraphicEffect`，紧接着追加本节新字段：

```csv
fieldID,fieldName,fieldType,defaultValue,secondaryValue,fieldDescription,minValue,maxValue,tab
GearIn_fullGraphicEffect,Full graphic effect,Boolean,true,,,,,GearIn
GearIn_trails_enabled,Show GearIn trails,Boolean,true,,,,,GearIn
GearIn_max_entries,Maximum entries,Int,12,,,1,64,GearIn
GearIn_pulse_delay,Pulse delay,Double,1.5,,,0.1,10,GearIn
GearIn_marker_color,Marker color,Color,#f22e6e,,,,,GearIn
GearIn_label_text,Label text,String,GearIn,,,,,GearIn
```

填表时记得保留中间空逗号占位，描述或默认值含英文逗号时按标准 CSV 用双引号包起来。对于 `Int` 与 `Double` 类型，`minValue` 和 `maxValue` 会同时交给滑条与文本框失焦钳制使用 —— 另外再次确认：[C1-S1] 定下的 `mod_info.json` `"id"` 是 `"GearIndustry"`，LunaLib 读取 `LunaSettings.csv` 后正是把数据存到 `"GearIndustry"` 名下。

| 字段 ID                 | 类型与默认          | 战斗插件用途           |
| ----------------------- | ------------------- | ---------------------- |
| `GearIn_trails_enabled` | Boolean，true       | 控制附近敌舰标记生成   |
| `GearIn_max_entries`    | Int，12，1–64       | 限制每次标记的目标上限 |
| `GearIn_pulse_delay`    | Double，1.5，0.1–10 | 控制两次标记间隔秒数   |
| `GearIn_marker_color`   | Color，`#f22e6e`    | 标记粒子颜色           |
| `GearIn_label_text`     | String，GearIn      | 玩家状态标签标题       |

## 用包装类型与嵌套适配器读配置

将 `GearInLunaSettingsDefs` 与 `GearInLunaSettingsReader` 保存到 `src/data/scripts/gearin/lib/`。读取器对外只暴露原版与 JDK 返回类型，真正的 `LunaSettings.get*` 调用全部收在私有静态类 `LunaAdapter` 里 (只有 `isLunaLibEnabled()` 返回 `true` 才会进入)：

```java
Boolean enabled = lunalib.lunaSettings.LunaSettings.getBoolean(GearInLunaSettingsDefs.MOD_ID, fieldId);
Integer entries = lunalib.lunaSettings.LunaSettings.getInt(GearInLunaSettingsDefs.MOD_ID, fieldId);
Float delay = lunalib.lunaSettings.LunaSettings.getFloat(GearInLunaSettingsDefs.MOD_ID, fieldId);
Color color = lunalib.lunaSettings.LunaSettings.getColor(GearInLunaSettingsDefs.MOD_ID, fieldId);
String label = lunalib.lunaSettings.LunaSettings.getString(GearInLunaSettingsDefs.MOD_ID, fieldId);
```

注意几个小细节：JSON 里没有专门的 `float`，`Double` 选项取出来时调 `LunaSettings.getFloat(...)` 转成单精度浮点数；`getColor(...)` 兼容带或不带 `#` 前缀的十六进制字符串；无论缺少 MOD、字段还是类型解析异常，LunaLib 都会记一条错误日志并返回 `null`，由 `GearInLunaSettingsReader` 回退到 `GearInLunaSettingsDefs` 里的常量。

由于本例每帧标签更新与周期粒子发射都非常轻量，直接按帧读取即可让修改即刻反映在下一次绘制；只有涉及纹理预加载等昂贵重建时，才需要改由下面的变更监听器触发重载。

## 在生涯加载时挂上变更监听

将 `GearInLunaSettingsListener` 保存到同一 `lib` 包。`GearInModPlugin.onGameLoad(...)` 在原有初始化之外追加：

```java
if (GearInLunaSettingsReader.isLunaLibEnabled()) {
    GearInLunaSettingsListener.registerListener();
    GearInLunaSettingsListener.logCurrentValues();
}
```

监听器同样通过内部 `LunaAdapter` 才实现 `LunaSettingsListener`：由于 `LunaSettings` 的监听器列表是静态保存的应用级容器，读档重调 `registerListener()` 时必须先用 `hasSettingsListenerOfClass(...)` 去重；需要退订则调用 `unregisterListener()`。

玩家在游戏内设置界面保存改动后，LunaLib 会把当前页的更改写入 `LunaSettings/GearIndustry.json` 并按受影响 MOD 派发 `settingsChanged(modId)`，我们的实现按 `"GearIndustry".equals(modId)` 过滤并打印当前生效参数。

::: warning 开新档时的设置界面不会触发变更回调

在 "新建游戏" 流程中打开的 LunaLib 设置弹窗会更新并保存 JSON，但在源代码里带了 `if (!newGame)` 守卫，特意**跳过** `reportSettingsChanged(...)`。测试监听日志务必在进入生涯后再打开设置面板修改并保存。

:::

## 把面板参数接进战斗渲染循环

最后把 `GearInSettingsVisualPlugin` 保存到 `src/data/scripts/gearin/combat/`，并在 `data/config/settings.json` 的 `plugins` 对象补充登记：

```json
"GearIn_settings_visual": "data.scripts.gearin.combat.GearInSettingsVisualPlugin"
```

插件按 [C8-S1] 惯例先过 `engine == null || engine.isPaused() || Global.getCurrentState() == GameState.TITLE` 与玩家舰存活检查，随后读取面板设置并维护左下角玩家状态标签：

- 关闭标记开关时清零计时并早退，已发出去的粒子自然走到自身 `0.4` 秒持续时间结束；
- 开启时按 `elapsed += amount` 累积非暂停时间，达到配置的秒数间隔后用 `AIUtils.getNearbyEnemies(player, 1600f)` 扫描碰撞圆间隔在 `1600` su 以内的可见敌舰，按上限截取前几个目标并各自生成一个 `24` su、持续 `0.4` 秒的平滑粒子。

## 完整最小例

完整代码由常量表、读取器、监听器和战斗插件组成 (CSV 配置见前文六行表)：

::: details 完整成品：GearInLunaSettingsDefs

```java
package data.scripts.gearin.lib;

import java.awt.Color;

public final class GearInLunaSettingsDefs {
    public static final String MOD_ID = "GearIndustry";
    public static final String LUNA_LIB_MOD_ID = "lunalib";

    public static final String KEY_TRAILS = "GearIn_trails_enabled";
    public static final String KEY_MAX_ENTRIES = "GearIn_max_entries";
    public static final String KEY_PULSE_DELAY = "GearIn_pulse_delay";
    public static final String KEY_MARKER_COLOR = "GearIn_marker_color";
    public static final String KEY_LABEL_TEXT = "GearIn_label_text";

    public static final boolean DEFAULT_TRAILS = true;
    public static final int DEFAULT_MAX_ENTRIES = 12;
    public static final float DEFAULT_PULSE_DELAY = 1.5f;
    public static final Color DEFAULT_MARKER_COLOR = new Color(0xf2, 0x2e, 0x6e);
    public static final String DEFAULT_LABEL_TEXT = "GearIn";

    private GearInLunaSettingsDefs() {
    }
}
```

:::

::: details 完整成品：GearInLunaSettingsReader

```java
package data.scripts.gearin.lib;

import java.awt.Color;

import com.fs.starfarer.api.Global;

/** 按启用状态读取设置，缺失值使用已定义的缺省。 */
public class GearInLunaSettingsReader {

    private GearInLunaSettingsReader() {
    }

    /** 查询 LunaLib 的启用状态。 */
    public static boolean isLunaLibEnabled() {
        return Global.getSettings().getModManager().isModEnabled(GearInLunaSettingsDefs.LUNA_LIB_MOD_ID);
    }

    /** 轨迹特效开关：Boolean 设置，缺省 true。 */
    public static boolean isTrailsEnabled() {
        if (!isLunaLibEnabled()) {
            return GearInLunaSettingsDefs.DEFAULT_TRAILS;
        }
        Boolean value = LunaAdapter.getBoolean(GearInLunaSettingsDefs.KEY_TRAILS);
        return value != null ? value : GearInLunaSettingsDefs.DEFAULT_TRAILS;
    }

    /** 面板最大条目数：Int 设置，缺省 12。 */
    public static int getMaxEntries() {
        if (!isLunaLibEnabled()) {
            return GearInLunaSettingsDefs.DEFAULT_MAX_ENTRIES;
        }
        Integer value = LunaAdapter.getInt(GearInLunaSettingsDefs.KEY_MAX_ENTRIES);
        return value != null ? value : GearInLunaSettingsDefs.DEFAULT_MAX_ENTRIES;
    }

    /** 脉冲间隔 (秒)：CSV 里是 Double 类型，读取时用 getFloat (从保存的 double 转换)。 */
    public static float getPulseDelay() {
        if (!isLunaLibEnabled()) {
            return GearInLunaSettingsDefs.DEFAULT_PULSE_DELAY;
        }
        Float value = LunaAdapter.getFloat(GearInLunaSettingsDefs.KEY_PULSE_DELAY);
        return value != null ? value : GearInLunaSettingsDefs.DEFAULT_PULSE_DELAY;
    }

    /** 标记颜色：Color 设置，保存/读取十六进制字符串 (# 前缀可省略)。 */
    public static Color getMarkerColor() {
        if (!isLunaLibEnabled()) {
            return GearInLunaSettingsDefs.DEFAULT_MARKER_COLOR;
        }
        Color value = LunaAdapter.getColor(GearInLunaSettingsDefs.KEY_MARKER_COLOR);
        return value != null ? value : GearInLunaSettingsDefs.DEFAULT_MARKER_COLOR;
    }

    /** 标签文本：String 设置，按原样读取。 */
    public static String getLabelText() {
        if (!isLunaLibEnabled()) {
            return GearInLunaSettingsDefs.DEFAULT_LABEL_TEXT;
        }
        String value = LunaAdapter.getString(GearInLunaSettingsDefs.KEY_LABEL_TEXT);
        return value != null ? value : GearInLunaSettingsDefs.DEFAULT_LABEL_TEXT;
    }

    private static final class LunaAdapter {
        private static Boolean getBoolean(String fieldId) {
            return lunalib.lunaSettings.LunaSettings.getBoolean(GearInLunaSettingsDefs.MOD_ID, fieldId);
        }

        private static Integer getInt(String fieldId) {
            return lunalib.lunaSettings.LunaSettings.getInt(GearInLunaSettingsDefs.MOD_ID, fieldId);
        }

        private static Float getFloat(String fieldId) {
            return lunalib.lunaSettings.LunaSettings.getFloat(GearInLunaSettingsDefs.MOD_ID, fieldId);
        }

        private static Color getColor(String fieldId) {
            return lunalib.lunaSettings.LunaSettings.getColor(GearInLunaSettingsDefs.MOD_ID, fieldId);
        }

        private static String getString(String fieldId) {
            return lunalib.lunaSettings.LunaSettings.getString(GearInLunaSettingsDefs.MOD_ID, fieldId);
        }
    }
}
```

:::

::: details 完整成品：GearInLunaSettingsListener

```java
package data.scripts.gearin.lib;

import java.awt.Color;

import org.apache.log4j.Logger;

import com.fs.starfarer.api.Global;

import lunalib.lunaSettings.LunaSettings;
import lunalib.lunaSettings.LunaSettingsListener;

/** 登记设置变更通知，并记录本 MOD 当前生效值。 */
public class GearInLunaSettingsListener {
    private static final Logger LOG = Global.getLogger(GearInLunaSettingsListener.class);

    private GearInLunaSettingsListener() {
    }

    public static void registerListener() {
        if (!GearInLunaSettingsReader.isLunaLibEnabled()) {
            return;
        }
        LunaAdapter.register();
    }

    public static void unregisterListener() {
        if (!GearInLunaSettingsReader.isLunaLibEnabled()) {
            return;
        }
        LunaAdapter.unregister();
    }

    public static void logCurrentValues() {
        LOG.info("GearIn luna settings: trails=" + GearInLunaSettingsReader.isTrailsEnabled()
                + " maxEntries=" + GearInLunaSettingsReader.getMaxEntries()
                + " pulseDelay=" + GearInLunaSettingsReader.getPulseDelay()
                + " markerColor=" + toHex(GearInLunaSettingsReader.getMarkerColor())
                + " labelText=" + GearInLunaSettingsReader.getLabelText());
    }

    private static String toHex(Color color) {
        return String.format("#%02x%02x%02x", color.getRed(), color.getGreen(), color.getBlue());
    }

    private static final class LunaAdapter {
        private static LunaSettingsListener registered;

        private static void register() {
            if (registered != null && LunaSettings.hasSettingsListenerOfClass(registered.getClass())) {
                return;
            }
            registered = new LunaSettingsListener() {
                @Override
                public void settingsChanged(String modId) {
                    if (!GearInLunaSettingsDefs.MOD_ID.equals(modId)) {
                        return;
                    }
                    LOG.info("GearIn luna settings: reloaded");
                    logCurrentValues();
                }
            };
            LunaSettings.addSettingsListener(registered);
            LOG.info("GearIn luna settings: listener registered");
        }

        private static void unregister() {
            if (registered != null) {
                LunaSettings.removeSettingsListener(registered);
                registered = null;
            }
        }
    }
}
```

:::

::: details 完整成品：GearInSettingsVisualPlugin

```java
package data.scripts.gearin.combat;

import java.awt.Color;
import java.util.List;

import com.fs.starfarer.api.GameState;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.BaseEveryFrameCombatPlugin;
import com.fs.starfarer.api.combat.CombatEngineAPI;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.input.InputEventAPI;
import data.scripts.gearin.lib.GearInLunaSettingsReader;
import org.lazywizard.lazylib.combat.AIUtils;
import org.lwjgl.util.vector.Vector2f;

/** 使用当前设置生成附近敌舰标记和玩家状态标签。 */
public final class GearInSettingsVisualPlugin extends BaseEveryFrameCombatPlugin {
    private CombatEngineAPI engine;
    private float elapsed;

    @Override
    public void init(CombatEngineAPI engine) {
        this.engine = engine;
    }

    @Override
    public void advance(float amount, List<InputEventAPI> events) {
        if (engine == null || engine.isPaused() || Global.getCurrentState() == GameState.TITLE) {
            return;
        }
        ShipAPI player = engine.getPlayerShip();
        if (player == null || !player.isAlive() || !engine.isEntityInPlay(player)) {
            return;
        }

        boolean enabled = GearInLunaSettingsReader.isTrailsEnabled();
        int maxEntries = GearInLunaSettingsReader.getMaxEntries();
        float delay = GearInLunaSettingsReader.getPulseDelay();
        Color color = GearInLunaSettingsReader.getMarkerColor();
        String label = GearInLunaSettingsReader.getLabelText();
        engine.maintainStatusForPlayerShip(this, null, label,
                "markers=" + enabled + " limit=" + maxEntries + " delay=" + delay, false);
        if (!enabled) {
            elapsed = 0f;
            return;
        }

        elapsed += amount;
        if (elapsed < delay) {
            return;
        }
        elapsed = 0f;
        List<ShipAPI> enemies = AIUtils.getNearbyEnemies(player, 1600f);
        int count = Math.min(maxEntries, enemies.size());
        for (int index = 0; index < count; index++) {
            engine.addSmoothParticle(enemies.get(index).getLocation(), new Vector2f(),
                    24f, 1f, 0.4f, color);
        }
    }
}
```

:::

### 进游戏核对

1. **面板即时生效与范围钳制**：启用 LunaLib 启动并载入生涯，进战斗核对默认 `GearIn` 标签和 `#f22e6e` 粉色标记；退回大地图打开 LunaLib 设置面板，将上限改为 `1`、间隔改 `0.5`、颜色改绿、标签改新文本 (也可故意输一次超出 `0.1–10` 的间隔看文本框失焦钳制，最后记得恢复 `1.5`)，保存后再进同一场战斗，标签文字、单目标标记、间隔和颜色应立即切换；关闭开关后停发新标记，已在场的粒子会在 `0.4` 秒内自然消失。
2. **核对监听去重与 MOD 过滤**：在生涯里重复读档后修改并保存齿轮工业设置，日志每次只多出一组 `GearIn luna settings: reloaded`；修改保存其他 MOD 的设置，本监听不应刷出多余日志。
3. **验证脱库回退**：在启动器关闭 LunaLib 重启，进同一测试战斗与生涯，整个工程无 `NoClassDefFoundError`，标签和标记直接采用代码默认值 (`true`、`12`、`1.5`、粉色、`GearIn`)。

## 常见问题

- **停用 LunaLib 时报错 `NoClassDefFoundError`**：外层类的字段、签名或静态初始化块里意外留了 `LunaSettings` 或 `LunaSettingsListener` 的引用。
    - **解决方案**：把所有第三方调用与接口实现严格留在启用判断之后的嵌套 `LunaAdapter` 里。

- **改完面板保存却总是退回默认数值**：拥有者 MOD ID、字段名或类型与当前配置不匹配。
    - **解决方案**：确认 `GearInLunaSettingsDefs.MOD_ID` 为 `"GearIndustry"`，再对照 `LunaSettings.csv` 检查 `fieldID` 与九列结构。

- **面板压根没显示新增控件**：路径、表头或字段类型无法被 LunaSettings 解析。
    - **解决方案**：核对 `data/config/LunaSettings.csv` 的九列表头、空列逗号和 `fieldType`，保存后重启。

- **保存一次设置打出多条 `reloaded` 日志**：监听器在读档或重复初始化时被多次加入列表。
    - **解决方案**：登记前用 `LunaSettings.hasSettingsListenerOfClass(...)` 去重。

## 建议参考项

- `data/config/LunaSettings.csv`：控件、范围与分页标签定义；
- `lunalib.lunaSettings.LunaSettings`：包装类型读取与监听管理；
- `lunalib.lunaSettings.LunaSettingsListener`：面板保存变更回调；
- `com.fs.starfarer.api.combat.CombatEngineAPI`：状态标签与平滑粒子绘制；
- `org.lazywizard.lazylib.combat.AIUtils`：附近可见敌舰查询。
