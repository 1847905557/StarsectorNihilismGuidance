# 用 BoxUtil 制作批量粒子与精灵实体

BoxUtil 的 `SpriteEntity` 可以用一次实体提交绘制多份实例数据。本节让玩家舰周围每两秒生成一圈 32 份青色粒子，并把 CPU 侧实例、GPU 提交和渲染数量三个动作明确分开。

*本节预期阅读时间：~18 分钟。*

*本节预期操作时间：~30 分钟。*

## 学习目标

完成后，您将能够：

- 判断 BoxUtil 的配置开关与 shader 核心是否同时可用；
- 读取设备同步兼容开关，并在异常设备上保留可观察的降级路径；
- 为一个 `SpriteEntity` 构造 32 份带位置、速度、颜色和寿命的 `Instance2Data`；
- 区分 CPU 列表、刷新范围、GPU 提交与实际绘制数量；
- 根据 `CombatRenderingManager.addEntity()` 返回码处理入队失败；
- 在 BoxUtil 渲染不可用时保留原版粒子反馈。

## 前置知识

- [C6-S2] 理解 Java 类、方法、字段与入口插件；
- [C7-S1] 理解每帧回调、暂停判断与运行时对象；
- 能够用 Gradle 构建示例 MOD，并从 `starsector.log` 定位类名。

## 明确当前需求

进入战斗后，玩家舰周围每 2 秒出现一圈 32 个方形粒子，四档半径让轮廓具有厚度；BoxUtil shader 不可用时，原版平滑粒子承担同一可观察反馈。

## 文件与 API 链路

### 粒子实例 API 职责速查表

| 对象 | 职责 |
|---|---|
| `SpriteEntity` | 持有材质和实例集合 |
| `Instance2Data` | 描述单个粒子的世界坐标、速度、颜色、缩放与计时 |
| `CombatRenderingManager` | 接收实体 |
| `BoxEnum` | 返回码决定实体所有权是否已经交给渲染管理器 |

### 确认 BoxUtil 渲染路径可用

**插入位置：`GearInBoxEntity.init()`。** 在 GL 上下文建立后检查配置和 `ShaderCore`。

```java
boxAvailable = BoxConfigs.isShaderEnable() && ShaderCore.isValid();
```

完成信号：关闭 shader 时插件进入原版粒子分支。

### 记录设备同步兼容状态

**插入位置：`GearInBoxEntity.init()` 的能力日志。** `isCompatibleSync()` 是 BoxUtil 对特定设备同步路径的兼容开关；它不代替 shader 能力判断，也不应被写成必然开启的门禁。

```java
boolean compatibleSync = BoxConfigs.isCompatibleSync();
LOG.info("GearIn box sync: compatible=" + compatibleSync);
```

完成信号：启动战斗时日志记录当前兼容值；切换 BoxUtil 配置后，下一次初始化读取到对应值。

### 创建一份精灵实体

**插入位置：`GearInBoxSpawner.spawnBox()`。** 选择贴图、颜色、图层与混合方式。

```java
SpriteEntity box = new SpriteEntity(sprite);
box.getMaterialData().setColor(Color.CYAN);
box.setLayer(CombatEngineLayers.ABOVE_PARTICLES_LOWER);
```

完成信号：实体材质为青色加法混合。

### 建立三十二份环形实例

**插入位置：`spawnBox()` 的 `instances` 构造处。** 均分角度并交错四档半径，再写入世界坐标、舰船速度、颜色、缩放与三段计时。

```java
for (int i = 0; i < PARTICLE_COUNT; i++) {
    float angle = player.getFacing() + i * 360f / PARTICLE_COUNT;
    float radius = 50f + (i % 4) * 18f;
    Instance2Data data = new Instance2Data();
    data.setLocation(MathUtils.getPoint(player.getLocation(), radius, angle));
    data.setVelocity(player.getVelocity());
    data.setColor(new Color(100, 220, 255, 210));
    data.setTimer(BOX_IN, BOX_FULL, BOX_OUT);
    instances.add(data);
}
```

