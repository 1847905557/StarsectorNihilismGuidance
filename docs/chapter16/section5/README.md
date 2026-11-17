# 接入酒吧任务

光在 Java 类里搭好任务状态机，那个任务还不会自动出现在游戏酒吧里；真正让它接进日常流程的，是酒吧调度表与规则表里一套约定了前缀的驱动对话。

分清哪一段留在 Java 管逻辑、哪一段放进表格管露面与接取台词，比什么都重要：接对这两头以后，整条任务链几乎不用多写额外胶水代码就能完整接进原版流程。

---

_本节预期阅读时间：\~12 分钟。_

_本节预期操作时间：\~15 分钟。_

## 前置知识

- [C16-S4] 的任务类，任务 ID 为 `GearIn_bsv`；
- [C15-S1] 与 [C15-S2] 的规则写法，[C15-S5] 的 Memory 前缀；
- [C16-S3] 中酒吧事件出现与调度的方式，酒吧任务同样由那个管理器调度。

## 登记酒吧任务

酒吧任务登记在 `data/campaign/bar_events.csv`，表头与原版相同：

```csv
bar event id,tags,freq,prob,min dur,max dur,min timeout,max timeout,min accepted timeout,max accepted timeout,plugin
GearIn_bsv,,10,1,30,40,15,20,60,90,data.scripts.gearin.campaign.GearInBeaconSurveyMission
```

| 列 | 内容 |
| -- | ---- |
| `bar event id` | 任务 ID，规则里的触发名都以它开头 |
| `freq` | 权重，与 [C16-S3] 创建器的权重同一个意思 |
| `prob` | 被挑中后真正生成的概率 |
| `min dur`、`max dur` | 生成后在酒吧里停留的天数 |
| `min timeout`、`max timeout` | 过期后隔多久才可能再出现 |
| `min accepted timeout`、`max accepted timeout` | 接下之后隔多久才可能再出现 |
| `plugin` | 任务类的完整类名 |

数值直接沿用原版 `SpySatDeployment` 那一行。表里登记过，原版的酒吧事件管理器会通过 `HubMissionBarEventWrapper` 自动生成包装，不需要像 [C16-S3] 那样手写创建器并在入口类里登记。

## 在酒吧里露面

原版在酒吧里放出任务时，依次发起两个规则时机：`<任务 ID>_blurbBar` 写描述，`<任务 ID>_optionBar` 给选项。

```csv
GearIn_bsvBlurbBar,GearIn_bsv_blurbBar,,,Aster Vance 一个人坐在靠窗的位置，面前摊着一张星图，看见您进来，抬手示意了一下。,,任务：酒吧描述
GearIn_bsvOptionBar,GearIn_bsv_optionBar,,,,GearIn_bsv_startBar:走到 Aster 那桌,任务：酒吧选项
```

这两个时机名就是任务 ID 加上后缀，没有在别处登记。任务 ID 写错一个字母，任务生成了，酒吧里却什么也看不到。

## 说明委托

玩家走过去之后，选中的是 `GearIn_bsv_startBar`：

```csv
GearIn_bsvOfferBar,DialogOptionSelected,$option == GearIn_bsv_startBar,"$missionId = GearIn_bsv
SetTextHighlights $GearIn_bsv_reward","""Ashen Verge 那座信标，挂的是我们的旗子，记录却一直传不回来。""她把星图推过来，""去读一下它的记录，带回来给我。报酬 $GearIn_bsv_reward 星币。""

""别指望那里风平浪静。要是太平，我就自己派船去了。""","contact_acceptBar:接下委托
contact_declineBarNoContinue:改天吧",任务：委托说明
```

- `$missionId = GearIn_bsv` 告诉原版接下来的接受与拒绝是针对哪个任务；
- `$GearIn_bsv_reward` 是 [C16-S4] 在 `updateInteractionDataImpl()` 里写入的奖励金额，`SetTextHighlights` 让它高亮；
- `contact_acceptBar` 与 `contact_declineBarNoContinue` 是原版的选项，接受、拒绝、回到酒吧的处理都已经写在原版规则里。

Aster 那句 "要是太平，我就自己派船去了"，对应的是 [C14-S1] 里 Foundry 周围时起时息的战事。

## 在信标旁推进

调查阶段，信标的 Memory 里有 [C16-S4] 用 `makeImportant(...)` 写下的 `$GearIn_bsv_target`。玩家点开信标时：

