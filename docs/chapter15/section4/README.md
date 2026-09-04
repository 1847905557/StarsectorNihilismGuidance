# 使用 Memory 系统保存状态

本节使用 Rules 内存保存对话与任务状态，并观察变量的写入、过期和持久化。内存键以 `$` 开头，所用槽位决定状态属于哪个游戏对象。

*本节预期阅读时间：~30 分钟。*

*本节预期操作时间：~60 分钟。*

## 学习目标

完成后，您将能够：

- 解释 `MemoryAPI` 的公开面：`set`(两参/三参)/ `get` 族 / `contains` / `unset` / `expire` / `is` / `between` / `getExpire` / `getKeys` / required 族 / `clear`，并说明记忆键必须以 `$` 开头及其违反后果；
- 区分过期语义：`expire` 以游戏天为单位 (10 真实秒 = 1 游戏天)、只在生涯非暂停时推进、0 天表示下一未暂停推进帧移除、负值取消过期、两参 `set` 清除未决过期、`getExpire` 无过期返回 -1；
- 解析 `memoryMap` 结构：`MemKeys` 槽位 (`global` / `player` / `local` / `market` / `sourceMarket` / `faction` / `entity` / `personFaction` / `mission`) 与 `$memkey.name` 变量解析规则 (无点号或槽位缺失回落 `local`)；
- 区分 `getMemory()` 与 `getMemoryWithoutUpdate()`：前者触发全部插件 `update*Facts`，后者只读写，并选择写键与高频读取的正确入口；
- 解释保存与恢复：记忆随所属对象序列化进存档，实体/市场引用转 `enRef_<id>` / `mRef_<id>`，解析不到时键被删除；`PersistentData` 是随存档保存的全局 `Map<String, Object>`，键无需 `$`；
- 验证：把六个示例类与 5 条规则行接入既有 MOD，通过日志、对话与读档结果核对记忆写入、过期与持久化流程。

## 前置知识

- [C7-S1] 的 `modPlugin`、`onGameLoad` 与持久数据注册；
- [C10-S1] 的 `addScript` 脚本注册；
- [C10-S6] 的对话框生命周期、`getInteractionTarget()` 与 `MemKeys.LOCAL`；
- [C15-S1] 的 `$memkey.name` 变量解析、判真规则与赋值过期天数；
- [C15-S2] 的 flag 模式、状态记忆、场景事实键与 `$player.` / `$global.` 槽位；
- [C15-S3] 的 rulecmd `execute(...)` 与 `BaseCommandPlugin.getEntityMemory(...)`。

## 术语

按术语约定，本节首次完整讲解以下词；API 类名保留源码大小写：

- **Rules 内存 (memory)**：API 名保留 `MemoryAPI`。规则系统、交互对象、实体、人物、舰队、市场和势力共用的键值状态容器；`memory` 是运行时对象 —— 没有数据文件、没有注册表，靠 `getMemory()` / `getMemoryWithoutUpdate()` 取到实例，随所属对象序列化进存档。
- **记忆键 (memory key)**：`MemoryAPI` 里的键名，必须以 `$` 开头 (如 `$GearIn_memory_tag`、原版的 `$option`、`$menuState`、`$hailing`)。
- **memoryMap**：`Map<String, MemoryAPI>`，槽位名由 `MemKeys` 常量定义 (`global` / `player` / `local` 等)；规则匹配、文本替换与 rulecmd 执行共享同一个 memoryMap(`RulesAPI.getAllMatching` / `getBestMatching` 第四参)。
- **PersistentData**：`Global.getSector().getPersistentData()` 返回的 `Map<String, Object>`，随存档保存与恢复；键**不需要** `$` 前缀，与记忆键的命名约束不同。

## 记忆层：从公开面到持久化

本节先建立 `MemoryAPI` 公开面与两条取值入口的完整认识，再依次理解过期、required 依赖、memoryMap 槽位、facts 刷新差异与推进者，最后落到存档序列化与 `PersistentData`。每步都用前置章节的既有用法对照，让规则侧语法在内存层找到落点。

### MemoryAPI 公开面速查表

接口在 `com.fs.starfarer.api.campaign.rules.MemoryAPI`；取实例入口是 `com.fs.starfarer.api.campaign.rules.HasMemory` 的 `getMemory()` / `getMemoryWithoutUpdate()`：

| 方法 | 语义 |
|---|---|
| `set(key, value)` | 写入永不过期键 (javadoc："Never expires.")；**清除该键未决过期** |
| `set(key, value, expire)` | 写入键并设置剩余寿命 (游戏天) |
| `expire(key, days)` | 重设键的剩余寿命；`days < 0` 表示取消过期 |
| `unset(key)` | 主动移除键 (含 required 级联) |
| `contains(key)` | 区分 "键缺失" 与 "值为 null/false" 的唯一可靠入口 |
| `get(key)` / `getString` / `getFloat` / `getBoolean` / `getLong` / `getInt` | 按类型读取 |
| `getVector2f` / `getEntity` / `getFleet` | 类型化读取，非该类型返回 null |
| `is(key, value)` | `contains` 且值相等 (float 精确比较) |
| `between(key, min, max)` | 闭区间 `[min, max]`；缺失返回 false |
| `getKeys()` / `getExpire(key)` | 全部键集合 / 剩余游戏天 (无过期 -1) |
| `advance(amount)` | **引擎驱动**，MOD 代码不要手动调用 |
| `addRequired` / `removeRequired` / `getRequired` / `removeAllRequired` | required 依赖清理 |
| `clear()` / `isEmpty()` | 清空全部键 (含过期与依赖表)/ 判空 |

缺失键的读取默认值 (`Memory` 类)：

- `getString` → null；
- `getFloat` → 0；
- `getInt`/`getLong` → 0；
- `getBoolean` → false (值转小写去空白后等于 `"true"` 才为真)；
- `getEntity`/`getFleet`/`getVector2f` → null；
- `between`/`is` → false。

**`getBoolean` 无法区分 "键缺失" 与 "显式 false"**，需要区分时先 `contains`。

### 记忆键：`$` 前缀必须遵守的规则

公开面里第一条必须遵守的约束是键名前缀。`Memory.set` 的两个重载都先检查 `key.startsWith("$")`，不满足直接抛 `RuntimeException("Key " + key + " should start with $")`。

Java 侧写键漏 `$` 是头号新手错误。

规则侧永远不可能违反：token 解析出的变量天然带 `$`；Java 侧所有写键调用点都要对照键常量复查前缀。

### 过期语义

键写进去只是第一步，什么时候消失由过期语义决定，四个要点直接决定键的寿命：

- **单位是游戏天**。`Memory.advance` 接收真实秒的推进量，按 `CampaignClockAPI` 换算成游戏天消耗寿命，基准是 **10 真实秒 = 1 游戏天**。
- **只在生涯非暂停时推进**：`Memory.advance` 内部先检查 `!isPaused()`，生涯暂停时引擎不把推进量传入记忆。战斗期间生涯不推进，记忆不消耗寿命。
- `expire(key, days)`：键已有过期条目 → 覆盖 `timeLeft`；`days < 0` → 移除过期条目 (键转为永不过期)；否则新增条目。
- **两参 `set` 清除未决过期**：先写值、再删该键的过期条目。可观察：`getExpire(key)` 从正数变 -1。想 "改成永久" 就调两参 `set`；想 "改回有过期" 必须再调三参 `set` 或 `expire`。
- **`0` 天过期即会话内 flag**：`timeLeft = 0`，下一个未暂停推进帧 `timeLeft -= days` 小于 0 → `unset`。
    - 对话打开期间生涯暂停、不推进，所以 `expire 0` 的键在整个对话内可读；对话关闭后的第一个推进帧被移除。
    - 原版范式：`$option` (`RuleBasedInteractionDialogPluginImpl.optionSelected` 中 `memory.set("$option", optionId); memory.expire("$option", 0)`) 与一次性 flag `$player.<flag> = true 0`。
