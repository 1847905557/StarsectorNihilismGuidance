# 放置生涯实体

观测星系已经建立，本节在其中放置一个信标，配置名称、势力、交互图片和轨道，再从地点与星区两处把它找回。实体自己的标签、Memory、custom data 与传感器分别承担不同用途；配置完成后，用定期日志比较它与玩家舰队的位置和可见性。

*本节预期阅读时间：\~22 分钟。*

*本节预期操作时间：\~30 分钟。*

## 学习目标

完成后，您将能够：

- 从地点创建具有稳定 ID 的实体并确认容器归属；
- 设置 name、faction、tag、facing、memory 和 custom data；
- 配置 interaction image 和 custom plugin 入口；
- 对照固定位置和圆轨道位置；
- 读取 sensor strength/profile 与玩家可见性。

## 前置知识

- [C10-S2] 的地点和 `StarSystemAPI` 查询；
- [C10-S1] 的当前星区和定期采样脚本；
- [C7-S1] 的生涯入口与日志读取；
- 理解实体 ID 必须在存档内稳定且唯一。

## 登记放置脚本并确认归属

创建 `src/data/scripts/gearin/campaign/GearInEntityPlacement.java`。在 [C7-S1] 的 `GearInModPlugin` 添加这个类的 import，把以下局部登记放到 `onGameLoad(...)` 中地点生成器登记之后，沿用已有的 `sector`：

```java
sector.addTransientScript(new GearInEntityPlacement());
```

本节沿用 [C10-S2] 的 `GearIn_observation_system`。脚本第一次创建信标，之后进入存档时对同一个 ID 配置当前参数，完成一轮后结束。

实体由 `addCustomEntity(...)` 创建并加入地点，`getContainingLocation()` 读取实际归属。下面的局部查询分别从地点与星区取得同一个信标：

