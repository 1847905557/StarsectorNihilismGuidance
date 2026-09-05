# 用 LunaLib 制作即时生效的设置面板

玩家无需重启就能调节视觉强度，前提是 CSV 定义、运行时读取和监听器更新同一组 key。本节建立开关与数值设置，并把变更写入缓存供战斗效果下一帧读取。

*本节预期阅读时间：~18 分钟。*

*本节预期操作时间：~30 分钟。*

## 学习目标

完成后，您将能够：

- 按 LunaLib 的九列表格式定义开关、整数、浮点数、颜色和文本设置；
- 用包装类型读取设置，并在 LunaLib 缺席或字段缺失时回退默认值；
- 注册幂等监听器，让保存后的值进入下一次效果读取。

## 前置知识

- [C7-S1] 理解 Java 类、方法、字段与入口插件；
- [C8-S3] 理解每帧回调与暂停判断；[C8-S2] 理解战斗对象读取与对象边界；
- 能够用 Gradle 构建示例 MOD，并从 `starsector.log` 定位类名。

## 定义 GearIn 设置字段

在 LunaLib 面板关闭 GearIn 轨迹、调整条目上限、脉冲间隔、标记颜色或标签文字后，GearIn 的下一次读取立即使用新值；LunaLib 缺席时安全地使用代码默认值。

### GearIn 设置字段速查表

| 字段 ID | 控件类型 | 默认值 | 取值范围 |
|---|---|---|---|
| `GearIn_trails_enabled` | Boolean | true | — |
| `GearIn_max_entries` | Int | 12 | 1–64 |
| `GearIn_pulse_delay` | Double | 1.5 | 0.1–10 |
| `GearIn_marker_color` | Color | `#f22e6e` | — |
| `GearIn_label_text` | String | GearIn | — |

## 文件与 API 链路

`data/config/LunaSettings.csv` 定义控件；`LunaSettings.getBoolean/getInt/getFloat/getColor/getString` 返回可为空的包装值；`LunaSettingsListener` 在保存设置后通知 MOD；GearIn 读取器集中处理门控和默认值。

### 在 CSV 定义开关和滑杆

在 `data/config/LunaSettings.csv` 中，每个字段使用唯一 `fieldID`；MOD ID 由 Java 调用的第一个参数传入，不写进 CSV 行：

```csv
fieldID,fieldName,fieldType,defaultValue,secondaryValue,fieldDescription,minValue,maxValue,tab
GearIn_trails_enabled,Show GearIn trails,Boolean,true,,,,,GearIn
GearIn_max_entries,Maximum entries,Int,12,,,1,64,GearIn
GearIn_pulse_delay,Pulse delay,Double,1.5,,,0.1,10,GearIn
GearIn_marker_color,Marker color,Color,#f22e6e,,,,,GearIn
GearIn_label_text,Label text,String,GearIn,,,,,GearIn
```

空白列仍要保留。缺少必需列时可能抛异常；只缺尾部可选 JSON 键时使用默认值；漏掉中间占位会使后续值错列。`fieldID` 要与 Java 中的 key 逐字一致。

LunaLib 面板会显示五个 GearIn 字段，整数范围为 1–64，脉冲间隔范围为 0.1–10。

### 读取包装类型并提供默认值

`GearInLunaSettingsReader` 的各个 getter 先检查 `lunalib` 是否启用，再读取可为空的包装类型：

```java
Boolean enabled = LunaSettings.getBoolean("gear_in", "GearIn_trails_enabled");
Integer entries = LunaSettings.getInt("gear_in", "GearIn_max_entries");
Float delay = LunaSettings.getFloat("gear_in", "GearIn_pulse_delay");
Color color = LunaSettings.getColor("gear_in", "GearIn_marker_color");
String label = LunaSettings.getString("gear_in", "GearIn_label_text");
```

每次读取都保留 `null` 分支：缺库、字段删除或用户设置损坏时分别回退 `Defs` 中的默认值。

缺键时轨迹仍开启、条目上限为 12、脉冲间隔为 1.5 秒、颜色为 `#f22e6e`、标签为 `GearIn`。

### 注册设置监听器

注册入口是 `GearInLunaSettingsListener.registerListener()`，并在 `onGameLoad()` 的 LunaLib 启用分支调用，每次加载只加入一个监听器：

```java
GearInLunaSettingsListener.registerListener();
```

`hasSettingsListenerOfClass` 用于幂等判断，`removeSettingsListener` 用于测试清理或卸载场景。

保存面板时日志出现一次 `reloaded`，重复进档不会倍增回调。

### 在回调中刷新缓存

监听器的 `settingsChanged(String modId)` 只响应 `gear_in` 的保存事件：

```java
public void settingsChanged(String modId) {
    if ("gear_in".equals(modId)) {
        GearInLunaSettingsListener.logCurrentValues();
    }
}
```

当前战斗的下一次读取会使用新值，其他 MOD 保存设置不会触发 GearIn 日志。

## 排查关键故障

下面是**局部故障片段**：

```java
Float value = LunaSettings.getFloat("gear_in", "GearIn_pulse_delay");
float scale = value;
```

实际症状：CSV 键缺失时保存设置触发 NullPointerException。

原因：LunaLib 返回可为空的包装类型；本页的浮点字段由 `getFloat` 读取，直接拆箱 `null` 会失败。

下面是**局部修正片段**：

```java
Float value = LunaSettings.getFloat("gear_in", "GearIn_pulse_delay");
float scale = value == null ? 1.5f : value;
```

复验：临时改错 `fieldID`，保存设置后确认使用 1.5 秒默认值且游戏继续运行。

## 完整最小例