- `getExpire(key)` 返回剩余**游戏天**；无过期条目返回 -1。devMode 的 `DumpMemory` 规则命令按此显示 `(e=<剩余天数>)`(`com.fs.starfarer.api.impl.campaign.rulecmd.DumpMemory`)，`(e=0)` 表示 "下一个推进帧移除"。
- rules 侧的过期语法：`=` / `++` / `--` 的第三 token 是过期天数，先 `set`(清过期) 再 `expire(days)`；不带第三 token 的赋值是**永久键**。

排障从 `DumpMemory` 与 `getExpire` 开始。

devMode 下 `DumpMemory` 显示键列表与 `(e=<剩余天数>)`；`getExpire` 是剩余寿命的第一核对入口：先看键在不在、剩多少天，再核对槽位与单位。

### required 依赖清理

过期之外，还有一类键与键之间的依赖需要自动清理。`addRequired(key, requiredKey)` 可为同一个键记录多个依赖，清理有两处：

1. `advance` 时检查：主键的全部 required 键都不存在 → 主键移除；
2. `unset(requiredKey)` 级联：`reqFor` 反向索引找到依赖它的主键 → 若主键最后一个 required 消失 → 递归 `unset` 主键。

典型用途：A 键只在 B 键存在时有意义：`addRequired(A, B)` 后 B 一消失 A 自动清理，不需要自己轮询。

### MemKeys 槽位速查表

键最终存放在哪个槽位，决定规则侧用 `$memkey.name` 还是裸 `$name` 读到它。`MemKeys`(`com.fs.starfarer.api.campaign.rules.MemKeys`) 定义 memoryMap 槽位名：

| 常量 | 值 | 归属 |
|---|---|---|
| `MemKeys.GLOBAL` | `"global"` | Sector 全局记忆 (`Global.getSector().getMemory()`) |
| `MemKeys.PLAYER` | `"player"` | 玩家记忆 (`getCharacterData().getMemory()`) |
| `MemKeys.LOCAL` | `"local"` | 当前交互局部记忆 (默认取交互目标记忆；活动人物对话时为人物记忆) |
| `MemKeys.MARKET` | `"market"` | 当前市场记忆 |
| `MemKeys.SOURCE_MARKET` | `"sourceMarket"` | 来源市场记忆 (local 里 `MemFlags.MEMORY_KEY_SOURCE_MARKET` 指定的市场) |
| `MemKeys.FACTION` | `"faction"` | 当前规则上下文势力记忆 |
| `MemKeys.ENTITY` | `"entity"` | 活动人物对话时的交互目标记忆 |
| `MemKeys.PERSON_FACTION` | `"personFaction"` | 活动人物所属势力记忆 |
| `MemKeys.MISSION` | `"mission"` | 任务上下文记忆 |

组装点 (memoryMap 从哪来)：

- **对话框**：`RuleBasedInteractionDialogPluginImpl.updateMemory`：`LOCAL` 即交互目标记忆、`FACTION`(null 时用 `createMemory()` 空记忆)、`GLOBAL`、`PLAYER`、`MARKET`、`SOURCE_MARKET`(按 local 的 `$sourceMarket` 查市场)；活动人物时 `updatePersonMemory` 把 `LOCAL` 换成人物记忆、`ENTITY` 即目标记忆、`PERSON_FACTION` 即人物势力记忆；任务上下文时 `setActiveMission` 补 `MISSION` 槽位。
- **非对话面板**：面板的 memoryMap 组装与规则执行 (`local` / `faction` / `global` / `player` / `market` / `sourceMarket` 槽位)，用于贸易面板 `TradePanelFlavorText` 等非对话场景。
- **主菜单新游戏对话框**：`NewGameDialogPluginImpl`：`LOCAL` 即角色数据实体记忆、`GLOBAL`，devMode 时写入 `$global.isDevMode`。

变量解析 (`Misc.Token.getVarNameAndMemory`，`com.fs.starfarer.api.util.Misc`)：

1. `$memkey.name`(点号存在且不在首尾)→ 从 `memoryMap` 取 `memkey` 槽位的记忆，读其中的 `$name` 键；
2. 无点号 (如 `$option`、`$GearIn_memory_fleetKey`)→ 槽位为 null → **回落 `local`**；
3. 槽位名不在 memoryMap 里 → **同样回落 `local`**，用原始串 `$memkey.name` 作键名；
4. `local` 也不存在 → `RuleException("No memory found for keys: ...")`。

**槽位选择决定键的可见范围**：`$global.xxx` 与 `$xxx`(local) 可能同时存在同名字段，规则读到的是槽位解析的结果 —— 槽位缺失回落 `local` 时**键名保留原始串** `$global.xxx`，不报错，但读的是 local 槽位里的 `$global.xxx` 键；若该键不存在，条件静默不命中。

文本 token 替换 (`Misc.replaceTokensFromMemory`) 同样按 memoryMap 槽位展开：`local` 优先、各记忆键按长度降序，替换 `$<memkey>.<key>` 与 `$<key>` 两种形态；

**只替换 String / Boolean / Float / Integer 值**，其它对象值原样留下。

### getMemory() 与 getMemoryWithoutUpdate()

先记住结论：写键与高频读取用 `getMemoryWithoutUpdate()`，需要最新 facts 时才用 `getMemory()`，两者的差别在 facts 刷新。

`SectorEntityToken.getMemory()` 首次调用时才创建记忆，并遍历全部已注册 `CampaignPlugin` 调 `updateEntityFacts` (`$tag:` 等 facts 键由 `CoreCampaignPluginImpl` 的 `update*Facts` 写入，前置章节已述)；

`getMemoryWithoutUpdate()` 只读不刷 facts。

市场同款：`Market.getMemory()` 触发 `updateMarketFacts`，`getMemoryWithoutUpdate()` 不触发。

- 写键不需要 facts：`getMemory()` 会在每次调用时把全部插件刷一遍 (副作用 + 性能损耗)。
- 读取时如果需要最新 facts(如 `$tag:` 条件依赖的键)，用 `getMemory()`；纯读自写键用 `getMemoryWithoutUpdate()`。
- `Global.getSector().getMemory()`(sector 记忆) **同样有** facts 钩子，遍历全部注册 `CampaignPlugin` 调 `updateGlobalFacts`(`CoreCampaignPluginImpl.updateGlobalFacts` 写入 `$isDevMode` / `$isInTutorial` / `$playerCanUseGates` / `$daysSinceStart`)，两入口不等价；`getMemoryWithoutUpdate()` 只读写；`SectorAPI.getPlayerMemoryWithoutUpdate()` 与 `getCharacterData().getMemoryWithoutUpdate()` 等价。

写入与高频读取一律用 `getMemoryWithoutUpdate()`。

