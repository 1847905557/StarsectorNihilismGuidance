# 放置生涯实体

实体放进星系之后，还有一层是玩家怎么发现它。可见等级是算出来的结果，能改这个结果的方法在 `SectorEntityToken` 上有一整组：轮廓、传感器强度、够不够被发现、雷达图标要不要提前亮。

改的是这一件实体。同一类型的两个实体各改各的，互相不影响。

点开实体时看到什么，是另一组方法：插图、描述文本、说话的人、上方那行浮字。这些都有前提：条目要带能开交互的标签，原版 89 条里 27 条有。

---

_本节预期阅读时间：\~15 分钟。_

_本节预期操作时间：\~20 分钟。_

## 前置知识

- [C10-S2] 的 Ashen Verge 与生成器，信标在其中的 `generate(...)` 里创建；
- [C10-S1] 的 `EveryFrameScript` 与 Memory 读写；
- [C7-S1] 的 `onGameLoad(...)`，发现脚本在那里登记。

## 创建信标

信标已经在 [C10-S2] 的生成器中放好，这里说明其中每一项选择：

```java
SectorEntityToken beacon = system.addCustomEntity(
        BEACON_ID, "灰烬信标", Entities.NAV_BUOY, GearInModPlugin.FACTION_ID);
beacon.setCircularOrbitPointingDown(center, 130f, 1100f, 90f);
beacon.setInteractionImage("illustrations", "comm_relay");
```

`Entities.NAV_BUOY` 是原版的导航浮标，定义在 `custom_entities.json` 中，继承自 `base_campaign_objective`。同势力的舰队进入它所在的地点，会获得 +3 的最大航速等级 (原版 `NAV_BONUS`)，在超空间中不生效；被玩家黑入之后，加成对玩家舰队同样生效。黑入由原版的规则命令完成，不需要自己编写。

势力写 `GearInModPlugin.FACTION_ID`，信标便属于齿轮工业。Ashen Verge 里的齿轮工业舰队因此能吃到这份加成；玩家也想要，就得先把它黑下来。

`BEACON_ID` 是 `"GearIn_ashen_beacon"`。**实体 ID 必须在存档内唯一且不再改变**，后续脚本靠它查找信标。

### 轨道与交互图

位置用 `setCircularOrbitPointingDown(focus, angle, radius, days)` 指定，焦点是 [C10-S2] 中 `initNonStarCenter()` 返回的中心点。需要固定在某处时，改用 `setFixedLocation(x, y)`，它会清除已有的轨道。

`setInteractionImage(category, key)` 的第一个参数是类别，第二个是键。键 `comm_relay` 已登记在原版 `settings.json` 的 `graphics.illustrations` 中，直接引用即可，不必在自己的 `settings.json` 里重复登记。引用一个没有登记的键，交互界面会缺少插图。

## 玩家看得见它吗

实体与玩家舰队之间的可见性有两个方向：

| 方法 | 含义 |
| ---- | ---- |
| `getVisibilityLevelToPlayerFleet()` | 玩家舰队对该实体的观察等级 |
| `getVisibilityLevelOfPlayerFleet()` | 该实体对玩家舰队的观察等级 |

等级是 `VisibilityLevel` 枚举，从低到高依次为 `NONE`、`SENSOR_CONTACT`、`COMPOSITION_DETAILS`、`COMPOSITION_AND_FACTION_DETAILS`。`isVisibleToPlayerFleet()` 返回布尔值，用于只需要知道看得见与否的场合。

**两个方向用到的传感器强度与轮廓是互换的。** 方向写反，脚本会在玩家毫无察觉时就判定为已经发现。信标的 `getSensorProfile()` 与 `setSensorProfile(...)` 控制它多容易被发现，改小之后，玩家需要靠得更近。

## 发现得早还是晚

可见等级是结果，`SectorEntityToken` 上还有几个方法能改这个结果。

```java
void setSensorProfile(Float sensorProfile);
void setSensorStrength(Float sensorStrength);
void setDiscoverable(Boolean discoverable);
void setDetectionRangeDetailsOverrideMult(Float mult);
void forceSensorFaderBrightness(float brightness);
void fadeOutIndicator();
void fadeInIndicator();
```

`setSensorProfile(...)` 是这件东西有多显眼。玩家只要进入这个数值对应的距离，就能拿到传感器接触。传 `null` 用回默认值。信标默认的轮廓不大，玩家从超空间跳进来之后往往要走近一点才看得见。

`setDiscoverable(false)` 让它彻底无法被发现。隐藏内容用它，玩家在附近也拿不到接触，雷达上什么都没有。

`setDetectionRangeDetailsOverrideMult(...)` 乘在提示里那个距离数字上，只改数字，不改实际探测：`2f` 会让鼠标悬停时显示的距离翻倍。**两者不一致时，实际探测仍然以 `sensorProfile` 为准。**

