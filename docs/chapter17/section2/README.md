# 让 Nexerelin 识别 GearIn 势力与外交立场

Nexerelin 只有读到 GearIn 的势力配置，才会把它纳入开局、外交和联盟计算。本节登记 faction JSON，设置外交倾向与关系边界，再用 Java 探针确认读取的确是 GearIn 配置。

*本节预期阅读时间：~18 分钟。*

*本节预期操作时间：~30 分钟。*

## 学习目标

完成后，您将能够：

- 用 `mod_factions.csv` 把自定义势力交给 Nexerelin 配置加载器；
- 配置初始关系、关系边界、外交特性和联盟倾向；
- 用运行期探针区分 "读到自定义配置" 和 "静默回退默认配置"。

## 前置知识

- [C6-S2] 理解 Java 类、方法、字段与入口插件；
- [C7-S1] 理解每帧回调、暂停判断与运行时对象；
- 能够用 Gradle 构建示例 MOD，并从 `starsector.log` 定位类名。

## 接入 Nexerelin 势力配置

GearIn 已经是 Starsector 势力，但 Nexerelin 还不知道它能否成为开局势力、是否参加随机外交，以及它在联盟轴线上的位置。接入点是一行势力 ID；Nexerelin 随后按这个 ID 读取同名 JSON，并把配置交给外交、联盟和战略系统共享。

## 文件与 API 链路

`mod_factions.csv` 登记势力 ID；`NexConfig` 从 `data/config/exerelinFactionConfig/<factionId>.json` 合并配置；`NexFactionConfig` 保存开局资格、外交规则和联盟倾向；`DiplomacyManager` 应用关系变化；`AllianceManager` 查询已经成立的联盟。

### 登记 GearIn 势力配置

**插入位置：`data/config/exerelinFactionConfig/mod_factions.csv`。** 保留 Nexerelin 的单列表头，再登记 GearIn 势力 ID。

```csv
faction
gear_industry
```

这个 CSV 文件不接受配置路径列。加载器会根据这一行自动寻找 `data/config/exerelinFactionConfig/gear_industry.json`。

完成信号：`NexConfig.getFactionConfig("gear_industry")` 返回的配置对象，其 `factionId` 为 `gear_industry`。

### 声明可玩性与外交开关

**插入位置：`gear_industry.json` 根对象。** 允许该势力出现在 Nexerelin 开局列表，并参加随机外交。

```json
"playableFaction": true,
"startingFaction": true,
"disableDiplomacy": false
```

`playableFaction` 控制玩家能否选择该势力；`startingFaction` 控制它是否参与开局生成；`disableDiplomacy` 决定外交管理器能否改变其关系。

完成信号：新档势力列表出现 GearIn，进入生涯后该势力会收到外交事件。

### 写清偏好与关系边界

**插入位置：`gear_industry.json` 外交字段。** 先给两个关键势力设置初始关系，再限制随机外交能够触及的边界。

```json
"startRelationships": {
  "independent": 0.25,
  "pirates": -0.6
},
"minRelationships": {
  "independent": 0.0
},
"maxRelationships": {
  "pirates": -0.25
}
```

`startRelationships` 决定 Nexerelin 初始化后的关系值；`minRelationships` 和 `maxRelationships` 是后续随机外交的硬边界。旧配置中的 `factionsLiked` 与 `factionsDisliked` 仍能被解析，但已被 Nexerelin 标记为弃用，新配置应直接写明关系值。

完成信号：独立势力关系不会降到敌对，海盗关系不会改善到高于 `-0.25`。

### 配置外交特性与联盟倾向

**插入位置：`gear_industry.json` 外交字段之后。** 外交特性影响事件选择，alignment 则参与联盟兼容性计算。

```json
"diplomacyTraits": [
  "helps_allies",
  "law_and_order"
],
"alignments": {
  "technocratic": 0.75,
  "diplomatic": 0.4,
  "hierarchical": 0.25
}
```

完成信号：探针能够读出三项 alignment；联盟系统据此计算 GearIn 与其他势力的长期兼容性。

