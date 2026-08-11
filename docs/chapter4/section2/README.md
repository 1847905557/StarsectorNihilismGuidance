# 为模拟战与标题画面加入装配

本节把既有装配 `GearIn_artizan_combat` 加入模拟战敌方舰队与标题画面背景战斗。两个入口分别由一张 CSV 清单控制，无效装配 ID 会让对应展示缺少舰船或在加载时暴露错误。

*本节预期阅读时间：~6 分钟。*

*本节预期操作时间：~8 分钟。*

## 学习目标

完成后，您将能够：

- 用 `data/campaign/sim_opponents.csv` 设置模拟战对手，说出 "模拟战对手列表" 从注册到使用的完整链路；
- 用 `data/config/title_screen_variants.csv` 设置标题画面演示舰船，说出它随机展示的机制；
- 说明这两个文件共同的合并语义 (按行合并、`#` 注释、`replace` 整文件替换、表头逐字 `variant id`)；
- 区分两种 "无效装配 ID" 的失败表现：模拟战回退成星云-级并留下日志堆栈，标题画面直接抛异常。

## 前置知识

- [C4-S1] 的装配注册与 `GearIn_artizan_combat` 制作方法；
- [C1-S2] 的船体注册、`GearIn_artizan` 与 `<hullId>_Hull` 空装配关系；
- [C1-S3] 的战机联队注册与 `_wing` 后缀规则；
- [C1-S5] 的武器注册规则；
- [C2-S2] 的 CSV 跨 MOD 合并、表头和行首注释规则。

## 模拟战对手

### 文件与注册链路

1. 文件位置：MOD 的 `data/campaign/sim_opponents.csv`。原版同路径：`\Starsector\starsector-core\data\campaign\sim_opponents.csv`(30 个对手条目)。
2. 注册入口：CSV spec 加载期调用 `loadCampaignData()`，它读取 `data/campaign/sim_opponents.csv`，把每行的 `variant id` 追加进 `simOpponentVariantIds`。
   - 这个调用在装配 (`loadVariants`) 之后执行，所以清单里可以引用任何已注册装配。
3. 合并语义：文件在核心与所有启用 MOD 之间**按行合并**，而且本文件**不按键去重** (`readCSVMerged(null, ...)` 不传键列表，它的键检查分支整体跳过)。由此推出三条必须遵守的规则：
   - **不要**把原版整份 `sim_opponents.csv` 复制进 MOD—— 原版 30 个对手会逐行追加、全部重复出现 (敌方舰队直接翻倍)，且没有任何报错；
   - 以 `#` 开头 (行首第一个字符) 的行整行跳过，可以当注释或分区标题 (成熟实例常用 `#战列舰`、`#巡洋舰` 这类分区标题)；空行同样跳过；`#` 前有空格就不算注释；
   - 表头必须逐字是 `variant id`(含空格)：加载时每行执行 `getString("variant id")`，拼错 (如 `Variant id`) 会抛 JSON 键缺失异常，加载直接中断、进不了主菜单。
4. 整文件替换：在 `mod_info.json` 的 `replace` 数组里列出 `data/campaign/sim_opponents.csv`，该文件会整份替换合并结果 (`ModManagerAPI` 把 `replace` 条目按整文件替换处理，`readCSV` 只保留替换源)。
   - 常规做法是 "只写自己的行"，`replace` 是特殊情况。

### 使用方 (这份列表被谁使用)

- 公开 API：`SettingsAPI.getSimOpponents()`(`com.fs.starfarer.api.SettingsAPI`) 返回该列表；`getSimOpponentsDev()` 返回开发列表。
- 装配界面模拟战：装配界面点 "Run simulation" → 创建模拟战任务时，把列表中的每个 ID 加进敌方舰队。
  - ID 以 `_wing` 结尾的按战机联队 (`FleetMemberType.FIGHTER_WING`) 加入，否则按舰船 (`SHIP`) 加入。
- 模拟器插件：`settings.json` 的 `"simulatorPlugin":"com.fs.starfarer.api.impl.SimulatorPluginImpl"`—— 模拟战部署界面的 "Default" 对手类别正是这份列表。
- 生涯舰队面板：生涯界面的舰队教条面板在创建模拟战任务定义时使用同一列表。
- 开发列表 `sim_opponents_dev.csv`：与主文件同格式、同合并规则，独立成 `simOpponentDevVariantIds`，只通过 `getSimOpponentsDev()` 暴露，模拟器插件在 DevMode 类别里展示。常规 MOD 不需要它。

### 清单的最小形态

在 MOD 中新建 `\Starsector\mods\GearIndustry\data\campaign\sim_opponents.csv`：