局部片段说明了行为如何逐步增加；以下是可直接放入示例 MOD 的完整角色类与资源。

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

::: details 完整成品：GearInLunaSettingsListener

```java
package data.scripts.gearin.lib;

import java.awt.Color;

import org.apache.log4j.Logger;

import com.fs.starfarer.api.Global;

import lunalib.lunaSettings.LunaSettings;
import lunalib.lunaSettings.LunaSettingsListener;

/**
 * 变更通知角色：注册/注销 LunaSettings 监听器，在设置界面保存后按本 MOD 过滤并
 * 重读输出当前生效值。addSettingsListener 不去重，注册前必须用
 * hasSettingsListenerOfClass 幂等查询 (重复注册会让同一次保存触发多次回调)。
 *
 * 监听器实例在门控通过之后才创建 —— 类加载路径不触碰 lunalib.**，LunaLib 未启用
 * 时调用本类不会抛 NoClassDefFoundError；缺库处理方式见 C18-S1。
 */
public class GearInLunaSettingsListener {

    private static final Logger LOG = Global.getLogger(GearInLunaSettingsListener.class);
    private static LunaSettingsListener registered;

    private GearInLunaSettingsListener() {
        // 工具类：不实例化
    }

    /**
     * 注册 (幂等)：LunaLib 未启用时跳过并写 warn。注册入口见页面正文的接入片段
     * (GearInModPlugin.onGameLoad 一行)；监听器实例在门控之后才创建。
     */
    public static void registerListener() {
        if (!GearInLunaSettingsReader.isLunaLibEnabled()) {
            LOG.warn("GearIn luna settings: LunaLib not enabled, listener not registered");
            return;
        }
        if (registered != null && LunaSettings.hasSettingsListenerOfClass(registered.getClass())) {
            LOG.info("GearIn luna settings: listener already registered");
            return;
        }
        registered = new LunaSettingsListener() {
            @Override
            public void settingsChanged(String modId) {
                if (!GearInLunaSettingsDefs.MOD_ID.equals(modId)) {
                    return; // 其他 MOD 的保存动作不影响本 MOD 的缓存
                }
                LOG.info("GearIn luna settings: reloaded");
                logCurrentValues();
            }
        };
        LunaSettings.addSettingsListener(registered);
        LOG.info("GearIn luna settings: listener registered");
    }

    /** 取消注册 (测试清理或卸载场景)。 */
    public static void unregisterListener() {
        if (!GearInLunaSettingsReader.isLunaLibEnabled()) {
            return;
        }
        if (registered != null) {
            LunaSettings.removeSettingsListener(registered);
            registered = null;
        }
    }

    /** 输出当前生效值：用于对照设置界面与用户设置文件 (进档时可调用一次)。 */
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
}
```

:::

::: details 完整成品：GearInLunaSettingsReader

```java
package data.scripts.gearin.lib;

import java.awt.Color;

import com.fs.starfarer.api.Global;

import lunalib.lunaSettings.LunaSettings;

/**
 * 运行时读取角色：门控读取 LunaLib 设置 (LunaSettings 静态读取 API)。
 * 每个 getter 都是三段式：先按 C18-S1 检查 isModEnabled("lunalib")，
 * 再调用 LunaSettings.getX，返回 null 时回退代码内默认值 (Defs)。
 * 类自身不引用 lunalib.** 于类加载路径，未启用时可直接安全调用。
 */
public class GearInLunaSettingsReader {

    private GearInLunaSettingsReader() {
        // 工具类：不实例化
    }

    /** LunaLib 是否已启用：调用任何 lunalib.** 之前先执行 C18-S1 的启用检查。 */
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

    /** 脉冲间隔 (秒)：CSV 里是 Double 类型，读取时用 getFloat(从保存的 double 转换)。 */
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

## 验证方案

1. 构建示例 MOD，确认 LunaLib 存在时三个设置类能够编译。
2. 打开 LunaLib 面板，修改轨迹开关、条目上限、脉冲间隔、颜色和标签。
3. 保存设置，观察下一次效果读取和日志中的当前值。
4. 将脉冲间隔设置为范围外的值并保存，确认面板按 CSV 的最小值和最大值处理输入。
5. 禁用 LunaLib 后启动一次，确认所有 getter 返回默认值且没有类加载异常。

预期结果：

- 面板显示五个 GearIn 字段并使用对应控件类型；
- 缺库或缺键时返回 Defs 默认值；
- 保存 GearIn 设置时只触发一次监听器回调；
- 下一次效果读取看到新值。
- 保存范围外的脉冲间隔后，读取到的值仍在 `0.1–10` 范围内。

## 故障分析

- **保存设置时出现 `NullPointerException`**：读取方法返回 null 包装类型，调用方直接拆箱。

    - **解决方案**：恢复精确 `fieldID` 并保留默认值分支。

- **设置面板没有 GearIn**：CSV 不是九列，或文件路径不正确。

    - **解决方案**：核对 `data/config/LunaSettings.csv`，重启。

- **保存后设置必须重启才生效**：效果继续使用旧缓存，没有在保存通知后重新调用 getter。

    - **解决方案**：让下次效果计算集中读取当前值。

- **每次读档后回调次数增加**：注册前没有用 `hasSettingsListenerOfClass` 去重。

    - **解决方案**：加入幂等检查。

## 总结

LunaLib 设置完整流程是九列 CSV 定义、包装类型读取、一次幂等监听器注册和下一次效果读取。即时生效来自保存通知后重新读取，而不是每帧解析设置文件。

## 建议参考项

- `LunaSettings.csv`：设置控件定义；
- `LunaSettings`：运行时读取包装类型值；
- `LunaSettingsListener`：保存后的变更通知。