完成信号：CPU 列表包含 32 份实例，所有位置都围绕本次生成时的玩家舰坐标。

### 完成 refresh、submit 与 count

**插入位置：`setInstanceData()` 之后。** 把修改范围提交到 GPU，并声明实际绘制数。

```java
box.setInstanceDataRefreshAllFromCurrentIndex();
box.submitInstanceData();
box.setRenderingCount(instances.size());
```

完成信号：粒子真正进入画面。

### 处理入队失败

**插入位置：`CombatRenderingManager.addEntity()` 返回后。** 失败时立即释放实体资源。

```java
byte result = CombatRenderingManager.addEntity(box);
if (result != BoxEnum.STATE_SUCCESS) box.delete();
```

完成信号：日志出现返回码，战斗继续运行。

## 排查关键故障

下面是**局部故障片段**：

```java
box.setInstanceData(instances);
CombatRenderingManager.addEntity(box);
```

实际症状：实体成功入队，日志也没有异常，画面却完全空白。

原因：实例只留在 CPU 集合，缺少 refresh、submit 与 renderingCount。

下面是**局部修正片段**：

```java
box.setInstanceDataRefreshAllFromCurrentIndex();
box.submitInstanceData();
box.setRenderingCount(instances.size());
```

复验：先记录日志中的 `instances=32`，再恢复三步提交进入战斗，确认 32 份实例同时出现。

## 完整最小例

接线插件每 2 秒调用一次生成器。生成器只创建一项 `SpriteEntity`，32 份粒子都通过同一实例列表提交；原版降级分支不创建 BoxUtil 资源。

## 验证方案

前置数据：BoxUtil 已完成全局初始化，粒子贴图路径有效，战斗插件已经注册。

1. 进入战斗并等待 2 秒，搜索 `GearIn box` 日志，核对 `instances=32`。
2. 暂停战斗，确认生成计时停止；恢复后等待下一组出现。
3. 观察 32 份粒子分布在 50、68、86、104 su 四档半径，并继承生成瞬间的玩家舰速度。
4. 临时移除 `submitInstanceData()`，确认实体入队但画面缺少实例；恢复完整提交链复验。
5. 关闭 BoxUtil shader，重启战斗并确认日志进入原版平滑粒子分支。
6. 分别保持默认同步设置和兼容设置，核对 `GearIn box sync` 日志与 `ShaderCore.isValid()` 结果。
7. 制造入队失败条件时，核对返回码警告和 `delete()` 清理路径。

预期结果：

- BoxUtil 可用时，每次生成只入队 1 个实体，但 `renderingCount` 为 32；
- 32 份实例的位置、速度、颜色、缩放和三段寿命均已写入并提交；
- 缺少刷新、提交或绘制数量中的任一步时，验证能稳定复现空白或数量错误；
- 入队失败时实体立即删除，不继续由调用侧推进；
- shader 不可用时只生成原版粒子，战斗没有未处理异常。
- 同步兼容设置只改变 BoxUtil 同步路径；能力不可用时仍进入原版粒子降级。

## 故障分析

- **实体入队后画面仍然空白**：实例只留在 CPU 集合，没有完成 refresh、submit 和 `renderingCount` 设置。补齐三步后，日志与画面应同时显示 32 份实例。
- **粒子只显示一份**：`renderingCount` 仍为 1，或实例列表只添加了一项。对照日志中的 `instances` 和提交数量，修正后确认显示数量一致。
- **颜色正确但位置不动**：`ControlData` 没有在 `advance` 中更新实例。每帧写入新位置并提交，移动舰船后确认精灵跟随。
- **战斗退出后资源持续增长**：入队失败或结束路径没有调用 `delete`。在退出回调清理全部实体，结束战斗后确认 BoxUtil 资源计数回落。
- **特定设备在同步调用处崩溃**：仍走不兼容的同步路径。读取 `isCompatibleSync()` 并启用兼容设置，重启战斗后确认日志值变化且 shader 失败时进入原版粒子。