```java
StarSystemAPI system = Global.getSector().getStarSystem(
        "GearIn_observation_system");
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

`byLocation` 和 `bySector` 应指向同一个 token，`entity.getContainingLocation()` 应返回 `GearIn_observation_system`。如果地点查询为空，先检查创建调用和地点注册，再检查全局索引。

::: tip 稳定 ID
ID 同时用于存档、地点 ID 索引、星区全局索引和后续交互查找。重复创建相同 ID 会让调用方无法判断目标对象，也会污染验证日志。
:::

## 设置身份和显示属性

**ID 用于查找，名称与势力用于显示，标签用于分类。** `facing` 设置朝向。custom data 随实体保存，本例放入字符串角色 `sensor`；Memory 则用于规则与事实，并可配置过期时间。保存到 custom data 的对象也参与存档序列化，长期数据按实际所属实体安排。

```java
entity.setName("观测信标");
entity.setFaction(Factions.INDEPENDENT);
entity.addTag("GearIn_entity_probe");
entity.setFacing(90f);
entity.getCustomData().put("GearIn_entity_role", "sensor");
entity.getMemoryWithoutUpdate().set("$GearIn_entity_ready", true);
```

`getMemory()` 会更新实体事实，当前读取已有状态用 `getMemoryWithoutUpdate()`。`hasTag(...)` 与地点的 `getEntitiesWithTag(...)` 查标签，custom data 按 Map 的键查值，两种分类入口各自对应本例的数据。

## 配置交互入口

先在 `data/config/settings.json` 的 `graphics.illustrations` 中登记本节键，使用原版中继站图片：

```json
{
    "graphics": {
        "illustrations": {
            "GearIn_entity_interaction": "graphics/illustrations/comm_relay.jpg"
        }
    }
}
```

`setInteractionImage(category, key)` 再选择分类与键。custom entity 的插件由 `custom_entities.json` 中的 type 提供，本例使用 `comm_relay`，通过 `getCustomPlugin()` 读取它的运行实例。局部检查如下：

```java
entity.setInteractionImage("illustrations", "GearIn_entity_interaction");
CustomCampaignEntityAPI customEntity = (CustomCampaignEntityAPI) entity;
boolean pluginLoaded = customEntity.getCustomPlugin() != null;
```

type 提供实体外观与插件，当前 `setInteractionImage(...)` 按 settings 分类与键选择交互图片。地图上出现一个中继站，尚不能代替交互图片与插件的验证；靠近后打开交互界面，再看实际显示。

## 对照固定位置和轨道位置

`setFixedLocation(x, y)` 会清除现有 orbit 并写入固定坐标；`setCircularOrbit(focus, angle, radius, days)` 会建立 orbit，位置由锚点和周期持续计算。**当前位置由固定坐标或轨道中的一种方式提供。** 本例用 `USE_FIXED_LOCATION` 选择两种配置，重新进入存档时应用当前选择。

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

## 让传感器日志持续观察当前位置

传感器属性属于实体。`setSensorStrength` 与 `setSensorProfile` 只写入检测参数，玩家是否看见实体还取决于地点、距离、传感器规则和实体自身的可发现状态。

```java
entity.setSensorStrength(1f);
entity.setSensorProfile(1f);
float strength = entity.getSensorStrength();
float profile = entity.getSensorProfile();
boolean visible = entity.isVisibleToPlayerFleet();
VisibilityLevel level = entity.getVisibilityLevelToPlayerFleet();
```

这里的方向也要分清：`getVisibilityLevelToPlayerFleet()` 表示玩家对实体的观察等级，`getVisibilityLevelOfPlayerFleet()` 表示实体对玩家舰队的观察等级。检测方的传感器强度、被检测方的轮廓与实际距离一起参与计算。

在 [C10-S1] 的 `GearInSectorStateSampler` 添加 `SectorEntityToken` 的 import，把以下局部片段加入 `writeStateLog(...)`。它跟随已有五日采样，不增加另一份计时：

```java
SectorEntityToken entity = sector.getEntityById("GearIn_entity_beacon");
if (entity != null) {
    LOG.info("GearIn entity visibility: location=" + entity.getContainingLocation().getId()
            + " position=" + entity.getLocation()
            + " strength=" + entity.getSensorStrength()
            + " profile=" + entity.getSensorProfile()
            + " visible=" + entity.isVisibleToPlayerFleet()
            + " toPlayer=" + entity.getVisibilityLevelToPlayerFleet()
            + " ofPlayer=" + entity.getVisibilityLevelOfPlayerFleet());
}
```

信标创建之后，这个观察入口就会记录它的状态。**配置值与检测结果一起比较。** 靠近、远离或切换地点后，先看坐标和所属地点，再看可见性；两种方向的等级也按各自检测方解释。

## 配置一个实体

放置类负责创建、应用配置和初次日志，定期观察沿用已有采样器。编者建议把身份与位置先看清，再增加交互：找到同一个对象，才能确定后面的状态属于谁。

## 完整最小例

保存下列类并保留前面的登记、图片配置与采样片段。

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
    private static final boolean USE_FIXED_LOCATION = false;

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
        StarSystemAPI system = sector.getStarSystem("GearIn_observation_system");
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
        }

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
        if (USE_FIXED_LOCATION) {
            entity.setFixedLocation(1600f, -900f);
        } else {
            entity.setCircularOrbit(
                    system.getStar(),
                    45f,
                    2600f,
                    160f);
        }
        entity.setSensorStrength(1f);
        entity.setSensorProfile(1f);

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

1. 保存放置类、生涯登记、图片配置与采样片段，构建重启，进入已生成 `GearIn_observation_system` 的存档。
2. 在地图中确认 `GearIn_entity_beacon` 的名称、势力颜色和轨道位置。
3. 搜索 `GearIn entity`，核对地点 ID、全局查询同一性、tag、memory、plugin 和传感器值。
4. 靠近与远离信标时各推进五日，比较 `GearIn entity visibility` 的坐标、visible、toPlayer 与 ofPlayer；切换地点后再记录一次。
5. 将 `USE_FIXED_LOCATION` 改为 `true`，构建并重启读档，确认信标位于 `(1600, -900)` 且 orbit 为空；改回 `false` 后再构建重启读档，确认恢复半径 2600、周期 160 日的轨道。

预期结果：

- 地图中 `GearIn_entity_beacon` 的名称、势力颜色和轨道位置与配置一致；
- 日志出现 `GearIn entity`，地点 ID、全局查询同一性、tag、memory、plugin 和传感器值与配置一致；
- 采样同时记录位置、地点与两种检测方向；切换地点后 `visible=false`，距离变化按实际检测范围反映在等级与可见性上；
- `setFixedLocation` 清除 orbit，轨道调用重新建立 orbit。

## 故障分析

- **全局查询返回空值**：查询 ID 与创建 ID 不同，或实体尚未加入当前星区管理的地点。
    - **解决方案**：核对 `GearIn_entity_beacon` 与 containing location，确认先完成 `addCustomEntity(...)`，再从当前星区查询。

- **实体位置保持旧坐标**：固定位置和轨道位置同时被写入。
    - **解决方案**：把固定位置和轨道位置作为互斥分支，只保留一种位置来源。

- **交互图片或 custom plugin 没有生效**：type 对应的 custom entity spec 或资源路径不存在。
    - **解决方案**：检查 type 的数据定义、interaction image 的 category/key 和 custom plugin 配置。

- **传感器属性已写入但实体不可见**：可见性还受到当前地点、距离、发现状态和传感器规则影响。
    - **解决方案**：同时记录 containing location、坐标、sensor strength/profile 和 VisibilityLevel，再逐项排除。

- **memory 读取结果不稳定**：读取使用了会触发更新的 `getMemory()`，或把 entity memory 和地点 memory 混用。
    - **解决方案**：纯读取使用 `getMemoryWithoutUpdate()`，并为实体和地点使用不同键前缀。

## 总结

信标以稳定 ID 加入观测星系，地点与星区查询找到同一个对象。名称、势力、标签与数据各有用途，settings 的图片键和实体 type 分别接到交互显示与插件。

位置由当前配置选择固定坐标或轨道，定期采样则把坐标与检测方向一起记录。配置已经写入、对象在什么地方、玩家能看见多少信息，这几项分别确认后，再用它们驱动生涯功能。

## 建议参考项

- `com.fs.starfarer.api.campaign.SectorEntityToken`：实体身份、位置、memory、tag、传感器和可见性入口；
- `com.fs.starfarer.api.campaign.CustomCampaignEntityAPI`：custom entity plugin、交互状态与 spec 行为入口；
- `data/config/custom_entities.json`：custom entity type、图片和 plugin 配置。