否则每次调用都触发全部插件 `update*Facts`：性能损耗 + facts 副作用在意外时机发生。这是必须级纪律。

### 记忆推进点速查表

键的寿命由谁扣减？答案统一在 `Memory.advance`：所有记忆的过期都由它处理，引擎在各推进点调用 (单位换算与暂停判断都在 `Memory.advance` 内部)：

| 记忆 | 推进点 |
|---|---|
| sector 记忆 | 生涯引擎推进 |
| 玩家记忆 (characterData) | 生涯引擎推进 |
| 势力记忆 | 生涯引擎调用 `Faction.advance` |
| 实体记忆 | 所属地点推进实体 |
| 地点记忆 | 生涯引擎调用 `BaseLocation.advance` |
| 市场记忆 | 普通市场由经济系统推进；行星条件市场由实体推进 |
| 人物记忆 | 所属舰队、市场或 `ImportantPeople` 推进 |

为什么推进是引擎的责任。

这些上层调用链最终都进入 `Memory.advance`。MOD 代码既不需要也不应手动驱动。

另外：记忆是延迟创建的 —— `getMemory()` / `getMemoryWithoutUpdate()` 首次调用才创建实例 (如实体记忆)，此后该对象序列化时记忆一并入库。

**"读记忆会创建记忆"**：只为判空而调用 `getMemoryWithoutUpdate()` 也会让空记忆开始随存档保存。

### 保存与恢复：enRef/mRef 与 PersistentData

运行时之外，记忆还要跨存档生存。

记忆随所属对象序列化进存档。

`Memory.writeReplace` 把两类引用换成字符串占位，存活的 `SectorEntityToken` → `"enRef_" + id`，`Market` → `"mRef_" + id`；

`readResolve` 延迟重建，首次访问 (`get` / `contains` / `advance`，`restored` 标志) 时 `replaceIdsWithEntities` 把占位解析回对象 —— **解析不到 (实体已销毁/移除)→ 该键被删除**。

可观察结果：读档后 "键还在但值变了" 或 "键整个消失"，都没有任何报错。

`PersistentData`(`SectorAPI.getPersistentData()`，`com.fs.starfarer.api.campaign.SectorAPI`)：随存档保存的全局 `Map<String, Object>`。

javadoc 明确它 "可被不属于存档的类 (`ModPlugin` 或 transient `CampaignPlugin` 实现) 用于跨会话保存数据"；`modPlugin` 回调实例是进程级缓存的、`onEnabled` 每次换新实例 (持久数据注册纪律见前置章节)，跨回调状态只能放这里。

键**不需要** `$` 前缀。

### 记忆与 PersistentData 选型速查表

需求决定存储位置，责任边界如下：

| 需求 | 用记忆 | 用 PersistentData |
|---|---|---|
| 绑定到实体/市场/人物的状态 (舰队 AI flag、`$sourceMarket`、任务状态) | 所属对象的记忆 | 不适用 |
| 全局跨对象状态 (规则场景状态机 `$menuState`、`$hailing`) | sector / player 记忆 | 少用 |
| 跨存档、跨 `ModPlugin` 实例、注册判断 (`REGISTER_MARK` 等) | 不适用 | 是 |
| 值类型 | 任意可序列化对象；实体/市场引用走 enRef/mRef 白名单 | 存**可序列化值**；存运行时实体引用会在读档恢复边界污染状态 |

纪律 (延续前置章节的持久数据键约定)：**每个注册项用各自 persistent data 键** —— 多个演示/系统共用一键会互相覆盖；把新注册项并入插件已有的 CLASS\_MARK 检查会让旧档永不注册。

`PersistentData` 不得存运行时实体/脚本引用。

读档恢复边界上引用对象状态过期或反序列化异常；只存可序列化值。这是必须级纪律。

## 规则强度速查表

把上面的约束汇总成规则清单，违反后的可观察结果均附上：

| 规则 | 强度 | 违反后的可观察结果 |
|---|---|---|
| 记忆键必须以 `$` 开头 (`Memory.set` 强制校验) | 必须 | Java 侧 `set` 抛 `RuntimeException("Key ... should start with $")`：脚本/命令调用点异常 (对话红字段落或日志堆栈)，写入不生效 |
| `set(key, value, expire)` 的 `expire` 单位是**游戏天** (`CampaignClockAPI`，10 真实秒 = 1 游戏天) | 必须 | 把真实秒当天数：键以 10 倍速过期，`getExpire` / `DumpMemory` 显示与直觉不符 |
| 两参 `set` 清除该键未决过期 | 事实 | `getExpire(key)` 变 -1，键转为永不过期，"顺手改成永久" 是特性，不是 bug |
| `expire(key, 负值)` 取消过期 | 事实 | 键永不过期；`getExpire` 返回 -1 |
| 过期只在生涯非暂停时推进 | 事实 | 对话打开 (生涯暂停) 期间 `expire 0` 的键不消失；对话关闭后的第一个推进帧才移除 |
| 一次性 flag 必须用 `= true 0` / `expire 0` 形态 (`RuleBasedInteractionDialogPluginImpl` 写 `$option` 的实现；[C15-S2] 模式) | 建议 | 不带第三 token 的赋值是永久键：flag 跨会话残留，`!$player.<flag>` 条件永假 |
| `getBoolean` 缺失键与显式 false 无法区分 | 事实 | `!$flag` 在 flag 从未写过时判真 true，条件命中 (`!` 分支对缺失键执行 `!memory.getBoolean(key)`，缺失键 getBoolean 返回 false) |
| memoryMap 槽位缺失/无点号时变量回落 `local`(`Misc` 的 `Token` 解析) | 事实 | 回落时**键名保留原始串**：槽位缺失读 local 里的 `$global.xxx` 键，无点号则读 `$xxx`，键不存在则条件静默不命中，不会报错 |
| 实体/市场引用入记忆存档转 `enRef_`/`mRef_`，解析不到删键 | 事实 | 读档后实体已销毁 → 键整体消失且无报错；不要用记忆判断 "实体是否还活着" |
| PersistentData 键无需 `$` 前缀、随存档保存 (`SectorAPI`) | 事实 | 无错误，与记忆键的命名约束差异是设计，不是 bug |
| 每个注册项用各自 persistent data 键 ([C7-S1] / [C10-S1] 延续) | 必须 | 共用一键互相覆盖；新注册项并入既有 CLASS\_MARK 检查 → 旧档永不注册 |
| PersistentData 不得存运行时实体/脚本引用 | 必须 | 读档恢复边界污染：引用对象状态过期或反序列化异常；只存可序列化值 |
| 写入/高频读取用 `getMemoryWithoutUpdate()` | 必须 | 每帧 `getMemory()` 触发全部插件 `update*Facts`：性能损耗 + facts 副作用在意外时机发生 |
| `Memory.advance` 由引擎驱动 (`MemoryAPI`) | 必须 | MOD 手动调用 advance 会与实际推进脱节 (多推进/漏推进)，不要调用 |

## 组装 Memory 状态实现

规则面建立后，把它落成可运行的最小例。在既有 MOD 布局 (前置章节 [C6-S1]) 之上新增：六个 Java 类 (本节所有) 与 `data/campaign/rules.csv` 追加本节前缀 `GearIn_memory_*` 的规则行 (跨单元共享文件，只追加自己的行，同前缀内 id 唯一)。

