# 用 BoxUtil 批量提交千个渲染实例

一千个粒子若对应一千个实体，会把管理和提交成本同时放大。本节让一个 SpriteEntity 持有一千份 Instance2Data，复用对象池，并只上传本帧真正变化的范围。

*本节预期阅读时间：~18 分钟。*

*本节预期操作时间：~30 分钟。*

## 学习目标

完成后，您将能够：

- 用一个 `SpriteEntity` 持有 1000 份二维实例状态；
- 用对象池复用 `Instance2Data`，并在归还时清除旧状态；
- 把完整初始化提交与后续局部刷新分开；
- 从渲染管理器移除实体后再归还实例池；
- 让 `renderingCount` 与本批有效实例数保持一致；
- 用实体数、实例数、提交次数和池容量核对批处理成本。

## 前置知识

- [C6-S2] 理解 Java 类、方法、字段与入口插件；
- [C7-S1] 理解每帧回调、暂停判断与运行时对象；
- 能够用 Gradle 构建示例 MOD，并从 `starsector.log` 定位类名。

## 明确当前需求

测试战斗中同时显示 1000 个小型能量点；统计面板显示一个实体、一批实例。重复生成与清理后，池中对象数量稳定复用。

## 文件与 API 链路

### 批量实例 API 职责速查表

| 对象 | 职责 |
|---|---|
| `SpriteEntity` | 一次批量绘制 |
| `InstanceDataAPI` | 实例集合接口 |
| `Instance2Data` | 保存二维状态 |
| `GearInBatchPool` | 管理可复用对象 |
| `GearInThousandInstanceBatch` | 首次全量提交、后续局部刷新和移除后的归还 |

### 预分配一千容量的实例列表

**插入位置：`GearInThousandInstanceBatch.submit()` 开头。** 避免 `ArrayList` 在构造批次时多次扩容。

```java
List<InstanceDataAPI> instances = new ArrayList<>(count);
```

完成信号：列表 capacity 与目标 count 一致。

### 从池中取得可复用实例

**插入位置：`submit()` 的循环内。** 只在池为空时创建 `Instance2Data`。

```java
Instance2Data data = pool.acquire();
```

完成信号：第二次生成批次时大部分对象来自池。

### 为每份实例写入稳定网格位置

**插入位置：`submit()` 循环体。** 建立可数的 40 × 25 测试阵列。

```java
data.setLocation(i % 40 * 18f, i / 40 * 18f);
data.setScale(0.5f, 0.5f);
```

完成信号：画面完整出现 1000 个点且间距一致。

### 一次提交全部实例

**插入位置：循环结束后。** 设置集合、刷新全部范围、上传并声明数量。

```java
entity.setInstanceData(instances);
entity.setInstanceDataRefreshAllFromCurrentIndex();
entity.submitInstanceData();
entity.setRenderingCount(instances.size());
```

完成信号：渲染统计显示一个 SpriteEntity 绘制 1000 份实例。

### 只上传本帧变化的区间

**插入位置：`GearInThousandInstanceBatch.updateRange()`。** 调用方修改连续实例区间后，只标记该起点和长度。

```java
entity.setInstanceDataRefreshIndex(start);
entity.setInstanceDataRefreshSize(size);
entity.submitInstanceData();
```

完成信号：修改索引 400–499 时，刷新起点为 400、长度为 100，`renderingCount` 仍为 1000。

### 结束时复位并归还对象池

**插入位置：实体已经从渲染管理器移除后的批次清理。** 逐项 `reset()` 并归还池，最后清空实体的客户端实例数据。

```java
for (InstanceDataAPI item : entity.getInstanceData()) {
    pool.release((Instance2Data) item);
}
entity.resetInstanceData();
```

完成信号：连续生成十轮后池大小稳定为 1000。

### 先移除实体再归还实例

**插入位置：`GearInThousandInstanceBatch.removeAndRelease()`。** 管理器移除决定渲染所有权是否已经结束，池回收只能发生在移除成功之后。

```java
boolean removed = CombatRenderingManager.removeEntity(entity);
if (removed) {
    release(entity, pool);
}
```

完成信号：实体不再出现在管理器中，1000 份实例进入池；重复 `removeEntity()` 返回 `false`，不会重复归还。

## 排查关键故障

下面是**局部故障片段**：

```java
for (InstanceDataAPI data : instances) {
    entity.submitInstanceData();
}
```

实际症状：1000 个实例可以显示，但帧时间明显抖动，提交次数随实例数线性增长。

原因：完整实例缓冲在循环内重复上传了 1000 次。

下面是**局部修正片段**：

```java
entity.setInstanceData(instances);
entity.setInstanceDataRefreshAllFromCurrentIndex();
entity.submitInstanceData();
```

复验：统计 `submitInstanceData()` 调用，确认首次批次只执行 1 次，而非 1000 次；后续 100 项局部修改再执行 1 次区间提交。

## 完整最小例

池只管理 Java 实例对象，批次类管理它们与 `SpriteEntity` 的所有权转换。`release()` 只能在实体不再被渲染线程读取后调用。

