# 补充描述文本

本节将为本章各前节建立的舰船、战机与武器补上描述文本，并解释 `descriptions.csv` 的组织方式。

*本节预期阅读时间：~15 分钟。*

*本节预期操作时间：~30 分钟。*

## 学习目标

完成后，您将能够：

- 说出 `descriptions.csv` 的列结构、`id + type` 键规则与 `Description.Type` 的全部枚举值，并解释缺行与键错误的可观察结果差异 (占位文本 vs 启动失败)；
- 按对象类型说出描述查找键 (舰船 = hullId 或 `.ship` 的 `descriptionId`、武器 = 武器 ID、战术系统 = 系统 ID)，并说出 text1–text5 各自的主要使用方；
- 为既有 GearIn 对象编写描述行，并用 `.ship` 的 `descriptionId` 字段把舰船描述挂到本节专属键上；
- 从 "占位文本、启动报错、数据库 (codex) 显示降级" 三类症状反推描述数据错误。

## 前置知识

本节在第一章前五节建立的 GearIn 数据之上工作，要求您已掌握以下内容：

- 舰船贴图 → `.ship` → `ship_data.csv` 的贴图与数据链 [C1-S2]。
- 战机 hull/variant/wing 三层结构与 `wing_data.csv`[C1-S3]。
- 武器 `.wpn` → `weapon_data.csv` → `.proj` 数据链 [C1-S5]。
- 内置武器、内置船插与内置机联的三件套装配 [C1-S6]。
- 能稳定使用 `gear_in` MOD 与 `GearIn_` 数据前缀 [C1-S1]。
- CSV 基础：行首 `#` 的整行被忽略、逗号分割、含逗号的字段用引号包裹 [C1-S2]。
- 术语：战术系统 (ship system)；数据库 (codex，生涯界面中的图鉴界面)。

## 文件/API 链路

数据文件是 MOD 内的 `data/strings/descriptions.csv`。

::: info
这个路径本身就是注册入口 —— 加载器按固定路径遍历原版与全部已启用 MOD 的同名文件并合并，MOD 不需要任何额外注册。
:::

加载与查询共三步：

1. 加载期以 `id`、`type` 两列为键读取全部行；`type` 必须能解析为 `Description.Type` 枚举；每行以 `id + "_" + type` 为 UID 存入描述注册表。
2. 运行期查询入口是公开 API：`Global.getSettings().getDescription(id, type)`(`com.fs.starfarer.api.SettingsAPI`，公开 API)，返回 `com.fs.starfarer.api.loading.Description` 对象。
3. 使用方先按 "对象 → 查找键"(下表) 取描述，再按展示位置取 text1–text5 之一。

| 对象 | 描述键 (`id` 列)| 依据 |
|---|---|---|
| 舰船 | `.ship` 文件的 `descriptionId` 字段；未填写时回退为 `hullId` | `ShipHullSpecAPI.getDescriptionId()` 的回退；加载器读取 `.ship` 可选字段 |
| 战机 (联队)| 复用其 hull 的舰船描述。装配界面的战机选择器按 hull 的 `descriptionId` 查询；数据库的战机详情直接按 `hullId` 查询 (数据库详情界面)| 为避免两处键不一致，战机 hull 建议不写 `descriptionId` |
| 武器 | 武器 ID(`weapon_data.csv` 的 `id`，与 `.wpn` 的 `id` 相同)| 全部武器使用方都用 `getWeaponId()`，没有替代键 |
| 战术系统 | 系统 ID(`ship_systems.csv` 的 `id` / `.system` 的 `id`)| 系统使用方用系统 spec 的 id，没有替代键 |

## descriptions.csv 结构与最小示例

### 列结构与键规则

表头固定为 8 列 (原版 `descriptions.csv`)：

```csv
id,type,text1,text2,text3,text4,text5,notes
```

- `id` 与 `type` 两列合成查找键 (例如 `GearIn_durin_WEAPON`)，`type` 必须是 `Description.Type` 的枚举名，**区分大小写**：

| 枚举值 | 用途 | 本教程范围 |
|---|---|---|
| `SHIP` | 舰船与战机 hull 的描述 | 本节 |
| `WEAPON` | 武器描述 | 本节 |
| `SHIP_SYSTEM` | 战术系统描述 | 本节 (观察原版行，不新建)|
| `RESOURCE`、`PLANET`、`ASTEROID`、`FACTION`、`TERRAIN`、`ACTION_TOOLTIP`、`GALLERY`、`CUSTOM` | 生涯/杂项文本 | 后续章节 |

- `text1`–`text5` 的默认值与主要使用方：