不新增其它资源、不修改任何既有文件：

```text
mods/GearIndustry/
├── build.gradle
├── mod_info.json
├── data\
│   └── campaign\
│       └── rules.csv                     # 追加本节 GearIn_memory_* 规则行 (本节示例所有权)
├── src\
│   └── data/scripts/gearin/rules\
│       ├── GearInMemoryKeys              # 键常量数据类 (8 个记忆键，本节示例所有权)
│       ├── GearInMemoryWriter            # 记忆写入与读回脚本
│       ├── GearInPersistentDataCounter   # PersistentData 跨存档计数脚本
│       ├── GearInMemoryMapAssembler      # memoryMap 组装与变量解析脚本
│       ├── GearInMemoryExpiryVerifier    # 过期与依赖清理验证脚本
│       └── GearInMemoryDialog            # 规则对话打开脚本
└── jars\
```

### 配置 Memory 规则数据

```csv
id,trigger,conditions,script,text,options,notes
GearIn_memory_welcome,GearInMemoryDialog,,,"global 槽位 (Java 写入、无过期)：$global.GearIn_memory_tag；计数键 (1000 游戏天过期)：$global.GearIn_memory_counter；本地槽位 (无点号回落 local)：$GearIn_memory_fleetKey。","GearIn_memory_verify:验证内存键
GearIn_memory_leave:结束 ","本节示例所有权 GearIn_memory_*"
GearIn_memory_verify,DialogOptionSelected,$option == GearIn_memory_verify,"$player.GearIn_memory_lastAction = verify 0
FireBest GearInMemoryResult",,,
GearIn_memory_result_expired,GearInMemoryResult,$global.GearIn_memory_probe,,过期键判真演示：$global.GearIn_memory_probe 已在 Java 侧 expire 0 移除，本规则条件恒假、不会命中。,,
GearIn_memory_result_alive,GearInMemoryResult,"$global.GearIn_memory_counter >= 1
!$global.GearIn_memory_probe",," 命中：$global.GearIn_memory_counter >= 1 且 $global.GearIn_memory_probe 已过期 (判真 false)。$player.GearIn_memory_lastAction 已写入玩家内存 (expire 0) —— 关闭对话后第一个推进帧移除。",GearIn_memory_leave:结束,
GearIn_memory_leave,DialogOptionSelected,$option == GearIn_memory_leave,DismissDialog,,,
```

对应关系：

- **welcome**(trigger `GearInMemoryDialog`)：文本演示三种读法：`$global.GearIn_memory_tag`(显式槽位，Java 两参 set 写入、无过期)、`$global.GearIn_memory_counter`(三参 set、1000 游戏天过期)、`$GearIn_memory_fleetKey`(无点号 → 回落 `local`，即交互目标记忆，也就是玩家舰队记忆)。
    - 选项 "验证内存键"/"结束"。
- **verify**(trigger `DialogOptionSelected`)：`$option ==` 回流。脚本演示 rules 侧过期语法：`$player.GearIn_memory_lastAction = verify 0`(写玩家内存 + 0 天过期)，再 `FireBest GearInMemoryResult` 进入结果分派。
- **result\_expired / result\_alive**(trigger `GearInMemoryResult`)：FireBest 分数竞争的对照：expired 的条件 `$global.GearIn_memory_probe` 判真 (probe 已被 Java 侧 expire 0 移除) 恒 false，永不命中；alive 的条件 `$global.GearIn_memory_counter >= 1` + `!$global.GearIn_memory_probe` 两条都过 (2 分) 恒胜。
    - alive 的文本里 `$player.GearIn_memory_lastAction` 会被 token 替换成 "verify"。
- **leave**(trigger `DialogOptionSelected`)：`DismissDialog` 关闭对话。
- 记忆键所有权：`$GearIn_memory_*` 全部由本节定义；原版与其它示例的既有键 (`$option`、`$player.gearInScene*` 等) 只消费不改写。

### 配置 Memory 注册位置

六个类之间不互相引用、不依赖注册顺序：全部以**键状态**对齐。

#### [GearInMemoryKeys(键常量数据类)]

```java
package data.scripts.gearin.rules;

/**
 * 本节记忆键常量 (GearIn_memory_* 数据所有权)：写入、验证、解析、对话各脚本
 * 共用，键值与 rules.csv 条件/文本逐字一致 (含 $ 前缀与大小写)。记忆键必须
 * 以 $ 开头 (Memory.set 硬校验)；PersistentData 键不在此列，由各自的脚本持有。
 */
public final class GearInMemoryKeys {

    private GearInMemoryKeys() {
    }

    /** sector 内存：无过期键 (两参 set)。 */
    public static final String KEY_TAG = "$GearIn_memory_tag";
    /** sector 内存：1000 游戏天过期键 (三参 set)。 */
    public static final String KEY_COUNTER = "$GearIn_memory_counter";
    /** sector 内存：expire 0 键 —— 下一个未暂停推进帧被移除 (rules.csv 用它演示判真)。 */
    public static final String KEY_PROBE = "$GearIn_memory_probe";
    /** sector 内存：expire() 重设与两参 set 清除过期演示键。 */
    public static final String KEY_REARM = "$GearIn_memory_rearm";
    /** sector 内存：required 依赖演示 (子键消失 → 父键递归移除)。 */
    public static final String KEY_REQUIRED_CHILD = "$GearIn_memory_required_child";
    public static final String KEY_REQUIRED_PARENT = "$GearIn_memory_required_parent";
    /** 玩家内存：MemKeys.PLAYER 槽位读法演示。 */
    public static final String KEY_PLAYER = "$GearIn_memory_player";
    /** 舰队内存：无点号变量回落 local 槽位演示 (rules.csv 的 $GearIn_memory_fleetKey)。 */
    public static final String KEY_FLEET = "$GearIn_memory_fleetKey";
}
```

对应关系：

- 职责：本节 8 个记忆键常量的数据所有权：写入、验证、解析、对话各脚本共用；键值与 rules.csv 条件/文本逐字一致 (含 `$` 前缀与大小写)。
- 注册位置：无注册入口，final 工具类 + 私有构造器，同包五个脚本直接引用常量。
- 联动：`KEY_TAG`(`$GearIn_memory_tag`) 是跨类时序的基准键；`KEY_PROBE` 的 "写入后立即读回" 约束见写入脚本。

#### [GearInMemoryWriter(记忆写入与读回脚本)]

