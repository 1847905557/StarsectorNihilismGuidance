# 接入 Rules 生涯场景

规则表能接管的不只是人物对话。停靠时港务的那几句话，舰队接通频道时对方的开场白，大都出自同一张表，只是挑选规则的时机不同。

想让齿轮工业在这些场合说自己的话，先得知道原版在什么时机挑规则，又往 Memory 里放了哪些能拿来写条件的东西。

---

_本节预期阅读时间：\~12 分钟。_

_本节预期操作时间：\~15 分钟。_

## 前置知识

- [C15-S1] 与 [C15-S2] 的规则写法；
- [C12-S1] 的 Foundry 市场 ID `GearIn_market_foundry`；
- [C11-S1] 巡逻队的 `$isPatrol` 标记与 [C14-S2] 的记仇标记。

## 常用的时机

下表是原版里规则最多的几个时机。写一条新规则之前，先在原版 `rules.csv` 里找到同一时机的规则照着改，比凭空写可靠得多：

| trigger | 发生在 |
| ------- | ------ |
| `OpenInteractionDialog` | 点击一个实体，打开交互对话 |
| `PopulateOptions` | 需要填充当前菜单的选项 |
| `MarketPostOpen` | 停靠市场时，决定能否交易以及港务说什么 |
| `PickGreeting` | 与人物通讯时挑选问候 |
| `OpenCommLink` | 接通舰队或实体的频道 |
| `BeginFleetEncounter` | 与舰队遭遇 |
| `DialogOptionSelected` | 玩家选中一个选项 |

时机名是原版代码约定好的字符串，拼错了不会有任何规则被挑中。

## Foundry 的港务

停靠市场时，原版在 `MarketPostOpen` 挑一条规则：港务说一句话，决定交易模式，再交给下一步。默认的那条叫 `marketPostOpenDefault`，复制过来，加上市场 ID 的条件，换掉文字：

```csv
GearIn_foundryPostOpen,MarketPostOpen,"$market.id == GearIn_market_foundry
$hasMarket
$player.transponderOn
!$faction.isHostile score:10","$tradeMode = OPEN 0
FireBest MarketPostDock",应答器的识别码发出去不久，港务频道就回了话，一个语气平平的声音念出泊位号，顺便提醒您：回火合金不在公开市场交易。,,Foundry 停靠
```

脚本两行原样照抄：`$tradeMode = OPEN 0` 允许正常交易，`FireBest MarketPostDock` 把流程交还给原版的下一步。**接管原版流程中间的一环时，脚本要把流程原样交下去。** 少了 `FireBest MarketPostDock`，停靠就停在这句话上，后面的菜单不再出现。

`score:10` 让这条规则在条件全部成立时盖过默认规则。原版对应答器关闭、关系敌对等情况各有分数更高的规则，那些情况下照样由原版处理，这里不去管它们。

`$market.id` 里的 `$market` 是当前市场的 Memory，原版在打开对话前已经把它放好。

## 记仇期间的巡逻队

接通一支舰队的频道时，原版在 `OpenCommLink` 挑一条开场白。记仇期间，齿轮工业的巡逻队只说一句话：

```csv
GearIn_patrolGrudgeComm,OpenCommLink,"$faction.id == gear_industry
$entity.isPatrol
$global.GearIn_patrol_grudge score:100","$menuState = fleetConvMain 0
FireAll PopulateOptions","频道接通了，$Rank $name 只说了一句：""我们记得你。""",,记仇期间的巡逻队通讯
```

- `$entity` 在舰队通讯里是对方舰队的 Memory，[C11-S1] 写入的 `$isPatrol` 在这里写作 `$entity.isPatrol`；
- 脚本照抄原版圣教巡逻队的写法：设置菜单状态，再用 `FireAll PopulateOptions` 凑齐原版的舰队通讯选项；
- 文字里的 `$Rank` 与 `$name` 由原版替换成对方指挥官的军衔与姓名。

这样巡逻队说完狠话之后，玩家还是可以照常选择离开或开打，规则只换掉了开场白。

### 进游戏核对

重启游戏，**新建一份存档**。

- 打开应答器停靠 Nipher Foundry，港务说的是念泊位号、提醒回火合金的那句话，之后照常进入交易菜单；
- 按 [C14-S2] 击败一支巡逻队，再与另一支齿轮工业巡逻队接通频道，对方只说 "我们记得你"，选项与原版相同。

页面中的现象依据原版规则与 API 推出，编者尚未逐项在游戏内实测。

## 常见问题

- **停靠 Foundry 后没有交易菜单**：脚本里漏了 `FireBest MarketPostDock`。
    - **解决方案**：两行脚本原样保留。

- **港务仍然是原版那句话**：市场 ID 写错，或者分数不够。
    - **解决方案**：核对 `GearIn_market_foundry`，保留 `score:10`。

- **巡逻队说完话后没有任何选项**：漏了 `FireAll PopulateOptions`，或者菜单状态没有设置。
    - **解决方案**：两行脚本照抄。

## 建议参考项

- `data/campaign/rules.csv`：`marketPostOpenDefault` 与 `greetingsLuddicPatrolCanaan`，本节两条规则的照抄来源。
- `com.fs.starfarer.api.campaign.rules.MemKeys`：原版放进对话 Memory 的各个槽位名称。
