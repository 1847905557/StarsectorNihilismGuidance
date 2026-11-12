# 组织 Rules 规则表

本节给独立舰队添加两种通讯问候，非敌对时采用一段，其他情况采用另一段，通讯菜单仍保留原版选项。借这两条规则，我们说明身份、条件、脚本与文字怎样放进 `rules.csv`，再从加载日志和实际通讯确认它们的作用。

_本节预期阅读时间：\~25 分钟。_

_本节预期操作时间：\~30 分钟。_

## 学习目标

完成后，您将能够：

- 说明 `rules.csv` 七列各自生成什么运行时数据；
- 在多行单元格中正确使用引号、换行和字段注释；
- 判断同路径合并、`replace` 和重复 ID 对加载结果的影响；
- 从日志区分数据未加载、规则未注册和表达式语法错误；
- 用两条独立势力 `OpenCommLink` 规则验证加载结果。

## 前置知识

- [C14-S3] 的交互对话框生命周期；
- [C16-S1] 的 `InteractionDialogAPI` 与选项分支；
- [C1-S7] 的 CSV 表头、单元格与引号；
- [C16-S5] 的 `trigger`、条件、脚本与选项最小记录。

## 给独立舰队安排通讯问候

在 MOD 的 `data/campaign/rules.csv` 追加 `GearIn_rules_comm_friendly` 与 `GearIn_rules_comm_fallback`，保留已有规则与表头。前者限定非敌对独立舰队，后者接住其他独立舰队，实际成品在后面的完整记录中保存。下面的局部片段分别展开这些字段。

规则表采用七列。表头把每个单元格对应到字段，字段内部还各有自己的语法：

```csv
id,trigger,conditions,script,text,options,notes
```

每个有效数据行生成规则，列名决定它读入哪份内容。列可以随表头调整顺序，同一文件中的记录继续按该表头排列。当前沿原版顺序组织，便于与已有数据对应。

### 让 ID 与通讯入口分别对应

`id` 表示规则身份，加载日志用它定位本条记录。空 ID 不创建规则，以 `#` 开头则停用整行，局部对照如下：

```csv
#GearIn_rules_comm_disabled,OpenCommLink,,,,,暂时停用
GearIn_rules_comm_friendly,OpenCommLink,,,,,独立势力的非敌对问候
```

第二条使用自己的前缀，日志与后续匹配可以找到同一个 ID。`trigger` 另决定规则在何时参加候选：独立舰队建立通讯时，原版调用 `OpenCommLink`，所以这两条规则都挂到这个名字。

```csv
GearIn_rules_comm_friendly,OpenCommLink,,,,,独立势力的非敌对问候
```

**规则身份与触发入口分别填写。** `id` 独立，`trigger` 与实际调用字符串逐字符一致。自有 trigger 也可以组织规则，但还要由 Java 或另一条 Rules 脚本发起调用。

### 按势力与敌对关系选择候选

`conditions` 按物理换行写多条条件。下面的局部片段先限定 `independent`，再限定非敌对，注释只说明这份条件：

```csv
GearIn_rules_comm_friendly,OpenCommLink,"# 只处理独立势力
$faction.id == independent
!$entity.isHostile score:100",,,,独立势力的非敌对问候
```

读取时跳过空行与 `#` 子行，其余表达式去掉首尾空白后依次求值。任意一条失败，候选就不成立；`score:100` 改变第二条条件的分值，[C15-S2] 继续说明计分与选择。

## 命中后恢复通讯菜单

本节的问候命中后，还要让玩家继续操作。`script` 先写通讯菜单状态，再用 `FireAll PopulateOptions` 填入原版选项：

```csv
GearIn_rules_comm_friendly,OpenCommLink,,"$menuState = fleetConvMain 0
FireAll PopulateOptions",,,独立势力的非敌对问候
```

两行按顺序执行，后面的菜单规则能取得前面刚写入的 `fleetConvMain`。脚本也按换行跳过空行和 `#` 注释，普通命令从上到下执行。

命令简单名由 `data/config/settings.json` 的 `ruleCommandPackages` 查找，原版基础包为 `com.fs.starfarer.api.impl.campaign.rulecmd`。命令没有对应类时，在加载阶段报告错误，[C15-S4] 再接入自有命令包。

命令参数需要字面双引号时，CSV 使用两个连续双引号转义。例如 `SetShortcut optionId "ESCAPE"` 在单元格中写成 `SetShortcut optionId ""ESCAPE""`。

### 文字与菜单各自提供结果