```java
package data.scripts.gearin.rules;

import java.util.TreeSet;

import org.apache.log4j.Logger;

import com.fs.starfarer.api.EveryFrameScript;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;

/**
 * 记忆写入与读回脚本 (EveryFrameScript)：读档首个未暂停帧写入全部记忆键并立即
 * 读回验证 (键常量见 GearInMemoryKeys)。probe 键 (expire 0) 的「写入后立即读回」
 * 必须在同一方法内完成 —— 跨脚本同帧执行顺序未定义，独立读回可能错过其存在状态。
 */
public class GearInMemoryWriter implements EveryFrameScript {

    private static final Logger LOG = Global.getLogger(GearInMemoryWriter.class);
    private boolean done = false;

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
        if (Global.getSector() == null || done) {
            return;
        }
        MemoryAPI sector = Global.getSector().getMemoryWithoutUpdate();
        sector.set(GearInMemoryKeys.KEY_TAG, "GearInReady");
        sector.set(GearInMemoryKeys.KEY_COUNTER, 1f, 1000f);
        sector.set(GearInMemoryKeys.KEY_PROBE, true, 0f);
        sector.set(GearInMemoryKeys.KEY_REARM, "armed", 1000f);
        sector.expire(GearInMemoryKeys.KEY_REARM, 30f);
        float rearmedExpire = sector.getExpire(GearInMemoryKeys.KEY_REARM);
        sector.set(GearInMemoryKeys.KEY_REARM, "permanent");
        float clearedExpire = sector.getExpire(GearInMemoryKeys.KEY_REARM);
        sector.set(GearInMemoryKeys.KEY_REQUIRED_CHILD, true);
        sector.set(GearInMemoryKeys.KEY_REQUIRED_PARENT, true);
        sector.addRequired(GearInMemoryKeys.KEY_REQUIRED_PARENT, GearInMemoryKeys.KEY_REQUIRED_CHILD);

        LOG.info("GearInMemoryWriter: 记忆键必须 $ 前缀 (Memory.set 对无 $ 键抛 RuntimeException)");
        LOG.info("GearInMemoryWriter: contains(KEY_TAG)="
                + sector.contains(GearInMemoryKeys.KEY_TAG)
                + " getString=" + sector.getString(GearInMemoryKeys.KEY_TAG)
                + " is(key,\"GearInReady\")=" + sector.is(GearInMemoryKeys.KEY_TAG, "GearInReady"));
        LOG.info("GearInMemoryWriter: getFloat(KEY_COUNTER)="
                + sector.getFloat(GearInMemoryKeys.KEY_COUNTER)
                + " is(key,1f)=" + sector.is(GearInMemoryKeys.KEY_COUNTER, 1f)
                + " between(0,2)=" + sector.between(GearInMemoryKeys.KEY_COUNTER, 0f, 2f)
                + " getExpire=" + sector.getExpire(GearInMemoryKeys.KEY_COUNTER) + "(游戏天)");
        LOG.info("GearInMemoryWriter: KEY_PROBE(expire 0)contains="
                + sector.contains(GearInMemoryKeys.KEY_PROBE)
                + " getBoolean=" + sector.getBoolean(GearInMemoryKeys.KEY_PROBE)
                + " getExpire=" + sector.getExpire(GearInMemoryKeys.KEY_PROBE)
                + "；将在下一个未暂停推进帧被移除");
        LOG.info("GearInMemoryWriter: expire(KEY_REARM, 30f) 重设后 getExpire=" + rearmedExpire
                + "；两参 set 清除未决过期后 getExpire=" + clearedExpire
                + "(-1 = 永不过期)");
        LOG.info("GearInMemoryWriter: required 依赖 getRequired(KEY_REQUIRED_PARENT)="
                + sector.getRequired(GearInMemoryKeys.KEY_REQUIRED_PARENT));

        Global.getSector().getCharacterData().getMemoryWithoutUpdate().set(
                GearInMemoryKeys.KEY_PLAYER, true);
        CampaignFleetAPI fleet = Global.getSector().getPlayerFleet();
        if (fleet != null) {
            fleet.getMemoryWithoutUpdate().set(GearInMemoryKeys.KEY_FLEET, "fleet-local");
        }
        LOG.info("GearInMemoryWriter: sector 内存键 = " + new TreeSet<>(sector.getKeys()));
        done = true;
    }
}
```

对应关系：

- 职责：读档首个未暂停帧写入全部记忆键并立即读回验证 (contains / is / between / getExpire / getString / getRequired)，覆盖 sector / player / fleet 三个槽位。
- 注册位置：`GearInModPlugin.onGameLoad` 内 `addScript` 注册。
- 时序角色：首批写入者，无前置条件；probe 键 (expire 0) 的 "写入后立即读回" 必须留在本方法内，跨脚本同帧执行顺序未定义，独立读回可能错过 probe 的存在状态。
- 值联动：tag 值 `"GearInReady"` 与 "预期结果" 的日志行、对话文本替换逐字一致。

#### [GearInPersistentDataCounter(PersistentData 跨存档计数脚本)]

```java
package data.scripts.gearin.rules;

import java.util.Map;

import org.apache.log4j.Logger;

import com.fs.starfarer.api.EveryFrameScript;
import com.fs.starfarer.api.Global;

/**
 * PersistentData 跨存档计数脚本 (EveryFrameScript)：每档一次累加运行计数并记录
 * 上次值 —— 直接可观察 PersistentData 随存档保存与恢复 (新档 run 1、读档 run N
 * 递增)。故意不加一次性 REGISTER_MARK 门控：每次读档都重跑才能看到递增；
 * 注册项使用各自的 persistent data 键 (C7-S1 纪律)。
 */
public class GearInPersistentDataCounter implements EveryFrameScript {

    /** PersistentData 键：与记忆键相反，不需要 $ 前缀 (随存档序列化的普通键)。 */
    public static final String PERSIST_KEY_RUNS = "GearIn_memory_runs";

    private static final Logger LOG = Global.getLogger(GearInPersistentDataCounter.class);

    private boolean done = false;

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
        if (Global.getSector() == null || done) {
            return;
        }
        Map<String, Object> persistent = Global.getSector().getPersistentData();
        Object previous = persistent.get(PERSIST_KEY_RUNS);
        int runs = (previous instanceof Number n) ? n.intValue() : 0;
        runs++;
        persistent.put(PERSIST_KEY_RUNS, runs);
        LOG.info("GearInPersistentDataCounter: PersistentData 运行计数 run " + runs
                + "(新档首次 = 1；读档恢复上次值 = " + previous + ")");
        done = true;
    }
}
```

对应关系：

- 职责：每档一次累加 run N 并记录上次值，直接可观察 PersistentData 随存档保存与恢复 (新档 run 1、读档 run N 递增)。
- 注册位置：`GearInModPlugin.onGameLoad` 内 `addScript` 注册。
- 故意不加一次性 `REGISTER_MARK` 检查：每次读档都重跑才能看到递增；持久键 `GearIn_memory_runs` 与记忆键不同，不需要 `$` 前缀，且各注册项独立 (持久数据键纪律，前置章节)。

#### [GearInMemoryMapAssembler(memoryMap 组装与变量解析脚本)]