## 总结

BoxUtil 实例渲染的关键不是 new 出对象，而是把 CPU 修改明确提交给渲染端。写实例、标记刷新、提交和设置数量是四个独立动作；返回码与释放路径同样属于成品。

## 建议参考项

- `SpriteEntity`：批量精灵实体；
- `Instance2Data`：单个二维实例状态；
- `CombatRenderingManager.addEntity`：加入 BoxUtil 战斗渲染队列。
- `BoxConfigs.isCompatibleSync`：读取设备同步兼容设置。
::: details 完整成品：GearInBoxEntity

```java
package data.scripts.gearin.lib;

import java.util.List;

import org.apache.log4j.Logger;
import org.boxutil.BoxUtilModPlugin;
import org.boxutil.config.BoxConfigs;
import org.boxutil.manager.ShaderCore;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.BaseEveryFrameCombatPlugin;
import com.fs.starfarer.api.combat.CombatEngineAPI;
import com.fs.starfarer.api.input.InputEventAPI;

/**
 * 完整最小例的接线载体：BoxUtil 初始化 (initLater 守卫)、可用性判据与生成
 * 计时在这里，实体生成委托给 GearInBoxSpawner；
 * 由既有战斗插件在 init() 中 addPlugin 注册 (C7-S1 模型)，只在本场战斗内有效。
 */
public class GearInBoxEntity extends BaseEveryFrameCombatPlugin {

    private static final Logger LOG = Global.getLogger(GearInBoxEntity.class);

    /** 生成间隔 (秒)。 */
    private static final float SPAWN_INTERVAL = 2f;

    private CombatEngineAPI engine;

    /** 初始化可用性：配置开关 && GPU 资源有效，二者共同决定 shader 渲染入口可用性。 */
    private boolean boxAvailable;

    private GearInBoxSpawner spawner;
    private float spawnTimer;

    @Override
    public void init(CombatEngineAPI engine) {
        this.engine = engine;
        // initLater 在 OpenGL context 创建后调用；isGlobalInitialized() 守卫保证只初始化一次
        if (!BoxUtilModPlugin.isGlobalInitialized()) {
            BoxUtilModPlugin.initLater();
        }
        boxAvailable = BoxConfigs.isShaderEnable() && ShaderCore.isValid();
        boolean compatibleSync = BoxConfigs.isCompatibleSync();
        spawner = new GearInBoxSpawner(engine, boxAvailable);
        LOG.info("GearIn box init: shaderEnable=" + BoxConfigs.isShaderEnable()
                + " shaderCoreValid=" + ShaderCore.isValid()
                + " available=" + boxAvailable
                + " compatibleSync=" + compatibleSync);
    }

    @Override
    public void advance(float amount, List<InputEventAPI> events) {
        if (engine == null) {
            return; // 防御性判空 (C2-S1 同款姿势)
        }
        if (engine.isPaused()) {
            return; // 暂停守卫：实体生成只在非暂停推进
        }
        spawnTimer += amount;
        if (spawnTimer >= SPAWN_INTERVAL) {
            spawnTimer = 0f;
            spawner.spawnBox();
        }
    }

}
```

:::

::: details 完整成品：GearInBoxSpawner