| 字段 | 默认值 | 语义与主要使用方 (加载器与公开 API)|
|---|---|---|
| `text1` | `No description... yet` | 长描述，可多段 (空行分隔)。数据库详情页完整显示；蓝图物品 tooltip、货舱 tooltip 只显示第一段 (`getText1FirstPara`)|
| `text2` | `No description... yet` | 一句话短描述。装配界面武器列表副标题 (`weapon_data.csv` 的 `primaryRoleStr` 非空时优先)；舰队界面成员 tooltip 引言 (text3 为空时)；数据库系统列表的类型标签 (无 text2 时显示 `Special`)；战情室部署界面的信息标题 |
| `text3` | `No description... yet` | 短描述。装配界面的战术系统/相位系统行 (有 text3 用 text3，否则用 text1)；战情室信息正文；舰队 tooltip 中 text3 非空时引言改用 text1 第一段 |
| `text4` | 空 | 以 `|` 分隔的高亮片段，装配界面系统行渲染时按片段高亮 |
| `text5` | 空 | 战术系统的效果行，支持 `{{内容}}` 高亮标记；数据库系统详情的 `Effects` 节逐行渲染 |
| `notes` | 空 | 备忘录列，加载器不读取 |

- `id` 或 `type` 为空的行会被静默跳过；text1–3 为空或被省略时显示占位文本 `No description... yet`。
- 这是缺描述不报错的原因：查询入口在找不到键时返回一个填充占位文本的新描述对象。

::: warning
对象缺描述行时游戏不报错，界面只显示 `No description... yet` 占位文本。

不要把它当成 bug 去翻启动日志 —— 日志里什么都不会有；排查方向是 "查找键是否与对象键逐字符一致"。
:::

::: caution
必须避免三类会让加载阶段直接失败的错误 ——`type` 列不是枚举名 (拼写、大小写或多余空格)、同一文件内出现相同 `id + type` 组合、文件带 BOM 导致表头首列键名被污染。

三类错误在本节故障分析中均有对应症状与修正。
:::

### 完整最小例

以下内容全部放在虚构示例 MOD(`gear_in`，第一章首节建立) 中。先建立 `data/strings/descriptions.csv`，表头之后依次是本节拥有的描述行：

```csv {1}
id,type,text1,text2,text3,text4,text5,notes
GearIn_durin,WEAPON,"都林自动炮是一门口径小、射速极快的近距实弹炮。密集的破片在短距离内对护盾有不错的压制效果，但离开射程就毫无用处。",反装甲,,,,,
GearIn_uri,WEAPON,"乌里光束在中距离持续照射目标，产生稳定的能量伤害与幅能负担。光束本身没有击退力，需要持续瞄准。",压制,,,,,
GearIn_dwalin,WEAPON,"德瓦林发射架向远距离目标倾泻高爆火箭。备弹有限，装填缓慢，但每一发都对装甲构成实质威胁。",压制,,,,,
GearIn_combat_desc_artizan,SHIP,"工匠-级是一艘以导弹见长的驱逐舰。它把锤头-级的中实弹槽位换成了中通用，可以安装强力的导弹武器。",中坚驱逐舰,,,,,
GearIn_combat_desc_furnaceman,SHIP,"守炉人-级轻型航母以两座甲板提供持续空中掩护, 自身保留可观的结构。",轻型航母,,,,,
GearIn_slag,SHIP,"炉渣-级是一款廉价的重型战机，装备双管轻型自动炮。",重型战机,,,,,
GearIn_combat_desc_forging,SHIP,"锻件-级相位护卫舰围绕一门中型实弹槽位设计，用相位机动躲避火力。",相位护卫舰,,,,,
```

要点：

- 武器的描述行 `id` **必须**等于武器 ID(此前建立的 `GearIn_durin` 等)，这是查找键，无法另起别名。
- 三条舰船描述使用本节专属键 `GearIn_combat_desc_*`，需要靠 `.ship` 的 `descriptionId` 字段挂接 (见下文)。
- `GearIn_slag` 是战机 hull，按 "战机建议不写 `descriptionId`" 的规则使用默认键 `GearIn_slag`。
- 武器 `text2` 与 `weapon_data.csv` 的 `primaryRoleStr` 保持一致 (反装甲/压制)：装配列表副标题优先显示 `primaryRoleStr`，`text2` 是它在留空时的备用值。
- `GearIn_combat_desc_furnaceman` 的 text1 内含英文逗号，因此必须用引号包裹 (中文逗号 "，" 不是分隔符，无需包裹)；`text4`/`text5` 在示例中留空，其用法见原版参考行。

然后给三个舰船 `.ship` 文件加入 `descriptionId` 字段 (以 `GearIn_artizan.ship` 为例，字段与 `hullId` 同级)：

```json {2,3}
    "descriptionId": "GearIn_combat_desc_artizan",
    "descriptionPrefix": "由编者制作的第一艘示例舰船",
```