`text` 是命中后显示的正文，规则执行器替换其中的文本 token，再写入当前对话。将条件、脚本与文字放在一起，局部记录如下：

```csv
GearIn_rules_comm_friendly,OpenCommLink,"$faction.id == independent
!$entity.isHostile score:100","$menuState = fleetConvMain 0
FireAll PopulateOptions",对方很快回应了通讯请求。,,独立势力的非敌对问候
```

第五列提供问候，脚本另恢复菜单，`notes` 留作维护说明。文本中的井号仍是显示字符，例如 `# 通讯接通了。` 会原样出现在面板中。

::: tip 用独占一行的 OR 准备候选文本

同一条规则需要随机采用多种措辞时，可以用独占一行的 `OR` 分隔候选文本，并用一对双引号包住整个多行单元格：

```csv
GearIn_rules_text_variants,OpenCommLink,,,"对方很快回应了通讯请求。
OR
# 通讯接通了。",,两种问候文本
```

加载器按精确的换行序列 `\nOR\n` 拆分内容，执行规则时再随机选择其中一段。第二个候选开头的井号会正常显示；`OR` 与其他文字共处一行时属于正文。
:::

## 给规则添加选项与维护说明

当前问候通过原版 `PopulateOptions` 取得选项，需要自有选项时，还可以在 `options` 列逐行写 `id:text` 或 `order:id:text`。以下局部片段分别使用默认顺序与顺序 50：

```csv
GearIn_rules_options,PopulateOptions,,,,"GearIn_rules_apply_open:检查通讯记录
50:cutCommLink:切断通讯",两个选项
```

玩家看到两段显示文字，选项数据分别为 `GearIn_rules_apply_open` 与 `cutCommLink`。两段格式默认顺序为 0，三段格式把首段解析为数值。读取器按半角冒号拆分，显示文字多出半角冒号就会改变这份结构；ID 以 `$` 开头时加载失败。

和条件、脚本一样，`options` 中以 `#` 开头的子行是注释。选项 ID 与 `$option` 的回流关系由 [C15-S2] 负责。

`notes` 保存用途、数据归属与规则关系，不参与运行。局部记录如下：

```csv
GearIn_rules_comm_friendly,OpenCommLink,,,,,GearIn 独立势力通讯问候
```

这里的维护说明不会出现在玩家面板中，停用仍通过行首 ID 处理。

## 把多行内容保留在同一条记录中

### 用双引号包住多行单元格

Rules 先按 [C1-S7] 的 CSV 规则读取单元格，再解释字段内部内容。条件、脚本、文字与选项都可多行，用一对双引号包住整个字段。局部记录如下：

```csv
GearIn_rules_comm_friendly,OpenCommLink,"$faction.id == independent
!$entity.isHostile score:100","$menuState = fleetConvMain 0
FireAll PopulateOptions",对方很快回应了通讯请求。,,独立势力的非敌对问候
```

这条记录占四个物理行，解析后仍是一条七列记录。引号内换行属于当前字段，引号外换行结束记录。加载错误指向后面的行时，前一条是否闭合也参与定位。

### 转义逗号和双引号

含半角逗号的单元格也要放进双引号。单元格内部的一个字面双引号写成 `""`；反斜杠不会替代 CSV 的双引号转义。

```csv
GearIn_rules_quoted,OpenCommLink,,"AddText ""信号稳定，继续通讯。""",,,含逗号和引号
```

命令取得的参数为 `信号稳定，继续通讯。`，字面引号通过 CSV 的 `""` 保留到命令层。参数中使用半角逗号时，单元格外层引号也继续保留，逗号才能留在字段内。

### 区分整行注释与字段注释

井号作用于所在层：ID 中停用记录，条件、脚本与选项中跳过子行，文字中保留显示。以下局部对照把这几种情况放在同一表头下：

```csv
#GearIn_rules_comm_disabled,OpenCommLink,,,,,整行停用
GearIn_rules_comm_note,OpenCommLink,"# 条件注释
$faction.id == independent","# 脚本注释
$menuState = fleetConvMain 0","# 这里的井号会显示",,字段内注释
```

第二条规则仍会注册。它只有一个有效条件和一条有效脚本，而文本面板会完整显示 "# 这里的井号会显示"。

### 保存为 UTF-8 无 BOM

规则表使用 UTF-8 无 BOM。BOM 位于文件首字节时可能进入首个表头或键的解析结果；智能引号替换只处理弯引号等字符，不负责移除错误编码标记。

先让当前记录解析出七列，字段内部的 Rules 表达式才会交给正确读取器。缺失引号或裸逗号改变列边界时，条件与脚本的语法判断也会随之落到错误内容上。

