# 编写 Rules 命令

规则表能判断、能赋值、能接力，可它算不了账。Aster 开口要价时，价格得按玩家打掉了几支巡逻队来算，钱够不够也要现场去查，这些都超出了一张表能做的事。

这时候就该写一个 Java 类，让规则表在脚本里像调用原版命令一样调用它。

---

_本节预期阅读时间：\~12 分钟。_

_本节预期操作时间：\~15 分钟。_

## 前置知识

- [C15-S2] 的记仇问候与 `GearIn_asterSettle` 选项；
- [C14-S2] 的 `$GearIn_patrols_lost` 与 `$GearIn_patrol_grudge`；
- [C7-S2] 的 MOD `settings.json` 合并规则。

## 规则怎样找到这个类

规则脚本里的每一行，第一个词是命令名，后面是参数：

```csv
GearIn_asterSettle,DialogOptionSelected,$option == GearIn_asterSettle,GearInSettleGrudge 5000,,cutCommLink:断开通讯,花钱摆平记仇
```

`GearInSettleGrudge` 就是类的简单类名。游戏拿着它，到 `settings.json` 的 `ruleCommandPackages` 列出的每一个包里去找同名的类。原版只列了自己的几个包，MOD 要在自己的 `data/config/settings.json` 里补上：

```json
"ruleCommandPackages":[
    "data.scripts.gearin.rules",
],
```

数组与原版合并，原版的包仍然有效，Console Commands、LunaLib 等 MOD 也是这样登记自己的命令包的。**Java 能编译通过，不代表规则找得到它。** 漏了这一项，加载规则时会报告找不到命令。

原版 `settings.json` 的注释写明，不同包里的命令不能同名。命令名就是类名，带上 `GearIn` 前缀，不会和别人撞上。

## 继承 BaseCommandPlugin

命令类继承原版的 `BaseCommandPlugin`，只需要实现 `execute(...)`：

```java
public boolean execute(String ruleId, InteractionDialogAPI dialog, List<Token> params,
        Map<String, MemoryAPI> memoryMap)
```

| 参数 | 内容 |
| ---- | ---- |
| `ruleId` | 调用它的那条规则的 ID |
| `dialog` | 当前对话，写文字、加选项都从它取 |
| `params` | 命令名之后的参数，按空格切开 |
| `memoryMap` | 当前对话能读到的各块 Memory |

返回值只在命令写在条件列里时才有意义，表示条件是否成立。写在脚本列里时，返回值不影响什么，原版的 `AddCredits` 处理完都返回 `true`。

`dialog` 可能为空，原版的命令开头都先判断一句。这个命令要往对话里写字，没有对话就什么也不做。

## 读参数

参数是 `Token` 对象，`getFloat(memoryMap)` 把它读成数值：

```java
float pricePerPatrol = params.get(0).getFloat(memoryMap);
```

参数可以是字面量，也可以是 `$` 开头的变量，`getFloat(...)` 会到 `memoryMap` 里替换。规则里写 `GearInSettleGrudge 5000`，读到的就是 5000；写成 `GearInSettleGrudge $global.someKey`，读到的是那个键的值。所以读参数时总要把 `memoryMap` 传进去。

## 算账

每支巡逻队 5000 星币，按累计数计算，至少按一支收：

```java
int lost = Math.max(1, (int) global.getFloat(GearInPatrolGrudgeListener.LOST_KEY));
int cost = (int) (pricePerPatrol * lost);
```

打得越多，摆平得越贵。偶尔失手打了一支，花点小钱就能回去停靠；把齿轮工业的巡逻队当靶子打的人，迟早会发现这笔账算不过来。

钱不够时只说一句话，什么也不改。钱够就扣钱、清掉记仇标记：

```java
cargo.getCredits().subtract(cost);
AddRemoveCommodity.addCreditsLossText(cost, text);
global.unset(GearInPatrolGrudgeListener.GRUDGE_KEY);
```

