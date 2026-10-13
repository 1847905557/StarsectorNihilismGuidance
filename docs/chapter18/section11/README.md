# 使用 MagicLib 辅助生涯开发

GearIn 星系已经生成，可以再放一艘工匠级残骸，给玩家一个实际探索目标。残骸以第一颗行星为轨道焦点，超空间清理则以整个星系为范围；把两项 MagicCampaign 操作接在星系生成之后，新档里就能直接观察发现、回收与云层变化。

*本节预期阅读时间：\~20 分钟。*

*本节预期操作时间：\~25 分钟。*

## 学习目标

- 使用 `MagicCampaign.createDerelict(...)` 创建可发现、可回收的舰船残骸；
- 使用 `MagicCampaign.hyperspaceCleanup(...)` 清理星系周围的超空间地形；
- 在新档生成顺序中接入生涯布置器；
- 使用 Sector Memory 保证同一存档只完成一次布置；
- 在调用依赖库前检查星系、锚点和装配 ID。

## 前置知识

- [C3-S4] 的 `GearInSystemGenerator`、`GearIn` 星系和 `GearIn_planet1`；
- [C4-S1] 定义的 `GearIn_artizan_combat` 装配；
- [C16-S5] 的 Sector Memory 读写；
- [C18-S1] 的 MagicLib 硬依赖与类加载边界。

## 将布置器接在星系生成之后

先保存本节完整类到 `src/data/scripts/gearin/campaign/GearInCampaignSetup.java`。在 `GearInModPlugin.onNewGame()` 中沿用星系生成，再调用布置器；势力关系初始化保留。**对象先生成，使用这些对象的布置随后执行。**

```text
GearInModPlugin.onNewGame()
    -> GearInSystemGenerator.generate(sector)
    -> GearInCampaignSetup.setup(sector)
    -> seedRelations(sector)
```

`setup(...)` 先取得 `GearIn` 星系，再取得 `GearIn_planet1`。残骸以行星为轨道焦点；超空间清理则接收整个 `StarSystemAPI`。同一个星系对象由此流向两个用途，但每个 API 的输入边界保持清楚。

## 新档入口的局部调用

在已有 `GearInModPlugin.onNewGame()` 中，先生成星系，再执行生涯布置，最后初始化势力关系：

```java
@Override
public void onNewGame() {
    SectorAPI sector = Global.getSector();
    new GearInSystemGenerator().generate(sector);
    new GearInCampaignSetup().setup(sector);
    seedRelations(sector);
}
```

这个顺序保证 `setup(...)` 运行时，`GearIn` 和 `GearIn_planet1` 已经加入 Sector。代码只从 `onNewGame()` 接入，因此成果属于新档生成阶段。

## 检查生涯对象与装配

`createDerelict(...)` 接收运行时星系实体和装配 ID。先检查三个前置对象，可以把缺失资源转化为带具体 ID 的日志，而不是让后续构造在更深的位置失败。

```java
StarSystemAPI system = sector.getStarSystem("GearIn");
SectorEntityToken orbitCenter = sector.getEntityById("GearIn_planet1");
boolean variantExists = Global.getSettings()
        .doesVariantExist("GearIn_artizan_combat");
```

锚点还要属于目标星系。`sector.getEntityById(...)` 是全局查询；比较 `orbitCenter.getStarSystem()` 与 `system`，可以防止同名或错误来源的实体把残骸送到另一处地点。

前置对象缺失时，本例记录错误并结束本次布置。日志分别包含 `GearIn`、`GearIn_planet1` 或 `GearIn_artizan_combat`，因此可以直接回到星系生成、实体 ID 或装配注册链排查。

## 配置可回收残骸

本节使用 `createDerelict(...)` 的十参数重载。前九个参数建立残骸与回收交互，最后一个参数决定舰船本身是否允许回收。

```java
MagicCampaign.createDerelict(
        "GearIn_artizan_combat",
        ShipCondition.BATTERED,
        true,
        null,
        true,
        orbitCenter,
        90f,
        orbitCenter.getRadius() + 50f,
        100f,
        true);
```

### 残骸参数速查表

参数按下面的职责解释：

| 参数 | 本例值 | 作用 |
| --- | --- | --- |
| `variantId` | `GearIn_artizan_combat` | 指定残骸使用的舰船装配 |
| `condition` | `BATTERED` | 让回收结果带有明显损伤 |
| `discoverable` | `true` | 让残骸进入传感器发现流程 |
| `discoveryXp` | `null` | 使用默认发现经验 |
| `recoverable` | `true` | 为实体附加舰船回收交互 |
| `orbitCenter` | `GearIn_planet1` | 指定圆形轨道焦点 |
| `orbitStartAngle` | `90f` | 从行星上方的轨道角开始 |
| `orbitRadius` | 行星半径加 `50 su` | 将残骸放在近地轨道 |
| `orbitDays` | `100f` 天 | 指定一圈轨道周期 |
| `isRecoverable` | `true` | 允许玩家实际回收舰船 |

