# 接入 Rules 生涯场景

`rules.csv` 管的可不止人物闲聊：空间站停靠时的港务播报、舰队通联时的开场白乃至菜单选项的拼装，同样在各自的 `trigger` 时机按整表匹配。

如果您不想在替换某句台词时把原版后面的交易或交战菜单一并截断，动手前只需守住一条硬性规则：把末尾的转发命令老实接回去，就能只改想改的那一截，其余流程照旧流转。

---

_本节预期阅读时间：\~12 分钟。_

_本节预期操作时间：\~15 分钟。_

## 前置知识

- [C15-S1] 与 [C15-S2] 的规则结构；
- [C12-S1] 的 Foundry 市场 ID `GearIn_market_foundry`；
- [C11-S1] 巡逻队的 `$isPatrol` 标记与 [C14-S2] 的 `$GearIn_patrol_grudge` 标记。

## 原版常用 trigger 入口

撰写全局场景规则前，最稳妥的做法是先去原版 `data/campaign/rules.csv` 里搜出同一 `trigger` 下的参考规则，查清原版依赖了哪些条件与交接命令：

| `trigger` | 何时触发 |
| --------- | -------- |
| `OpenInteractionDialog` | 玩家点击实体打开交互界面瞬间 |
| `PopulateOptions` | 界面需要填充当前阶段的功能选项列表时 |
| `MarketPostOpen` | 停靠带有市场的站点时，决定允许哪些交易模式与港务播报 |
| `PickGreeting` | 接通人物通信后挑选开场问候 |
| `OpenCommLink` | 刚与某支舰队或星图实体接通无线电频道时 |
| `BeginFleetEncounter` | 与舰队遭遇进入接触菜单时 |
| `DialogOptionSelected` | 玩家选中交互选项按钮时 |

## 改写 Foundry 停靠播报：MarketPostOpen

玩家停靠并准备打开市场时，原版会在 `MarketPostOpen` 挑出 `marketPostOpenDefault` 等规则设置 `$tradeMode` 并触发 `FireBest MarketPostDock`。如果希望 Foundry 拥有专属停靠词，在条件中限定 `$market.id` 并保留完整的交易门槛即可：

```csv
GearIn_foundryPostOpen,MarketPostOpen,"$market.id == GearIn_market_foundry
$hasMarket
$player.transponderOn
RepGTE $faction.id SUSPICIOUS
!$faction.isHostile score:10","$tradeMode = OPEN 0
FireBest MarketPostDock",应答器的识别码发出去不久，港务频道就回了话，一个语气平平的声音念出泊位号，顺便提醒您：回火合金不在公开市场交易。,,Foundry 停靠
```

这里有两处不能省略的细节：

- **必须保留流程交接脚本**：`$tradeMode = OPEN 0` 允许开放常规交易，`FireBest MarketPostDock` 负责继续打开交易与离开菜单；漏掉 `FireBest MarketPostDock`，对话就会卡在这句话上彻底失去后续菜单；
- **配合 `RepGTE $faction.id SUSPICIOUS` 守住声望门限**：原版里拒绝冷淡 (`INHOSPITABLE`) 关系进入公开交易的 `marketPostOpenNoTrade` 只有 5 个条件、没有额外 `score:`，而 `!$faction.isHostile` 在 `INHOSPITABLE` 时依然为真；若不加上 `RepGTE $faction.id SUSPICIOUS`，直接挂 `score:10` 就会把本来该被原版拒贸规则拦住的冷淡状态强行放行成 `$tradeMode = OPEN`。

## 改写敌意期间的巡逻队喊话：OpenCommLink

在星图上主动向齿轮工业巡逻队发起通讯时，原版会在 `OpenCommLink` 前将对方指挥官设成当前对话人物 (`setActivePerson`)，再挑选开场白。若玩家近期击毁过巡逻队 (`$global.GearIn_patrol_grudge` 为真)，改由下面这条规则接管：

```csv
GearIn_patrolGrudgeComm,OpenCommLink,"$faction.id == gear_industry
$entity.isPatrol
$global.GearIn_patrol_grudge score:100","$menuState = fleetConvMain 0
FireAll PopulateOptions","频道接通了，$Rank $name 只说了一句：""我们记得你。""",,敌意期间的巡逻队通讯
```

- `$entity` 指向当前通讯的目标舰队，因此 [C11-S1] 写入的 `MemFlags.MEMORY_KEY_PATROL_FLEET` (`$isPatrol`) 在规则里写成 `$entity.isPatrol`；
- 脚本延续原版舰队通讯的标准写法：先写入 `$menuState = fleetConvMain 0`，再通过 `FireAll PopulateOptions` 组装开火或离开选项，保证对话只换开场白而不破坏原有按钮；
- 文字里的 **`$Rank`** 与 **`$name`** 来自原版 `CoreCampaignPluginImpl.updatePersonFacts(...)` 写入人物 `local` Memory 的字段，在 `OpenCommLink` 与 `PickGreeting` 这类绑定了 `activePerson` 的对话中会直接替换成对方指挥官的军衔与姓名 (若在未绑定 `activePerson` 的场景引用，则需改用 `CoreRuleTokenReplacementGeneratorImpl` 提供的 `$PersonRank` / `$personName`)；
- `score:100` 确保敌意生效期间即便玩家关着应答器也优先触发这句警告，但仍低于屏蔽通讯用的 `greetingNoComms` (`score:10000`)。

### 进游戏核对

重启游戏，**新建一份存档**：

- 开启应答器且势力关系在可交易范围内停靠 Nipher Foundry，屏幕打印港务播报并顺畅进入正常交易菜单；
- 按 [C14-S2] 击败一支巡逻队后再找另一支齿轮工业巡逻队建立通讯，对方开头出现带指挥官军衔与姓名的 `"我们记得你。"`，且原本的对话选项完整保留。

## 常见问题

- **停靠 Foundry 后港务说完话没有交易按钮**：脚本列漏掉了 `FireBest MarketPostDock`。
    - **解决方案**：保留 `$tradeMode = OPEN 0` 与 `FireBest MarketPostDock` 两行脚本。

- **港务播报仍是原版默认文本**：市场 ID 写错，或当前关系未满足 `RepGTE` / `transponderOn` 条件而落入了原版其他分流规则。
    - **解决方案**：核对 `GearIn_market_foundry` 与 `score:10`。

- **巡逻队说完话后没有任何选项**：漏了 `FireAll PopulateOptions`，或者菜单状态没有设置。
    - **解决方案**：逐字核对 `$menuState = fleetConvMain 0` 与 `FireAll PopulateOptions`。

## 建议参考项

- `data/campaign/rules.csv`：`marketPostOpenDefault`、`marketPostOpenNoTrade` 与 `greetingsLuddicPatrolCanaan` 原版范例。
- `com.fs.starfarer.api.impl.campaign.CoreCampaignPluginImpl`：`updatePersonFacts(...)` 写入 `$Rank`、`$name`、`$post` 等人物实时事实的来源。
- `com.fs.starfarer.api.impl.campaign.CoreRuleTokenReplacementGeneratorImpl`：全对话通用文本替换键 (`$PersonRank`、`$personName`、`$MarketName` 等)。
- `com.fs.starfarer.api.campaign.rules.MemKeys`：对话过程中注入的各个 Memory 命名空间键名。
