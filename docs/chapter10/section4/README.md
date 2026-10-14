# 放置生涯实体

本节在观测星系放置一个信标，配置位置、身份、交互图片与业务状态，再比较它和玩家舰队之间的可见性。放置脚本负责创建与配置，已有采样器继续读取同一实体，让坐标、轨道和检测结果都能对应到实际操作。

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

## 将信标放入已有星系

信标沿用 [C10-S2] 的 `GearIn_observation_system`，实体 ID 为 `GearIn_entity_beacon`。创建 `src/data/scripts/gearin/campaign/GearInEntityPlacement.java`，加入 `GearInModPlugin` 的 import，再将局部登记放在地点生成器登记之后：

```java
sector.addTransientScript(new GearInEntityPlacement());
```

脚本首次创建信标，后续进入存档时查询同一 ID，对已有对象应用当前配置，完成一轮便结束。因此改变参数后，可以通过构建、重启与读档继续使用同一个信标。

下面的局部片段展示创建与查询关系：

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

`addCustomEntity(...)` 将对象加入星系，使用原版 `comm_relay` 类型。地点与星区查询应取得同一个 token，`getContainingLocation()` 回读观测星系。名字可以在后面调整，ID 则用于存档、索引与后续交互查找，重复创建同 ID 会让这些读取失去明确对象。

## 选择固定坐标或轨道

实体已经属于地点，接下来确定它在哪里。本例的 `USE_FIXED_LOCATION` 选择两种方式，读档时重新应用当前分支。

固定位置调用会清除现有轨道，并写入坐标：

```java
entity.setFixedLocation(1600f, -900f);
```

需要绕恒星移动时，改用圆轨道：

```java
entity.setCircularOrbit(
        system.getStar(),
        45f,
        2600f,
        160f);
```

锚点为本系恒星，初始角度 45，半径 2600 个生涯地图单位，周期 160 个生涯日。位置随锚点与周期计算，锚点也应属于同一个地点。

**固定坐标与轨道分别提供位置，采用其中一种。** 仅改坐标却保留原有轨道，后续推进仍可能按轨道更新位置；本例的两个分支分别建立或清除轨道，验证也比较这两种结果。

## 配置名称、归属与业务状态

在同一实体上设置名称、势力、标签与朝向，再写入角色和就绪状态：

```java
entity.setName("观测信标");
entity.setFaction(Factions.INDEPENDENT);
entity.addTag("GearIn_entity_probe");
entity.setFacing(90f);
entity.getCustomData().put("GearIn_entity_role", "sensor");
entity.getMemoryWithoutUpdate().set("$GearIn_entity_ready", true);
```

名称和势力用于显示，`facing` 提供朝向，`GearIn_entity_probe` 标签用于分类。`hasTag(...)` 查询当前实体，地点的 `getEntitiesWithTag(...)` 查询具有该标签的实体集合。

custom data 是随实体保存的 Map，本例在 `GearIn_entity_role` 下保存字符串 `sensor`。其中对象也参与存档序列化，长期状态按实际所属实体安排。

Memory 则提供规则状态、事实与过期值语义。这里写入 `$GearIn_entity_ready`，纯读取时使用 `getMemoryWithoutUpdate()`；需要更新实体事实时，调用 `getMemory()`。不同的读取入口可能触发不同处理，日志与规则也沿实体自己的 Memory 查询。

## 将交互图片接到信标

`comm_relay` 类型从 `custom_entities.json` 提供地图外观与插件。交互图片另通过设置中的分类与键选择。本例登记原版中继站插图：

```json
{
    "graphics": {
        "illustrations": {
            "GearIn_entity_interaction": "graphics/illustrations/comm_relay.jpg"
        }
    }
}
```

把 `GearIn_entity_interaction` 合入已有 `graphics.illustrations`，然后在实体配置中选择这份登记，并取得 custom plugin：

```java
entity.setInteractionImage("illustrations", "GearIn_entity_interaction");
CustomCampaignEntityAPI customEntity = (CustomCampaignEntityAPI) entity;
boolean pluginLoaded = customEntity.getCustomPlugin() != null;
```

`setInteractionImage(...)` 指定交互时的图片，`getCustomPlugin()` 读取该实体类型创建的插件。地图显示中继站、交互界面显示插图、插件引用存在，分别对应这几处配置。靠近信标打开交互时，再将实际画面与日志对照。

## 从双方的传感器判断可见性

信标的传感器强度与轮廓都设为 1。写入参数后，读取玩家是否能发现它以及观察等级：

```java
entity.setSensorStrength(1f);
entity.setSensorProfile(1f);
float strength = entity.getSensorStrength();
float profile = entity.getSensorProfile();
boolean visible = entity.isVisibleToPlayerFleet();
VisibilityLevel level = entity.getVisibilityLevelToPlayerFleet();
```

检测还取决于地点、距离、传感器规则与实体的可发现状态，不能只凭两个参数已经写入就判断玩家必然看见它。

**`toPlayer` 与 `ofPlayer` 的检测方向相反。** `getVisibilityLevelToPlayerFleet()` 描述玩家对信标的观察，`getVisibilityLevelOfPlayerFleet()` 描述信标对玩家舰队的观察。每个方向都使用检测方的强度、被检测方的轮廓与双方距离，返回等级也可能不同。

### 沿现有采样器比较变化

信标放置完成后，由 [C10-S1] 的五日采样持续观察。在 `GearInSectorStateSampler` 加入 `SectorEntityToken` 的 import，将以下局部片段放入 `writeStateLog(...)`：

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

放置脚本记录初始配置后结束，采样器按稳定 ID 继续取得信标。靠近、远离和切换地点时，日志一起保留坐标、所属地点、检测参数与两种方向的等级。

比较时按当前地点和距离解释可见性，再对应观察等级。固定位置与轨道的实验也采用同一信标，参数变化就能沿这个 ID 找回，而无需重新创建另一份观察对象。

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

信标以稳定 ID 加入地点，固定坐标或轨道提供位置，类型提供外观与插件，设置中的图片键提供交互画面。标签、custom data 与 Memory 则分别保存分类和状态。

创建完成之后，采样器持续读取同一实体。玩家对信标与信标对玩家采用各自检测方向，实际可见性再结合地点和距离判断。

## 建议参考项

- `com.fs.starfarer.api.campaign.SectorEntityToken`：实体身份、位置、memory、tag、传感器和可见性入口；
- `com.fs.starfarer.api.campaign.CustomCampaignEntityAPI`：custom entity plugin、交互状态与 spec 行为入口；
- `data/config/custom_entities.json`：custom entity type、图片和 plugin 配置。
