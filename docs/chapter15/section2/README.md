# 编排 Rules 对话

Rules 表里没有树状对话节点，所谓多轮选项，本质上是一次次重写状态后的重新匹配：玩家点下按钮、脚本修改 Memory 键值，引擎再在下一帧重新挑出得分最高的一行。想明白这种接力逻辑，后续给 Aster 加上行情询问、返回主菜单和敌意拦截分支时，才不会把选项顺序理乱。

---

_本节预期阅读时间：\~12 分钟。_

_本节预期操作时间：\~15 分钟。_

## 前置知识

- [C15-S1] 的 Aster 问候规则与 `rules.csv` 列结构；
- [C14-S2] 的 `$GearIn_patrol_grudge` 敌意标记。

## 响应选项：DialogOptionSelected

点击选项按钮时，引擎把按钮的选项 ID 写进 `$option`，随后在 `DialogOptionSelected` 时机挑规则。处理 [C15-S1] 留下的 `GearIn_asterAlloy` 选项只需一行：

```csv
GearIn_asterAlloy,DialogOptionSelected,$option == GearIn_asterAlloy,,"""卖给您？可以。从您手里收？价格我说了算。""她敲了敲桌面，""想靠它发财的人，都去黑市碰运气了。""","GearIn_asterBack:""没别的事了。""",回火合金
```

条件唯一要求 `$option` 匹配按钮 ID。选项 ID 与规则 ID 各自独立，既可以不同也可以保持一致；此处刻意沿用同名，在表里排查流向时能直接对照。

对话框里的台词同样应当紧贴实际玩法 —— [C12-S2] 里已经把回火合金的公开回购倍率压成一半，Aster 的这句吐槽正和真实经济规则对得上号。

## 返回上级与两条触发命令的区别

玩家点击 `"没别的事了。"` 后，通过 `FireBest` 把对话重新引回问候菜单：

```csv
GearIn_asterBack,DialogOptionSelected,$option == GearIn_asterBack,FireBest PickGreeting,,,回到问候
```

`FireBest PickGreeting` 会检索当前所有命中的 `PickGreeting` 规则并执行得分最高的那一条 (先输出 `text`，再清掉旧按钮并刷新新选项、执行 `script`)。此时 `$GearIn_asterMet` 已经写下，界面自然回到 `"又是您。"` 的问候与两个起始选项。

原版还常用另一条触发命令 `FireAll`，两者的职责区分非常明确：

- **`FireBest <trigger>`**：只挑最优的**唯一一条**命中规则来执行 (适合挑选问候语、事件入口或处理某个具体按钮)；
- **`FireAll <trigger>`**：依次执行**所有**命中的规则 (常用于 `PopulateOptions`，让原版与各个 MOD 各自往同一个菜单里追加选项，互不挤占)。

## 用更高分数覆盖敌意对话

在 [C14-S2] 记下巡逻队战损后的 60 天内，全局 Memory 中存在 `$GearIn_patrol_grudge`。新增一条更高分数的 `PickGreeting` 规则，就能在不改旧规则的前提下让 Aster 换一副语气：

```csv
GearIn_asterGreetingGrudge,PickGreeting,"$id == GearIn_aster_vance score:1100
$global.GearIn_patrol_grudge","ShowPersonVisual
$GearIn_asterMet = true","Aster 的脸色不太好看。

""打掉我们巡逻队的人，还敢开着频道来找我？港务那边已经把您的船挂上黑名单了。想停靠，就得有人去打招呼，打招呼可不是免费的。""","GearIn_asterSettle:""多少钱？""
cutCommLink:断开通讯",敌意期间的问候
```

这里把分数定为 `1100` (高于日常问候的 `1000`、低于拉黑拒听的 `10000`)。敌意生效期间这条规则与日常问候同时成立时，靠更高分直接胜出；60 天一过或标记被清掉，它自动失效，对话无痕退回平常的问候。`$global.GearIn_patrol_grudge` 读的是全局 Memory (前缀细节详见 [C15-S5])；而 `GearIn_asterSettle` 涉及动态算账，留在 [C15-S4] 用 Java 命令实现。

## 条件与脚本列常用语法

| 写法 | 所在列 | 含义 |
| ---- | ------ | ---- |
| `$key` | 条件 | 键存在且值为真 |
| `!$key` | 条件 | 键不存在或值为假 |
| `$key == value` | 条件 | 值相等 (`!=`、`>`、`<`、`>=`、`<=` 同理) |
| `score:100` | 条件 | 为本条规则追加权重分数 |
| `$key = value` | 脚本 | 写入永久键 |
| `$key = value 30` | 脚本 | 写入限时键，30 个生涯天后过期 |
| `unset $key` | 脚本 | 立即删除键 |

同一格里的多个条件按换行或空格分隔，全部成立才算命中，`score:...` 也随条件行一起书写。

### 进游戏核对

重启游戏，**新建一份存档**，在 Foundry 的通信目录中点开 Aster Vance：

- 选择 `"回火合金卖得怎么样？"`，弹出黑市相关台词与 `"没别的事了。"` 按钮；
- 选择 `"没别的事了。"`，界面退回 `"又是您。"` 问候及原始选项；
- 按 [C14-S2] 击败一支巡逻队后再点开 Aster，问候切换为敌意台词，并提供 `"多少钱？"` 选项。

## 常见问题

- **点下选项后文本停住且按钮全部消失**：没有 `DialogOptionSelected` 规则接住对应的 `$option` ID。
    - **解决方案**：逐字核对 `options` 列冒号前的按钮 ID 与目标规则的 `$option == ...`。

- **敌意生效期间依旧是日常问候**：`score` 没高于 `1000`，或者条件漏写了 `$global.` 前缀。
    - **解决方案**：确认 `score:1100` 与 `$global.GearIn_patrol_grudge` 拼写完整。

- **点返回后按钮没有刷新出来**：`FireBest` 后面的 `trigger` 名字拼错。
    - **解决方案**：写成 `FireBest PickGreeting`。

## 建议参考项

- `data/campaign/rules.csv`：大量 `DialogOptionSelected` 串联与 `PopulateOptions` 写法。
- `com.fs.starfarer.api.impl.campaign.rulecmd.FireBest`：选优触发流程、选项清空与文本先行的执行顺序。
- `com.fs.starfarer.api.impl.campaign.rulecmd.FireAll`：合并执行所有命中规则的机制。
