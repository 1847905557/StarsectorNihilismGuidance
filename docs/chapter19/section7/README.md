# 制作即时生效的 LunaLib 设置面板

本节让 LunaLib 的开关、数量、间隔、颜色和标签进入实际战斗显示。控件负责保存，读取器取得当前值，战斗插件在自己的调用时机使用它们；变更监听另外记录通知。设置保存后无需重新构建代码，库缺席时同一个插件采用代码缺省。

*本节预期阅读时间：\~18 分钟。*

*本节预期操作时间：\~30 分钟。*

## 学习目标

完成后，您将能够：

- 按九列 CSV 定义开关、整数、浮点数、颜色和文本；
- 用包装类型读取字段，并处理库缺席和缺失值；
- 登记一次变更监听，按 MOD ID 过滤通知；
- 将设置接到战斗标记与标签，观察保存后的实际结果。

## 前置知识

- [C19-S1] 的 LunaLib 编译依赖、拥有者 ID 和可选适配；
- [C8-S3] 的战斗插件、暂停和 settings 登记；
- [C18-S3] 的附近敌舰查询；
- [C1-S7] 的 CSV 空列与转义规则。

## 在现有设置表追加字段

打开 `data/config/LunaSettings.csv`，保留 [C19-S1] 的 `GearIn_fullGraphicEffect`，追加本节五行。完整输入为：

```csv
fieldID,fieldName,fieldType,defaultValue,secondaryValue,fieldDescription,minValue,maxValue,tab
GearIn_fullGraphicEffect,Full graphic effect,Boolean,true,,,,,GearIn
GearIn_trails_enabled,Show GearIn trails,Boolean,true,,,,,GearIn
GearIn_max_entries,Maximum entries,Int,12,,,1,64,GearIn
GearIn_pulse_delay,Pulse delay,Double,1.5,,,0.1,10,GearIn
GearIn_marker_color,Marker color,Color,#f22e6e,,,,,GearIn
GearIn_label_text,Label text,String,GearIn,,,,,GearIn
```

**字段 ID、控件类型和读取方法对应同一份设置。**CSV 按来源 MOD 记录拥有者，Java 再以 `gear_in` 和字段 ID 读取；空列保留位置，文本中含英文逗号时按 CSV 转义。

`fieldID`、`fieldName`、`fieldType` 与 `defaultValue` 参与控件创建，描述、次值、范围和 tab 各自按用途读取。类型为 Int 或 Double 时，面板使用范围组织输入；getter 取得已保存值，当前代码的缺省只处理缺席或缺失。

### 设置与画面速查表

| 字段 ID | 类型与缺省 | 当前使用方 |
|---|---|---|
| `GearIn_trails_enabled` | Boolean，true | 控制附近敌舰标记生成 |
| `GearIn_max_entries` | Int，12，1–64 | 限制一次标记的目标数 |
| `GearIn_pulse_delay` | Double，1.5，0.1–10 | 控制两次标记的间隔秒数 |
| `GearIn_marker_color` | Color，`#f22e6e` | 标记颜色 |
| `GearIn_label_text` | String，GearIn | 玩家状态标签文字 |

本节定义 `GearIn_trails_enabled`，当前插件用短寿命粒子提供观察结果。需要接到尾迹效果时，同一个布尔值也可以决定是否追加节点，实际使用由效果类安排。

## 保存定义和读取器

将 Defs、Reader、Listener 完整类保存到 `src/data/scripts/gearin/lib/`。Defs 保存键和代码默认，Reader 各方法先查询启用状态，再用对应包装类型取值。局部调用如下：

```java
Boolean enabled = LunaSettings.getBoolean("gear_in", "GearIn_trails_enabled");
Integer entries = LunaSettings.getInt("gear_in", "GearIn_max_entries");
Float delay = LunaSettings.getFloat("gear_in", "GearIn_pulse_delay");
Color color = LunaSettings.getColor("gear_in", "GearIn_marker_color");
String label = LunaSettings.getString("gear_in", "GearIn_label_text");
```

LunaLib 缺少 MOD、字段或不能取得所需类型时，getter 记录错误并返回 `null`，Reader 返回 Defs 缺省。Double 配置通过 `getFloat` 转成浮点秒数，Color 从十六进制文本取得 RGB，String 保留保存的文字。

