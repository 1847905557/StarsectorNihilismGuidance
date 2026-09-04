# 为模拟战与标题画面加入装配

本节把既有装配 `GearIn_artizan_combat` 加入模拟战敌方舰队与标题画面背景战斗。两个入口分别由一张 CSV 清单控制，无效装配 ID 会让对应展示缺少舰船或在加载时暴露错误。

*本节预期阅读时间：\~6 分钟。*

*本节预期操作时间：\~8 分钟。*

## 学习目标

完成后，您将能够：

- 用 `data/campaign/sim_opponents.csv` 设置模拟战对手，并验证装配 ID 已进入模拟战候选列表；
- 用 `data/config/title_screen_variants.csv` 设置标题画面演示舰船，并解释它随机展示的机制；
- 验证装配 ID 是否有效，并根据日志和画面表现定位加载故障。

## 前置知识

- [C4-S1] 的装配注册与 `GearIn_artizan_combat` 制作方法；
- [C1-S2] 的船体注册、`GearIn_artizan` 与 `<hullId>_Hull` 空装配关系；
- [C1-S3] 的战机联队注册与 `_wing` 后缀规则；
- [C1-S5] 的武器注册规则；
- [C2-S2] 的 CSV 跨 MOD 合并、表头和行首注释规则。

## 模拟战对手

### 登记模拟战对手

原版与启用的 MOD 会按行合并该文件，因此 MOD 只提交自己的装配行。表头必须逐字写成 `variant id`；在 `mod_info.json` 的 `replace` 数组声明该路径时，整份文件改为替换读取。

### 清单的最小形态

在 MOD 中新建 `data/campaign/sim_opponents.csv`：

```csv
variant id
GearIn_artizan_combat
```

- 第一行 `variant id` 是表头，其余每行是一个装配 ID；
- `GearIn_artizan_combat` 是前序装配注册单元的既有装配 (hull 为船体注册的 `GearIn_artizan`)，本节只使用、不新增装配 ID；
- 保存即生效，无需其他注册：下次启动游戏时随 `loadCampaignData` 一起加载。

![模拟战中的敌方舰队列表](./sim.png "sim.png")

*模拟战中的敌方舰队包含 MOD 装配*

### 无效 ID 的可观察结果 (星云-级回退)

模拟战构建敌方舰队时，每个 ID 都要构造一个 `FleetMemberAPI`。装配 ID 查不到时会发生两件事：

1. 打印堆栈 `[ID] is not a valid ship variant id`(`printStackTrace`，不中止战斗)；
2. 回退到 `settings.json` 的 `"errorShipVariant":"nebula_Standard"` —— 一艘星云-级 (Nebula 级) 运输船。

因此迁移原文 "填错 ID 会导致它变成一艘星云-级" 依然成立，且机制如上：**日志里能同时搜到该堆栈和这艘星云-级**。

模拟战、战役舰队和 `MissionDefinitionAPI.addToFleet` [C4-S1] 都通过 `FleetMemberAPI` 加载装配，因此会采用同一回退目标。

## 标题演示装配

### 登记标题画面装配

MOD 在 `data/config/title_screen_variants.csv` 提交标题画面装配行，表头同样是 `variant id`。CSV spec 加载期的 `loadTitleScreenVariants()` 读取后把每行追加进 `titleScreenVariantIds`，标题画面背景战斗随后从这份列表随机选择装配：

- 每次构建背景场景时，从整份列表**随机取一个 ID** 作为旗舰；`_wing` 结尾的按战机联队生成，其余按舰船生成；
- 场景会在需要重建时重新抽取候选，因此每次看到的舰船可能不同。

### 装配清单 (本例)

在 MOD 中新建 `data/config/title_screen_variants.csv`：

```csv
variant id
GearIn_artizan_combat
```

保存即可：下次启动后，标题画面背景战斗的候选池就包含这艘船。

::: tip 减少演示装配数

每个条目都可能成为主角，因此**只放 MOD 内最具代表性的常规舰船**，避免候选列表过长而难以观察目标装配。

:::

由于刷新是随机的，截图时机不好把握；教程沿用迁移版本的做法，不提供标题画面截图。

### 无效 ID 的表现 (与模拟战不同)