```java
package data.scripts.gearin.rules;

import java.util.HashMap;
import java.util.Map;

import org.apache.log4j.Logger;

import com.fs.starfarer.api.EveryFrameScript;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.rules.MemKeys;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;

/**
 * memoryMap 组装脚本 (EveryFrameScript)：落键后按 MemKeys 槽位组装与对话框插件
 * 同构的 memoryMap，解析 $global. / $player. / 无点号变量 (复现变量解析规则)。
 */
public class GearInMemoryMapAssembler implements EveryFrameScript {

    private static final Logger LOG = Global.getLogger(GearInMemoryMapAssembler.class);
    private boolean done = false;

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
        if (Global.getSector() == null || done) {
            return;
        }
        MemoryAPI sector = Global.getSector().getMemoryWithoutUpdate();
        if (!sector.contains(GearInMemoryKeys.KEY_TAG)) {
            return; // 等写入脚本落键 (同帧顺序未定义，用键状态对齐)
        }
        Map<String, MemoryAPI> memoryMap = new HashMap<>();
        memoryMap.put(MemKeys.GLOBAL, sector);
        memoryMap.put(MemKeys.PLAYER, Global.getSector().getCharacterData().getMemoryWithoutUpdate());
        CampaignFleetAPI fleet = Global.getSector().getPlayerFleet();
        if (fleet != null) {
            memoryMap.put(MemKeys.LOCAL, fleet.getMemoryWithoutUpdate());
        }
        LOG.info("GearInMemoryMapAssembler: memoryMap 槽位 = " + memoryMap.keySet()
                + "(与对话框插件组装一致：local = 交互目标记忆、global、player)");
        LOG.info("GearInMemoryMapAssembler: 解析 $global.GearIn_memory_tag = "
                + resolveVariable(memoryMap, "$global.GearIn_memory_tag"));
        LOG.info("GearInMemoryMapAssembler: 解析 $player.GearIn_memory_player = "
                + resolveVariable(memoryMap, "$player.GearIn_memory_player"));
        LOG.info("GearInMemoryMapAssembler: 解析 $GearIn_memory_fleetKey(无点号回落 local)= "
                + resolveVariable(memoryMap, "$GearIn_memory_fleetKey"));
        done = true;
    }

    /**
     * 复现 Rules 引擎的变量解析 (Misc.Token.getVarNameAndMemory)：
     * "$memkey.name" → 取 memoryMap 的 memkey 槽位，读其中的 $name 键；
     * 无点号 → 回落 local 槽位。memoryMap 中没有该槽位时同样回落 local。
     */
    private static Object resolveVariable(Map<String, MemoryAPI> memoryMap, String variable) {
        int dot = variable.indexOf('.');
        String memoryKey = null;
        String name = variable;
        if (dot > 0 && dot < variable.length() - 1) {
            memoryKey = variable.substring(1, dot);
            name = "$" + variable.substring(dot + 1);
        }
        MemoryAPI memory = memoryMap.get(memoryKey);
        if (memory == null) {
            memory = memoryMap.get(MemKeys.LOCAL);
            name = variable;
        }
        return memory == null ? null : memory.get(name);
    }
}
```

对应关系：

- 职责：落键后按 `MemKeys` 槽位组装与对话框插件同构的 memoryMap(global / player / local，其中 local 为玩家舰队记忆)，解析 `$global.` / `$player.` / 无点号三种变量形态。
- 注册位置：`GearInModPlugin.onGameLoad` 内 `addScript` 注册。
- 时序条件：`KEY_TAG` 存在才执行，等写入脚本落键 (同帧顺序未定义，用键状态对齐)。
- `resolveVariable`：以最小代码复现 `Misc.Token.getVarNameAndMemory`(`com.fs.starfarer.api.util.Misc`) 的解析规则，演示 memoryMap 槽位语义，不等价于引擎实现，只用于教学对照。

#### [GearInMemoryExpiryVerifier(过期与依赖清理验证脚本)]

```java
package data.scripts.gearin.rules;

import org.apache.log4j.Logger;

import com.fs.starfarer.api.EveryFrameScript;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;

/**
 * 过期与依赖清理验证脚本 (EveryFrameScript)：写入脚本落键 (KEY_TAG 存在) 且
 * expire 0 的 probe 键已被 Memory.advance 移除后，记录移除结果，再 unset 一个
 * required 子键验证父键被递归移除。
 *
 * 时序对齐：probe 每次读档都被写入脚本重新写入并移除，故「tag 存在 && probe
 * 不存在」是稳定的验证时机 (同帧执行顺序未定义，用键状态对齐)。
 */
public class GearInMemoryExpiryVerifier implements EveryFrameScript {

    private static final Logger LOG = Global.getLogger(GearInMemoryExpiryVerifier.class);

    private boolean done = false;

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
        if (Global.getSector() == null || done) {
            return;
        }
        MemoryAPI sector = Global.getSector().getMemoryWithoutUpdate();
        if (!sector.contains(GearInMemoryKeys.KEY_TAG)
                || sector.contains(GearInMemoryKeys.KEY_PROBE)) {
            return; // 等写入脚本跑完，且 probe 已被下一个推进帧的 Memory.advance 移除
        }
        LOG.info("GearInMemoryExpiryVerifier: 下一推进帧后 contains(KEY_PROBE)="
                + sector.contains(GearInMemoryKeys.KEY_PROBE)
                + " getString=" + sector.getString(GearInMemoryKeys.KEY_PROBE)
                + "(expire 0 的键已被 Memory.advance 移除)");
        sector.unset(GearInMemoryKeys.KEY_REQUIRED_CHILD);
        LOG.info("GearInMemoryExpiryVerifier: unset(KEY_REQUIRED_CHILD) 后 contains(child)="
                + sector.contains(GearInMemoryKeys.KEY_REQUIRED_CHILD)
                + " contains(parent)=" + sector.contains(GearInMemoryKeys.KEY_REQUIRED_PARENT)
                + "(required 依赖：最后一个 required 键消失时父键被递归移除)");
        done = true;
    }
}
```

对应关系：

- 职责：probe(expire 0) 被 `Memory.advance` 移除后记录移除结果；再 unset 一个 required 子键验证父键被递归移除。
- 注册位置：`GearInModPlugin.onGameLoad` 内 `addScript` 注册。
- 时序条件：`KEY_TAG` 存在 && probe 不存在，probe 每次读档都被写入脚本重新写入并在下一推进帧被移除，所以 "tag 存在 && probe 不存在" 是稳定的验证时机。

#### [GearInMemoryDialog(规则对话打开脚本)]

```java
package data.scripts.gearin.rules;

import org.apache.log4j.Logger;

import com.fs.starfarer.api.EveryFrameScript;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.CampaignUIAPI;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.impl.campaign.RuleBasedInteractionDialogPluginImpl;

/**
 * 规则对话打开脚本 (EveryFrameScript)：记忆写入与过期验证完成 (KEY_TAG 存在、
 * probe 已被移除) 后，打开初始 trigger 为 GearInMemoryDialog 的对话 —— 对话文本、
 * 条件与选项全部来自 rules.csv 中本节拥有的 GearIn_memory_* 规则 ($global. /
 * $player. / 无点号回落 local 三种读法 + $player. 赋值 0 天过期)。
 */
public class GearInMemoryDialog implements EveryFrameScript {

    /** 对话初始 trigger：与 rules.csv 的 GearIn_memory_welcome 规则 trigger 一致。 */
    public static final String INITIAL_TRIGGER = "GearInMemoryDialog";

    private static final Logger LOG = Global.getLogger(GearInMemoryDialog.class);

    private boolean done = false;

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
        if (Global.getSector() == null || done) {
            return;
        }
        MemoryAPI sector = Global.getSector().getMemoryWithoutUpdate();
        if (!sector.contains(GearInMemoryKeys.KEY_TAG)
                || sector.contains(GearInMemoryKeys.KEY_PROBE)) {
            return; // 等写入与过期演示完成 (probe 移除后) 再弹对话，日志顺序可预期
        }
        CampaignFleetAPI fleet = Global.getSector().getPlayerFleet();
        if (fleet == null) {
            LOG.warn("GearInMemoryDialog: player fleet is null, skip dialog");
            done = true;
            return;
        }
        CampaignUIAPI ui = Global.getSector().getCampaignUI();
        boolean shown = ui.showInteractionDialog(
                new RuleBasedInteractionDialogPluginImpl(INITIAL_TRIGGER), fleet);
        if (shown) {
            LOG.info("GearInMemoryDialog: dialog shown=true"
                    + " (initial trigger GearInMemoryDialog; 对话内容全部来自 rules.csv)");
            done = true;
        }
        // shown=false：正有其它对话/界面排队 —— 不置 done，下一个未暂停帧重试
    }
}
```

