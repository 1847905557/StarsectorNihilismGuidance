# 选择 BoxUtil 渲染方案

手写 GLSL 和 VAO/SSBO 时，显存分配、状态还原与渲染提交每一步都得插件自行处理。

转到 BoxUtil `1.5.5` 以后，这些底层管线被收进了现成的实体类与 `CombatRenderingManager` 里。

这页按重复粒子、连续节点、文本面板、OBJ 模型与局部光源拆开对照选型，并标清几个容易误判的提交与销毁边界。

---

_本节预期阅读时间：\~16 分钟。_

_本节预期操作时间：\~10 分钟。_

## 前置知识

- [C18-S1] 的强依赖声明与 `compileOnly` 编译路径配置；
- [C20-S1] 的战斗图层、共享 OpenGL 状态与分层插件生命周期；
- [C20-S2] 的着色器 program、片元轮廓与 Alpha 混合；
- [C20-S5] 的实例数据与批量绘制关系；
- 能够区分世界坐标与屏幕窗口坐标。

## 按数据结构挑对实体类

BoxUtil 把常见战斗/UI 图形按 "每次更新变化的是什么" 拆开了：

| 画面需求               | 选择对象                                           | 关键判断                                     |
| ---------------------- | -------------------------------------------------- | -------------------------------------------- |
| 大量同材质贴图粒子     | `SpriteEntity` 与 `Instance2Data`                  | 材质共享，每个实例各自存位置、颜色与持续时间 |
| 范围环或连续弯折光束   | `SegmentEntity` 或 `CurveEntity`                   | 边界由节点和插值决定，改完调 `submitNodes()` |
| 尾迹、空间扭曲或光斑   | `TrailEntity`、`DistortionEntity` 或 `FlareEntity` | 按路径衰减、背景折射或瞬时耀斑分开计时入队   |
| 固定在窗口角的文本面板 | `TextFieldEntity` 与 UI 绘制路径                   | 采用 `TransformUtil.createWindowOrthoMatrix` |
| 随舰船转动的 OBJ 模型  | `ModelData` 与 `CommonEntity`                      | 模型有效后设世界变换矩阵与材质/光照          |
| 局部光源或方向光       | `BaseIlluminantData` 子类光源                      | 需配合支持光照着色的受光对象/光影包          |

## 提交重复 Sprite 实例与安全回退

提交前先把初始化和着色器就绪状态查清楚：若全局尚未初始化就调 `BoxUtilModPlugin.initLater()` 并早退等待下次回调；若 `BoxConfigs.isShaderEnable()` 或 `ShaderCore.isValid()` 为 `false`，回退成原版 `addSmoothParticle` 保留反馈：

```java
List<InstanceDataAPI> instances = new ArrayList<>();
float BOX_IN = 0.1f;
float BOX_FULL = 0.3f;
float BOX_OUT = 0.6f;
float radius = 80f;
float angle = 30f;

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

就绪之后，用 `SpriteEntity` 承载共享的 `SpriteAPI`、颜色、图层和加法混合 —— 切记 `setBaseSizePerTiles(width * 0.5f, height * 0.5f)` 传的是**半宽与半高**(`SpriteEntity` 构造器默认半尺寸只有 `1`)；而 `Instance2Data` 的位置、速度、朝向、缩放与淡入/停留/淡出秒数只管各自的变化：

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

box.setInstanceData(instances);
box.setInstanceDataRefreshAllFromCurrentIndex();
box.submitInstanceData();
box.setRenderingCount(instances.size());
box.setGlobalTimer(BOX_IN, BOX_FULL, BOX_OUT);

byte result = CombatRenderingManager.addEntity(box);
if (result != BoxEnum.STATE_SUCCESS) {
    box.delete();
    return;
}
```

这里有三个容易遗漏的点：其一，`Instance2Data` 提交给实体后按自身速度漂移，不会自动绑死在舰船上；其二，`submitInstanceData()` 与 `setRenderingCount(...)` 缺一不可 (不设绘制数或漏传索引范围就画不出来)；其三，入队返回码不是 `BoxEnum.STATE_SUCCESS` 时要立刻 `box.delete()` 释放本地尚未交出的显存对象。

