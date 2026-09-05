# 制作分层渲染插件

本节制作一个跟随舰船移动的战斗标记，并把它绘制到指定的战斗图层。`BaseCombatLayeredRenderingPlugin` 负责插件的生命周期和位置，`SpriteAPI` 负责贴图的取得、外观配置与最终绘制。

*本节预期阅读时间：~16 分钟。*

*本节预期操作时间：~25 分钟。*

## 学习目标

完成后，您将能够：

- 继承 `BaseCombatLayeredRenderingPlugin` 并注册分层插件；
- 在 `settings.json` 注册 Sprite，并使用 `SpriteAPI` 配置和绘制贴图；
- 根据承载实体、视口和失效状态定位渲染问题。

## 前置知识

- [C8-S3] 的每帧插件、暂停判断和战斗实例边界；
- [C8-S1] 的战斗引擎状态；
- [C8-S2] 的战斗对象与舰船位置；
- [C4-S1] 的 `GearIn_test` 测试战役；
- [C6-S1] 的 Java 构建与 JAR 输出。

::: tip 先用 Sprite 完成绘制
OpenGL 属于更进阶的渲染主题。本节使用游戏提供的 `SpriteAPI` 完成贴图绘制；更底层的 OpenGL 资源和绘制状态放在后方专题讲解。
:::

## 继承分层渲染插件

`BaseCombatLayeredRenderingPlugin` 提供分层渲染插件的默认实现。它和 `BaseEveryFrameCombatPlugin` 较为相似。当前示例只覆写实际需要的几个方法：`init(...)` 接收引擎创建的承载实体，`getActiveLayers()` 选择图层，`getRenderRadius()` 提供视口剔除半径，`advance(...)` 同步位置，`render(...)` 绘制 Sprite，`isExpired()` 与 `cleanup()` 结束实例。

插件注册在战斗引擎中完成。下面的片段把一个跟踪舰船的插件加入当前战斗：

```java
if (ship != null) {
    engine.addLayeredRenderingPlugin(
            new GearInLayeredRenderer(ship, new Color(0, 255, 255, 180)));
}
```

`addLayeredRenderingPlugin(...)` 接受插件对象。引擎随后为它创建承载实体并调用 `init(...)`；颜色参数保存在插件实例中，供 Sprite 设置颜色时使用。

## 选择渲染图层

本例把标记放在 `ABOVE_SHIPS_AND_MISSILES_LAYER`，这样它会显示在舰船和导弹之上。`getActiveLayers()` 返回这个枚举值，`render(...)` 收到的 `layer` 参数就是当前被调用的图层。

### CombatEngineLayers 图层速查表

以下代码块保留完整枚举，便于按照名称查找图层并比较本例的位置：

```text
BELOW_PLANETS
PLANET_LAYER
ABOVE_PLANETS
CLOUD_LAYER
BELOW_SHIPS_LAYER
UNDER_SHIPS_LAYER
ASTEROIDS_LAYER
CAPITAL_SHIPS_LAYER
CRUISERS_LAYER
DESTROYERS_LAYER
FRIGATES_LAYER
BELOW_PHASED_SHIPS_LAYER
PHASED_SHIPS_LAYER
STATION_WEAPONS_LAYER
CONTRAILS_LAYER
FIGHTERS_LAYER
BELOW_INDICATORS_LAYER
FF_INDICATORS_LAYER
ABOVE_SHIPS_LAYER
ABOVE_SHIPS_AND_MISSILES_LAYER
ABOVE_PARTICLES_LOWER
ABOVE_PARTICLES
JUST_BELOW_WIDGETS
```

枚举声明顺序就是图层顺序。完整表用于查阅名称和相邻关系，实际插件只返回自己需要的层。

```java
@Override
public EnumSet<CombatEngineLayers> getActiveLayers() {
    return EnumSet.of(layer);
}
```

这个实现让引擎只在声明的图层调用当前插件；使用多个图层时，再根据 `layer` 参数决定每次绘制内容。

## 注册、取得并配置 Sprite

### 在 settings.json 注册 Sprite

Java 代码通过分类和键读取 Sprite，因此先在 `data/config/settings.json` 的 `graphics` 对象中登记贴图。本例新建 `gearin` 分类，并把 `layered_marker` 指向用于战斗标记的图片：

