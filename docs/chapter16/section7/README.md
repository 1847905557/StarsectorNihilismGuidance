# 用 MagicLib 制作可调脉冲光束与状态条

脉冲光束需要三个部分同时成立：设置决定颜色与伤害，`MagicFakeBeam` 在一次调用中结算命中，`MagicUI` 持续刷新玩家状态条。本节让光束每 2 秒自动向最近敌舰发射一次，并让 HUD 同步显示充能与累计发射次数。

*本节预期阅读时间：~18 分钟。*

*本节预期操作时间：~30 分钟。*

## 学习目标

完成后，您将能够：

- 用 `MagicSettings` 一次读取并缓存光束与 HUD 配置；
- 按帧时间推进 0–1 充能，并在满充时只触发一次攻击；
- 选择最近可见敌舰，计算实际发射角度；
- 用 `MagicFakeBeam` 生成一次瞬时伤害与短暂视觉；
- 用固定 key 刷新 `MagicUI` 状态条，并从调用次数定位重复伤害。

## 前置知识

- [C6-S2] 理解 Java 类、方法、字段与入口插件；
- [C7-S1] 理解每帧回调、暂停判断与运行时对象；
- 能够用 Gradle 构建示例 MOD，并从 `starsector.log` 定位类名。

## 明确当前需求

玩家舰每 2 秒完成一次充能，随后向最近可见敌舰发射短促光束；命中在调用当帧结算，HUD 同时显示下一次发射的充能进度和累计次数。修改配置并重启后，宽度、射程、颜色、伤害与 EMP 随之变化。

## 文件与 API 链路

### 脉冲光束 API 职责速查表

| 对象 | 职责 |
|---|---|
| `MagicSettings` | 从 `modSettings.json` 的 `gear_in` 段读取值 |
| `AIUtils` 与 `VectorUtils` | 选择目标并计算角度 |
| `MagicFakeBeam` | 完成射线、视觉与伤害 |
| `MagicUI` | 为玩家舰绘制状态条，并要求定期刷新唯一 key |

### 缓存光束与界面配置

**插入位置：`GearInMagicSettings` 构造方法。** 缓存宽度、射程、伤害、EMP 与颜色，避免每帧解析设置。

```java
beamWidth = MagicSettings.getFloat(CONFIG_SECTION, "beam_width");
beamRange = MagicSettings.getFloat(CONFIG_SECTION, "beam_range");
beamDamage = MagicSettings.getFloat(CONFIG_SECTION, "beam_damage");
beamCoreColor = MagicSettings.getColorRGB(CONFIG_SECTION, "beam_color_core");
```

完成信号：构造日志一次打印 `width`、`range`、`damage`、`emp`、持续时间、颜色和标签。

### 推进自动充能

**插入位置：`GearInBeamCaster.advance()`。** 用帧时间推进充能，达到 1 时清零并触发一次发射。

```java
beamCharge += amount / BEAM_INTERVAL;
if (beamCharge >= 1f) {
    beamCharge = 0f;
    fireBeam();
}
```

完成信号：非暂停状态下约 2 秒触发一次；一次 `advance()` 至多调用一次 `fireBeam()`。

### 选择目标并计算角度

**插入位置：`GearInBeamCaster.fireBeam()` 的目标检查后。** 选择最近可见敌舰，并从玩家舰位置计算世界角度。

```java
ShipAPI enemy = AIUtils.getNearestEnemy(player);
if (enemy == null) {
    return;
}
Vector2f from = player.getLocation();
float angle = VectorUtils.getAngle(from, enemy.getLocation());
```

完成信号：没有敌舰时记录诊断并跳过发射；目标移动到另一侧后，光束角度随目标改变。

### 结算一次伪光束

**插入位置：`fireBeam()` 的目标分支。** 一次调用同时生成视觉与瞬时伤害。

```java
MagicFakeBeam.spawnFakeBeam(engine, from, settings.beamRange, angle,
        settings.beamWidth, settings.beamFull, settings.beamFading,
        settings.beamImpactSize, settings.beamCoreColor,
        settings.beamFringeColor, settings.beamDamage,
        DamageType.ENERGY, settings.beamEmp, player);
```

完成信号：`beamCount` 每次成功调用后增加 1，目标在该帧受到一次伤害，视觉按配置继续保留。

### 用唯一 key 刷新状态条

