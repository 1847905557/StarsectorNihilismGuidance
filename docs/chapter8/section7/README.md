# 制作分层渲染插件

本节给玩家舰绘制一个青色标记，让它跟随舰船，并在舰船死亡或离场后结束。贴图由 `SpriteAPI` 绘制，分层插件负责图层、位置和清理。除了画在哪里，还要让视口剔除使用正确的位置，标记才能在镜头移动时正常显示。

*本节预期阅读时间：\~16 分钟。*

*本节预期操作时间：\~25 分钟。*

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
- [C6-S4] 的 JAR 构建与游戏加载。

## 准备标记的 Sprite

`SpriteAPI` 提供尺寸、颜色与绘制方法，本节直接使用它，OpenGL 的资源与状态在后方专题展开。先把标记贴图登记到 `data/config/settings.json` 的 `graphics` 对象：

```json
{
  "graphics": {
    "gearin": {
      "layered_marker": "graphics/fx/particlealpha32sq.png"
    }
  }
}
```

`gearin` 是分类，`layered_marker` 是代码查找的键，值指向实际图片。本例沿用原版 `graphics/fx/particlealpha32sq.png`。改用自有图片时，填写相对 MOD 根目录的路径，代码中的分类与键可以继续保留。

已有 `graphics` 对象时，将 `gearin` 加入其中，避免在同一个 JSON 对象中重复声明 `graphics`。

## 在玩家舰部署后创建标记

创建 `src/data/scripts/gearin/combat/GearInLayeredRenderer.java`，继承 `BaseCombatLayeredRenderingPlugin`。它提供默认实现，我们覆写标记需要的初始化、更新、绘制和结束方法，完整类放在后面。

沿用 [C8-S1] 的 `GearInCombatStatePlugin` 及其登记，增加 `java.awt.Color` 和 `com.fs.starfarer.api.combat.ShipAPI` 的 `import`，字段区加入：

```java
private boolean markerCreated;
```

在 `advance(...)` 绑定引擎之后、暂停判断之前加入局部片段：

```java
if (!markerCreated) {
    ShipAPI ship = engine.getPlayerShip();
    if (ship != null) {
        engine.addLayeredRenderingPlugin(
                new GearInLayeredRenderer(ship, new Color(0, 255, 255, 180)));
        markerCreated = true;
    }
}
```

玩家舰尚未部署时没有可用引用，后续帧继续等待。取得舰船之后创建一份标记，将颜色交给它，并设置 `markerCreated`。**创建成功后停止重复添加。** 若每帧都加一份，重叠标记很快就会把颜色和性能一起改掉。

`addLayeredRenderingPlugin(...)` 让引擎为插件创建承载实体，再将这个实体传给 `init(...)`。它的位置参与绘制和视口剔除，`trackedShip` 则提供舰船位置与存活状态。后面的更新将两者位置同步，完成跟随。

## 决定标记与其他效果的遮挡关系

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

枚举声明顺序就是绘制顺序。本例需要盖在舰船与导弹上，因此选取这一层；其他效果则按实际遮挡关系决定位置。

```java
@Override
public EnumSet<CombatEngineLayers> getActiveLayers() {
    return EnumSet.of(layer);
}
```

引擎在 `getActiveLayers()` 返回的图层调用 `render(...)`。声明多个层时，每层分别调用一次，方法根据传入的 `layer` 绘制该层内容。

## 初始化贴图外观

### 取得贴图

在 `init(...)` 中使用 `Global.getSettings().getSprite(String category, String key)`，按前面的分类与键取得 `SpriteAPI`：

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

代码查找 `gearin/layered_marker`，设置中的值再指向图片。资源无法取得时，就沿分类、键和实际路径检查。取得后继续设置标记外观。

### 设置尺寸、颜色和透明度

当前标记使用 120 世界单位的正方形，设置初始外观：

```java
sprite.setSize(MARKER_SIZE, MARKER_SIZE);
sprite.setColor(color);
sprite.setAlphaMult(1f);
```

`setSize(...)` 设置世界单位的宽高，`setColor(...)` 提供颜色和基础 Alpha，`setAlphaMult(...)` 再乘透明度倍率。标记使用默认中心与角度，需要调整时分别调用 `setCenter(...)` 和 `setAngle(...)`。

绘制时，`renderAtCenter(...)` 将传入坐标作为中心，`render(...)` 则采用 Sprite 角点位置。本例要让标记中心跟随舰船，因此使用前者。