`forceSensorFaderBrightness(...)` 与 `fadeInIndicator()`、`fadeOutIndicator()` 管的是雷达上的图标。图标平时由可见性驱动淡入淡出，这三项可以直接把它顶成常亮或淡出。做剧情演出时用它让玩家注意到某处，做完了记得让图标回到正常状态。

**这几个值改的是这一件实体，与势力、与地点无关。** 同一个 `Entities.NAV_BUOY` 类型，原版导航浮标一个轮廓，自己放的信标另一个轮廓，两者互不影响。

## 交互图与描述

玩家点开实体时看到的界面由几项决定：

```java
void setInteractionImage(String category, String key);
void setCustomDescriptionId(String customDescriptionId);
void setActivePerson(PersonAPI activePerson);
void addFloatingText(String text, Color color, float duration);
```

`setInteractionImage(...)` 换插图，键要在 `settings.json` 的 `graphics.illustrations` 里登记过。`setCustomDescriptionId(...)` 换掉描述文本，对应 `strings.json` 里的一组键。`setActivePerson(...)` 指定这次交互里说话的人。

`addFloatingText(...)` 在实体上方挂一行提示，只在实体可见时显示。做首次接触的指引时用它比弹窗轻。

**这些方法只对带 `has_interaction_dialog` 标签的条目有实际效果。** 原版 89 条自定义实体里只有 27 条带这个标签。自己新增条目时忘了这一项，调用上面任何一个方法，玩家都点不开界面。

## 发现脚本

玩家的传感器第一次捕捉到信标时，脚本在信标自己的 Memory 里写入 `$GearIn_entity_ready`。后续章节的规则和脚本，读取这个键就知道玩家已经注意到了这座信标。

脚本每 0.5 至 1 秒检查一次，用 `getVisibilityLevelToPlayerFleet()` 取得观察等级，只要不是 `NONE` 就写入：

```java
VisibilityLevel level = beacon.getVisibilityLevelToPlayerFleet();
if (level != VisibilityLevel.NONE) {
    beacon.getMemoryWithoutUpdate().set(READY_KEY, true);
}
```

键写在信标的 Memory 上，因为它描述的只是这一座信标，与 [C10-S1] 里写在市场 Memory 的时间戳同理。写入使用 `getMemoryWithoutUpdate()`，只存值，不触发 Memory 的更新处理。

`isDone()` 读取同一个键，发现之后脚本在下一帧被清除。脚本是 transient 的，读档后由入口类重新登记，是否还需要它，由存档里的标记决定。

### 登记脚本

在 `GearInModPlugin.onGameLoad(...)` 中加入：

```java
if (!sector.hasTransientScript(GearInBeaconDiscoveryScript.class)) {
    sector.addTransientScript(new GearInBeaconDiscoveryScript());
}
```

## 完整的发现脚本

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

### 进游戏核对

执行 `gradle jar`，重启游戏，**新建一份存档**。

- 星图上，在 Ashen Verge 中能找到灰烬信标，名称与归属颜色与配置一致；
- 远离信标时，在控制台执行 `runcode Global.getSector().getEntityById("GearIn_ashen_beacon").getMemoryWithoutUpdate().getBoolean("$GearIn_entity_ready")`，结果为 `false`；
- 靠近到传感器能够捕捉到它之后再执行同一条语句，结果为 `true`；
- 保存并重新读档，再执行一次，结果仍为 `true`。

## 常见问题

- **按 ID 查找信标得到 `null`**：实体的创建发生在旧存档，或者查询用的 ID 与创建时不同。
    - **解决方案**：使用新档测试，并统一引用 `BEACON_ID`。

- **信标一直没有被发现**：脚本没有登记，或者传感器轮廓设得过小。
    - **解决方案**：核对 `onGameLoad(...)` 中的登记，再逐步增大 `setSensorProfile(...)` 的值。

- **玩家尚未靠近就已经发现**：把 `getVisibilityLevelOfPlayerFleet()` 与 `getVisibilityLevelToPlayerFleet()` 用反了。
    - **解决方案**：判断玩家是否看见实体，使用带 `ToPlayerFleet` 的方法。

- **交互界面没有插图**：`setInteractionImage(...)` 的键没有登记。
    - **解决方案**：使用原版已登记的键，或在自己的 `settings.json` 的 `graphics.illustrations` 中登记。

## 建议参考项

- `com.fs.starfarer.api.campaign.SectorEntityToken`：可见性、传感器、交互图与 Memory 的方法，`VisibilityLevel` 的枚举也在其中。
- `com.fs.starfarer.api.impl.campaign.NavBuoyEntityPlugin`：原版导航浮标的航速加成。
- `com.fs.starfarer.api.impl.campaign.BaseCampaignObjectivePlugin`：黑入与重置状态的保存方式。
- `data/config/custom_entities.json`：`nav_buoy` 与其他实体类型的定义。