**插入位置：`GearInHudBar.refresh()`。** 使用固定 key、当前充能和累计次数刷新同一条 HUD 项。

```java
MagicUI.drawInterfaceStatusBar(player, HUD_BAR_KEY, beam.getBeamCharge(),
        settings.beamCoreColor, settings.beamFringeColor, 0f,
        "Beam charge", beam.getBeamCount());
```

完成信号：HUD 只有一条 `Beam charge`，填充从 0 走向 1，右侧数字在成功发射后增加。

### 安排刷新与暂停顺序

**插入位置：战斗插件 `advance()`。** HUD 先刷新，再由暂停守卫决定是否推进充能。

```java
if (settings.hudEnabled) {
    hud.refresh(engine);
}
if (engine.isPaused()) {
    return;
}
beam.advance(amount);
```

完成信号：暂停期间状态条不会因停止刷新而超时消失，充能数值保持不变。

## 排查关键故障

下面是**局部故障片段**：

```java
MagicFakeBeam.spawnFakeBeam(...);
MagicFakeBeam.spawnFakeBeam(...);
```

实际症状：画面看似一束更亮的光，目标却在同一帧受到两次伤害。

原因：伪光束每次调用都会即时结算伤害，视觉持续时间不代表持续伤害。

下面是**局部修正片段**：

```java
beamCharge += amount / BEAM_INTERVAL;
if (beamCharge >= 1f) {
    beamCharge = 0f;
    fireBeam();
}
```

复验：记录相邻两次 `beamCount` 变化、调用前后的目标装甲或船体值，确认每个 2 秒周期只增加一次计数并结算一次伤害。

## 完整最小例

设置、攻击与 HUD 分成三个角色。战斗插件负责按 "刷新 HUD、暂停判断、推进充能" 的顺序接线，完整类不假定任何按键或舰船系统状态。

::: details 完整成品：GearInBeamCaster

```java
package data.scripts.gearin.lib;

import org.apache.log4j.Logger;
import org.lazywizard.lazylib.VectorUtils;
import org.lazywizard.lazylib.combat.AIUtils;
import org.lwjgl.util.vector.Vector2f;
import org.magiclib.util.MagicFakeBeam;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.CombatEngineAPI;
import com.fs.starfarer.api.combat.DamageType;
import com.fs.starfarer.api.combat.ShipAPI;

/** 伪光束 (MagicFakeBeam)：每 BEAM_INTERVAL 秒向最近可见敌舰发射一条伪光束。
 * 伤害瞬时结算，持续视觉时间不等于持续伤害时间；光束不会被中途物体截断；
 * HIGH_EXPLOSIVE 对装甲 200% 伤害、对护盾 50% (折减在护盾侧)，本类用 ENERGY 规避。 */
public class GearInBeamCaster {
    private static final Logger LOG = Global.getLogger(GearInBeamCaster.class);
    private static final float BEAM_INTERVAL = 2f; // 光束发射间隔 (秒)；伤害瞬时，视觉时长由 modSettings.json 控制

    private final GearInMagicSettings settings;
    private CombatEngineAPI engine;
    private float beamCharge; // 0..1：充能进度，HUD 状态条显示它
    private int beamCount;

    public GearInBeamCaster(GearInMagicSettings settings) {
        this.settings = settings;
    }

    /** 战斗 init 时注入引擎 (由接线类调用)。 */
    public void init(CombatEngineAPI engine) {
        this.engine = engine;
    }

    /** 每帧推进充能，满 1 发射并清零；只在非暂停推进 (暂停守卫由接线类负责)。 */
    public void advance(float amount) {
        beamCharge += amount / BEAM_INTERVAL;
        if (beamCharge >= 1f) {
            beamCharge = 0f;
            fireBeam();
        }
    }

    public float getBeamCharge() { return beamCharge; }
    public int getBeamCount() { return beamCount; }

    /** 向最近可见敌舰发射一条伪光束：视觉立即生成，伤害在调用内瞬时结算。 */
    private void fireBeam() {
        ShipAPI player = engine.getPlayerShip();
        if (player == null || !player.isAlive()) {
            return;
        }
        ShipAPI enemy = AIUtils.getNearestEnemy(player); // @Nullable：判空方式见 C16-S3
        if (enemy == null) {
            LOG.info("GearIn magic beam: no enemy in sight");
            return;
        }
        Vector2f from = player.getLocation();
        float angle = VectorUtils.getAngle(from, enemy.getLocation());
        MagicFakeBeam.spawnFakeBeam(
                engine,
                from,
                settings.beamRange,
                angle,
                settings.beamWidth,
                settings.beamFull,
                settings.beamFading,
                settings.beamImpactSize,
                settings.beamCoreColor,
                settings.beamFringeColor,
                settings.beamDamage,
                DamageType.ENERGY,
                settings.beamEmp,
                player);
        beamCount++;
        LOG.info("GearIn magic beam: #" + beamCount + " damage=" + settings.beamDamage
                + " emp=" + settings.beamEmp + " width=" + settings.beamWidth
                + " range=" + settings.beamRange + " tags=" + settings.beamTags);
    }
}
```

