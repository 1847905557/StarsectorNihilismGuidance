# 放置生涯实体

本节处理 `SectorEntityToken` 的完整状态：先创建稳定可查询的实体并确认地点归属，再设置显示、交互、memory、位置和传感器属性。实体属性与交互是本页重点，地点生成细节只通过已有地点入口取得。

*本节预期阅读时间：~22 分钟。*

*本节预期操作时间：~30 分钟。*

## 学习目标

完成后，您将能够：

- 从地点创建具有稳定 ID 的实体并确认容器归属；
- 设置 name、faction、tag、facing、memory 和 custom data；
- 配置 interaction image 和 custom plugin 入口；
- 对照固定位置和圆轨道位置；
- 读取 sensor strength/profile 与玩家可见性。

## 前置知识

- [C10-S2] 的地点和 `StarSystemAPI` 查询；
- [C10-S1] 的 `SectorAPI.getEntityById()` 全局查询；
- [C7-S1] 的生涯入口与日志读取；
- 理解实体 ID 必须在存档内稳定且唯一。

## 创建实体并确认归属

实体由目标 `StarSystemAPI.addCustomEntity` 创建。创建返回的 token 已经加入地点对象集合；`getContainingLocation()` 是运行时归属判断，不能只依赖创建时的局部变量。

```java
StarSystemAPI system = Global.getSector().getStarSystem(
        "GearIn_entity_system");
SectorEntityToken entity = system.addCustomEntity(
        "GearIn_entity_beacon",
        "观测信标",
        "comm_relay",
        Factions.INDEPENDENT);

SectorEntityToken byLocation = system.getEntityById(
        "GearIn_entity_beacon");
SectorEntityToken bySector = Global.getSector().getEntityById(
        "GearIn_entity_beacon");
```

`byLocation` 和 `bySector` 应指向同一个 token，`entity.getContainingLocation()` 应返回 `GearIn_entity_system`。如果地点查询为空，先检查创建调用和地点注册，再检查全局索引。

::: tip 稳定 ID
ID 同时用于存档、地点 ID 索引、星区全局索引和后续交互查找。重复创建相同 ID 会让调用方无法判断目标对象，也会污染验证日志。
:::

## 设置身份和显示属性

实体身份由 ID、名称和势力组成；tag 用于业务分类，facing 控制显示朝向。custom data 适合保存实体自身的非持久业务对象，memory 适合参与规则和事实读取。

```java
entity.setName("观测信标");
entity.setFaction(Factions.INDEPENDENT);
entity.addTag("GearIn_entity_probe");
entity.setFacing(90f);
entity.getCustomData().put("GearIn_entity_role", "sensor");
entity.getMemoryWithoutUpdate().set("$GearIn_entity_ready", true);
```

`getMemory()` 可能触发事实更新，纯读取状态时使用 `getMemoryWithoutUpdate()`。标签查询使用 `hasTag` 或地点的 `getEntitiesWithTag`，不要把 custom data 键当作地点标签。

## 配置交互入口

`setInteractionImage(category, key)` 为实体配置交互界面使用的图片资源。custom entity 的 plugin 来自 custom entity spec，运行时可以通过 `getCustomPlugin()` 检查是否加载成功；页面只验证入口，不把对话插件实现混入实体主题。

```java
entity.setInteractionImage("illustrations", "GearIn_entity_interaction");
CustomCampaignEntityAPI customEntity = (CustomCampaignEntityAPI) entity;
boolean pluginLoaded = customEntity.getCustomPlugin() != null;
```

交互图片路径和 custom plugin 由 `type` 对应的数据定义提供。代码只设置实体侧入口；资源缺失或 type 没有 plugin 时，实体仍可能出现在地图上，但交互行为不会完整建立。

## 对照固定位置和轨道位置

`setFixedLocation(x, y)` 会清除现有 orbit 并写入固定坐标；`setCircularOrbit(focus, angle, radius, days)` 会建立 orbit，位置由锚点和周期持续计算。一个实体只保留一种位置来源。

```java
entity.setFixedLocation(1600f, -900f);
```

需要运动时改为：

```java
entity.setCircularOrbit(
        system.getStar(),
        45f,
        2600f,
        160f);
```

锚点应属于同一个地点；轨道半径和周期是生涯地图单位与生涯天数，不是像素和现实秒数。

## 读取传感器和可见性

传感器属性属于实体。`setSensorStrength` 与 `setSensorProfile` 只写入检测参数，玩家是否看见实体还取决于地点、距离、传感器规则和实体自身的可发现状态。

```java
entity.setSensorStrength(1f);
entity.setSensorProfile(1f);
float strength = entity.getSensorStrength();
float profile = entity.getSensorProfile();
boolean visible = entity.isVisibleToPlayerFleet();
VisibilityLevel level = entity.getVisibilityLevelOfPlayerFleet();
```

验证时同时记录属性值和可见性结果。属性已经写入而 `visible=false`，问题通常发生在当前位置、距离、传感器规则或实体发现状态，而不是 setter 没有生效。

## 配置一个实体

完整示例只负责创建和配置实体；地点查询与传感器检查用正文局部片段完成，不再增加第二个完整角色。

## 完整最小例

