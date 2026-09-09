# 用 BoxUtil 组合尾迹、扭曲与镜头光斑

尾迹说明弹体从哪里来，扭曲表现冲击波，光斑给出瞬间亮点，一次有重量的命中需要这三个时间尺度。本节分别创建三类实体，再由同一命中入口协调它们的寿命。

*本节预期阅读时间：~18 分钟。*

*本节预期操作时间：~30 分钟。*

## 学习目标

完成后，您将能够：

- 使用 `TrailEntity`、`DistortionEntity` 和 `FlareEntity` 组合命中视觉；
- 配置三个实体的位置、外观、寿命和计时阶段；
- 使用共享默认畸变，并为尾迹与光斑选择图层；
- 根据各自的入队结果保留或删除渲染实体。

## 前置知识

- [C7-S1] 理解 Java 类、方法、字段与入口插件；
- [C8-S3] 理解每帧回调与暂停判断；[C8-S2] 理解战斗对象读取与对象边界；
- 能够用 Gradle 构建示例 MOD，并从 `starsector.log` 定位类名。

## 明确当前需求

能量弹命中后，旧路径留下渐隐尾迹，命中点产生扩张扭曲和短促横向光斑；任一 shader 路径关闭时，其余效果仍可独立显示。

## 文件与 API 链路

### 命中特效实体速查表

| 实体 | 职责 |
|---|---|
| `TrailEntity` | 接收世界路径节点 |
| `DistortionEntity` | 直接绘制三阶段冲击环 |
| `FlareEntity` | 通过模型矩阵确定世界位置和朝向 |

三者都继承 BoxUtil 的全局计时与图层状态，并分别交给 `CombatRenderingManager`。

### 用两个节点建立可见尾迹

`GearInBoxTrailEffect.make()` 设置首尾宽度、颜色并追加路径点：

```java
trail.addNode(first);
trail.addNode(second);
trail.submitNodes();
```

返回对象至少有两个节点。

### 让尾迹从亮宽过渡到透明细线

`make()` 在设置颜色与宽度时把方向感写进首尾状态，并给尾迹 1 秒总寿命：

```java
trail.setStartWidth(28f);
trail.setEndWidth(2f);
trail.setEndColor(new Color(20, 80, 255, 0));
trail.setGlobalTimer(0.05f, 0.15f, 0.8f);
```

弹体后方逐渐变细并淡出。

### 配置三阶段扭曲尺寸

`GearInImpactSpecialFX.makeDistortion()` 先写命中位置，再让冲击从 20 su 扩张到 180 su，强度从 0 升到 0.8 后回到 0：

```java
distortion.setLocation(location);
distortion.setSizeIn(20f, 20f);
distortion.setSizeFull(120f, 120f);
distortion.setSizeOut(180f, 180f);
distortion.setPowerIn(0f);
distortion.setPowerFull(0.8f);
distortion.setPowerOut(0f);
distortion.setGlobalTimer(0.05f, 0.1f, 0.55f);
```

命中点出现扩张环而非固定圆斑。

### 比较默认畸变与手动实体

需要统一默认冲击参数的命中入口可以改用默认控制器；它返回 `Instance2Data`，适合直接复用共享默认时序，但仍需按入队返回码处理实例所有权：

```java
Instance2Data defaultDistortion = RenderingUtil.SpecialFX
        .addDefaultDistortion(false, 0.7f);
```

默认路径返回可入队的实例或明确返回 `null`；手动 `DistortionEntity` 仍保留自定义尺寸、强度和环硬度。

### 配置横向镜头光斑

`GearInImpactSpecialFX.makeFlare()` 设置命中位置、弹体朝向、长宽、颜色、混合和 0.4 秒总寿命：

```java
flare.setLocation(location);
flare.setFacingScale(facing, 1f, 1f);
flare.setSize(180f, 28f);
flare.setCoreColor(Color.WHITE);
flare.setFringeColor(new Color(70, 200, 255, 180));
flare.setAdditiveBlend();
flare.setGlobalTimer(0.05f, 0.05f, 0.3f);
```

命中瞬间出现白芯青边的短光斑。

### 分别处理三个入队结果

命中效果入口为每个实体独立检查返回码并释放失败项：

```java
for (RenderDataAPI effect : effects) {
    byte state = CombatRenderingManager.addEntity(effect);
    if (state != BoxEnum.STATE_SUCCESS) effect.delete();
}
```

关闭扭曲时，尾迹与光斑仍可出现。

## 完整最小例

两个完整类只构造效果。命中入口传入两个轨迹点、命中位置和弹体朝向，再逐项入队；构造器已经写好世界位置、图层与寿命。

::: details 完整成品：GearInBoxTrailEffect