```json
{
  "graphics": {
    "gearin": {
      "layered_marker": "graphics/fx/particlealpha32sq.png"
    }
  }
}
```

`gearin` 是 Sprite 分类，`layered_marker` 是分类内的键，字符串值是相对 MOD 根目录的资源路径。Java 代码使用的分类和键必须与这里完全一致；更换图片时只需修改注册路径，调用代码仍然读取同一个 Sprite ID。

如果 `settings.json` 已经包含 `graphics`，应把 `gearin` 加入现有对象；同一个 JSON 对象中只保留一个 `graphics` 键。

### 取得贴图

`Global.getSettings().getSprite(String category, String key)` 根据 `settings.json` 中的分类和键返回 `SpriteAPI`。插件初始化时使用刚才注册的 `gearin/layered_marker`：

```java
private static final String SPRITE_CATEGORY = "gearin";
private static final String SPRITE_KEY = "layered_marker";

private SpriteAPI sprite;

@Override
public void init(CombatEntityAPI entity) {
    super.init(entity);
    sprite = Global.getSettings().getSprite(SPRITE_CATEGORY, SPRITE_KEY);
}
```

分类或键写错时，Java 无法取得已注册的 Sprite；注册值中的路径写错时，引擎无法加载对应图片。排查时先核对分类和键，再核对资源路径与文件名。

### 设置尺寸、颜色和透明度

取得 Sprite 后设置它的初始外观：

```java
sprite.setSize(MARKER_SIZE, MARKER_SIZE);
sprite.setColor(color);
sprite.setAlphaMult(1f);
```

`setSize(...)` 使用世界单位设置宽高，`setColor(...)` 设置颜色和基础 Alpha，`setAlphaMult(...)` 再乘上一层透明度倍率。`setCenter(...)` 调整贴图中心，`setAngle(...)` 旋转贴图；本例使用默认中心和角度。`renderAtCenter(...)` 按中心坐标绘制，`render(...)` 则按 Sprite 的角点位置绘制。

### SpriteAPI 常用属性速查表

| 方法 | 作用 |
|---|---|
| `setColor(Color)` | 设置颜色和基础 Alpha |
| `setAlphaMult(float)` | 乘上透明度倍率 |
| `setSize(float, float)` | 设置世界单位宽高 |
| `setCenter(float, float)` | 调整贴图中心 |
| `setAngle(float)` | 设置旋转角度 |
| `renderAtCenter(float, float)` | 以中心坐标绘制 |
| `render(float, float)` | 以角点坐标绘制 |

## 同步承载实体位置

引擎根据承载实体的位置进行视口剔除，因此位置同步和 Sprite 绘制必须使用同一坐标。`advance(...)` 在非暂停帧读取舰船位置，并在舰船离场或死亡时让插件失效：

```java
if (engine == null || engine.isPaused()) {
    return;
}
if (!engine.isEntityInPlay(trackedShip) || !trackedShip.isAlive()) {
    valid = false;
    return;
}
elapsed += amount;
entity.getLocation().set(trackedShip.getLocation());
```

`getRenderRadius()` 返回 `MARKER_SIZE`，覆盖标记的绘制范围。位置只从当前舰船读取，不把上一场战斗的坐标带入新的插件实例。

```java
@Override
public float getRenderRadius() {
    return MARKER_SIZE;
}
```

## 在 render 中绘制 Sprite

`render(...)` 只提交当前图层的 Sprite。在本例中，为了便于演示，每次调用都重新写入颜色和透明度倍率：

```java
@Override
public void render(CombatEngineLayers layer, ViewportAPI viewport) {
    if (sprite == null || entity == null) {
        return;
    }
    sprite.setColor(color);
    sprite.setAlphaMult(1f);
    sprite.renderAtCenter(entity.getLocation().x, entity.getLocation().y);
}
```

::: tip 透明度
透明度在 `SpriteAPI` 里一般取 0~1f，1f 即为不透明。
:::

`renderAtCenter(...)` 使用承载实体的世界坐标，因此标记中心会跟随舰船。

## 处理失效与清理

舰船离场或死亡后，`isExpired()` 返回 `true`，引擎移除承载实体并调用 `cleanup()`：

```java
@Override
public boolean isExpired() {
    return !valid;
}

@Override
public void cleanup() {
    LOG.info("GearIn layered renderer cleanup: elapsed=" + elapsed);
}
```

