# 用 GraphicsLib 让舰船灯光响应武器开火

常亮光源只能证明库已接入，无法体现战斗状态。本节把一盏 `StandardLight` 固定在玩家舰上，并让强度与武器开火共同变化；武器停火后，灯光平滑收回。

*本节预期阅读时间：~18 分钟。*

*本节预期操作时间：~30 分钟。*

## 学习目标

完成后，您将能够：

- 在应用加载期初始化 GraphicsLib，并在初始化后读取 shader 能力；
- 在战斗首帧创建一盏跟随舰船的 `StandardLight`；
- 用武器开火状态驱动目标强度，并按帧时间平滑追赶；
- 在玩家舰销毁时让常驻光源淡出并退出光源列表；
- 从初始化时机、战斗本地数据和材质贴图定位灯光故障。

## 前置知识

- [C6-S2] 理解 Java 类、方法、字段与入口插件；
- [C7-S1] 理解每帧回调、暂停判断与运行时对象；
- 能够用 Gradle 构建示例 MOD，并从 `starsector.log` 定位类名。

## 明确当前需求

玩家舰主炮开火时船体周围出现暖色增强光，停火后约半秒暗回基础亮度。GraphicsLib 保持硬依赖；用户关闭 shader 或缓冲能力时，报告器给出当前能力状态，战斗逻辑继续运行。

## 文件与 API 链路

### 灯光 API 职责速查表

| 对象 | 职责 |
|---|---|
| `ShaderLib` | 应用加载期初始化 |
| `LightShader` | 管理当前战斗光源 |
| `StandardLight` | 保存位置、颜色、大小与强度 |
| `ShaderAPI` | 与配置开关共同决定能力是否可用 |

### 在应用加载期初始化 GraphicsLib

**插入位置：`GearInModPlugin.onApplicationLoad()`。** 让纹理和 shader 能力在战斗前准备完成。

```java
ShaderLib.init();
```

完成信号：日志显示 GraphicsLib 初始化完成。

### 读取当前光源 shader 能力

**插入位置：`GearInLightReporter.reportCapabilities()`。** 在 GraphicsLib 初始化后取得 `LightShader` 的 `ShaderAPI`。

```java
ShaderAPI lightShader = ShaderLib.getShaderAPI(LightShader.class);
boolean lightReady = lightShader != null && lightShader.isEnabled();
```

完成信号：关闭光源 shader 时 lightReady 为 false，报告过程保持无异常。

### 把点光源挂到舰船宿主

**插入位置：`GearInShipLight.add()`。** 用宿主舰船和局部偏移构造 `StandardLight`。该方法由战斗首帧调用，此时 GraphicsLib 已建立当前战斗的本地数据。

```java
light = new StandardLight(player.getLocation(), new Vector2f(), OFFSET, player,
        BASE_INTENSITY, SIZE);
LightShader.addLight(light);
```

完成信号：舰船平移和转向时，光源按 60 su 偏移持续跟随。

### 检测本帧是否有武器开火

**插入位置：`GearInShipLight.update()` 的强度计算前。** 扫描全部武器，得到单一 `firing` 状态。

```java
boolean firing = false;
for (WeaponAPI weapon : ship.getAllWeapons()) firing |= weapon.isFiring();
```

完成信号：任一主武器开火时强度目标切到高值。

### 平滑追赶目标强度

**插入位置：`GearInShipLight.update()` 末尾。** 用 `amount` 限制每帧变化，避免亮度突跳。

```java
float target = firing ? 1.5f : 0.45f;
intensity += Math.signum(target - intensity) * Math.min(Math.abs(target - intensity), amount * 2f);
light.setIntensity(intensity);
```

完成信号：停火后灯光在约 0.5 秒内回到基础值。

## 排查关键故障

下面是**局部故障片段**：

```java
private static final ShaderAPI LIGHT_SHADER = ShaderLib.getShaderAPI(LightShader.class);
```

实际症状：GraphicsLib 已完成初始化，报告却始终认为 LightShader 不可用。

原因：类加载期过早缓存了 null，后续初始化不会替换这个 static final 引用。

下面是**局部修正片段**：

```java
ShaderAPI lightShader = ShaderLib.getShaderAPI(LightShader.class);
boolean lightReady = lightShader != null && lightShader.isEnabled();
```