```java
package data.scripts.gearin.lib;

import java.awt.Color;

import org.boxutil.units.standard.entity.TrailEntity;
import org.lwjgl.util.vector.Vector2f;

import com.fs.starfarer.api.combat.CombatEngineLayers;

public final class GearInBoxTrailEffect {
    public TrailEntity make(Vector2f first, Vector2f second) {
        TrailEntity trail = new TrailEntity();
        trail.setStartWidth(28f);
        trail.setEndWidth(2f);
        trail.setStartColor(new Color(80, 230, 255, 220));
        trail.setEndColor(new Color(20, 80, 255, 0));
        trail.addNode(first);
        trail.addNode(second);
        trail.setFlowWhenPaused(false);
        trail.setGlobalTimer(0.05f, 0.15f, 0.8f);
        trail.setLayer(CombatEngineLayers.ABOVE_PARTICLES_LOWER);
        trail.submitNodes();
        return trail;
    }
}
```

:::

::: details 完整成品：GearInImpactSpecialFX

```java
package data.scripts.gearin.lib;

import java.awt.Color;

import org.boxutil.units.standard.attribute.Instance2Data;
import org.boxutil.units.standard.entity.DistortionEntity;
import org.boxutil.units.standard.entity.FlareEntity;
import org.boxutil.util.RenderingUtil;
import org.lwjgl.util.vector.Vector2f;

import com.fs.starfarer.api.combat.CombatEngineLayers;

public final class GearInImpactSpecialFX {
    /** BoxUtil 缺陷：首参实为 isCampaign(战役管线)，命名易误导调用方。战斗入口应传 false。 */
    public Instance2Data makeDefaultDistortion(boolean isCampaign, float duration) {
        return RenderingUtil.SpecialFX.addDefaultDistortion(isCampaign, duration);
    }

    public DistortionEntity makeDistortion(Vector2f location) {
        DistortionEntity distortion = new DistortionEntity();
        distortion.setLocation(location);
        distortion.setSizeIn(20f, 20f);
        distortion.setSizeFull(120f, 120f);
        distortion.setSizeOut(180f, 180f);
        distortion.setPowerIn(0f);
        distortion.setPowerFull(0.8f);
        distortion.setPowerOut(0f);
        distortion.setInnerFull(0.1f, 0.1f);
        distortion.setRingHardness(0.75f);
        distortion.setGlobalTimer(0.05f, 0.1f, 0.55f);
        return distortion;
    }

    public FlareEntity makeFlare(Vector2f location, float facing) {
        FlareEntity flare = new FlareEntity();
        flare.setLocation(location);
        flare.setFacingScale(facing, 1f, 1f);
        flare.setSize(180f, 28f);
        flare.setCoreColor(Color.WHITE);
        flare.setFringeColor(new Color(70, 200, 255, 180));
        flare.setSmooth();
        flare.setAdditiveBlend();
        flare.setGlobalTimer(0.05f, 0.05f, 0.3f);
        flare.setLayer(CombatEngineLayers.ABOVE_PARTICLES_LOWER);
        return flare;
    }
}
```

:::

## 验证方案

前置数据：BoxUtil 三项渲染路径可用，弹体命中效果能提供上一采样点、命中点与飞行朝向。

1. 触发一次命中，确认尾迹连接两个输入点，并在 1 秒内渐细、淡出。
2. 核对扭曲中心位于命中点，尺寸按 20、120、180 su 三阶段扩张，总寿命为 0.7 秒。
3. 核对光斑位于同一命中点，长轴沿弹体朝向，总寿命为 0.4 秒。
4. 用默认畸变路径连续触发 100 次，记录返回 `null` 的次数、入队次数和持续时间。
5. 暂停战斗，确认三个实体的全局计时不推进；恢复后继续退出阶段。

预期结果：

- 尾迹至少有两个节点，并按首尾宽度、颜色和全局计时变化；
- 扭曲的位置、尺寸、强度和环硬度都来自命中参数与构造器状态；
- 光斑位置与朝向正确，使用白芯青边的加法混合；
- 默认畸变的复用次数、空值数和持续时间可与手动实体分开记录。

## 故障分析

- **关闭扭曲后无效对象持续积累**：调用方忽略入队返回码，也没有释放失败实体。

    - **解决方案**：逐项检查状态，失败时立即 `delete()`。

- **尾迹不可见**：节点少于两个，或没有执行 `submitNodes`。

    - **解决方案**：补齐首尾节点并提交。

- **扭曲方向与预期相反**：`power` 使用了负值，或内外尺寸参数顺序错误。

    - **解决方案**：修正符号和尺寸顺序。

- **默认畸变没有返回实例**：发生在 3D 模式、forceDelete 或实体未初始化等情形，与畸变开关无关。

    - **解决方案**：记录 `addDefaultDistortion()` 的空值结果，改用手动实体只作为对照，不把空值继续提交。

- **光斑遮住整个画面**：宽高按屏幕像素填写，但实体使用世界尺度。

    - **解决方案**：按世界单位缩小尺寸。

## 总结

三个效果共享触发点，各自拥有节点、尺寸和寿命。独立创建、独立入队、独立失败，组合效果才能在配置降级时保持可控。

## 建议参考项

- `org.boxutil.units.standard.entity.TrailEntity`：路径型尾迹；
- `org.boxutil.units.standard.entity.DistortionEntity`：环形空间扭曲；
- `org.boxutil.units.standard.entity.FlareEntity`：核心与边缘可调的光斑；
- `org.boxutil.util.RenderingUtil`：`SpecialFX.addDefaultDistortion` 取得共享默认畸变实例。