首次 getter 可以触发库加载设置，后续从已加载数据取值。**值更新以后，使用方的下一次读取才能改变行为。** 保存通知可以处理缓存更新或其他工作，本例的监听器用于记录字段值。

编者在这个示例选择直接读取：这些参数改变下一次标签显示与粒子生成，无需重新建立战斗插件。若设置改变涉及纹理预载等较重操作，再把对应重建安排到通知中，职责和代价会更明确。

## 将监听器接到生涯加载

在既有 `GearInModPlugin.onGameLoad(...)` 追加下面的局部调用，导入 Reader 与 Listener，原有初始化继续保留：

```java
if (GearInLunaSettingsReader.isLunaLibEnabled()) {
    GearInLunaSettingsListener.registerListener();
    GearInLunaSettingsListener.logCurrentValues();
}
```

Listener 的外层只使用 Java 和自有类型，第三方接口字段与实例留在嵌套 `LunaAdapter`。启用分支通过后才调用适配器；同类监听已经存在时结束登记，读档继续使用应用级注册。

面板保存实际修改的字段后，按拥有者分别更新值并发送通知。`settingsChanged(modId)` 只处理 `gear_in`，输出 `reloaded` 和当前字段；`unregisterListener()` 移除本例实例，之后再注册会重新建立。

::: warning 新档设置面板不会发送这项通知
新档创建过程中的面板保存会更新数据，但跳过 `settingsChanged`。本节在进入生涯后登记监听，并通过正常生涯的保存操作观察回调。
:::

## 让五个字段进入战斗

保存完整 `GearInSettingsVisualPlugin` 到 `src/data/scripts/gearin/combat/`，在已有 `data/config/settings.json` 的 plugins 对象追加：

```json
"GearIn_settings_visual": "data.scripts.gearin.combat.GearInSettingsVisualPlugin"
```

插件在每个有效帧读取五项设置，状态标签显示文字、开关、上限与间隔。开关开启时累积非暂停时间，到达阈值后查询与玩家碰撞圆间隔不超过 1600 su 的可见敌舰，再按集合顺序标记前若干目标。数量采用配置上限与候选数中的较小者，24 su 粒子持续 0.4 秒。

关闭开关会清零计时，新标记停止；已经生成的粒子继续结束自己的寿命。保存颜色后，下一组粒子采用新颜色；保存文字后，下一有效帧更新标签。改变间隔时，当前已经累计的时间与新阈值继续比较。

## 完整最小例

配置采用前面的 CSV 与战斗登记，四个类分别承担常量、读取、通知与实际效果：

::: details 完整成品：GearInLunaSettingsDefs

