# 用 BoxUtil 绘制线段、圆环与曲线光束

战术范围提示和能量束都由节点组成，但两者需要不同的插值与材质策略。本节先用 SegmentEntity 画稳定圆环，再用 CurveEntity 画带弯曲的光束，并明确节点提交时机。

*本节预期阅读时间：~18 分钟。*

*本节预期操作时间：~30 分钟。*

## 学习目标

完成后，您将能够：

* 根据折线与平滑曲线的差异选择 `SegmentEntity` 或 `CurveEntity`；
* 用实体初始化方法生成圆环和曲线光束的节点；
* 配置纹理流速与暂停策略；
* 在节点变化后标记刷新范围并重新提交；
* 把构造完成的实体交给战斗渲染管理器，并处理入队失败。

## 前置知识

* [C6-S2] 理解 Java 类、方法、字段与入口插件；
* [C7-S1] 理解每帧回调、暂停判断与运行时对象；
* 能够用 Gradle 构建示例 MOD，并从 `starsector.log` 定位类名。

## 明确当前需求

玩家舰周围显示一圈缓慢流动的 600 su 范围环；战斗接线在武器开火时从舰首创建一条平滑曲线光束。两个构造器只负责节点和材质状态，调用方负责入队、更新与删除。

## 文件与 API 链路

`SegmentEntity` 适合线段、折线、圆与椭圆；`CurveEntity` 提供平滑曲线插值；两者都在客户端节点列表和 GPU 节点缓冲之间保留明确的提交边界。`CombatRenderingManager` 负责把构造完成的实体加入当前战斗。

### 初始化战术范围圆环

**插入位置：`GearInSegmentPainter.makeRangeRing()`。** 一次生成圆环节点与颜色。

```java
SegmentEntity ring = new SegmentEntity();
ring.initCircle(center, radius, Color.CYAN, Color.WHITE, 5f);
```

完成信号：返回的 SegmentEntity 拥有有效节点。

### 让纹理沿圆环缓慢流动

**插入位置：`makeRangeRing()` 的纹理设置处。** 设置纹理速度并选择暂停策略。

```java
ring.setTextureSpeed(0.15f);
ring.setFlowWhenPaused(false);
```

完成信号：非暂停时纹理偏移持续变化。

### 提交圆环节点

**插入位置：`makeRangeRing()` 返回前。** 把节点数组上传到渲染端。

```java
byte state = ring.submitNodes();
```

完成信号：submitNodes 返回成功状态且圆环出现。

### 初始化平滑曲线光束

**插入位置：`GearInCurveBeam.make()`。** 用起点、长度、颜色、宽度和曲率建立曲线。

```java
CurveEntity beam = new CurveEntity();
beam.initSmoothLine(start, length, Color.CYAN, Color.WHITE, 9f, 0.35f);
```

完成信号：光束从舰首延伸且边缘平滑。

### 把构造结果加入当前战斗

**插入位置：战斗接线取得圆环或光束后。** 根据返回码决定实体是否已经交给 BoxUtil 管理。

```java
byte state = CombatRenderingManager.addEntity(beam);
if (state != BoxEnum.STATE_SUCCESS) {
    beam.delete();
}
```

完成信号：成功时实体进入当前战斗；失败时资源立即删除，调用侧不再更新该对象。

## 排查关键故障

下面是**局部故障片段**：

```java
beam.addNode(node);
return beam;
```

实际症状：调试器能看到节点列表增长，画面中的光束保持旧形状。

原因：修改客户端节点后没有标记刷新并 submitNodes。

下面是**局部修正片段**：

```java
beam.setNodeRefreshAllFromCurrentIndex();
beam.submitNodes();
```

复验：移动末端控制点，确认下一帧曲线形状同步变化。

## 完整最小例

两个完整类是形状构造器：返回前已经提交初始节点，并禁止暂停时纹理继续流动。动态控制器若修改 `getNodes()` 中的数据，仍需执行故障修正片段中的刷新与提交。

## 验证方案

前置数据：BoxUtil 渲染核心可用，战斗接线能在初始化时创建圆环，并在测试输入时创建曲线光束。

1. 以玩家舰坐标和 600 su 半径创建圆环，核对 `submitNodes()` 状态与入队返回码。
2. 观察纹理以 0.15 的速度沿圆环流动；暂停后偏移保持不变。
3. 以舰首位置和测试长度创建曲线，核对 9 su 宽度、0.35 曲率与 0.4 纹理速度。
4. 修改曲线末端节点但省略重新提交，确认画面保持旧形状。
5. 恢复刷新范围与 `submitNodes()`，确认下一帧显示新形状。
6. 让入队返回失败状态，确认对应实体执行 `delete()`。

预期结果：

* 圆环与曲线的初始节点在返回构造器前完成提交；
* 非暂停时纹理偏移按各自速度变化，暂停时保持不变；
* 实体只有在 `addEntity()` 成功后才由渲染管理器持有；
* 客户端节点变化不会自动出现在画面，刷新并提交后才同步；
* 入队失败的实体被删除，战斗没有未处理异常。

## 故障分析

- **节点列表变化但画面保持旧形状**：修改客户端节点后没有标记刷新并调用 `submitNodes()`。移动末端控制点并完成两步提交，确认渲染形状同步变化。
- **圆环缺少一段**：节点数量或闭合参数不完整。重新初始化闭合圆环，检查首尾节点连接后再观察完整轮廓。
- **曲线出现尖角**：插值数量过低，或控制点顺序反转。增加插值并按路径顺序提交控制点，重新观察曲线平滑度。
- **暂停时纹理仍在流动**：`flowWhenPaused` 与目标语义相反。设置为 false，暂停战斗后确认纹理速度停止。

## 总结

SegmentEntity 与 CurveEntity 都依赖节点提交，但服务的形状不同。先选适合成果的实体，再把节点修改范围和提交时机控制清楚，曲线才不会用每帧全量上传换取表面稳定。

## 建议参考项

* `SegmentEntity.initCircle`：快速建立圆环节点；
* `CurveEntity.initSmoothLine`：建立平滑曲线；
* `submitNodes`：把节点修改提交到渲染端。
::: details 完整成品：GearInCurveBeam

```java
package data.scripts.gearin.lib;

import java.awt.Color;

import org.boxutil.units.standard.entity.CurveEntity;
import org.lwjgl.util.vector.Vector2f;

public final class GearInCurveBeam {
    public CurveEntity make(Vector2f start, float length) {
        CurveEntity beam = new CurveEntity();
        beam.initSmoothLine(start, length, Color.CYAN, Color.WHITE, 9f, 0.35f);
        beam.setTextureSpeed(0.4f);
        beam.setFlowWhenPaused(false);
        beam.submitNodes();
        return beam;
    }
}
```

:::

::: details 完整成品：GearInSegmentPainter

```java
package data.scripts.gearin.lib;

import java.awt.Color;

import org.boxutil.units.standard.entity.SegmentEntity;
import org.lwjgl.util.vector.Vector2f;

public final class GearInSegmentPainter {
    public SegmentEntity makeRangeRing(Vector2f center, float radius) {
        SegmentEntity ring = new SegmentEntity();
        ring.initCircle(center, radius, new Color(30, 210, 255, 220), Color.WHITE, 5f);
        ring.setTextureSpeed(0.15f);
        ring.setFlowWhenPaused(false);
        ring.submitNodes();
        return ring;
    }
}
```

:::