```csv
GearIn_bsvBeacon,OpenInteractionDialog,$GearIn_bsv_target score:1000,"Call $GearIn_bsv_ref updateData
ShowDefaultVisual",您的舰队靠近了灰烬信标。它还在运转，只是对外的发报全部停了，记录还留在里面。,"GearIn_bsv_scan:读取信标记录
defaultLeave:离开",任务：抵达信标
GearIn_bsvBeaconScan,DialogOptionSelected,$option == GearIn_bsv_scan,"$GearIn_bsv_surveyed = true
Call $GearIn_bsv_ref updateStage",记录不长，大半是巡逻队的航迹。最后几条被人刻意抹掉了，抹得并不干净。,defaultLeave:离开,任务：读取记录
```

- 条件没有前缀，读的是信标自己的 Memory，[C15-S5] 讲过这一点；
- `score:1000` 盖过导航浮标原本的交互。任务一结束，键被撤掉，信标又恢复成原版的样子；
- `Call $GearIn_bsv_ref updateData` 在原版 `ssatObjectInteraction` 中习惯写在 `conditions` 列 (因为若规则条件或文本要立刻读取 `updateData` 刚写入的变量，先在条件判定前执行更稳妥；由于 `updateData` 始终返回 `true` 且本条正文不依赖动态变量，放进 `script` 列同样能正常运行)；
- `Call $GearIn_bsv_ref updateStage` 通过引用调用任务对象，让它立刻检查一遍阶段。此时 `$GearIn_bsv_surveyed` 已经写在信标上，任务进入报告阶段；
- `defaultLeave` 是原版的离开选项。

## 回去报告

报告阶段，Aster 的 Memory 里有 `$GearIn_bsv_report`：

```csv
GearIn_bsvReport,PickGreeting,"$id == GearIn_aster_vance score:1200
$GearIn_bsv_report","ShowPersonVisual
$GearIn_bsv_reported = true
$global.GearIn_bsv_completed = true
Call $GearIn_bsv_ref updateStage","Aster 把记录调出来看了很久，最后只说了一句：""被抹掉的那几条，我会去查。""

报酬随后打进了您的账户。",cutCommLink:断开通讯,任务：报告
```

分数 1200 高于 [C15-S2] 的敌意问候。报告阶段里，哪怕刚打过齿轮工业的巡逻队，Aster 也先听完报告再说别的。

`$GearIn_bsv_reported` 写在 Aster 身上，推进到成功阶段，父类发放 30000 星币并结束任务。`$global.GearIn_bsv_completed` 让 [C16-S4] 的 `shouldShowAtMarket(...)` 不再发这份委托。

那几条被抹掉的记录，这一章不会去追。它们留给以后的章节，也留给您自己去接着写。

### 进游戏核对

重启游戏，**新建一份存档**，完成与齿轮工业的接触。委托按权重随机出现，可以多推进几天、反复进出 Foundry 的酒吧。

- Foundry 的酒吧里出现 Aster 坐在窗边的描述与 "走到 Aster 那桌"；
- 走过去后，委托说明中的 30,000 星币高亮显示，选择 "接下委托"，情报栏出现 "灰烬信标的记录"；
- 星图上灰烬信标被标为任务目标，点开后出现 "读取信标记录"；
- 读取之后，情报栏的下一步变为向 Aster 报告，Aster 被标为任务人物；
- 在通信目录中点开 Aster，出现报告的对话，获得 30000 星币，任务完成；
- 之后 Foundry 的酒吧里不再出现这份委托。

## 常见问题

- **酒吧里看不到委托**：`bar_events.csv` 的类名写错，或者 `GearIn_bsv_blurbBar` 的触发名与任务 ID 不一致。
    - **解决方案**：核对完整类名与触发名的前缀。

- **点了 "接下委托" 没有反应**：委托说明的脚本里没有 `$missionId = GearIn_bsv`。
    - **解决方案**：补上这一行。

- **读完信标，任务没有进入下一阶段**：`$GearIn_bsv_surveyed` 写了前缀，记在了别处。
    - **解决方案**：不加前缀，写在信标自己的 Memory 上。

- **报告时还是平常的问候**：分数不高于其他问候，或者任务还停在调查阶段。
    - **解决方案**：分数用 1200，先确认信标已经读取。

## 建议参考项

- `data/campaign/bar_events.csv`：原版酒吧任务的登记，`ssat` 那一行是本节的参数参考来源。
- `data/campaign/rules.csv`：以 `ssat` 开头的规则，以及 `contact_acceptBar`、`defaultLeave` 的原版处理。
- `com.fs.starfarer.api.impl.campaign.missions.hub.BaseHubMission`：`Call` 可调用的 `updateStage`、`updateData` 等动作。