本例只持有 Sprite 引用，没有额外资源句柄，因此清理阶段记录日志即可。后续如果保存了 Sprite 状态或其他资源，也在 `cleanup()` 中恢复和释放。

## 完整最小例

下面的完整类把 Sprite 读取与配置、位置同步、图层声明、绘制和失效清理组合在一起。

::: details 完整成品：GearInLayeredRenderer

```java
package data.scripts.gearin.combat;

import java.awt.Color;
import java.util.EnumSet;

import org.apache.log4j.Logger;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.BaseCombatLayeredRenderingPlugin;
import com.fs.starfarer.api.combat.CombatEngineAPI;
import com.fs.starfarer.api.combat.CombatEngineLayers;
import com.fs.starfarer.api.combat.CombatEntityAPI;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.combat.ViewportAPI;
import com.fs.starfarer.api.graphics.SpriteAPI;

/**
 * 完整最小例：使用 BaseCombatLayeredRenderingPlugin 和 SpriteAPI 绘制战斗标记。
 *
 * 插件实例绑定一艘舰船和一个 Sprite：
 * - init() 保存承载实体、取得当前战斗引擎并读取已注册的 Sprite；
 * - advance() 在非暂停帧同步承载实体的位置，并在舰船离场时让插件失效；
 * - render() 设置 Sprite 的颜色和透明度，再以承载实体位置为中心绘制；
 * - isExpired() 与 cleanup() 负责结束当前实例。
 */
public class GearInLayeredRenderer extends BaseCombatLayeredRenderingPlugin {

    private static final Logger LOG = Global.getLogger(GearInLayeredRenderer.class);

    private static final String SPRITE_CATEGORY = "gearin";
    private static final String SPRITE_KEY = "layered_marker";
    private static final float MARKER_SIZE = 120f;

    private final ShipAPI trackedShip;
    private final Color color;

    private CombatEngineAPI engine;
    private SpriteAPI sprite;
    private boolean valid = true;
    private float elapsed = 0f;

    public GearInLayeredRenderer(ShipAPI trackedShip, Color color) {
        this.trackedShip = trackedShip;
        this.color = color;
        this.layer = CombatEngineLayers.ABOVE_SHIPS_AND_MISSILES_LAYER;
    }

    @Override
    public void init(CombatEntityAPI entity) {
        super.init(entity);
        engine = Global.getCombatEngine();
        sprite = Global.getSettings().getSprite(SPRITE_CATEGORY, SPRITE_KEY);
        sprite.setSize(MARKER_SIZE, MARKER_SIZE);
        sprite.setColor(color);
        LOG.info("GearIn layered renderer init: layers=" + getActiveLayers());
    }

    @Override
    public EnumSet<CombatEngineLayers> getActiveLayers() {
        return EnumSet.of(layer);
    }

    @Override
    public float getRenderRadius() {
        return MARKER_SIZE;
    }

    @Override
    public void advance(float amount) {
        if (engine == null || engine.isPaused()) {
            return;
        }
        if (!engine.isEntityInPlay(trackedShip) || !trackedShip.isAlive()) {
            valid = false;
            return;
        }
        elapsed += amount;
        entity.getLocation().set(trackedShip.getLocation());
    }

    @Override
    public boolean isExpired() {
        return !valid;
    }

    @Override
    public void cleanup() {
        LOG.info("GearIn layered renderer cleanup: elapsed=" + elapsed);
    }

    @Override
    public void render(CombatEngineLayers layer, ViewportAPI viewport) {
        if (sprite == null || entity == null) {
            return;
        }
        sprite.setColor(color);
        sprite.setAlphaMult(1f);
        sprite.renderAtCenter(entity.getLocation().x, entity.getLocation().y);
    }
}
```

:::

## 验证方案

前置数据：已编译的 `GearInLayeredRenderer`、`GearIn_test` 测试战役，以及在 `settings.json` 中注册为 `gearin/layered_marker` 的 `graphics/fx/particlealpha32sq.png`。