```java
package data.scripts.gearin.lib;

import java.awt.Color;

public final class GearInLunaSettingsDefs {
    public static final String MOD_ID = "gear_in";
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

import lunalib.lunaSettings.LunaSettings;

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
        Boolean value = LunaSettings.getBoolean(GearInLunaSettingsDefs.MOD_ID, GearInLunaSettingsDefs.KEY_TRAILS);
        return value != null ? value : GearInLunaSettingsDefs.DEFAULT_TRAILS;
    }

    /** 面板最大条目数：Int 设置，缺省 12。 */
    public static int getMaxEntries() {
        if (!isLunaLibEnabled()) {
            return GearInLunaSettingsDefs.DEFAULT_MAX_ENTRIES;
        }
        Integer value = LunaSettings.getInt(GearInLunaSettingsDefs.MOD_ID, GearInLunaSettingsDefs.KEY_MAX_ENTRIES);
        return value != null ? value : GearInLunaSettingsDefs.DEFAULT_MAX_ENTRIES;
    }

    /** 脉冲间隔 (秒)：CSV 里是 Double 类型，读取时用 getFloat (从保存的 double 转换)。 */
    public static float getPulseDelay() {
        if (!isLunaLibEnabled()) {
            return GearInLunaSettingsDefs.DEFAULT_PULSE_DELAY;
        }
        Float value = LunaSettings.getFloat(GearInLunaSettingsDefs.MOD_ID, GearInLunaSettingsDefs.KEY_PULSE_DELAY);
        return value != null ? value : GearInLunaSettingsDefs.DEFAULT_PULSE_DELAY;
    }

    /** 标记颜色：Color 设置，保存/读取十六进制字符串 (# 前缀可省略)。 */
    public static Color getMarkerColor() {
        if (!isLunaLibEnabled()) {
            return GearInLunaSettingsDefs.DEFAULT_MARKER_COLOR;
        }
        Color value = LunaSettings.getColor(GearInLunaSettingsDefs.MOD_ID, GearInLunaSettingsDefs.KEY_MARKER_COLOR);
        return value != null ? value : GearInLunaSettingsDefs.DEFAULT_MARKER_COLOR;
    }

    /** 标签文本：String 设置，按原样读取。 */
    public static String getLabelText() {
        if (!isLunaLibEnabled()) {
            return GearInLunaSettingsDefs.DEFAULT_LABEL_TEXT;
        }
        String value = LunaSettings.getString(GearInLunaSettingsDefs.MOD_ID, GearInLunaSettingsDefs.KEY_LABEL_TEXT);
        return value != null ? value : GearInLunaSettingsDefs.DEFAULT_LABEL_TEXT;
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
        if (engine.isPaused()) {
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

## 验证方案

前置数据：准备包含至少两艘可见敌舰的测试战斗，LunaLib 可在启动器启用与禁用。

1. 保存 CSV、四个类、生涯调用与战斗插件登记，构建并重启，载入生涯确认注册和字段日志。
2. 进入测试战斗，查看 GearIn 标签与默认粉色标记。返回生涯，在 LunaLib 面板把上限改为 1、间隔改为 0.5、颜色改为绿色、标签改为新文字，保存后再次进入测试战斗。
3. 比较下一有效帧的标签、下一组粒子的颜色、每次目标数与间隔。关闭开关后观察新标记停止，再开启比较。
4. 在面板尝试范围外的间隔，通过控件观察输入结果并保存，确认界面和实际读取对应；恢复 1.5。
5. 重复读档，再实际修改并保存本 MOD 设置，核对每次变更通知只产生一组 reloaded 日志；修改并保存其他 MOD 时比较本 MOD 日志。
6. 禁用 LunaLib 后重启，进入同一基础内容和测试战斗，核对 Defs 默认及类加载。

预期结果：

- 本节五个字段与前文图形开关共同出现，拥有者为 gear_in；
- 保存后标签采用当前文字，粒子采用新颜色、数量与间隔；
- 关闭时停止新增，已有粒子在 0.4 秒内结束；
- 本 MOD 保存只通知当前登记的一份监听，读档保持注册状态；
- 库缺席时四个自有角色按默认运行，当前默认为 true、12、1.5、粉色和 GearIn。

## 故障分析

- **库缺席时调用监听器出现类加载异常**：外层字段或接口直接引用 LunaLib 类型。
    - **解决方案**：采用本节嵌套适配器，把接口实例留在启用分支后的类中。

- **字段读取回到默认**：拥有者、字段名或保存类型不匹配。
    - **解决方案**：核对 gear_in、Defs 的键和 LunaLib 取值日志，恢复 CSV 中的定义。

- **面板没有本节控件**：路径、必需列或字段类型无法建立控件。
    - **解决方案**：核对九列表头与实际空列，保存后重启。

- **日志更新但画面保持此前参数**：效果仍保存旧副本或没有调用当前读取器。
    - **解决方案**：核对战斗插件登记和五个 getter 的使用位置。

- **保存后回调次数增加**：同类监听器被重复登记。
    - **解决方案**：按 registered 类查询已存在实例，再加入监听。

- **关闭后仍短暂看到此前粒子**：已生成粒子继续按自身寿命显示。
    - **解决方案**：观察新生成停止，并等待本节 0.4 秒粒子结束。

## 总结

面板更新已加载设置，读取器按拥有者和字段取得值，战斗插件再将它们用于当前显示与粒子生成。通知在旁记录保存结果；即时生效来自实际读取，已有粒子与累计计时仍遵循自己的状态。库缺席时采用同一组缺省，使用方的工作继续成立。

## 建议参考项

- `data/config/LunaSettings.csv`：控件与范围定义；
- `lunalib.lunaSettings.LunaSettings`：包装值读取与监听器登记；
- `lunalib.lunaSettings.LunaSettingsListener`：保存通知；
- `com.fs.starfarer.api.combat.CombatEngineAPI`：玩家状态标签和粒子；
- `org.lazywizard.lazylib.combat.AIUtils`：可见敌舰候选。