复验：重启后在 `reportCapabilities()` 中读取一次，确认日志的 `lightShaderEnabled` 与 GraphicsLib 设置一致；查询发生在 `ShaderLib.init()` 之后。

## 完整最小例

三个类分别承担战斗接线、能力与纹理报告、光源生命周期。完整光源使用暖色、60 su 局部偏移、150 su 尺寸，并在舰船死亡时执行 1 秒淡出。

## 验证方案

前置数据：GraphicsLib 已启用，`ShaderLib.init()` 已在应用加载期执行，战斗插件已经注册。

1. 启动战斗，搜索 `GearIn light capabilities`、`GearIn light data` 与 `GearIn light texture` 日志。
2. 保持武器停火，确认玩家舰后方 60 su 处存在基础强度为 0.45 的暖色光。
3. 持续开火至少 1 秒，观察强度追到 1.5；停火并计时，观察其约 0.525 秒后回到 0.45。
4. 暂停战斗，确认本页插件不推进强度；恢复后继续追赶目标值。
5. 摧毁玩家舰，搜索淡出日志并观察光源在 1 秒淡出后消失。
6. 关闭 GraphicsLib 的光源 shader，重启并核对能力日志与战斗稳定性。

预期结果：

- 能力日志反映 shader、缓冲和 `LightShader` 的当前开关状态；
- 光源以宿主局部偏移跟随舰船平移与转向，颜色与完整成品一致；
- 任一武器开火时目标强度为 1.5，全部停火时目标强度为 0.45；
- 60 FPS 与 30 FPS 下回落时长保持约 0.525 秒，不随帧率翻倍；
- 玩家舰死亡后光源进入淡出，不永久残留在战场；
- 光源 shader 关闭时报告过程和战斗逻辑没有未处理异常。

## 故障分析

- **能力检查始终认为 `LightShader` 不可用**：类加载期缓存了初始化前的 null。改为在 `ShaderLib.init()` 后按需查询，进入战斗确认能力状态变为可用。
- **光源停在出生点**：`StandardLight` 没有绑定宿主舰船和局部偏移。传入宿主与偏移，移动舰船后确认光源同步移动。
- **舰船离场后灯光永久残留**：死亡或移除路径没有调用 `fadeOut`。在离场回调中淡出并移除光源，结束战斗后确认光源列表为空。
- **灯光存在但船体材质没有响应**：光源 shader 已启用，但法线或材质纹理没有注册。补齐纹理与材质资源，开火后确认船体受光变化。

## 总结

动态灯光的重点是生命周期和状态来源。初始化后读取当前能力，用宿主和偏移建立挂接，以武器开火驱动目标强度，并在舰船离场时淡出，画面才会稳定。

## 建议参考项

- `LightShader.addLight`：把动态光源加入当前战斗；
- `StandardLight`：通过宿主与偏移配置跟随点光源；
- `ShaderLib.getShaderAPI`：读取当前 LightShader 能力。
::: details 完整成品：GearInLightCombatPlugin