`addCreditsLossText(...)` 是原版用来显示 "失去 x 星币" 的那一行文字，自己的命令也拿来用，玩家看到的提示与原版一致。

记仇标记清掉之后，[C14-S3] 的插件不再拦住停靠，[C15-S2] 的记仇问候也不再命中。累计数没有清，以后再打，价格照旧往上涨。

## 完整的命令

`src/data/scripts/gearin/rules/GearInSettleGrudge.java`：

```java
package data.scripts.gearin.rules;

import java.util.List;
import java.util.Map;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CargoAPI;
import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.campaign.TextPanelAPI;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.impl.campaign.rulecmd.AddRemoveCommodity;
import com.fs.starfarer.api.impl.campaign.rulecmd.BaseCommandPlugin;
import com.fs.starfarer.api.util.Misc.Token;

import data.scripts.gearin.campaign.GearInPatrolGrudgeListener;

/**
 * GearInSettleGrudge &lt;每支巡逻队的价格&gt;
 * 玩家付钱请 Aster 摆平记仇：价格按累计击败的巡逻队数量计算，付得起就清除记仇标记。
 */
public class GearInSettleGrudge extends BaseCommandPlugin {

    @Override
    public boolean execute(String ruleId, InteractionDialogAPI dialog, List<Token> params,
            Map<String, MemoryAPI> memoryMap) {
        if (dialog == null || params.isEmpty()) {
            return false;
        }
        float pricePerPatrol = params.get(0).getFloat(memoryMap);
        MemoryAPI global = Global.getSector().getMemoryWithoutUpdate();
        int lost = Math.max(1, (int) global.getFloat(GearInPatrolGrudgeListener.LOST_KEY));
        int cost = (int) (pricePerPatrol * lost);

        CargoAPI cargo = Global.getSector().getPlayerFleet().getCargo();
        TextPanelAPI text = dialog.getTextPanel();
        if (cargo.getCredits().get() < cost) {
            text.addPara("Aster 摇了摇头：这件事要 " + cost + " 星币，您的账上不够。");
            return true;
        }
        cargo.getCredits().subtract(cost);
        AddRemoveCommodity.addCreditsLossText(cost, text);
        global.unset(GearInPatrolGrudgeListener.GRUDGE_KEY);
        text.addPara("Aster 收下了钱：港务那边，她会去打招呼。");
        return true;
    }
}
```

### 进游戏核对

执行 `gradle jar`，重启游戏，**新建一份存档**。按 [C14-S2] 击败一支巡逻队，再在 Foundry 的通信目录中点开 Aster。

- 选择 "多少钱？"，星币足够时扣去 5000 星币，Aster 说她会去打招呼；
- 断开通讯后停靠 Foundry，不再被拒绝；
- 星币不足时，Aster 只说钱不够，记仇标记保留，停靠仍被拒绝。

页面中的现象依据原版规则与 API 推出，编者尚未逐项在游戏内实测。

## 常见问题

- **加载时报告找不到命令**：`ruleCommandPackages` 没有登记，或者包名与类所在的包不一致。
    - **解决方案**：在 MOD 的 `settings.json` 中加入 `data.scripts.gearin.rules`。

- **选了 "多少钱？" 之后没有任何文字**：命令名与类名不一致，或者命令写进了条件列。
    - **解决方案**：脚本列写 `GearInSettleGrudge 5000`，大小写与类名一致。

- **价格始终是 5000**：累计数没有记录，按至少一支计算。
    - **解决方案**：核对 [C14-S2] 的监听器是否登记。

## 建议参考项

- `com.fs.starfarer.api.impl.campaign.rulecmd.BaseCommandPlugin`：命令的基类。
- `com.fs.starfarer.api.impl.campaign.rulecmd.AddCredits`：原版加减星币的命令，参数与文字提示的写法。
- `com.fs.starfarer.api.util.Misc`：内部类 `Token` 的各个读取方法。
- `data/config/settings.json`：`ruleCommandPackages` 及其注释。
