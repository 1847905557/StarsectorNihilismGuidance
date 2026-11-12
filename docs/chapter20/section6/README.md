# 选择 BoxUtil 渲染方案

BoxUtil 用实体组织材质、实例数据与渲染管理。本节围绕重复粒子、连续形状、窗口面板、模型和光源选择对象，再说明数据提交、入队与结束的关系。前面的 OpenGL 经验用于判断这些接口各自接管了哪项工作，选型则依据实际画面与变化的数据。

_本节预期阅读时间：\~16 分钟。_

_本节预期操作时间：\~6 分钟。_

## 学习目标

- 根据效果的重复数量、形状和坐标系选择 BoxUtil 对象；
- 解释实例、节点、模型与光源各自提交的数据；
- 判断何时需要更新数据、设置生命周期和处理入队结果。

## 前置知识

- [C20-S1] 的战斗图层、共享 OpenGL 状态与分层插件生命周期；
- [C20-S2] 的 shader program、片元形状和 Alpha 混合；
- [C20-S5] 的实例数据与批量绘制关系；
- 能够区分世界坐标与屏幕坐标。

## 从画面需求选择对象

选型从每次更新的数据开始。外观相同的粒子主要改变位置、颜色与寿命，用一份 `SpriteEntity` 配多份 `Instance2Data`；范围环和曲线光束通过节点改变形状，分别使用 `SegmentEntity` 与 `CurveEntity`。窗口数字采用屏幕坐标，随舰船转动的 OBJ 模型采用世界变换，光源再与受光对象的材质一起工作。

[C20-S1] 至 [C20-S5] 已经展开顶点、参数与批量绘制。编者选择 BoxUtil 的依据是这些工作是否能由同一套实体和管理入口组织起来：组合多种几何、模型或光照时，资源与提交有共同约定，效果侧便能集中处理自己的变化。

实际接入沿用 [C18-S1] 的依赖方式：manifest 的 `dependencies` 声明 `BoxUtil`，Gradle 编译路径包含其 `jars` 与 `jars/backends`。本页的片段用于相应功能已有的回调，以下按对象分别说明用法。

## 为重复 Sprite 贴图提交实例

下面的片段用于已有战斗效果方法：`engine` 是当前 `CombatEngineAPI`，`player` 是该方法取得的当前玩家舰，`sprite` 是已加载的 `SpriteAPI`。实例列表与本次效果的参数在方法内准备；`BOX_IN`、`BOX_FULL`、`BOX_OUT` 分别给出淡入、完整显示和淡出的秒数：

```java
List<InstanceDataAPI> instances = new ArrayList<>();
float BOX_IN = 0.1f;
float BOX_FULL = 0.3f;
float BOX_OUT = 0.6f;
float radius = 80f;
float angle = 30f;
```

`List`、`ArrayList` 来自 `java.util`，`Color` 来自 `java.awt`；`MathUtils` 来自 `org.lazywizard.lazylib`。BoxUtil 的入口是 `org.boxutil.BoxUtilModPlugin`，设置类为 `org.boxutil.config.BoxConfigs`，枚举类为 `org.boxutil.define.BoxEnum`；`ShaderCore`、`CombatRenderingManager` 来自 `org.boxutil.manager`，`Instance2Data` 来自 `org.boxutil.units.standard.attribute`，实体来自 `org.boxutil.units.standard.entity`。列表按 `setInstanceData(...)` 的参数类型声明为 `List<org.boxutil.base.api.InstanceDataAPI>`，里面保存本次创建的 `Instance2Data`。

创建实体之前检查初始化与 shader 能力。初始化尚未完成时调用 `initLater()` 并结束本次调用，下一次功能调用再按状态继续；shader 不可用时，在玩家舰位置生成原版粒子，保留这次事件的短时反馈：

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

能力可用时创建 Sprite 实体，设置材质、图层和加法混合，再准备实例。位置距玩家舰 80 su、角度为 30 度，速度取当时的舰船速度，缩放与颜色保存在实例中，`setTimer(...)` 安排淡入、显示与淡出：