### `SpriteAPI` 常用属性速查表

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

**承载实体的位置同时参与绘制和视口剔除。** 在 `init(...)` 中就把它放到舰船位置，开场立即暂停时也能取得正确的初始位置：

```java
entity.getLocation().set(trackedShip.getLocation());
```

随后在非暂停帧同步位置，舰船离场或死亡则令实例失效：

```java
if (engine.isPaused()) {
    return;
}
if (!engine.isEntityInPlay(trackedShip) || !trackedShip.isAlive()) {
    valid = false;
    return;
}
elapsed += amount;
entity.getLocation().set(trackedShip.getLocation());
```

`getRenderRadius()` 给出以承载实体为中心的剔除半径。标记使用 120 世界单位的正方形，返回 `MARKER_SIZE` 足以覆盖这块区域。镜头移出范围时，引擎就能据此决定是否绘制：

```java
@Override
public float getRenderRadius() {
    return MARKER_SIZE;
}
```

## 在 `render` 中绘制 Sprite

轮到本例图层时，`render(...)` 设置实例使用的颜色与透明度，再按承载实体中心绘制：

```java
@Override
public void render(CombatEngineLayers layer, ViewportAPI viewport) {
    sprite.setColor(color);
    sprite.setAlphaMult(1f);
    sprite.renderAtCenter(entity.getLocation().x, entity.getLocation().y);
}
```

当前倍率为 1，基础 Alpha 仍是构造时的 180。`setAlphaMult(...)` 常用范围为 0 到 1，改为 0.5 就再降低一半透明度。中心坐标来自承载实体，它已经在非暂停更新中跟随舰船。

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

本例在 `cleanup()` 记录累计时间。若功能还持有额外资源或改变了需要恢复的外观状态，也在这个实例的清理方法中完成释放与恢复。下一场战斗重新创建标记和相关引用。

## 完整最小例

把下面的类保存到前述路径，保留 Sprite 登记与状态插件中的创建片段。

::: details 完整成品：`GearInLayeredRenderer`

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

/** 跟随当前舰船，在指定图层绘制 Sprite 标记。 */
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
        entity.getLocation().set(trackedShip.getLocation());
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
        if (engine.isPaused()) {
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
        sprite.setColor(color);
        sprite.setAlphaMult(1f);
        sprite.renderAtCenter(entity.getLocation().x, entity.getLocation().y);
    }
}
```

:::

## 验证方案

1. 在 MOD 目录执行 `gradle jar`，确认 JAR 中包含 `data/scripts/gearin/combat/GearInLayeredRenderer.class`。
2. 重启游戏，进入 `GearIn_test` 并部署玩家舰，确认 `gearin/layered_marker` 登记与创建片段已生效。
3. 进入 `GearIn_test` 战斗，确认日志出现 `GearIn layered renderer init`。
4. 观察舰船旁的 Sprite 标记，确认颜色、透明度和尺寸符合构造器与 Sprite 设置。
5. 移动被追踪舰船，确认 Sprite 中心与舰船位置同步。
6. 暂停战斗，确认标记保持最后位置；恢复后继续跟随。下一场部署后立即暂停，再确认初始化位置。
7. 平移镜头使标记及其覆盖范围离开视口，再移回观察显示恢复；缩放时比较标记与舰船的相对尺寸。
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
    - **解决方案**：在 `init(...)` 中设置初始位置，并在非暂停 `advance(...)` 中同步舰船位置。

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

标记现在随玩家舰创建、移动与结束。Sprite 提供外观，图层提供遮挡顺序，承载实体的位置与半径共同决定绘制和剔除，失效状态再连接到清理。

增加动画时仍然沿用这些关系。颜色和尺寸可以变化，跟随位置、视口覆盖范围与结束条件也要继续对应实际画面。

## 建议参考项

- `com.fs.starfarer.api.combat.BaseCombatLayeredRenderingPlugin`：分层插件的默认实现；
- `com.fs.starfarer.api.graphics.SpriteAPI`：贴图颜色、透明度、尺寸和绘制方法；
- `com.fs.starfarer.api.SettingsAPI`：`getSprite(String, String)` 按注册的分类和键取得 Sprite；
- `com.fs.starfarer.api.combat.CombatEngineLayers`：战斗渲染图层枚举。