### 大批量更新与实体不可复用的边界

当数百个实例里只有连续一小段发生变动时，可以用 `setInstanceDataRefreshIndex(srcIdx)`、`setInstanceDataRefreshOffset(gpuOffset)` 与 `setInstanceDataRefreshSize(count)` 做局部覆写再 `submitInstanceData()`，避免每次都整块上传。

::: warning 调用过 removeEntity / delete 的实体不可再重新入队

`CombatRenderingManager.removeEntity(entity)` 内部直接调用 `entity.delete()` 并把 `_hasDelete` 置 `true`、释放缓冲，渲染队列随后在清理流程中把引用剔除。而 `BaseRenderData.reset()` 并**不会**重置 `_hasDelete` 标记 —— 因此一旦对某个实体调用过 `removeEntity(...)` 或 `delete()`，该实体就已永久失效，需要再次展示效果时必须新建实体；如果想复用实例容量，应在实体仍存活期间直接局部更新 `Instance2Data` 列表并重新 `submitInstanceData()`。

:::

## 用节点、坐标与模型组织复合效果

- **连续形状 (`SegmentEntity` / `CurveEntity`)**：调用 `initCircle(...)` 或 `initSmoothLine(...)` 时内部会自动填入节点并执行 `submitNodes()`；但之后如果代码又改了节点坐标，必须手动再调一次 `submitNodes()` 才会把新边界同步进 GPU。暂停时不希望纹理继续流动，则调 `setFlowWhenPaused(false)`：

```java
SegmentEntity ring = new SegmentEntity();
ring.initCircle(center, radius, new Color(30, 210, 255, 220), Color.WHITE, 5f);
ring.setInterpolation((short) 64);
ring.setTextureSpeed(0.15f);
ring.setFlowWhenPaused(false);
ring.submitNodes();

CurveEntity beam = new CurveEntity();
beam.initSmoothLine(start, length, Color.CYAN, Color.WHITE, 9f, 0.35f);
beam.setTextureSpeed(0.4f);
beam.setFlowWhenPaused(false);
beam.submitNodes();
```

- **复合命中效果**：`TrailEntity`(尾迹)、`DistortionEntity`(折射扭曲) 与 `FlareEntity`(耀斑高亮) 互相独立，各自设好 `setGlobalTimer(...)` 持续时间并分别判断 `addEntity(...)` 返回码，让短耀斑提前退场、长尾迹慢慢消散。
- **窗口 UI vs 世界坐标**：固定在屏幕角落的 `TextFieldEntity` 必须走 UI 绘制路径并配 `TransformUtil.createWindowOrthoMatrix(null)` 按当前窗口分辨率算边距；跟船走的标识才使用世界坐标矩阵。
- **OBJ 模型 (`CommonEntity`)**：使用前用 `ModelManager.containsModelID(modelId)` 与 `model.isValid()` 双检资源，并用 `TransformUtil.createModelMatrixVanilla(location, facing, scale, matrix)` 提供世界矩阵：

```java
if (!ModelManager.containsModelID(modelId)) {
    throw new IllegalArgumentException("Unknown BoxUtil model: " + modelId);
}
ModelData model = ModelManager.getModelData(modelId);
if (model == null || !model.isValid()) {
    throw new IllegalArgumentException("BoxUtil model is not valid");
}
CommonEntity entity = new CommonEntity(model, true);
entity.setModelMatrix(TransformUtil.createModelMatrixVanilla(
        location, facing, scale, matrix));
entity.setDefaultLight();
```

## 为受光对象配置光源与着色包边界

局部点/线/聚光灯与区域光、方向光都继承自 `org.boxutil.base.BaseIlluminantData`，可以抽出统一的通用配置工具方法再叠加各自的形状参数：