### 从运行期确认配置和联盟

**插入位置：`GearInNexDiplomacyProbe.describe()`。** 同时输出配置身份、可玩性、外交开关、联盟倾向和当前联盟状态。

```java
NexFactionConfig config = NexConfig.getFactionConfig(factionId);
boolean allied = AllianceManager.areFactionsAllied(factionId, "hegemony");
```

完整探针把 `config.factionId` 和 `config.getBaseAlignments()` 一并写入结果。若 CSV 漏行，`NexConfig` 会静默回退默认配置；这两个值比单看 "没有异常" 更能证明配置确实命中。

完成信号：日志同时出现 `factionId=gear_industry`、`playable=true`、`diplomacy=true` 和三项 alignment。

## 排查关键故障

下面是**局部故障片段**：

```csv
faction
Gear_Industry
```

实际症状：`gear_industry.json` 文件存在且内容可解析，但新档列表没有 GearIn；探针也没有读到预期 alignment。

原因：CSV 行就是配置身份。加载器会按 `Gear_Industry` 构造配置 ID，并寻找同名 JSON；它不会猜测该行实际指向小写的 `gear_industry.json`。

下面是**局部修正片段**：

```csv
faction
gear_industry
```

复验：重新启动游戏以刷新合并配置，新建游戏并在势力列表选择 GearIn，再确认探针输出 `factionId=gear_industry` 和配置中的 alignment。

## 完整最小例

局部片段说明了行为如何逐步增加；以下是可直接放入示例 MOD 的完整角色类与资源。

::: details 完整成品：GearInNexCompat

```java
package data.scripts.gearin.modcompat;

import com.fs.starfarer.api.Global;
import exerelin.campaign.SectorManager;
import exerelin.utilities.NexConfig;
import exerelin.world.ExerelinCorvusLocations;

/**
 * Nexerelin 软依赖适配器：把对 exerelin.* 的直接引用全部收敛在本类，
 * 工程里只有本类与特遣队命名器 (GearInSpecialForcesNamer，仅被 Nexerelin
 * 按配置类名加载) 出现 exerelin.* 类名。
 *
 * 调用链 (软依赖组合判据)：
 *   isModEnabled 判定 MOD 启用状态 -> SectorManager.getManager() 判管理器
 *   是否已创建 (null 守卫)-> isCorvusMode() 判当前模式。
 * NexConfig.getFactionConfig 读取本 MOD 势力配置 (经 mod_factions.csv 注册)；
 * ExerelinCorvusLocations 读取本 MOD 的出生点行 (corvus_spawnpoints.csv)。
 * Nexerelin 未启用时全部回落降级值，不抛异常。
 */
public class GearInNexCompat {

    /** Nexerelin 的 MOD id，与 Starsector/mods/Nexerelin/mod_info.json 的 id 字段逐字一致。 */
    public static final String NEX_MOD_ID = "nexerelin";

    /** 本 MOD 势力 id(教程势力章节注册)。 */
    public static final String GEAR_FACTION_ID = "gear_industry";

    private GearInNexCompat() {
    }

    /** 判据一：Nexerelin 是否在启用列表 (启动器 MOD 管理器按 id 逐字比较，大小写敏感)。 */
    public static boolean isNexEnabled() {
        return Global.getSettings().getModManager().isModEnabled(NEX_MOD_ID);
    }

    /**
     * 组合判据：Nexerelin 已启用、SectorManager 已创建且处于 Corvus 模式。
     * SectorManager.getManager() 在 manager 尚未创建时返回 null(sector
     * persistent data 中无条目) —— 直接调用 isCorvusMode() 会 NPE；
     * manager 由 Nexerelin 的入口生成器创建，早于它的调用点必须靠这里守卫。
     */
    public static boolean isNexCorvusMode() {
        if (!isNexEnabled()) {
            return false;
        }
        SectorManager manager = SectorManager.getManager();
        return manager != null && manager.isCorvusMode();
    }

    /**
     * 读取本 MOD 势力配置的 marketSpawnWeight(随机星域模式的市场份额倍率)。
     * NexConfig.getFactionConfig 未注册时回退默认配置 (neutral)，不抛异常 ——
     * "读到的不是自己的配置" 是该 API 的静默失败模式 (mod_factions.csv 漏写时
     * playableFaction 读回 false)。
     */
    public static float getFactionMarketSpawnWeight(String factionId) {
        if (!isNexEnabled()) {
            return 1f;
        }
        return NexConfig.getFactionConfig(factionId).marketSpawnWeight;
    }

    /**
     * 读取本 MOD 势力在 corvus_spawnpoints.csv 里的生成点实体 id。
     * 漏写行时返回 null —— 玩家出生点回退 homeworld / 随机位置，无报错。
     */
    public static String getFactionSpawnPointEntityId(String factionId) {
        if (!isNexEnabled()) {
            return null;
        }
        ExerelinCorvusLocations.SpawnPointEntry entry = ExerelinCorvusLocations.getFactionSpawnPoint(factionId);
        return entry == null ? null : entry.entityId;
    }
}
```

