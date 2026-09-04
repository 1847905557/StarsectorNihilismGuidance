# 用 LazyLib 评估下一段航路的风险

生涯自动导航若只看直线距离，会把补给不足的舰队送进敌方巡逻群。本节在每个航路点周围统计敌对舰队，以舰队点数和距离生成风险，并把最近一次评估写进存档。

*本节预期阅读时间：~18 分钟。*

*本节预期操作时间：~30 分钟。*

## 学习目标

完成后，您将能够：

- 用 `CampaignUtils` 按当前位置和关系查询附近敌对舰队；
- 用舰队点数与距离构造一项可调的航路风险指标；
- 在基础风险之后叠加玩家货舱的补给数量修正；
- 只持久化最终数值，不持久化当前场景中的舰队引用；
- 从位置、关系和持久键定位风险值异常。

## 前置知识

- [C7-S1] 理解 Java 类、方法、字段与入口插件；
- [C8-S1] 理解每帧回调、暂停判断与运行时对象；
- 能够用 Gradle 构建示例 MOD，并从 `starsector.log` 定位类名。

## 明确当前需求

玩家在星系内选择下一个补给站。目标附近有两支敌对舰队时，界面应显示较高风险；它们离开扫描半径后，风险下降并在读档后保持最近一次值。

## 文件与 API 链路

### 航路风险 API 职责速查表

| 对象 | 职责 |
|---|---|
| `CampaignUtils` | 敌对舰队查询与关系判断 |
| `MathUtils` | 计算距离 |
| `CargoAPI` | 读取玩家货舱补给 |
| 界面入口 | 输出风险等级与数值消息 |
| `SectorAPI.getPersistentData()` | 持久记录最近一次评估 |

### 扫描航路点附近敌对舰队

**插入位置：`GearInRouteRiskAssessor.assess()` 开头。** 按航路点和半径取得当前敌对舰队。

```java
for (CampaignFleetAPI fleet : CampaignUtils.getNearbyHostileFleets(waypoint, radius)) {
    // 只处理敌对舰队
}
```

完成信号：中立与友方舰队不会进入风险计算。

### 让大型近敌贡献更高风险

**插入位置：`assess()` 的循环体。** 使用舰队点数乘以距离衰减。这是 GearIn 自己的评分公式，数值只在同一套半径与阈值下比较。

```java
float distance = Math.max(500f, MathUtils.getDistance(waypoint, fleet));
risk += fleet.getFleetPoints() * radius / distance;
```

完成信号：同距离下大型舰队风险更高，同规模下近敌更高。

### 加入补给数量修正

**插入位置：航路规划入口取得基础风险后。** 货舱补给少于 30 单位时放大同一段航路的风险；这里没有计算每日消耗或可航行天数。

```java
float supplies = Global.getSector().getPlayerFleet().getCargo().getSupplies();
if (supplies < 30f) risk *= 1.5f;
```

完成信号：相同敌舰布置下，补给数量跨过 30 单位门槛时风险乘数发生变化。

### 保存最近一次评估

**插入位置：`GearInRouteRiskRecord.save()`。** 把 `float` 写入星区持久数据。

```java
Global.getSector().getPersistentData().put("$GearIn_routeRisk", risk);
```

完成信号：保存并读档后仍能读到相同数值。

### 把结论交给消息层

**插入位置：航路 UI 或 Intel 更新处。** 输出等级和数值，保留判断依据。

```java
String level = risk >= 120f ? "HIGH" : risk >= 50f ? "MEDIUM" : "LOW";
Global.getSector().getCampaignUI().addMessage("Route risk: " + level + " (" + (int) risk + ")");
```

完成信号：玩家能同时看到 LOW/MEDIUM/HIGH 与具体风险值。

## 排查关键故障

下面是**局部故障片段**：

```java
private final List<CampaignFleetAPI> threats = CampaignUtils.getNearbyHostileFleets(point, 5000f);
```

实际症状：敌舰已经离开星系，风险仍保持高值，读档后还可能引用失效舰队。