::: details 完整成品：GearInEntityPlacement
```java
package data.scripts.gearin.campaign;

import org.apache.log4j.Logger;

import com.fs.starfarer.api.EveryFrameScript;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CustomCampaignEntityAPI;
import com.fs.starfarer.api.campaign.SectorAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.StarSystemAPI;
import com.fs.starfarer.api.impl.campaign.ids.Factions;

public class GearInEntityPlacement implements EveryFrameScript {

    private static final Logger LOG = Global.getLogger(GearInEntityPlacement.class);

    private boolean done;

    @Override
    public boolean isDone() {
        return done;
    }

    @Override
    public boolean runWhilePaused() {
        return false;
    }

    @Override
    public void advance(float amount) {
        if (done) {
            return;
        }

        SectorAPI sector = Global.getSector();
        StarSystemAPI system = sector.getStarSystem("GearIn_entity_system");
        if (system == null) {
            LOG.warn("GearIn entity placement: system missing");
            done = true;
            return;
        }

        SectorEntityToken entity = system.getEntityById(
                "GearIn_entity_beacon");
        if (entity == null) {
            entity = system.addCustomEntity(
                    "GearIn_entity_beacon",
                    "观测信标",
                    "comm_relay",
                    Factions.INDEPENDENT);
            entity.setName("观测信标");
            entity.setFaction(Factions.INDEPENDENT);
            entity.addTag("GearIn_entity_probe");
            entity.setFacing(90f);
            entity.getCustomData().put("GearIn_entity_role", "sensor");
            entity.getMemoryWithoutUpdate().set(
                    "$GearIn_entity_ready",
                    true);
            entity.setInteractionImage(
                    "illustrations",
                    "GearIn_entity_interaction");
            entity.setCircularOrbit(
                    system.getStar(),
                    45f,
                    2600f,
                    160f);
            entity.setSensorStrength(1f);
            entity.setSensorProfile(1f);
        }

        CustomCampaignEntityAPI customEntity =
                (CustomCampaignEntityAPI) entity;
        SectorEntityToken globalEntity = sector.getEntityById(
                "GearIn_entity_beacon");
        String locationId = entity.getContainingLocation() == null
                ? "null"
                : entity.getContainingLocation().getId();

        LOG.info(String.format(
                "GearIn entity: id=%s location=%s globalSame=%s tag=%s ready=%s plugin=%s strength=%.2f profile=%.2f visible=%s",
                entity.getId(),
                locationId,
                entity == globalEntity,
                entity.hasTag("GearIn_entity_probe"),
                entity.getMemoryWithoutUpdate().getBoolean(
                        "$GearIn_entity_ready"),
                customEntity.getCustomPlugin() != null,
                entity.getSensorStrength(),
                entity.getSensorProfile(),
                entity.isVisibleToPlayerFleet()));
        done = true;
    }
}
```
:::

## 验证方案

前置数据：[C10-S2] 的地点生成示例已就绪，包含本节实体配置示例的 GearIn MOD 已构建并启用。

1. 使用地点生成示例建立 `GearIn_entity_system`，再启用实体配置示例。
2. 在地图中确认 `GearIn_entity_beacon` 的名称、势力颜色和轨道位置。
3. 搜索 `GearIn entity`，核对地点 ID、全局查询同一性、tag、memory、plugin 和传感器值。
4. 在实体附近和远离实体的位置分别读取 `visible` 与 `VisibilityLevel`。
5. 暂时把实体改为固定坐标，再改回轨道，确认 `setFixedLocation` 清除 orbit，轨道调用重新建立 orbit。

预期结果：

- 地图中 `GearIn_entity_beacon` 的名称、势力颜色和轨道位置与配置一致；
- 日志出现 `GearIn entity`，地点 ID、全局查询同一性、tag、memory、plugin 和传感器值与配置一致；
- 实体附近与远离实体处的 `visible` 与 `VisibilityLevel` 读数不同；
- `setFixedLocation` 清除 orbit，轨道调用重新建立 orbit。

## 故障分析

- **地点查询有实体但全局查询为空**：实体没有完成星区索引更新，或查询发生在地点注册之前。
    - **解决方案**：确认实体由地点的 `addCustomEntity` 创建，并在下一帧重新执行全局查询。

- **实体位置保持旧坐标**：固定位置和轨道位置同时被写入。
    - **解决方案**：把固定位置和轨道位置作为互斥分支，只保留一种位置来源。

- **交互图片或 custom plugin 没有生效**：type 对应的 custom entity spec 或资源路径不存在。
    - **解决方案**：检查 type 的数据定义、interaction image 的 category/key 和 custom plugin 配置。

- **传感器属性已写入但实体不可见**：可见性还受到当前地点、距离、发现状态和传感器规则影响。
    - **解决方案**：同时记录 containing location、坐标、sensor strength/profile 和 VisibilityLevel，再逐项排除。

- **memory 读取结果不稳定**：读取使用了会触发更新的 `getMemory()`，或把 entity memory 和地点 memory 混用。
    - **解决方案**：纯读取使用 `getMemoryWithoutUpdate()`，并为实体和地点使用不同键前缀。

## 总结

实体页先确认创建入口、稳定 ID 和地点归属，再配置身份、显示、交互、memory、位置和传感器。固定位置与轨道位置互斥；可见性验证需要把实体属性和地点运行状态一起观察。

## 建议参考项

- `com.fs.starfarer.api.campaign.SectorEntityToken`：实体身份、位置、memory、tag、传感器和可见性入口；
- `com.fs.starfarer.api.campaign.CustomCampaignEntityAPI`：custom entity plugin、交互状态与 spec 行为入口；
- `data/config/custom_entities.json`：custom entity type、图片和 plugin 配置。