## 从加载结果定位规则

### 登记规则文件并合并同路径资源

游戏先从 `data/config/settings.json` 的 `rules` 数组取得规则文件路径，再逐个读取。原版登记了 `data/campaign/rules.csv` 和 `data/campaign/rules/askoniaCrisis2.csv`。本节的两条问候放在前一个路径下，与原版及其他 MOD 的同路径 CSV 按 ID 合并，并处理智能引号。规则从启用的数据重建，存档中的运行状态另由 Memory 等对象保存。

日志先出现 `Loading CSV data from [...]`，再输出 `Loading rule: <id>`。后者表示开始解析这条规则，随后还可能发生表达式错误；确认启动加载成功后，再用同一个 ID 找到实际通讯候选。文件路径、规则解析和运行命中各有对应结果。

::: warning 重复 ID 要按文件路径区分

同一个 CSV 文件内的重复 ID 会抛出 `RuntimeException("Duplicate key [...] while loading [...]")`。合并原版与 MOD 的同路径 CSV 时，先处理的同 ID 行保留，后处理的跳过。对于 `rules` 数组中分别登记的不同路径，游戏会继续逐个加载；它们若在同一 trigger 下使用了相同 ID，会抛出 `Duplicate rule id: <id>`。
:::

`mod_info.json` 中的 `replace` 按路径生效。例如，替换 `data/campaign/rules.csv` 后，这个路径采用替换文件，`rules` 数组中的其他路径仍会加载。

::: caution replace 要提供被替换路径的完整规则

`data/campaign/rules.csv` 包含原版通讯、市场和任务等规则。替换这个路径，就需要在替换文件中完整提供这些内容。本节通过同路径合并添加两条问候。
:::

加载器还会统计只出现一次的变量，并向标准输出打印 `WARNING: variable [...] only used once; rule id: [...]`。`$tag:` 和 `$market.mc:` 不参与这项统计；WARNING 不会改变规则匹配。

### 表达式错误发生在哪一列

条件和脚本使用同一套表达式解析。状态赋值写在脚本，比较放在条件；加载明确拒绝条件中的 `=` 和脚本中的 `==`。`!=`、`>`、`>=`、`<`、`<=` 同样用于比较，脚本中的计算返回值也不承担候选筛选。

条件列出现赋值、脚本列出现 `==`、未知命令、选项 ID 以 `$` 开头、三段选项的 `order` 不是数值，以及同一文件出现重复 ID，都会阻止对应数据正常进入运行时。堆栈中的规则 ID 和 CSV 路径是修复入口。

定位文件已读取、目标 ID 却没有进入规则解析时，先查同路径合并中是否已有相同 ID。日志若报告 `Duplicate rule id`，则查 `rules` 分别登记的文件中，哪个文件在同一 trigger 下使用了这个 ID。

## 七列速查表

| 字段         | 读取结果            | 内部拆分                            | 主要错误                          |
| ------------ | ------------------- | ----------------------------------- | --------------------------------- |
| `id`         | 规则稳定身份        | 不拆分                              | 空值、`#` 跳过、重复 ID           |
| `trigger`    | 候选集合名称        | 不拆分                              | 拼写或大小写与调用方不一致        |
| `conditions` | 候选匹配条件        | 按换行拆分，跳过空行和 `#` 子行     | 写入赋值或错误比较                |
| `script`     | 命中后的赋值与命令  | 按换行顺序拆分，跳过空行和 `#` 子行 | 未知命令、比较进入脚本            |
| `text`       | 文本面板内容        | 整个单元格作为文本内容读取          | 把 `#` 误认为字段注释             |
| `options`    | 选项 ID、文本与顺序 | 按换行拆分，再按冒号分成 2 或 3 段  | 裸冒号、非法 `order`、`$` 开头 ID |
| `notes`      | 维护说明            | 不参与运行时解析                    | 把 `notes` 当作停用开关           |

## 用 `OpenCommLink` 验证规则表

`OpenCommLink` 从原版舰队通讯进入规则匹配。两个成品都限定 `independent`，friendly 另限定非敌对并取得较高分数，脚本恢复 `fleetConvMain` 与原版选项。目标关系变化时采用对应问候，通讯操作继续保留。

## 完整最小例

把下列完整表头与两条数据写入前述文件。已有表头时追加两条记录，保留前文规则。

