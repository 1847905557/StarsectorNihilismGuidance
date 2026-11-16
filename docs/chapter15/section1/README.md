# 组织 Rules 规则表

原版里几乎所有能点开的对话，开口说的第一句话都写在同一张规则表里。表里每一行是一条规则，游戏在某个时机把所有规则翻一遍，挑出条件成立、分数最高的那一条来用。

Aster 已经在 Foundry 的通信目录里待了好几章，接通之后说的却还是原版的套话。她的第一句话，该由齿轮工业自己来写了。

---

_本节预期阅读时间：\~12 分钟。_

_本节预期操作时间：\~15 分钟。_

## 前置知识

- [C13-S1] 的 Aster Vance，人物 ID 为 `GearIn_aster_vance`；
- [C1-S7] 的 CSV 引号与转义，规则表的多行单元格离不开它；
- [C10-S1] 的 Memory 与 `$` 开头的键。

## 表头与每一列

在 MOD 中新建 `data/campaign/rules.csv`，表头与原版相同：

```csv
id,trigger,conditions,script,text,options,notes
```

| 列 | 内容 |
| -- | ---- |
| `id` | 规则的唯一 ID，带 `GearIn_` 前缀 |
| `trigger` | 在什么时机参与挑选 |
| `conditions` | 条件，每行一条，全部成立才算命中 |
| `script` | 命中后执行的命令，每行一条 |
| `text` | 命中后显示的文字 |
| `options` | 命中后给出的选项，每行一个 |
| `notes` | 注释，游戏不读 |

MOD 的 `rules.csv` 与原版合并，只写自己的规则。规则 ID 与原版或别的 MOD 重复时，后加载的会顶掉前面的，所以前缀不能省。

## Aster 的问候

玩家在通信目录里点开一个人物时，原版会在 `PickGreeting` 这个时机挑一条问候。Aster 的第一句话写成：

```csv
GearIn_asterGreetingFirst,PickGreeting,"$id == GearIn_aster_vance score:1000
!$GearIn_asterMet","ShowPersonVisual
$GearIn_asterMet = true","屏幕那头的女人把一摞货单推到一边，打量了您一会儿。

""Aster Vance，齿轮工业在 Foundry 的联络人。有生意就谈，没有就别占着频道。""","GearIn_asterAlloy:""回火合金卖得怎么样？""
cutCommLink:断开通讯",Aster 首次问候
```

- **条件**：`$id == GearIn_aster_vance` 只在对方是 Aster 时成立。`!$GearIn_asterMet` 要求还没见过面，见面的标记由下一列写入；
- **分数**：条件末尾的 `score:1000` 给这条规则加分。原版的通用问候没有分数，命中的规则里分数高的胜出，Aster 的问候因此总能盖过原版那句 "Hello. What do you want?"；
- **脚本**：`ShowPersonVisual` 在画面上显示人物肖像，原版的人物问候都先调用它。`$GearIn_asterMet = true` 写下见过面的标记；
- **选项**：每行一个，冒号前是选项 ID，冒号后是按钮文字。`cutCommLink` 是原版的选项，选中后断开通讯，用不着自己写处理。

第二次再接通，标记已经在了，这条规则不再命中。见过面之后的问候是另一条规则，条件写成 `$GearIn_asterMet`，口气也不一样：

```csv
GearIn_asterGreeting,PickGreeting,"$id == GearIn_aster_vance score:1000
$GearIn_asterMet",ShowPersonVisual,"""又是您。""Aster 头也没抬，""说吧。""","GearIn_asterAlloy:""回火合金卖得怎么样？""
cutCommLink:断开通讯",Aster 再次问候
```

**同一时机下的规则，按条件分开，不要让它们同时命中。** 两条分数相同的规则都成立时，选中哪一条是不确定的。

`GearIn_asterAlloy` 这个选项现在点下去还没有反应，处理选项的规则放在 [C15-S2]。

## 多行单元格

规则表里几乎每一格都有换行。按 [C1-S7] 的规则：

- 单元格里有换行、逗号或双引号时，整格用双引号包住；
- 单元格里的双引号写成两个，`""Aster Vance""` 显示为 `"Aster Vance"`；
- 正文里的空行就是对话中的分段。

一处引号没有配对，从这一格往后的整张表都会错位，后面的规则跟着全部失效。用支持 CSV 的编辑器打开，比在纯文本里数引号可靠得多，首页推荐的 `Rainbow CSV` 插件能把每一列染成不同的颜色。

行首写 `#` 的行整行被忽略，用来暂时停用一条规则很方便。

### 进游戏核对

重启游戏，**新建一份存档**，停靠 Nipher Foundry，在通信目录中点开 Aster Vance。

- 第一次接通，出现 "Aster Vance，齿轮工业在 Foundry 的联络人" 这段话，画面显示她的肖像；
- 选择 "断开通讯"，再次点开，问候变成 "又是您"；
- 选择 "回火合金卖得怎么样？"，暂时没有反应。

页面中的现象依据原版规则与 API 推出，编者尚未逐项在游戏内实测。

## 常见问题

- **点开 Aster，仍然是原版的问候**：分数不够，或者 `$id` 写错。
    - **解决方案**：核对人物 ID `GearIn_aster_vance`，并保留 `score:1000`。

- **启动时报 `rules.csv` 解析错误，或者后面的规则全部失效**：某一格的引号没有配对。
    - **解决方案**：从报错指出的行往前找，核对每一格的引号。

- **第二次接通还是第一次的问候**：脚本里没有写入见面标记，或者两条规则的条件写反了。
    - **解决方案**：确认首次问候的脚本有 `$GearIn_asterMet = true`，两条规则的条件互为相反。

## 建议参考项

- `data/campaign/rules.csv`：原版全部规则，`PickGreeting` 下的人物问候都可以对照。
- `com.fs.starfarer.api.impl.campaign.rulecmd.ShowPersonVisual`：显示人物肖像的命令。