`GearIn_furnaceman.ship` 与 `GearIn_forging.ship` 分别写 `GearIn_combat_desc_furnaceman`、`GearIn_combat_desc_forging`。

`descriptionPrefix` 是可选字段，写在任何描述文本之前 (使用方用 `前缀 + "\n\n"` 拼接)；**它不以句号结尾时加载器会自动补一个句号**。

### 文本字段的书写规则

- **引号**：字段含英文逗号、换行或引号时**必须**用 `"` 包裹；字段内部的引号写成两个 `""`；换行会保留为段落分隔 (text1 的多段之间用空行)。
- **注释**：行首是 `#` 的整行被忽略，可以用它暂时注释掉某条描述。
- **编码**：加载器按 UTF-8 读取 (文本读取工具 `readText`)。
- 中文内容必须以 UTF-8 无 BOM 保存，否则显示乱码或加载异常 (见故障分析)。
- **弯引号**：加载时会把弯引号 (" " ' ') 替换为直引号 (`replaceSmartQuotes`)，所以不要依赖弯引号保留原样 (原版文件本身也含少量 Windows-1252 弯引号字节，靠这一步归一化)。
- **键唯一**：`id + type` 在同一文件内**必须**唯一，否则加载期抛异常 (见故障分析)。
- 也不要重定义原版或其他 MOD 已存在的 `id + type` 组合：跨文件合并时先被枚举到的文件的行生效，后到的同键行被**静默跳过**(读 CSV 时的去重分支)，覆盖顺序不是可以依赖的约定。
- 需要整体替换某个数据文件时，支持在 `mod_info.json` 的 `replace` 数组里声明 (加载器内部映射为 fullOverrides)，整体替换的细节见后续专题。

原版参考行 (`Starsector/starsector-core/data/strings/descriptions.csv`，仅观察不修改)：

```csv
hammerhead,SHIP,"A midline destroyer from the golden age of the Domain, the Hammerhead is respected for its staying power in battle as well as its ballistic capabilities.",,,,,
maneuveringjets,SHIP_SYSTEM,Engages extra thrusters to greatly improve the ship's maneuverability. Also provides a slight boost to the ship's top speed.,Movement,Provides a brief boost to maneuverability and top speed.,,The ship's top speed is increased by {{50}}.,
```

`maneuveringjets` 行展示了系统描述的标准结构：text1 长描述、text2 一句话类型名 (数据库系统列表用它当类型标签)、text3 短描述 (装配界面系统行)、text5 效果行 (含 `{{50}}` 高亮标记)。

`hammerhead` 行则说明：舰船通常只写 text1，text2/text3 留空即可。

### 验证方案

前置数据：`gear_in` MOD[C1-S1] 与第一章前五节 ([C1-S2] 至 [C1-S6]) 建立的全部数据，加上本节的 `data/strings/descriptions.csv` 与三个 `.ship` 文件的 `descriptionId` 修改 (即本节前述示例内容)。

1. 启动游戏进入生涯界面，观察 `starsector.log` 的描述文本加载记录。
2. 用 dev 控制台输入 `Addship GearIn_artizan`、`Addship GearIn_forging`、`Addwing GearIn_slag_wing`。
3. 进入装配界面：核对三门 GearIn 武器的副标题、工匠-级的战术系统行、锻件-级的相位系统行。
4. 回到舰队界面悬停工匠-级，观察舰船引言。
5. 打开数据库 (codex)：核对船体详情、武器详情、系统列表 (类型标签) 与系统详情 (`Effects` 节)、战机详情。
6. 悬停舰船/武器蓝图与货舱物品：核对 tooltip 的 text1 第一段。
7. 修改某一行的 text1 后重启游戏，在对应位置观察文本变化。

预期结果：

- **启动日志**：出现 `Loading descriptions` 的加载日志，`starsector.log` 无 `ERROR`。
- **装配界面**(`Addship GearIn_artizan` 后进入)：武器选择列表里三门 GearIn 武器的副标题显示 `反装甲, range 600` 之类的内容。
- 副标题文本由 `primaryRoleStr + ", range"` 拼出 (`text2` 是备用值)。
- 战术系统行显示 text3(例如锻件-级的相位系统行显示对应描述)，没有 text3 时显示完整 text1。
- text4 非空时其中片段被高亮。
- **舰队界面**：悬停工匠-级舰船成员，tooltip 引言显示 `descriptionPrefix + text2`(`GearIn_combat_desc_artizan` 行是 "由编者制作的第一艘示例舰船。中坚驱逐舰")。
- 没有 text2 的对象显示 text1 第一段。
- **数据库 (codex)**：船体详情显示 text1；武器详情显示 text1；战机详情显示其 hull 的 text1。
- 舰船系统列表每个条目都列出 (`HIDE_IN_CODEX` 除外)，类型标签取 text2、缺 text2 时显示 `Special`。
- 系统详情除 text1 外还有 `Effects` 节 (text5 逐行渲染，`{{...}}` 处高亮)。
- **蓝图与货舱**：悬停舰船/武器蓝图或货舱里的物品，tooltip 显示 text1 第一段 (舰船带 `descriptionPrefix` 前缀)。
- **最小任务链的验证流程**：修改某一行的 text1 后重启游戏，对应位置文本随之变化，证明该行确实被使用方读取，而不是碰巧显示别的文本。

