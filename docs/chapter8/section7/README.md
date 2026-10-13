# 制作分层渲染插件

前面已经能取得舰船和修改数值，这次把结果画到战场上：给玩家舰加一个青色标记，让它跟随舰船、在暂停时保持位置，并随舰船离场而结束。先用 `SpriteAPI` 完成贴图绘制，再由分层插件安排显示位置和生命周期；图层、坐标与视口范围都需要对应这一个标记。

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

::: tip 本节的绘制入口
`SpriteAPI` 已经提供贴图尺寸、颜色和绘制方法，本节直接使用它。OpenGL 的资源与绘制状态放在后方专题展开。
:::

## 在玩家舰部署后创建标记

创建 `src/data/scripts/gearin/combat/GearInLayeredRenderer.java`，继承 `BaseCombatLayeredRenderingPlugin`。它提供默认实现，我们覆写当前标记需要的方法。完整类在后面给出，先接上创建入口。

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

玩家舰尚未部署时引用为空，后续帧继续等待。取得舰船后创建一次，颜色保存在这个渲染实例中。**逐帧检查创建条件，成功后停止创建。** 在每一帧无条件添加，几十个重叠标记很快就会把颜色和性能一起改掉。

引擎为插件创建承载实体，并将它传给 `init(...)`。这个实体提供位置和视口剔除依据；我们的 `trackedShip` 则提供被追踪舰船的位置与存活状态，两者的用途分别保留。

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

枚举声明顺序就是绘制顺序。选择层时先看需要覆盖什么，再看它附近还有什么效果；当前标记要盖在舰船与导弹上方，因此选择这一层。

```java
@Override
public EnumSet<CombatEngineLayers> getActiveLayers() {
    return EnumSet.of(layer);
}
```

引擎只在返回的图层调用当前插件。声明多个层时，每层会分别收到 `render(...)`，再用它的 `layer` 参数选择该层内容。

## 注册、取得并配置 Sprite

### 在 `settings.json` 注册 Sprite

先把贴图登记到 `data/config/settings.json` 的 `graphics` 对象中。本例新建 `gearin` 分类，并把 `layered_marker` 指向用于战斗标记的图片：

```json
{
  "graphics": {
    "gearin": {
      "layered_marker": "graphics/fx/particlealpha32sq.png"
    }
  }
}
```

代码会按 `gearin` 分类中的 `layered_marker` 键取得 Sprite。值是游戏加载的资源路径，当前沿用原版 `graphics/fx/particlealpha32sq.png`；自有图片按相对 MOD 根目录的路径填写。分类与键保持不变，替换图片时修改路径即可。

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

这里分为两次查找：`getSprite(...)` 查分类与键，登记值指向实际图片。加载失败时顺着这两个关系检查，位置和大小稍后再看。

### 设置尺寸、颜色和透明度

当前标记使用 120 世界单位的正方形，设置初始外观：

```java
sprite.setSize(MARKER_SIZE, MARKER_SIZE);
sprite.setColor(color);
sprite.setAlphaMult(1f);
```

`setSize(...)` 使用世界单位设置宽高，`setColor(...)` 设置颜色和基础 Alpha，`setAlphaMult(...)` 再乘上一层透明度倍率。`setCenter(...)` 调整贴图中心，`setAngle(...)` 旋转贴图；本例使用默认中心和角度。`renderAtCenter(...)` 按中心坐标绘制，`render(...)` 则按 Sprite 的角点位置绘制。

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

**承载实体的位置同时参与绘制和视口剔除。** `init(...)` 里先把它放到舰船位置，即使开场立即暂停，标记也能显示在正确位置：

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

`getRenderRadius()` 给出以承载实体为中心的剔除半径。本例返回 `MARKER_SIZE`，足以覆盖 120 单位的正方形。位置来自当前舰船，每场创建新的渲染实例。

```java
@Override
public float getRenderRadius() {
    return MARKER_SIZE;
}
```

## 在 `render` 中绘制 Sprite

轮到当前图层时，`render(...)` 写入这个实例使用的颜色与透明度，再按承载实体中心绘制。局部方法如下：

```java
@Override
public void render(CombatEngineLayers layer, ViewportAPI viewport) {
    sprite.setColor(color);
    sprite.setAlphaMult(1f);
    sprite.renderAtCenter(entity.getLocation().x, entity.getLocation().y);
}
```

::: tip 透明度
`setAlphaMult(...)` 的常用范围为 0 到 1。当前倍率为 1，最终仍受 `Color` 的基础 Alpha 180 影响；把倍率改为 0.5，可以观察颜色透明度再减半。
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

本例的结束动作只有记录日志。功能持有额外资源或需要恢复外观状态时，把相应释放与恢复放进 `cleanup()`；它服务的是这个实例的收尾。

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

标记由当前战斗插件在玩家舰部署后创建。贴图按分类与键取得，承载实体从初始化开始跟随舰船，图层与半径决定它在哪里绘制、何时进入视口。

编者建议先把这个静态标记的位置、遮挡和结束条件看清，再增加动画。颜色会动、尺寸会变之后，坐标错误往往也跟着热闹起来。

## 建议参考项

- `com.fs.starfarer.api.combat.BaseCombatLayeredRenderingPlugin`：分层插件的默认实现；
- `com.fs.starfarer.api.graphics.SpriteAPI`：贴图颜色、透明度、尺寸和绘制方法；
- `com.fs.starfarer.api.SettingsAPI`：`getSprite(String, String)` 按注册的分类和键取得 Sprite；
- `com.fs.starfarer.api.combat.CombatEngineLayers`：战斗渲染图层枚举。