:::

::: details 完整成品：GearInHudBar

```java
package data.scripts.gearin.lib;

import org.magiclib.util.MagicUI;

import com.fs.starfarer.api.combat.CombatEngineAPI;
import com.fs.starfarer.api.combat.ShipAPI;

/** 战斗 HUD 状态条 (MagicUI)：以固定 key 刷新玩家舰状态条。
 * 只注册数据，绘制由 MagicLib 的 MagicRenderPlugin(settings plugins 注册的
 * 战斗插件) 每帧完成；状态条约 5 秒不被刷新就会被移除 (含暂停时间)，因此
 * 刷新调用必须放在暂停守卫之前。 */
public class GearInHudBar {

    /** 战斗 HUD 状态条 key：MagicLib 要求对本 MOD 唯一；换 key 会生成另一条状态条。 */
    private static final String HUD_BAR_KEY = "gear_in_beam_charge";

    private final GearInBeamCaster beam;
    private final GearInMagicSettings settings;

    public GearInHudBar(GearInBeamCaster beam, GearInMagicSettings settings) {
        this.beam = beam;
        this.settings = settings;
    }

    /** 刷新状态条：填充进度 = 光束充能，颜色复用光束颜色 (改配置 → 光束与状态条一起变色)。 */
    public void refresh(CombatEngineAPI engine) {
        ShipAPI player = engine.getPlayerShip();
        if (player == null) {
            return;
        }
        MagicUI.drawInterfaceStatusBar(
                player,
                HUD_BAR_KEY,            // 固定 key：改变文本不会生成新条 (无 key 重载用文本兼作 key)
                beam.getBeamCharge(),   // 0..1 的填充进度
                settings.beamCoreColor,   // 填充色；null 时用原版 HUD 绿
                settings.beamFringeColor, // 边框色；null 时用原版 HUD 绿
                0f,                     // 第二段填充 (类硬幅能)，0 即关闭
                "Beam charge",
                beam.getBeamCount());   // 右侧数字，0..999999
    }
}
```

:::

::: details 完整成品：GearInMagicSettings

