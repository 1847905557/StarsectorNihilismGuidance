# 选择 BoxUtil 渲染方案

本节从战斗效果的需求出发，选择合适的 BoxUtil 渲染对象。您将判断画面需要大量重复精灵、连续形状、界面元素、模型还是局部光照，并用短片段认识创建、提交与结束的边界。

*本节预期阅读时间：\~16 分钟。*

*本节预期操作时间：\~6 分钟。*

## 学习目标

- 根据效果的重复数量、形状和坐标系选择 BoxUtil 对象；
- 解释实例、节点、模型与光源各自提交的数据；
- 判断何时需要更新数据、设置生命周期和处理入队结果。

## 前置知识

- [C21-S1] 的战斗图层、共享 OpenGL 状态与分层插件生命周期；
- [C21-S2] 的 shader program、片元形状和 Alpha 混合；
- [C21-S5] 的实例数据与批量绘制关系；
- 能够区分世界坐标与屏幕坐标。

## 从画面需求选择对象

先看效果中什么会变化。数百个外观相同的粒子主要改变位置、颜色和寿命，适合一份 `SpriteEntity` 配多份 `Instance2Data`。范围环和弯曲光束由节点决定形状，适合 `SegmentEntity` 与 `CurveEntity`。固定在窗口角落的数字要在屏幕坐标绘制；随舰船转动的 OBJ 模型则需要世界变换。光照还取决于受光对象的材质注册。

[C21-S1] 至 [C21-S5] 的方向标由 Java 逐项管理绘制状态和 GPU 数据。BoxUtil 提供实体、材质和战斗渲染管理器，适合需要组合多种效果的场景；选型时仍要明确数据由谁更新、何时提交、画面位于哪一层，以及实体何时结束。

## 为重复精灵提交实例

下列局部片段展示批量精灵的关键关系。BoxUtil 初始化尚未完成时，当前调用先结束；shader 能力不可用时，原版粒子提供可见反馈。实体创建发生在这些判断之后：

```java
if (!BoxUtilModPlugin.isGlobalInitialized()) {
    BoxUtilModPlugin.initLater();
    return;
}
boolean boxAvailable = BoxConfigs.isShaderEnable() && ShaderCore.isValid();
if (!boxAvailable) {
    engine.addSmoothParticle(player.getLocation(), player.getVelocity(),
            24f, 0.6f, BOX_IN + BOX_FULL + BOX_OUT, Color.CYAN);
    return;
}
```

`SpriteEntity` 保存材质、图层和实例列表；每份 `Instance2Data` 保存位置、速度、朝向、缩放、颜色和寿命。一个实体对应同一类外观，实例数据负责彼此不同的运动：

```java
SpriteEntity box = new SpriteEntity(sprite);
box.getMaterialData().setColor(Color.CYAN);
box.setLayer(CombatEngineLayers.ABOVE_PARTICLES_LOWER);
box.setAdditiveBlend();

Instance2Data data = new Instance2Data();
data.setLocation(MathUtils.getPoint(player.getLocation(), radius, angle));
data.setVelocity(player.getVelocity());
data.setFacing(angle);
data.setScale(0.6f, 0.6f);
data.setColor(new Color(100, 220, 255, 210));
data.setTimer(BOX_IN, BOX_FULL, BOX_OUT);
instances.add(data);
```

实例写入完成后，刷新范围、提交数据和绘制数量共同决定画面。这个片段展示首次提交；后续只改动连续区间时，可以设置刷新起点和长度再提交：

```java
box.setInstanceData(instances);
box.setInstanceDataRefreshAllFromCurrentIndex();
box.submitInstanceData();
box.setRenderingCount(instances.size());
box.setGlobalTimer(BOX_IN, BOX_FULL, BOX_OUT);
```

入队返回码决定实体是否进入战斗渲染管理器。失败时删除尚未入队的实体：

```java
byte result = CombatRenderingManager.addEntity(box);
if (result != BoxEnum.STATE_SUCCESS) {
    box.delete();
    return;
}
```

选用批量精灵的依据是大量对象共享材质和绘制方式。若每个效果都需要不同的几何形状，先按形状分组，再为每组建立实体。

## 用节点描述连续形状