```java
package data.scripts.gearin.lib;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;

import org.apache.log4j.Logger;
import org.boxutil.base.api.InstanceDataAPI;
import org.boxutil.define.BoxEnum;
import org.boxutil.manager.CombatRenderingManager;
import org.boxutil.units.standard.attribute.Instance2Data;
import org.boxutil.units.standard.entity.SpriteEntity;
import org.lazywizard.lazylib.MathUtils;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.CombatEngineAPI;
import com.fs.starfarer.api.combat.CombatEngineLayers;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.graphics.SpriteAPI;

/**
 * 方框实体生成 (BoxUtil Entity / 实例数据工具族)：SpriteEntity + Instance2Data，
 * 写入实例字段只是客户端状态，必须 setInstanceDataRefreshAllFromCurrentIndex() +
 * submitInstanceData() + setRenderingCount() 完成提交 (addInstanceData 成功
 * 不等于 GPU 已上传)；addEntity 返回非 STATE_SUCCESS 时显式 delete()；
 * BoxUtil 不可用时回退原版粒子 (降级分支)。
 */
public class GearInBoxSpawner {

    private static final Logger LOG = Global.getLogger(GearInBoxSpawner.class);
    private static final String SPRITE_PATH = "graphics/fx/particlealpha32sq.png";
    private static final float BOX_IN = 0.2f;
    private static final float BOX_FULL = 1.5f;
    private static final float BOX_OUT = 0.5f;
    private static final int PARTICLE_COUNT = 32;

    private final CombatEngineAPI engine;
    private final boolean boxAvailable;
    private int boxCount;

    public GearInBoxSpawner(CombatEngineAPI engine, boolean boxAvailable) {
        this.engine = engine;
        this.boxAvailable = boxAvailable;
    }

    public void spawnBox() {
        ShipAPI player = engine.getPlayerShip();
        if (player == null || !player.isAlive()) {
            return;
        }
        if (!boxAvailable) {
            // 降级分支：shader 渲染入口不可用时，BoxUtil 实体不能作为唯一视觉输出
            engine.addSmoothParticle(player.getLocation(), player.getVelocity(),
                    24f, 0.6f, BOX_IN + BOX_FULL + BOX_OUT, Color.CYAN);
            LOG.info("GearIn box: box unavailable, vanilla fallback particle");
            return;
        }
        SpriteAPI sprite = Global.getSettings().getSprite(SPRITE_PATH);
        SpriteEntity box = new SpriteEntity(sprite);
        box.getMaterialData().setColor(Color.CYAN);
        box.setLayer(CombatEngineLayers.ABOVE_PARTICLES_LOWER);
        box.setAdditiveBlend();
        List<InstanceDataAPI> instances = new ArrayList<>();
        for (int i = 0; i < PARTICLE_COUNT; i++) {
            float angle = player.getFacing() + i * 360f / PARTICLE_COUNT;
            float radius = 50f + (i % 4) * 18f;
            Instance2Data data = new Instance2Data();
            data.setLocation(MathUtils.getPoint(player.getLocation(), radius, angle));
            data.setVelocity(player.getVelocity());
            data.setFacing(angle);
            data.setTurnRate(90f);
            data.setScale(0.6f, 0.6f);
            data.setScaleRate(0.5f, 0.5f);
            data.setColor(new Color(100, 220, 255, 210));
            data.setTimer(BOX_IN, BOX_FULL, BOX_OUT);
            instances.add(data);
        }
        // 实例数据提交链：写字段只是客户端状态，refresh + submit + count 才是提交点
        box.setInstanceData(instances);
        box.setInstanceDataRefreshAllFromCurrentIndex();
        box.submitInstanceData();
        box.setRenderingCount(instances.size());
        box.setAlwaysRefreshInstanceData(true);
        // 生命周期：全局计时到期后实体退出渲染
        box.setGlobalTimer(BOX_IN, BOX_FULL, BOX_OUT);
        byte result = CombatRenderingManager.addEntity(box);
        if (result != BoxEnum.STATE_SUCCESS) {
            box.delete(); // 入队失败：显式删除，不能当已入队继续推进
            LOG.warn("GearIn box: addEntity failed result=" + result);
            return;
        }
        boxCount++;
        LOG.info("GearIn box: #" + boxCount
                + " instances=" + instances.size()
                + " hull=" + player.getHullSpec().getHullName()
                + " layer=" + box.getCombatLayer());
    }
}
```

:::
