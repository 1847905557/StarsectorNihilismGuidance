# 编排 Rules 对话

一段对话就是一串规则接力：玩家点了一个选项，游戏再翻一遍表，找下一条接得上的规则。表里没有对话树这种东西，前后两句话之间唯一的联系，就是选项 ID 与 Memory 里的标记。

这种写法看着散，改起来却方便。Aster 碰上记过仇的玩家时换一副脸色，加一条规则就够了。

---

_本节预期阅读时间：\~12 分钟。_

_本节预期操作时间：\~15 分钟。_

## 前置知识

- [C15-S1] 的 Aster 问候规则与表格各列；
- [C14-S2] 的 `$GearIn_patrol_grudge` 记仇标记。

## 接住一个选项

玩家选中一个选项时，游戏在 `DialogOptionSelected` 这个时机挑规则，选项 ID 放在 `$option` 里。[C15-S1] 留下的 `GearIn_asterAlloy`，这样接：

```csv
GearIn_asterAlloy,DialogOptionSelected,$option == GearIn_asterAlloy,,"""卖给您？可以。从您手里收？价钱我说了算。""她敲了敲桌面，""想靠它发财的人，都去黑市碰运气了。""","GearIn_asterBack:""没别的事了。""",回火合金
```

条件只有一条：选项 ID 对得上。选项 ID 与规则 ID 是两回事，可以同名，也可以不同名；这里刻意让它们相同，查表时一眼就能对上。

Aster 的这句话说的就是 [C12-S2] 的设定：回火合金是垄断物资，想拿它赚钱得走黑市。对话里的人说的话，最好和游戏里实际发生的事情对得上。

## 回到问候

`GearIn_asterBack` 让对话回到问候：

```csv
GearIn_asterBack,DialogOptionSelected,$option == GearIn_asterBack,FireBest PickGreeting,,,回到问候
```

`FireBest PickGreeting` 在脚本里主动发起一次 `PickGreeting` 挑选，取命中规则中分数最高的一条来执行。此时 `$GearIn_asterMet` 已经写下，命中的是 "又是您" 那一条，选项也随之恢复。

另一个命令 `FireAll` 执行全部命中的规则，原版在 `PopulateOptions` 时机用它把各处的选项凑到一起。一句话只该有一个出处，用 `FireBest`；选项菜单需要各家往里添东西，用 `FireAll`。

## 记仇时换一副脸色

[C14-S2] 记下仇之后的 60 天里，Aster 不会再客客气气地问 "说吧"：

```csv
GearIn_asterGreetingGrudge,PickGreeting,"$id == GearIn_aster_vance score:1100
$global.GearIn_patrol_grudge","ShowPersonVisual
$GearIn_asterMet = true","Aster 的脸色不太好看。

""打掉我们巡逻队的人，还敢开着频道来找我？港务那边已经把您的船挂上黑名单了。想停靠，就得有人去打招呼，打招呼可不是免费的。""","GearIn_asterSettle:""多少钱？""
cutCommLink:断开通讯",记仇期间的问候
```

分数 1100 比另外两条问候高。记仇期间这一条与 [C15-S1] 的问候同时成立，分数高的胜出；记仇标记一过期，它不再命中，Aster 又回到平常的口气。不必去改原来那两条规则。

`$global.GearIn_patrol_grudge` 读的是全局 Memory，前缀决定读哪一块 Memory，完整的对照留到 [C15-S5]。"黑名单" 指的是 [C14-S3] 的拒绝停靠。`GearIn_asterSettle` 要交给 Java 处理，放在 [C15-S4]。

## 条件与赋值的写法

条件列与脚本列各有一套简单的写法：

| 写法 | 位置 | 含义 |
| ---- | ---- | ---- |
| `$key` | 条件 | 值为真 |
| `!$key` | 条件 | 值为假或不存在 |
| `$key == value` | 条件 | 等于，`!=`、`>`、`<`、`>=`、`<=` 同理 |
| `score:100` | 条件 | 给这条规则加分 |
| `$key = value` | 脚本 | 永久写入 |
| `$key = value 30` | 脚本 | 写入，30 个生涯天后过期 |
| `unset $key` | 脚本 | 删除 |

条件里的每一行都要成立，写在一行里的多个条件用空格分开即可，`score` 也算其中一项。

### 进游戏核对

重启游戏，**新建一份存档**，在 Foundry 的通信目录中点开 Aster。

- 选择 "回火合金卖得怎么样？"，出现关于黑市的回答，选项变为 "没别的事了"；
- 选择 "没别的事了"，回到 "又是您" 的问候与原来的两个选项；
- 按 [C14-S2] 击败一支巡逻队后再点开她，出现记仇的问候与 "多少钱？"。

页面中的现象依据原版规则与 API 推出，编者尚未逐项在游戏内实测。

## 常见问题

- **选了选项之后对话卡住，没有任何文字和按钮**：没有规则接住这个选项，或者选项 ID 拼写不一致。
    - **解决方案**：核对选项列冒号前的 ID 与条件中的 `$option ==`。

- **记仇期间仍然是平常的问候**：记仇规则的分数不高于平常问候，或者 `$global.` 前缀漏写。
    - **解决方案**：分数高于 1000，键写成 `$global.GearIn_patrol_grudge`。

- **回到问候后选项消失**：`FireBest` 后面的时机名写错。
    - **解决方案**：写成 `FireBest PickGreeting`。

## 建议参考项

- `data/campaign/rules.csv`：原版 `DialogOptionSelected` 的写法，`cutCommLink` 的处理规则 `convEnd` 也在其中。
- `com.fs.starfarer.api.impl.campaign.rulecmd.FireBest`：主动发起一次挑选并执行最佳规则。
- `com.fs.starfarer.api.impl.campaign.rulecmd.FireAll`：执行全部命中的规则。