1. 在 MOD 目录执行 `gradle jar`，确认 JAR 中包含 `data/scripts/gearin/combat/GearInLayeredRenderer.class`。
2. 启动游戏并确认 `settings.json` 中的 `gearin/layered_marker` 没有资源加载错误。
3. 进入 `GearIn_test` 战斗，确认日志出现 `GearIn layered renderer init`。
4. 观察舰船旁的 Sprite 标记，确认颜色、透明度和尺寸符合构造器与 Sprite 设置。
5. 移动被追踪舰船，确认 Sprite 中心与舰船位置同步。
6. 暂停战斗，确认标记保持最后位置并继续显示；恢复后继续跟随。
7. 拉远镜头使标记离开 `getRenderRadius()` 覆盖范围，再拉回确认剔除和恢复。
8. 摧毁被追踪舰船，确认标记消失并出现 `GearIn layered renderer cleanup`。
9. 退出并重新进入第二场战斗，确认 Sprite、位置和插件状态重新初始化。

预期结果：

- Sprite 能按注册的分类和键成功取得，并在指定图层显示；
- `setColor(...)`、`setAlphaMult(...)` 和 `setSize(...)` 的设置能在画面中观察；
- `renderAtCenter(...)` 使标记中心与舰船位置重合；
- 暂停时位置不推进，恢复后继续同步；
- 视口剔除范围与 `getRenderRadius()` 一致；
- 舰船失效后当前实例完成清理，第二场战斗使用新实例。

## 故障分析

- **游戏启动时报告 Sprite 不存在**：Java 使用的分类或键与 `settings.json` 注册值不一致。

    - **解决方案**：核对 `SPRITE_CATEGORY`、`SPRITE_KEY` 与 `gearin/layered_marker`，再检查注册路径 `graphics/fx/particlealpha32sq.png`。

- **标记颜色或透明度异常**：Sprite 没有在绘制时同步颜色或透明度倍率。

    - **解决方案**：在 `render(...)` 中重新调用 `setColor(color)` 和 `setAlphaMult(1f)`。

- **标记出现在战场原点**：承载实体位置没有跟随舰船更新。

    - **解决方案**：在 `advance(...)` 中调用 `entity.getLocation().set(trackedShip.getLocation())`。

- **标记进入视口后仍然消失**：`getRenderRadius()` 小于 Sprite 的实际覆盖范围。

    - **解决方案**：让返回值覆盖 `setSize(...)` 设置的标记尺寸，并再次缩放镜头验证。

- **暂停后标记继续移动**：暂停判断放在位置同步之后，或被省略。

    - **解决方案**：在 `advance(...)` 开头检查 `engine.isPaused()`，暂停时直接返回。

- **标记层级错误**：`getActiveLayers()` 返回的图层不符合预期。

    - **解决方案**：确认返回 `ABOVE_SHIPS_AND_MISSILES_LAYER`，并检查注册后的画面遮挡关系。

- **舰船销毁后标记仍然存在**：插件没有设置失效状态，或 `isExpired()` 没有返回失效结果。

    - **解决方案**：舰船不在引擎或不再存活时设置 `valid = false`，让引擎调用 `cleanup()`。

- **下一场战斗沿用上一场位置**：插件或舰船引用跨战斗保存。

    - **解决方案**：每场战斗重新创建 `GearInLayeredRenderer`，只在当前实例中保存 `trackedShip`、`engine` 和 `entity`。

## 总结

分层渲染插件继承 `BaseCombatLayeredRenderingPlugin`。Sprite 先在 `settings.json` 中注册分类和键，再由 `getSprite(category, key)` 读取；插件通过 `getActiveLayers()` 选择图层，在 `advance(...)` 中把承载实体同步到舰船位置，并在 `render(...)` 中使用颜色、透明度、尺寸和中心坐标绘制贴图。`getRenderRadius()` 决定视口剔除范围，舰船失效后由 `isExpired()` 触发 `cleanup()`；每场战斗都使用独立的插件和 Sprite 状态。

## 建议参考项

- [C8-S1]：战斗引擎状态；
- [C8-S2]：战斗对象与舰船位置；
- [C8-S3]：每帧战斗插件、暂停和实例边界；
- [C4-S1]：`GearIn_test` 测试战役；
- `BaseCombatLayeredRenderingPlugin`：分层插件的默认实现；
- `SpriteAPI`：贴图颜色、透明度、尺寸和绘制方法；
- `SettingsAPI.getSprite(String, String)`：按注册的分类和键取得 Sprite；
- `CombatEngineLayers`：战斗渲染图层枚举。