```java
private void configure(BaseIlluminantData light, Vector2f location,
        Color color, float strength, float radius) {
    light.setLocation(location);
    light.setColor(color);
    light.setStrength(strength);
    light.setAttenuationRadius(radius);
    light.setGlobalTimer(0.1f, 0.3f, 0.6f);
}

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

需要全图方向光时可选 `InfiniteLight`(默认矩阵沿局部 `-Z` 方向、默认半径 `2048.0f`，`setNoneAttenuation()` 置 `0.0f`)。需要说明的是，BoxUtil `1.5.5` 自带着色器只提供 Bloom 与抗锯齿，光源的实际明暗、法线计算与深度衰减取决于外部光影包实现以及通过 `EntityShadingDataManager` 登记好的受光材质对象 —— 缺了受光材质或支持光照的光影包，光有光源实体也不会直接产生受光反射效果。

### 场景方案核对

1. **大批量同材质移动粒子**：选用 `SpriteEntity` + `Instance2Data` 列表；写入实例与全局计时后设置 `setInstanceDataRefreshAllFromCurrentIndex()`、`submitInstanceData()` 和 `setRenderingCount(...)`，再把实体交给 `CombatRenderingManager.addEntity(...)`。
2. **动态变化的范围环与弯曲光束**：分别选用 `SegmentEntity` 与 `CurveEntity`；每次在代码里更新节点后都记得调用 `submitNodes()` 刷新 GPU 端节点缓冲。
3. **固定在屏幕角的 HUD 面板与带照明 OBJ 模型**：面板用 `TextFieldEntity` 加 `TransformUtil.createWindowOrthoMatrix(null)` 并在窗口大小变化时重算像素边距；OBJ 模型依次核对 `ModelManager.containsModelID`、`ModelData.isValid()`、`TransformUtil.createModelMatrixVanilla(...)` 与 `EntityShadingDataManager` 受光材质登记。

## 常见问题

- **提交了上百个粒子实例却只画出一个甚至看不见**：没有设好实例刷新范围、`submitInstanceData()` 漏调，或忘了按实例列表长度设置 `setRenderingCount(...)`(以及 `setBaseSizePerTiles` 仍停在默认 `1`)。
    - **解决方案**：按 `setInstanceData` -> `setInstanceDataRefreshAllFromCurrentIndex` -> `submitInstanceData` -> `setRenderingCount` -> `setGlobalTimer` 的顺序提交并检查半宽半高设置。

- **范围环或光束在节点坐标更新后形状还停在原地**：只修改了节点列表没有把新节点上传 GPU。
    - **解决方案**：更新完节点数据后显式调用 `submitNodes()`。

- **HUD 状态面板随着战场镜头跑出屏幕**：误把 UI 面板塞进了世界坐标渲染路径，或者没有建立窗口正交投影矩阵。
    - **解决方案**：将 `TextFieldEntity` 移到 UI 绘制管线并绑定 `TransformUtil.createWindowOrthoMatrix(null)`，随窗口尺寸重算位置。

- **调用过 `removeEntity` 后再调 `reset` 与 `addEntity` 还是什么都画不出来**：`removeEntity(...)` 已触发 `delete()` 将 `_hasDelete` 设为 `true` 并释放显存，而 `reset()` 不会复位 `_hasDelete`。
    - **解决方案**：新建实体对象重新入队；若需要反复复用，则只保留一个存活实体并局部刷新它的 `Instance2Data`。

## 建议参考项

- `org.boxutil.BoxUtilModPlugin`：全局初始化状态查询与 `initLater()`；
- `org.boxutil.manager.CombatRenderingManager`：战斗渲染实体的入队与移除入口；
- `org.boxutil.units.standard.entity.SpriteEntity`：同材质多实例精灵实体；
- `org.boxutil.units.standard.attribute.Instance2Data`：二维实例位置、朝向、缩放、颜色与计时数据；
- `org.boxutil.units.standard.entity.CommonEntity`：配合 `ModelManager` 与 `TransformUtil` 承载 OBJ 模型；
- `org.boxutil.base.BaseIlluminantData`：BoxUtil 光源数据共同基类；
- `BoxUtil`：访问[实体、材质与渲染管理官方文档](https://shiozakanaptr.github.io/boxutil-page/)。