两个回收布尔值控制不同层级。第一个 `recoverable` 决定是否创建回收特殊交互，最后的 `isRecoverable` 决定实体是否带有不可回收标签。本例把两者都设为 `true`，交互入口与最终结果因此一致。

`ShipCondition.BATTERED` 控制舰船状况。它会影响 D-mod 数量和残留武器状况；具体结果仍由游戏的舰船回收流程生成，而不是由本节手工填写。

## 清理星系周围的超空间

`hyperspaceCleanup(...)` 接收整个 `StarSystemAPI`。MagicLib 根据该星系在超空间中的位置与最大半径清理周围云层，为跳跃点和星系边界留出通行区域。

```java
MagicCampaign.hyperspaceCleanup(system);
```

这个调用应放在星系的位置、跳跃点和边界已经生成之后。本例由 `GearInSystemGenerator.generate(...)` 先完成星系构造，再由 `GearInCampaignSetup.setup(...)` 清理超空间。

清理结果作用于超空间地形，观察位置是星系外侧的超空间地图。把行星或残骸实体传给这个方法不符合其输入类型；这里的所有权对象始终是 `GearIn` 星系。

## 使用 Memory 保证一次布置

新档入口正常只执行一次，但布置方法仍应对重复调用保持稳定。本例在 Sector Memory 使用 `$GearIn_campaign_setup_done`：值为 `true` 时直接返回，值不存在时继续检查前置对象。

```java
MemoryAPI sectorMemory = sector.getMemoryWithoutUpdate();
if (sectorMemory.getBoolean("$GearIn_campaign_setup_done")) {
    return;
}
```

成功标记写在残骸创建和超空间清理之后：

```java
MagicCampaign.createDerelict(/* 本节的残骸参数 */);
MagicCampaign.hyperspaceCleanup(system);
sectorMemory.set("$GearIn_campaign_setup_done", true);
```

前置检查失败或 MagicLib 调用抛出异常时，成功标记保持未写入。残骸已创建而清理失败时，处理分支移除本次残骸并记录异常，下次调用重新创建并清理。地形操作可能已执行一部分，这份处理只明确移除残骸；再次清理继续按当前星系范围执行。

## 常用方法速查表

| API | 返回值或结果 | 本节用途 |
| --- | --- | --- |
| `SectorAPI.getStarSystem(String)` | `StarSystemAPI` 或 `null` | 按名称取得 GearIn 星系 |
| `SectorAPI.getEntityById(String)` | `SectorEntityToken` 或 `null` | 取得残骸轨道焦点 |
| `SettingsAPI.doesVariantExist(String)` | `boolean` | 在创建前检查装配 ID |
| `MagicCampaign.createDerelict(...)` | `SectorEntityToken` | 创建带轨道与回收参数的残骸 |
| `MagicCampaign.hyperspaceCleanup(StarSystemAPI)` | 无返回值 | 清理星系周围的超空间云层 |
| `MemoryAPI.getBoolean(String)` | `boolean` | 读取布置完成状态 |
| `MemoryAPI.set(String, Object)` | 无返回值 | 保存布置完成状态 |

## 完整最小例

完整布置器先读完成标记，再取得星系、锚点与装配，随后创建残骸、清理云层并写入成功状态：

::: details 完整成品：GearInCampaignSetup

```java
package data.scripts.gearin.campaign;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.SectorAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.StarSystemAPI;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.impl.campaign.rulecmd.salvage.special.ShipRecoverySpecial.ShipCondition;
import org.apache.log4j.Logger;
import org.magiclib.util.MagicCampaign;

public final class GearInCampaignSetup {

    private static final Logger LOGGER = Global.getLogger(GearInCampaignSetup.class);

    private static final String SYSTEM_ID = "GearIn";
    private static final String ORBIT_CENTER_ID = "GearIn_planet1";
    private static final String VARIANT_ID = "GearIn_artizan_combat";
    private static final String SETUP_DONE_KEY = "$GearIn_campaign_setup_done";

    private static final float ORBIT_START_ANGLE = 90f;
    private static final float ORBIT_ALTITUDE = 50f;
    private static final float ORBIT_DAYS = 100f;

    public void setup(SectorAPI sector) {
        MemoryAPI sectorMemory = sector.getMemoryWithoutUpdate();
        if (sectorMemory.getBoolean(SETUP_DONE_KEY)) {
            return;
        }

        StarSystemAPI system = sector.getStarSystem(SYSTEM_ID);
        if (system == null) {
            LOGGER.error("GearIn campaign setup skipped: missing star system " + SYSTEM_ID);
            return;
        }

        SectorEntityToken orbitCenter = sector.getEntityById(ORBIT_CENTER_ID);
        if (orbitCenter == null || orbitCenter.getStarSystem() != system) {
            LOGGER.error("GearIn campaign setup skipped: missing orbit center " + ORBIT_CENTER_ID);
            return;
        }

        if (!Global.getSettings().doesVariantExist(VARIANT_ID)) {
            LOGGER.error("GearIn campaign setup skipped: missing ship variant " + VARIANT_ID);
            return;
        }

        SectorEntityToken derelict = null;
        try {
            derelict = MagicCampaign.createDerelict(
                    VARIANT_ID,
                    ShipCondition.BATTERED,
                    true,
                    null,
                    true,
                    orbitCenter,
                    ORBIT_START_ANGLE,
                    orbitCenter.getRadius() + ORBIT_ALTITUDE,
                    ORBIT_DAYS,
                    true);
            MagicCampaign.hyperspaceCleanup(system);
            sectorMemory.set(SETUP_DONE_KEY, true);
        } catch (RuntimeException | LinkageError exception) {
            if (derelict != null) {
                system.removeEntity(derelict);
            }
            LOGGER.error("GearIn campaign setup failed while calling MagicLib", exception);
        }
    }
}
```

