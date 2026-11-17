# 放置生涯实体

在 [C10-S3] 把实体放进星系只是占住了位置，玩家隔着多远能在雷达上扫到它、点开后弹出什么界面与插图，全靠实体自己的这层属性撑着。

更重要的是，每件 `SectorEntityToken` 的轨道、传感器轮廓与交互配置都是实例级的独立状态。哪怕两座信标用的是同一个原版类型 ID，改其中一座的探测距离或插图，也不会牵连星区里的另一座。

---

_本节预期阅读时间：\~15 分钟。_

_本节预期操作时间：\~20 分钟。_

## 前置知识

- [C7-S1] 的 `onGameLoad(...)` 瞬态脚本挂载；
- [C10-S1] 的 `EveryFrameScript` 与 `sector.getEntityById(...)`；
- [C10-S2] 的实体级 `MemoryAPI` 与 `getMemoryWithoutUpdate()`；
- [C10-S3] 的 `Ashen Verge` 生成器与灰烬信标 (`GearIn_ashen_beacon`)。

## 自定义实体的类型定义与继承

`location.addCustomEntity(id, name, type, factionId)` 的第三个参数 `type` 指向 `data/config/custom_entities.json` 中的一条配置。我们在 [C10-S3] 创建灰烬信标时使用的是原版导航浮标 `Entities.NAV_BUOY` (`"nav_buoy"`)：

```java
SectorEntityToken beacon = system.addCustomEntity(
        BEACON_ID, "灰烬信标", Entities.NAV_BUOY, GearInModPlugin.FACTION_ID);
beacon.setCircularOrbitPointingDown(center, 130f, 1100f, 90f);
beacon.setInteractionImage("illustrations", "comm_relay");
```

在 `data/config/custom_entities.json` 中，`nav_buoy` 通过 `"baseId": "base_campaign_objective"` 继承了原版星系战略设施的公共底座，并指定了专属插件 `NavBuoyEntityPlugin`：

- 同势力的舰队进入该浮标所在的恒星系时，插件会给予 `+3` 最大航速加成 (原版 `NAV_BONUS`)；
- 浮标所属势力设为 `GearInModPlugin.FACTION_ID` 后，`Ashen Verge` 内的齿轮工业舰队直接享受该加成；玩家舰队想要获得加成，也可以通过原版内置的交互规则将其黑入 (`hack`)；
- 当您在自己的 `data/config/custom_entities.json` 中定义新实体类型时，同样可以用 `"baseId"` 继承已有条目 (例如 `weapons_cache`、`equipment_cache` 均继承自 `supply_cache`)。**但要注意：一旦在子条目中重新声明了 `"tags"` 数组，它会整体覆盖父条目的 `"tags"`，而不是追加。**

## 固定坐标与三种公转轨道

实体放进地点后，可以通过 `SectorEntityToken` 上的四组方法随时设定或切换它的运动方式：

```java
void setFixedLocation(float x, float y);
void setCircularOrbit(SectorEntityToken focus, float angle, float orbitRadius, float orbitDays);
void setCircularOrbitPointingDown(SectorEntityToken focus, float angle, float orbitRadius, float orbitDays);
void setCircularOrbitWithSpin(SectorEntityToken focus, float angle, float orbitRadius, float orbitDays,
        float minSpin, float maxSpin);
```

- `setFixedLocation(x, y)` 将实体钉死在固定坐标上，并自动清除此前绑定的公转轨道；
- `setCircularOrbit(...)` 让实体绕焦点 `focus` 按给定半径和周期公转，贴图朝向保持不变；
- `setCircularOrbitPointingDown(...)` 在公转的同时让实体贴图始终朝向轨道焦点，行星、信标与大型残骸普遍使用这一种；
- `setCircularOrbitWithSpin(...)` 在公转之外叠加一层介于 `minSpin` 与 `maxSpin` 之间的随机自转角速度，适合漂浮的小型货舱或碎片。

## 交互图、描述与开启交互的前提

玩家点击实体打开交互对话框时，界面呈现的内容由以下方法控制：

```java
void setInteractionImage(String category, String key);
void setCustomDescriptionId(String customDescriptionId);
void setActivePerson(PersonAPI activePerson);
void addFloatingText(String text, Color color, float duration);
```

- `setInteractionImage(category, key)` 替换对话框顶部的插图，对应 `settings.json` 中 `graphics` 下已登记的分类与键 (如原版 `graphics.illustrations` 下的 `"comm_relay"`)；
- `setCustomDescriptionId(id)` 替换实体的描述文本，对应 `descriptions.csv` 中类型为 `CUSTOM` 的条目 ID ([C4-S6])；
- `setActivePerson(person)` 指定打开该实体界面时默认出面交谈的 NPC 人物；
- `addFloatingText(...)` 在星图实体上方飘起一行文字提示，仅在实体对玩家可见时显示。