## 验证方案

前置数据：一个有效 `SpriteEntity` 已创建但尚未持有实例，测试面板能显示实体数、实例数、提交次数和池容量。

1. 使用 `count=1000` 首次提交，核对 40 × 25 网格、`renderingCount=1000` 和 1 次上传。
2. 修改索引 400–499 的位置，调用 `updateRange(entity, 400, 100)`，核对刷新区间和上传次数。
3. 从渲染管理器移除实体后调用 `release()`，确认池容量变为 1000，实体实例数据清空。
4. 通过 `removeAndRelease()` 移除实体，再次调用移除入口，确认第二次返回 `false`。
5. 再次提交 1000 项，统计新建 `Instance2Data` 数量应为 0。
6. 连续执行 10 轮提交、移除和归还，记录每轮池容量与上传次数。
7. 把 `submitInstanceData()` 放入实例循环复现故障，再恢复单次提交。

预期结果：

- 首轮由 1 个实体绘制 1000 项，完整提交次数为 1；
- 100 项连续修改只标记 100 项刷新区间，不改变总绘制数量；
- 实体移除后 1000 项全部重置并进入池，客户端实例列表清空；
- 管理器重复移除不会重复归还实例，`delete()` 重复调用不会产生二次清理错误；
- 第二轮不创建新的 `Instance2Data`，10 轮后池容量仍稳定为 1000；
- 循环内提交的故障会产生 1000 次上传，修正后恢复为每批 1 次；
- 战斗没有未处理异常。

## 故障分析

- **千个实例出现但帧时间明显抖动**：完整实例缓冲在循环内重复上传了 1000 次。字段写入留在循环内，刷新和提交移到循环后；用调用计数确认每批只上传一次。
- **画面只显示前一部分实例**：`renderingCount` 小于实例集合大小。将绘制数量设为本批有效实例数，再核对日志计数与画面数量一致。
- **复用实例仍带着旧位置**：对象归还池前没有重置状态。`release` 时清理位置、颜色和寿命，重新 acquire 后确认字段为初始值。
- **对象池启用后每帧仍有大量分配**：`advance` 中反复创建临时列表、颜色或向量。复用这些临时对象，再比较分配计数和帧时间。
- **归还后画面仍读取旧实例**：实体仍在渲染管理器中就先归还了池。先确认 `removeEntity()` 返回 `true`，再归还并重复生成一批实例。

## 总结

千实例的重点是把对象数量与提交次数分离。实例可以有一千份，实体和全量提交仍各为一次；对象池负责复用状态，刷新区间负责限制后续更新成本。

## 建议参考项

- `Instance2Data`：二维实例状态；
- `SpriteEntity.submitInstanceData`：上传实例缓冲；
- `setRenderingCount`：声明本批实际绘制数量。
- `CombatRenderingManager.removeEntity`：结束战斗渲染所有权。
::: details 完整成品：GearInBatchPool

```java
package data.scripts.gearin.lib;

import java.util.ArrayDeque;
import java.util.Deque;

import org.boxutil.units.standard.attribute.Instance2Data;

public final class GearInBatchPool {
    private final Deque<Instance2Data> free = new ArrayDeque<>();

    public Instance2Data acquire() {
        Instance2Data data = free.pollFirst();
        return data == null ? new Instance2Data() : data;
    }

    public void release(Instance2Data data) {
        data.reset();
        free.addFirst(data);
    }

    public int available() {
        return free.size();
    }
}
```

:::

::: details 完整成品：GearInThousandInstanceBatch

```java
package data.scripts.gearin.lib;

import java.util.ArrayList;
import java.util.List;

import org.boxutil.base.api.InstanceDataAPI;
import org.boxutil.units.standard.attribute.Instance2Data;
import org.boxutil.units.standard.entity.SpriteEntity;
import org.boxutil.manager.CombatRenderingManager;

public final class GearInThousandInstanceBatch {
    public void submit(SpriteEntity entity, GearInBatchPool pool, int count) {
        List<InstanceDataAPI> instances = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            Instance2Data data = pool.acquire();
            data.setLocation(i % 40 * 18f, i / 40 * 18f);
            data.setScale(0.5f, 0.5f);
            instances.add(data);
        }
        entity.setInstanceData(instances);
        entity.setInstanceDataRefreshAllFromCurrentIndex();
        entity.submitInstanceData();
        entity.setRenderingCount(instances.size());
    }

    public void updateRange(SpriteEntity entity, int start, int size) {
        entity.setInstanceDataRefreshIndex(start);
        entity.setInstanceDataRefreshSize(size);
        entity.submitInstanceData();
    }

    public void release(SpriteEntity entity, GearInBatchPool pool) {
        for (InstanceDataAPI item : entity.getInstanceData()) {
            pool.release((Instance2Data) item);
        }
        entity.resetInstanceData();
    }

    public boolean removeAndRelease(SpriteEntity entity, GearInBatchPool pool) {
        boolean removed = CombatRenderingManager.removeEntity(entity);
        if (removed) {
            release(entity, pool);
        }
        return removed;
    }
}
```

:::