```java
package data.scripts.gearin.visuals;

import java.util.List;

import org.dark.shaders.light.LightData.LightDataType;

import com.fs.starfarer.api.combat.BaseEveryFrameCombatPlugin;
import com.fs.starfarer.api.combat.CombatEngineAPI;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.input.InputEventAPI;

/**
 * 完整最小例：GraphicsLib 光源与纹理的最小消费面(战斗内)的接线插件。
 *
 * 适用边界：
 * - GraphicsLib 的初始化与 LightData/TextureData CSV 注册发生在 onApplicationLoad
 *   (本 MOD 的接入片段见页面正文，本节示例类不含该片段)；
 * - ShaderHook(settings plugins 注册的 EveryFrameCombatPlugin)在每场战斗开始时
 *   创建 LightShader 的战斗 LocalData(engine customData 键 "shaderlib_LightShader")，
 *   并注册一个覆盖全部层的分层渲染钩子；LightShader 声明在
 *   ABOVE_SHIPS_AND_MISSILES_LAYER 层渲染(ShaderAPI.isCombat() == true)；
 * - LightShader.addLight 只在光源 shader 已启用且战斗 LocalData 已存在时把光源写进
 *   当前战斗的光源列表，其余情况静默跳过 —— 动态光源必须在战斗内添加；
 * - 光源列表随战斗销毁；未永久化的光源在 superLifetime(60 秒)耗尽后被 failsafe
 *   移除；advance 返回 true(size 或 intensity 归零)的光源由 LightShader.advance
 *   逐帧移除，fadeOut 是标准自灭姿势；
 * - 暂停时 LightShader.advance 不推进光源(光源静止但保持可见)。
 *
 * 本类只做接入：GearInShipLight 根据玩家舰武器开火状态调整挂载光，
 * GearInLightReporter 报告 GraphicsLib 能力和数据注册状态。
 */
public class GearInLightCombatPlugin extends BaseEveryFrameCombatPlugin {

    private CombatEngineAPI engine;
    private final GearInShipLight shipLight = new GearInShipLight();

    @Override
    public void init(CombatEngineAPI engine) {
        this.engine = engine;
        GearInLightReporter.reportCapabilities();
        GearInLightReporter.reportLightData("lightac_shot", LightDataType.PROJECTILE);
        GearInLightReporter.reportLightData("pdlaser", LightDataType.BEAM);
        GearInLightReporter.reportShipTextures(engine);
    }

    @Override
    public void advance(float amount, List<InputEventAPI> events) {
        if (engine == null) {
            return; // 防御性判空(C7-S1 同款姿势)
        }
        if (engine.isPaused()) {
            return; // 暂停守卫(C7-S1)：暂停时 advance 仍会被调用，光源静止但保持可见
        }
        ShipAPI player = engine.getPlayerShip();
        shipLight.update(amount, player);
    }
}
```

:::

::: details 完整成品：GearInLightReporter

```java
package data.scripts.gearin.visuals;

import org.apache.log4j.Logger;
import org.dark.shaders.light.LightData;
import org.dark.shaders.light.LightData.LightDataType;
import org.dark.shaders.light.LightEntry;
import org.dark.shaders.light.LightShader;
import org.dark.shaders.util.ShaderAPI;
import org.dark.shaders.util.ShaderLib;
import org.dark.shaders.util.TextureData;
import org.dark.shaders.util.TextureData.ObjectType;
import org.dark.shaders.util.TextureData.TextureDataType;
import org.dark.shaders.util.TextureEntry;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.CombatEngineAPI;
import com.fs.starfarer.api.combat.ShipAPI;

/**
 * GraphicsLib 初始化状态与数据注册状态的日志报告(查询面，注册/初始化报告类)。
 *
 * 报告内容：能力门禁与光源 shader 可用性(必须在 ShaderLib.init 之后读取)、
 * LightData 注册表查询(projectile/beam 自动光源数据)、TextureData 注册表查询
 * (玩家舰船船体的 material/normal/surface 贴图条目)。只做查询与日志，不创建光源。
 */
public class GearInLightReporter {

    private static final Logger LOG = Global.getLogger(GearInLightReporter.class);

    /** 能力门禁与光源 shader 可用性报告(必须在 ShaderLib.init 之后读取)。 */
    public static void reportCapabilities() {
        boolean shaders = ShaderLib.areShadersAllowed();
        boolean buffers = ShaderLib.areBuffersAllowed();
        ShaderAPI lightShader = ShaderLib.getShaderAPI(LightShader.class);
        boolean lightReady = lightShader != null && lightShader.isEnabled();
        LOG.info("GearIn light capabilities: shaders=" + shaders + " buffers=" + buffers
                + " lightShaderEnabled=" + lightReady);
    }

    /** 查询 LightData 注册表(projectile/beam 自动光源数据)，报告消费侧查询面。 */
    public static void reportLightData(String id, LightDataType type) {
        LightEntry entry = LightData.getLightData(id, type);
        if (entry == null) {
            LOG.info("GearIn light data: " + id + " (" + type.name().toLowerCase() + ") not registered");
            return;
        }
        LOG.info("GearIn light data: " + id + " (" + type.name().toLowerCase() + ")"
                + " standard=" + entry.hasStandard
                + " size=" + entry.standardSize
                + " intensity=" + entry.standardIntensity
                + " chance=" + entry.chance
                + " fighterDim=" + entry.fighterDim);
    }

    /** 查询 TextureData 注册表：玩家舰船船体的 material/normal/surface 贴图条目。 */
    public static void reportShipTextures(CombatEngineAPI engine) {
        ShipAPI player = engine.getPlayerShip();
        if (player == null) {
            return;
        }
        String hullId = player.getHullSpec().getHullId();
        LOG.info("GearIn light texture: hull=" + hullId
                + " material=" + describeTexture(hullId, TextureDataType.MATERIAL_MAP)
                + " normal=" + describeTexture(hullId, TextureDataType.NORMAL_MAP)
                + " surface=" + describeTexture(hullId, TextureDataType.SURFACE_MAP)
                + " shipTex=" + describeEntry(
                        ShaderLib.getShipTexture(player, TextureDataType.MATERIAL_MAP)));
    }

    private static String describeTexture(String id, TextureDataType map) {
        return describeEntry(TextureData.getTextureData(id, map, ObjectType.SHIP, 0));
    }

    private static String describeEntry(TextureEntry entry) {
        if (entry == null) {
            return "none";
        }
        return "mag=" + entry.magnitude + " tex=" + entry.sprite.getTextureId();
    }
}
```