**上述交互设置能够被玩家点开的前提，是该实体在原版生涯插件 `CoreCampaignPluginImpl` 的派发范围内：** 除了显式带有 `"has_interaction_dialog"` 标签 (`Tags.HAS_INTERACTION_DIALOG`) 之外，拥有市场 (`getMarket() != null`)，或是带有 `Tags.OBJECTIVE`、`Tags.GATE`、`Tags.STATION` 标签的实体同样会被规则对话接管 (例如本例用到的 `nav_buoy` 正是通过 `Tags.OBJECTIVE` 启用交互的)。如果选用的是既无市场也不带这些标签的纯装饰条目，就需要通过 `entity.addTag(Tags.HAS_INTERACTION_DIALOG)` 补上交互入口。

## 双向可见性与传感器控制

判断 "玩家舰队有没有在雷达上发现这座实体"，需要区分两个方向相反的方法：

| 方法 | 含义 |
| --- | --- |
| `entity.getVisibilityLevelToPlayerFleet()` | **玩家舰队看该实体**的观察等级 |
| `entity.getVisibilityLevelOfPlayerFleet()` | **该实体看玩家舰队**的观察等级 |

返回值是 `VisibilityLevel` 枚举，按信息详细程度从低到高分为四档：

1. `NONE`：完全未探测到；
2. `SENSOR_CONTACT`：仅在雷达上看到未识别的传感器信号点；
3. `COMPOSITION_DETAILS`：已识别实体或舰队构成，但尚未识别所属势力；
4. `COMPOSITION_AND_FACTION_DETAILS`：完整识别构成与所属势力。

如果只需要布尔判断 "玩家是否已经看到该实体"，可以直接调用 `entity.isVisibleToPlayerFleet()` (等价于 `getVisibilityLevelToPlayerFleet() != VisibilityLevel.NONE`)。**不要把 `ToPlayerFleet` 和 `OfPlayerFleet` 写反**：两者的传感器强度与探测轮廓是互换计算的。一旦写反，玩家刚跳进星系还没看见信标，信标反倒先扫到了高信号特征的玩家舰队，脚本就提前判定 "玩家已发现信标" 了。

若要调整单个实体被玩家发现的难易度，`SectorEntityToken` 提供了以下实例级控制方法：

```java
float getSensorProfile();
void setSensorProfile(Float sensorProfile);
void setSensorStrength(Float sensorStrength);
void setDiscoverable(Boolean discoverable);
void setDetectionRangeDetailsOverrideMult(Float mult);
void forceSensorFaderBrightness(float brightness);
void fadeOutIndicator();
void fadeInIndicator();
```

- `setSensorProfile(profile)` 控制实体是否参与传感距离衰减与轮廓大小 (如 `1f` 按常规传感器公式计算；若在 `custom_entities.json` 中未配置 `"detectionRange"` 的实体如 `Entities.NAV_BUOY`，默认 `hasSensorProfile()` 为 `false`，`getVisibilityLevelToPlayerFleet()` 会在检查所在星系前就直接返回完全可见；同理，主动传入 `null` 会**关闭**距离限制让目标直接可见，原版 `CoreDiscoverEntityPlugin` 就在首次发现后通过 `setSensorProfile(null)` 让已发现目标常驻可见)；
- `setDiscoverable(false)` 只清除 "尚未发现" 标记 (不再触发 `"Discovered: ..."` 弹窗与首次发现经验)，**不会**让实体从传感器雷达上隐身；
- `setDetectionRangeDetailsOverrideMult(mult)` 会直接按倍率缩放非舰队实体的**实际细节识别距离** (`COMPOSITION_DETAILS` / `COMPOSITION_AND_FACTION_DETAILS`)；传负值时则会抑制细节展示，使可见度最高止步于未识别信号 (`SENSOR_CONTACT`)；
- `forceSensorFaderBrightness(...)`、`fadeInIndicator()` 与 `fadeOutIndicator()` 直接控制雷达指示框的亮暗淡入淡出。

## 灰烬信标发现脚本：GearInBeaconDiscoveryScript

[C10-S3] 放入 `Ashen Verge` 的灰烬信标 (`"GearIn_ashen_beacon"`) 是后续 [C16-S4] 调查委托的目标，选用原版 `Entities.NAV_BUOY`。由于 `nav_buoy` 默认没有 `"detectionRange"` (`hasSensorProfile() == false`)，本节在脚本调用 `getVisibilityLevelToPlayerFleet()` 之前先确保为它写入 `setSensorProfile(1f)`，再检测玩家舰队是否实际飞抵同一星系并进入探测范围；一旦可见度不为 `VisibilityLevel.NONE`，就在信标自身的 Memory 中写入 `$GearIn_entity_ready = true`。