对应关系：

- 职责：写入与过期验证完成 (`KEY_TAG` 存在、probe 已移除) 后，打开初始 trigger 为 `GearInMemoryDialog` 的对话，文本、条件与选项全部来自 rules.csv 本节 `GearIn_memory_*` 规则。
- 注册位置：`GearInModPlugin.onGameLoad` 内 `addScript` 注册。
- 时序条件：`KEY_TAG` 存在 && probe 不存在；`INITIAL_TRIGGER = "GearInMemoryDialog"` 与 rules.csv welcome 行 trigger 列逐字一致。
- 重试语义：`showInteractionDialog` 返回 false(正有其它对话/界面排队) 时不置 done，下一个未暂停帧重试；交互目标非 null(`init` 的 `LOCAL` 依赖它)。

#### [跨类时序]

六个类之间没有脚本引用，注册顺序与同帧执行顺序都不构成约定：全部以**键状态**对齐 —— 组装脚本等 `KEY_TAG` 落键，验证与对话脚本等 probe 被移除。

因此任意注册顺序、任意存档时机下行为确定：写入帧的下一帧起 probe 消失，验证与对话脚本在该帧通过键状态检查并执行；若该帧正有其它对话/界面，对话脚本自动顺延重试。

调用点 (工程片段，方法所在类归前置 modPlugin 章节所有，本节只演示调用，不改动那些文件)：

```java
    // GearInModPlugin(C7-S1 所有)onGameLoad(boolean newGame) 内新增本节注册块。
    // 故意不加一次性门控：五个脚本每次读档都重跑 (run N 递增、键重写)，才能观察
    // PersistentData 跨存档恢复与内存键重写；持久数据键使用各自的注册项键
    // (GearIn_memory_runs)，不挂进插件已有的 CLASS_MARK 门。
    Global.getSector().addScript(new GearInMemoryWriter());
    Global.getSector().addScript(new GearInPersistentDataCounter());
    Global.getSector().addScript(new GearInMemoryMapAssembler());
    Global.getSector().addScript(new GearInMemoryExpiryVerifier());
    Global.getSector().addScript(new GearInMemoryDialog());
```

构建命令 (在 `Starsector/mods/GearIndustry/` 执行，沿用前置章节)：

```text
gradle jar
```

产物 `jars/GearIndustry.jar` 应包含 `data/scripts/gearin/rules/` 下六个类的 `.class` 文件 (可用 `jar tf` 核对)；`data/campaign/rules.csv` 是 MOD 数据文件，随 MOD 目录被加载器读取。

注意示例路径 `mods/GearIndustry` 是教程虚构路径，页面内路径互相自洽即可。

## 验证方案

前置数据：既有 MOD 布局 [C6-S1]\+ `GearInModPlugin`([C7-S1]，含本节调用点片段)、`data/campaign/rules.csv` 本节 5 行、本节六个类。

1. 在 `Starsector/mods/GearIndustry/` 执行 `gradle jar`，确认 `BUILD SUCCESSFUL` 且 `jar tf jars/GearIndustry.jar` 含 `data/scripts/gearin/rules/` 下六个类的 `.class`。
2. 启动游戏，确认 `starsector.log` 出现本节 5 条 `Loading rule: GearIn_memory_*` 行。
3. 新建生涯，观察写入与计数日志：run 1、contains/is/between、getExpire=1000、rearmed 30 / cleared -1、required 依赖和 sector 键列表。
4. 观察 memoryMap 槽位和三个解析结果。
5. 观察 probe 移除、required 级联和对话弹出。
6. 在对话中点 "验证内存键"，观察文本和选项；devMode 下展开 "(dev) dump memory" 选项并查看输出。
7. 保存后读档，观察计数日志、内存状态和对话行为。

预期结果：

- **游戏构建**：`gradle jar` 以 `BUILD SUCCESSFUL` 结束；`jar tf jars/GearIndustry.jar` 能看到 `data/scripts/gearin/rules/` 下 `GearInMemoryKeys`、`GearInMemoryWriter`、`GearInPersistentDataCounter`、`GearInMemoryMapAssembler`、`GearInMemoryExpiryVerifier`、`GearInMemoryDialog` 六个类的 `.class`。
- **规则注册 (加载期)**：启动日志出现本节 5 条 `Loading rule: GearIn_memory_welcome` / `..._verify` / `..._result_expired` / `..._result_alive` / `..._leave`，规则行确实被读进内存的静态证明。
- **新档首次进入生涯 (首个未暂停推进帧)**：`starsector.log` 先出现写入脚本输出 (引擎按注册顺序推进脚本，`GearInMemoryWriter` 注册在 `GearInPersistentDataCounter` 之前)，随后出现 `GearInPersistentDataCounter: PersistentData 运行计数 run 1(新档首次 = 1；读档恢复上次值 = null)`；写入脚本输出：
    - `GearInMemoryWriter: contains(KEY_TAG)=true getString=GearInReady is(key,"GearInReady")=true`；
    - `getFloat(KEY_COUNTER)=1.0 is(key,1f)=true between(0,2)=true getExpire=1000.0(游戏天)`；
    - `KEY_PROBE(expire 0)contains=true getBoolean=true getExpire=0.0`；
    - `expire(KEY_REARM, 30f) 重设后 getExpire=30.0；两参 set 清除未决过期后 getExpire=-1.0(-1 = 永不过期)`；
    - `required 依赖 getRequired(KEY_REQUIRED_PARENT)=[$GearIn_memory_required_child]`；
    - `sector 内存键 = [$GearIn_memory_counter, $GearIn_memory_probe, $GearIn_memory_rearm, $GearIn_memory_required_child, $GearIn_memory_required_parent, $GearIn_memory_tag]`(全部 `$` 前缀)。
- **memoryMap 组装与解析 (`KEY_TAG` 落键后的未暂停帧，写入同帧或下一帧，取决于同帧脚本顺序)**：`GearInMemoryMapAssembler: memoryMap 槽位 = [global, local, player]`；三个解析结果：`$global.GearIn_memory_tag = GearInReady`、`$player.GearIn_memory_player = true`、`$GearIn_memory_fleetKey(无点号回落 local)= fleet-local`。
- **probe 移除与对话弹出 (probe 被移除后的第一个未暂停帧)**：`GearInMemoryExpiryVerifier: 下一推进帧后 contains(KEY_PROBE)=false getString=null(expire 0 的键已被 Memory.advance 移除)`；`unset(KEY_REQUIRED_CHILD) 后 contains(child)=false contains(parent)=false(required 依赖：...父键被递归移除)`；同时 `GearInMemoryDialog: dialog shown=true`，对话弹出：
    - 文本三行分别替换出 `GearInReady`、`1.0`、`fleet-local`(`$global.` / 无点号回落 local 两种 token 替换路径)；
    - 选项面板按声明顺序显示 "验证内存键" 与 "结束"。
    - 若该帧正有其它对话/界面排队，弹窗顺延到后续未暂停帧 (对话脚本不置 done，自动重试)。