```csv
variant id
# 本文件在所有启用 MOD 之间按行合并，只写自己的船
GearIn_artizan_combat
```

- 第一行 `variant id` 是表头 (逐字一致)，其余每行是一个装配 ID；
- `GearIn_artizan_combat` 是前序装配注册单元的既有装配 (hull 为船体注册的 `GearIn_artizan`)，本节只使用、不新增装配 ID；
- 保存即生效，无需其他注册：下次启动游戏时随 `loadCampaignData` 一起加载。

![模拟战中的原版与 MOD 敌方舰队列表](./sim.png "sim.png")

*模拟战中的敌方舰队 (原版 30 个对手与 MOD 装配的混合)*

### 无效 ID 的可观察结果 (星云-级回退)

模拟战构建敌方舰队时，每个 ID 都要构造一个 `FleetMemberAPI`。装配 ID 查不到时会发生两件事：

1. 打印堆栈 `[ID] is not a valid ship variant id`(`printStackTrace`，不中止战斗)；
2. 回退到 `settings.json` 的 `"errorShipVariant":"nebula_Standard"`—— 一艘星云-级 (Nebula 级) 运输船。

因此迁移原文 "填错 ID 会导致它变成一艘星云-级" 依然成立，且机制如上：**日志里能同时搜到该堆栈和这艘星云-级**。

所有经 `FleetMemberAPI` 加载装配 ID 的路径 —— 模拟战对手、战役舰队、`MissionDefinitionAPI.addToFleet` [C4-S1] —— 都走这个回退，因此该规则适用于经 `FleetMemberAPI` 加载装配 ID 的场景。

**唯一例外是标题画面演示** (见下节)，它不走 `FleetMemberAPI`，失败表现完全不同。

## 标题演示装配

### 文件与注册链路

1. 文件位置：MOD 的 `data/config/title_screen_variants.csv`。原版同路径：`\Starsector\starsector-core\data\config\title_screen_variants.csv`(12 个条目，全是常规舰船装配)。
   - 表头同样是 `variant id`。
2. 注册入口：CSV spec 加载期调用 `loadTitleScreenVariants()`，读取后把每行追加进 `titleScreenVariantIds`。合并、注释、表头规则与模拟战清单完全相同。
3. 使用方：标题画面背景战斗的插件：
   - 每次构建背景场景时，从整份列表**随机取一个 ID** 作为旗舰；`_wing` 结尾的按战机联队生成，其余按舰船生成；
   - 场景每约 1 秒检查一次 (`spawnIntervalTracker`)；旗舰飞出视口且距上次构建超过 10 秒才重建场景 —— 这就是 "刷新是随机的" 的机制来源：展示的船不是每次都在；
   - 首次构建还会额外生成若干战机联队，以及第一艘非战机条目的一艘 "受创" 演示船 (被施加 2 倍最大血量的高爆伤害)。

### 装配清单 (本例)

在 MOD 中新建 `\Starsector\mods\GearIndustry\data\config\title_screen_variants.csv`：

```csv
variant id
GearIn_artizan_combat
```

保存即可：下次启动后，标题画面背景战斗的候选池就包含这艘船。

::: tip 减少演示装配数

整份列表是随机抽取的，每个条目都可能成为主角，因此**只放 MOD 内最具代表性的常规舰船** (原版只有 12 艘；成熟实例通常只放 5–8 艘)，不要把自己的全部船塞进去。这是惯例建议，不是硬性限制。

:::

由于刷新是随机的，截图时机不好把握；教程沿用迁移版本的做法，不提供标题画面截图。

### 无效 ID 的表现 (与模拟战不同)

标题画面建船直接走舰船对象创建流程：先按 ID 查变体装配，查不到时抛 `Ship hull variant [ID] not found!`，**没有星云-级回退**。

可观察结果是标题画面背景战斗的场景构建中断，`starsector.log` 出现该异常。

因此标题演示的 ID 必须保证有效，"星云-级" 预期只适用于 `FleetMemberAPI` 路径。

### 验证方案

前置数据：装配注册单元的 `data/variants/GearIn_artizan_combat.variant`[C4-S1]，以及本节新建的两个 CSV——`data/campaign/sim_opponents.csv` 与 `data/config/title_screen_variants.csv` (保持本例内容，UTF-8 无 BOM)。

1. 启动游戏 (启用测试 MOD)，进入主菜单。
2. 查看 `\Starsector\starsector-core\starsector.log`。
3. 在主菜单停留 10 秒以上，观察背景战斗中的舰船；必要时多等几次或重启几次。
4. 读取或新建生涯存档，进入任意舰船装配界面，点 "Run simulation"，观察部署界面的敌方舰队。

预期结果：