清理：恢复 `data/strings/descriptions.csv` 中临时改动的描述行。

## 故障分析

按 "症状 → 原因 → 修正" 逐条排查；每条症状都是本节示例数据可以实际复现的。

- **启动即失败，日志含 `Invalid type of description: [null]`**：某行 `type` 列不是枚举名 (拼写、大小写或多余空格)。
- 描述加载对每一行做枚举解析，解析失败立即抛 `RuntimeException`。
- 修正：与 `Type` 枚举名逐字符一致，重启。
- **启动即失败，日志含 `Duplicate key [...] while loading [data/strings/descriptions.csv ...]`**：同一文件内出现相同 `id + type` 组合 (键串形如 `id | type |`)。
- 读 CSV 时对每个文件内部做重复键检查并抛异常。
- 修正：保证 `id + type` 全文件唯一，重启。
- **启动即失败，日志含 `JSONObject["id"] not found` 一类异常**：文件带 BOM 或表头被污染。
- BOM 字符会粘在表头第一列的列名上 (`id` 变成 `\ufeffid`)，加载器按 `id` 取键失败。
- 修正：以 UTF-8 **无 BOM** 重新保存。
- **对象缺描述行，界面显示 `No description... yet`**：这是设计行为而非报错 —— 查询缺行时得到占位描述对象 (text1–3 为占位文本)。
- 典型场景：武器行 `id` 写错、`.ship` 的 `descriptionId` 指向不存在的行、或该对象根本没有描述行。
- 修正：补行或改正键；注意大小写敏感。
- **改了描述但游戏里没变化**：先确认改的是会被读取的行 —— 键与任何对象都不匹配的未挂接行永远不会被读取，且不报错。
- 核对描述行的 `id`、`type` 与对象实际使用的查找键。
- **舰船 tooltip 引言与 text2 不一致**：tooltip 生成的规则是 text3 非空或 text2 缺失/为占位时改用 text1 第一段。
- 想用 text2 当引言，就不要填 text3。
- **数据库系统列表的类型标签显示 `Special`**：该系统的 `SHIP_SYSTEM` 行缺 text2。
- 条目本身仍会列出 (系统列表不依赖描述行)，只是类型标签降级；缺整行时详情正文显示占位文本。
- 修正：为自定义系统提供描述行，text1 必填、text2 建议填。
- **装配界面系统行显示整段长文**：系统描述缺 text3，界面回退到 text1。
- 若希望行内文本短而精，补 text3；text4 的 `|` 分隔片段会作为高亮渲染。
- **战机详情显示占位文本但装配界面正常**：战机的 hull 写了 `descriptionId`—— 装配界面的战机选择器按它查询，数据库战机详情却直接按 `hullId` 查询，两处键不一致。
- 修正：战机 hull 不写 `descriptionId`(本例 `GearIn_slag` 的用法)。
- **文本错位、截断或串到别的列**：某字段内含未加引号的英文逗号或换行，按列切分后错位 —— 键可能因此改变 (变成占位文本或没被使用的行)，或列数异常。
- 修正：含这些字符的字段加引号，重新跑结构检查。
- **重定义原版或其他 MOD 已有的 `id + type` 后没有任何效果**：跨文件合并时先被枚举到的文件的行生效，后到的同键行被静默跳过。
- 不要重定义既有键；确有整体替换需要时使用 `replace`(fullOverrides 整文件替换)。

## 总结

本节完成了 `descriptions.csv` 的解析：从 8 列结构、`id + type` 键和 `.ship` 的 `descriptionId`，到 text1–text5 在舰船、战机、武器和系统界面中的使用位置。

描述行的键必须与使用方逐字符匹配；缺少描述时显示占位文本，键、类型、引号或编码错误则可能在加载阶段失败。遇到异常时，先按对象类型确认查找键，再按字段内容和文件编码排查。

## 建议参考项

- `data/strings/descriptions.csv`(MOD 内相对路径)：描述文本注册入口，路径即注册、无需额外声明。
- 原版参考文件位于 `Starsector/starsector-core/data/strings/descriptions.csv`(仅观察不修改)；
- `com.fs.starfarer.api.loading.Description`：`Type` 枚举与 text1–text5 的默认值/占位行为。
- `com.fs.starfarer.api.impl.codex.CodexDataV2`：数据库系统列表的类型标签与 `Special` 回退。