- **点 "验证内存键"**：`$player.GearIn_memory_lastAction = verify 0` 写入玩家内存 (`DumpMemory` 可见 `$player.GearIn_memory_lastAction = verify (e=0)`)；`FireBest GearInMemoryResult` 命中 `result_alive` (条件 `$global.GearIn_memory_counter >= 1` + `!$global.GearIn_memory_probe` 两条 1 分全过)；`result_expired` 的条件 `$global.GearIn_memory_probe` 判真 false 不命中，文本面板追加 alive 的文本，其中 `$player.GearIn_memory_lastAction` 已替换为 `verify`。
- **点 "结束"(或按 ESC)**：`DismissDialog` 关闭对话；对话关闭后的第一个推进帧 `$player.GearIn_memory_lastAction` 被移除 (此后 `DumpMemory` 无该键)，"会话内 flag" 语义的完整流程得到可观察验证。
- **保存后读档**：日志 `PersistentData 运行计数 run 2(新档首次 = 1；读档恢复上次值 = 1)`；写入/组装/验证/对话脚本重跑、对话再次弹出；记忆键被重新写入。
- **全程无 ERROR**：启动与游玩期间无 `gear_in` 引起的 `ERROR`；加载期无 `Duplicate key` / 重复规则 id 报错。

## 故障分析

- **`set` 报告键必须以 `$` 开头**：Java 侧传入了没有前缀的 memory 键。

    - **解决方案**：修正键常量并重新写入。

- **过期键一直存在**：后续两参 `set` 清除了过期时间，或生涯仍处于暂停状态。

    - **解决方案**：使用三参 `set` 或再次调用 `expire`。

- **键在下一帧就消失**：代码把 `expire 0` 当成永久保存。

    - **解决方案**：永久键使用两参 `set`，重新写入。

- **键的寿命与预期相差约 10 倍**：过期参数按真实秒填写，而接口单位是游戏天。

    - **解决方案**：换算为游戏天。

- **一次性条件没有拦住重复进入**：写入和读取使用了不同 memory 槽位。

    - **解决方案**：让 `$player.<flag>` 等完整键在规则两侧一致。

- **规则条件静默不命中**：槽位前缀、键名或大小写与实际 memory 不一致。

    - **解决方案**：根据 `DumpMemory` 输出修正完整变量名。

- **读档后实体引用键消失**：保存的实体已经销毁或无法在星区中解析，`readResolve` 删除了该键。

    - **解决方案**：改为保存稳定实体 ID，读档后通过 sector 查询实体。

- **缺失 flag 与显式 false 无法区分**：`getBoolean` 对两者都返回 false。

    - **解决方案**：需要区分时先调用 `contains`。

- **PersistentData 的值被其他功能覆盖**：多个注册项复用了同一个持久键。

    - **解决方案**：为每项分配独立语义键。

- **规则读不到 Java 写入的键**：Java 写入的 memory 与规则变量指向不同槽位。

    - **解决方案**：sector 使用 `$global.`、玩家使用 `$player.`、交互目标使用 local。

- **同一个键在 rulecmd 中读到不同值**：命令收到的 memoryMap 上下文错误。

    - **解决方案**：通过 `BaseCommandPlugin.getEntityMemory(memoryMap)` 选择实体 memory，并处理 ENTITY 缺失。

- **高频读取导致 facts 回调持续执行**：循环调用 `getMemory()` 会刷新全部已注册插件。

    - **解决方案**：普通读写改用 `getMemoryWithoutUpdate()`。

## 总结

本节把 Rules 内存接到最小示例上，跑通了写入、过期与持久化的完整流程：`MemoryAPI` 提供键值读写与过期控制，`MemKeys` 槽位决定规则侧 `$memkey.name` 的解析结果，`Memory.advance` 在生涯非暂停时统一推进过期，`PersistentData` 承担跨存档的全局数据。六个类之间不互相引用，全部用键状态对齐，任意注册顺序下的行为因此都确定。

可以带走的判断：

- 记忆是运行时对象：没有数据文件、没有注册表，`getMemory()` / `getMemoryWithoutUpdate()` 取实例并延迟创建；
- 过期三问：单位是游戏天吗？谁推进 (引擎、非暂停)？值怎么改 (`expire` 重设、负值取消、两参 `set` 清除)？
- 槽位决定可见范围：`$global.`、`$player.`、无点号回落 `local`，拼错不报错，只会静默读错；
- 排障顺序：`DumpMemory`(devMode) 看实际键与 `(e=...)` → 核对槽位与键名 → 核对过期单位 → 核对调用时机 (暂停/对话)；
- 持久化二选一：实体/对象绑定状态用记忆；跨存档/跨实例状态用 `PersistentData`(独立键、只存可序列化值)。

前置章节的变量语法与状态机/一次性 flag 模式在本节获得内存层解释 —— `$` 前缀、槽位与过期机制正是它们能成立的原因。本节定义并持有的 `$GearIn_memory_*` 键、`GearIn_memory_runs` 持久键与六个类遵循示例所有权约定：其他内容按需使用，不改写既有语义。

## 建议参考项

- `com.fs.starfarer.api.campaign.rules.MemoryAPI`，Rules 内存接口：`set`(两/三参)/ `get` 族 / `contains` / `unset` / `expire` / `is` / `between` / `getExpire` / `getKeys` / required 族 / `clear`。
- `com.fs.starfarer.api.campaign.rules.HasMemory`：`getMemory()` / `getMemoryWithoutUpdate()` 取实例入口。
- `com.fs.starfarer.api.campaign.rules.MemKeys`：memoryMap 槽位常量 (`global` / `player` / `local` / `market` / `sourceMarket` / `faction` / `entity` / `personFaction` / `mission`)。
- `com.fs.starfarer.api.campaign.rules.RulesAPI`：`getAllMatching` / `getBestMatching` 的 memoryMap 第四参与 `performTokenReplacement`。
- `com.fs.starfarer.api.campaign.SectorAPI`：`getPersistentData()` / `getMemory()` / `getMemoryWithoutUpdate()` / `getPlayerMemoryWithoutUpdate()`。
- `com.fs.starfarer.api.campaign.SectorEntityToken`：实体记忆入口 (`getMemory()` / `getMemoryWithoutUpdate()`)。
- `com.fs.starfarer.api.impl.campaign.RuleBasedInteractionDialogPluginImpl`，对话框插件：memoryMap 组装与 `$option` 写 `expire 0` 的原版范式。
- `com.fs.starfarer.api.impl.campaign.rulecmd.DumpMemory`：devMode 记忆转储 (键列表与 `(e=<剩余天数>)`)。
- `com.fs.starfarer.api.impl.campaign.rulecmd.BaseCommandPlugin`：`getEntityMemory()`(`ENTITY` 优先、`LOCAL` 回退)。
- `com.fs.starfarer.api.impl.campaign.ids.MemFlags`：原版记忆键常量惯例 (`MEMORY_KEY_SOURCE_MARKET` 等)。
- `com.fs.starfarer.api.util.Misc`：`Token.getVarNameAndMemory` 变量解析与 `replaceTokensFromMemory` 文本替换。
- `data/campaign/rules.csv`：本节 5 条 `GearIn_memory_*` 规则行所在 (MOD 数据文件，追加行)。
- `data/scripts/gearin/rules/`：本节六个示例类所在目录 (MOD 内路径)。