标题画面建船直接走舰船对象创建流程：先按 ID 查变体装配，查不到时抛 `Ship hull variant [ID] not found!`，**没有星云-级回退**。

可观察结果是标题画面背景战斗的场景构建中断，`starsector.log` 出现该异常。

因此标题演示的 ID 必须保证有效，"星云-级" 预期只适用于 `FleetMemberAPI` 路径。

### 验证方案

前置数据：装配注册单元的 `data/variants/GearIn_artizan_combat.variant`[C4-S1]，以及本节新建的两个 CSV —— `data/campaign/sim_opponents.csv` 与 `data/config/title_screen_variants.csv` (保持本例内容，UTF-8 无 BOM)。

1. 启动游戏 (启用测试 MOD)，进入主菜单。
2. 查看 `starsector.log`。
3. 在主菜单停留 10 秒以上，观察背景战斗中的舰船；必要时多等几次或重启几次。
4. 读取或新建生涯存档，进入任意舰船装配界面，点 "Run simulation"，观察部署界面的敌方舰队。

预期结果：

- 启动游戏 (启用测试 MOD)：正常进入主菜单 (没有加载中断)；
- `starsector.log` 出现 `Loading CSV data from [DIRECTORY: ...]` 记录。该日志不含文件名。
- 进入生涯，打开任意舰船的装配界面，点 "Run simulation"：敌方舰队包含 `GearIn_artizan_combat`；日志没有 `is not a valid ship variant id`。
- 回到主菜单停留一段时间 (或多次重启)：背景战斗的舰船池会随机出现 GearIn 的船 (首次构建时第一艘非战机条目以受创形态出现)。

## 故障分析

- **模拟战里没有您的船**：文件不在 `data/campaign` 时，列表会静默缺少目标；表头不是逐字的 `variant id` 时，`getString("variant id")` 会抛出键缺失异常并中断加载。

    - **解决方案**：确认路径和表头后重启。

- **对手变成星云-级运输船**：装配 ID 错误或未注册时，模拟战会回退到 `nebula_Standard`，日志记录 `[ID] is not a valid ship variant id`。

    - **解决方案**：让 ID 与 `data/variants` 中的注册值逐字一致，重新进入模拟战。

- **敌方舰队出现重复装配**：复制原版清单会把原版行再次合并。

    - **解决方案**：MOD 文件只保留新增行，重新加载。

- **标题画面一直看不到自己的船**：候选池较大，且只在场景重建时重新抽选。

    - **解决方案**：确认条目已写入文件，多次重建标题场景。

- **标题画面背景战斗报错**：`title_screen_variants.csv` 含无效 ID 时，场景构建会直接抛出 `Ship hull variant [...] not found!`，不会使用星云回退。

    - **解决方案**：删除或改正无效行，重建标题场景。

- **以 `_wing` 结尾的条目无法按舰船创建**：这类 ID 会按战机联队解析，模拟战使用 `FIGHTER_WING`，标题画面通过 `FighterWingSpecAPI` 查询。

    - **解决方案**：舰船装配 ID 避免 `_wing` 后缀；需要战机对手时填写 [C1-S3] 已注册的 wing ID，重新加载。

## 总结

本节把装配 ID(或 `_wing` 联队 ID) 分别写入 `data/campaign/sim_opponents.csv` 与 `data/config/title_screen_variants.csv`，让模拟战和标题画面能够读取这些装配。

两个文件都按行合并，MOD 只提交自己的装配行，并保持表头、编码和装配 ID 有效；需要整文件控制时，在 `mod_info.json` 使用 `replace`。

关键区别在无效 ID 的失败表现：经 `FleetMemberAPI` 的路径 (模拟战、战役) 回退成 `nebula_Standard` 星云-级并打印堆栈，标题画面直接抛 `Ship hull variant [...] not found!`。

装配注册后，样式与声音等观感要素也会通过船体引用进入渲染结果。

## 建议参考项

- `data/campaign/sim_opponents.csv`：MOD 模拟战对手清单 (相对 MOD 根目录，只写自己的行)。
- `data/config/title_screen_variants.csv`：MOD 标题画面演示清单 (相对 MOD 根目录)。
- `data/config/settings.json`：`errorShipVariant` 无效装配回退目标。
- `data/variants/GearIn_artizan_combat.variant`：本节两张清单使用的装配 (由装配注册单元持有)。