范围环和曲线光束的边界由节点或插值控制。以下局部片段分别配置圆环与曲线；节点写完后提交，显示时再按入队结果处理实体生命周期：

```java
SegmentEntity ring = new SegmentEntity();
ring.initCircle(center, radius, new Color(30, 210, 255, 220), Color.WHITE, 5f);
ring.setInterpolation((short) 64);
ring.setTextureSpeed(0.15f);
ring.setFlowWhenPaused(false);
ring.submitNodes();
```

```java
CurveEntity beam = new CurveEntity();
beam.initSmoothLine(start, length, Color.CYAN, Color.WHITE, 9f, 0.35f);
beam.setTextureSpeed(0.4f);
beam.setFlowWhenPaused(false);
beam.submitNodes();
```

范围环的关键数据是中心、半径和线宽；曲线光束还需要长度与平滑参数。形状改变后重新提交节点，暂停期间是否流动由 `setFlowWhenPaused(false)` 明确控制。

## 判断命中特效的生命周期

一次命中可以同时产生尾迹、扩张扭曲和短促光斑，但这三种效果有不同的结束时机。`TrailEntity` 的节点描述运动路径，`DistortionEntity` 的尺寸与强度描述冲击，`FlareEntity` 的朝向和颜色描述高亮。分别设置计时与入队结果，才能让某一种效果失败时仍准确处理其余实体。选择前先问：需要沿路径留下痕迹、改变背景采样，还是给命中点增加一瞬亮度？

## 判断界面与世界坐标

战斗内状态面板固定在窗口角落时，文字、数字和边框使用 UI 坐标。`TextFieldEntity` 需要有效字体，`TransformUtil.createWindowOrthoMatrix(null)` 提供窗口正交矩阵；面板的像素边距应在窗口尺寸变化后重新计算。随舰船移动的标签则使用世界坐标，不能把窗口角落的坐标直接交给世界实体。

## 为模型选择世界变换与光照

OBJ 模型要随舰船转向时，先由 `ModelManager` 查询资源，再用 `ModelData` 和世界变换创建 `CommonEntity`。下列局部片段把无效 ID 与无效模型数据拦在创建之前：

```java
if (!ModelManager.containsModelID(modelId)) {
    throw new IllegalArgumentException("Unknown BoxUtil model: " + modelId);
}
ModelData model = ModelManager.getModelData(modelId);
if (model == null || !model.isValid()) {
    throw new IllegalArgumentException("BoxUtil model is not valid");
}
```

```java
CommonEntity entity = new CommonEntity(model, true);
entity.setModelMatrix(TransformUtil.createModelMatrixVanilla(
        location, facing, scale, matrix));
entity.setDefaultLight();
```

模型的位置、朝向和尺度由输入矩阵决定。材质通道或贴图缺失时，先核对模型资源登记，再判断是否需要光照；`setDefaultLight()` 为当前实体选择默认光照路径。

## 按受光对象选择光源

局部光源还要求受光对象具有相应的材质或光照注册。点状命中适合 `PointLight`，沿光束延伸的亮带适合 `LinearLight`，舰首探照适合 `SpotLight`。以下局部片段展示三种光源共享的位置、颜色、强度、衰减和寿命设置：

```java
private void configure(BaseIlluminantData light, Vector2f location,
        Color color, float strength, float radius) {
    light.setLocation(location);
    light.setColor(color);
    light.setStrength(strength);
    light.setAttenuationRadius(radius);
    light.setGlobalTimer(0.1f, 0.3f, 0.6f);
}
```

```java
PointLight point = new PointLight();
configure(point, location, new Color(90, 220, 255), 1.2f, 180f);

LinearLight linear = new LinearLight();
linear.setLength(240f);
linear.setCylinderRadius(18f);
configure(linear, location, new Color(100, 230, 255), 1f, 220f);

SpotLight spot = new SpotLight();
spot.setCone(18f);
spot.setFacingScale(facing, 1f, 1f);
configure(spot, location, new Color(180, 235, 255), 0.9f, 320f);
```

`AreaLight` 可覆盖一片区域，`InfiniteLight` 可提供环境光。创建前用 `EntityShadingDataManager` 核对受光对象的注册；若注册缺失，增加光源仍无法得到预期的受光画面。