原因：查询结果属于当前生涯状态，跨帧缓存实体列表会过期。

下面是**局部修正片段**：

```java
List<CampaignFleetAPI> threats = CampaignUtils.getNearbyHostileFleets(point, 5000f);
```

复验：先记录敌舰在半径内的风险，再让它驶出范围并重新调用 `assess()`；第二次结果应下降，持久数据只在再次调用 `save()` 后更新。

## 完整最小例

评估器读取当前场景并返回数值，记录器只保存和恢复该数值。货舱乘数与消息文本属于调用层，因此不会把玩家舰队依赖塞进评估器。

::: details 完整成品：GearInRouteRiskAssessor

```java
package data.scripts.gearin.lib;

import org.lazywizard.lazylib.MathUtils;
import org.lazywizard.lazylib.campaign.CampaignUtils;

import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;

public final class GearInRouteRiskAssessor {
    public float assess(SectorEntityToken waypoint, float radius) {
        float risk = 0f;
        for (CampaignFleetAPI fleet : CampaignUtils.getNearbyHostileFleets(waypoint, radius)) {
            float distance = Math.max(500f, MathUtils.getDistance(waypoint, fleet));
            risk += fleet.getFleetPoints() * radius / distance;
        }
        return risk;
    }
}
```

:::

::: details 完整成品：GearInRouteRiskRecord

```java
package data.scripts.gearin.lib;

import java.util.Map;

import com.fs.starfarer.api.Global;

public final class GearInRouteRiskRecord {
    private static final String KEY = "$GearIn_routeRisk";

    public void save(float risk) {
        Global.getSector().getPersistentData().put(KEY, risk);
    }

    public float load() {
        Map<String, Object> data = Global.getSector().getPersistentData();
        Object value = data.get(KEY);
        return value instanceof Number ? ((Number) value).floatValue() : 0f;
    }
}
```

:::

## 验证方案

前置场景：在同一星系内建立一个航路点，并准备一支可调整舰队点数、距离和势力关系的测试舰队。

1. 在扫描半径外、半径边缘和 500 su 内分别放置同一支敌对舰队，记录三次基础风险。
2. 保持距离不变，改变测试舰队的舰队点数，确认风险按比例变化。
3. 把测试舰队关系改为中立并重新评估，确认它不再贡献风险。
4. 分别把玩家补给设为 29 与 30 单位，比较调用层应用乘数后的结果。
5. 保存风险，存档并读档，调用 `load()` 核对数值；随后让敌舰离开范围并重新评估、保存。

预期结果：

- 中立与友方舰队不贡献风险，同关系下舰队点数越高、距离越近，基础风险越高；
- 29 单位补给触发 1.5 倍修正，30 单位不触发；
- 消息同时显示等级与取整后的风险值，等级在 50 与 120 两个阈值处切换；
- 读档后 `load()` 返回最近一次保存的数值；重新评估不会自动改写持久数据；
- 敌舰离开半径并重新评估、保存后，读到的风险随之下降。

## 故障分析

- **敌舰离开后风险仍保持高值**：代码跨帧缓存了已经过期的舰队列表。

    - **解决方案**：每次更新时重新查询，只持久化最终数值。

- **风险始终为零**：航路点与目标舰队不在同一个 `LocationAPI`。

    - **解决方案**：传入当前 location 内的 waypoint。

- **友军被计入风险**：代码使用固定阵营名单，没有读取当前势力关系。

    - **解决方案**：改由 `CampaignUtils` 按实时敌对关系筛选。

- **读档后风险恢复为零**：结果写入了 local memory，没有进入 sector persistent data。

    - **解决方案**：改用独立持久键。

## 总结

航路风险是一次当前状态计算，只有最终数值需要持久化。敌对舰队列表每次重新查询，货舱修正放在基础风险之后，消息层只负责展示结论。

## 建议参考项

- `CampaignUtils.getNearbyHostileFleets`：按关系筛选附近舰队；
- `CampaignFleetAPI.getFleetPoints`：提供舰队规模指标；
- `SectorAPI.getPersistentData`：保存跨读档的最近风险。