:::

::: details 完整成品：GearInShipLight

```java
package data.scripts.gearin.visuals;

import java.awt.Color;

import org.apache.log4j.Logger;
import org.dark.shaders.light.LightShader;
import org.dark.shaders.light.StandardLight;
import org.lwjgl.util.vector.Vector2f;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.combat.WeaponAPI;

/**
 * 挂接到玩家舰船的常驻点光源实体(光源实体类)。
 *
 * 生命周期：战斗首帧由 GearInLightCombatPlugin 驱动 update 时创建(此时战斗
 * LocalData 已存在，addLight 不再静默跳过)，makePermanent 关闭 60 秒 failsafe；
 * 玩家舰船死亡后 fadeOut(1f) 自灭，由 LightShader.advance 在 intensity 归零后移除。
 */
public class GearInShipLight {

    private static final Logger LOG = Global.getLogger(GearInShipLight.class);

    /** 挂接到玩家舰船的暖色点光源参数。 */
    private static final Vector2f OFFSET = new Vector2f(0f, 60f);
    private static final float BASE_INTENSITY = 0.45f;
    private static final float FIRING_INTENSITY = 1.5f;
    private static final float INTENSITY_CHANGE_PER_SECOND = 2f;
    private static final float SIZE = 150f;

    private StandardLight light;
    private boolean added;
    private float intensity = BASE_INTENSITY;

    /** 每帧推进：首帧创建挂接光，舰船死亡后主动 fadeOut 让引擎移除。 */
    public void update(float amount, ShipAPI player) {
        if (player == null) {
            return;
        }
        if (light == null) {
            if (!added && player.isAlive()) {
                add(player); // 延迟到战斗首帧：保证战斗 LocalData 已存在
            }
            return;
        }
        if (!player.isAlive() && !light.isFadingOut()) {
            light.fadeOut(1f);
            LOG.info("GearIn light ship: player destroyed, fading out attached light");
            return;
        }
        boolean firing = false;
        for (WeaponAPI weapon : player.getAllWeapons()) {
            firing |= weapon.isFiring();
        }
        float target = firing ? FIRING_INTENSITY : BASE_INTENSITY;
        float change = Math.min(Math.abs(target - intensity),
                amount * INTENSITY_CHANGE_PER_SECOND);
        intensity += Math.signum(target - intensity) * change;
        light.setIntensity(intensity);
    }

    /** 创建并挂接常驻点光源：跟随舰船移动，关闭 60 秒 failsafe。 */
    private void add(ShipAPI player) {
        added = true;
        light = new StandardLight(player.getLocation(), new Vector2f(), OFFSET, player,
                BASE_INTENSITY, SIZE);
        light.setColor(new Color(255, 210, 130));
        light.makePermanent(); // lifetime 与 superLifetime 均置 -1，不再受 failsafe 限制
        LightShader.addLight(light);
        LOG.info("GearIn light ship: attached light added offset=" + OFFSET
                + " size=" + SIZE + " intensity=" + BASE_INTENSITY);
    }
}
```

:::