## 处理大批量实例的更新范围

单个 `SpriteEntity` 可以承载大量 `Instance2Data`。首次提交时设置完整刷新范围；此后只有连续一段实例变化时，用 `setInstanceDataRefreshIndex(...)` 和 `setInstanceDataRefreshSize(...)` 限定上传范围。对象池只复用 Java 对象，不能代替 GPU 数据提交。结束时先让 `CombatRenderingManager` 移除实体，再重置实例状态并归还对象池，避免仍在绘制的数据被下一批效果复用。

## 渲染需求速查表

|画面需求|选择对象|关键判断|
|---|---|---|
|大量同材质粒子|`SpriteEntity` 与 `Instance2Data`|实例各有位置、颜色或寿命，材质共享|
|范围环或连续光束|`SegmentEntity` 或 `CurveEntity`|形状由节点和插值决定|
|尾迹、扭曲或光斑|`TrailEntity`、`DistortionEntity` 或 `FlareEntity`|根据路径、背景扭曲或瞬时高亮分别选择|
|固定在窗口的状态面板|`TextFieldEntity` 与 UI 绘制对象|位置由窗口尺寸和像素边距决定|
|随舰船变换的 OBJ 模型|`ModelData` 与 `CommonEntity`|模型资源、材质和世界矩阵均有效|
|局部或环境光|对应的光源数据|受光对象已有材质或光照注册|
|大量实例的部分更新|实例刷新区间|变化连续且范围小于完整实例集合|

先按画面需求选对象，再核对资源、图层、提交方式和结束条件。让同一实体承担互不相干的几何与生命周期，通常会使更新和清理难以判断。

## 验证方案

前置数据：BoxUtil `1.5.5` 的公开 API 说明与下列战斗效果需求。

1. 为沿舰船周围移动的多份同材质粒子选择对象，并指出何时提交实例数据。
2. 为随舰船转向的范围环和弯曲光束分别选择对象，并指出形状变化后的提交动作。
3. 为固定在窗口右上角的状态面板选择坐标系，并指出窗口尺寸变化时需更新的值。
4. 为带局部照明的 OBJ 模型确定资源、变换和受光条件，再说明模型或光源失效时从何处排查。

预期结果：

- 同材质粒子选择 `SpriteEntity` 和多份 `Instance2Data`，写入后设置刷新范围、提交实例数据并设置绘制数量；
- 范围环选择 `SegmentEntity`，弯曲光束选择 `CurveEntity`，节点变化后调用 `submitNodes()`；
- 右上角面板使用 UI 坐标和窗口正交矩阵，并按当前窗口尺寸重算像素边距；
- OBJ 模型先核对 `ModelManager` 中的资源和 `ModelData`，再设置世界矩阵与光照，并检查受光对象的注册。

## 故障分析

- **批量粒子只显示一份**：实例数据已创建，但刷新范围或绘制数量仍按单个实例设置。
    - **解决方案**：核对 `setInstanceData()`、刷新范围、`submitInstanceData()` 与 `setRenderingCount()`。

- **范围环形状保持旧值**：节点已经改变，实体仍使用上次提交的数据。
    - **解决方案**：更新节点后调用 `submitNodes()`，再核对暂停时的流动设置。

- **面板随战场移动**：绘制使用世界坐标或缺少窗口正交矩阵。
    - **解决方案**：将面板放入 UI 绘制路径，并按窗口尺寸重算位置。

- **模型或光照没有出现**：模型资源、材质通道或受光对象注册缺失。
    - **解决方案**：核对 `ModelManager` 的模型 ID 与数据，再检查受光对象的光照注册。

## 总结

BoxUtil 的选型从画面形状、重复数量和坐标系开始：重复精灵使用实例，连续形状使用节点，窗口面板使用 UI 坐标，模型和光照还需要资源与材质条件。确定对象后，再沿数据更新、提交、入队和清理核对生命周期。

方向标示例中的 OpenGL 状态与实例数据关系仍适用。选择高层实体时，您可以把精力放在效果本身，同时清楚每次画面变化由哪份数据驱动。

## 建议参考项

- `BoxUtil wiki`：查询[实体、材质和渲染管理接口](https://shiozakanaptr.github.io/boxutil-page/)。
