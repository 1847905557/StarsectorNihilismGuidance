# 用 BoxUtil 加载 OBJ 模型并配置材质

OBJ 文件本身只描述几何；模型 ID、贴图材质与世界变换仍要由 BoxUtil 数据表和 Java 代码组合。本节先确认 ModelManager 已注册模型，再为实体建立平移、旋转和缩放矩阵。

*本节预期阅读时间：~18 分钟。*

*本节预期操作时间：~30 分钟。*

## 学习目标

完成后，您将能够：

- 登记 BoxUtil 的 OBJ 与材质贴图；
- 用 `ModelManager` 验证模型 ID 并取得共享 `ModelData`；
- 用 `TransformUtil` 建立与游戏世界坐标一致的模型矩阵；
- 用 `CommonEntity` 组合模型、变换与默认光照；
- 从 ID、模型有效性、贴图通道和尺度定位空白模型。

## 前置知识

- [C7-S1] 理解 Java 类、方法、字段与入口插件；
- [C8-S3] 理解每帧回调与暂停判断；[C8-S2] 理解战斗对象读取与对象边界；
- 能够用 Gradle 构建示例 MOD，并从 `starsector.log` 定位类名。

## 明确当前需求

战斗开始后，玩家舰旁显示一个小型齿轮模型；舰船转向时模型保持自身角度，贴图缺失时日志明确指出模型 ID 或材质项。

## 文件与 API 链路

### 模型加载 API 速查表

| 对象 | 职责 |
|---|---|
| `ModelManager` | 从 `data/config/modFiles/BUtil_obj_data.csv` 取得模型数据 |
| `ModelData` | 保存几何缓冲和各贴图通道 |
| `CommonEntity` | 使用模型与默认光照 |
| `TransformUtil` | 构建与游戏坐标一致的模型矩阵 |

自定义生成网格时才需要直接调用 `MeshUtil` 计算法线或切线。

### 在 CSV 中登记模型 ID

在 `data/config/modFiles/BUtil_obj_data.csv` 的 GearIn 行中，按表头关联 OBJ、漫反射、法线、复杂、发光和切线贴图，空通道保留空字段。启动期只加载单文件 (无跨 MOD 行合并、资源优先级首个命中)，默认命名下 BoxUtil 自带 CSV 恒胜出，因此 GearIn 行需并入该文件，另建同名文件不会生效：

```csv
GearIn_gear,data/models/GearIn_gear.obj,data/models/GearIn_gear_diffuse.png,data/models/GearIn_gear_normal.png,,data/models/GearIn_gear_emissive.png,,,F32
```

GearIn 行并入生效后，ModelManager.containsModelID 返回 true。

### 在创建实体前确认模型存在

`GearInModelCatalog.require()` 把未知 ID 改成明确参数错误：

```java
if (!ModelManager.containsModelID(modelId)) {
    throw new IllegalArgumentException("Unknown BoxUtil model: " + modelId);
}
```

错误日志包含未知 modelId。

### 取得已解析的 ModelData

`require()` 在返回处从 `ModelManager` 取得已加载的模型数据，并让实体构造器再次检查 `model.isValid()`：

```java
ModelData model = ModelManager.getModelData(modelId);
```

返回对象包含模型缓冲与材质引用。

### 组合模型与世界变换

`GearInModelTransform.create()` 先用有效 `ModelData` 创建 `CommonEntity`，再把坐标、舰首角和缩放写入同一 `Matrix4f`：

```java
CommonEntity entity = new CommonEntity(model, true);
entity.setModelMatrix(TransformUtil.createModelMatrixVanilla(
        location, facing, scale, matrix));
```

实体持有同一份共享模型缓冲，模型矩阵包含本次位置、角度和缩放。

### 建立可观察的基础光照

`create()` 在返回前先使用 `CommonEntity` 的默认光照，证明法线与材质通道可读；动态光源留到 [C19-S6]：

```java
entity.setDefaultLight();
return entity;
```