```java
package data.scripts.gearin.lib;

import java.awt.Color;
import java.util.List;

import org.apache.log4j.Logger;
import org.magiclib.util.MagicSettings;

import com.fs.starfarer.api.Global;

/** 设置接入 (MagicSettings)：从 data/config/modSettings.json 的 "gear_in" 段读取
 * 光束与 HUD 配置，构造时缓存一次、不在每帧解析 (MagicSettings javadoc 建议)。
 * 键缺失返回类型缺省值 (false / 0 / Color.RED / 空集合) 并写 warn 日志，不抛
 * 异常 —— 缺省值会参与后续运行语义，业务开关不要依赖缺省值。 */
public class GearInMagicSettings {

    private static final Logger LOG = Global.getLogger(GearInMagicSettings.class);

    /** data/config/modSettings.json 中本 MOD 的段名 (= MOD id "gear_in")。 */
    private static final String CONFIG_SECTION = "gear_in";

    /** HUD 状态条总开关。 */
    public final boolean hudEnabled;
    /** 光束宽度 / 射程 / 伤害 / EMP(数值键缺省 0)。 */
    public final float beamWidth;
    public final float beamRange;
    public final float beamDamage;
    public final float beamEmp;
    /** 全亮与淡出时长 (秒)：只控制视觉，不影响伤害结算。 */
    public final float beamFull;
    public final float beamFading;
    /** 命中光斑尺寸。 */
    public final float beamImpactSize;
    /** 光束核心 / 边缘颜色 (缺省 Color.RED)。 */
    public final Color beamCoreColor;
    public final Color beamFringeColor;
    /** 光束标签列表 (缺省空集合)。 */
    public final List<String> beamTags;

    public GearInMagicSettings() {
        hudEnabled = MagicSettings.getBoolean(CONFIG_SECTION, "hud_enabled");
        beamWidth = MagicSettings.getFloat(CONFIG_SECTION, "beam_width");
        beamRange = MagicSettings.getFloat(CONFIG_SECTION, "beam_range");
        beamDamage = MagicSettings.getFloat(CONFIG_SECTION, "beam_damage");
        beamEmp = MagicSettings.getFloat(CONFIG_SECTION, "beam_emp");
        beamFull = MagicSettings.getFloat(CONFIG_SECTION, "beam_full");
        beamFading = MagicSettings.getFloat(CONFIG_SECTION, "beam_fading");
        beamImpactSize = MagicSettings.getFloat(CONFIG_SECTION, "beam_impact_size");
        beamCoreColor = MagicSettings.getColorRGB(CONFIG_SECTION, "beam_color_core");
        beamFringeColor = MagicSettings.getColorRGB(CONFIG_SECTION, "beam_color_fringe");
        beamTags = MagicSettings.getList(CONFIG_SECTION, "beam_tags");
        LOG.info("GearIn magic settings: hud=" + hudEnabled
                + " width=" + beamWidth + " range=" + beamRange
                + " damage=" + beamDamage + " emp=" + beamEmp
                + " full=" + beamFull + " fading=" + beamFading
                + " impact=" + beamImpactSize
                + " core=(" + beamCoreColor.getRed() + "," + beamCoreColor.getGreen() + "," + beamCoreColor.getBlue() + ")"
                + " tags=" + beamTags);
    }
}
```

:::

## 验证方案

前置数据：`modSettings.json` 的 `gear_in` 段包含完整的 `beam_*` 键，三个角色已经由战斗插件构造和接线。

1. 进入战斗，核对一次 `GearIn magic settings` 日志中的最终配置。
2. 保持一艘敌舰可见，观察 HUD 在 2 秒内从 0 充到 1，随后清零并使右侧计数增加 1。
3. 记录目标发射前后的装甲或船体值，确认每次计数变化只对应一次伤害。
4. 移走所有敌舰并等待满充，确认日志出现 `no enemy in sight`，计数不增加。
5. 暂停超过 5 秒，确认状态条仍在、充能不推进；恢复后继续充能。
6. 修改颜色与伤害配置并重启，核对设置日志、光束颜色和单次伤害同时变化。

预期结果：

- 设置只在角色构造时读取一次，日志值与本次战斗的光束参数一致；
- 非暂停状态下每 2 秒尝试一次发射，有目标时计数增加并结算一次伤害；
- 无目标时不生成光束、不增加计数，下一周期重新查询；
- 固定 key 让 HUD 保持为一条，填充与 `beamCharge` 一致；
- 暂停期间 HUD 继续刷新，充能与攻击停止推进；
- 配置修改在重启并重新构造设置角色后生效。

## 故障分析

- **一次充能造成两次伤害**：`spawnFakeBeam()` 每次调用都会立即结算，视觉持续时间不会增加伤害次数。

    - **解决方案**：让充能状态只调用一次 `fireBeam()`。

- **错误配置没有抛异常**：`MagicSettings` 对缺键或类型错误返回缺省值并写警告。

    - **解决方案**：核对构造日志，修正配置。

- **HUD 状态条约五秒后消失**：状态条没有持续刷新，或刷新被暂停判断跳过。

    - **解决方案**：把 `refresh()` 放在暂停判断之前并保持固定 key。

- **AI 舰没有显示状态条**：`MagicUI` 只在玩家舰且 HUD 可见时绘制。

    - **解决方案**：将验证目标换为玩家舰。

## 总结

`MagicSettings` 决定本次战斗使用的参数，`GearInBeamCaster` 每 2 秒尝试一次攻击，`MagicUI` 负责持续反馈。计数只在成功生成光束后增加，因此它既是 HUD 信息，也是核对伤害调用次数的直接证据。

## 建议参考项

- `MagicSettings`：读取合并后的 MOD 设置；
- `MagicFakeBeam.spawnFakeBeam`：生成瞬时伤害光束；
- `MagicUI.drawInterfaceStatusBar`：刷新玩家 HUD 状态条。