:::

## 验证方案

前置数据：使用包含 `GearIn`、`GearIn_planet1` 与 `GearIn_artizan_combat` 的开发环境，并在 `onNewGame()` 中按本节顺序接入 `GearInCampaignSetup`。

1. 启动游戏并新建生涯，进入 GearIn 星系。
2. 在 `GearIn_planet1` 周围开启传感器观测，接近并发现舰船残骸。
3. 在残骸尚未回收时再次调用 `new GearInCampaignSetup().setup(Global.getSector())`，确认数量仍为 1。
4. 打开残骸交互，进入舰船回收界面并回收，检查船体、装配来源、舰况与 D-mod。
5. 返回星图，从 GearIn 星系跳入超空间，观察边界周围的云层。

预期结果：

- 新档中只生成 `1` 艘 `GearIn_artizan_combat` 残骸；
- 残骸围绕 `GearIn_planet1` 运行，轨道高度为行星半径外 `50 su`，周期为 `100 天`；
- 残骸在传感器发现后显示，并能进入舰船回收界面；
- 回收操作可以完成，舰船状况对应 `BATTERED`；
- GearIn 星系边界周围形成可通行的超空间清理区域；
- 第二次调用后残骸总数仍为 `1`，Sector Memory 中 `$GearIn_campaign_setup_done` 为 `true`。

## 故障分析

- **日志出现 `missing star system GearIn`**：生涯布置器运行时尚未生成 GearIn 星系。
    - **解决方案**：确认 `GearInSystemGenerator.generate(sector)` 位于 `GearInCampaignSetup.setup(sector)` 之前。

- **日志出现 `missing orbit center GearIn_planet1`**：行星 ID 不存在，或取得的实体不属于 GearIn 星系。
    - **解决方案**：核对 `addPlanet(...)` 的实体 ID，并确认生成器把行星加入同一个星系。

- **日志出现 `missing ship variant GearIn_artizan_combat`**：装配文件未注册为该 ID。
    - **解决方案**：回到 [C4-S1]，核对文件名、`variantId` 与装配加载日志。

- **日志出现 `failed while calling MagicLib`**：MagicLib 调用阶段发生运行时异常或链接错误。
    - **解决方案**：核对 `mod_info.json` 的 MagicLib 硬依赖、实际安装版本和异常堆栈首个业务帧；本次已创建的残骸会在记录日志前移除。

- **残骸可以发现但没有回收入口**：`recoverable` 没有设为 `true`。
    - **解决方案**：核对十参数重载中的第五个参数。

- **回收界面出现但舰船不可回收**：`isRecoverable` 被设为 `false`，实体带有不可回收标签。
    - **解决方案**：核对十参数重载的最后一个参数。

- **重复调用后出现多艘残骸**：成功标记没有写入 Sector Memory，或读取了不同对象的 Memory。
    - **解决方案**：统一从 `sector.getMemoryWithoutUpdate()` 读写 `$GearIn_campaign_setup_done`，并把写入放在两项 MagicLib 操作之后。

## 总结

`MagicCampaign.createDerelict(...)` 把装配、舰况、发现、回收和轨道参数组织成一个生涯实体创建入口；`hyperspaceCleanup(...)` 则以星系为边界整理对应的超空间地形。将两者放在星系生成之后，可以让对象依赖和观察位置保持一致。

Sector Memory 保存这次生涯的完成标记，重复调用可以据此结束。编者建议先观察未回收时的残骸数量，再完成回收；把首次生成、重复调用和玩家取走舰船分别看清，才能知道数量变化发生在哪一步。

## 建议参考项

- `org.magiclib.util.MagicCampaign`：残骸创建与超空间清理；
- `com.fs.starfarer.api.impl.campaign.rulecmd.salvage.special.ShipRecoverySpecial.ShipCondition`：残骸舰况枚举；
- `com.fs.starfarer.api.campaign.StarSystemAPI`：星系对象与超空间位置边界；
- `com.fs.starfarer.api.campaign.SectorEntityToken`：残骸和轨道焦点；
- `com.fs.starfarer.api.campaign.rules.MemoryAPI`：生涯布置完成状态。