```csv
id,trigger,conditions,script,text,options,notes
GearIn_rules_comm_friendly,OpenCommLink,"$faction.id == independent
!$entity.isHostile score:100","$menuState = fleetConvMain 0
FireAll PopulateOptions",对方很快回应了通讯请求。,,独立势力的非敌对问候
GearIn_rules_comm_fallback,OpenCommLink,$faction.id == independent score:50,"$menuState = fleetConvMain 0
FireAll PopulateOptions",独立舰队接通了通讯。,,独立势力的回退问候
```

## 验证方案

前置数据：已启用包含本页 `data/campaign/rules.csv` 的 MOD。观察位置包括启动日志、启动器标准输出和独立舰队通讯界面。

1. 启动游戏，确认日志出现 `Loading CSV data from [...]`，并找到 `GearIn_rules_comm_friendly` 与 `GearIn_rules_comm_fallback` 的 `Loading rule:` 行。
2. 找到非敌对独立舰队，选择建立通讯，查看 `friendly` 文本与原版选项。
3. 让同一势力舰队进入敌对状态后再次通讯，确认 `fallback` 文本接替 `friendly` 文本。
4. 在启动器标准输出中核对单次使用变量 WARNING，并把变量名与规则 ID 对应起来。
5. 在测试副本中依次检查三种重复：同一 CSV 内重复 ID 时加载报错；两个 MOD 的同路径 CSV 使用相同 ID 时保留先处理项；两个已分别登记的路径在同一 trigger 下重复 ID 时报告 `Duplicate rule id`。每次检查后恢复唯一 ID。
6. 在 `conditions` 中写赋值、在 `script` 中写比较、把命令名写错，逐项确认加载期错误后恢复正确表达式。
7. 在 `options` 文本中加入半角冒号，确认选项解析错误；改用全角冒号或改写句子。
8. 在测试副本中给 `data/campaign/rules.csv` 声明 `replace`，确认该路径采用替换文件，而 `data/campaign/rules/askoniaCrisis2.csv` 仍有加载记录；移除声明后，确认主规则表恢复同路径合并。

预期结果：

- 日志同时出现规则文件路径和两个稳定 ID；数据结构错误在进入生涯前暴露。
- 非敌对独立舰队使用 `friendly` 文本，敌对独立舰队使用 `fallback` 文本。
- 两条脚本都恢复 `fleetConvMain` 并追加原版 `PopulateOptions` 结果。

## 故障分析

- **规则加载时启动中止**：`conditions` 中出现赋值、`script` 中出现 `==`、选项 ID 以 `$` 开头、命令类缺失或同文件 ID 重复。
    - **解决方案**：按堆栈定位 CSV 行并把表达式放回正确列，重启。

- **规则没有触发且日志无异常**：本规则 ID 与其他同路径来源重复，后处理项被跳过。
    - **解决方案**：改用带 MOD 前缀的唯一 ID。

- **加载时报 Duplicate rule id**：分别登记的规则文件在同一 trigger 下使用了相同 ID。
    - **解决方案**：按日志中的 ID 检查 `settings.json` 的 `rules` 所列文件，为自有规则使用唯一 ID。

- **选项文本在冒号后被截断**：加载器把冒号解释为选项字段分隔。
    - **解决方案**：改写文本或使用全角冒号，重启。

- **选项加载时报 `NumberFormatException`**：三段选项的首段不是可解析的排序数值。
    - **解决方案**：删除多余冒号或提供合法 `order`，重启。

- **原版主规则表中的通讯或市场规则缺失**：MOD 对 `data/campaign/rules.csv` 声明了 `replace`。
    - **解决方案**：移除 `replace`，让同路径 CSV 参与合并。

## 总结

两条通讯记录用规则 ID 保存身份，trigger 连接通讯请求，条件按目标关系选择，脚本恢复菜单，文字提供问候。CSV 保留多行字段，Rules 再解析其中的表达式与显示内容。

加载成功后，规则还要进入实际调用的候选并满足条件。日志与通讯界面共同确认这些结果，下一节再把选项回流与结果选择连接成完整分支。

## 建议参考项

- `data/campaign/rules.csv`：本节 `GearIn_rules_comm_*` 数据与原版规则表的存放路径。
- `data/config/settings.json`：`rules` 规则文件列表与 `ruleCommandPackages` 命令包配置。
- `com.fs.starfarer.api.campaign.rules.RuleAPI`：加载后单条规则的字段对象。
- `com.fs.starfarer.api.campaign.rules.Option`：对话选项的 `id` / `text` / `order`。
- `com.fs.starfarer.api.campaign.rules.ExpressionAPI`：条件和脚本表达式的加载结果。
- `com.fs.starfarer.api.impl.campaign.rulecmd.FireAll`：示例脚本调用的原版菜单填充命令。