```java
SpriteEntity box = new SpriteEntity(sprite);
box.setBaseSizePerTiles(sprite.getWidth() * 0.5f, sprite.getHeight() * 0.5f);
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

实例保存的是这次提交的位置与速度分量，后续运动按自身数据推进。舰船以后转向或改变速度，不会自动把这份实例重新附着到舰船；需要跟随时，由效果侧继续更新对应位置。

`setBaseSizePerTiles(...)` 接收每张贴图的基础半宽、半高，实例的 `0.6f` 缩放再乘到这个尺寸上。因此本例图案的完整宽高为原 Sprite 尺寸的 60%；创建实体时的基础半尺寸默认是 1，仅传入贴图还没有设置它的世界尺寸。

接着把列表交给实体，设置本次刷新范围并提交。`setRenderingCount(...)` 决定实际绘制多少份，`setGlobalTimer(...)` 决定整个实体的计时。下面按首次提交处理全部实例；后续更新可以缩小刷新区间：

```java
box.setInstanceData(instances);
box.setInstanceDataRefreshAllFromCurrentIndex();
box.submitInstanceData();
box.setRenderingCount(instances.size());
box.setGlobalTimer(BOX_IN, BOX_FULL, BOX_OUT);
```

数据准备完毕后调用 `CombatRenderingManager.addEntity(...)`。成功表示登记到后续处理的队列，绘制由管理器接着完成；失败时删除这份尚未交出的实体：

```java
byte result = CombatRenderingManager.addEntity(box);
if (result != BoxEnum.STATE_SUCCESS) {
    box.delete();
    return;
}
```

重复 Sprite 贴图的收益来自共享材质和绘制方式。效果中有多种几何时，先按形状分组，每组建立自己的实体，再让组内实例保存各自的运动数据。

## 用节点描述连续形状

范围环和曲线光束的边界由节点或插值控制。下面两个局部片段使用世界坐标：`center`、`start` 都是 `Vector2f`，`radius` 是圆环半径，`length` 是光束长度，单位为 `su`。节点写完后提交，再按前述入队返回码处理生命周期：

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

`initCircle(...)` 与 `initSmoothLine(...)` 建立初始节点并提交，后续修改节点形状再调用 `submitNodes()`。圆环根据中心、半径与线宽拟合，曲线光束从起点沿正 X 方向建立，再通过节点或变换调整需要的形状。`setFlowWhenPaused(false)` 让纹理流动随暂停停止，几何更新继续由自己的调用安排。

## 判断命中特效的生命周期

命中效果可以沿路径留下尾迹，也可以扭曲背景，或给命中点增加短促亮度。对应地，`TrailEntity` 用节点描述路径，`DistortionEntity` 用尺寸和强度描述扭曲，`FlareEntity` 用朝向和颜色描述高亮。组合效果时分别设置计时，并分别处理入队返回码：光斑已经结束，尾迹仍可能继续消散，每种实体都按自己的寿命处理。

## 判断界面与世界坐标

状态面板固定在窗口角落，文字、数字与边框就按 UI 坐标放置。为 `TextFieldEntity` 准备有效字体，用 `TransformUtil.createWindowOrthoMatrix(null)` 建立窗口正交矩阵，再根据当前窗口尺寸计算像素边距。窗口改变后重新计算这些位置；需要跟随舰船的标签则放在世界坐标中，让视口变换决定它在屏幕上的位置。

## 为模型选择世界变换与光照

OBJ 模型从 `org.boxutil.manager.ModelManager` 取得 `org.boxutil.units.standard.attribute.ModelData`，交给 `CommonEntity` 使用。下面的局部片段接收本次选择的 `String modelId`，在资源取得处确认模型登记与数据：

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

`location` 与 `scale` 是世界位置和缩放向量，`facing` 是以度为单位的朝向，`matrix` 为可复用的 `Matrix4f`，`TransformUtil` 来自 `org.boxutil.util`。模型按这个矩阵放置；`CommonEntity(model, true)` 同步模型纹理，`setDefaultLight()` 设置默认白色光照值。材质通道与局部光照再按实际显示要求配置。

## 按受光对象选择光源

局部光源还要求受光对象具有相应的材质或光照注册。点状命中适合 `PointLight`，沿光束延伸的亮带适合 `LinearLight`，舰首探照适合 `SpotLight`。三种光源来自 `org.boxutil.units.standard.light`，共享基类为 `org.boxutil.base.BaseIlluminantData`；`location` 是世界位置，`facing` 是以度为单位的朝向。下面的局部方法统一设置颜色、强度、世界单位衰减半径和秒数计时：

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

需要照亮区域时选择 `AreaLight`，需要无限远方向光时选择 `InfiniteLight`。后者默认朝向 `vec3(0, 0, -1)`，衰减半径只限制照射深度，取消衰减后可照亮全场；明暗仍取决于光照方向与受光面法线。创建前用 `EntityShadingDataManager` 确认受光对象的注册，光源才能和目标材质共同产生预期画面。

## 处理大批量实例的更新范围

变化集中在连续区间时，可以只刷新那一段实例。`setInstanceDataRefreshIndex(...)` 选择 Java 列表的起点，`setInstanceDataRefreshOffset(...)` 选择 GPU 数据的目标起点，`setInstanceDataRefreshSize(...)` 指定长度。源与目标索引各自对应，随后再提交；首次提交则刷新整个集合。

::: warning 移除实体与清除队列引用各有时机
`CombatRenderingManager.removeEntity(...)` 调用实体的 `delete()`，管理器中的引用随后清理。复用对象前确认它已离开管理器，再重置并重新交出，才能保留正确的结束与入队关系。
:::

对象池复用 Java 对象以后，变化的数据仍要上传。是否采用对象池由实际创建频率与成本决定，实例更新范围则按本次变化独立选择。

## 渲染需求速查表

| 画面需求              | 选择对象                                           | 关键判断                             |
| --------------------- | -------------------------------------------------- | ------------------------------------ |
| 大量同材质粒子        | `SpriteEntity` 与 `Instance2Data`                  | 实例各有位置、颜色或寿命，材质共享   |
| 范围环或连续光束      | `SegmentEntity` 或 `CurveEntity`                   | 形状由节点和插值决定                 |
| 尾迹、扭曲或光斑      | `TrailEntity`、`DistortionEntity` 或 `FlareEntity` | 根据路径、背景扭曲或瞬时高亮分别选择 |
| 固定在窗口的状态面板  | `TextFieldEntity` 与 UI 绘制对象                   | 位置由窗口尺寸和像素边距决定         |
| 随舰船变换的 OBJ 模型 | `ModelData` 与 `CommonEntity`                      | 模型资源、材质和世界矩阵均有效       |
| 局部或方向光          | 对应的光源数据                                     | 受光对象已有材质或光照注册           |
| 大量实例的部分更新    | 实例刷新区间                                       | 变化连续且范围小于完整实例集合       |

从表中选定对象后，继续确定资源、图层、提交入口和结束条件。每种形状按自己的数据与寿命组织，更新和清理时才有明确的对象。

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

BoxUtil 的选择依据是画面需要什么数据：重复 Sprite 贴图由实例提供差异，连续形状由节点决定边界，窗口面板按 UI 坐标放置，模型和光照共同依赖资源与材质。确定实体后，把更新、提交、入队和结束接成连续的调用关系。

前面手动绘制方向标的经验在这里仍有用：数据变化怎样进入 GPU，位置怎样进入坐标变换，生命周期怎样结束，都能用于判断效果。交给高层实体管理以后，我们集中设计画面，同时保留对这些关系的理解。

## 建议参考项

- `BoxUtil wiki`：查询[实体、材质和渲染管理接口](https://shiozakanaptr.github.io/boxutil-page/)。