GearIn 齿轮以漫反射、法线和发光通道渲染，朝向与传入 `facing` 一致。

## 完整最小例

目录类只负责模型注册表查询，变换类只接受有效 `ModelData` 并返回配置好矩阵与默认光照的 `CommonEntity`。战斗接线仍需检查 `CombatRenderingManager.addEntity()` 返回码。

::: details 完整成品：GearInModelCatalog

```java
package data.scripts.gearin.lib;

import org.boxutil.manager.ModelManager;
import org.boxutil.units.standard.attribute.ModelData;

public final class GearInModelCatalog {
    public ModelData require(String modelId) {
        if (!ModelManager.containsModelID(modelId)) {
            throw new IllegalArgumentException("Unknown BoxUtil model: " + modelId);
        }
        return ModelManager.getModelData(modelId);
    }
}
```

:::

::: details 完整成品：GearInModelTransform

```java
package data.scripts.gearin.lib;

import org.boxutil.units.standard.attribute.ModelData;
import org.boxutil.units.standard.entity.CommonEntity;
import org.boxutil.util.TransformUtil;
import org.lwjgl.util.vector.Matrix4f;
import org.lwjgl.util.vector.Vector2f;

public final class GearInModelTransform {
    private final Matrix4f matrix = new Matrix4f();

    public CommonEntity create(ModelData model, Vector2f location,
            float facing, Vector2f scale) {
        if (model == null || !model.isValid()) {
            throw new IllegalArgumentException("BoxUtil model is not valid");
        }
        CommonEntity entity = new CommonEntity(model, true);
        entity.setModelMatrix(TransformUtil.createModelMatrixVanilla(
                location, facing, scale, matrix));
        entity.setDefaultLight();
        return entity;
    }
}
```

:::

## 验证方案

前置数据：GearIn 行已并入 BoxUtil 启动期加载的 9 列模型 CSV，OBJ、diffuse、normal 与 emissive 文件均存在，战斗接线能把返回实体入队。

1. 启动后核对 `ModelManager.containsModelID("GearIn_gear")` 和 `ModelData.isValid()`。
2. 在玩家舰旁创建比例为 `(1, 1)` 的实体，核对位置和朝向。
3. 把比例改为 `(0.5, 0.5)`，确认世界尺寸减半而模型缓冲仍被复用。
4. 分别移除 emissive、normal 路径并重启，观察材质通道变化和加载日志。

预期结果：

- 精确模型 ID 返回有效、可复用的 `ModelData`；
- `CommonEntity` 的模型矩阵与传入位置、朝向、缩放一致；
- 默认光照能够区分漫反射、法线和发光通道的加载结果；
- 有效实体入队后没有未处理异常。

## 故障分析

- **构造实体时抛空指针异常**：模型 ID 大小写与 CSV 不一致 (`getModelData()` 返回 null，`new CommonEntity(model, true)` 构造期即 NPE)，或 `ModelData.isValid()` 为 false。

    - **解决方案**：先通过 `require()` 验证数据，再构造实体。

- **模型整体显示为黑色**：漫反射贴图或材质数据没有注册。

    - **解决方案**：补齐材质引用，重新载入。

- **光照方向与模型表面相反**：OBJ 的法线或切线方向不一致。

    - **解决方案**：修正并重新导出网格。

- **模型位置正确但比例巨大**：OBJ 单位与世界缩放没有统一。

    - **解决方案**：调整导出单位或模型矩阵缩放。

## 总结

模型加载分成数据注册、存在性确认、模型取得和世界变换。先让 ID 查询失败得足够明确，再处理法线、材质和尺度，排错顺序会短很多。

## 建议参考项

- `org.boxutil.manager.ModelManager`：注册并查询模型数据；
- `org.boxutil.units.standard.entity.CommonEntity`：组合模型与材质；
- `org.boxutil.util.TransformUtil`：`createModelMatrixVanilla` 建立世界变换。
