# 组织 Rules 规则表

写惯了 Java 回调之后，刚接触 `rules.csv` 时往往会有点不习惯：游戏里绝大多数人物对话与设施交互并不靠弹窗类硬撑，而是统统写进同一张扁平表格，在触发时机上整表筛选、让权重最高的那一行胜出。

Aster Vance 已经在 Foundry 的通信目录里待了好几章，接通后却还在念原版的通用客套话。先从她的第一句开场白动笔，替这位联络人写进自己的语气。

---

_本节预期阅读时间：\~12 分钟。_

_本节预期操作时间：\~15 分钟。_

## 前置知识

- [C13-S1] 的 Aster Vance，人物 ID 为 `GearIn_aster_vance`；
- [C1-S7] 的 CSV 引号与转义规则；
- [C10-S2] 的 Memory 与 `$` 前缀键值习惯。

## 表头与合并规则

在 MOD 目录下新建 `data/campaign/rules.csv`，7 列表头必须与原版保持一致：

```csv
id,trigger,conditions,script,text,options,notes
```

| 列 | 作用 |
| -- | ---- |
| `id` | 规则唯一标识，建议统一带上 `GearIn_` 前缀 |
| `trigger` | 触发时机名称，决定何时参与挑选 |
| `conditions` | 匹配条件，支持多行，全部成立才算命中 |
| `script` | 命中后依次执行的赋值或命令，支持多行 |
| `text` | 对话窗口显示的文本段落 |
| `options` | 供玩家点击的选项列表，支持多行 |
| `notes` | 注释列，游戏不读取 |

MOD 的 `rules.csv` 会与原版以及其余 MOD 合并加载。若规则 `id` 与已加载的条目同名，后加载的一条会覆盖前者，因此固定带 `GearIn_` 前缀是最省心的防撞方式。

## 编写 Aster 的问候

玩家在通信目录中点开人物时，原版会在 `PickGreeting` 时机挑选一条问候语。先写首次见面时触发的那一条：

```csv
GearIn_asterGreetingFirst,PickGreeting,"$id == GearIn_aster_vance score:1000
!$GearIn_asterMet","ShowPersonVisual
$GearIn_asterMet = true","屏幕那头的女人把一摞货单推到一边，打量了您一会儿。

""Aster Vance，齿轮工业在 Foundry 的联络人。有生意就谈，没有就别占着频道。""","GearIn_asterAlloy:""回火合金卖得怎么样？""
cutCommLink:断开通讯",Aster 首次问候
```

- **条件筛选**：`$id == GearIn_aster_vance` 限定只对 Aster 生效；`!$GearIn_asterMet` 要求还没见过面；
- **分数控制**：条件末尾的 `score:1000` 为这条规则加上基础权重。原版常规人物问候通常不写 `score` (只按成立条件数计分)，而拒接通讯的保护规则 `defaultGreetingIgnore` 设在 `score:10000` —— 因此 `score:1000` 刚好能可靠压过默认问候，又不会顶掉拉黑拒听等高优先保护规则；
- **脚本与视觉**：无参数调用 `ShowPersonVisual` 会自动展示当前对话目标人物 (`target.getActivePerson()`) 的肖像；紧接着 `$GearIn_asterMet = true` 写下已见面标记；
- **选项格式**：每行一个选项，冒号前是选项 ID，冒号后是按钮文本。`cutCommLink` 对应原版现成的 `convEnd` 结束规则，选中后直接挂断通讯。

第二次再点开 Aster 时，`$GearIn_asterMet` 已经存在，上一条不再命中，改由这条规则接替：

```csv
GearIn_asterGreeting,PickGreeting,"$id == GearIn_aster_vance score:1000
$GearIn_asterMet",ShowPersonVisual,"""又是您。""Aster 头也没抬，""说吧。""","GearIn_asterAlloy:""回火合金卖得怎么样？""
cutCommLink:断开通讯",Aster 再次问候
```

**同一时机下的同分规则必须用互斥条件隔开。** 如果两条分数相同的规则同时成立，挑选结果是不确定的。此处 `GearIn_asterAlloy` 选项尚未挂上处理规则，将在 [C15-S2] 补全。

## 规则表的书写细节

因为 `conditions`、`script`、`text` 与 `options` 经常包含换行与多段台词，书写 `rules.csv` 时只需顺带留意两条格式习惯：

- `text` 单元格内部的空白换行会直接在对话框里切成两段文字；
- 在某一行最开头加上 `#` 可以让整行被规则解析器跳过，调试时用来临时屏蔽某条规则非常顺手。

### 进游戏核对

重启游戏，**新建一份存档**，停靠 Nipher Foundry 并在通信目录中点开 Aster Vance：

- 首次接通时显示 `"Aster Vance，齿轮工业在 Foundry 的联络人..."` 段落，且左侧画面切出她的立绘；
- 点击 `"断开通讯"` 后再次接通，问候切换为 `"又是您。"`；
- 点击 `"回火合金卖得怎么样？"` 暂未触发后续台词 (将在 [C15-S2] 处理)。

## 常见问题

- **点开 Aster 后仍是原版问候**：`$id` 拼写与人物 ID 不一致，或者分数低于原版命中的规则。
    - **解决方案**：核对 `GearIn_aster_vance` 与 `score:1000`。

- **规则文件加载报错或后续规则集体失效**：某一行内的换行文本或双引号配对错位，导致整张表从该处偏移。
    - **解决方案**：顺着报错行号向上排查最近编辑过的多行单元格引号是否闭合。

- **每次接通都重复第一句开场白**：脚本里漏了 `$GearIn_asterMet = true`，或者第二条规则的条件依然写了 `!$GearIn_asterMet`。
    - **解决方案**：确认首次规则写入标记，且两条规则的条件互为反面。

## 建议参考项

- `data/campaign/rules.csv`：原版全套规则，`PickGreeting` 下各类问候与 `convEnd` 均在此表内。
- `com.fs.starfarer.api.impl.campaign.rulecmd.ShowPersonVisual`：对话左侧肖像展示命令的源码与参数说明。
