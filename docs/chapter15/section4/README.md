# 编写 Rules 命令

规则表擅长拼装台词和比对状态键，但碰到算动态星币、扣货舱物资这类复杂账目时，硬往表格脚本列里塞只会徒增维护难度。只要在配置里登记好自定义命令包，就能在规则执行到关键节点时把计算交给 Java 类接手，算完再顺滑把结果写回对话内存。

---

_本节预期阅读时间：\~12 分钟。_

_本节预期操作时间：\~15 分钟。_

## 前置知识

- [C15-S2] 的敌意问候与 `GearIn_asterSettle` 选项；
- [C14-S2] 的 `$GearIn_patrols_lost` 与 `$GearIn_patrol_grudge`；
- [C7-S2] 的 MOD `settings.json` 数组合并规则。

## 注册命令扫描包：ruleCommandPackages

规则脚本里的第一项就是命令的**简单类名**，后面以空格分隔若干参数：

```csv
GearIn_asterSettle,DialogOptionSelected,$option == GearIn_asterSettle,GearInSettleGrudge 5000,,cutCommLink:断开通讯,花钱撤掉敌意标记
```

引擎在解析 `rules.csv` 时，会按 `settings.json` 中 `ruleCommandPackages` 列出的包路径依次查找对应类。MOD 只需在自己的 `data/config/settings.json` 里声明专属包，数组会自动与原版列表合并：

```json
"ruleCommandPackages":[
    "data.scripts.gearin.rules",
],
```

**Java 编译通过不代表规则找得到它。** 漏掉 `ruleCommandPackages` 登记，游戏会在加载规则时报错找不到命令；同时不同命令包之间不能存在同名类，命令类务必保持 `GearIn` 前缀。

## 继承 BaseCommandPlugin 与参数读取

所有规则命令都继承 `com.fs.starfarer.api.impl.campaign.rulecmd.BaseCommandPlugin`，核心入口为：

```java
public boolean execute(String ruleId, InteractionDialogAPI dialog, List<Token> params,
        Map<String, MemoryAPI> memoryMap)
```

| 参数 / 钩子 | 作用 |
| ----------- | ---- |
| `ruleId` | 触发本次调用的那条规则 ID |
| `dialog` | 当前对话窗口，输出文本或增减按钮都从这里取 |
| `params` | 命令名之后的实参列表，已按空格切分；含空格文本需用引号包裹 |
| `memoryMap` | 本场对话收集的各层 Memory 表 |
| 返回值 `boolean` | 写在 `conditions` 列中时充当条件真假；写在 `script` 列里一般处理完返回 `true` |
| `doesCommandAddOptions()` | 若想让命令自己动态往界面添选项而不靠 CSV `options` 列，可覆写并返回 `true` |

`execute(...)` 开头习惯先检查 `dialog == null || params.isEmpty()`。传入的实参以 `Misc.Token` 封装，调用 `params.get(0).getFloat(memoryMap)` (同理还有 `getString`、`getBoolean`、`getInt`、`getColor`) 即可自动完成字面量解析或 `$变量` 的 Memory 替换：

```java
float pricePerPatrol = params.get(0).getFloat(memoryMap);
```

因此无论规则里写的是字面量 `GearInSettleGrudge 5000` 还是动态变量 `GearInSettleGrudge $global.someRate`，这行读取逻辑都能原样兼容。

## 扣款结算与提示同步

按每支巡逻队 `5000` 星币计算，且最少按 1 支结算：

```java
int lost = Math.max(1, (int) global.getFloat(GearInPatrolGrudgeListener.LOST_KEY));
int cost = (int) (pricePerPatrol * lost);
```

星币不足时只在文本面板给出提示并保留敌意状态；星币足够则扣钱、用原版工具 `AddRemoveCommodity.addCreditsLossText(cost, text)` 输出统一样式的失金红字，再删除 `$GearIn_patrol_grudge`：

```java
cargo.getCredits().subtract(cost);
AddRemoveCommodity.addCreditsLossText(cost, text);
global.unset(GearInPatrolGrudgeListener.GRUDGE_KEY);
```

这里顺带留了一手真实代价：命令只清除了 60 天有效期的 `$GearIn_patrol_grudge`，让 [C14-S3] 的拒客插件和 [C15-S2] 的敌意问候立刻解除；但累计击毁数 `$GearIn_patrols_lost` 原封不动留在档案里，下次再与巡逻队冲突，和解价格也会按新的累计数一并提高。

## 完整的命令类

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
 * 玩家付钱请 Aster 撤掉敌意状态：价格按累计击败的巡逻队数量计算，付得起就清除敌意标记。
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

执行 `gradle jar`，重启游戏，**新建一份存档**。按 [C14-S2] 击败一支巡逻队后再在 Foundry 通信目录中点开 Aster：

- 选择 `"多少钱？"`，星币充足时扣除对应 `5000` 星币、显示失金提示并收到 Aster 的回复；
- 断开通讯重新停靠 Foundry，停靠被正常放行；
- 若故意把星币花到不足，选择该选项会提示账上不够，且 Foundry 依旧拒绝停靠。

## 常见问题

- **加载期间报错找不到 `GearInSettleGrudge` 命令**：`settings.json` 中遗漏了 `ruleCommandPackages` 登记，或者包名写错。
    - **解决方案**：检查 MOD `data/config/settings.json` 中的 `"data.scripts.gearin.rules"` 拼写。

- **选中 `"多少钱？"` 没有任何文本反馈**：类名的大小写和规则脚本里写的不一致，或误把命令填进了 `conditions` 列而非 `script` 列。
    - **解决方案**：确认规则第 4 列 (`script`) 为 `GearInSettleGrudge 5000`。

- **无论击毁几支巡逻队收费都是 `5000`**：`$GearIn_patrols_lost` 尚未正确累计，触发了保底的 `Math.max(1, ...)`。
    - **解决方案**：核对 [C14-S2] 监听器已在 `onGameLoad(...)` 生效。

## 建议参考项

- `com.fs.starfarer.api.impl.campaign.rulecmd.BaseCommandPlugin`：命令基类与 `doesCommandAddOptions` 选项注入钩子。
- `com.fs.starfarer.api.impl.campaign.rulecmd.AddCredits`：原版星币增减命令与提示输出风格。
- `com.fs.starfarer.api.util.Misc`：`Token` 参数类型的各类读取与变量解析方法。
- `data/config/settings.json`：`ruleCommandPackages` 合并规则及同名禁令注释。