### 完整发现脚本

`src/data/scripts/gearin/campaign/GearInBeaconDiscoveryScript.java`：

```java
package data.scripts.gearin.campaign;

import com.fs.starfarer.api.EveryFrameScript;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.SectorEntityToken.VisibilityLevel;
import com.fs.starfarer.api.util.IntervalUtil;

/** 玩家的传感器第一次捕捉到灰烬信标时，在信标自己的 Memory 中记下。 */
public class GearInBeaconDiscoveryScript implements EveryFrameScript {

    /** 信标 Memory：玩家已经发现这座信标。 */
    public static final String READY_KEY = "$GearIn_entity_ready";

    private final IntervalUtil interval = new IntervalUtil(0.5f, 1f);

    @Override
    public boolean isDone() {
        SectorEntityToken beacon = findBeacon();
        return beacon != null && beacon.getMemoryWithoutUpdate().getBoolean(READY_KEY);
    }

    @Override
    public boolean runWhilePaused() {
        return false;
    }

    @Override
    public void advance(float amount) {
        interval.advance(amount);
        if (!interval.intervalElapsed()) {
            return;
        }
        SectorEntityToken beacon = findBeacon();
        if (beacon == null) {
            return;
        }
        if (!beacon.hasSensorProfile()) {
            beacon.setSensorProfile(1f);
        }
        VisibilityLevel level = beacon.getVisibilityLevelToPlayerFleet();
        if (level != VisibilityLevel.NONE) {
            beacon.getMemoryWithoutUpdate().set(READY_KEY, true);
        }
    }

    private SectorEntityToken findBeacon() {
        return Global.getSector().getEntityById(GearInAshenVergeGenerator.BEACON_ID);
    }
}
```

### 在入口类中登记脚本

在 `GearInModPlugin.onGameLoad(...)` 中追加：

```java
if (!sector.hasTransientScript(GearInBeaconDiscoveryScript.class)) {
    sector.addTransientScript(new GearInBeaconDiscoveryScript());
}
```

如果存档中的灰烬信标已经被发现过 (`$GearIn_entity_ready` 为 `true`)，脚本挂载后会在第一帧由 `isDone()` 返回 `true` 并自动退场。

### 进游戏核对

执行 `gradle jar`，重启游戏，**新建一份存档**。

- 在未进入 `Ashen Verge` 时打开控制台，执行 `runcode Global.getSector().getEntityById("GearIn_ashen_beacon").getMemoryWithoutUpdate().getBoolean("$GearIn_entity_ready")`，输出为 `false`；
- 从超空间跳入 `Ashen Verge` 并向中心靠近，直到雷达捕捉到 "灰烬信标"；点开信标可以看到顶部显示原版通讯中继站的插图；
- 再次执行同一条 `runcode` 命令，输出变为 `true`；保存并重新读档后核对，结果仍保持为 `true`。

## 常见问题

- **`Global.getSector().getEntityById("GearIn_ashen_beacon")` 返回 `null`**：在旧存档中测试，尚未生成 `Ashen Verge`，或创建实体时的 ID 常量不一致。
    - **解决方案**：新建存档测试，并统一引用 `GearInAshenVergeGenerator.BEACON_ID`。

- **玩家刚跳入星系边缘、雷达上还没看到信标时，脚本就已经写入了 `$GearIn_entity_ready`**：误用了 `beacon.getVisibilityLevelOfPlayerFleet()`。
    - **解决方案**：判断玩家是否看见实体，必须使用 `beacon.getVisibilityLevelToPlayerFleet()`。

- **点开实体交互界面时顶部没有显示插图**：`setInteractionImage(category, key)` 传入的分类或键未在 `settings.json` 的 `graphics` 段中登记。
    - **解决方案**：核对 `settings.json` 中的贴图分类与键名。

## 建议参考项

- `com.fs.starfarer.api.campaign.SectorEntityToken`：轨道、交互展示、双向可见性 (`VisibilityLevel`) 与传感器轮廓控制方法。
- `com.fs.starfarer.api.impl.campaign.NavBuoyEntityPlugin`：原版导航浮标的星系内航速加成实现。
- `com.fs.starfarer.api.impl.campaign.BaseCampaignObjectivePlugin`：战略设施黑入状态与重置逻辑。
- `data/config/custom_entities.json`：原版自定义实体类型、`baseId` 继承与 `has_interaction_dialog` 标签配置。