- 启动游戏 (启用测试 MOD)：正常进入主菜单 (没有加载中断)；
- `starsector.log` 出现 `Loading CSV data from [DIRECTORY: ...]` 记录。该日志不含文件名。
- 进入生涯，打开任意舰船的装配界面，点 "Run simulation"：敌方舰队包含原版 30 个对手与 `GearIn_artizan_combat`；日志**没有** `is not a valid ship variant id`。
- 回到主菜单停留一段时间 (或多次重启)：背景战斗的舰船池会随机出现 GearIn 的船 (首次构建时第一艘非战机条目以受创形态出现)。
## 故障分析

- **模拟战里没有您的船**：文件不在 `data/campaign` 时，列表会静默缺少目标；表头不是逐字的 `variant id` 时，`getString("variant id")` 会抛出键缺失异常并中断加载。

    - **解决方案**：确认路径和表头后重启。

- **对手变成星云-级运输船**：装配 ID 错误或未注册时，模拟战会回退到 `nebula_Standard`，日志记录 `[ID] is not a valid ship variant id`。

    - **解决方案**：让 ID 与 `data/variants` 中的注册值逐字一致，重新进入模拟战。

- **敌方舰队的对手数量翻倍**：复制整份原版 `sim_opponents.csv` 会重复合并原版 30 个对手。

    - **解决方案**：MOD 文件只保留新增行，重新加载。

- **`#` 注释不生效**：`#` 前存在空格时，该行仍会被解析为装配 ID。

    - **解决方案**：把 `#` 放在行首第一个字符，重新加载。

- **标题画面一直看不到自己的船**：候选池较大，且只在场景重建时重新抽选。

    - **解决方案**：确认条目已写入文件，多次重建标题场景。

- **标题画面背景战斗报错**：`title_screen_variants.csv` 含无效 ID 时，场景构建会直接抛出 `Ship hull variant [...] not found!`，不会使用星云回退。

    - **解决方案**：删除或改正无效行，重建标题场景。

- **以 `_wing` 结尾的条目无法按舰船创建**：这类 ID 会按战机联队解析，模拟战使用 `FIGHTER_WING`，标题画面通过 `FighterWingSpecAPI` 查询。

    - **解决方案**：舰船装配 ID 避免 `_wing` 后缀；需要战机对手时填写 [C1-S3] 已注册的 wing ID，重新加载。

## 总结

本节完成了两张清单的完整走查：把装配 ID(或 `_wing` 联队 ID) 填进 `data/campaign/sim_opponents.csv` 与 `data/config/title_screen_variants.csv`，分别供给模拟战敌方舰队与标题画面背景战斗随机展示。

两个文件共享同一套合并语义 —— 按行合并、不按键去重、`#` 行首注释、表头逐字 `variant id`、文件 UTF-8 无 BOM(带 BOM 的表头会让键解析失败)；

关键区别在无效 ID 的失败表现：经 `FleetMemberAPI` 的路径 (模拟战、战役) 回退成 `nebula_Standard` 星云-级并打印堆栈，标题画面直接抛 `Ship hull variant [...] not found!`。

这套 "装配 → 展示" 的思路会继续延伸：装配注册之后，样式与声音等观感要素同样沿类似的注册与挂载链路落到船体上，渲染层最终呈现的正是这些注册成果的组合。

## 建议参考项

- `Starsector/starsector-core/data/campaign/sim_opponents.csv`：原版模拟战对手清单 (30 条，可对照 `#` 注释与表头写法)。
- `Starsector/starsector-core/data/config/title_screen_variants.csv`：原版标题画面演示清单 (12 条常规舰船装配)。
- `data/campaign/sim_opponents.csv`：MOD 模拟战对手清单 (相对 MOD 根目录，只写自己的行)。
- `data/config/title_screen_variants.csv`：MOD 标题画面演示清单 (相对 MOD 根目录)。
- `data/campaign/sim_opponents_dev.csv`：独立开发列表，仅经 `getSimOpponentsDev()` 暴露。
- `data/config/settings.json`：`errorShipVariant` 无效装配回退目标与 `simulatorPlugin` 模拟器插件注册。
- `data/variants/GearIn_artizan_combat.variant`：本节两张清单使用的装配 (由装配注册单元持有)。
- `com.fs.starfarer.api.SettingsAPI`：`getSimOpponents()` / `getSimOpponentsDev()` 公开 API。
- `com.fs.starfarer.api.impl.SimulatorPluginImpl`：模拟器插件，"Default" 类别使用主列表、DevMode 类别使用开发列表。
- `com.fs.starfarer.api.util.Misc`：`getSimulatorPlugin()` 获取模拟器插件实例。