:::

::: details 完整成品：GearInNexDiplomacyProbe

```java
package data.scripts.gearin.modcompat;

import exerelin.campaign.AllianceManager;
import exerelin.utilities.NexConfig;
import exerelin.utilities.NexFactionConfig;

public final class GearInNexDiplomacyProbe {
    public String describe(String factionId) {
        NexFactionConfig config = NexConfig.getFactionConfig(factionId);
        return "factionId=" + config.factionId
                + ", playable=" + config.playableFaction
                + ", diplomacy=" + !config.disableDiplomacy
                + ", alignments=" + config.getBaseAlignments()
                + ", alliedWithHegemony="
                + AllianceManager.areFactionsAllied(factionId, "hegemony");
    }
}
```

:::

## 验证方案

1. 构建示例 MOD，确认两个探针类能够针对当前安装的 `ExerelinCore.jar` 编译。
2. 启动新档，确认 GearIn 出现在势力选择列表，并记录探针输出。
3. 触发至少一次外交关系变化，核对独立势力与海盗关系没有越过配置边界。
4. 查看联盟界面，确认实际联盟状态与探针的 `alliedWithHegemony` 输出一致。

预期结果：

- `NexConfig.getFactionConfig` 返回 `factionId=gear_industry`；
- 新档列表出现 GearIn，外交事件可以选择它；
- 独立势力与海盗关系始终处于各自边界内；
- 日志出现 `playable=true`、`diplomacy=true` 和三项 alignment；
- 当前联盟查询与联盟界面显示一致。

## 故障分析

- **JSON 可解析但新档列表没有 GearIn**：`mod_factions.csv` 中的势力 ID 与 Starsector ID 或 JSON 文件名不一致。

    - **解决方案**：逐字统一三处名称并重启游戏。

- **读取到 `neutral` 默认配置**：CSV 缺少注册行，或大小写与配置文件名不一致。

    - **解决方案**：读取 `config.factionId` 确认实际命中的身份，修正后重新创建新档。

- **外交关系始终不变化**：`disableDiplomacy` 关闭了外交，或 `noRandomizeRelations` 阻止了开局随机化。

    - **解决方案**：按目标行为调整对应字段。

- **联盟始终被拒绝**：关系满足但 alignment 不兼容，或其他准入条件未通过。

    - **解决方案**：先用 `getBaseAlignments()` 确认配置，再逐项检查联盟条件。

## 总结

Nexerelin 势力接入从唯一势力 ID 开始，CSV 行、JSON 文件名和 Java 查询必须一致。初始关系确定开局状态，关系边界约束后续变化，外交特性影响事件选择，alignment 则参与联盟判断。运行期探针应同时证明配置身份和关键字段，避免把静默回退误判为接入成功。

## 建议参考项

- `NexConfig.getFactionConfig`：读取合并后的势力配置；
- `NexFactionConfig`：势力外交与战略字段；
- `AllianceManager.areFactionsAllied`：查询当前联盟关系。
